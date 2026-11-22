package dev.calamidade_awakening.client;

import dev.calamidade_awakening.api.AutoQuality;
import dev.calamidade_awakening.api.FrameLimits;
import dev.calamidade_awakening.api.QualityMode;
import dev.calamidade_awakening.api.QualityProfile;
import dev.calamidade_awakening.config.ClientConfig;
import net.minecraft.world.phys.Vec3;

/**
 * Geometry and post-process use the same selection for the current frame. Since 0.11.2 it also holds the quality
 * mode: AUTO (the profile follows {@link AutoQuality}) or a profile forced with F8 or in the Atlas.
 */
public final class FrameCoordinator {
    private final StageScene scene;
    /** Internal test profile (0.11.2, debug command only); null when the quality profile applies. */
    private FrameLimits test;
    private StageScene.Plan plan, last;
    private final AutoQuality auto = new AutoQuality(QualityProfile.BALANCED, 1000.0 / 60, System.currentTimeMillis());
    private QualityMode lastMode;

    public FrameCoordinator(StageScene scene) { this.scene = scene; }
    public void invalidate() { plan = null; }
    /** Quality mode in use (client config). */
    public QualityMode mode() { return ClientConfig.qualityMode(); }
    /** Profile in use: the forced one, or the one AUTO chose. */
    public QualityProfile profile() {
        QualityMode mode = mode();
        return mode.profile != null ? mode.profile : auto.current();
    }
    /** AUTO state: the profile and the reason of the last change. */
    public AutoQuality auto() { return auto; }
    /** True while the internal test profile UNLIMITED is on. */
    public boolean testing() { return test != null; }
    /** Caps applied this frame: the test profile when set, otherwise those of the profile (client config). */
    public FrameLimits limits() {
        if (test != null) return test;
        QualityProfile profile = profile();
        return profile.limits(ClientConfig.caps(profile));
    }
    /** F8: next mode (AUTO, LOW, BALANCED, HIGH, ULTRA), stored in the config; also leaves the test profile. */
    public QualityMode cycleQuality() {
        QualityMode next = mode().next();
        select(next);
        return next;
    }
    /** Chooses a mode (F8, Atlas) and stores it. Entering AUTO starts it from the profile in use. */
    public void select(QualityMode mode) {
        QualityProfile current = profile();
        ClientConfig.qualityMode(mode);
        if (mode == QualityMode.AUTO) auto.restart(current, targetMs(), System.currentTimeMillis());
        lastMode = mode;
        test = null;
        invalidate();
    }
    /** Sets (or clears, with null) the internal test profile UNLIMITED. */
    public void test(FrameLimits limits) {
        test = limits;
        invalidate();
    }
    public StageScene.Plan plan(Vec3 camera, Vec3 look, float partial) {
        if (plan == null) last = plan = scene.frame(camera, look, partial, limits());
        return plan;
    }

    /**
     * Feeds AUTO with the frame that just ended (RenderFrameEvent.Pre of the next one); {@code passCut} tells that the
     * pass budget refused a pass. Nothing happens under a forced profile or the test profile.
     */
    void frame(long nowMs, double frameMs, boolean passCut) {
        QualityMode mode = mode();
        if (mode != lastMode) {
            // Config changed outside F8 (config screen, file): restart AUTO from the profile it had.
            if (mode == QualityMode.AUTO && lastMode != null && lastMode.profile != null)
                auto.restart(lastMode.profile, targetMs(), nowMs);
            lastMode = mode;
        }
        if (mode != QualityMode.AUTO || test != null) return;
        auto.target(targetMs());
        StageScene.Plan p = last;
        boolean drawn = p != null && !p.effects().isEmpty();
        boolean cut = p != null && (p.cut() || passCut);
        if (auto.frame(nowMs, frameMs, drawn, cut, scene.heroActive())) invalidate();
    }

    private static double targetMs() { return 1000.0 / ClientConfig.autoTargetFps(); }
}
