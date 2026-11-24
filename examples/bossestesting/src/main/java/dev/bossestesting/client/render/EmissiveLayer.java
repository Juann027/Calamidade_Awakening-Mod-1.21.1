package dev.bossestesting.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.function.BiFunction;

/**
 * Real emissive layer from a provided glowmask: the model is drawn again with the glowmask texture, full bright and
 * translucent (colors and alpha of the mask are kept; the rest of the model, the cabin glass and the pilot keep the
 * world lighting). Intensity below 1 fades the mask; above 1 an additive pass ({@code eyes}) overdrives it.
 */
public final class EmissiveLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    private final ResourceLocation mask;
    private final BiFunction<T, Float, Float> intensity;

    public EmissiveLayer(GeoRenderer<T> renderer, ResourceLocation mask, BiFunction<T, Float, Float> intensity) {
        super(renderer);
        this.mask = mask;
        this.intensity = intensity;
    }

    @Override
    public void render(PoseStack pose, T animatable, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                       VertexConsumer buffer, float partial, int light, int overlay) {
        float k = Math.max(0, intensity.apply(animatable, partial));
        if (k <= .01f) return;
        float base = Math.min(1, k);
        int alpha = Math.round(base * 255);
        RenderType translucent = RenderType.entityTranslucentEmissive(mask);
        getRenderer().reRender(model, pose, buffers, animatable, translucent, buffers.getBuffer(translucent), partial,
            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, (alpha << 24) | 0xffffff);
        float over = Math.min(1.5f, k - 1);
        if (over > .01f) {
            int g = Math.round(Math.min(1, over) * 255);
            RenderType add = RenderType.eyes(mask);
            getRenderer().reRender(model, pose, buffers, animatable, add, buffers.getBuffer(add), partial,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xff000000 | (g << 16) | (g << 8) | g);
        }
    }
}
