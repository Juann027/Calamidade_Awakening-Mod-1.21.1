package dev.bossestesting.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.bossestesting.BossesTesting;
import dev.bossestesting.ai.Brain;
import dev.bossestesting.entity.GuilherminoBoss;
import dev.bossestesting.entity.GuilherminoFinale;
import dev.bossestesting.fx.Anchors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import static dev.bossestesting.anim.Anims.JG_SPAWN_LANDING;

/**
 * Renderer of both forms: translucent base (the cabin glass stays transparent and the pilot visible), emissive
 * glowmask layers with animated intensity (phase 2: the provided mask split into cyan reactor and red indicators),
 * anchor capture for the engine and, during the landing, thruster flames under the feet.
 */
public class BossRenderer<T extends GuilherminoBoss> extends GeoEntityRenderer<T> {
    public BossRenderer(EntityRendererProvider.Context ctx, String name, boolean phase2) {
        super(ctx, new BossModel<>(name));
        this.shadowRadius = 1.8f;
        if (phase2) {
            addRenderLayer(new EmissiveLayer<>(this, BossesTesting.id("textures/entity/guilhermino_phase2_glow_cyan.png"), GlowCurve::main));
            addRenderLayer(new EmissiveLayer<>(this, BossesTesting.id("textures/entity/guilhermino_phase2_glow_red.png"), GlowCurve::red));
            addRenderLayer(new AnchorLayer<>(this, Anchors.PHASE2));
        } else {
            addRenderLayer(new EmissiveLayer<>(this, BossesTesting.id("textures/entity/jar_golem_glowmask.png"), GlowCurve::main));
            addRenderLayer(new AnchorLayer<T>(this, Anchors.PHASE1)
                .hook("foot_l", BossRenderer::thruster).hook("foot_r", BossRenderer::thruster));
        }
    }

    /** Landing thrusters: from descent_start to the impact, stronger around landing_prep. */
    static <T extends GuilherminoBoss> void thruster(PoseStack pose, T boss, software.bernie.geckolib.cache.object.GeoBone bone,
                                                    MultiBufferSource buffers, float partial) {
        if (boss.clip() != JG_SPAWN_LANDING) return;
        float age = boss.clipAge(partial);
        int impact = Brain.LEAD - 1 + JG_SPAWN_LANDING.at("impact"), prep = Brain.LEAD - 1 + JG_SPAWN_LANDING.at("landing_prep");
        if (age > impact + 1) return;
        float k = age < prep - 3 ? .8f : age < prep + 3 ? 1.5f : 1.1f;
        pose.translate(0, -5 / 16f, 0);
        Flames.jet(pose, buffers, 0, -1, 0, 2.2f * k, .38f, 0xfffff4d8, 0xc0ff7a1a, boss.tickCount + partial, boss.getId());
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, MultiBufferSource buffers, float partial) {
        return RenderType.entityTranslucent(texture);
    }

    /** No vanilla death tilt: the death animations play the fall. */
    @Override protected float getDeathMaxRotation(T animatable) { return 0; }

    @Override public boolean shouldShowName(T animatable) { return false; }
}
