package dev.calamidade_awakening.api;

import dev.calamidade_awakening.CalamidadeAwakening;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable effect definition shared by server, menu, geometry and post processing. */
public final class VisualKind {
    public enum Geometry { NONE, PRISM, RINGS, ORBIT, NEXUS }
    public enum Volume {
        NONE(-1), CRYSTAL(0), VEIL(1), LENS(2), NEBULA(3);
        public final int shaderMode;
        Volume(int shaderMode) { this.shaderMode = shaderMode; }
    }

    private static final Map<Integer, VisualKind> REGISTRY = new LinkedHashMap<>();
    private static final Map<String, VisualKind> BY_NAME = new LinkedHashMap<>();
    private static final Map<Integer, VisualKind> EXTERNAL = new LinkedHashMap<>();
    private String externalKey;
    private dev.calamidade_awakening.api.v1.Addons.Entry card;
    public void bindCard(dev.calamidade_awakening.api.v1.Addons.Entry entry) { this.card = entry; }
    private dev.calamidade_awakening.api.v1.Effects.Definition definition;
    public dev.calamidade_awakening.api.v1.Effects.Definition definition() { return definition; }
    /** True for external effects registered with {@code Priority.HERO} (0.9.0); built-in effects are NORMAL. */
    public boolean hero() {
        return definition != null && definition.priority() == dev.calamidade_awakening.api.v1.Effects.Priority.HERO;
    }
    private static boolean sealed;

    /** Server gameplay callback scheduled on an instance clock. */
    public record ScheduledCue(int tick, dev.calamidade_awakening.api.v1.Effects.Cue cue) {}
    // Written during (possibly parallel) mod construction, read on the server thread.
    private final java.util.List<ScheduledCue> cues = new java.util.concurrent.CopyOnWriteArrayList<>();
    public java.util.List<ScheduledCue> cues() { return cues; }

    public static synchronized void addCue(net.minecraft.resources.ResourceLocation id, int tick,
            dev.calamidade_awakening.api.v1.Effects.Cue cue) {
        VisualKind kind = BY_NAME.get(id.toString());
        if (kind == null || kind.externalKey == null)
            throw new IllegalArgumentException("Register the external effect before adding cues: " + id);
        if (tick < 0 || tick >= kind.duration)
            throw new IllegalArgumentException("Cue tick must be within 0.." + (kind.duration - 1) + ": " + id);
        kind.cues.add(new ScheduledCue(tick, Objects.requireNonNull(cue)));
    }

    // 0.7.0: optional script of an external effect. Null means "no timeline" (local age == real age).
    private volatile dev.calamidade_awakening.api.v1.Timeline timeline;
    private volatile dev.calamidade_awakening.api.v1.Timeline emptyTimeline;

    /** Registered timeline, or null. */
    public dev.calamidade_awakening.api.v1.Timeline timeline() { return timeline; }

    /** Registered timeline, or an empty one with this effect's duration (what client layers see). */
    public dev.calamidade_awakening.api.v1.Timeline clock() {
        var registered = timeline;
        if (registered != null) return registered;
        var empty = emptyTimeline;
        if (empty == null) emptyTimeline = empty = dev.calamidade_awakening.api.v1.Timeline.empty(duration);
        return empty;
    }

    /** True when instances loop until released (timeline with sustain). */
    public boolean sustained() {
        var registered = timeline;
        return registered != null && registered.sustained();
    }

    /** Local age of an instance; equals {@code realAge} without a sustained timeline. */
    public double localAge(double realAge, double releaseAge) {
        var registered = timeline;
        return registered == null ? realAge : registered.localAge(realAge, releaseAge);
    }

    public static synchronized void setTimeline(net.minecraft.resources.ResourceLocation id,
            dev.calamidade_awakening.api.v1.Timeline script) {
        VisualKind kind = BY_NAME.get(id.toString());
        if (kind == null || kind.externalKey == null)
            throw new IllegalArgumentException("Register the external effect before its timeline: " + id);
        if (kind.timeline != null) throw new IllegalArgumentException("Duplicate timeline: " + id);
        if (!kind.cues.isEmpty()) throw new IllegalArgumentException("Register the timeline before the cues: " + id);
        if (script.duration() > kind.duration)
            throw new IllegalArgumentException("Timeline (" + script.duration() + " ticks) longer than the definition ("
                + kind.duration + "): " + id);
        kind.timeline = Objects.requireNonNull(script);
    }

    // 0.8.0: optional atmosphere of an external effect. Null means "no atmosphere".
    private volatile dev.calamidade_awakening.api.v1.Atmosphere atmosphere;

    /** Registered atmosphere, or null. */
    public dev.calamidade_awakening.api.v1.Atmosphere atmosphere() { return atmosphere; }

    /** Binds an atmosphere; tracks and phase it reads must exist in the timeline registered before it. */
    public static synchronized void setAtmosphere(net.minecraft.resources.ResourceLocation id,
            dev.calamidade_awakening.api.v1.Atmosphere value) {
        VisualKind kind = BY_NAME.get(id.toString());
        if (kind == null || kind.externalKey == null)
            throw new IllegalArgumentException("Register the external effect before its atmosphere: " + id);
        if (kind.atmosphere != null) throw new IllegalArgumentException("Duplicate atmosphere: " + id);
        Objects.requireNonNull(value).validate(kind.timeline);
        kind.atmosphere = value;
    }

    // 0.9.0: optional beams of an external effect, drawn by the engine's beams pass. Empty means "no beams".
    private volatile java.util.List<dev.calamidade_awakening.api.v1.Beam> beams = java.util.List.of();

    /** Registered beams, in registration order (empty when none). */
    public java.util.List<dev.calamidade_awakening.api.v1.Beam> beams() { return beams; }

    /** Binds 1..16 beams once; tracks and phases they read must exist in the timeline registered before them. */
    public static synchronized void setBeams(net.minecraft.resources.ResourceLocation id,
            java.util.List<dev.calamidade_awakening.api.v1.Beam> value) {
        VisualKind kind = BY_NAME.get(id.toString());
        if (kind == null || kind.externalKey == null)
            throw new IllegalArgumentException("Register the external effect before its beams: " + id);
        if (!kind.beams.isEmpty()) throw new IllegalArgumentException("Duplicate beams: " + id);
        var list = java.util.List.copyOf(value);
        if (list.isEmpty() || list.size() > dev.calamidade_awakening.api.v1.Beam.MAX_PER_EFFECT)
            throw new IllegalArgumentException("Expected 1.." + dev.calamidade_awakening.api.v1.Beam.MAX_PER_EFFECT
                + " beams per effect: " + id);
        for (var beam : list) beam.validate(kind.timeline);
        kind.beams = list;
    }

    // 0.9.0: optional visual lights of an external effect, drawn by the engine's lights pass. Empty means none.
    private volatile java.util.List<dev.calamidade_awakening.api.v1.Light> lights = java.util.List.of();

    /** Registered lights, in registration order (empty when none). */
    public java.util.List<dev.calamidade_awakening.api.v1.Light> lights() { return lights; }

    /**
     * Binds 1..8 lights once. Tracks and phases they read must exist in the timeline registered before them, and a
     * light made with {@code Light.along(beam)} needs that beam among the effect's beams, registered before.
     */
    public static synchronized void setLights(net.minecraft.resources.ResourceLocation id,
            java.util.List<dev.calamidade_awakening.api.v1.Light> value) {
        VisualKind kind = BY_NAME.get(id.toString());
        if (kind == null || kind.externalKey == null)
            throw new IllegalArgumentException("Register the external effect before its lights: " + id);
        if (!kind.lights.isEmpty()) throw new IllegalArgumentException("Duplicate lights: " + id);
        var list = java.util.List.copyOf(value);
        if (list.isEmpty() || list.size() > dev.calamidade_awakening.api.v1.Light.MAX_PER_EFFECT)
            throw new IllegalArgumentException("Expected 1.." + dev.calamidade_awakening.api.v1.Light.MAX_PER_EFFECT
                + " lights per effect: " + id);
        for (var light : list) {
            light.validate(kind.timeline);
            if (light.beam() != null && kind.beams.stream().noneMatch(b -> b == light.beam()))
                throw new IllegalArgumentException("Light follows a beam that is not registered for " + id
                    + ": call Effects.beams with that beam first");
        }
        kind.lights = list;
    }

    // 0.12.0: optional sky layers of an external effect, painted by the engine's sky pass. Empty means none.
    private volatile java.util.List<dev.calamidade_awakening.api.v1.Sky> sky = java.util.List.of();

    /** Registered sky layers, in registration order (empty when none). */
    public java.util.List<dev.calamidade_awakening.api.v1.Sky> sky() { return sky; }

    /** Binds 1..6 sky layers once; tracks and phases they read must exist in the timeline registered before them. */
    public static synchronized void setSky(net.minecraft.resources.ResourceLocation id,
            java.util.List<dev.calamidade_awakening.api.v1.Sky> value) {
        VisualKind kind = BY_NAME.get(id.toString());
        if (kind == null || kind.externalKey == null)
            throw new IllegalArgumentException("Register the external effect before its sky layers: " + id);
        if (!kind.sky.isEmpty()) throw new IllegalArgumentException("Duplicate sky layers: " + id);
        var list = java.util.List.copyOf(value);
        if (list.isEmpty() || list.size() > dev.calamidade_awakening.api.v1.Sky.MAX_PER_EFFECT)
            throw new IllegalArgumentException("Expected 1.." + dev.calamidade_awakening.api.v1.Sky.MAX_PER_EFFECT
                + " sky layers per effect: " + id);
        for (var layer : list) layer.validate(kind.timeline);
        kind.sky = list;
    }

    // 0.14.0: optional custom sky background of an external effect, drawn in AFTER_SKY. Null means none.
    private volatile dev.calamidade_awakening.api.v1.Backdrop backdrop;

    /** Registered background, or null. */
    public dev.calamidade_awakening.api.v1.Backdrop backdrop() { return backdrop; }

    /** Binds a background once; tracks and phases it reads must exist in the timeline registered before it. */
    public static synchronized void setBackdrop(net.minecraft.resources.ResourceLocation id,
            dev.calamidade_awakening.api.v1.Backdrop value) {
        VisualKind kind = BY_NAME.get(id.toString());
        if (kind == null || kind.externalKey == null)
            throw new IllegalArgumentException("Register the external effect before its backdrop: " + id);
        if (kind.backdrop != null) throw new IllegalArgumentException("Duplicate backdrop: " + id);
        value.validate(kind.timeline);
        kind.backdrop = value;
    }

    // 0.14.0: optional star fields and nebulae of an external effect, drawn in AFTER_SKY. Empty: none.
    private volatile java.util.List<dev.calamidade_awakening.api.v1.StarField> stars = java.util.List.of();
    private volatile java.util.List<dev.calamidade_awakening.api.v1.Nebula> nebulae = java.util.List.of();

    /** Registered star fields, possibly empty. */
    public java.util.List<dev.calamidade_awakening.api.v1.StarField> stars() { return stars; }
    /** Registered nebulae, possibly empty. */
    public java.util.List<dev.calamidade_awakening.api.v1.Nebula> nebulae() { return nebulae; }

    /** Binds 1..4 star fields once; tracks and phases they read must exist in the timeline registered before them. */
    public static synchronized void setStars(net.minecraft.resources.ResourceLocation id,
            java.util.List<dev.calamidade_awakening.api.v1.StarField> list) {
        VisualKind kind = BY_NAME.get(id.toString());
        if (kind == null || kind.externalKey == null)
            throw new IllegalArgumentException("Register the external effect before its star fields: " + id);
        if (!kind.stars.isEmpty()) throw new IllegalArgumentException("Duplicate star fields: " + id);
        if (list.isEmpty() || list.size() > dev.calamidade_awakening.api.v1.StarField.MAX_PER_EFFECT)
            throw new IllegalArgumentException("Expected 1.." + dev.calamidade_awakening.api.v1.StarField.MAX_PER_EFFECT + " star fields");
        for (var field : list) java.util.Objects.requireNonNull(field, "star field").validate(kind.timeline);
        kind.stars = java.util.List.copyOf(list);
    }

    /** Binds 1..8 nebulae once; tracks and phases they read must exist in the timeline registered before them. */
    public static synchronized void setNebulae(net.minecraft.resources.ResourceLocation id,
            java.util.List<dev.calamidade_awakening.api.v1.Nebula> list) {
        VisualKind kind = BY_NAME.get(id.toString());
        if (kind == null || kind.externalKey == null)
            throw new IllegalArgumentException("Register the external effect before its nebulae: " + id);
        if (!kind.nebulae.isEmpty()) throw new IllegalArgumentException("Duplicate nebulae: " + id);
        if (list.isEmpty() || list.size() > dev.calamidade_awakening.api.v1.Nebula.MAX_PER_EFFECT)
            throw new IllegalArgumentException("Expected 1.." + dev.calamidade_awakening.api.v1.Nebula.MAX_PER_EFFECT + " nebulae");
        for (var nebula : list) java.util.Objects.requireNonNull(nebula, "nebula").validate(kind.timeline);
        kind.nebulae = java.util.List.copyOf(list);
    }

    // 0.14.0 (phase 4): optional images (PNGs, flipbooks) and impostors of an external effect, drawn in AFTER_SKY.
    private volatile java.util.List<dev.calamidade_awakening.api.v1.SkyImage> images = java.util.List.of();
    private volatile java.util.List<dev.calamidade_awakening.api.v1.SkyImpostor> impostors = java.util.List.of();

    /** Registered sky images, possibly empty. */
    public java.util.List<dev.calamidade_awakening.api.v1.SkyImage> images() { return images; }
    /** Registered sky impostors, possibly empty. */
    public java.util.List<dev.calamidade_awakening.api.v1.SkyImpostor> impostors() { return impostors; }
    /** True when the kind has any sky object (stars, nebulae, images or impostors). */
    public boolean hasSkyObjects() { return !stars.isEmpty() || !nebulae.isEmpty() || !images.isEmpty() || !impostors.isEmpty(); }

    /** Binds 1..8 images once; tracks and phases they read must exist in the timeline registered before them. */
    public static synchronized void setImages(net.minecraft.resources.ResourceLocation id,
            java.util.List<dev.calamidade_awakening.api.v1.SkyImage> list) {
        VisualKind kind = BY_NAME.get(id.toString());
        if (kind == null || kind.externalKey == null)
            throw new IllegalArgumentException("Register the external effect before its sky images: " + id);
        if (!kind.images.isEmpty()) throw new IllegalArgumentException("Duplicate sky images: " + id);
        if (list.isEmpty() || list.size() > dev.calamidade_awakening.api.v1.SkyImage.MAX_PER_EFFECT)
            throw new IllegalArgumentException("Expected 1.." + dev.calamidade_awakening.api.v1.SkyImage.MAX_PER_EFFECT + " sky images");
        for (var image : list) java.util.Objects.requireNonNull(image, "sky image").validate(kind.timeline);
        kind.images = java.util.List.copyOf(list);
    }

    /** Binds 1..4 impostors once; tracks and phases they read must exist in the timeline registered before them. */
    public static synchronized void setImpostors(net.minecraft.resources.ResourceLocation id,
            java.util.List<dev.calamidade_awakening.api.v1.SkyImpostor> list) {
        VisualKind kind = BY_NAME.get(id.toString());
        if (kind == null || kind.externalKey == null)
            throw new IllegalArgumentException("Register the external effect before its sky impostors: " + id);
        if (!kind.impostors.isEmpty()) throw new IllegalArgumentException("Duplicate sky impostors: " + id);
        if (list.isEmpty() || list.size() > dev.calamidade_awakening.api.v1.SkyImpostor.MAX_PER_EFFECT)
            throw new IllegalArgumentException("Expected 1.." + dev.calamidade_awakening.api.v1.SkyImpostor.MAX_PER_EFFECT + " sky impostors");
        for (var impostor : list) java.util.Objects.requireNonNull(impostor, "sky impostor").validate(kind.timeline);
        kind.impostors = java.util.List.copyOf(list);
    }

    // 0.11.0: optional terrain edits of an external effect (server commit, client heat and stand-in). Empty: none.
    private volatile java.util.List<dev.calamidade_awakening.api.v1.TerrainEdit> terrain = java.util.List.of();

    /** Registered terrain edits, in registration order (empty when none). */
    public java.util.List<dev.calamidade_awakening.api.v1.TerrainEdit> terrain() { return terrain; }

    /**
     * Binds 1..4 terrain edits once. The effect needs its timeline registered before (the commit is a marker); the
     * markers, tracks and stand-ins are validated against it.
     */
    public static synchronized void setTerrain(net.minecraft.resources.ResourceLocation id,
            java.util.List<dev.calamidade_awakening.api.v1.TerrainEdit> value) {
        VisualKind kind = BY_NAME.get(id.toString());
        if (kind == null || kind.externalKey == null)
            throw new IllegalArgumentException("Register the external effect before its terrain edits: " + id);
        if (!kind.terrain.isEmpty()) throw new IllegalArgumentException("Duplicate terrain edits: " + id);
        var list = java.util.List.copyOf(value);
        if (list.isEmpty() || list.size() > dev.calamidade_awakening.api.v1.TerrainEdit.MAX_PER_EFFECT)
            throw new IllegalArgumentException("Expected 1.." + dev.calamidade_awakening.api.v1.TerrainEdit.MAX_PER_EFFECT
                + " terrain edits per effect: " + id);
        for (var edit : list) edit.validate(kind.timeline, kind.duration);
        kind.terrain = list;
        // The commit is an engine cue at the marker: it runs once per instance, when the clock walks that tick.
        for (int i = 0; i < list.size(); i++) {
            int index = i;
            kind.cues.add(new ScheduledCue(kind.timeline.tick(list.get(i).marker()),
                ctx -> dev.calamidade_awakening.terrain.TerrainWorks.commitCue(ctx, index)));
        }
    }

    /** Cue on a timeline marker; the marker tick is resolved now. */
    public static synchronized void addCue(net.minecraft.resources.ResourceLocation id, String marker,
            dev.calamidade_awakening.api.v1.Effects.Cue cue) {
        VisualKind kind = BY_NAME.get(id.toString());
        if (kind == null || kind.externalKey == null)
            throw new IllegalArgumentException("Register the external effect before adding cues: " + id);
        if (kind.timeline == null)
            throw new IllegalArgumentException("Register a timeline before cues on markers: " + id);
        addCue(id, kind.timeline.tick(Objects.requireNonNull(marker)), cue);
    }

    /** Every network key (internal and external), for command suggestions and diagnostics. */
    public static synchronized java.util.List<String> networkKeys() { return java.util.List.copyOf(BY_NAME.keySet()); }

    public static synchronized void registerExternal(net.minecraft.resources.ResourceLocation id,
            dev.calamidade_awakening.api.v1.Effects.Definition definition) {
        if (id.getNamespace().equals(CalamidadeAwakening.ID) || BY_NAME.containsKey(id.toString()))
            throw new IllegalArgumentException("Reserved or duplicate effect ID: " + id);
        int local = -2 - EXTERNAL.size(); // -1 is the unknown wire ID.
        var kind = new VisualKind(local, id.getPath(), id.toString(), definition.argb(), definition.ticks(),
            definition.radius(), definition.range(), Geometry.valueOf(definition.shape().name()), Volume.valueOf(definition.material().name()), false, false);
        kind.definition = definition;
        kind.externalKey = id.toString();
        EXTERNAL.put(local, kind);
        BY_NAME.put(kind.externalKey, kind);
    }
    public static final VisualKind LATTICE = register(0, "lattice", "Crystal Bloom", 0xFF65E8C9,
        100, 7, Geometry.RINGS, Volume.CRYSTAL, false, false);
    public static final VisualKind VEIL = register(1, "veil", "Horizon Stained Glass", 0xFFFFAD68,
        140, 9, Geometry.ORBIT, Volume.VEIL, true, false);
    public static final VisualKind PRISM = register(2, "prism", "Prismatic Pulse", 0xFF78B8FF,
        38, 6, Geometry.PRISM, Volume.NONE, false, false);
    public static final VisualKind AURORA = register(3, "aurora", "Aurora Convergence", 0xFF83F5EA,
        180, 9, Geometry.ORBIT, Volume.LENS, true, true);
    public static final VisualKind NEXUS = register(4, "nexus", "Horizon Core", 0xFFE5B3FF,
        200, 12, Geometry.NEXUS, Volume.NEBULA, true, false);

    public final int id, color, duration;
    public final String key, title;
    public final double radius;
    /** Distribution distance in blocks (128 for built-in effects). */
    public final double range;
    public final Geometry geometry;
    public final Volume volume;
    public final boolean film, orbital;

    private VisualKind(int id, String key, String title, int color, int duration, double radius, double range,
                       Geometry geometry, Volume volume, boolean film, boolean orbital) {
        this.id = id; this.key = key; this.title = title; this.color = color; this.duration = duration;
        this.radius = radius; this.range = range; this.geometry = geometry; this.volume = volume; this.film = film; this.orbital = orbital;
    }

    public static VisualKind register(int id, String key, String title, int color, int duration, double radius,
                                      Geometry geometry, Volume volume, boolean film, boolean orbital) {
        if (sealed) throw new IllegalStateException("Effect registry is sealed");
        if (id < 0 || key == null || !key.matches("[a-z0-9_]+") || title == null || title.isBlank()
            || duration < 1 || duration > 1200 || (film && duration < 40) || !Double.isFinite(radius) || radius <= 0 || radius > 128
            || (orbital && !film) || REGISTRY.containsKey(id)
            || REGISTRY.values().stream().anyMatch(kind -> kind.key.equals(key))) {
            throw new IllegalArgumentException("Invalid or duplicate effect definition: " + key);
        }
        VisualKind kind = new VisualKind(id, key, title, color, duration, radius, 128,
            Objects.requireNonNull(geometry), Objects.requireNonNull(volume), film, orbital);
        REGISTRY.put(id, kind);
        BY_NAME.put(kind.networkKey(), kind);
        return kind;
    }

    public dev.calamidade_awakening.api.v1.Addons.Entry addon() {
        return card;
    }

    public String localizedTitle() {
        var addon = addon();
        if (addon != null) return net.minecraft.network.chat.Component.translatableWithFallback(
            addon.card().titleKey(), addon.card().fallback()).getString();
        return net.minecraft.network.chat.Component.translatableWithFallback(
            "key." + CalamidadeAwakening.ID + "." + key, title).getString();
    }

    public String networkKey() { return externalKey != null ? externalKey : CalamidadeAwakening.ID + ":" + key; }
    public static int localId(String networkKey) {
        VisualKind kind = BY_NAME.get(networkKey);
        return kind == null ? -1 : kind.id;
    }

    public static void seal() { sealed = true; }
    public static VisualKind byId(int id) { return id < -1 ? EXTERNAL.get(id) : REGISTRY.get(id); }
    public static VisualKind[] values() { return REGISTRY.values().toArray(VisualKind[]::new); }
}
