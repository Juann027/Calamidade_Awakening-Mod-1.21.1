package dev.calamidade_awakening.client;

import dev.calamidade_awakening.api.Quality;
import net.minecraft.world.phys.Vec3;

/** Geometry and post-process use the same selection for the current frame. */
public final class FrameCoordinator {
    private final StageScene scene;
    private Quality quality = Quality.BALANCED;
    private StageScene.Plan plan;

    public FrameCoordinator(StageScene scene) { this.scene = scene; }
    public void invalidate() { plan = null; }
    public Quality quality() { return quality; }
    public Quality cycleQuality() {
        quality = quality.next();
        invalidate();
        return quality;
    }
    public StageScene.Plan plan(Vec3 camera, Vec3 look, float partial) {
        if (plan == null) plan = scene.frame(camera, look, partial, quality);
        return plan;
    }
}
