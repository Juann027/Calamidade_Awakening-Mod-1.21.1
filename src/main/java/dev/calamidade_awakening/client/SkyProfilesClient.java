package dev.calamidade_awakening.client;

import dev.calamidade_awakening.api.SkyMath;
import dev.calamidade_awakening.api.SkyProfileMath;
import dev.calamidade_awakening.api.v1.Atmosphere;
import dev.calamidade_awakening.api.v1.SkyProfile;
import dev.calamidade_awakening.api.v1.SkyProfiles;
import dev.calamidade_awakening.net.SkyProfileSync;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dimension skies on this client (0.12.0): the transition of each dimension received from the server (the initial
 * state of the profile until one arrives). Feeds the sky pass (both states of a transition, weighted), the atmosphere
 * mix, the vanilla sun and moon to hide and whether the whole vanilla sky is replaced ({@code ProfileSkyEffects}).
 */
final class SkyProfilesClient {
    /** Rank of profile layers in the sky selection: always kept, above global events. */
    static final int RANK = 1000;
    private final Map<ResourceLocation, SkyProfileMath.Transition> transitions = new HashMap<>();

    void receive(SkyProfileSync packet) {
        transitions.put(packet.dimension(), new SkyProfileMath.Transition(packet.from(), packet.to(), packet.start(), packet.ticks()));
    }

    void clear() { transitions.clear(); }

    /** The profile of the level and its transition, or null without a profile. */
    private record Current(ResourceLocation dimension, SkyProfile profile, SkyProfileMath.Transition transition) {}

    private Current current(ClientLevel level) {
        if (level == null) return null;
        ResourceLocation dimension = level.dimension().location();
        SkyProfile profile = SkyProfiles.profile(dimension).orElse(null);
        if (profile == null) return null;
        var t = transitions.getOrDefault(dimension, SkyProfileMath.Transition.settled(profile.initial()));
        if (!profile.has(t.from()) || !profile.has(t.to())) t = SkyProfileMath.Transition.settled(profile.initial());
        return new Current(dimension, profile, t);
    }

    /** Weighted states of the level now: (state, weight), up to two. */
    private List<Map.Entry<SkyProfile.State, Float>> states(Current c, double now) {
        List<Map.Entry<SkyProfile.State, Float>> out = new ArrayList<>(2);
        float to = c.transition().weightTo(now), from = c.transition().weightFrom(now);
        if (to > 0) out.add(Map.entry(c.profile().state(c.transition().to()), to));
        if (from > 0) out.add(Map.entry(c.profile().state(c.transition().from()), from));
        return out;
    }

    /** Current state name of the level (F7 and tests), or null without a profile. */
    String state(ClientLevel level) {
        Current c = current(level);
        return c == null ? null : c.transition().dominant(level.getGameTime());
    }

    /** Sky layers of the level's profile, weighted by the transition. */
    List<SkyMath.Candidate<SkyFrame.Resolved>> skies(ClientLevel level, Vec3 camera, float partial, double timeOfDay) {
        Current c = current(level);
        if (c == null) return List.of();
        double now = level.getGameTime() + partial;
        long seed = c.dimension().hashCode() * 0x9E3779B97F4A7C15L;
        List<SkyMath.Candidate<SkyFrame.Resolved>> out = new ArrayList<>();
        var sampler = dev.calamidade_awakening.api.v1.Timeline.empty(1).at(0);
        int index = 0;
        for (var entry : states(c, now)) {
            for (var layer : entry.getKey().layers()) {
                var state = layer.sample(sampler, entry.getValue());
                index++;
                if (!state.visible()) continue;
                var anchor = StageScene.anchorDirection(layer, camera, camera, dev.calamidade_awakening.api.Basis.of(0, 0), timeOfDay);
                if (anchor == null) continue;
                long layerSeed = seed + index * 31L;
                out.add(new SkyMath.Candidate<>(new SkyFrame.Resolved(layer, state, state.direction(anchor),
                    (layerSeed & 0xFFFFL) / 65535f, layerSeed, now, Long.MIN_VALUE + index, false), false,
                    state.weight(), Long.MIN_VALUE + index, RANK));
            }
        }
        return out;
    }

    /** Atmospheres of the level's profile, weighted by the transition. */
    List<Atmosphere.State> atmospheres(ClientLevel level, float partial) {
        Current c = current(level);
        if (c == null) return List.of();
        var sampler = dev.calamidade_awakening.api.v1.Timeline.empty(1).at(0);
        List<Atmosphere.State> out = new ArrayList<>(2);
        for (var entry : states(c, level.getGameTime() + partial))
            if (entry.getKey().atmosphere() != null) out.add(entry.getKey().atmosphere().sample(sampler, entry.getValue()));
        return out;
    }

    /** How much of the vanilla sun (index 0) and moon (index 1) the profile hides now, 0..1. */
    float[] hidden(ClientLevel level, float partial) {
        Current c = current(level);
        float sun = 0, moon = 0;
        if (c != null) for (var entry : states(c, level.getGameTime() + partial)) {
            if (!entry.getKey().vanillaSun() || !entry.getKey().vanillaSky()) sun += entry.getValue();
            if (!entry.getKey().vanillaMoon() || !entry.getKey().vanillaSky()) moon += entry.getValue();
        }
        return new float[] {Math.min(sun, 1), Math.min(moon, 1)};
    }

    /** True while the states that drop the vanilla sky weigh at least half (ProfileSkyEffects then draws no sky). */
    boolean replacesSky(ClientLevel level) {
        Current c = current(level);
        if (c == null) return false;
        float w = 0;
        for (var entry : states(c, level.getGameTime())) if (!entry.getKey().vanillaSky()) w += entry.getValue();
        return w >= .5f;
    }
}
