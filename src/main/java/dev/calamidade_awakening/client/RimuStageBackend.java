package dev.calamidade_awakening.client;

import cyou.rimuru.rimulib.mc1211.client.api.FxApi;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.client.geometry.Timeline;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** One shared RimuLib layer, one pass for all selected marks. */
public final class RimuStageBackend {
    private final FrameCoordinator coordinator;
    private final StageFilms films;
    private FxApi.Stage stage;
    private final FxApi.Layer layer = this::draw;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final java.util.Set<String> failedPosts = new java.util.HashSet<>();

    public RimuStageBackend(FrameCoordinator coordinator, StageFilms films) {
        this.coordinator = coordinator;
        this.films = films;
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
        List<StageScene.Sample> marks = plan.volumes();
        float orbit = films.orbitalMix(frame.partial());
        float time = films.seconds(frame.partial());
        if (plan.effects().isEmpty() && orbit <= .001f) return;
        int passes = 0;
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
        // Addon passes: card shaders and post layers share one per-frame budget (1/2/4), spent in the plan's
        // priority order. Post layers are charged for every pass they really queue. Since 0.6.2 a post layer is
        // always called for its visible instance, even with no pass left, so its screen effects still apply.
        int addonPasses = 0;
        boolean flashAllowed = !mc.options.hideLightningFlash().get();
        for (var sample : plan.effects()) {
            var addon = sample.kind().addon();
            if (addon != null && addon.card().shader() != null && addonPasses < plan.quality().marks) {
                addonPasses++;
                passes++;
                frame.pass(addon.card().shader().toString(), u -> {
                    u.v3("CaCenter", sample.relative());
                    u.f4("CaEffect", sample.progress(), sample.opacity(), sample.seed(), (float)sample.kind().radius);
                    u.f1("CaFlashAllowed", flashAllowed ? 1 : 0);
                });
            }
            String id = sample.kind().networkKey();
            var post = dev.calamidade_awakening.api.v1.client.AddonLayers.post(id);
            int remaining = Math.max(0, plan.quality().marks - addonPasses);
            if (post == null || failedPosts.contains(id)) continue;
            double age = mc.level.getGameTime() - sample.event().tick() + frame.partial();
            var context = new dev.calamidade_awakening.api.v1.client.AddonLayers.Post(frame,
                new dev.calamidade_awakening.api.v1.client.AddonLayers.Time(sample.progress(), sample.opacity(),
                    sample.seed(), sample.kind().radius),
                sample.relative(), sample.distance(), age, sample.event().order(), plan.quality(), flashAllowed, remaining);
            try {
                post.accept(context);
            } catch (RuntimeException failure) {
                // Disable a broken layer instead of logging every frame or breaking the shared stage.
                failedPosts.add(id);
                LOGGER.error("Post layer {} failed and was disabled for this session", id, failure);
            }
            // Count what was really queued, including passes issued before a failure.
            addonPasses += context.used();
            passes += context.used();
        }
        PerfOverlay.passes(passes);
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
                double age = (mc.level.getGameTime() - mark.event().tick()) + frame.partial();
                float weight = cue.weight(age) * (float)Math.clamp(1 - mark.distance() / 64, 0, 1);
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
