package dev.calamidade_awakening.client;

import cyou.rimuru.rimulib.mc1211.client.api.FxApi;
import dev.calamidade_awakening.api.AtmosphereMath;
import dev.calamidade_awakening.api.v1.Atmosphere;
import dev.calamidade_awakening.config.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The engine's atmosphere for the current frame (0.8.0). Mixes the active atmospheres once per frame, drives the
 * game fog through the NeoForge fog events and queues the single "atmosphere" pass, which is not charged to the
 * addon pass budget.
 *
 * <p>Fog: by default the game's own fog carries it (ComputeFogColor for the color, RenderFog for the distances),
 * so terrain, entities, particles, water and clouds are fogged per fragment at their real distance and the
 * engine's emissive meshes and post effects stay readable. The pass handles what the game fog cannot: the sky
 * (gradient, stars and haze toward the horizon), exposure and ambient tint. With the client option
 * {@code vanillaFog} off, the pass also draws the fog on the screen from the depth buffer.
 */
final class AtmosphereFrame {
    static final String PROGRAM = "calamidade_awakening:atmosphere";
    private static final float EXPOSURE_EPSILON = 1e-3f;

    private final StageScene scene;
    private final WorldEventsClient world;
    private final SkyProfilesClient profiles;
    private boolean computed;
    private Atmosphere.State state = Atmosphere.State.NEUTRAL;
    private int active;
    private float exposure = 1;
    private double previousTime = Double.NaN;
    private float fogR = 1, fogG = 1, fogB = 1;

    AtmosphereFrame(StageScene scene, WorldEventsClient world, SkyProfilesClient profiles) {
        this.scene = scene;
        this.world = world;
        this.profiles = profiles;
    }

    /** True when the dimension's sky profile replaced the whole vanilla sky this frame (0.12.0). */
    private boolean replaced() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.effects() instanceof dev.calamidade_awakening.api.v1.client.ProfileSkyEffects own
            && own.replacing(mc.level);
    }

    /** Called at the start of every frame. */
    void invalidate() { computed = false; }

    /** Mixed state of this frame, computed on first use. */
    Atmosphere.State state(Vec3 camera, float partial) {
        if (computed) return state;
        computed = true;
        // 0.12.0: the atmospheres of the global events that apply here join the mix.
        var states = new java.util.ArrayList<>(scene.atmospheres(camera, partial));
        states.addAll(world.atmospheres(Minecraft.getInstance().level, partial));
        states.addAll(profiles.atmospheres(Minecraft.getInstance().level, partial));
        int count = 0;
        for (var s : states) if (s.active()) count++;
        active = count;
        state = Atmosphere.mix(states);
        // Exposure: accessibility intensity, then a calm ramp when the player hides lightning flashes.
        Minecraft mc = Minecraft.getInstance();
        double now = mc.level == null ? Double.NaN : mc.level.getGameTime() + partial;
        float elapsed = Double.isNaN(previousTime) || Double.isNaN(now) ? 0 : (float) Math.clamp(now - previousTime, 0, 20);
        previousTime = now;
        float target = state.active() ? AtmosphereMath.scaledExposure(state.exposure(), ClientConfig.screenEffects()) : 1;
        boolean calm = mc.options.hideLightningFlash().get();
        exposure = calm ? AtmosphereMath.approach(exposure, target, elapsed, AtmosphereMath.CALM_EXPOSURE_PER_TICK) : target;
        return state;
    }

    /** Game fog color recorded in this frame's ComputeFogColor (0.12.0: the sky pass estimates the vanilla sky). */
    float[] fogColor() { return new float[] {fogR, fogG, fogB}; }

    /**
     * Opaque sky regions and gravitational lenses of this frame's sky layers (0.12.0), set by the sky pass before this
     * one: the atmosphere skips its gradient and stars on the regions and bends the scene like the sky pass.
     */
    void skyMasks(float[] masks, int count, float[] lenses) {
        skyMasks = masks;
        skyMaskCount = count;
        skyLenses = lenses;
    }
    private float[] skyLenses = new float[12];
    private float[] skyMasks = new float[32];
    private int skyMaskCount;

    /** Active atmospheres in the last computed frame (F7). */
    int active() { return active; }

    /** True when this frame needs the pass: an active atmosphere, or exposure still easing back to 1. */
    boolean needsPass() { return state.active() || Math.abs(exposure - 1) > EXPOSURE_EPSILON || replaced(); }

    /** NeoForge ComputeFogColor: moves the game fog color toward the atmosphere, then records what was rendered. */
    void fogColor(ViewportEvent.ComputeFogColor event) {
        var s = state(event.getCamera().getPosition(), (float) event.getPartialTick());
        if (s.active() && s.fogDensity() > 0 && clearView(event.getCamera())) {
            var vanilla = new Atmosphere.Rgb(unit(event.getRed()), unit(event.getGreen()), unit(event.getBlue()));
            var color = s.fogColorOver(vanilla);
            event.setRed(color.r());
            event.setGreen(color.g());
            event.setBlue(color.b());
        }
        fogR = event.getRed();
        fogG = event.getGreen();
        fogB = event.getBlue();
    }

    /** NeoForge RenderFog: pulls the terrain fog toward the atmosphere distances (never thinner than the game's). */
    void fogDistances(ViewportEvent.RenderFog event) {
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || event.getType() != FogType.NONE
            || !ClientConfig.vanillaFog() || !clearView(event.getCamera())) return;
        var s = state(event.getCamera().getPosition(), (float) event.getPartialTick());
        if (!s.active() || s.fogDensity() <= 0) return;
        float near = event.getNearPlaneDistance(), far = event.getFarPlaneDistance();
        event.setNearPlaneDistance(s.fogNear(near));
        event.setFarPlaneDistance(s.fogFar(near, far));
        // RenderFog changes only apply when the event is canceled (ClientHooks.onFogRender).
        event.setCanceled(true);
    }

    /** Queues the atmosphere pass when needed. Returns true when a pass was queued. */
    boolean draw(FxApi.Frame frame, Vec3 camera, float partial) {
        var s = state(camera, partial);
        if (!needsPass()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        Vec3 sky = mc.level.getSkyColor(camera, partial);
        float intensity = ClientConfig.screenEffects();
        var tint = s.ambientTint();
        float norm = 1 / Math.max(tint.luminance(), .05f);
        boolean screenFog = !ClientConfig.vanillaFog() && clearView(mc.gameRenderer.getMainCamera());
        float renderDistance = mc.gameRenderer.getRenderDistance();
        float fogR = this.fogR, fogG = this.fogG, fogB = this.fogB, exposure = this.exposure;
        float[] masks = skyMasks.clone();
        float[] lens = skyLenses.clone();
        int maskCount = skyMaskCount;
        float replaced = replaced() ? 1 : 0;
        frame.pass(PROGRAM, u -> {
            u.f3("CaAtmoSkyTop", s.skyTop().r(), s.skyTop().g(), s.skyTop().b());
            u.f3("CaAtmoSkyHorizon", s.skyHorizon().r(), s.skyHorizon().g(), s.skyHorizon().b());
            u.f3("CaAtmoFogColor", s.fogColor().r(), s.fogColor().g(), s.fogColor().b());
            u.f3("CaAtmoTint", Math.min(4, tint.r() * norm), Math.min(4, tint.g() * norm), Math.min(4, tint.b() * norm));
            u.f3("CaAtmoVanillaSky", (float) sky.x, (float) sky.y, (float) sky.z);
            u.f3("CaAtmoVanillaFog", fogR, fogG, fogB);
            u.f4("CaAtmoA", s.skyBlend(), s.fogDensity(), s.fogStart(), s.fogEnd());
            u.f4("CaAtmoB", exposure, s.ambientStrength() * intensity, s.stars(), screenFog ? 1 : 0);
            u.f4("CaAtmoC", renderDistance, maskCount, replaced, 0);
            if (maskCount > 0) u.vec4s("CaAtmoMask", masks);
            u.f4("CaAtmoLens0", lens[0], lens[1], lens[2], lens[3]);
            u.f4("CaAtmoLens1", lens[4], lens[5], lens[6], lens[7]);
            u.f4("CaAtmoLensExt", lens[8], lens[9], lens[10], 0);
        });
        return true;
    }

    /** False under water, lava or powder snow and with blindness or darkness: the game fog wins there. */
    private static boolean clearView(Camera camera) {
        if (camera.getFluidInCamera() != FogType.NONE) return false;
        return !(camera.getEntity() instanceof LivingEntity living)
            || !(living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    private static float unit(float x) { return Float.isNaN(x) ? 0 : Math.clamp(x, 0f, 1f); }
}
