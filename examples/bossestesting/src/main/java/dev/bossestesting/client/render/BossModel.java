package dev.bossestesting.client.render;

import dev.bossestesting.BossesTesting;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/** GeckoLib model of one of the three provided assets (geo, animation and texture with the normalized names). */
public final class BossModel<T extends GeoAnimatable> extends GeoModel<T> {
    private final ResourceLocation model, animation, texture;

    public BossModel(String name) {
        this.model = BossesTesting.id("geo/" + name + ".geo.json");
        this.animation = BossesTesting.id("animations/" + name + ".animation.json");
        this.texture = BossesTesting.id("textures/entity/" + name + ".png");
    }

    @Override public ResourceLocation getModelResource(T animatable) { return model; }
    @Override public ResourceLocation getTextureResource(T animatable) { return texture; }
    @Override public ResourceLocation getAnimationResource(T animatable) { return animation; }
}
