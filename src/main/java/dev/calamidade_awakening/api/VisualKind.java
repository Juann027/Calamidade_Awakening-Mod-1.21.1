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
