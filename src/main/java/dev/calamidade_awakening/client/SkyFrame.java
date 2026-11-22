package dev.calamidade_awakening.client;

import cyou.rimuru.rimulib.mc1211.client.api.FxApi;
import dev.calamidade_awakening.api.FrameLimits;
import dev.calamidade_awakening.api.SkyMath;
import dev.calamidade_awakening.api.v1.Sky;
import dev.calamidade_awakening.config.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sky layers of the current frame (0.12.0). Resolves the {@link Sky} layers of every active instance (no frustum and no
 * effect budget: the sky stays when you turn away), keeps the most important ones up to the profile's cap (HERO first,
 * then by weight) and paints them in the engine pass "sky", the first pass of the frame, outside the addon pass budget.
 * Also carries the world darkening (eclipse) with the atmosphere's accessibility rules and hides the vanilla sun or moon
 * where a layer asks.
 *
 * <p>Vanilla sun and moon: NeoForge 21.1 only lets a dimension replace its whole sky
 * ({@code IDimensionSpecialEffectsExtension.renderSky} returning true, javap); there is no event to skip only the sun.
 * So the pass removes them itself: the game adds the sun and moon textures with {@code SRC_ALPHA, ONE}, and the pass
 * subtracts the same texels at the same quad (geometry read from {@code LevelRenderer.renderSky}: rotation
 * {@code Ry(-90)·Rx(timeOfDay·360)}, quads of half size 30 and 20 at 100 blocks). Only when the vanilla path really drew
 * them: NORMAL sky, no fog, no blindness, no lava or powder snow, and dimension effects that do not override
 * {@code renderSky}.
 */
final class SkyFrame {
    static final String PROGRAM = "calamidade_awakening:sky";
    static final int TEXELS = 6, ROWS = SkyMath.MAX_LAYERS;
    private static final ResourceLocation SUN = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    private static final ResourceLocation MOON = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    private static final float EXPOSURE_EPSILON = 1e-3f;
    /** Seconds after which the animation clock of a layer wraps (keeps the shader's floats precise). */
    private static final double WRAP_SECONDS = 3600;

    /**
     * One resolved layer: its state, direction (world axes), anchor, the instance's seed, real age (ticks, partial
     * included) and order, and whether it is from a HERO effect.
     */
    record Resolved(Sky layer, Sky.State state, Sky.Direction direction, float seed, double realAge, long order,
                    boolean hero) {}

    private final StageScene scene;
    private final DataTexture texture = new DataTexture(TEXELS, ROWS);
    private final float[] data = new float[TEXELS * 4 * ROWS];
    private final float[] masks = new float[4 * 8];
    private List<Resolved> layers = List.of();
    private int candidates, maskCount;
    private float darkening, hideSun, hideMoon, exposure = 1;
    private double previousTime = Double.NaN;
    private static final Map<Class<?>, Boolean> OVERRIDES_SKY = new ConcurrentHashMap<>();

    SkyFrame(StageScene scene) { this.scene = scene; }

    /** Resolves this frame's layers, the darkening and the vanilla bodies to hide. */
    void collect(Vec3 camera, float partial, FrameLimits limits) {
        Minecraft mc = Minecraft.getInstance();
        double timeOfDay = mc.level == null ? 0 : mc.level.getTimeOfDay(partial);
        List<dev.calamidade_awakening.api.SkyMath.Candidate<Resolved>> all = scene.skies(camera, partial, timeOfDay);
        candidates = all.size();
        float dark = 0, sun = 0, moon = 0;
        for (var c : all) {
            Resolved r = c.value();
            dark = Math.max(dark, r.state().darkening());
            if (r.layer().hidesVanilla()) {
                if (r.layer().anchor() == Sky.Anchor.SUN) sun = Math.max(sun, r.state().weight());
                if (r.layer().anchor() == Sky.Anchor.MOON) moon = Math.max(moon, r.state().weight());
            }
        }
        darkening = dark;
        hideSun = sun;
        hideMoon = moon;
        layers = SkyMath.select(all, limits.skyLayers());
        // Exposure of the world: accessibility intensity, then the atmosphere's calm ramp with hidden flashes.
        double now = mc.level == null ? Double.NaN : mc.level.getGameTime() + partial;
        float elapsed = Double.isNaN(previousTime) || Double.isNaN(now) ? 0 : (float) Math.clamp(now - previousTime, 0, 20);
        previousTime = now;
        float target = SkyMath.exposure(darkening, ClientConfig.screenEffects());
        exposure = SkyMath.displayed(exposure, target, elapsed, mc.options.hideLightningFlash().get());
        buildMasks();
    }

    /** Layers painted this frame. */
    List<Resolved> layers() { return layers; }
    /** Active layers that competed this frame (before the profile cap). */
    int candidates() { return candidates; }

    /** True when this frame needs the pass: a layer, a vanilla body to hide, or exposure still easing back to 1. */
    boolean needsPass() {
        return !layers.isEmpty() || hideSun > 0 || hideMoon > 0 || Math.abs(exposure - 1) > EXPOSURE_EPSILON;
    }

    /**
     * Opaque regions of the painted layers for the atmosphere pass (direction and angular radius, radians, per vec4):
     * there the atmosphere's sky gradient and stars do not apply, so a black disk stays black.
     */
    float[] masks() { return masks; }
    /** Number of entries in {@link #masks()}. */
    int maskCount() { return maskCount; }

    private void buildMasks() {
        int n = 0;
        for (Resolved r : layers) {
            var s = r.state();
            double radius = Math.toRadians(s.radius());
            var dir = r.direction();
            switch (s.type()) {
                case BLACK_HOLE -> n = mask(n, dir, radius * .34 * 1.02);
                case PORTAL -> n = mask(n, dir, radius * s.progress());
                case CELESTIAL_BODY -> n = mask(n, dir, radius);
                case ECLIPSE -> {
                    n = mask(n, dir, radius * Sky.ECLIPSE_SUN_RATIO);
                    if (r.layer().anchor() != Sky.Anchor.MOON) {
                        double[] basis = SkyMath.basis(dir, Math.toRadians(s.rotationAt(0)));
                        var disk = SkyMath.fromLocal(basis, radius * (1 - s.progress()) * Sky.ECLIPSE_START_DISTANCE, 0);
                        n = mask(n, disk, radius);
                    }
                }
                default -> { }
            }
        }
        maskCount = n;
    }

    private int mask(int n, Sky.Direction dir, double radius) {
        if (n >= 8 || !(radius > 0)) return n;
        masks[n * 4] = (float) dir.x();
        masks[n * 4 + 1] = (float) dir.y();
        masks[n * 4 + 2] = (float) dir.z();
        masks[n * 4 + 3] = (float) radius;
        return n + 1;
    }

    /** Queues the sky pass when needed. Returns true when a pass was queued. */
    boolean draw(FxApi.Frame frame, Vec3 camera, float partial, FrameLimits limits, AtmosphereFrame atmosphere) {
        if (!needsPass()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        boolean motion = ClientConfig.motionEffects();
        float flash = mc.options.hideLightningFlash().get() ? 0 : ClientConfig.screenEffects();
        int lenses = SkyMath.lenses(limits.skyLayers());
        int count = Math.min(layers.size(), ROWS);
        int k = 0;
        for (int i = 0; i < count; i++) {
            Resolved r = layers.get(i);
            Sky.State s = r.state();
            var dir = r.direction();
            float weight = s.weight();
            boolean portal = s.type() == Sky.Type.PORTAL;
            double spinDegrees = motion ? s.spin() * r.realAge() : 0;
            double roll = Math.toRadians(s.rotation() + (portal ? spinDegrees : 0));
            float lens = 0;
            if (s.type() == Sky.Type.BLACK_HOLE && lenses > 0 && s.lens() > 0) {
                lens = s.lens() * weight * (motion ? 1 : .5f);
                lenses--;
            }
            double seconds = r.realAge() / 20.0;
            if (!motion) seconds = s.type() == Sky.Type.AURORA ? seconds * .2 : 0;
            seconds %= WRAP_SECONDS;
            int anchor = r.layer().anchor() == Sky.Anchor.SUN ? 1 : r.layer().anchor() == Sky.Anchor.MOON ? 2 : 0;
            // 0: direction, angular radius
            k = put(k, (float) dir.x(), (float) dir.y(), (float) dir.z(), (float) Math.toRadians(s.radius()));
            // 1: type, roll, tilt, progress
            k = put(k, s.type().ordinal(), (float) (roll % (Math.PI * 2)), (float) Math.toRadians(s.tilt()), s.progress());
            // 2: primary × intensity, weight
            k = put(k, BeamFrame.red(s.primary()) * s.intensity(), BeamFrame.green(s.primary()) * s.intensity(),
                BeamFrame.blue(s.primary()) * s.intensity(), weight);
            // 3: secondary × intensity, lens strength
            k = put(k, BeamFrame.red(s.secondary()) * s.intensity(), BeamFrame.green(s.secondary()) * s.intensity(),
                BeamFrame.blue(s.secondary()) * s.intensity(), lens);
            // 4: seed, seconds, flash gain, anchor
            k = put(k, r.seed(), (float) seconds, flash, anchor);
            // 5: accumulated spin (radians)
            k = put(k, (float) Math.toRadians(spinDegrees % 360.0), 0, 0, 0);
        }
        int id = texture.upload(data, count);
        // Vanilla sun and moon: the frame they were drawn in, and whether they were drawn at all this frame.
        double timeOfDay = mc.level.getTimeOfDay(partial);
        double[] axes = SkyMath.vanillaAxes(timeOfDay);
        float vanilla = vanillaDrawn(mc, camera) ? 1 - mc.level.getRainLevel(partial) : 0;
        int sunId = hideSun > 0 && vanilla > 0 ? mc.getTextureManager().getTexture(SUN).getId() : 0;
        int moonId = hideMoon > 0 && vanilla > 0 ? mc.getTextureManager().getTexture(MOON).getId() : 0;
        int moonPhase = mc.level.getMoonPhase();
        Vec3 skyColor = mc.level.getSkyColor(camera, partial);
        float[] fog = atmosphere.fogColor();
        float renderDistance = mc.gameRenderer.getRenderDistance();
        float info = count, worldExposure = exposure, sunHide = sunId != 0 ? hideSun : 0, moonHide = moonId != 0 ? hideMoon : 0;
        int quality = limits.tier().ordinal();
        frame.pass(PROGRAM, u -> {
            u.glTexture("CaSkyData", id);
            if (sunId != 0) u.glTexture("CaSkySunTex", sunId);
            if (moonId != 0) u.glTexture("CaSkyMoonTex", moonId);
            u.f4("CaSkyInfo", info, worldExposure, quality, 0);
            u.f4("CaSkyVanilla", sunHide, moonHide, moonPhase, vanilla);
            u.f3("CaSkyAxisX", (float) axes[0], (float) axes[1], (float) axes[2]);
            u.f3("CaSkyAxisY", (float) axes[3], (float) axes[4], (float) axes[5]);
            u.f3("CaSkyAxisZ", (float) axes[6], (float) axes[7], (float) axes[8]);
            u.f3("CaSkyVanillaSky", (float) skyColor.x, (float) skyColor.y, (float) skyColor.z);
            u.f3("CaSkyVanillaFog", fog[0], fog[1], fog[2]);
            u.f4("CaSkyMisc", renderDistance, 0, 0, 0);
        });
        return true;
    }

    /**
     * True when the vanilla sky path drew the sun and moon this frame ({@code LevelRenderer.renderSky}, javap): the
     * dimension effects do not replace the sky, the sky type is NORMAL, there is no world fog (foggy biome or boss) and
     * the camera is not in lava or powder snow nor blinded.
     */
    private static boolean vanillaDrawn(Minecraft mc, Vec3 camera) {
        DimensionSpecialEffects effects = mc.level.effects();
        if (effects.skyType() != DimensionSpecialEffects.SkyType.NORMAL || overridesSky(effects)) return false;
        if (effects.isFoggyAt((int) Math.floor(camera.x), (int) Math.floor(camera.z))
            || mc.gui.getBossOverlay().shouldCreateWorldFog()) return false;
        Camera view = mc.gameRenderer.getMainCamera();
        FogType fluid = view.getFluidInCamera();
        if (fluid == FogType.POWDER_SNOW || fluid == FogType.LAVA) return false;
        return !(view.getEntity() instanceof LivingEntity living)
            || !(living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    /** True when a dimension effects class overrides NeoForge's {@code renderSky} (it may draw no vanilla sun). */
    private static boolean overridesSky(DimensionSpecialEffects effects) {
        return OVERRIDES_SKY.computeIfAbsent(effects.getClass(), type -> {
            try {
                var method = type.getMethod("renderSky", net.minecraft.client.multiplayer.ClientLevel.class, int.class,
                    float.class, org.joml.Matrix4f.class, Camera.class, org.joml.Matrix4f.class, boolean.class, Runnable.class);
                return method.getDeclaringClass() != net.neoforged.neoforge.client.extensions.IDimensionSpecialEffectsExtension.class;
            } catch (NoSuchMethodException unexpected) {
                return true;
            }
        });
    }

    private int put(int k, float x, float y, float z, float w) {
        data[k] = x; data[k + 1] = y; data[k + 2] = z; data[k + 3] = w;
        return k + 4;
    }

    /** World darkening requested this frame (0..1, before accessibility) and the exposure displayed, for F7. */
    float darkening() { return darkening; }
    float exposure() { return exposure; }
}
