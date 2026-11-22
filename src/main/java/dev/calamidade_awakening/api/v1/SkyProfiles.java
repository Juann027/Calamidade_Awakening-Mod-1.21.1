package dev.calamidade_awakening.api.v1;

import dev.calamidade_awakening.world.SkyProfileServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Sky of each dimension (0.12.0): one {@link SkyProfile} per dimension, with named states and smooth transitions.
 * Define the profile during mod construction on both sides ({@link #define}); on the server,
 * {@link #setState(MinecraftServer, ResourceLocation, String, int)} crossfades to another state over N ticks. The state
 * is synced to every player in that dimension (also to those who log in, arrive from another dimension or respawn
 * later), keeps running on the server's game time and is saved with the world, so a restart resumes it. Uses the
 * channel {@code sky_profile_v1}: run the same engine version on the server and every client.
 *
 * <p>Profiles defined in code; datapack JSON profiles are on the ROADMAP. To let the engine replace the whole vanilla
 * sky of your own dimension (states with {@code vanillaSky(false)}), give its dimension type an {@code effects} id and
 * register {@code ProfileSkyEffects} for it (see {@code AddonLayers.skyEffects}).
 *
 * <pre>{@code
 * SkyProfiles.define(ARENA, CEU);                                    // mod constructor
 * SkyProfiles.setState(server, ARENA, "caos", 100);                  // server: 5 s to the chaos sky
 * }</pre>
 */
public final class SkyProfiles {
    private static final Map<ResourceLocation, SkyProfile> PROFILES = new LinkedHashMap<>();
    /** Longest transition, ticks (10 minutes). */
    public static final int MAX_TICKS = 12000;

    private SkyProfiles() {}

    /** Registers the profile of a dimension (one per dimension). Call during mod construction, on both sides. */
    public static synchronized void define(ResourceLocation dimension, SkyProfile profile) {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(profile);
        if (PROFILES.putIfAbsent(dimension, profile) != null)
            throw new IllegalArgumentException("Dimension already has a sky profile: " + dimension);
    }

    /** The profile of a dimension, if any. */
    public static synchronized Optional<SkyProfile> profile(ResourceLocation dimension) {
        return Optional.ofNullable(PROFILES.get(Objects.requireNonNull(dimension)));
    }

    /** Every defined profile, by dimension. */
    public static synchronized Map<ResourceLocation, SkyProfile> profiles() { return Map.copyOf(PROFILES); }

    /**
     * Crossfades the sky of {@code dimension} to {@code state} in {@code ticks} (0..{@value #MAX_TICKS}; 0 is instant).
     * A change in the middle of another fades from the state that shows more. Returns false when the dimension has no
     * profile or is not loaded; unknown states throw {@link IllegalArgumentException}. Server thread.
     */
    public static boolean setState(MinecraftServer server, ResourceLocation dimension, String state, int ticks) {
        if (ticks < 0 || ticks > MAX_TICKS) throw new IllegalArgumentException("Expected 0.." + MAX_TICKS + " ticks: " + ticks);
        return SkyProfileServer.setState(Objects.requireNonNull(server), Objects.requireNonNull(dimension),
            Objects.requireNonNull(state), ticks);
    }

    /** The state the sky of {@code dimension} is in or going to, if it has a profile. Server thread. */
    public static Optional<String> state(MinecraftServer server, ResourceLocation dimension) {
        return SkyProfileServer.state(Objects.requireNonNull(server), Objects.requireNonNull(dimension));
    }
}
