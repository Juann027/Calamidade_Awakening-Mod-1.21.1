package dev.calamidade_awakening.client;

import dev.calamidade_awakening.api.FrameLimits;
import dev.calamidade_awakening.api.Quality;
import net.minecraft.world.phys.Vec3;

/** Geometry and post-process use the same selection for the current frame. */
public final class FrameCoordinator {
    private final StageScene scene;
    private Quality quality = Quality.BALANCED;
    /** Internal test profile (0.11.2, debug command only); null when the quality profile applies. */
    private FrameLimits test;
    private StageScene.Plan plan;

    public FrameCoordinator(StageScene scene) { this.scene = scene; }
    public void invalidate() { plan = null; }
    public Quality quality() { return quality; }
    /** Caps applied this frame: the test profile when set, otherwise those of the quality profile. */
    public FrameLimits limits() { return test != null ? test : FrameLimits.of(quality); }
    /** F8: next quality profile; also leaves the test profile. */
    public Quality cycleQuality() {
        quality = quality.next();
        test = null;
        invalidate();
        return quality;
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
