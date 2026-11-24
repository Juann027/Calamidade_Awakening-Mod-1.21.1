package dev.bossestesting.entity;

import dev.bossestesting.anim.Anims;
import dev.bossestesting.combat.Hits;
import dev.bossestesting.fx.Fx;
import dev.bossestesting.fx.Sfx;
import dev.bossestesting.registry.ModEntities;
import dev.calamidade_awakening.api.v1.EffectSource;
import dev.calamidade_awakening.api.v1.Placement;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Missile of the barrage, with its own model, glowmask and animations (launch → flight). The server owns the
 * position, guidance, collision and explosion; the client only draws it (rotation follows the velocity) and its
 * exhaust. Launch: {@link Anims#MS_LAUNCH} lasts until its last keyframe (9 ticks, the clip declares 8), then
 * guidance starts with a limited turn rate.
 */
public class GuilherminoMissile extends Projectile implements GeoEntity {
    private static final RawAnimation ANIM = RawAnimation.begin()
        .then(Anims.MS_LAUNCH.name(), Animation.LoopType.PLAY_ONCE).thenLoop(Anims.MS_FLIGHT.name());
    /** Ticks of the launch phase: the effective end of the launch clip. */
    public static final int LAUNCH_TICKS = Anims.MS_LAUNCH.endTick();
    static final int LIFETIME = 140;
    static final double MAX_TURN = Math.toRadians(5.5), TOP_SPEED = 1.55;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Vec3 aim = Vec3.ZERO;
    private LivingEntity homing;
    private int life;
    private boolean exploded;

    public GuilherminoMissile(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
        noCulling = true;
    }

    public GuilherminoMissile(ServerLevel level, Entity owner, Vec3 start, Vec3 launchDir, Vec3 aim, LivingEntity homing, int seed) {
        this(ModEntities.MISSILE.get(), level);
        setOwner(owner);
        setPos(start);
        this.aim = aim;
        this.homing = homing;
        setDeltaMovement(launchDir.normalize().scale(.45 + (seed % 3) * .05));
        orient(getDeltaMovement());
        xRotO = getXRot();
        yRotO = getYRot();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        if (!level().isClientSide) {
            life++;
            if (life > LAUNCH_TICKS) v = guide(v);
            else v = v.add(0, .02, 0).scale(1.04);
            setDeltaMovement(v);
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, e -> Hits.missileCanHit(e, getOwner()));
            if (hit.getType() != HitResult.Type.MISS) {
                setPos(hit.getLocation());
                explode(hit.getType() == HitResult.Type.BLOCK);
                return;
            }
            if (life > LIFETIME) { explode(false); return; }
        }
        setPos(position().add(v));
        orient(v);
        if (level().isClientSide) clientTrail(v);
    }

    /** Proportional guidance toward the aim point (re-aimed at the target while it is far), limited turn rate. */
    private Vec3 guide(Vec3 v) {
        if (homing != null && homing.isAlive() && homing.distanceToSqr(this) > 64)
            aim = aim.lerp(homing.position(), .04);
        Vec3 want = aim.subtract(position());
        double speed = Math.min(TOP_SPEED, v.length() + .06);
        Vec3 dir = v.normalize(), to = want.normalize();
        double angle = Math.acos(Math.clamp(dir.dot(to), -1, 1));
        Vec3 next = angle <= MAX_TURN ? to : slerp(dir, to, MAX_TURN / angle);
        return next.scale(speed);
    }

    private static Vec3 slerp(Vec3 a, Vec3 b, double t) {
        double dot = Math.clamp(a.dot(b), -1, 1), theta = Math.acos(dot) * t;
        Vec3 rel = b.subtract(a.scale(dot));
        if (rel.lengthSqr() < 1e-8) return a;
        return a.scale(Math.cos(theta)).add(rel.normalize().scale(Math.sin(theta))).normalize();
    }

    /** Mob convention: yaw 0 = south, pitch positive = down (the renderer turns the model the same way). */
    private void orient(Vec3 v) {
        if (v.lengthSqr() < 1e-6) return;
        setYRot((float) Math.toDegrees(Math.atan2(-v.x, v.z)));
        setXRot((float) -Math.toDegrees(Math.atan2(v.y, v.horizontalDistance())));
    }

    private void explode(boolean ground) {
        if (exploded || !(level() instanceof ServerLevel server)) return;
        exploded = true;
        Vec3 at = position();
        Entity owner = getOwner();
        // Terrain only for ground hits (the variant follows destruction settings and mobGriefing); visuals always.
        Placement where = Placement.at(at, getYRot(), 0).withSource(EffectSource.script());
        if (ground && owner != null) Fx.MISSILE_IMPACT.start(server, owner, Placement.at(at, getYRot(), 0));
        else dev.calamidade_awakening.api.v1.Effects.start(Fx.MISSILE_IMPACT.base, server, where);
        Hits.area(owner != null ? owner : this, at, 3.6, 11, .3f, 1.3, .55, Hits.blast(owner != null ? owner : this, this));
        Sfx.explosion(server, at, 2.2f);
        Sfx.burst(server, ParticleTypes.EXPLOSION, at, 2, .4, .3, 0);
        Sfx.burst(server, Sfx.ember(0xffff8a2a), at, 26, .5, .4, .35);
        Sfx.burst(server, Sfx.smoke(0xff2a2420), at.add(0, .6, 0), 14, .6, .4, .03);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.DRAGON_FIREBALL_EXPLODE, getSoundSource(), 1.2f, 1.4f);
        discard();
    }

    /** Client: smoke and embers behind the exhaust (the exhaust bone sits 0.5 block behind the center). */
    private void clientTrail(Vec3 v) {
        Vec3 back = v.lengthSqr() > 1e-6 ? v.normalize().scale(-.55) : Vec3.ZERO;
        Vec3 p = position().add(0, .3, 0).add(back);
        var r = level().random;
        level().addParticle(Sfx.smoke(0xff4a4440), p.x, p.y, p.z, back.x * .1 + r.nextGaussian() * .02, .02, back.z * .1 + r.nextGaussian() * .02);
        level().addParticle(Sfx.ember(0xffffb040), p.x, p.y, p.z, back.x * .3, back.y * .3, back.z * .3);
        if (tickCount % 2 == 0) level().addParticle(Sfx.arc(Fx.P2_HOT), p.x, p.y, p.z, 0, 0, 0);
    }

    /** Server: removes the missile without exploding (end of the fight). */
    public void fizzle() { discard(); }

    @Override protected boolean canHitEntity(Entity entity) { return Hits.missileCanHit(entity, getOwner()); }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 256 * 256; }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); }

    /** Missiles are not saved: a reload never leaves one hanging in the air. */
    @Override public boolean shouldBeSaved() { return false; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<GuilherminoMissile>(this, "main", 0, state -> state.setAndContinue(ANIM)));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
