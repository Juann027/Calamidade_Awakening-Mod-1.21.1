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
    /** Texels per layer: 6 for the layer, then 4 for the distant bolts (0.12.0). */
    static final int TEXELS = 10, ROWS = SkyMath.MAX_LAYERS;
    private static final ResourceLocation SUN = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    private static final ResourceLocation MOON = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    private static final float EXPOSURE_EPSILON = 1e-3f;
    /** Seconds after which the animation clock of a layer wraps (keeps the shader's floats precise). */
    private static final double WRAP_SECONDS = 3600;

    /**
     * One resolved layer: its state, direction (world axes), anchor, the instance's seed, real age (ticks, partial
     * included) and order, and whether it is from a HERO effect.
     */
    record Resolved(Sky layer, Sky.State state, Sky.Direction direction, float seed, long seedBits, double realAge,
                    long order, boolean hero) {}

    private final StageScene scene;
    private final WorldEventsClient world;
    private final SkyProfilesClient profiles;
    private final DataTexture texture = new DataTexture(TEXELS, ROWS);
    private final float[] data = new float[TEXELS * 4 * ROWS];
    /** Gravitational lenses of this frame (0.12.0): two vec4 (direction, Einstein radius) and (extent 0, extent 1, count, 0). */
    private final float[] lenses = new float[12];
    private List<Resolved> layers = List.of();
    private int candidates;
    private float darkening, hideSun, hideMoon, exposure = 1;
    private double previousTime = Double.NaN;
    private static final Map<Class<?>, Boolean> OVERRIDES_SKY = new ConcurrentHashMap<>();

    SkyFrame(StageScene scene, WorldEventsClient world, SkyProfilesClient profiles) {
        this.scene = scene;
        this.world = world;
        this.profiles = profiles;
    }

    /** Resolves this frame's layers, the darkening and the vanilla bodies to hide. */
    void collect(Vec3 camera, float partial, FrameLimits limits) {
        Minecraft mc = Minecraft.getInstance();
        // NaN: no vanilla sun in this dimension (the Nether, the End); sun and moon layers go to a fixed spot (0.12.0).
        double timeOfDay = mc.level == null || mc.level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL
            ? Double.NaN : mc.level.getTimeOfDay(partial);
        List<SkyMath.Candidate<Resolved>> all = new java.util.ArrayList<>(scene.skies(camera, partial, timeOfDay));
        all.addAll(world.skies(mc.level, camera, partial, timeOfDay));
        all.addAll(profiles.skies(mc.level, camera, partial, timeOfDay));
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
        // 0.12.0: the dimension's sky profile may hide the vanilla sun and moon too.
        float[] hidden = mc.level == null ? new float[2] : profiles.hidden(mc.level, partial);
        darkening = dark;
        hideSun = Math.max(sun, hidden[0]);
        hideMoon = Math.max(moon, hidden[1]);
        layers = SkyMath.select(all, limits.skyLayers());
        // Exposure of the world: accessibility intensity, then the atmosphere's calm ramp with hidden flashes.
        double now = mc.level == null ? Double.NaN : mc.level.getGameTime() + partial;
        float elapsed = Double.isNaN(previousTime) || Double.isNaN(now) ? 0 : (float) Math.clamp(now - previousTime, 0, 20);
        previousTime = now;
        float target = SkyMath.exposure(darkening, ClientConfig.screenEffects());
        exposure = SkyMath.displayed(exposure, target, elapsed, mc.options.hideLightningFlash().get());
        buildLenses(limits);
    }

    /**
     * Lenses of the painted black holes, at most {@link SkyMath#lenses} (1 or 2): Einstein radius
     * {@code shadow × (1.05 + 1.6 × lens)} and extent {@code radius × (1.6 + 2.2 × lens)}, with {@code lens} the layer's
     * lens times its weight, halved when the player turns motion effects off. The same numbers go to the sky and the
     * atmosphere passes (calamidade_sky.glsl), so both bend the scene alike.
     */
    private void buildLenses(FrameLimits limits) {
        java.util.Arrays.fill(lenses, 0);
        int max = SkyMath.lenses(limits.skyLayers()), n = 0;
        boolean motion = ClientConfig.motionEffects();
        for (Resolved r : layers) {
            var s = r.state();
            if (n >= max || s.type() != Sky.Type.BLACK_HOLE || !(s.lens() > 0)) continue;
            float strength = s.lens() * s.weight() * (motion ? 1 : .5f);
            double radius = Math.toRadians(s.radius());
            lenses[n * 4] = (float) r.direction().x();
            lenses[n * 4 + 1] = (float) r.direction().y();
            lenses[n * 4 + 2] = (float) r.direction().z();
            lenses[n * 4 + 3] = (float) SkyMath.einstein(radius, strength);
            lenses[8 + n] = (float) SkyMath.lensExtent(radius, strength);
            n++;
        }
        lenses[10] = n;
    }

    /** Lens uniforms of this frame: lens 0, lens 1 and (extent 0, extent 1, count, 0). */
    float[] lenses() { return lenses; }

    /** Layers painted this frame. */
    List<Resolved> layers() { return layers; }
    /** Active layers that competed this frame (before the profile cap). */
    int candidates() { return candidates; }

    /** True when this frame needs the pass: a layer, a vanilla body to hide, or exposure still easing back to 1. */
    boolean needsPass() {
        return !layers.isEmpty() || hideSun > 0 || hideMoon > 0 || Math.abs(exposure - 1) > EXPOSURE_EPSILON;
    }

    /** Queues the sky pass when needed. Returns true when a pass was queued. */
    boolean draw(FxApi.Frame frame, Vec3 camera, float partial, FrameLimits limits, AtmosphereFrame atmosphere) {
        if (!needsPass()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        boolean motion = ClientConfig.motionEffects();
        float flash = mc.options.hideLightningFlash().get() ? 0 : ClientConfig.screenEffects();
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
            // 3: secondary × intensity
            k = put(k, BeamFrame.red(s.secondary()) * s.intensity(), BeamFrame.green(s.secondary()) * s.intensity(),
                BeamFrame.blue(s.secondary()) * s.intensity(), 0);
            // 4: seed, seconds, flash gain, anchor
            k = put(k, r.seed(), (float) seconds, flash, anchor);
            // 5: accumulated spin (radians)
            k = put(k, (float) Math.toRadians(spinDegrees % 360.0), 0, 0, 0);
            // 6..9: distant bolts (0.12.0), deterministic by the instance seed and real age; feet relative to the camera.
            List<SkyMath.Bolt> bolts = s.type() == Sky.Type.DISTANT_BOLTS
                ? SkyMath.bolts(r.seedBits(), r.realAge() / 20.0, s.progress(), dir.azimuth(), s.size(), camera.x, camera.z,
                    mc.options.hideLightningFlash().get())
                : List.of();
            for (int b = 0; b < SkyMath.MAX_BOLTS; b++) {
                if (b < bolts.size()) {
                    var bolt = bolts.get(b);
                    k = put(k, (float) (bolt.x() - camera.x), (float) (bolt.z() - camera.z),
                        bolt.envelope() * ClientConfig.screenEffects() * .5f + bolt.envelope() * .5f, bolt.seed());
                } else k = put(k, 0, 0, 0, 0);
            }
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
        float[] lens = lenses.clone();
        // 0.12.0: the mixed atmosphere's sky is painted here, under the layers (the atmosphere pass then skips sky pixels).
        var atmo = atmosphere.state(camera, partial);
        float atmoActive = atmo.active() ? 1 : 0, replaced = atmosphere.replaced() ? 1 : 0;
        boolean bolts = layers.stream().limit(count).anyMatch(r -> r.state().type() == Sky.Type.DISTANT_BOLTS);
        frame.pass(PROGRAM, u -> {
            u.glTexture("CaSkyData", id);
            if (sunId != 0) u.glTexture("CaSkySunTex", sunId);
            if (moonId != 0) u.glTexture("CaSkyMoonTex", moonId);
            u.f4("CaSkyInfo", info, worldExposure, quality, bolts ? 1 : 0);
            u.f3("CaSkyAtmoTop", atmo.skyTop().r(), atmo.skyTop().g(), atmo.skyTop().b());
            u.f3("CaSkyAtmoHorizon", atmo.skyHorizon().r(), atmo.skyHorizon().g(), atmo.skyHorizon().b());
            u.f3("CaSkyAtmoFog", atmo.fogColor().r(), atmo.fogColor().g(), atmo.fogColor().b());
            u.f4("CaSkyAtmoA", atmo.skyBlend(), atmo.fogDensity(), atmo.stars(), atmoActive);
            u.f4("CaSkyAtmoB", replaced, 0, 0, 0);
            u.f4("CaSkyVanilla", sunHide, moonHide, moonPhase, vanilla);
            u.f3("CaSkyAxisX", (float) axes[0], (float) axes[1], (float) axes[2]);
            u.f3("CaSkyAxisY", (float) axes[3], (float) axes[4], (float) axes[5]);
            u.f3("CaSkyAxisZ", (float) axes[6], (float) axes[7], (float) axes[8]);
            u.f3("CaSkyVanillaSky", (float) skyColor.x, (float) skyColor.y, (float) skyColor.z);
            u.f3("CaSkyVanillaFog", fog[0], fog[1], fog[2]);
            u.f4("CaSkyMisc", renderDistance, 0, 0, 0);
            u.f4("CaSkyLens0", lens[0], lens[1], lens[2], lens[3]);
            u.f4("CaSkyLens1", lens[4], lens[5], lens[6], lens[7]);
            u.f4("CaSkyLensExt", lens[8], lens[9], lens[10], 0);
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
        if (effects.skyType() != DimensionSpecialEffects.SkyType.NORMAL) return false;
        // The engine's own effects (0.12.0) draw the vanilla sky unless the profile replaces it this frame.
        if (effects instanceof dev.calamidade_awakening.api.v1.client.ProfileSkyEffects own) {
            if (own.replacing(mc.level) || overridesSky(own.base())) return false;
        } else if (overridesSky(effects)) return false;
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
