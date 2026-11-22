package dev.calamidade_awakening.client;

import dev.calamidade_awakening.api.FrameLimits;
import dev.calamidade_awakening.api.QualityProfile;
import dev.calamidade_awakening.config.ClientConfig;
import net.minecraft.world.phys.Vec3;

/** Geometry and post-process use the same selection for the current frame. */
public final class FrameCoordinator {
    private final StageScene scene;
    /** Internal test profile (0.11.2, debug command only); null when the quality profile applies. */
    private FrameLimits test;
    private StageScene.Plan plan;

    public FrameCoordinator(StageScene scene) { this.scene = scene; }
    public void invalidate() { plan = null; }
    /** Profile in use (0.11.2): stored in the client config. */
    public QualityProfile profile() { return ClientConfig.qualityProfile(); }
    /** Caps applied this frame: the test profile when set, otherwise those of the profile (client config). */
    public FrameLimits limits() {
        if (test != null) return test;
        QualityProfile profile = profile();
        return profile.limits(ClientConfig.caps(profile));
    }
    /** F8: next profile (LOW, BALANCED, HIGH, ULTRA), stored in the config; also leaves the test profile. */
    public QualityProfile cycleQuality() {
        QualityProfile next = QualityProfile.values()[(profile().ordinal() + 1) % QualityProfile.values().length];
        select(next);
        return next;
    }
    /** Chooses a profile (F8, Atlas) and stores it. */
    public void select(QualityProfile profile) {
        ClientConfig.qualityProfile(profile);
        test = null;
        invalidate();
    }
    /** Sets (or clears, with null) the internal test profile UNLIMITED. */
    public void test(FrameLimits limits) {
        test = limits;
        invalidate();
    }
    public StageScene.Plan plan(Vec3 camera, Vec3 look, float partial) {
        if (plan == null) plan = scene.frame(camera, look, partial, limits());
        return plan;
    }
}
