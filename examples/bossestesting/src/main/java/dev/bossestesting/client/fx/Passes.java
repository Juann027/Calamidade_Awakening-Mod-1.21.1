package dev.bossestesting.client.fx;

import dev.calamidade_awakening.api.v1.client.AddonLayers;
import net.minecraft.world.phys.Vec3;

/**
 * Typed helpers of the addon's post passes (assets/bossestesting/shaders/fx). Every helper checks the pass budget
 * ({@code passesLeft}) and the accessibility settings: full-screen changes scale with the screen-effects intensity,
 * no negative flip or arc strobe when flashes are hidden, no tearing when motion effects are off.
 */
public final class Passes {
    private Passes() {}

    static float r(int argb) { return ((argb >> 16) & 255) / 255f; }
    static float g(int argb) { return ((argb >> 8) & 255) / 255f; }
    static float b(int argb) { return (argb & 255) / 255f; }
    static float seconds(AddonLayers.Post post) { return (float) (post.realAge() / 20.0); }

    /** Danger circle on the ground around `center` (camera-relative). */
    public static void circle(AddonLayers.Post post, Vec3 center, float radius, float fill, float intensity, float urgency, int a, int b) {
        if (intensity <= .01f || post.passesLeft() <= 0) return;
        post.pass("bossestesting:bt_decal", u -> {
            u.v3("BtCenter", center);
            u.f4("BtShape", 0, radius, 0, 0);
            u.f4("BtState", Math.clamp(fill, 0, 1), Math.clamp(intensity, 0, 1), seconds(post), Math.clamp(urgency, 0, 1));
            u.f3("BtColorA", r(a), g(a), b(a));
            u.f3("BtColorB", r(b), g(b), b(b));
        });
    }

    /** Attack lane: from `center` forward along the instance yaw, `halfWidth` × `length` blocks. */
    public static void lane(AddonLayers.Post post, Vec3 center, float halfWidth, float length, float fill, float intensity, int a, int b) {
        if (intensity <= .01f || post.passesLeft() <= 0) return;
        float yaw = (float) Math.toRadians(post.yaw());
        post.pass("bossestesting:bt_decal", u -> {
            u.v3("BtCenter", center);
            u.f4("BtShape", 1, halfWidth, length, yaw);
            u.f4("BtState", Math.clamp(fill, 0, 1), Math.clamp(intensity, 0, 1), seconds(post), Math.clamp(fill, 0, 1));
            u.f3("BtColorA", r(a), g(a), b(a));
            u.f3("BtColorB", r(b), g(b), b(b));
        });
    }

    /** Volumetric shockwave (dome shell + ground ring + dust wall). */
    public static void shock(AddonLayers.Post post, Vec3 center, float radius, float thickness, float strength, float dome, float flash, float dust, int color) {
        if (strength <= .01f || post.passesLeft() <= 0) return;
        float screen = post.screenEffects();
        float fl = post.flashAllowed() ? flash : flash * .25f;
        post.pass("bossestesting:bt_shock", u -> {
            u.v3("BtCenter", center);
            u.f4("BtWave", radius, thickness, Math.clamp(strength, 0, 1), dome);
            u.f4("BtMisc", seconds(post), fl * screen, screen, dust);
            u.f3("BtColor", r(color), g(color), b(color));
        });
    }

    /** Custom impact frame (phase palettes). `t` = ticks since the impact; lasts `ticks`. */
    public static void impactFrame(AddonLayers.Post post, Vec3 center, double t, int ticks, float strength, boolean phase2) {
        if (t < 0 || t >= ticks || post.passesLeft() <= 0 || post.distance() > 64) return;
        float k = strength * post.screenEffects() * (post.flashAllowed() ? 1 : .4f) * (float) (1 - t / ticks * .5);
        if (k <= .01f) return;
        boolean invert = post.flashAllowed() && t >= 1 && t < 2.5;
        int ink = phase2 ? 0xff020a10 : 0xff120604, mid = phase2 ? 0xff20d0f0 : 0xffff7a1a, paper = phase2 ? 0xfff0fcff : 0xfffff0dc;
        post.pass("bossestesting:bt_impact", u -> {
            u.v3("BtCenter", center);
            u.f4("BtFrame", Math.min(1, k), invert ? 1 : 0, seconds(post), post.motionEffects() ? 1 : 0);
            u.f3("BtInk", r(ink), g(ink), b(ink));
            u.f3("BtMid", r(mid), g(mid), b(mid));
            u.f3("BtPaper", r(paper), g(paper), b(paper));
        });
        post.shake(k * .5f);
    }

    /** Heat shimmer around a capsule. */
    public static void heat(AddonLayers.Post post, Vec3 a, Vec3 bEnd, float radius, float strength, float glow, int tint) {
        if (strength <= .01f || post.passesLeft() <= 0 || !post.motionEffects()) return;
        post.pass("bossestesting:bt_heat", u -> {
            u.v3("BtA", a);
            u.v3("BtB", bEnd);
            u.f4("BtHeat", radius, Math.min(1, strength) * post.screenEffects(), seconds(post), glow);
            u.f3("BtTint", r(tint), g(tint), b(tint));
        });
    }

    /** Mechanical crisis overlay: chromatic split, tearing, arcs from `center`, alarm vignette. */
    public static void glitch(AddonLayers.Post post, Vec3 center, float glitch, float arcs, float alarm, int count, int arc, int alarmColor) {
        if (glitch + arcs + alarm <= .01f || post.passesLeft() <= 0 || post.distance() > 72) return;
        float screen = post.screenEffects();
        post.pass("bossestesting:bt_glitch", u -> {
            u.v3("BtCenter", center);
            u.f4("BtGlitch", glitch * screen, arcs * (post.flashAllowed() ? 1 : .5f), seconds(post), alarm * screen);
            u.f4("BtFlags", post.motionEffects() ? 1 : 0, post.flashAllowed() ? 1 : 0, count, (float) (post.instance() % 97));
            u.f3("BtArc", r(arc), g(arc), b(arc));
            u.f3("BtAlarm", r(alarmColor), g(alarmColor), b(alarmColor));
        });
    }

    /** Volumetric energy core (charging reactor). */
    public static void core(AddonLayers.Post post, Vec3 center, float radius, float charge, float instability, int inner, int outer) {
        if (charge <= .01f || post.passesLeft() <= 0) return;
        post.pass("bossestesting:bt_core", u -> {
            u.v3("BtCenter", center);
            u.f4("BtCore", radius, charge, seconds(post), instability);
            u.f3("BtInner", r(inner), g(inner), b(inner));
            u.f3("BtOuter", r(outer), g(outer), b(outer));
        });
    }
}
