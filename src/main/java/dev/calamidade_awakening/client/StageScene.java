package dev.calamidade_awakening.client;

import dev.calamidade_awakening.api.Basis;
import dev.calamidade_awakening.api.FrameBudget;
import dev.calamidade_awakening.api.FrameLimits;
import dev.calamidade_awakening.api.Quality;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.net.StageEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Independent client timeline; no framebuffers or game state mutations. */
public final class StageScene {
    /** Mark slots of the engine field shader ({@code awakening_field.fsh}). */
    static final int MAX_VOLUMES = 4;
    /**
     * {@code age} is the real age with the partial tick; {@code localAge} the timeline age (equal without sustain).
     * Progress, opacity and the impact accent follow the local age (0.7.0). {@code basis} is the instance frame of this
     * frame (0.10.0): the start orientation, or the anchor's interpolated body yaw plus the yaw offset.
     */
    public record Sample(StageEvent event, VisualKind kind, Vec3 relative, float progress,
                         float opacity, float seed, double distance, double age, double localAge, Basis basis) {
        /** World vector of a local one (x left, y up, z forward) in this instance's frame. */
        public Vec3 turn(double x, double y, double z) { return StageScene.turn(basis, x, y, z); }
        /** Public clock handed to addon layers, with the timeline sampled at the local age. */
        public dev.calamidade_awakening.api.v1.client.AddonLayers.Time time() {
            return new dev.calamidade_awakening.api.v1.client.AddonLayers.Time(progress, opacity, seed, kind.radius,
                kind.clock().at(localAge));
        }
    }
    /**
     * Selection of one frame. {@code limits} are the caps applied (0.11.2: a quality profile or the test profile);
     * {@code quality()} is the tier addons see. {@code eligible} counts the visible instances that competed for the
     * frame, so {@code eligible > effects.size()} means the caps cut something (AUTO quality, 0.11.2).
     */
    public record Plan(List<Sample> effects, List<Sample> volumes, FrameLimits limits, int eligible) {
        public Quality quality() { return limits.tier(); }
        /** True when the effect cap left visible instances out of this frame. */
        public boolean cut() { return eligible > effects.size(); }
    }
    private final Map<Long, StageEvent> active = new LinkedHashMap<>();
    /** Real release age of sustained instances, by order (0.7.0). */
    private final Map<Long, Long> released = new HashMap<>();
    private ClientLevel level;
    private long previousTick = Long.MIN_VALUE;
    private static final int[] CORE_TICKS = {4, 69, 120, 145};
    private static final String[] CORE_SOUNDS = {"core_gather", "core_tension", "core_rupture", "core_echo"};
    private static final int MAX_LIVE = 96;

    public boolean updateLevel(ClientLevel current) {
        if (level == current) return false;
        level = current;
        previousTick = current == null ? Long.MIN_VALUE : current.getGameTime() - 1;
        active.clear();
        released.clear();
        return true;
    }

    public void remove(long order) {
        active.remove(order);
        released.remove(order);
    }

    /** Release of a sustained instance, sent by the server (also to late arrivals right after the start). */
    public void release(long order, long age) {
        if (active.containsKey(order)) released.put(order, age);
    }

    /** Timeline age of an instance at a real age; equal to the real age without sustain. */
    public double localAge(StageEvent event, double realAge) {
        return VisualKind.byId(event.visual()).localAge(realAge, released.getOrDefault(event.order(), -1L));
    }

    /** Without sustain: the real age reached the duration. With sustain: the release played out. */
    private boolean expired(StageEvent event, long realAge) {
        VisualKind kind = VisualKind.byId(event.visual());
        if (!kind.sustained()) return realAge >= kind.duration;
        Long release = released.get(event.order());
        return release != null && realAge >= kind.timeline().endAge(release, kind.duration);
    }
    /** Resident instances, read-only (0.11.0: dust bursts of terrain edits). */
    public java.util.Collection<StageEvent> events() { return java.util.Collections.unmodifiableCollection(active.values()); }

    /** True while the instance with this order is resident (0.11.0, terrain state pruning). */
    public boolean has(long order) { return active.containsKey(order); }

    /** True while a HERO effect is resident on this client (0.11.2: AUTO quality never changes during one). */
    public boolean heroActive() {
        for (StageEvent effect : active.values()) if (VisualKind.byId(effect.visual()).hero()) return true;
        return false;
    }

    /** Resident sequences on this client, for diagnostics. */
    public int size() { return active.size(); }

    /** Resident sequences by source kind, in {@code EffectSource.Kind} order (0.10.0, F7). */
    public int[] sizeBySource() {
        int[] counts = new int[dev.calamidade_awakening.api.v1.EffectSource.Kind.values().length];
        for (StageEvent event : active.values()) counts[event.source().ordinal()]++;
        return counts;
    }

    private Vec3 center(StageEvent event, float partial) {
        if (event.entityId() < 0) return event.center();
        var entity = level.getEntity(event.entityId());
        if (entity == null || !entity.getUUID().equals(event.anchorUuid()) || entity.isRemoved()) return null;
        Vec3 o = event.offset();
        return entity.getPosition(partial).add(turn(basis(event, partial), o.x, o.y, o.z));
    }

    /**
     * Frame of an instance this frame (0.10.0): the start orientation, or, when the yaw follows the anchor's body, the
     * body yaw interpolated like the entity renderer does ({@code Mth.rotLerp} of {@code yBodyRotO} and
     * {@code yBodyRot}) plus the yaw offset. Falls back to the start orientation while the anchor is not loaded.
     */
    public Basis basis(StageEvent event, float partial) {
        if (!event.followBody()) return event.basis();
        var entity = anchor(event);
        if (entity == null) return event.basis();
        float body = entity instanceof net.minecraft.world.entity.LivingEntity living
            ? net.minecraft.util.Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot) : entity.getViewYRot(partial);
        return Basis.of(body + event.yaw(), event.pitch());
    }

    /** World vector of a local one (x left, y up, z forward); no work for the identity frame. */
    static Vec3 turn(Basis basis, double x, double y, double z) {
        if (basis.identity()) return new Vec3(x, y, z);
        return new Vec3(basis.worldX(x, y, z), basis.worldY(x, y, z), basis.worldZ(x, y, z));
    }

    /** Aim rays already cast this frame, by entity id and reach (several beams may share one). */
    private final Map<Long, Vec3> aims = new HashMap<>();

    /** Forgets the aim rays of the previous frame. */
    void newFrame() { aims.clear(); }

    /**
     * World position of a {@link dev.calamidade_awakening.api.v1.Point} of an instance (0.9.0), or null when the
     * entity it needs (anchor or caster) is not loaded on this client. {@code center} is the interpolated center and
     * {@code basis} the instance frame (0.10.0), which turns the offsets of center points.
     */
    public Vec3 point(StageEvent event, Vec3 center, Basis basis, dev.calamidade_awakening.api.v1.Point point, float partial) {
        var source = point.source();
        if (source == dev.calamidade_awakening.api.v1.Point.Source.CENTER) return center.add(turn(basis, point.x(), point.y(), point.z()));
        if (source == dev.calamidade_awakening.api.v1.Point.Source.WORLD) return new Vec3(point.x(), point.y(), point.z());
        net.minecraft.world.entity.Entity entity = source.caster() ? caster(event) : anchor(event);
        if (entity == null) return null;
        if (source.aim()) {
            long key = ((long) entity.getId() << 32) ^ Double.doubleToLongBits(point.reach());
            Vec3 cached = aims.get(key);
            if (cached != null) return cached;
            Vec3 eye = entity.getEyePosition(partial);
            Vec3 end = eye.add(entity.getViewVector(partial).scale(point.reach()));
            var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, entity));
            Vec3 at = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? end : hit.getLocation();
            aims.put(key, at);
            return at;
        }
        Vec3 base = source.eye() ? entity.getEyePosition(partial) : entity.getPosition(partial);
        return base.add(point.x(), point.y(), point.z());
    }

    private net.minecraft.world.entity.Entity anchor(StageEvent event) {
        if (event.entityId() < 0 || level == null) return null;
        var entity = level.getEntity(event.entityId());
        return entity == null || entity.isRemoved() || !entity.getUUID().equals(event.anchorUuid()) ? null : entity;
    }

    private net.minecraft.world.entity.Entity caster(StageEvent event) {
        if (level == null || event.caster() == null
            || (event.caster().getMostSignificantBits() == 0 && event.caster().getLeastSignificantBits() == 0)) return null;
        var player = level.getPlayerByUUID(event.caster());
        return player == null || player.isRemoved() ? null : player;
    }

    public boolean accept(StageEvent event, ClientLevel current) {
        updateLevel(current);
        VisualKind kind = VisualKind.byId(event.visual());
        if (current == null || kind == null || !current.dimension().location().equals(event.dimension())
            || !finite(event.center()) || active.containsKey(event.order())
            || active.size() >= MAX_LIVE) return false;
        long age = current.getGameTime() - event.tick();
        // A sustained instance may be older than its duration while it loops; the server ends it explicitly.
        if ((!kind.sustained() && age >= kind.duration) || age < -40) return false;
        active.put(event.order(), event);
        var definition = kind.definition();
        Vec3 at = center(event, 1);
        if (definition != null && definition.sound() != null && at != null && age >= 0 && age <= 2) {
            current.playLocalSound(at.x, at.y, at.z, net.minecraft.sounds.SoundEvent.createVariableRangeEvent(definition.sound()),
                net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f, false);
        }
        return true;
    }

    public void tick(ClientLevel current) {
        updateLevel(current);
        if (current == null) return;
        long now = current.getGameTime();
        for (StageEvent event : active.values()) {
            var definition = VisualKind.byId(event.visual()).definition();
            long local = (long) localAge(event, now - event.tick());
            if (definition != null && definition.particle() != null && now > previousTick
                && now >= event.tick() && local % 4 == 0 && local < definition.ticks()) {
                Vec3 at = center(event, 1);
                var type = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(definition.particle());
                if (at != null && type instanceof net.minecraft.core.particles.SimpleParticleType particle)
                    current.addParticle(particle, at.x, at.y, at.z, 0, .035, 0);
            }
            if (event.visual() != VisualKind.NEXUS.id) continue;
            for (int i = 0; i < CORE_TICKS.length; i++) {
                long cue = event.tick() + CORE_TICKS[i];
                if (previousTick >= cue || now < cue || now - cue > 2) continue;
                Vec3 p = event.center();
                var soundId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    dev.calamidade_awakening.CalamidadeAwakening.ID, CORE_SOUNDS[i]);
                current.playLocalSound(p.x, p.y, p.z, net.minecraft.sounds.SoundEvent.createVariableRangeEvent(soundId),
                    net.minecraft.sounds.SoundSource.PLAYERS, 2f, 1f, false);
            }
        }
        previousTick = now;
        active.values().removeIf(e -> {
            if (!expired(e, current.getGameTime() - e.tick())) return false;
            released.remove(e.order());
            return true;
        });
    }

    public Plan frame(Vec3 camera, Vec3 look, float partial, FrameLimits limits) {
        if (level == null || active.isEmpty()) return new Plan(List.of(), List.of(), limits, 0);
        List<FrameBudget.Candidate<Sample>> candidates = new ArrayList<>(active.size());
        double now = level.getGameTime() + partial;
        Frustum frustum = Minecraft.getInstance().levelRenderer.getFrustum();
        for (StageEvent effect : active.values()) {
            VisualKind kind = VisualKind.byId(effect.visual());
            double age = now - effect.tick();
            double local = localAge(effect, age);
            double p = Math.clamp(local / kind.duration, 0, 1);
            double alpha = FrameBudget.envelope(local, kind.duration);
            if (kind.definition() != null && kind.definition().impact() != null)
                alpha = Math.max(alpha, kind.definition().impact().weight(local));
            Vec3 origin = center(effect, partial);
            if (origin == null) continue;
            Vec3 relative = origin.subtract(camera);
            double distance = relative.length();
            if (!isVisible(frustum, origin, kind.radius)) continue;
            float seed = (effect.seed() & 0xFFFFL) / 65535f;
            Sample mark = new Sample(effect, kind, relative, (float) p, (float) alpha, seed, distance, age, local,
                basis(effect, partial));
            candidates.add(new FrameBudget.Candidate<>(mark, distance, alpha, effect.order(), kind.range, kind.hero()));
        }
        // 0.9.0: HERO instances first (nearest 2), never cut by the effect limit; NORMAL ones fill the rest.
        List<Sample> selected = FrameBudget.select(candidates, limits.effects(), limits.heroes());
        // The engine field shader has 4 mark slots (AwakeningMark0..3), whatever the pass budget of the profile.
        List<Sample> volumes = selected.stream().filter(sample -> sample.kind().volume != VisualKind.Volume.NONE)
            .limit(Math.min(limits.marks(), MAX_VOLUMES)).toList();
        int eligible = (int) candidates.stream().filter(FrameBudget.Candidate::eligible).count();
        return new Plan(selected, volumes, limits, eligible);
    }

    /**
     * Atmosphere of every active instance that has one (0.8.0), resolved at the local age. Not limited by the
     * frustum or the frame budget: turning away from the effect keeps its sky. The weight is the opacity envelope
     * times a fade over the last quarter of the effect's range.
     */
    public List<dev.calamidade_awakening.api.v1.Atmosphere.State> atmospheres(Vec3 camera, float partial) {
        if (level == null || active.isEmpty()) return List.of();
        List<dev.calamidade_awakening.api.v1.Atmosphere.State> states = new ArrayList<>(2);
        double now = level.getGameTime() + partial;
        for (StageEvent effect : active.values()) {
            VisualKind kind = VisualKind.byId(effect.visual());
            var atmosphere = kind.atmosphere();
            if (atmosphere == null) continue;
            double age = now - effect.tick();
            Vec3 origin = age < 0 ? null : center(effect, partial);
            if (origin == null) continue;
            double local = localAge(effect, age);
            float weight = (float) FrameBudget.envelope(local, kind.duration)
                * dev.calamidade_awakening.api.AtmosphereMath.rangeFade(origin.distanceTo(camera), kind.range);
            states.add(atmosphere.sample(kind.clock().at(local), weight));
        }
        return states;
    }

    /**
     * Sky layers of every active instance that has them (0.12.0), resolved at the local age: candidates for the sky pass,
     * not limited by the frustum or the effect budget. The instance weight is the opacity envelope times a fade over the
     * last quarter of the effect's range, as for the atmosphere; {@code timeOfDay} places layers on the sun and moon.
     */
    List<dev.calamidade_awakening.api.SkyMath.Candidate<SkyFrame.Resolved>> skies(Vec3 camera, float partial, double timeOfDay) {
        if (level == null || active.isEmpty()) return List.of();
        List<dev.calamidade_awakening.api.SkyMath.Candidate<SkyFrame.Resolved>> out = new ArrayList<>(2);
        double now = level.getGameTime() + partial;
        for (StageEvent effect : active.values()) {
            VisualKind kind = VisualKind.byId(effect.visual());
            var layers = kind.sky();
            if (layers.isEmpty()) continue;
            double age = now - effect.tick();
            Vec3 origin = age < 0 ? null : center(effect, partial);
            if (origin == null) continue;
            double local = localAge(effect, age);
            float weight = (float) FrameBudget.envelope(local, kind.duration)
                * dev.calamidade_awakening.api.AtmosphereMath.rangeFade(origin.distanceTo(camera), kind.range);
            if (!(weight > 0)) continue;
            var sampler = kind.clock().at(local);
            Basis basis = basis(effect, partial);
            float seed = (effect.seed() & 0xFFFFL) / 65535f;
            for (var layer : layers) {
                var state = layer.sample(sampler, weight);
                if (!state.visible()) continue;
                var anchor = anchorDirection(layer, camera, origin, basis, timeOfDay);
                if (anchor == null) continue;
                out.add(new dev.calamidade_awakening.api.SkyMath.Candidate<>(new SkyFrame.Resolved(layer, state,
                    state.direction(anchor), seed, effect.seed(), age, effect.order(), kind.hero()), kind.hero(),
                    state.weight(), effect.order()));
            }
        }
        return out;
    }

    /** Anchor direction of a sky layer from the camera, or null when its point coincides with the camera. */
    static dev.calamidade_awakening.api.v1.Sky.Direction anchorDirection(dev.calamidade_awakening.api.v1.Sky layer,
            Vec3 camera, Vec3 center, Basis basis, double timeOfDay) {
        return switch (layer.anchor()) {
            case DIRECTION -> dev.calamidade_awakening.api.v1.Sky.Direction.of(layer.anchorX(), layer.anchorY());
            case SUN -> dev.calamidade_awakening.api.SkyMath.sunDirection(timeOfDay);
            case MOON -> dev.calamidade_awakening.api.SkyMath.moonDirection(timeOfDay);
            case WORLD -> toward(camera, new Vec3(layer.anchorX(), layer.anchorY(), layer.anchorZ()));
            case CENTER -> toward(camera, center.add(turn(basis, layer.anchorX(), layer.anchorY(), layer.anchorZ())));
        };
    }

    private static dev.calamidade_awakening.api.v1.Sky.Direction toward(Vec3 camera, Vec3 point) {
        Vec3 d = point.subtract(camera);
        return d.lengthSqr() < 1e-6 ? null : new dev.calamidade_awakening.api.v1.Sky.Direction(d.x, d.y, d.z);
    }

    static boolean isVisible(Frustum frustum, Vec3 center, double radius) {
        if (frustum == null) return true;
        double extent = Math.max(2.5, radius * 2.5);
        return frustum.isVisible(new AABB(center.x - extent, center.y - extent, center.z - extent,
            center.x + extent, center.y + extent, center.z + extent));
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
