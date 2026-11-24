package dev.bossestesting.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bossestesting.fx.Anchors;
import dev.calamidade_awakening.api.v1.client.AnchorCapture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reports the real pose of the rig anchors to the engine (AnchorCapture), from the animated GeckoLib bones: beams,
 * lights and anchored effects then leave the exact bone (reactor, fists, feet, missile racks). Only anchors that an
 * active effect asked for are captured. Visual only: the server keeps its static approximations.
 * An optional per-bone hook draws extra geometry in the bone frame (thruster and exhaust flames).
 */
public final class AnchorLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    /** GeckoLib model axes: the model faces -Z, up is +Y. */
    static final Vector3f FORWARD = new Vector3f(0, 0, -1), UP = new Vector3f(0, 1, 0);

    public interface BoneHook<T> { void draw(PoseStack pose, T animatable, GeoBone bone, MultiBufferSource buffers, float partial); }

    private final Map<String, List<Anchors.Bone>> byBone = new HashMap<>();
    private final Map<String, BoneHook<T>> hooks = new HashMap<>();

    public AnchorLayer(GeoRenderer<T> renderer, List<Anchors.Bone> anchors) {
        super(renderer);
        for (Anchors.Bone a : anchors) byBone.computeIfAbsent(a.bone(), k -> new java.util.ArrayList<>()).add(a);
    }

    public AnchorLayer<T> hook(String bone, BoneHook<T> hook) { hooks.put(bone, hook); return this; }

    @Override
    public void renderForBone(PoseStack pose, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource buffers,
                              VertexConsumer buffer, float partial, int light, int overlay) {
        if (!(animatable instanceof Entity entity)) return;
        List<Anchors.Bone> list = byBone.get(bone.getName());
        if (list != null) for (Anchors.Bone a : list) {
            if (!AnchorCapture.wanted(entity, a.anchor())) continue;
            pose.pushPose();
            RenderUtil.translateToPivotPoint(pose, bone);
            pose.translate(a.x() / 16f, a.y() / 16f, a.z() / 16f);
            AnchorCapture.capture(entity, a.anchor(), pose.last().pose(), FORWARD, UP);
            pose.popPose();
        }
        BoneHook<T> hook = hooks.get(bone.getName());
        if (hook != null) {
            pose.pushPose();
            RenderUtil.translateToPivotPoint(pose, bone);
            hook.draw(pose, animatable, bone, buffers, partial);
            pose.popPose();
        }
    }
}
