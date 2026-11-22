package dev.calamidade_awakening.client;

import cyou.rimuru.rimulib.mc1211.client.api.FxApi;
import dev.calamidade_awakening.api.EnginePasses;
import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.config.ClientConfig;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Beams of the current frame (0.9.0). Resolves the beams of every drawn instance (HERO first, then by weight), keeps
 * at most {@code Quality.beams} (16/48/128) and draws them all in the single engine pass "beams". The pass costs one
 * slot of the HERO budget when a drawn HERO effect has beams, otherwise one of the common budget.
 */
final class BeamFrame {
    static final String PROGRAM = "calamidade_awakening:beams";
    static final int TEXELS = 7, ROWS = 128;
    /** An end closer than this to the camera (blocks) marks a beam attached to the viewer (its own laser). */
    static final double ATTACHED = 2.0;

    /** One resolved beam: ends relative to the camera (end before the reveal), its state and the instance clock. */
    record Resolved(StageScene.Sample sample, int index, Vec3 from, Vec3 to, Beam.State state) {
        /** End actually drawn (start + reveal × length), camera-relative. */
        Vec3 revealedEnd() { return from.add(to.subtract(from).scale(state.reveal())); }
    }

    private final StageScene scene;
    private final DataTexture texture = new DataTexture(TEXELS, ROWS);
    private final float[] data = new float[TEXELS * 4 * ROWS];
    private List<Resolved> beams = List.of();
    private boolean heroNeeds, normalNeeds;

    BeamFrame(StageScene scene) { this.scene = scene; }

    /** Resolves the visible beams of the plan, in priority order, capped by the quality. */
    void collect(StageScene.Plan plan, Vec3 camera, float partial) {
        heroNeeds = normalNeeds = false;
        scene.newFrame();
        List<List<Resolved>> perEffect = new ArrayList<>();
        for (StageScene.Sample sample : plan.effects()) {
            var list = sample.kind().beams();
            if (list.isEmpty()) continue;
            Vec3 center = camera.add(sample.relative());
            var sampler = sample.time().timeline();
            List<Resolved> resolved = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                Beam beam = list.get(i);
                Beam.State state = beam.sample(sampler, sample.opacity());
                if (!state.visible()) continue;
                Vec3 from = scene.point(sample.event(), center, sample.basis(), beam.from(), partial);
                Vec3 to = scene.point(sample.event(), center, sample.basis(), beam.to(), partial);
                if (from == null || to == null) continue;
                // 0.10.0: track offsets are in the instance frame (world axes without orientation).
                from = from.add(sample.turn(state.fromX(), state.fromY(), state.fromZ())).subtract(camera);
                to = to.add(sample.turn(state.toX(), state.toY(), state.toZ())).subtract(camera);
                if (from.distanceToSqr(to) < 1e-6) continue;
                resolved.add(new Resolved(sample, i, from, to, state));
            }
            if (resolved.isEmpty()) continue;
            perEffect.add(resolved);
            if (sample.kind().hero()) heroNeeds = true; else normalNeeds = true;
        }
        beams = EnginePasses.cap(perEffect, plan.limits().beams());
    }

    /** Beams kept this frame (camera-relative), for lights linked to beams and for F7. */
    List<Resolved> beams() { return beams; }

    boolean heroNeeds() { return heroNeeds; }
    boolean normalNeeds() { return normalNeeds; }

    /** Uploads the beams and queues the pass. Call only when the pass was paid for. */
    void queue(FxApi.Frame frame) {
        int count = Math.min(beams.size(), ROWS);
        float access = ClientConfig.screenEffects();
        int k = 0;
        for (int i = 0; i < count; i++) {
            Resolved r = beams.get(i);
            Beam.State s = r.state();
            float gain = s.intensity() * s.weight();
            float realAge = (float) r.sample().age();
            // 0: start, core half width
            k = put(k, (float) r.from().x, (float) r.from().y, (float) r.from().z, s.coreWidth() * .5f);
            // 1: end before the reveal, halo half width
            k = put(k, (float) r.to().x, (float) r.to().y, (float) r.to().z, s.haloWidth() * .5f);
            // 2: core color × gain, reveal
            k = put(k, red(s.coreColor()) * gain, green(s.coreColor()) * gain, blue(s.coreColor()) * gain, s.reveal());
            // 3: halo color × gain × screen-effects intensity, pulse count
            float halo = gain * access;
            k = put(k, red(s.haloColor()) * halo, green(s.haloColor()) * halo, blue(s.haloColor()) * halo, s.pulses());
            // 4: pulse travel (blocks), pulse length, pulse gain (screen-effects intensity), noise time
            k = put(k, realAge * s.pulseSpeed(), s.pulseSize(), access,
                realAge / 20f + r.sample().seed() * 37f + r.index() * 5.3f);
            // 5: wave amplitude, wavelength, wave travel (blocks), noise amplitude
            k = put(k, s.waveAmplitude(), s.waveLength(), realAge * s.waveSpeed(), s.noise());
            // 6: attached to the viewer (an end within 2 blocks of the camera): the shader caps the angular width
            boolean attached = r.from().length() < ATTACHED || r.revealedEnd().length() < ATTACHED;
            k = put(k, attached ? 1 : 0, 0, 0, 0);
        }
        int id = texture.upload(data, count);
        frame.pass(PROGRAM, u -> {
            u.glTexture("CaBeamData", id);
            u.f4("CaBeamInfo", count, 0, 0, 0);
        });
    }

    private int put(int k, float x, float y, float z, float w) {
        data[k] = x; data[k + 1] = y; data[k + 2] = z; data[k + 3] = w;
        return k + 4;
    }

    static float red(int argb) { return ((argb >> 16) & 255) / 255f; }
    static float green(int argb) { return ((argb >> 8) & 255) / 255f; }
    static float blue(int argb) { return (argb & 255) / 255f; }

    /** Pays for this frame's pass from the right pool. */
    EnginePasses.Pool pay(dev.calamidade_awakening.api.PassBudget budget) {
        return beams.isEmpty() ? EnginePasses.Pool.NONE : EnginePasses.pay(budget, heroNeeds, normalNeeds);
    }
}
