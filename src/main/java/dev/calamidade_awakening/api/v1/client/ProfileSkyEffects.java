package dev.calamidade_awakening.api.v1.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.Objects;

/**
 * Dimension effects that let the engine own the sky of your dimension (0.12.0). NeoForge 21.1 replaces a sky only
 * through {@code renderSky} of the dimension's effects (conferido com javap; nothing skips only the sun). Register one
 * for the {@code effects} id of your dimension type, wrapping the vanilla effects it should otherwise behave like:
 *
 * <pre>{@code
 * // Client mod bus (RegisterDimensionSpecialEffectsEvent):
 * event.register(ResourceLocation.fromNamespaceAndPath("meumod", "arena"),
 *     new ProfileSkyEffects(new DimensionSpecialEffects.OverworldEffects()));
 * }</pre>
 *
 * While the dimension's {@code SkyProfile} state says {@code vanillaSky(false)} (from the middle of the transition into
 * it), the game draws no sky at all there (no gradient, sunrise, sun, moon or stars) and the engine paints the state's
 * atmosphere gradient, or the vanilla colors, with its stars and layers. Otherwise everything is delegated to
 * {@code base}: fog color, foggy areas, sunrise colors, clouds, weather and lightmap.
 */
public class ProfileSkyEffects extends DimensionSpecialEffects {
    private final DimensionSpecialEffects base;
    private static java.util.function.Predicate<ClientLevel> replaces = level -> false;

    /** Effects that behave like {@code base} except while a profile state replaces the sky. */
    public ProfileSkyEffects(DimensionSpecialEffects base) {
        super(Objects.requireNonNull(base).getCloudHeight(), base.hasGround(), base.skyType(), base.forceBrightLightmap(),
            base.constantAmbientLight());
        this.base = base;
    }

    /** The wrapped vanilla effects. */
    public DimensionSpecialEffects base() { return base; }

    /** True when the profile state of this level replaces the vanilla sky right now. */
    public boolean replacing(ClientLevel level) { return level != null && replaces.test(level); }

    /** Engine hook (internal): who answers {@link #replacing}. */
    public static void engine(java.util.function.Predicate<ClientLevel> predicate) { replaces = Objects.requireNonNull(predicate); }

    /** Draws nothing (returns true) while the profile replaces the sky; otherwise what {@code base} does. */
    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        if (replacing(level)) return true;
        return base.renderSky(level, ticks, partialTick, modelViewMatrix, camera, projectionMatrix, isFoggy, setupFog);
    }

    /** Delegated to {@code base}. */
    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return base.getBrightnessDependentFogColor(fogColor, brightness);
    }

    /** Delegated to {@code base}. */
    @Override
    public boolean isFoggyAt(int x, int z) { return base.isFoggyAt(x, z); }

    /** Delegated to {@code base}. */
    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) { return base.getSunriseColor(timeOfDay, partialTicks); }

    /** Delegated to {@code base}. */
    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY,
                                double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        return base.renderClouds(level, ticks, partialTick, poseStack, camX, camY, camZ, modelViewMatrix, projectionMatrix);
    }

    /** Delegated to {@code base}. */
    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX,
                                     double camY, double camZ) {
        return base.renderSnowAndRain(level, ticks, partialTick, lightTexture, camX, camY, camZ);
    }

    /** Delegated to {@code base}. */
    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) { return base.tickRain(level, ticks, camera); }

    /** Delegated to {@code base}. */
    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                     float skyLight, int pixelX, int pixelY, Vector3f colors) {
        base.adjustLightmapColors(level, partialTicks, skyDarken, blockLightRedFlicker, skyLight, pixelX, pixelY, colors);
    }
}
