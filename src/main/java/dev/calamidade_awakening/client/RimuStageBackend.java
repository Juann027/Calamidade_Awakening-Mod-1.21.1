package dev.calamidade_awakening.client;

import cyou.rimuru.rimulib.mc1211.client.api.FxApi;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.client.geometry.Timeline;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * One shared RimuLib layer. Pass order in a frame: the lights pass (0.9.0, so effects and atmosphere stay on top),
 * the terrain pass (0.11.0: heat over edited terrain and falling stand-ins), the engine field (one pass for all
 * selected marks), the addon passes (card shaders and post layers), the beams pass (0.9.0) and the atmosphere pass
 * (0.8.0). Addon passes and the beams, terrain and lights passes spend the per-frame budget: the HERO pool for HERO
 * effects, the common pool for the others; the engine passes are paid first (beams, terrain, lights), because one pass
 * serves every drawn effect.
 */
public final class RimuStageBackend {
    private final FrameCoordinator coordinator;
    private final StageFilms films;
    private final AtmosphereFrame atmosphere;
    private final BeamFrame beams;
    private final LightFrame lights;
    private final TerrainFrame terrain;
    private FxApi.Stage stage;
    private final FxApi.Layer layer = this::draw;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final java.util.Set<String> failedPosts = new java.util.HashSet<>();

    RimuStageBackend(FrameCoordinator coordinator, StageFilms films, AtmosphereFrame atmosphere, BeamFrame beams,
                     LightFrame lights, TerrainFrame terrain) {
        this.coordinator = coordinator;
        this.films = films;
        this.atmosphere = atmosphere;
        this.beams = beams;
        this.lights = lights;
        this.terrain = terrain;
    }

    public void install() {
        if (stage != null) return;
        stage = FxApi.stage(CalamidadeAwakening.ID);
        stage.add(layer);
    }

    private void draw(FxApi.Frame frame) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var view = mc.gameRenderer.getMainCamera();
        Vec3 camera = view.getPosition();
        var look = view.getLookVector();
        StageScene.Plan plan = coordinator.plan(camera, new Vec3(look.x(), look.y(), look.z()), frame.partial());
        float orbit = films.orbitalMix(frame.partial());
        beams.collect(plan, camera, frame.partial());
        terrain.collect(plan, camera, frame.partial());
        lights.collect(plan, camera, frame.partial(), terrain);
        if (!plan.effects().isEmpty() || orbit > .001f) drawEffects(frame, plan, orbit);
        // 0.8.0: one engine pass for the mixed atmosphere, last, so exposure and tint cover the whole scene.
        // Not charged to the addon budget and independent of the frustum (the sky stays when you turn away).
        boolean queued = atmosphere.draw(frame, camera, frame.partial());
        PerfOverlay.atmosphere(atmosphere.active(), queued);
    }

    private void drawEffects(FxApi.Frame frame, StageScene.Plan plan, float orbit) {
        Minecraft mc = Minecraft.getInstance();
        List<StageScene.Sample> marks = plan.volumes();
        float time = films.seconds(frame.partial());
        int passes = 0;
        // 0.9.0: the shared engine passes are paid first, because one pass serves every drawn effect.
        var budget = new dev.calamidade_awakening.api.PassBudget(plan.limits().marks(), plan.limits().heroPasses());
        var beamPool = beams.pay(budget);
        // 0.11.0: the terrain pass before the lights: a falling stand-in is the object itself, its light a bonus.
        var terrainPool = terrain.pay(budget);
        var lightPool = lights.pay(budget);
        if (lightPool.drawn()) {
            lights.queue(frame);
            passes++;
        }
        if (terrainPool.drawn()) {
            terrain.queue(frame, plan.quality(), time, !mc.options.hideLightningFlash().get());
            passes++;
        }
        PerfOverlay.terrain(terrainPool.drawn() ? terrain.zones() : 0, terrainPool.drawn() ? terrain.standIns() : 0, terrainPool);
        if (!marks.isEmpty() || orbit > .001f) {
            passes++;
            frame.pass("awakening_field", uniforms -> {
                uniforms.f1("AwakeningCount", marks.size());
                uniforms.f1("AwakeningSteps", plan.quality().steps);
                uniforms.f2("AwakeningScene", orbit, time);
                for (int i = 0; i < 4; i++) {
                    StageScene.Sample mark = i < marks.size() ? marks.get(i) : null;
                    if (mark == null) {
                        uniforms.f4("AwakeningMark" + i, 0, 0, 0, 0);
                        uniforms.f4("AwakeningData" + i, 0, 0, 0, 0);
                    } else {
                        Vec3 p = mark.relative();
                        uniforms.f4("AwakeningMark" + i, (float) p.x, (float) p.y, (float) p.z, (float) mark.kind().radius);
                        uniforms.f4("AwakeningData" + i, mark.progress(), mark.opacity(), mark.seed(), mark.kind().volume.shaderMode);
                    }
                }
            });
        }
        // Addon passes: card shaders and post layers spend one per-frame budget in the plan's priority order. Since
        // 0.9.0 there are two pools: HERO effects spend their own (1/2/3), NORMAL ones the common pool (1/2/4); the
        // plan lists HERO instances first. Post layers are charged for every pass they really queue. Since 0.6.2 a
        // post layer is always called for its visible instance, even with no pass left, so its screen effects apply.
        boolean flashAllowed = !mc.options.hideLightningFlash().get();
        int heroes = 0;
        for (var sample : plan.effects()) {
            boolean hero = sample.kind().hero();
            if (hero) heroes++;
            var addon = sample.kind().addon();
            if (addon != null && addon.card().shader() != null && budget.take(hero)) {
                passes++;
                frame.pass(addon.card().shader().toString(), u -> {
                    u.v3("CaCenter", sample.relative());
                    u.f4("CaEffect", sample.progress(), sample.opacity(), sample.seed(), (float)sample.kind().radius);
                    u.f1("CaFlashAllowed", flashAllowed ? 1 : 0);
                });
            }
            String id = sample.kind().networkKey();
            var post = dev.calamidade_awakening.api.v1.client.AddonLayers.post(id);
            if (post == null || failedPosts.contains(id)) continue;
            // Local age drives phases and tracks (equal to the real age without sustain, as in 0.6).
            var context = new dev.calamidade_awakening.api.v1.client.AddonLayers.Post(frame, sample.time(),
                sample.relative(), sample.distance(), sample.localAge(), sample.age(), sample.event().order(),
                plan.quality(), flashAllowed, budget.left(hero), sample.basis().yaw, sample.basis().pitch);
            try {
                post.accept(context);
            } catch (RuntimeException failure) {
                // Disable a broken layer instead of logging every frame or breaking the shared stage.
                failedPosts.add(id);
                LOGGER.error("Post layer {} failed and was disabled for this session", id, failure);
            }
            // Count what was really queued, including passes issued before a failure.
            budget.charge(hero, context.used());
            passes += context.used();
        }
        if (beamPool.drawn()) {
            beams.queue(frame);
            passes++;
        }
        PerfOverlay.beams(beamPool.drawn() ? Math.min(beams.beams().size(), BeamFrame.ROWS) : 0, beamPool);
        PerfOverlay.lights(lightPool.drawn() ? Math.min(lights.lights().size(), LightFrame.ROWS) : 0, lightPool);
        PerfOverlay.heroes(heroes, budget.used(true));
        PerfOverlay.passes(passes);
        PerfOverlay.passCut(budget.refused() > 0);
        float strongest = 0;
        float impulse = 0;
        float flash = 0, monochrome = 0;
        boolean allowFlash = !mc.options.hideLightningFlash().get();
        int flashColor = 0xFFFFFFFF;
        for (StageScene.Sample mark : plan.effects()) {
            strongest = Math.max(strongest, mark.opacity());
            double onset = mark.kind() == dev.calamidade_awakening.api.VisualKind.PRISM ? .10
                : mark.kind() == dev.calamidade_awakening.api.VisualKind.NEXUS ? .60 : .58;
            float release = (float) (Timeline.envelope(mark.progress(), onset, onset + .02, onset + .045, onset + .17)
                * Math.max(0, 1 - mark.distance() / 64));
            impulse = Math.max(impulse, release);
            if (!allowFlash) continue;
            var definition = mark.kind().definition();
            float tint = release * .09f;
            if (definition != null && definition.impact() != null) {
                var cue = definition.impact();
                float weight = cue.weight(mark.localAge()) * (float)Math.clamp(1 - mark.distance() / 64, 0, 1);
                tint = 0;
                if (cue.style() == dev.calamidade_awakening.api.v1.Effects.ImpactStyle.MONOCHROME)
                    monochrome = Math.max(monochrome, weight);
                else tint = weight;
            }
            if (tint > flash) {
                flash = tint;
                flashColor = mark.kind().color;
            }
        }
        // Engine screen effects follow the same accessibility settings as addon post layers (0.6.2).
        float intensity = dev.calamidade_awakening.config.ClientConfig.screenEffects();
        frame.screen().bloom = Math.max(frame.screen().bloom, strongest * .18f * intensity);
        if (dev.calamidade_awakening.config.ClientConfig.cameraShake())
            frame.screen().shake = Math.max(frame.screen().shake, impulse * .22f);
        if (flash * intensity > .001f) frame.screen().addFlash(flashColor, flash * intensity);
        if (monochrome * intensity > .001f) {
            frame.screen().desaturate = Math.max(frame.screen().desaturate, monochrome * intensity);
            frame.screen().contrast = Math.max(frame.screen().contrast, monochrome * .65f * intensity);
        }
    }
}
