package dev.calamidade_awakening.api.v1;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Persistent sky of a dimension (0.12.0): named {@link State states} (for example {@code calmo}, {@code ameaca},
 * {@code caos}), each with its {@link Sky} layers, an optional {@link Atmosphere} and whether the vanilla sun, moon and
 * sky stay visible, plus the state the dimension starts in. Pure Java, no Minecraft types. Register it for a dimension
 * with {@link SkyProfiles#define} during mod construction, on both sides; change the state on the server with
 * {@link SkyProfiles#setState} (a smooth crossfade over N ticks, synced to every player of the dimension, including
 * those who arrive later, and saved with the world).
 *
 * <pre>{@code
 * static final SkyProfile CEU = SkyProfile.builder()
 *     .state("calmo", SkyProfile.State.builder().layers(Sky.aurora().progress(.4f).build()).build())
 *     .state("ameaca", SkyProfile.State.builder().layers(Sky.body().direction(40, 25).size(36).build())
 *         .atmosphere(Atmosphere.builder().sky(0xff200818, 0xff803040, .7f).build()).build())
 *     .state("caos", SkyProfile.State.builder().layers(Sky.blackHole().direction(0, 30).size(50).build())
 *         .vanillaSun(false).vanillaMoon(false).build())
 *     .initial("calmo")
 *     .build();
 * SkyProfiles.define(ResourceLocation.fromNamespaceAndPath("meumod", "arena"), CEU);
 * }</pre>
 *
 * Layers of a profile have no timeline: they must not read tracks or phases (their spin runs on the world clock).
 * Invalid input throws {@link IllegalArgumentException}.
 */
public final class SkyProfile {
    /** Most states per profile. */
    public static final int MAX_STATES = 16;
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,64}");

    /**
     * One state of a dimension's sky. Immutable.
     */
    public static final class State {
        private final List<Sky> layers;
        private final Atmosphere atmosphere;
        private final boolean vanillaSun, vanillaMoon, vanillaSky;

        private State(StateBuilder b) {
            layers = List.copyOf(b.layers);
            atmosphere = b.atmosphere;
            vanillaSun = b.vanillaSun;
            vanillaMoon = b.vanillaMoon;
            vanillaSky = b.vanillaSky;
        }

        /** New state builder: no layers, no atmosphere, vanilla sun, moon and sky visible. */
        public static StateBuilder builder() { return new StateBuilder(); }

        /** Sky layers of this state (at most {@link Sky#MAX_PER_EFFECT}). */
        public List<Sky> layers() { return layers; }
        /** Atmosphere of this state, or null. */
        public Atmosphere atmosphere() { return atmosphere; }
        /** True when the vanilla sun stays visible (false: the sky pass hides it, in any dimension). */
        public boolean vanillaSun() { return vanillaSun; }
        /** True when the vanilla moon stays visible. */
        public boolean vanillaMoon() { return vanillaMoon; }
        /**
         * True when the vanilla sky stays (false: in a dimension whose effects are
         * {@code ProfileSkyEffects}, the game does not draw its sky at all — gradient, sunrise, sun, moon, stars — and the
         * engine paints the gradient of the state's atmosphere, or the vanilla colors, instead).
         */
        public boolean vanillaSky() { return vanillaSky; }
    }

    /** Builder of a {@link State}. */
    public static final class StateBuilder {
        private final java.util.ArrayList<Sky> layers = new java.util.ArrayList<>();
        private Atmosphere atmosphere;
        private boolean vanillaSun = true, vanillaMoon = true, vanillaSky = true;

        private StateBuilder() {}

        /** Adds sky layers (constant: no tracks nor phase), up to {@link Sky#MAX_PER_EFFECT} in total. */
        public StateBuilder layers(Sky... values) {
            for (Sky layer : values) {
                Objects.requireNonNull(layer, "layer");
                if (layer.animated())
                    throw new IllegalArgumentException("Sky layers of a profile cannot read tracks or phases (no timeline)");
                layers.add(layer);
            }
            if (layers.size() > Sky.MAX_PER_EFFECT)
                throw new IllegalArgumentException("Expected at most " + Sky.MAX_PER_EFFECT + " layers per state");
            return this;
        }

        /** Atmosphere of the state (constant: no tracks nor phase). */
        public StateBuilder atmosphere(Atmosphere value) {
            if (Objects.requireNonNull(value).animated())
                throw new IllegalArgumentException("The atmosphere of a profile cannot read tracks or phases (no timeline)");
            atmosphere = value;
            return this;
        }

        /** Whether the vanilla sun stays visible (true by default). */
        public StateBuilder vanillaSun(boolean visible) { vanillaSun = visible; return this; }
        /** Whether the vanilla moon stays visible (true by default). */
        public StateBuilder vanillaMoon(boolean visible) { vanillaMoon = visible; return this; }
        /** Whether the whole vanilla sky stays (true by default); see {@link State#vanillaSky()}. */
        public StateBuilder vanillaSky(boolean visible) { vanillaSky = visible; return this; }

        /** Immutable state. */
        public State build() { return new State(this); }
    }

    private final Map<String, State> states;
    private final String initial;

    private SkyProfile(Builder b) {
        if (b.states.isEmpty()) throw new IllegalArgumentException("A sky profile needs at least one state");
        states = Collections.unmodifiableMap(new LinkedHashMap<>(b.states));
        initial = b.initial != null ? b.initial : b.states.keySet().iterator().next();
        if (!states.containsKey(initial)) throw new IllegalArgumentException("Unknown initial state: " + initial);
    }

    /** New profile builder. */
    public static Builder builder() { return new Builder(); }

    /** States by name, in declaration order. */
    public Map<String, State> states() { return states; }
    /** State the dimension starts in (the first one unless {@link Builder#initial} says otherwise). */
    public String initial() { return initial; }
    /** True when the profile has this state. */
    public boolean has(String state) { return states.containsKey(state); }
    /** A state by name; throws {@link IllegalArgumentException} when unknown. */
    public State state(String name) {
        State s = states.get(name);
        if (s == null) throw new IllegalArgumentException("Unknown sky state: " + name);
        return s;
    }

    /** Builder of a {@link SkyProfile}. */
    public static final class Builder {
        private final Map<String, State> states = new LinkedHashMap<>();
        private String initial;

        private Builder() {}

        /** Adds a named state ({@code [a-z0-9_]}, up to 64 characters), at most {@value #MAX_STATES}. */
        public Builder state(String name, State state) {
            if (name == null || !NAME.matcher(name).matches())
                throw new IllegalArgumentException("Invalid state name (expected [a-z0-9_], up to 64): " + name);
            if (states.putIfAbsent(name, Objects.requireNonNull(state)) != null)
                throw new IllegalArgumentException("Duplicate state: " + name);
            if (states.size() > MAX_STATES) throw new IllegalArgumentException("Expected at most " + MAX_STATES + " states");
            return this;
        }

        /** State the dimension starts in. */
        public Builder initial(String name) {
            initial = Objects.requireNonNull(name);
            return this;
        }

        /** Immutable profile; the initial state must exist. */
        public SkyProfile build() { return new SkyProfile(this); }
    }
}
