package dev.calamidade_awakening;

import dev.calamidade_awakening.api.SkyProfileMath.Transition;
import dev.calamidade_awakening.api.v1.Atmosphere;
import dev.calamidade_awakening.api.v1.Sky;
import dev.calamidade_awakening.api.v1.SkyProfile;
import dev.calamidade_awakening.api.v1.SkyProfiles;
import dev.calamidade_awakening.net.SkyProfileSync;
import dev.calamidade_awakening.world.SkyProfileServer;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Dimension skies (0.12.0): profiles, state transitions, persistence and the sync packet. */
final class SkyProfileCheck {
    private SkyProfileCheck() {}

    static void run() {
        profiles();
        transitions();
        persistence();
        System.out.println("SkyProfileCheck PASS");
    }

    private static void profiles() {
        var calm = SkyProfile.State.builder().layers(Sky.aurora().build()).build();
        var chaos = SkyProfile.State.builder().layers(Sky.blackHole().build(), Sky.bolts().build())
            .atmosphere(Atmosphere.builder().sky(0xff100010, 0xff600020, .8f).build()).vanillaSun(false).vanillaMoon(false)
            .vanillaSky(false).build();
        var profile = SkyProfile.builder().state("calmo", calm).state("caos", chaos).build();
        require(profile.initial().equals("calmo") && profile.has("caos") && profile.states().size() == 2, "first state is initial");
        require(SkyProfile.builder().state("a", calm).state("b", chaos).initial("b").build().initial().equals("b"), "initial");
        require(calm.vanillaSun() && calm.vanillaMoon() && calm.vanillaSky() && calm.atmosphere() == null, "state defaults");
        require(!chaos.vanillaSky() && chaos.layers().size() == 2, "state values");
        expectRejected(() -> SkyProfile.builder().build(), "empty profile");
        expectRejected(() -> SkyProfile.builder().state("a", calm).initial("b").build(), "unknown initial");
        expectRejected(() -> SkyProfile.builder().state("a", calm).state("a", calm), "duplicate state");
        expectRejected(() -> SkyProfile.builder().state("Bad", calm), "bad name");
        expectRejected(() -> profile.state("nada"), "unknown state");
        expectRejected(() -> SkyProfile.State.builder().layers(Sky.portal().track(Sky.Param.SIZE, "t").build()), "animated layer");
        expectRejected(() -> SkyProfile.State.builder().atmosphere(Atmosphere.builder().weightPhase("f").build()),
            "animated atmosphere");
        Sky[] seven = new Sky[Sky.MAX_PER_EFFECT + 1];
        java.util.Arrays.fill(seven, Sky.portal().build());
        expectRejected(() -> SkyProfile.State.builder().layers(seven), "too many layers");
        var dimension = ResourceLocation.fromNamespaceAndPath("checksky", "arena");
        SkyProfiles.define(dimension, profile);
        require(SkyProfiles.profile(dimension).orElseThrow() == profile, "defined");
        expectRejected(() -> SkyProfiles.define(dimension, profile), "one profile per dimension");
        require(SkyProfiles.profile(ResourceLocation.withDefaultNamespace("overworld")).isEmpty(), "no profile");
    }

    private static void transitions() {
        var settled = Transition.settled("calmo");
        require(settled.weightTo(0) == 1 && settled.weightFrom(0) == 0 && settled.done(-5), "settled");
        var t = settled.toward("caos", 1000, 100);
        require(t.from().equals("calmo") && t.to().equals("caos") && t.start() == 1000, "toward");
        require(t.weightTo(1000) == 0 && t.weightTo(1100) == 1 && Math.abs(t.weightTo(1050) - .5f) < 1e-6, "smoothstep");
        require(Math.abs(t.weightTo(1025) + t.weightFrom(1025) - 1) < 1e-6, "weights add up to 1");
        require(t.weightTo(1025) < .25f, "smooth start (smoothstep below linear)");
        require(t.dominant(1049).equals("calmo") && t.dominant(1050).equals("caos"), "dominant");
        require(t.toward("caos", 1060, 40) == t, "same target: unchanged");
        // A change in the middle fades from the state that shows more.
        var back = t.toward("ameaca", 1030, 60);
        require(back.from().equals("calmo") && back.to().equals("ameaca"), "mid-transition: from the dominant");
        require(t.toward("ameaca", 1070, 60).from().equals("caos"), "past half: from the target");
        var instant = settled.toward("caos", 5, 0);
        require(instant.weightTo(5) == 1 && instant.done(5), "0 ticks is instant");
        require(new Transition("a", "b", 0, -10).ticks() == 0, "negative ticks clamp to 0");
    }

    private static void persistence() {
        var t = new Transition("calmo", "caos", 123456789L, 240);
        require(t.equals(SkyProfileServer.read(SkyProfileServer.write(t, new CompoundTag()))), "transition round-trips");
        require(SkyProfileServer.read(new CompoundTag()) == null, "no saved state");
        var sync = new SkyProfileSync(ResourceLocation.fromNamespaceAndPath("vfxsample", "dimensao_teste"), "calmo", "caos",
            987654321L, 100);
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        sync.write(buf);
        require(SkyProfileSync.read(buf).equals(sync) && buf.readableBytes() == 0, "sky_profile_v1 round-trips");
    }

    private static void expectRejected(Runnable action, String test) {
        try {
            action.run();
            throw new AssertionError(test + " accepted");
        } catch (IllegalArgumentException expected) {}
    }

    private static void require(boolean condition, String test) {
        if (!condition) throw new AssertionError(test);
    }
}
