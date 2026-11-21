package dev.calamidade_awakening.client;

import cyou.rimuru.rimulib.mc1211.client.api.FxApi;
import dev.calamidade_awakening.api.EnginePasses;
import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Visual lights of the current frame (0.9.0). Resolves the lights of every drawn instance (HERO first, then by
 * weight), keeps at most {@code Quality.lights} (8/24/64) and draws them in the single engine pass "lights", queued
 * before the effect passes so the atmosphere and the effects stay on top. The pass costs one slot of the HERO budget
 * when a drawn HERO effect has lights, otherwise one of the common budget. Visual only: no light level changes.
 */
final class LightFrame {
    static final String PROGRAM = "calamidade_awakening:lights";
    static final int TEXELS = 4, ROWS = 64;

    /** One resolved light: positions relative to the camera and its state (weight already includes a linked beam's). */
    record Resolved(Light light, Vec3 a, Vec3 b, Light.State state, float weight) {}

    private final StageScene scene;
    private final DataTexture texture = new DataTexture(TEXELS, ROWS);
    private final float[] data = new float[TEXELS * 4 * ROWS];
    private List<Resolved> lights = List.of();
    private boolean heroNeeds, normalNeeds;
    // Game terrain fog of the frame (RenderFog, recorded after every listener): the light fades with it.
    private float fogStart = 1e6f, fogEnd = 2e6f;
    private boolean fogCylinder;

    LightFrame(StageScene scene) { this.scene = scene; }

    /** Terrain fog the game uses this frame (blocks), recorded from ViewportEvent.RenderFog. */
    void fog(float start, float end, boolean cylinder) {
        fogStart = start; fogEnd = end; fogCylinder = cylinder;
    }

    /** Resolves the visible lights of the plan, in priority order, capped by the quality. */
    void collect(StageScene.Plan plan, Vec3 camera, float partial) {
        heroNeeds = normalNeeds = false;
        var mc = Minecraft.getInstance();
        boolean calm = mc.options.hideLightningFlash().get();
        float flickerScale = ClientConfig.screenEffects() * (calm ? .25f : 1f);
        List<List<Resolved>> perEffect = new ArrayList<>();
        for (StageScene.Sample sample : plan.effects()) {
            var list = sample.kind().lights();
            if (list.isEmpty()) continue;
            Vec3 center = camera.add(sample.relative());
            var sampler = sample.time().timeline();
            List<Resolved> resolved = new ArrayList<>(list.size());
            for (Light light : list) {
                Light.State state = light.sample(sampler, sample.opacity(), sample.age() / 20.0, sample.seed(), flickerScale);
                if (!state.visible()) continue;
                // 0.10.0: track offsets are in the instance frame (world axes without orientation).
                Vec3 offset = sample.turn(state.x(), state.y(), state.z());
                Vec3 a = scene.point(sample.event(), center, sample.basis(), light.from(), partial);
                if (a == null) continue;
                Vec3 b = a;
                float weight = state.weight();
                if (light.shape() == Light.Shape.SEGMENT) {
                    b = scene.point(sample.event(), center, sample.basis(), light.to(), partial);
                    if (b == null) continue;
                    Beam beam = light.beam();
                    if (beam != null) {
                        // Follows the beam: its track offsets, reveal and weight.
                        Beam.State bs = beam.sample(sampler, sample.opacity());
                        if (!bs.visible()) continue;
                        a = a.add(sample.turn(bs.fromX(), bs.fromY(), bs.fromZ()));
                        b = b.add(sample.turn(bs.toX(), bs.toY(), bs.toZ()));
                        b = a.add(b.subtract(a).scale(bs.reveal()));
                        weight *= bs.weight();
                    }
                }
                a = a.add(offset).subtract(camera);
                b = b.add(offset).subtract(camera);
                if (weight <= Light.ACTIVE_WEIGHT) continue;
                resolved.add(new Resolved(light, a, b, state, weight));
            }
            if (resolved.isEmpty()) continue;
            perEffect.add(resolved);
            if (sample.kind().hero()) heroNeeds = true; else normalNeeds = true;
        }
        lights = EnginePasses.cap(perEffect, plan.quality().lights);
    }

    /** Lights kept this frame (F7). */
    List<Resolved> lights() { return lights; }

    /** Pays for this frame's pass from the right pool. */
    EnginePasses.Pool pay(dev.calamidade_awakening.api.PassBudget budget) {
        return lights.isEmpty() ? EnginePasses.Pool.NONE : EnginePasses.pay(budget, heroNeeds, normalNeeds);
    }

    /** Uploads the lights and queues the pass. Call only when the pass was paid for, before the effect passes. */
    void queue(FxApi.Frame frame) {
        int count = Math.min(lights.size(), ROWS);
        int k = 0;
        for (int i = 0; i < count; i++) {
            Resolved r = lights.get(i);
            Light.State s = r.state();
            float gain = s.intensity() * r.weight();
            k = put(k, (float) r.a().x, (float) r.a().y, (float) r.a().z, s.radius());
            k = put(k, (float) r.b().x, (float) r.b().y, (float) r.b().z, s.falloff());
            k = put(k, BeamFrame.red(s.color()) * gain, BeamFrame.green(s.color()) * gain, BeamFrame.blue(s.color()) * gain,
                r.light().shape().ordinal());
            k = put(k, s.width(), s.height(), 0, 0);
        }
        int id = texture.upload(data, count);
        float start = fogStart, end = fogEnd, cylinder = fogCylinder ? 1 : 0;
        frame.pass(PROGRAM, u -> {
            u.glTexture("CaLightData", id);
            u.f4("CaLightInfo", count, start, end, cylinder);
        });
    }

    private int put(int k, float x, float y, float z, float w) {
        data[k] = x; data[k + 1] = y; data[k + 2] = z; data[k + 3] = w;
        return k + 4;
    }
}
