package dev.calamidade_awakening.api.v1;

import dev.calamidade_awakening.api.Basis;
import dev.calamidade_awakening.api.TriggerLogic;
import dev.calamidade_awakening.api.VisualKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Objects;
import java.util.Optional;

/**
 * Ready-made triggers that start effects from items, block entities and entities (0.10.0). Common code only (no
 * GeckoLib or other library): call them from your {@code Item}, {@code BlockEntity} or mob code. Every trigger is an
 * immutable configuration (each method returns a copy), so keep it in a {@code static final} field; it builds a
 * {@link Placement} with the right {@link EffectSource source}, orientation and cooldown and calls
 * {@link Effects#start(ResourceLocation, ServerLevel, Placement)}. Starts above the server limits are refused without
 * error (empty Optional, or {@code false}). The effect only has to be registered when the trigger fires (an unknown
 * effect then throws {@link IllegalArgumentException}, like {@code Effects.start}), so a trigger can be created before
 * {@link Effects#register} runs.
 *
 * <ul>
 * <li>{@link #item(ResourceLocation)}: use and attack of an item, with the user as caster, a cooldown (also shown on
 * the hotbar) and an optional durability cost.</li>
 * <li>{@link #blockEntity(ResourceLocation)}: a component for block entities, with the modes PERIODIC, REDSTONE and
 * PROXIMITY; its instance ends when the block is broken or its chunk unloads.</li>
 * <li>{@link #entity(ResourceLocation)}: an instance anchored to an entity, turned with its body or toward a target,
 * for mob attacks.</li>
 * </ul>
 *
 * <pre>{@code
 * static final EffectTriggers.ItemTrigger RAIO = EffectTriggers.item(ID).cooldown(30).durability(1);
 * @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
 *     return RAIO.use(level, player, hand);
 * }
 * }</pre>
 */
public final class EffectTriggers {
    private EffectTriggers() {}

    /** Item trigger for {@code effect} (registered with {@link Effects#register} before it fires): spot EYES, no cooldown, no durability cost. */
    public static ItemTrigger item(ResourceLocation effect) { return new ItemTrigger(effect(effect), ItemTrigger.Spot.EYES, 32, 0, 0, 0, 0, 0); }

    /** Block entity trigger for {@code effect}: PERIODIC every 40 ticks, 1 block above the block, turned by its facing. */
    public static BlockTrigger blockEntity(ResourceLocation effect) {
        return new BlockTrigger(effect(effect), BlockTrigger.Mode.PERIODIC, 40, 8, 0, 1, 0, true, 0, 0, 0);
    }

    /** Entity trigger for {@code effect}: anchored at the feet, yaw following the body, no cooldown. */
    public static EntityTrigger entity(ResourceLocation effect) { return new EntityTrigger(effect(effect), 0, 0, 0, true, 0, 0); }

    // The effect is looked up when the trigger fires, not here: triggers live in static final fields, which are
    // initialized when their class loads, often before the mod constructor registers the effect.
    private static ResourceLocation effect(ResourceLocation id) {
        return Objects.requireNonNull(id, "effect");
    }

    private static void finite(double value, String what) {
        if (!Double.isFinite(value) || Math.abs(value) > Point.MAX_OFFSET)
            throw new IllegalArgumentException(what + " must be finite and within " + Point.MAX_OFFSET + " blocks");
    }

    private static int cooldownTicks(int ticks) {
        if (ticks < 0 || ticks > 72000) throw new IllegalArgumentException("Expected a cooldown in 0..72000 ticks: " + ticks);
        return ticks;
    }

    /**
     * Starts effects from an item (0.10.0): call {@link #use} from {@code Item#use} and {@link #attack} from
     * {@code Item#hurtEnemy}, or {@link #fire} from anywhere on the server. The user is the
     * {@link EffectSource source} (PLAYER for players) and, when a player, the caster (camera, cues, caster points).
     * After an accepted start the item loses {@link #durability} points (not in creative) and, for players, shows the
     * {@link #cooldown} on the hotbar; the cooldown itself is the engine's, per source and effect.
     *
     * <pre>{@code
     * public class Cajado extends Item {
     *     static final EffectTriggers.ItemTrigger RAIO = EffectTriggers.item(RAIO_ID).cooldown(30).durability(1);
     *     static final EffectTriggers.ItemTrigger GOLPE = EffectTriggers.item(GOLPE_ID).at(EffectTriggers.ItemTrigger.Spot.TARGET);
     *     @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
     *         return RAIO.use(level, player, hand);
     *     }
     *     @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
     *         GOLPE.attack(stack, target, attacker);
     *         return super.hurtEnemy(stack, target, attacker);
     *     }
     * }
     * }</pre>
     */
    public static final class ItemTrigger {
        /** Where the instance starts and how it is turned. */
        public enum Spot {
            /** Anchored to the user's feet; the yaw follows the user's body. For auras and effects carried along. */
            CASTER,
            /** Fixed at the user's eyes, turned by the user's look (yaw and pitch). For bolts: {@code Point.facing(0, 0, 24)} is straight ahead. */
            EYES,
            /** Fixed at the first block the user looks at, up to the reach (the full reach when nothing is hit), turned by the look's yaw. */
            AIM,
            /** Fixed at the middle of the hit entity, turned from the user's eyes toward it. Without a target (use), AIM. */
            TARGET
        }

        private final ResourceLocation effect;
        private final Spot spot;
        private final double reach, right, up, forward;
        private final int cooldown, durability;

        private ItemTrigger(ResourceLocation effect, Spot spot, double reach, double right, double up, double forward,
                            int cooldown, int durability) {
            this.effect = effect; this.spot = spot; this.reach = reach; this.right = right; this.up = up;
            this.forward = forward; this.cooldown = cooldown; this.durability = durability;
        }

        /** Copy that starts at this spot. */
        public ItemTrigger at(Spot spot) {
            return new ItemTrigger(effect, Objects.requireNonNull(spot), reach, right, up, forward, cooldown, durability);
        }

        /** Copy with this aim reach for {@link Spot#AIM}, 0.5..256 blocks (32 by default). */
        public ItemTrigger reach(double blocks) {
            if (!(blocks >= .5 && blocks <= Point.MAX_REACH)) throw new IllegalArgumentException("Expected a reach in 0.5..256 blocks");
            return new ItemTrigger(effect, spot, blocks, right, up, forward, cooldown, durability);
        }

        /** Copy that moves the start by {@code right}, {@code up} and {@code forward} blocks in the instance frame. */
        public ItemTrigger offset(double right, double up, double forward) {
            finite(right, "Offset"); finite(up, "Offset"); finite(forward, "Offset");
            return new ItemTrigger(effect, spot, reach, right, up, forward, cooldown, durability);
        }

        /** Copy with a cooldown per user and effect, 0..72000 ticks (also put on the item for players). */
        public ItemTrigger cooldown(int ticks) {
            return new ItemTrigger(effect, spot, reach, right, up, forward, cooldownTicks(ticks), durability);
        }

        /** Copy that costs this many durability points per accepted start, 0..1024 (damageable items, not in creative). */
        public ItemTrigger durability(int points) {
            if (points < 0 || points > 1024) throw new IllegalArgumentException("Expected a durability cost in 0..1024");
            return new ItemTrigger(effect, spot, reach, right, up, forward, cooldown, points);
        }

        /** Effect this trigger starts. */
        public ResourceLocation effect() { return effect; }
        /** Where it starts. */
        public Spot spot() { return spot; }
        /** Cooldown in ticks, 0 for none. */
        public int cooldown() { return cooldown; }
        /** Durability cost per start. */
        public int durability() { return durability; }

        /**
         * For {@code Item#use}: on the server starts the effect and returns {@code consume} (or {@code fail} when
         * refused); on the client returns {@code success}, so the hand swings while the server decides.
         */
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide()) return InteractionResultHolder.sidedSuccess(stack, true);
            return fire(player, hand, null).isPresent() ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
        }

        /**
         * For {@code Item#hurtEnemy}: starts the effect with {@code target} (spot TARGET puts it on the target), using
         * the main hand. Returns true when an instance started. Does nothing on the client.
         */
        public boolean attack(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            if (attacker.level().isClientSide()) return false;
            return fire(attacker, InteractionHand.MAIN_HAND, target).isPresent();
        }

        /**
         * Starts the effect for {@code user} holding the item in {@code hand}, with an optional {@code target} (null for
         * none). Server only: empty on the client, during the cooldown, above the limits or when the chunk is not
         * loaded. Applies the durability cost and the hotbar cooldown after an accepted start.
         */
        public Optional<Effects.Handle> fire(LivingEntity user, InteractionHand hand, Entity target) {
            if (!(user.level() instanceof ServerLevel level)) return Optional.empty();
            Placement placement = switch (spot) {
                case CASTER -> Placement.on(user).shifted(right, up, forward).followingBody();
                case EYES -> Placement.at(user.getEyePosition()).withLookOf(user).shifted(right, up, forward);
                case AIM -> aim(user);
                case TARGET -> target == null ? aim(user) : strike(user, target);
            };
            if (user instanceof ServerPlayer player) placement = placement.withCaster(player);
            var started = Effects.start(effect, level, placement.withSource(EffectSource.entity(user)).withCooldown(cooldown));
            if (started.isPresent()) {
                ItemStack stack = user.getItemInHand(hand);
                if (durability > 0 && stack.isDamageableItem())
                    stack.hurtAndBreak(durability, user, LivingEntity.getSlotForHand(hand));
                if (cooldown > 0 && user instanceof Player player && !stack.isEmpty())
                    player.getCooldowns().addCooldown(stack.getItem(), cooldown);
            }
            return started;
        }

        private Placement aim(LivingEntity user) {
            Vec3 eye = user.getEyePosition();
            Vec3 end = eye.add(user.getLookAngle().scale(reach));
            var hit = user.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, user));
            Vec3 at = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
            return Placement.at(at, user.getYRot(), 0).shifted(right, up, forward);
        }

        private Placement strike(LivingEntity user, Entity target) {
            Vec3 middle = target.getBoundingBox().getCenter();
            Vec3 d = middle.subtract(user.getEyePosition());
            return Placement.at(middle, Basis.yawTowards(d.x, d.z), Basis.pitchTowards(d.x, d.y, d.z)).shifted(right, up, forward);
        }
    }

    /**
     * Starts effects from a block entity (0.10.0). Create a {@link Component} per block entity with {@link #attach} and
     * call {@link Component#tick()} from its server ticker; the component reads the redstone signal or looks for players
     * and starts, holds or releases the effect by its {@link Mode}. The instance is bound to the block entity
     * ({@link Placement#boundTo}): it ends when the block is broken or replaced and when its chunk unloads, even if the
     * component is never told. The source is the block (by default at most 2 live instances per block).
     *
     * <p>The center is the bottom center of the block plus {@link #offset} (right, up, forward), turned by the block's
     * {@code facing} or {@code horizontal_facing} property when it has one (yaw 0 faces south, as {@code Direction.toYRot}),
     * or by a fixed {@link #yaw}. A sustained effect (timeline with sustain) is held while the trigger is on and released
     * when it goes off; other effects start again every {@link #period}.
     *
     * <pre>{@code
     * public class PilarEntity extends BlockEntity {
     *     static final EffectTriggers.BlockTrigger PULSO = EffectTriggers.blockEntity(ID).redstone().period(20);
     *     private final EffectTriggers.BlockTrigger.Component fx = PULSO.attach(this);
     *     public static void serverTick(Level level, BlockPos pos, BlockState state, PilarEntity self) { self.fx.tick(); }
     * }
     * // In the block: getTicker returns PilarEntity::serverTick on the server (createTickerHelper).
     * }</pre>
     */
    public static final class BlockTrigger {
        /** When the block starts its effect. */
        public enum Mode {
            /** Every {@link #period} ticks while the block is loaded (a sustained effect is held instead). */
            PERIODIC,
            /** On the rising edge of a redstone signal into the block; again every {@link #period} while powered (0: once). */
            REDSTONE,
            /** When a player (not a spectator) comes within {@link #radius} blocks; again every {@link #period} while one stays. */
            PROXIMITY
        }

        private final ResourceLocation effect;
        private final Mode mode;
        private final int period, cooldown;
        private final double radius, right, up, forward;
        private final boolean useFacing;
        private final float yaw, pitch;

        private BlockTrigger(ResourceLocation effect, Mode mode, int period, double radius, double right, double up,
                             double forward, boolean useFacing, float yaw, float pitch, int cooldown) {
            this.effect = effect; this.mode = mode; this.period = period; this.radius = radius; this.right = right;
            this.up = up; this.forward = forward; this.useFacing = useFacing; this.yaw = yaw; this.pitch = pitch;
            this.cooldown = cooldown;
        }

        /** Copy in this mode. */
        public BlockTrigger mode(Mode mode) {
            return new BlockTrigger(effect, Objects.requireNonNull(mode), period, radius, right, up, forward, useFacing, yaw, pitch, cooldown);
        }

        /** Copy in PERIODIC mode every {@code ticks} ticks, 1..72000. */
        public BlockTrigger periodic(int ticks) { return mode(Mode.PERIODIC).period(ticks); }

        /** Copy in REDSTONE mode (period 0: once per rising edge, unless {@link #period} says otherwise). */
        public BlockTrigger redstone() { return mode(Mode.REDSTONE).period(0); }

        /** Copy in PROXIMITY mode with this radius, 0.5..128 blocks (period 0: once per arrival). */
        public BlockTrigger proximity(double radius) { return mode(Mode.PROXIMITY).radius(radius).period(0); }

        /**
         * Copy with this period in ticks: PERIODIC 1..72000; REDSTONE and PROXIMITY 0..72000, where 0 starts once per
         * rising edge and more repeats while the trigger stays on.
         */
        public BlockTrigger period(int ticks) {
            if (ticks < (mode == Mode.PERIODIC ? 1 : 0) || ticks > 72000)
                throw new IllegalArgumentException("Expected a period in " + (mode == Mode.PERIODIC ? 1 : 0) + "..72000 ticks: " + ticks);
            return new BlockTrigger(effect, mode, ticks, radius, right, up, forward, useFacing, yaw, pitch, cooldown);
        }

        /** Copy with this PROXIMITY radius, 0.5..128 blocks. */
        public BlockTrigger radius(double blocks) {
            if (!(blocks >= .5 && blocks <= 128)) throw new IllegalArgumentException("Expected a radius in 0.5..128 blocks");
            return new BlockTrigger(effect, mode, period, blocks, right, up, forward, useFacing, yaw, pitch, cooldown);
        }

        /** Copy with the center moved {@code right}, {@code up}, {@code forward} blocks from the block's bottom center (default 0, 1, 0). */
        public BlockTrigger offset(double right, double up, double forward) {
            finite(right, "Offset"); finite(up, "Offset"); finite(forward, "Offset");
            return new BlockTrigger(effect, mode, period, radius, right, up, forward, useFacing, yaw, pitch, cooldown);
        }

        /** Copy turned by a fixed yaw in degrees instead of the block's facing. */
        public BlockTrigger yaw(float degrees) {
            if (!Float.isFinite(degrees)) throw new IllegalArgumentException("Yaw must be finite");
            return new BlockTrigger(effect, mode, period, radius, right, up, forward, false, Basis.wrapYaw(degrees), pitch, cooldown);
        }

        /** Copy with this pitch in degrees, [-90, 90] (positive looks down). */
        public BlockTrigger pitch(float degrees) {
            if (!(degrees >= -90 && degrees <= 90)) throw new IllegalArgumentException("Expected a pitch in [-90, 90]");
            return new BlockTrigger(effect, mode, period, radius, right, up, forward, useFacing, yaw, degrees, cooldown);
        }

        /** Copy with a cooldown per block and effect, 0..72000 ticks. */
        public BlockTrigger cooldown(int ticks) {
            return new BlockTrigger(effect, mode, period, radius, right, up, forward, useFacing, yaw, pitch, cooldownTicks(ticks));
        }

        /** Effect this trigger starts. */
        public ResourceLocation effect() { return effect; }
        /** Mode. */
        public Mode mode() { return mode; }
        /** Period in ticks. */
        public int period() { return period; }
        /** PROXIMITY radius in blocks. */
        public double radius() { return radius; }
        /** Cooldown in ticks, 0 for none. */
        public int cooldown() { return cooldown; }

        /** New component for one block entity; keep it in a field of that block entity. */
        public Component attach(BlockEntity blockEntity) { return new Component(this, Objects.requireNonNull(blockEntity)); }

        /** Yaw of a block state: its facing (horizontal facing first), or 0. */
        float facingYaw(BlockState state) {
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot();
            if (state.hasProperty(BlockStateProperties.FACING)) {
                var direction = state.getValue(BlockStateProperties.FACING);
                return direction.getAxis().isHorizontal() ? direction.toYRot() : 0;
            }
            return 0;
        }

        /**
         * Effect state of one block entity. Call {@link #tick()} on the server every tick; everything else is optional.
         * Not thread-safe: use it from the server thread.
         */
        public static final class Component {
            private final BlockTrigger trigger;
            private final BlockEntity blockEntity;
            private final TriggerLogic logic = new TriggerLogic();
            private Effects.Handle handle;

            private Component(BlockTrigger trigger, BlockEntity blockEntity) {
                this.trigger = trigger; this.blockEntity = blockEntity;
            }

            /** Server tick: reads the input and starts, holds or releases the effect. No-op on the client and while detached. */
            public void tick() {
                if (!(blockEntity.getLevel() instanceof ServerLevel level) || blockEntity.isRemoved()) return;
                var pos = blockEntity.getBlockPos();
                boolean active = switch (trigger.mode) {
                    case PERIODIC -> true;
                    case REDSTONE -> level.hasNeighborSignal(pos);
                    case PROXIMITY -> level.hasNearbyAlivePlayer(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, trigger.radius);
                };
                boolean running = handle != null && handle.alive();
                if (!running) handle = null;
                VisualKind kind = VisualKind.byId(VisualKind.localId(trigger.effect.toString()));
                if (kind == null) throw new IllegalArgumentException("Unknown effect " + trigger.effect + " in a block trigger");
                var action = logic.step(TriggerLogic.Mode.valueOf(trigger.mode.name()), active, kind.sustained(),
                    running, trigger.period, level.getGameTime());
                switch (action) {
                    case START -> start(level).ifPresent(started -> handle = started);
                    case RELEASE -> { if (handle != null) handle.release(); }
                    case NONE -> {}
                }
            }

            private Optional<Effects.Handle> start(ServerLevel level) {
                float yaw = trigger.useFacing ? trigger.facingYaw(blockEntity.getBlockState()) : trigger.yaw;
                Vec3 base = Vec3.atBottomCenterOf(blockEntity.getBlockPos());
                Placement placement = Placement.at(base, yaw, trigger.pitch).shifted(trigger.right, trigger.up, trigger.forward)
                    .boundTo(blockEntity).withCooldown(trigger.cooldown);
                return Effects.start(trigger.effect, level, placement);
            }

            /** Stops the running instance now. Returns true when one was running. */
            public boolean stop() {
                var running = handle;
                handle = null;
                return running != null && running.stop();
            }

            /** Handle of the instance this component started last, while it runs. */
            public Optional<Effects.Handle> handle() {
                return handle != null && handle.alive() ? Optional.of(handle) : Optional.empty();
            }

            /** Input seen on the last tick: powered (REDSTONE), a player near (PROXIMITY), always true for PERIODIC. */
            public boolean active() { return logic.active(); }

            /** Block entity this component serves. */
            public BlockEntity blockEntity() { return blockEntity; }
        }
    }

    /**
     * Starts effects anchored to an entity (0.10.0), for mob attacks. {@link #fire(Entity)} turns the instance with the
     * entity's body (living entities) every tick and frame; {@link #fire(Entity, Entity)} turns it toward a target at
     * the moment of the attack and keeps that direction while the instance follows the entity. The entity is the
     * {@link EffectSource source} (by default at most 4 live instances per mob) and the anchor: cues receive it as
     * {@code anchor}, {@code Point.anchor*} resolve to it and the instance ends when it is removed.
     *
     * <pre>{@code
     * static final EffectTriggers.EntityTrigger FEIXE = EffectTriggers.entity(ID).offset(0, 1.3, .4).cooldown(20);
     * // NeoForge.EVENT_BUS: LivingDamageEvent.Post
     * if (event.getSource().getEntity() instanceof Zombie zombie) FEIXE.fire(zombie, event.getEntity());
     * }</pre>
     *
     * <p><b>Animated mobs (GeckoLib or any rig).</b> Animation keyframes run on the client, but effects start on the
     * server. Start the effect on the server in the same tick the attack animation starts (for GeckoLib, where you call
     * {@code triggerAnim}) and write the effect's {@link Timeline} with the animation's timing: a marker at the hit
     * keyframe for the damage cue, phases for wind-up and recovery. The Calamidade never depends on GeckoLib; the
     * animation code stays in your mod (see {@code docs/INTEGRACAO-0.10.md}).
     */
    public static final class EntityTrigger {
        private final ResourceLocation effect;
        private final double right, up, forward;
        private final boolean followBody;
        private final float pitch;
        private final int cooldown;

        private EntityTrigger(ResourceLocation effect, double right, double up, double forward, boolean followBody,
                              float pitch, int cooldown) {
            this.effect = effect; this.right = right; this.up = up; this.forward = forward; this.followBody = followBody;
            this.pitch = pitch; this.cooldown = cooldown;
        }

        /** Copy whose center is {@code right}, {@code up}, {@code forward} blocks from the entity's feet, in its body frame. */
        public EntityTrigger offset(double right, double up, double forward) {
            finite(right, "Offset"); finite(up, "Offset"); finite(forward, "Offset");
            return new EntityTrigger(effect, right, up, forward, followBody, pitch, cooldown);
        }

        /** Copy whose yaw follows the body ({@code true}, default) or stays as it was at the start ({@code false}). */
        public EntityTrigger followBody(boolean follow) {
            return new EntityTrigger(effect, right, up, forward, follow, pitch, cooldown);
        }

        /** Copy with this pitch for {@link #fire(Entity)}, degrees in [-90, 90] (positive looks down). */
        public EntityTrigger pitch(float degrees) {
            if (!(degrees >= -90 && degrees <= 90)) throw new IllegalArgumentException("Expected a pitch in [-90, 90]");
            return new EntityTrigger(effect, right, up, forward, followBody, degrees, cooldown);
        }

        /** Copy with a cooldown per entity and effect, 0..72000 ticks. */
        public EntityTrigger cooldown(int ticks) {
            return new EntityTrigger(effect, right, up, forward, followBody, pitch, cooldownTicks(ticks));
        }

        /** Effect this trigger starts. */
        public ResourceLocation effect() { return effect; }
        /** Cooldown in ticks, 0 for none. */
        public int cooldown() { return cooldown; }

        /** Starts the effect on {@code entity}, turned with its body (or with its yaw at the start). Server only. */
        public Optional<Effects.Handle> fire(Entity entity) {
            if (!(entity.level() instanceof ServerLevel level)) return Optional.empty();
            Placement placement = Placement.on(entity).shifted(right, up, forward);
            placement = followBody ? placement.withOrientation(0, pitch).followingBody()
                : placement.withOrientation(entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot(), pitch);
            return start(level, entity, placement);
        }

        /**
         * Starts the effect on {@code entity}, turned toward {@code target} (yaw and pitch from the center to the
         * target's middle) at this moment; the instance keeps following the entity. The offset stays level (body yaw
         * only), so the center does not tilt with the aim. Server only.
         */
        public Optional<Effects.Handle> fire(Entity entity, Entity target) {
            if (!(entity.level() instanceof ServerLevel level)) return Optional.empty();
            Objects.requireNonNull(target, "target");
            Vec3 to = target.getBoundingBox().getCenter();
            // Center with the offset turned by the yaw toward the target only, then the aim from there.
            float yaw0 = Basis.yawTowards(to.x - entity.getX(), to.z - entity.getZ());
            Basis level0 = Basis.of(yaw0, 0);
            double ox = level0.worldX(-right, up, forward), oy = level0.worldY(-right, up, forward), oz = level0.worldZ(-right, up, forward);
            Vec3 d = to.subtract(entity.position().add(ox, oy, oz));
            float yaw = d.horizontalDistanceSqr() < 1e-8 ? yaw0 : Basis.yawTowards(d.x, d.z);
            float aim = Basis.pitchTowards(d.x, d.y, d.z);
            // Local offset that the aimed frame turns into the level world offset above.
            Basis aimed = Basis.of(yaw, aim);
            Vec3 local = new Vec3(aimed.localX(ox, oy, oz), aimed.localY(ox, oy, oz), aimed.localZ(ox, oy, oz));
            return start(level, entity, Placement.on(entity, local).withOrientation(yaw, aim));
        }

        private Optional<Effects.Handle> start(ServerLevel level, Entity entity, Placement placement) {
            if (entity instanceof ServerPlayer player) placement = placement.withCaster(player);
            return Effects.start(effect, level, placement.withSource(EffectSource.entity(entity)).withCooldown(cooldown));
        }
    }
}
