package dev.calamidade_awakening;

import dev.calamidade_awakening.api.AtmosphereMath;
import dev.calamidade_awakening.api.v1.Atmosphere;
import dev.calamidade_awakening.api.v1.Atmosphere.Param;
import dev.calamidade_awakening.api.v1.Atmosphere.Rgb;
import dev.calamidade_awakening.api.v1.Atmosphere.State;
import dev.calamidade_awakening.api.v1.Timeline;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/** Atmosphere (0.8.0): pure math, run by FrameBudgetCheck inside checkBudget. */
final class AtmosphereCheck {
    private static final float EPS = 1e-4f;
    private static final Timeline.Sampler NONE = Timeline.empty(10).at(0);

    private AtmosphereCheck() {}

    static void run() {
        validation();
        constants();
        tracks();
        zeroWeight();
        mixing();
        limits();
        engineHelpers();
    }

    private static void validation() {
        expectRejected(() -> Atmosphere.builder().sky(0, 0, -.01f), "sky blend below 0");
        expectRejected(() -> Atmosphere.builder().sky(0, 0, 1.01f), "sky blend above 1");
        expectRejected(() -> Atmosphere.builder().sky(0, 0, Float.NaN), "sky blend NaN");
        expectRejected(() -> Atmosphere.builder().fog(0, .5f, 10, 10), "fog start == end");
        expectRejected(() -> Atmosphere.builder().fog(0, .5f, -1, 10), "negative fog start");
        expectRejected(() -> Atmosphere.builder().fog(0, .5f, 0, Atmosphere.MAX_DISTANCE + 1), "fog end too far");
        expectRejected(() -> Atmosphere.builder().fog(0, 1.5f, 0, 10), "fog density above 1");
        expectRejected(() -> Atmosphere.builder().fog(0, .5f, Float.NaN, 10), "fog start NaN");
        expectRejected(() -> Atmosphere.builder().exposure(-.1f), "negative exposure");
        expectRejected(() -> Atmosphere.builder().exposure(Atmosphere.MAX_EXPOSURE + .1f), "exposure too high");
        expectRejected(() -> Atmosphere.builder().exposure(Float.POSITIVE_INFINITY), "infinite exposure");
        expectRejected(() -> Atmosphere.builder().ambient(0, 2), "ambient strength above 1");
        expectRejected(() -> Atmosphere.builder().stars(-1), "negative stars");
        expectRejected(() -> Atmosphere.builder().track(Param.STARS, "Bad-Name"), "invalid track name");
        expectRejected(() -> Atmosphere.builder().track(Param.STARS, null), "null track name");
        expectRejected(() -> Atmosphere.builder().track(Param.STARS, "a").track(Param.STARS, "b"), "parameter bound twice");
        expectRejected(() -> Atmosphere.builder().weightPhase("x").weightPhase("y"), "weight phase twice");
        expectRejected(() -> Atmosphere.builder().weightPhase(""), "empty phase name");
        // A track may feed several parameters.
        var shared = Atmosphere.builder().track(Param.STARS, "ceu").track(Param.SKY_BLEND, "ceu").build();
        require(shared.track(Param.STARS).equals("ceu") && shared.track(Param.SKY_BLEND).equals("ceu")
            && shared.track(Param.EXPOSURE) == null && shared.animated(), "shared track");
        // Bindings are checked against the timeline at registration.
        var timeline = Timeline.builder(60).phase("impacto", 10, 30, 2, 4).track("ceu", keyframe(0, 0f)).build();
        shared.validate(timeline);
        expectRejected(() -> shared.validate(null), "animated atmosphere without timeline");
        expectRejected(() -> Atmosphere.builder().track(Param.FOG_DENSITY, "nevoa").build().validate(timeline), "unknown track");
        expectRejected(() -> Atmosphere.builder().weightPhase("eco").build().validate(timeline), "unknown phase");
        Atmosphere.builder().weightPhase("impacto").build().validate(timeline);
        Atmosphere.builder().stars(.5f).build().validate(null);
        require(!Atmosphere.builder().stars(.5f).build().animated(), "constant atmosphere not animated");
        require(shared.tracks().keySet().iterator().next() == Param.SKY_BLEND, "bindings in Param order");
        try {
            shared.tracks().put(Param.WEIGHT, "x");
            throw new AssertionError("tracks map mutable");
        } catch (UnsupportedOperationException expected) {}
        // State validates its own ranges.
        expectRejected(() -> new State(1.5f, Rgb.BLACK, Rgb.BLACK, 0, Rgb.WHITE, 0, 1, 2, 1, Rgb.WHITE, 0, 0), "state weight");
        expectRejected(() -> new State(1, Rgb.BLACK, Rgb.BLACK, 0, Rgb.WHITE, 0, 5, 5, 1, Rgb.WHITE, 0, 0), "state fog span");
        expectRejected(() -> new Rgb(1.1f, 0, 0), "rgb range");
    }

    private static void constants() {
        var a = Atmosphere.builder().sky(0xff102030, 0xffffb050, .5f).fog(0x80ff0000, .75f, 8, 64)
            .exposure(1.25f).ambient(0xff00ff00, .3f).stars(.4f).build();
        var s = a.sample(NONE, 1);
        require(s.weight() == 1 && s.skyBlend() == .5f && s.fogDensity() == .75f && s.fogStart() == 8
            && s.fogEnd() == 64 && s.exposure() == 1.25f && s.ambientStrength() == .3f && s.stars() == .4f, "constants");
        require(s.skyTop().argb() == 0xff102030 && s.skyHorizon().argb() == 0xffffb050, "sky colors round trip");
        require(s.fogColor().argb() == 0xffff0000, "alpha ignored");
        require(s.ambientTint().equals(Rgb.of(0xff00ff00)), "tint");
        require(near(Rgb.of(0xffffffff).luminance(), 1) && Rgb.BLACK.luminance() == 0, "luminance");
        // The envelope is the global weight, clamped.
        require(a.sample(NONE, .4f).weight() == .4f && a.sample(NONE, 3).weight() == 1
            && a.sample(NONE, -1).weight() == 0 && a.sample(NONE, Float.NaN).weight() == 0, "envelope weight");
        // Neutral builder.
        var neutral = Atmosphere.builder().build().sample(NONE, 1);
        require(neutral.skyBlend() == 0 && neutral.fogDensity() == 0 && neutral.exposure() == 1
            && neutral.ambientStrength() == 0 && neutral.stars() == 0, "neutral defaults");
    }

    private static void tracks() {
        var timeline = Timeline.builder(100)
            .phase("impacto", 40, 60, 0, 0)
            .track("nevoa", keyframe(0, 0f), keyframe(50, 1f))
            .track("perto", keyframe(0, 300f), keyframe(100, 4f))
            .track("longe", keyframe(0, 10f))
            .track("brilho", keyframe(0, 1f), keyframe(50, 9f))
            .track("peso", keyframe(0, .5f))
            .build();
        var a = Atmosphere.builder().fog(0xffffffff, .2f, 8, 64)
            .track(Param.FOG_DENSITY, "nevoa").track(Param.EXPOSURE, "brilho").track(Param.WEIGHT, "peso")
            .weightPhase("impacto").build();
        a.validate(timeline);
        require(a.sample(timeline.at(30), 1).weight() == 0, "outside the weight phase");
        var at45 = a.sample(timeline.at(45), .8f);
        require(near(at45.weight(), .4f), "envelope * phase * weight track");
        require(near(at45.fogDensity(), .9f), "density from track");
        require(at45.fogStart() == 8 && at45.fogEnd() == 64, "constants kept beside tracks");
        // Track values are clamped to the parameter range.
        require(a.sample(timeline.at(55), 1).exposure() == Atmosphere.MAX_EXPOSURE, "exposure clamped");
        // Fog distances from tracks keep end beyond start.
        var distances = Atmosphere.builder().fog(0, 1, 1, 2).track(Param.FOG_START, "perto").track(Param.FOG_END, "longe").build();
        var d = distances.sample(timeline.at(0), 1);
        require(d.fogStart() == 300 && d.fogEnd() == 300 + Atmosphere.MIN_FOG_SPAN, "end pushed past start");
        var far = Atmosphere.builder().fog(0, 1, 1, 2).track(Param.FOG_START, "brilho").build();
        require(far.sample(timeline.at(50), 1).fogEnd() == 9 + Atmosphere.MIN_FOG_SPAN, "constant end pushed past track start");
        var top = Atmosphere.builder().track(Param.FOG_START, "perto").build();
        var huge = Timeline.builder(10).track("perto", keyframe(0, 99999f)).build();
        var t = top.sample(huge.at(0), 1);
        require(t.fogStart() == Atmosphere.MAX_DISTANCE - Atmosphere.MIN_FOG_SPAN && t.fogEnd() == Atmosphere.MAX_DISTANCE,
            "start clamped below the maximum distance");
        require(Param.STARS.clamp(Float.NaN) == 0 && Param.EXPOSURE.clamp(-3) == 0 && Param.FOG_END.clamp(1e9f) == Atmosphere.MAX_DISTANCE,
            "param clamp");
        expectRejected(() -> a.sample(Timeline.empty(10).at(0), 1), "sampling a track the timeline lacks");
    }

    private static void zeroWeight() {
        var a = Atmosphere.builder().fog(0xff0000ff, 1, 4, 8).exposure(2).build();
        var off = a.sample(NONE, 0);
        require(off.weight() == 0 && !off.active(), "zero weight inactive");
        require(Atmosphere.mix(List.of(off)) == State.NEUTRAL, "zero weight mixes to neutral");
        require(Atmosphere.mix(List.of()) == State.NEUTRAL, "empty list neutral");
        var withNull = new ArrayList<State>();
        withNull.add(null);
        require(Atmosphere.mix(withNull) == State.NEUTRAL, "null ignored");
        require(!State.NEUTRAL.active() && State.NEUTRAL.exposure() == 1, "neutral state");
        require(!a.sample(NONE, Atmosphere.ACTIVE_WEIGHT * .5f).active() && a.sample(NONE, .01f).active(), "active threshold");
    }

    private static void mixing() {
        var gold = Atmosphere.builder().sky(0xffffb000, 0xffff8000, 1).exposure(2).stars(1).build();
        var fog = Atmosphere.builder().fog(0xff0000ff, 1, 10, 50).ambient(0xffff0000, 1).build();
        // A lone state keeps its colors and scales its amounts by the weight.
        var half = Atmosphere.mix(List.of(gold.sample(NONE, .5f)));
        require(half.weight() == .5f && near(half.skyBlend(), .5f) && near(half.exposure(), 1.5f) && near(half.stars(), .5f)
            && half.skyTop().equals(Rgb.of(0xffffb000)), "lone state");
        require(half.fogDensity() == 0 && half.ambientStrength() == 0, "lone state without fog or tint");
        // Two at full weight: means of the amounts, each feature colored only by the state that uses it.
        var both = Atmosphere.mix(List.of(gold.sample(NONE, 1), fog.sample(NONE, 1)));
        require(both.weight() == 1 && near(both.skyBlend(), .5f) && near(both.fogDensity(), .5f)
            && near(both.exposure(), 1.5f) && near(both.ambientStrength(), .5f), "two full states");
        require(both.skyTop().equals(Rgb.of(0xffffb000)) && both.fogColor().equals(Rgb.of(0xff0000ff))
            && both.ambientTint().equals(Rgb.of(0xffff0000)), "colors not pulled by unused features");
        require(both.fogStart() == 10 && both.fogEnd() == 50, "fog distances from the foggy state");
        // Crossfade keeps full strength.
        for (float k = 0; k <= 1.0001f; k += .125f) {
            var m = Atmosphere.mix(List.of(gold.sample(NONE, 1 - k), fog.sample(NONE, k)));
            require(near(m.weight(), 1) || (k == 0 || k > .999f), "crossfade weight " + k);
            require(near(m.skyBlend(), 1 - k) && near(m.fogDensity(), Math.min(1, k)), "crossfade amounts " + k);
        }
        // Weighted color interpolation between two skies.
        var blue = Atmosphere.builder().sky(0xff0000ff, 0xff0000ff, 1).build();
        var red = Atmosphere.builder().sky(0xffff0000, 0xffff0000, 1).build();
        var m = Atmosphere.mix(List.of(red.sample(NONE, .75f), blue.sample(NONE, .25f)));
        require(near(m.skyTop().r(), .75f) && near(m.skyTop().b(), .25f) && m.weight() == 1, "weighted colors");
        // Only the two heaviest count; ties keep list order.
        var green = Atmosphere.builder().sky(0xff00ff00, 0xff00ff00, 1).build();
        var three = Atmosphere.mix(List.of(green.sample(NONE, .1f), red.sample(NONE, .6f), blue.sample(NONE, .4f)));
        require(three.skyTop().g() == 0 && near(three.skyTop().r(), .6f), "third ignored");
        var tie = Atmosphere.mix(List.of(red.sample(NONE, .5f), blue.sample(NONE, .5f), green.sample(NONE, .5f)));
        require(tie.skyTop().g() == 0 && near(tie.skyTop().r(), .5f) && near(tie.skyTop().b(), .5f), "ties keep order");
        // Order of the two heaviest does not change the result.
        var ab = Atmosphere.mix(List.of(gold.sample(NONE, .7f), fog.sample(NONE, .2f)));
        var ba = Atmosphere.mix(List.of(fog.sample(NONE, .2f), gold.sample(NONE, .7f)));
        require(ab.equals(ba), "symmetric");
        // Fog distances weighted by fog use.
        var nearFog = Atmosphere.builder().fog(0xffffffff, 1, 0, 20).build();
        var farFog = Atmosphere.builder().fog(0xffffffff, 1, 100, 200).build();
        var fogs = Atmosphere.mix(List.of(nearFog.sample(NONE, 1), farFog.sample(NONE, 1)));
        require(fogs.fogStart() == 50 && fogs.fogEnd() == 110, "fog distances mean");
    }

    private static void limits() {
        var s = Atmosphere.builder().fog(0xff808080, .5f, 20, 60).build().sample(NONE, 1);
        // Game fog moved halfway toward the atmosphere, never thinner than the game's own.
        require(s.fogNear(180) == 100 && s.fogFar(180, 200) == 130, "fog halfway");
        require(s.fogNear(10) == 10, "never thinner (near)");
        require(s.fogFar(10, 30) == 30, "never thinner (far)");
        var full = Atmosphere.builder().fog(0xff808080, 1, 20, 60).build().sample(NONE, 1);
        require(full.fogNear(180) == 20 && full.fogFar(180, 200) == 60, "full density uses the atmosphere distances");
        var zero = Atmosphere.builder().fog(0xff808080, 0, 20, 60).build().sample(NONE, 1);
        require(zero.fogNear(180) == 180 && zero.fogFar(180, 200) == 200, "zero density keeps the game fog");
        require(full.fogFar(70, 200) > full.fogNear(70), "far stays beyond near");
        require(full.fogColorOver(Rgb.WHITE).equals(Rgb.of(0xff808080)) && zero.fogColorOver(Rgb.WHITE).equals(Rgb.WHITE)
            && near(s.fogColorOver(Rgb.WHITE).r(), (1 + 128 / 255f) / 2), "fog color");
        require(s.screenFog(0) == 0 && s.screenFog(20) == 0 && near(s.screenFog(40), .25f) && s.screenFog(60) == .5f
            && s.screenFog(1e9) == .5f, "screen fog capped by density");
        for (double x = 20; x < 60; x += .5) require(s.screenFog(x) <= s.screenFog(x + .5), "screen fog monotonic");
        // Mixing stays within ranges at the extremes.
        var max = Atmosphere.builder().sky(-1, -1, 1).fog(-1, 1, 0, Atmosphere.MAX_DISTANCE).exposure(Atmosphere.MAX_EXPOSURE)
            .ambient(-1, 1).stars(1).build().sample(NONE, 1);
        var min = Atmosphere.builder().exposure(0).build().sample(NONE, 1);
        var m = Atmosphere.mix(Arrays.asList(max, max, min));
        require(m.exposure() == Atmosphere.MAX_EXPOSURE && m.weight() == 1, "extremes");
        var dark = Atmosphere.mix(List.of(min, min));
        require(dark.exposure() == 0, "zero exposure");
    }

    private static void engineHelpers() {
        require(AtmosphereMath.rangeFade(0, 128) == 1 && AtmosphereMath.rangeFade(96, 128) == 1
            && near(AtmosphereMath.rangeFade(112, 128), .5f) && AtmosphereMath.rangeFade(128, 128) == 0
            && AtmosphereMath.rangeFade(500, 128) == 0 && AtmosphereMath.rangeFade(Double.NaN, 128) == 0
            && AtmosphereMath.rangeFade(1, 0) == 0, "range fade");
        require(AtmosphereMath.approach(1, 3, 4, .25f) == 2 && AtmosphereMath.approach(1, 1.1f, 4, .25f) == 1.1f
            && AtmosphereMath.approach(2, 0, 1, .5f) == 1.5f && AtmosphereMath.approach(Float.NaN, 2, 1, .1f) == 2
            && AtmosphereMath.approach(1, 2, -1, .5f) == 1, "exposure ramp");
        // A brusque change (1 -> 2.5 in two ticks) takes 60 ticks at the calm rate.
        float e = 1;
        int ticks = 0;
        while (e < 2.5f && ticks < 1000) { e = AtmosphereMath.approach(e, 2.5f, 1, AtmosphereMath.CALM_EXPOSURE_PER_TICK); ticks++; }
        require(ticks >= 60 && ticks <= 61, "calm exposure rate: " + ticks);
        require(AtmosphereMath.scaledExposure(2, 0) == 1 && AtmosphereMath.scaledExposure(2, .5f) == 1.5f
            && AtmosphereMath.scaledExposure(.5f, 1) == .5f, "exposure intensity");
    }

    private static boolean near(float a, float b) { return Math.abs(a - b) < EPS; }

    private static void expectRejected(Runnable action, String name) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(name);
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
