package dev.bossestesting.entity;

import dev.bossestesting.ai.Brain;
import dev.bossestesting.anim.Anims;
import dev.bossestesting.anim.Clip;
import dev.bossestesting.encounter.Encounter;
import dev.bossestesting.encounter.Encounters;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Common body of both forms of El Guilhermino: the animation shown on clients comes from one synced clip id plus a
 * sequence counter (late joiners get it too), the fight is driven on the server by a {@link Brain}, death is delayed
 * until the death sequence ends and the loot is released once.
 *
 * <p>Sync rule: the server decides every gameplay event by tick ({@link Brain#LEAD} ticks after the clip starts, the
 * GeckoLib transition plus the network tick); GeckoLib custom instructions on the client only drive cosmetics that
 * the server ignores (idle spasms, footsteps), so nothing happens twice.
 */
public abstract class GuilherminoBoss extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(GuilherminoBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIM_SEQ = SynchedEntityData.defineId(GuilherminoBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(GuilherminoBoss.class, EntityDataSerializers.BYTE);

    /** Synced state bits. */
    public static final int F_OVERLOADED = 1, F_REACTOR_DARK = 2, F_VULNERABLE = 4, F_IGNITING = 8, F_RUNNING = 16, F_INVULNERABLE = 32;

    /** Client hook for GeckoLib custom instructions (set by the client setup; a no-op on dedicated servers). */
    public static volatile BiConsumer<GuilherminoBoss, String> instructionSink = (boss, instruction) -> {};

    private static final Map<Clip, RawAnimation> RAW = new HashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    protected final Brain brain;
    private UUID encounterId;
    private boolean lootReleased;
    private DamageSource killingBlow;

    /** Client: tick when the current clip arrived, and a pending GeckoLib reset. */
    public int clientClipStart;
    private boolean resetPending;

    protected GuilherminoBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.brain = level.isClientSide ? null : createBrain();
        this.xpReward = 0;
        setPersistenceRequired();
        noCulling = true; // a 5-block robot with beams and missile pods: never culled by its own small box
    }

    /** The server AI of this form. */
    protected abstract Brain createBrain();

    /** Locomotion clips of this form. */
    public abstract Clip idleClip();
    public abstract Clip walkClip();
    /** Run clip, or null when the form cannot run. */
    public abstract Clip runClip();
    /** Death sequence length on the server, ticks (the clip plus a hold for the last effects). */
    protected abstract int deathTicks();
    /** True for the final form: loot and experience are released at the end of its death. */
    protected abstract boolean dropsLoot();

    // ---------------------------------------------------------------- synced state

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ANIM, -1);
        builder.define(ANIM_SEQ, 0);
        builder.define(FLAGS, (byte) 0);
    }

    /** Server: shows `clip` from its first frame on every client (also restarts the same clip). */
    public void playClip(Clip clip) {
        entityData.set(ANIM, Anims.index(clip));
        entityData.set(ANIM_SEQ, entityData.get(ANIM_SEQ) + 1);
    }

    /** Server: back to locomotion (idle/walk). */
    public void clearClip() {
        if (entityData.get(ANIM) == -1) return;
        entityData.set(ANIM, -1);
        entityData.set(ANIM_SEQ, entityData.get(ANIM_SEQ) + 1);
    }

    /** The clip being shown, or null during locomotion. */
    public Clip clip() {
        int i = entityData.get(ANIM);
        return i >= 0 && i < Anims.ALL.length ? Anims.ALL[i] : null;
    }

    public boolean flag(int bit) { return (entityData.get(FLAGS) & bit) != 0; }

    public void setFlag(int bit, boolean on) {
        byte old = entityData.get(FLAGS);
        byte now = (byte) (on ? old | bit : old & ~bit);
        if (now != old) entityData.set(FLAGS, now);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide && ANIM_SEQ.equals(key)) {
            clientClipStart = tickCount;
            resetPending = true;
        }
    }

    /** Client: ticks since the current clip arrived (partial included). */
    public float clipAge(float partial) { return tickCount - clientClipStart + partial; }

    // ---------------------------------------------------------------- GeckoLib

    public static RawAnimation raw(Clip clip) {
        return RAW.computeIfAbsent(clip, c -> switch (c.loop()) {
            case LOOP -> RawAnimation.begin().thenLoop(c.name());
            case HOLD -> RawAnimation.begin().thenPlayAndHold(c.name());
            case ONCE -> RawAnimation.begin().thenPlay(c.name());
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Transition of 2 ticks: Brain.LEAD accounts for it on the server.
        controllers.add(new AnimationController<GuilherminoBoss>(this, "main", Brain.TRANSITION, state -> {
            Clip clip = clip();
            if (resetPending) {
                resetPending = false;
                state.getController().forceAnimationReset();
            }
            if (clip != null) return state.setAndContinue(raw(clip));
            if (flag(F_RUNNING) && runClip() != null) return state.setAndContinue(raw(runClip()));
            if (state.isMoving()) return state.setAndContinue(raw(walkClip()));
            return state.setAndContinue(raw(idleClip()));
        }).setCustomInstructionKeyframeHandler(event -> {
            for (String part : event.getKeyframeData().getInstructions().split(";")) {
                String s = part.trim();
                if (!s.isEmpty()) instructionSink.accept(this, s);
            }
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // ---------------------------------------------------------------- server life

    public Brain brain() { return brain; }

    public UUID encounterId() { return encounterId; }
    public void setEncounterId(UUID id) { this.encounterId = id; }

    /** The encounter this boss belongs to, recreated from the saved id after a restart. */
    public Encounter encounter() {
        return level() instanceof ServerLevel server ? Encounters.of(this, server) : null;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (brain != null) brain.tick();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 20 == 0) encounter(); // registers or refreshes the encounter
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) return super.hurt(source, amount);
        Entity attacker = source.getEntity();
        if (attacker instanceof GuilherminoBoss || source.getDirectEntity() instanceof GuilherminoMissile) return false;
        if (flag(F_INVULNERABLE) || (brain != null && brain.invulnerable())) {
            playSound(SoundEvents.ANVIL_LAND, .5f, 1.6f);
            return false;
        }
        float scaled = brain == null ? amount : amount * brain.damageMultiplier(source);
        boolean hit = super.hurt(source, scaled);
        if (hit && brain != null) brain.onHurt(source, scaled);
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        if (isRemoved() || dead) return;
        killingBlow = source;
        super.die(source); // marks dead; dropAllDeathLoot below holds the loot back
        if (brain != null) brain.startDeath();
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        if (lootReleased) super.dropAllDeathLoot(level, source);
    }

    /** Server: keeps the body during the death sequence, releases loot and experience once, then removes it. */
    @Override
    protected void tickDeath() {
        ++deathTime;
        if (level().isClientSide) return;
        if (brain != null) brain.tick();
        if (deathTime >= deathTicks() && !isRemoved()) {
            if (dropsLoot() && !lootReleased && level() instanceof ServerLevel server) {
                lootReleased = true;
                lastHurtByPlayerTime = Math.max(lastHurtByPlayerTime, 100); // the death took longer than vanilla's window
                dropAllDeathLoot(server, killingBlow != null ? killingBlow : damageSources().generic());
            }
            Encounter e = encounter();
            if (e != null) e.bossGone(this, true);
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}
    @Override public boolean isPushable() { return false; }
    @Override public void push(Entity entity) {}
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean requiresCustomPersistence() { return true; }
    @Override public boolean canChangeDimensions(Level from, Level to) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public int getMaxHeadYRot() { return 35; }
    @Override public int getHeadRotSpeed() { return 6; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.IRON_GOLEM_HURT; }
    @Override protected SoundEvent getDeathSound() { return null; }
    @Override protected float getSoundVolume() { return 2.2f; }
    @Override public float getVoicePitch() { return .55f; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (encounterId != null) tag.putUUID("Encounter", encounterId);
        tag.putByte("BtFlags", (byte) (entityData.get(FLAGS) & (F_OVERLOADED | F_REACTOR_DARK)));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Encounter")) encounterId = tag.getUUID("Encounter");
        entityData.set(FLAGS, tag.getByte("BtFlags"));
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && brain != null) brain.interrupt();
        super.remove(reason);
    }
}
