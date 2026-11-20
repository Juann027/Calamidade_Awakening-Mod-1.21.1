package dev.calamidade_awakening.api.v1.client;

import cyou.rimuru.rimulib.mc1211.client.api.FxApi;
import cyou.rimuru.rimulib.mc1211.client.cinematic.Shot;
import cyou.rimuru.rimulib.mc1211.client.cinematic.Subject;
import cyou.rimuru.rimulib.mc1211.client.render.Uniforms;
import dev.calamidade_awakening.api.Quality;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.config.ClientConfig;
import dev.calamidade_awakening.client.geometry.Color;
import dev.calamidade_awakening.client.geometry.Mesh;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Client-only callbacks. Register them in FMLClientSetupEvent. Each kind accepts one callback per effect ID.
 * Mesh coordinates are offsets in blocks from the interpolated anchor.
 */
public final class AddonLayers {
    /**
     * Clock of one instance. {@code progress} is local age / duration. Since 0.7.0, {@code timeline} samples the
     * effect's {@link Timeline} at the instance's local age (phases, tracks, markers); effects registered without
     * a timeline get an empty one whose local age is the real age.
     */
    public record Time(float progress, float opacity, float seed, double radius, Timeline.Sampler timeline) {
        private static final Timeline.Sampler NONE = Timeline.empty(1).at(0);
        /** 0.6 form, kept for compatibility: empty timeline sampler. */
        public Time(float progress, float opacity, float seed, double radius) {
            this(progress, opacity, seed, radius, NONE);
        }
        public Time {
            Objects.requireNonNull(timeline, "timeline");
        }
    }
    public record Hud(GuiGraphics graphics, Time time) {
        /** Timeline sampler of the instance (0.7.0). */
        public Timeline.Sampler timeline() { return time.timeline(); }
    }
    public static final class World {
        private final Mesh mesh;
        private final Vec3 origin;
        private final Time time;
        public World(Mesh mesh, Vec3 origin, Time time) {
            this.mesh = mesh; this.origin = origin; this.time = time;
        }
        public Time time() { return time; }
        /** Timeline sampler of the instance (0.7.0). */
        public Timeline.Sampler timeline() { return time.timeline(); }
        public int remainingVertices() { return mesh.remaining(); }
        public void triangle(Vec3 a, Vec3 b, Vec3 c, int argb) {
            Color color = color(argb);
            mesh.triangle(origin.add(a),origin.add(b),origin.add(c),color,color,color);
        }
        public void ribbon(Vec3 a, Vec3 b, Vec3 side, double width, int argb) {
            mesh.ribbon(origin.add(a),origin.add(b),side,width,color(argb));
        }
        private Color color(int argb) {
            return new Color(((argb>>16)&255)/255f,((argb>>8)&255)/255f,(argb&255)/255f,
                ((argb>>>24)/255f)*time.opacity());
        }
    }

    /**
     * Post-processing access for one visible instance (0.6.1). Passes go through {@link #pass}, which charges the
     * shared per-frame addon pass budget (1/2/4 for LOW/BALANCED/HIGH, also used by card shaders) and refuses
     * passes beyond it; check {@link #passesLeft()} to pick a cheaper variant. Screen effects are combined with
     * the other contributors (maximum, never overwritten). {@code center} is camera-relative, in the same space
     * as scenePosAt. {@code age} is the local age in ticks including partial (0.7.0: the timeline age, equal to
     * the real age without sustain; {@link #realAge()} keeps growing across loops). Flashes are dropped when the
     * player hides lightning flashes.
     */
    public static final class Post {
        private final FxApi.Frame frame;
        private final Time time;
        private final Vec3 center;
        private final double distance, age, realAge;
        private final long instance;
        private final Quality quality;
        private final boolean flashAllowed;
        private int passesLeft, used;

        /** 0.6 form, kept for compatibility: local and real age are equal. */
        public Post(FxApi.Frame frame, Time time, Vec3 center, double distance, double age, long instance,
                    Quality quality, boolean flashAllowed, int passBudget) {
            this(frame, time, center, distance, age, age, instance, quality, flashAllowed, passBudget);
        }

        public Post(FxApi.Frame frame, Time time, Vec3 center, double distance, double age, double realAge,
                    long instance, Quality quality, boolean flashAllowed, int passBudget) {
            this.frame = frame; this.time = time; this.center = center; this.distance = distance; this.age = age;
            this.realAge = realAge; this.instance = instance; this.quality = quality; this.flashAllowed = flashAllowed;
            this.passesLeft = Math.max(0, passBudget);
        }

        /** Queues a shader pass from assets/&lt;namespace&gt;/shaders/fx. Returns false, without drawing, when the budget is spent. */
        public boolean pass(String program, Consumer<Uniforms> uniforms) {
            if (passesLeft <= 0) return false;
            passesLeft--;
            used++;
            frame.pass(Objects.requireNonNull(program), Objects.requireNonNull(uniforms));
            return true;
        }

        /**
         * Sends every track of the timeline, at the current local age, as a float uniform named
         * {@code prefix + trackName} (0.7.0). Uniforms the shader does not declare are ignored by RimuLib.
         * Call inside the {@link #pass} consumer.
         */
        public void uploadTracks(Uniforms uniforms, String prefix) {
            Objects.requireNonNull(uniforms, "uniforms");
            Objects.requireNonNull(prefix, "prefix");
            Timeline.Sampler sampler = time.timeline();
            for (String track : sampler.timeline().tracks()) uniforms.f1(prefix + track, sampler.value(track));
        }

        public int passesLeft() { return passesLeft; }
        /** Passes actually queued by this callback (diagnostics). */
        public int used() { return used; }

        // Scaled by the player's accessibility settings (ClientConfig) before combining with other contributors.
        public void bloom(float amount) {
            frame.screen().bloom = Math.max(frame.screen().bloom, amount * ClientConfig.screenEffects());
        }
        /** Ignored when the player turns camera shake off. */
        public void shake(float amount) {
            if (ClientConfig.cameraShake()) frame.screen().shake = Math.max(frame.screen().shake, amount);
        }
        public void desaturate(float amount) {
            frame.screen().desaturate = Math.max(frame.screen().desaturate, amount * ClientConfig.screenEffects());
        }
        public void contrast(float amount) {
            frame.screen().contrast = Math.max(frame.screen().contrast, amount * ClientConfig.screenEffects());
        }
        /** Ignored when the player hides lightning flashes; scaled by the screen-effects setting. */
        public void flash(int argb, float amount) {
            float scaled = amount * ClientConfig.screenEffects();
            if (flashAllowed && scaled > 0) frame.screen().addFlash(argb, scaled);
        }

        // 0.7.0: the remaining RimuLib ScreenFx fields. All combine by maximum and follow the screen-effects setting.
        /** Chromatic aberration. */
        public void aberration(float amount) {
            frame.screen().aberration = Math.max(frame.screen().aberration, amount * ClientConfig.screenEffects());
        }
        /** Radial zoom blur. Ignored when the player turns motion effects off. */
        public void zoomBlur(float amount) {
            if (ClientConfig.motionEffects())
                frame.screen().zoomBlur = Math.max(frame.screen().zoomBlur, amount * ClientConfig.screenEffects());
        }
        /** Colored vignette; the strongest request keeps its color (ScreenFx.addVignette). */
        public void vignette(int argb, float amount) {
            float scaled = amount * ClientConfig.screenEffects();
            if (scaled > 0) frame.screen().addVignette(argb, scaled);
        }
        /** Afterimage trail. Ignored when the player turns motion effects off. */
        public void afterimage(float amount) {
            if (ClientConfig.motionEffects())
                frame.screen().afterimage = Math.max(frame.screen().afterimage, amount * ClientConfig.screenEffects());
        }
        /** Film grain. */
        public void grain(float amount) {
            frame.screen().grain = Math.max(frame.screen().grain, amount * ClientConfig.screenEffects());
        }

        public float partial() { return (float) frame.partial(); }
        public Time time() { return time; }
        /** Timeline sampler of the instance (0.7.0). */
        public Timeline.Sampler timeline() { return time.timeline(); }
        public Vec3 center() { return center; }
        public double distance() { return distance; }
        public double age() { return age; }
        /** Real age in ticks including partial; differs from {@link #age()} only for sustained timelines. */
        public double realAge() { return realAge; }
        public long instance() { return instance; }
        public Quality quality() { return quality; }
        public boolean flashAllowed() { return flashAllowed; }
    }

    /**
     * Custom camera (0.6.0), built on the caster's client when the instance starts. Return RimuLib shots made with
     * FilmApi.cut/sweep/pose around {@code subject}; the engine plays them, owns skip/cancel and ends the film with
     * the instance. Return an empty list for no camera. Overrides the card's camera list.
     * Since 0.7.0, {@code timeline} is the effect's {@link Timeline} (empty when none was registered), to align cuts
     * with phases: {@code FilmApi.cut(film.timeline().start("ruptura"), ...)}. Shots run on real time, so align them
     * with phases before a sustain loop.
     */
    public record Film(Subject subject, Vec3 center, int duration, long instance, Timeline timeline) {
        /** 0.6 form, kept for compatibility: empty timeline of the given duration. */
        public Film(Subject subject, Vec3 center, int duration, long instance) {
            this(subject, center, duration, instance, Timeline.empty(Math.max(1, Math.min(duration, dev.calamidade_awakening.api.v1.Effects.MAX_TICKS))));
        }
        public Film {
            Objects.requireNonNull(timeline, "timeline");
        }
    }

    /**
     * Fired once on every client that accepts an instance (0.6.0), including late arrivals ({@code age} > 0).
     * Use it for client-only bursts: sounds, particles, local state. {@code caster} is true on the caster's client.
     */
    public record Start(ClientLevel level, Vec3 center, long age, float seed, boolean caster, long instance) {}

    public record Panel(GuiGraphics graphics, float partial) {}

    private static final Map<String,Consumer<World>> world = new ConcurrentHashMap<>();
    private static final Map<String,Consumer<Hud>> hud = new ConcurrentHashMap<>();
    private static final Map<String,Consumer<Panel>> panels = new ConcurrentHashMap<>();
    private static final Map<String,Consumer<Post>> posts = new ConcurrentHashMap<>();
    private static final Map<String,Function<Film,List<Shot>>> films = new ConcurrentHashMap<>();
    private static final Map<String,Consumer<Start>> starts = new ConcurrentHashMap<>();
    private AddonLayers() {}

    public static void world(ResourceLocation effect, Consumer<World> layer) { put(world,effect,layer); }
    public static void hud(ResourceLocation effect, Consumer<Hud> layer) { put(hud,effect,layer); }
    public static void panel(ResourceLocation effect, Consumer<Panel> layer) { put(panels,effect,layer); }
    public static void post(ResourceLocation effect, Consumer<Post> layer) { put(posts,effect,layer); }
    public static void film(ResourceLocation effect, Function<Film,List<Shot>> shots) { put(films,effect,shots); }
    public static void start(ResourceLocation effect, Consumer<Start> listener) { put(starts,effect,listener); }

    private static <T> void put(Map<String,T> map, ResourceLocation id, T layer) {
        if (map.putIfAbsent(id.toString(), Objects.requireNonNull(layer)) != null)
            throw new IllegalArgumentException("Duplicate layer: " + id);
    }

    public static Consumer<World> world(String id) { return world.get(id); }
    public static Consumer<Hud> hud(String id) { return hud.get(id); }
    public static Consumer<Panel> panel(String id) { return panels.get(id); }
    public static Consumer<Post> post(String id) { return posts.get(id); }
    public static Function<Film,List<Shot>> film(String id) { return films.get(id); }
    public static Consumer<Start> start(String id) { return starts.get(id); }
}
