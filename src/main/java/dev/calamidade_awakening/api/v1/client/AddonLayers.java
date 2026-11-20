package dev.calamidade_awakening.api.v1.client;

import cyou.rimuru.rimulib.mc1211.client.api.FxApi;
import cyou.rimuru.rimulib.mc1211.client.cinematic.Shot;
import cyou.rimuru.rimulib.mc1211.client.cinematic.Subject;
import cyou.rimuru.rimulib.mc1211.client.render.Uniforms;
import dev.calamidade_awakening.api.Quality;
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
    public record Time(float progress, float opacity, float seed, double radius) {}
    public record Hud(GuiGraphics graphics, Time time) {}
    public static final class World {
        private final Mesh mesh;
        private final Vec3 origin;
        private final Time time;
        public World(Mesh mesh, Vec3 origin, Time time) {
            this.mesh = mesh; this.origin = origin; this.time = time;
        }
        public Time time() { return time; }
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
     * as scenePosAt. {@code age} is in ticks including partial. Flashes are dropped when the player hides
     * lightning flashes.
     */
    public static final class Post {
        private final FxApi.Frame frame;
        private final Time time;
        private final Vec3 center;
        private final double distance, age;
        private final long instance;
        private final Quality quality;
        private final boolean flashAllowed;
        private int passesLeft, used;

        public Post(FxApi.Frame frame, Time time, Vec3 center, double distance, double age, long instance,
                    Quality quality, boolean flashAllowed, int passBudget) {
            this.frame = frame; this.time = time; this.center = center; this.distance = distance; this.age = age;
            this.instance = instance; this.quality = quality; this.flashAllowed = flashAllowed;
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

        public float partial() { return (float) frame.partial(); }
        public Time time() { return time; }
        public Vec3 center() { return center; }
        public double distance() { return distance; }
        public double age() { return age; }
        public long instance() { return instance; }
        public Quality quality() { return quality; }
        public boolean flashAllowed() { return flashAllowed; }
    }

    /**
     * Custom camera (0.6.0), built on the caster's client when the instance starts. Return RimuLib shots made with
     * FilmApi.cut/sweep/pose around {@code subject}; the engine plays them, owns skip/cancel and ends the film with
     * the instance. Return an empty list for no camera. Overrides the card's camera list.
     */
    public record Film(Subject subject, Vec3 center, int duration, long instance) {}

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
