package dev.bossestesting.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bossestesting.BossesTesting;
import dev.bossestesting.entity.GuilherminoMissile;
import dev.bossestesting.fx.Anchors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** The provided missile model turned along its velocity, its glowmask emissive and a jet flame at the exhaust bone. */
public class MissileRenderer extends GeoEntityRenderer<GuilherminoMissile> {
    public MissileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new BossModel<>("guilhermino_missile"));
        addRenderLayer(new EmissiveLayer<>(this, BossesTesting.id("textures/entity/guilhermino_missile_glowmask.png"),
            (m, p) -> 1.25f + .25f * (float) Math.sin((m.tickCount + p) * 1.3)));
        addRenderLayer(new AnchorLayer<GuilherminoMissile>(this, Anchors.MISSILE).hook("exhaust", (pose, m, bone, buffers, partial) -> {
            float t = m.tickCount + partial;
            float k = t < GuilherminoMissile.LAUNCH_TICKS ? .6f + .4f * t / GuilherminoMissile.LAUNCH_TICKS : 1.1f;
            Flames.jet(pose, buffers, 0, 0, 1, 1.4f * k, .2f, 0xfffff8e0, 0xc0ff9a30, t, m.getId());
            Flames.jet(pose, buffers, 0, 0, 1, .7f * k, .12f, 0xffe0ffff, 0x9030e8ff, t + 5, m.getId() + 7);
        }));
    }

    @Override
    protected void applyRotations(GuilherminoMissile m, PoseStack pose, float age, float yaw, float partial, float nativeScale) {
        float y = Mth.rotLerp(partial, m.yRotO, m.getYRot());
        float p = Mth.lerp(partial, m.xRotO, m.getXRot());
        pose.translate(0, .3, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180 - y));
        pose.mulPose(Axis.XP.rotationDegrees(-p));
    }

    @Override
    public RenderType getRenderType(GuilherminoMissile animatable, ResourceLocation texture, MultiBufferSource buffers, float partial) {
        return RenderType.entityCutoutNoCull(texture);
    }
}
