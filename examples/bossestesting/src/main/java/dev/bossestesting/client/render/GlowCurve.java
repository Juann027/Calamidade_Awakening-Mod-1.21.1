package dev.bossestesting.client.render;

import dev.bossestesting.ai.Brain;
import dev.bossestesting.anim.Clip;
import dev.bossestesting.entity.GuilherminoBoss;
import dev.bossestesting.entity.GuilherminoFinale;

import static dev.bossestesting.anim.Anims.*;

/**
 * Emissive intensity of the glowmasks, derived on each client from the synced clip, its age and the state bits (no
 * extra packets): charges ramp up, shots and surges spike, the overload pulses, the orange reactor dies at
 * pause_start, the cyan one ignites after the swap, the red emergency lights blink faster when Overloaded.
 * Values: 0 = off, 1 = the glowmask as authored, above 1 adds an additive overdrive pass.
 */
public final class GlowCurve {
    private GlowCurve() {}

    /** Ticks since a clip event as the server schedules it ({@link Brain#LEAD}); the client sees the clip one tick later. */
    static float since(Clip c, String event, float age) { return age - (Brain.LEAD - 1 + c.at(event)); }

    static float spike(float since, float peak, float decay) {
        return since < 0 ? 0 : peak * (float) Math.exp(-since / decay);
    }

    static float ramp(float since, float length) { return Math.clamp(since / length, 0, 1); }

    static float flicker(GuilherminoBoss b, float time, float amount) {
        double n = Math.sin(time * 1.7 + b.getId()) * Math.sin(time * 4.3 + b.getId() * 3) * Math.sin(time * 11.1);
        return (float) (1 - amount * (.5 + .5 * n));
    }

    /** Main glow (orange reactor of phase 1, cyan reactor of phase 2). */
    public static float main(GuilherminoBoss b, float partial) {
        float time = b.tickCount + partial;
        Clip c = b.clip();
        float age = c == null ? 0 : b.clipAge(partial);
        if (b.flag(GuilherminoBoss.F_REACTOR_DARK)) return .04f * flicker(b, time, 1);
        float base = 1;
        if (b instanceof GuilherminoFinale) {
            if (b.flag(GuilherminoBoss.F_IGNITING)) base = Math.clamp((time - ignitingStart(b)) / 26f, 0, 1) * flicker(b, time, .5f) * 1.3f;
            if (b.flag(GuilherminoBoss.F_OVERLOADED)) base = 1.35f + .35f * (float) Math.sin(time * .55);
        }
        if (c == null) return base * (b.isDeadOrDying() ? flicker(b, time, .9f) : 1);
        return base + boost(b, c, age, time);
    }

    private static float boost(GuilherminoBoss b, Clip c, float age, float time) {
        if (c == JG_REACTOR_CHARGE) return 1.2f * ramp(age, c.ticks()) + spike(since(c, "surge_1", age), .6f, 4) + spike(since(c, "surge_2", age), .7f, 4)
            + spike(since(c, "surge_3", age), .8f, 4) + spike(since(c, "full_charge", age), 1, 6);
        if (c == JG_REACTOR_FIRE) return spike(since(c, "fire", age), 1.6f, 10) + (age < since(c, "fire", 0) * -1 ? 1.2f : 0);
        if (c == JG_GROUND_SLAM) return .6f * ramp(since(c, "windup_start", age), 30) + spike(since(c, "impact", age), 1.2f, 6);
        if (c == JG_SPAWN_LANDING) return .8f * (age < Brain.LEAD + c.at("impact") ? 1 : 0) + spike(since(c, "impact", age), 1.4f, 8);
        if (c == JG_STAGGER || c == JG_DEATH) return -1 + flicker(b, time * 3, .95f);
        if (c == JG_PHASE_TRANSITION) return since(c, "pilot_regains_control", age) < 0 ? -1 + flicker(b, time * 3, .9f) : spike(since(c, "roar_peak", age), 1.2f, 12);
        if (c == JG_OVERLOAD) {
            if (since(c, "pause_start", age) >= 0) return -1;
            float s = spike(since(c, "reactor_surge", age), 1.2f, 8);
            for (String p : new String[]{"pulse_1", "pulse_2", "pulse_3", "pulse_4", "pulse_5", "pulse_6", "pulse_7", "pulse_8_peak"})
                s += spike(since(c, p, age), p.endsWith("peak") ? 2.2f : 1.1f, 3);
            return s - .3f * (1 - flicker(b, time * 4, 1));
        }
        if (c == P2_EXPOSED_REACTOR_CHARGE) return 1.4f * ramp(since(c, "chest_exposed", age), 40) + spike(since(c, "surge_1", age), .7f, 4)
            + spike(since(c, "surge_2", age), .8f, 4) + spike(since(c, "surge_3", age), .9f, 4) + spike(since(c, "full_charge", age), 1.2f, 6);
        if (c == P2_EXPOSED_REACTOR_FIRE) {
            float spent = since(c, "heart_spent", age);
            return spent < 0 ? 1.4f + spike(since(c, "fire", age), 1.6f, 8) : -.7f + .7f * ramp(spent, 40);
        }
        if (c == P2_OVERLOAD_V2) return spike(since(c, "reactor_surge", age), 2.4f, 18) + .3f * ramp(since(c, "windup_peak", age), 10);
        if (c == P2_OVERLOADED_CHARGED_PUNCH) return 1.2f * ramp(since(c, "charge_start", age), 36) * (since(c, "impact", age) < 0 ? 1 : 0)
            + spike(since(c, "impact", age), 1.5f, 6);
        if (c == P2_DEATH) {
            float fl = flicker(b, time * 3, .9f);
            return -1 + fl * (1 - ramp(since(c, "torso_breaks_off", age), 60)) + spike(since(c, "heart_flare", age), 2, 10);
        }
        if (c == P2_GROUNDSLAM_V2) return spike(since(c, "impact", age), 1.2f, 6) + .5f * ramp(since(c, "left_arm_online", age), 30);
        return 0;
    }

    /** Red emergency indicators of phase 2: blink, faster and harder when Overloaded, steady flicker while dying. */
    public static float red(GuilherminoBoss b, float partial) {
        float time = b.tickCount + partial;
        if (b.isDeadOrDying()) return flicker(b, time * 2, .8f);
        boolean over = b.flag(GuilherminoBoss.F_OVERLOADED);
        double rate = over ? .9 : .32;
        float blink = (float) (.5 + .5 * Math.sin(time * rate));
        blink = blink * blink;
        float vuln = b.flag(GuilherminoBoss.F_VULNERABLE) ? .5f : 0;
        return (over ? .55f + 1.1f * blink : .35f + .75f * blink) + vuln;
    }

    private static final java.util.WeakHashMap<GuilherminoBoss, Float> IGNITE = new java.util.WeakHashMap<>();

    static float ignitingStart(GuilherminoBoss b) { return IGNITE.computeIfAbsent(b, k -> (float) k.tickCount); }
}
