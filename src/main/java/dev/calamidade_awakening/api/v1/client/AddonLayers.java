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
 * Client-only callbacks of an effect: world mesh, HUD, Focus panel, post-processing passes, camera and start event.
 * Register them in {@code FMLClientSetupEvent} (client side only). Each kind accepts one callback per effect ID.
 * Callbacks run on the render thread for every visible instance chosen by the frame budget; an exception disables
 * that callback for the session and is logged once.
 *
 * <pre>{@code
 * AddonLayers.post(ID, post -> {
 *     var t = post.timeline();
 *     post.pass("meumod:golpe", u -> {
 *         u.v3("Centro", post.center());
 *         u.f1("Peso", t.weight("impacto"));
 *     });
 *     post.bloom(t.weight("impacto") * .3f);
 * });
 * }</pre>
 */
public final class AddonLayers {
    /**
     * Clock of one instance.
     *
     * @param progress local age / duration, 0..1
     * @param opacity  engine envelope (fade in and out), 0..1, raised by the impact accent
     * @param seed     instance seed, 0..1
     * @param radius   definition radius, blocks
     * @param timeline sampler of the effect's {@link Timeline} at the local age (0.7.0); an empty timeline whose
     *                 local age is the real age when none was registered
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
    /**
     * HUD drawing for one effect per frame (the strongest visible instance), in GUI coordinates.
     *
     * @param graphics GUI graphics of the frame
     * @param time     clock of the instance
     */
    public record Hud(GuiGraphics graphics, Time time) {
        /** Timeline sampler of the instance (0.7.0). */
        public Timeline.Sampler timeline() { return time.timeline(); }
    }
    /**
     * Additive world mesh of one instance, drawn after particles in one batch with the engine geometry (no depth
     * write, not fogged, so it reads as emissive light). Coordinates are offsets in blocks from the interpolated
     * instance center. Vertices are capped per frame by the quality profile and shared among visible effects;
     * calls beyond the cap are dropped. Alpha is multiplied by the instance opacity.
     */
    public static final class World {
        private final Mesh mesh;
        private final Vec3 origin;
        private final Time time;
        /** Engine use. */
        public World(Mesh mesh, Vec3 origin, Time time) {
            this.mesh = mesh; this.origin = origin; this.time = time;
        }
        /** Clock of the instance. */
        public Time time() { return time; }
        /** Timeline sampler of the instance (0.7.0). */
        public Timeline.Sampler timeline() { return time.timeline(); }
        /** Vertices left in this instance's share of the frame budget. */
        public int remainingVertices() { return mesh.remaining(); }
        /** One triangle (3 vertices); points are offsets in blocks from the center, color ARGB. */
        public void triangle(Vec3 a, Vec3 b, Vec3 c, int argb) {
            Color color = color(argb);
            mesh.triangle(origin.add(a),origin.add(b),origin.add(c),color,color,color);
        }
        /** Flat ribbon from {@code a} to {@code b} (offsets in blocks), {@code width} blocks wide along {@code side}; 6 vertices. */
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

        /** Engine use (0.6 form, kept for compatibility: local and real age are equal). */
        public Post(FxApi.Frame frame, Time time, Vec3 center, double distance, double age, long instance,
                    Quality quality, boolean flashAllowed, int passBudget) {
            this(frame, time, center, distance, age, age, instance, quality, flashAllowed, passBudget);
        }

        /** Engine use. */
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

        /** Passes that still fit in this frame's addon budget (1/2/4 for LOW/BALANCED/HIGH, shared). */
        public int passesLeft() { return passesLeft; }
        /** Passes actually queued by this callback (diagnostics). */
        public int used() { return used; }

        // Scaled by the player's accessibility settings (ClientConfig) before combining with other contributors.
        /** Bloom, 0..1 typical; combined by maximum and scaled by the screen-effects setting. */
        public void bloom(float amount) {
            frame.screen().bloom = Math.max(frame.screen().bloom, amount * ClientConfig.screenEffects());
        }
        /** Camera shake, 0..1 typical; combined by maximum and ignored when the player turns camera shake off. */
        public void shake(float amount) {
            if (ClientConfig.cameraShake()) frame.screen().shake = Math.max(frame.screen().shake, amount);
        }
        /** Desaturation, 0..1; combined by maximum and scaled by the screen-effects setting. */
        public void desaturate(float amount) {
            frame.screen().desaturate = Math.max(frame.screen().desaturate, amount * ClientConfig.screenEffects());
        }
        /** Extra contrast, 0..1 typical; combined by maximum and scaled by the screen-effects setting. */
        public void contrast(float amount) {
            frame.screen().contrast = Math.max(frame.screen().contrast, amount * ClientConfig.screenEffects());
        }
        /** Colored flash (ARGB), 0..1; ignored when the player hides lightning flashes, scaled by the screen-effects setting. */
        public void flash(int argb, float amount) {
            float scaled = amount * ClientConfig.screenEffects();
            if (flashAllowed && scaled > 0) frame.screen().addFlash(argb, scaled);
        }

        // 0.7.0: the remaining RimuLib ScreenFx fields. All combine by maximum and follow the screen-effects setting.
        /** Chromatic aberration, 0..1 typical; combined by maximum and scaled by the screen-effects setting. */
        public void aberration(float amount) {
            frame.screen().aberration = Math.max(frame.screen().aberration, amount * ClientConfig.screenEffects());
        }
        /** Radial zoom blur, 0..1 typical; ignored when the player turns motion effects off. */
        public void zoomBlur(float amount) {
            if (ClientConfig.motionEffects())
                frame.screen().zoomBlur = Math.max(frame.screen().zoomBlur, amount * ClientConfig.screenEffects());
        }
        /** Colored vignette (ARGB), 0..1; the strongest request keeps its color (ScreenFx.addVignette). */
        public void vignette(int argb, float amount) {
            float scaled = amount * ClientConfig.screenEffects();
            if (scaled > 0) frame.screen().addVignette(argb, scaled);
        }
        /** Afterimage trail, 0..1 typical; ignored when the player turns motion effects off. */
        public void afterimage(float amount) {
            if (ClientConfig.motionEffects())
                frame.screen().afterimage = Math.max(frame.screen().afterimage, amount * ClientConfig.screenEffects());
        }
        /** Film grain, 0..1 typical; combined by maximum and scaled by the screen-effects setting. */
        public void grain(float amount) {
            frame.screen().grain = Math.max(frame.screen().grain, amount * ClientConfig.screenEffects());
        }

        /** Partial tick of the frame, 0..1. */
        public float partial() { return (float) frame.partial(); }
        /** Clock of the instance (progress, opacity, seed, radius, timeline). */
        public Time time() { return time; }
        /** Timeline sampler of the instance (0.7.0). */
        public Timeline.Sampler timeline() { return time.timeline(); }
        /** Instance center relative to the camera, in blocks (the space of scenePosAt in shaders). */
        public Vec3 center() { return center; }
        /** Distance from the camera to the center, in blocks. */
        public double distance() { return distance; }
        /** Local age in ticks including the partial tick (equal to the real age without sustain). */
        public double age() { return age; }
        /** Real age in ticks including partial; differs from {@link #age()} only for sustained timelines. */
        public double realAge() { return realAge; }
        /** Instance identifier (stable for its lifetime). */
        public long instance() { return instance; }
        /** Quality profile: LOW, BALANCED or HIGH; lower the raymarch steps on the smaller ones. */
        public Quality quality() { return quality; }
        /** False when the player hides lightning flashes: avoid strobes and sudden brightness changes. */
        public boolean flashAllowed() { return flashAllowed; }
        /**
         * Screen-effects intensity chosen by the player, 0..1 (0.8.0). The screen-effect methods above already apply
         * it; multiply custom full-screen changes in your own passes (impact frames, color grading) by it.
         */
        public float screenEffects() { return ClientConfig.screenEffects(); }
        /** False when the player turned motion effects off (0.8.0): skip custom zoom, smear or shake passes. */
        public boolean motionEffects() { return ClientConfig.motionEffects(); }
    }

    /**
     * Custom camera (0.6.0), built on the caster's client when the instance starts. Return RimuLib shots made with
     * FilmApi.cut/sweep/pose around {@code subject}; the engine plays them, owns skip/cancel and ends the film with
     * the instance. Return an empty list for no camera. Overrides the card's camera list.
     * Since 0.7.0, {@code timeline} is the effect's {@link Timeline} (empty when none was registered), to align cuts
     * with phases: {@code FilmApi.cut(film.timeline().start("ruptura"), ...)}. Shots run on real time, so align them
     * with phases before a sustain loop.
     *
     * @param subject  RimuLib camera subject created by the engine for the caster
     * @param center   world center of the instance, blocks
     * @param duration effect duration, ticks
     * @param instance instance identifier
     * @param timeline effect timeline (empty when none was registered)
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
     * Use it for client-only bursts: sounds, particles, local state.
     *
     * @param level    client level
     * @param center   world center at the start, blocks
     * @param age      ticks since the server started it (0 for an on-time start, more for late arrivals)
     * @param seed     instance seed, 0..1
     * @param caster   true on the caster's client
     * @param instance instance identifier
     */
    public record Start(ClientLevel level, Vec3 center, long age, float seed, boolean caster, long instance) {}

    /**
     * Drawing over the Focus menu while the effect's card is selected (explain parameters there).
     *
     * @param graphics GUI graphics
     * @param partial  partial tick, 0..1
     */
    public record Panel(GuiGraphics graphics, float partial) {}

    private static final Map<String,Consumer<World>> world = new ConcurrentHashMap<>();
    private static final Map<String,Consumer<Hud>> hud = new ConcurrentHashMap<>();
    private static final Map<String,Consumer<Panel>> panels = new ConcurrentHashMap<>();
    private static final Map<String,Consumer<Post>> posts = new ConcurrentHashMap<>();
    private static final Map<String,Function<Film,List<Shot>>> films = new ConcurrentHashMap<>();
    private static final Map<String,Consumer<Start>> starts = new ConcurrentHashMap<>();
    private AddonLayers() {}

    /** Registers the world mesh of an effect (one per effect). */
    public static void world(ResourceLocation effect, Consumer<World> layer) { put(world,effect,layer); }
    /** Registers the HUD layer of an effect (one per effect). */
    public static void hud(ResourceLocation effect, Consumer<Hud> layer) { put(hud,effect,layer); }
    /** Registers the Focus panel of a published effect (one per effect). */
    public static void panel(ResourceLocation effect, Consumer<Panel> layer) { put(panels,effect,layer); }
    /** Registers the post-processing callback of an effect (one per effect); called for every visible instance. */
    public static void post(ResourceLocation effect, Consumer<Post> layer) { put(posts,effect,layer); }
    /** Registers the custom camera of an effect (one per effect); replaces the card's shots. */
    public static void film(ResourceLocation effect, Function<Film,List<Shot>> shots) { put(films,effect,shots); }
    /** Registers the start listener of an effect (one per effect). */
    public static void start(ResourceLocation effect, Consumer<Start> listener) { put(starts,effect,listener); }

    private static <T> void put(Map<String,T> map, ResourceLocation id, T layer) {
        if (map.putIfAbsent(id.toString(), Objects.requireNonNull(layer)) != null)
            throw new IllegalArgumentException("Duplicate layer: " + id);
    }

    /** Engine lookup: world layer registered for an effect ID string, or null. */
    public static Consumer<World> world(String id) { return world.get(id); }
    /** Engine lookup: HUD layer for an effect ID string, or null. */
    public static Consumer<Hud> hud(String id) { return hud.get(id); }
    /** Engine lookup: Focus panel for an effect ID string, or null. */
    public static Consumer<Panel> panel(String id) { return panels.get(id); }
    /** Engine lookup: post callback for an effect ID string, or null. */
    public static Consumer<Post> post(String id) { return posts.get(id); }
    /** Engine lookup: camera callback for an effect ID string, or null. */
    public static Function<Film,List<Shot>> film(String id) { return films.get(id); }
    /** Engine lookup: start listener for an effect ID string, or null. */
    public static Consumer<Start> start(String id) { return starts.get(id); }
}
