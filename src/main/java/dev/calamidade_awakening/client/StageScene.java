package dev.calamidade_awakening.client;

import dev.calamidade_awakening.api.FrameBudget;
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
    /**
     * {@code age} is the real age with the partial tick; {@code localAge} the timeline age (equal without sustain).
     * Progress, opacity and the impact accent follow the local age (0.7.0).
     */
    public record Sample(StageEvent event, VisualKind kind, Vec3 relative, float progress,
                         float opacity, float seed, double distance, double age, double localAge) {
        /** Public clock handed to addon layers, with the timeline sampled at the local age. */
        public dev.calamidade_awakening.api.v1.client.AddonLayers.Time time() {
            return new dev.calamidade_awakening.api.v1.client.AddonLayers.Time(progress, opacity, seed, kind.radius,
                kind.clock().at(localAge));
        }
    }
    public record Plan(List<Sample> effects, List<Sample> volumes, Quality quality) {}
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
    /** Resident sequences on this client, for diagnostics. */
    public int size() { return active.size(); }

    private Vec3 center(StageEvent event, float partial) {
        if (event.entityId() < 0) return event.center();
        var entity = level.getEntity(event.entityId());
        if (entity == null || !entity.getUUID().equals(event.anchorUuid()) || entity.isRemoved()) return null;
        return entity.getPosition(partial).add(event.offset());
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

    public Plan frame(Vec3 camera, Vec3 look, float partial, Quality quality) {
        if (level == null || active.isEmpty()) return new Plan(List.of(), List.of(), quality);
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
            Sample mark = new Sample(effect, kind, relative, (float) p, (float) alpha, seed, distance, age, local);
            candidates.add(new FrameBudget.Candidate<>(mark, distance, alpha, effect.order(), kind.range));
        }
        List<Sample> selected = FrameBudget.select(candidates, quality.effects);
        List<Sample> volumes = selected.stream().filter(sample -> sample.kind().volume != VisualKind.Volume.NONE)
            .limit(quality.marks).toList();
        return new Plan(selected, volumes, quality);
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
