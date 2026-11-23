package dev.calamidade_awakening.api.v1.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.calamidade_awakening.api.v1.RigAnchors;
import dev.calamidade_awakening.client.boss.AnchorCache;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.Objects;

/**
 * Client side of the rig anchors (0.13.0): the addon reports where a bone or model part was drawn this frame, and the
 * engine uses it for {@code Point.socket}, {@code Point.socketAim} and {@code Effects.anchorSocket}. Render thread only.
 *
 * <p><b>Same frame.</b> The engine builds beams, lights and effect passes at the end of the level render (after every
 * entity), so a pose captured while the entity is drawn is used in the same frame. When the entity is not drawn
 * (out of view), the last capture is carried along with the entity's position and body yaw for up to 10 ticks; after
 * that the order is: a {@link #provider}, the static {@link RigAnchors} definition, the eyes.
 *
 * <p><b>Cost.</b> {@link #capture} copies one matrix and only when an active effect asked for that anchor recently
 * ({@link #wanted}); calling it for every anchor of every rendered mob costs a map lookup.
 *
 * <p><b>Visual only.</b> Captured poses never reach the server; authoritative code uses
 * {@link RigAnchors#approximate}.
 *
 * <pre>{@code
 * // In a RenderLayer of a vanilla-style model (the PoseStack is the one the layer receives):
 * AnchorCapture.capturePart(golem, "olhos", poseStack, model.root(), model.root().getChild("head"));
 * // In any renderer (GeckoLib included) after moving the PoseStack to the bone:
 * AnchorCapture.capture(dragao, "boca", poseStack.last().pose(), new Vector3f(0, 0, 1), new Vector3f(0, 1, 0));
 * }</pre>
 *
 * <p>No animation library is required. For GeckoLib, capture inside the addon's own render layer or renderer with the
 * bone's PoseStack and check the bone axes once (GeckoLib models are not flipped like vanilla ones).
 */
public final class AnchorCapture {
    /** Forward of a part in vanilla living-entity model space (the renderer flips x and y): {@code (0, 0, -1)}. */
    public static final Vector3f VANILLA_FORWARD = new Vector3f(0, 0, -1);
    /** Up of a part in vanilla living-entity model space: {@code (0, -1, 0)}. */
    public static final Vector3f VANILLA_UP = new Vector3f(0, -1, 0);

    /** Computes a pose without a render (a procedural model, a vehicle's turret). */
    @FunctionalInterface
    public interface Provider {
        /** Pose of the anchor at {@code partial} (0..1), or null to fall through to the static definition. */
        RigAnchors.Pose resolve(Entity entity, String name, float partial);
    }

    private AnchorCapture() {}

    /**
     * Reports the pose of an anchor as drawn this frame, from a camera-relative model matrix (the PoseStack of an
     * entity renderer), with vanilla model axes ({@link #VANILLA_FORWARD}, {@link #VANILLA_UP}).
     */
    public static void capture(Entity entity, String name, Matrix4f pose) {
        capture(entity, name, pose, VANILLA_FORWARD, VANILLA_UP);
    }

    /**
     * Reports the pose of an anchor as drawn this frame, from a camera-relative model matrix and the model-space axes
     * of the part's front and top. The origin of the matrix is the anchor position; scale is ignored.
     */
    public static void capture(Entity entity, String name, Matrix4f pose, Vector3f localForward, Vector3f localUp) {
        AnchorCache.capture(Objects.requireNonNull(entity), Objects.requireNonNull(name), Objects.requireNonNull(pose),
            Objects.requireNonNull(localForward), Objects.requireNonNull(localUp));
    }

    /**
     * Applies a chain of model parts (root first) to a copy of the PoseStack and captures the result: the pivot of the
     * last part, with vanilla model axes. The PoseStack is left as it was.
     */
    public static void capturePart(Entity entity, String name, PoseStack poseStack, ModelPart... chain) {
        Objects.requireNonNull(poseStack);
        if (!wanted(entity, name)) return;
        poseStack.pushPose();
        try {
            for (ModelPart part : chain) part.translateAndRotate(poseStack);
            capture(entity, name, poseStack.last().pose());
        } finally {
            poseStack.popPose();
        }
    }

    /** True when an active effect asked for this anchor of this entity recently (skip the work otherwise). */
    public static boolean wanted(Entity entity, String name) { return AnchorCache.wanted(entity, name); }

    /** Registers a provider for an entity type (client setup); replaces an earlier one. */
    public static void provider(EntityType<?> type, Provider provider) {
        AnchorCache.provider(Objects.requireNonNull(type), Objects.requireNonNull(provider));
    }

    /**
     * The engine's pose of an anchor this frame, through the whole chain (capture, carried capture, provider,
     * static, eyes); never null. For the addon's own client layers (a mesh in the hand, a HUD marker).
     */
    public static RigAnchors.Pose resolve(Entity entity, String name, float partial) {
        return AnchorCache.resolve(Objects.requireNonNull(entity), Objects.requireNonNull(name), partial);
    }
}
