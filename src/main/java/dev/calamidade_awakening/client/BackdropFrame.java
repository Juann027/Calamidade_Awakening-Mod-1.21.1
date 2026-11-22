package dev.calamidade_awakening.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import cyou.rimuru.rimulib.mc1211.client.render.Uniforms;
import dev.calamidade_awakening.api.BackdropMath;
import dev.calamidade_awakening.api.v1.Backdrop;
import dev.calamidade_awakening.config.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom sky backgrounds of the current frame (0.14.0, {@link Backdrop}). Collects the backgrounds of active effects,
 * global events and the dimension's sky profile, keeps at most {@link BackdropMath#MAX_DRAWN} ({@link BackdropMath#select})
 * and draws them right after the vanilla sky ({@code AFTER_SKY}) as static geometry around the camera: one cube for
 * every cubemap and one UV sphere for every panorama and gradient, uploaded once, with the game's
 * {@code position_tex_color} shader, no depth write and no depth test. No post-processing pass is needed for that:
 * the engine's passes only learn the frame's backgrounds ({@link #uniforms}) to compose the Atmosphere sky, the
 * vanilla sun and moon removal and the lens fill with them.
 *
 * <p>Same conditions as the vanilla sky ({@code LevelRenderer.renderSky}, javap): nothing is drawn with blindness or
 * darkness, with the camera in lava or powder snow, or under a foggy biome or boss fog; and nothing at all with the
 * client option {@code customSkies} off. The render state the game had at {@code AFTER_SKY} (depth mask and test on,
 * culling on, blending off, default blend function, white shader color) is put back after drawing.
 */
final class BackdropFrame {
    private static final ResourceLocation SUN = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    private static final ResourceLocation MOON = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    /** Radius of the geometry around the camera, blocks (any value inside the near/far range works: no depth test). */
    private static final float RADIUS = 50;
    private static final int SEGMENTS = 64, RINGS = 32;

    /** One candidate from a source: the background, its state without the weight and the real age for the spin. */
    record Source(Backdrop backdrop, Backdrop.State state, double seconds) {}

    /** One background drawn this frame (bottom first): texture, opacity, turn (degrees) and tint. */
    record Layer(Backdrop backdrop, BackdropTextures.Entry entry, float alpha, float rotation) {
        float red() { return ((backdrop.tint() >> 16) & 0xFF) / 255f; }
        float green() { return ((backdrop.tint() >> 8) & 0xFF) / 255f; }
        float blue() { return (backdrop.tint() & 0xFF) / 255f; }
    }

    private final StageScene scene;
    private final WorldEventsClient world;
    private final SkyProfilesClient profiles;
    private final BackdropTextures textures = new BackdropTextures();
    private boolean computed;
    private List<Layer> layers = List.of();
    private int candidates;
    private boolean blocked;
    private int draws;
    private VertexBuffer cube, sphere, sun;
    private final VertexBuffer[] moon = new VertexBuffer[8];

    BackdropFrame(StageScene scene, WorldEventsClient world, SkyProfilesClient profiles) {
        this.scene = scene;
        this.world = world;
        this.profiles = profiles;
    }

    /** Called at the start of every frame. */
    void invalidate() { computed = false; }

    /** The frame's backgrounds, resolved on first use (bottom first; empty when none, blocked or turned off). */
    List<Layer> layers() {
        if (computed) return layers;
        computed = true;
        Minecraft mc = Minecraft.getInstance();
        layers = List.of();
        candidates = 0;
        blocked = false;
        if (mc.level == null || !ClientConfig.customSkies()) return layers;
        Camera view = mc.gameRenderer.getMainCamera();
        Vec3 camera = view.getPosition();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        List<BackdropMath.Candidate<Source>> all = new ArrayList<>(scene.backdrops(camera, partial));
        all.addAll(world.backdrops(mc.level, partial));
        all.addAll(profiles.backdrops(mc.level, partial));
        candidates = all.size();
        if (all.isEmpty()) return layers;
        if (skyBlocked(mc, view)) {
            blocked = true;
            return layers;
        }
        boolean motion = ClientConfig.motionEffects();
        List<Layer> out = new ArrayList<>(BackdropMath.MAX_DRAWN);
        for (var drawn : BackdropMath.select(all)) {
            Source s = drawn.value();
            BackdropTextures.Entry entry = textures.get(s.backdrop());
            if (entry == null) continue;
            out.add(new Layer(s.backdrop(), entry, drawn.alpha(), (float) (s.state().rotationAt(s.seconds(), motion) % 360.0)));
        }
        layers = out;
        return layers;
    }

    /**
     * True when the game draws no sky this frame (javap, {@code LevelRenderer.renderSky} and
     * {@code doesMobEffectBlockSky}): foggy biome or boss fog, camera in powder snow or lava, blindness or darkness.
     */
    static boolean skyBlocked(Minecraft mc, Camera view) {
        Vec3 p = view.getPosition();
        if (mc.level.effects().isFoggyAt((int) Math.floor(p.x), (int) Math.floor(p.z))
            || mc.gui.getBossOverlay().shouldCreateWorldFog()) return true;
        FogType fluid = view.getFluidInCamera();
        if (fluid == FogType.POWDER_SNOW || fluid == FogType.LAVA) return true;
        return view.getEntity() instanceof LivingEntity living
            && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    /** {@code AFTER_SKY}: draws the frame's backgrounds and, when asked, the vanilla sun and moon again on top. */
    void draw(RenderLevelStageEvent event, float hideSun, float hideMoon) {
        List<Layer> list = layers();
        if (list.isEmpty()) return;
        ShaderInstance shader = GameRenderer.getPositionTexColorShader();
        if (shader == null) return;
        prepare();
        Matrix4f view = event.getModelViewMatrix(), projection = event.getProjectionMatrix();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        try {
            for (Layer layer : list) {
                int id = layer.entry().id();
                if (id == 0) continue;
                RenderSystem.setShaderTexture(0, id);
                RenderSystem.setShaderColor(layer.red(), layer.green(), layer.blue(), layer.alpha());
                VertexBuffer buffer = layer.entry().cube ? cube : sphere;
                buffer.bind();
                buffer.drawWithShader(new Matrix4f(view).rotateY((float) -Math.toRadians(layer.rotation())), projection, shader);
                draws++;
            }
            VertexBuffer.unbind();
            redrawSunAndMoon(list, view, projection, event.getPartialTick().getGameTimeDeltaPartialTick(false), hideSun, hideMoon);
        } finally {
            VertexBuffer.unbind();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        }
    }

    /**
     * Vanilla sun and moon drawn again over backgrounds that ask for it ({@link Backdrop#vanillaSunMoon()}), with the
     * game's geometry ({@code Ry(-90)·Rx(timeOfDay·360)}, quads of half size 30 and 20 at 100 blocks), additive like the
     * game, and only the part the backgrounds hid: alpha = (1 − rain) × coverage at the body × (1 − what a sky layer
     * hides). The sky pass then removes only the uncovered part of the vanilla body ({@code 1 − coverage}).
     */
    private void redrawSunAndMoon(List<Layer> list, Matrix4f view, Matrix4f projection, float partial, float hideSun, float hideMoon) {
        sunRedrawn = moonRedrawn = 0;
        if (list.stream().noneMatch(l -> l.backdrop().vanillaSunMoon())) return;
        Minecraft mc = Minecraft.getInstance();
        if (!SkyFrame.vanillaDrawn(mc, mc.gameRenderer.getMainCamera().getPosition())) return;
        ShaderInstance shader = GameRenderer.getPositionTexShader();
        if (shader == null) return;
        double timeOfDay = mc.level.getTimeOfDay(partial);
        float clear = 1 - mc.level.getRainLevel(partial);
        var sunDir = dev.calamidade_awakening.api.SkyMath.sunDirection(timeOfDay);
        float sunAlpha = clear * redrawn(list, sunDir.x(), sunDir.y(), sunDir.z()) * (1 - hideSun);
        float moonAlpha = clear * redrawn(list, -sunDir.x(), -sunDir.y(), -sunDir.z()) * (1 - hideMoon);
        sunRedrawn = sunAlpha;
        moonRedrawn = moonAlpha;
        Matrix4f body = new Matrix4f(view).rotateY((float) Math.toRadians(-90)).rotateX((float) (timeOfDay * Math.PI * 2));
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        if (sunAlpha > 1e-3f) {
            RenderSystem.setShaderTexture(0, mc.getTextureManager().getTexture(SUN).getId());
            RenderSystem.setShaderColor(1, 1, 1, sunAlpha);
            sun.bind();
            sun.drawWithShader(body, projection, shader);
        }
        if (moonAlpha > 1e-3f) {
            int phase = Math.floorMod(mc.level.getMoonPhase(), 8);
            RenderSystem.setShaderTexture(0, mc.getTextureManager().getTexture(MOON).getId());
            RenderSystem.setShaderColor(1, 1, 1, moonAlpha);
            moon[phase].bind();
            moon[phase].drawWithShader(body, projection, shader);
        }
    }

    /** Coverage, in a world direction, of the backgrounds that redraw the vanilla bodies (0..1). */
    private static float redrawn(List<Layer> list, double x, double y, double z) {
        float clear = 1;
        for (Layer l : list) if (l.backdrop().vanillaSunMoon()) clear *= 1 - l.alpha() * sample(l, x, y, z)[3];
        return 1 - clear;
    }

    /** Texture color and coverage of a layer in a world direction (the CPU grid, turned back by the layer's rotation). */
    private static float[] sample(Layer l, double x, double y, double z) {
        double[] t = BackdropMath.turn(x, y, z, -l.rotation());
        return l.entry().sample(t[0], t[1], t[2]);
    }

    /** Combined coverage of the frame's backgrounds in a world direction (0..1). */
    float coverage(double x, double y, double z) {
        float clear = 1;
        for (Layer l : layers()) clear *= 1 - l.alpha() * sample(l, x, y, z)[3];
        return 1 - clear;
    }

    /**
     * NeoForge {@code ComputeFogColor}, before the Atmosphere: with backgrounds that {@linkplain Backdrop#matchFog match
     * the fog}, the fog color becomes the backgrounds' color just above the horizon in the direction the camera looks,
     * composed over the game's fog by their coverage. Distant terrain then fades into the background instead of into
     * the vanilla fog color (the light line at the horizon). Not under water, lava or powder snow, nor blinded.
     */
    void fogColor(ViewportEvent.ComputeFogColor event) {
        List<Layer> list = layers();
        if (list.isEmpty()) return;
        Camera camera = event.getCamera();
        if (camera.getFluidInCamera() != FogType.NONE) return;
        double yaw = Math.toRadians(camera.getYRot());
        // About 1.5 degrees above the horizon, where terrain at the render distance meets the sky.
        double x = -Math.sin(yaw), y = .026, z = Math.cos(yaw);
        float r = event.getRed(), g = event.getGreen(), b = event.getBlue();
        boolean any = false;
        for (Layer l : list) {
            if (!l.backdrop().matchFog()) continue;
            float[] c = sample(l, x, y, z);
            float a = l.alpha() * c[3];
            r += (c[0] * l.red() - r) * a;
            g += (c[1] * l.green() - g) * a;
            b += (c[2] * l.blue() - b) * a;
            any = true;
        }
        if (!any) return;
        event.setRed(r);
        event.setGreen(g);
        event.setBlue(b);
    }

    /**
     * Uniforms of the frame's backgrounds for the sky and atmosphere passes ({@code calamidade_sky.glsl}): for slot 0
     * (bottom) and 1 (top), the texture, {@code CaBdN} = (kind: 0 none, 1 cubemap, 2 panorama or gradient; opacity;
     * turn in radians; 0) and {@code CaBdTintN}. A snapshot: call while queueing the pass, apply inside it.
     */
    java.util.function.Consumer<Uniforms> uniforms() {
        List<Layer> list = layers();
        int n = list.size();
        int[] ids = new int[2];
        float[] values = new float[14];
        for (int i = 0; i < 2 && i < n; i++) {
            Layer l = list.get(i);
            ids[i] = l.entry().id();
            if (ids[i] == 0) continue;
            values[i * 7] = l.entry().cube ? 1 : 2;
            values[i * 7 + 1] = l.alpha();
            values[i * 7 + 2] = (float) Math.toRadians(l.rotation());
            values[i * 7 + 3] = l.red();
            values[i * 7 + 4] = l.green();
            values[i * 7 + 5] = l.blue();
        }
        return u -> {
            for (int i = 0; i < 2; i++) {
                if (ids[i] != 0 && values[i * 7] > 0) u.glTexture("CaBdTex" + i, ids[i]);
                u.f4("CaBd" + i, values[i * 7], values[i * 7 + 1], values[i * 7 + 2], 0);
                u.f3("CaBdTint" + i, values[i * 7 + 3], values[i * 7 + 4], values[i * 7 + 5]);
            }
        };
    }

    /** Uploads the static geometry once (render thread). */
    private void prepare() {
        if (cube != null) return;
        cube = upload(cubeMesh());
        sphere = upload(sphereMesh());
        sun = upload(bodyMesh(30, 100, 0, 0, 1, 1));
        for (int phase = 0; phase < 8; phase++) {
            int column = phase % 4, row = phase / 4 % 2;
            moon[phase] = upload(moonMesh(20, column / 4f, row / 2f, (column + 1) / 4f, (row + 1) / 2f));
        }
    }

    private static VertexBuffer upload(BufferBuilder builder) {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(builder.buildOrThrow());
        VertexBuffer.unbind();
        return buffer;
    }

    /** Six faces, corners from {@link BackdropMath#cubeDirection}, atlas coordinates of the 3 × 2 layout. */
    private static BufferBuilder cubeMesh() {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        double[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int f = 0; f < 6; f++) for (double[] c : corners) {
            double[] d = BackdropMath.cubeDirection(f, c[0], c[1]);
            b.addVertex((float) d[0] * RADIUS, (float) d[1] * RADIUS, (float) d[2] * RADIUS)
                .setUv((float) (((f % 3) + c[0]) / 3), (float) (((f / 3) + c[1]) / 2)).setColor(255, 255, 255, 255);
        }
        return b;
    }

    /** UV sphere, {@value #SEGMENTS} × {@value #RINGS} quads, corners from {@link BackdropMath#panoramaDirection}. */
    private static BufferBuilder sphereMesh() {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int j = 0; j < RINGS; j++) for (int i = 0; i < SEGMENTS; i++) {
            int[][] corners = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
            for (int[] c : corners) {
                double u = c[0] / (double) SEGMENTS, v = c[1] / (double) RINGS;
                double[] d = BackdropMath.panoramaDirection(u, v);
                b.addVertex((float) d[0] * RADIUS, (float) d[1] * RADIUS, (float) d[2] * RADIUS)
                    .setUv((float) u, (float) v).setColor(255, 255, 255, 255);
            }
        }
        return b;
    }

    /** The vanilla sun quad ({@code LevelRenderer.renderSky}): half size {@code s} at height {@code h}. */
    private static BufferBuilder bodyMesh(float s, float h, float u0, float v0, float u1, float v1) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(-s, h, -s).setUv(u0, v0);
        b.addVertex(s, h, -s).setUv(u1, v0);
        b.addVertex(s, h, s).setUv(u1, v1);
        b.addVertex(-s, h, s).setUv(u0, v1);
        return b;
    }

    /** The vanilla moon quad of one phase, below at −100 with the game's corner order. */
    private static BufferBuilder moonMesh(float s, float u0, float v0, float u1, float v1) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(-s, -100, s).setUv(u1, v1);
        b.addVertex(s, -100, s).setUv(u0, v1);
        b.addVertex(s, -100, -s).setUv(u0, v0);
        b.addVertex(-s, -100, -s).setUv(u1, v0);
        return b;
    }

    /** Releases the textures (resource reload, leaving the world); the geometry stays (it depends on no resource). */
    void clear() {
        textures.clear();
        computed = false;
        layers = List.of();
    }

    /** Backgrounds drawn in the last resolved frame. */
    int drawn() { return layers.size(); }
    /** Active backgrounds that competed in the last resolved frame. */
    int candidates() { return candidates; }
    /** True when the last frame had backgrounds but the game's sky was blocked (blindness, lava...). */
    boolean blocked() { return blocked; }
    /** True when the frame's backgrounds cover the whole sky (every drawn texel opaque, combined opacity 1). */
    boolean opaque() {
        float clear = 1;
        for (Layer l : layers) clear *= 1 - (l.entry().opaque ? l.alpha() : 0);
        return !layers.isEmpty() && clear <= 1 - BackdropMath.OPAQUE;
    }
    private float sunRedrawn, moonRedrawn;
    /** Opacity of the vanilla sun and moon drawn again over the backgrounds in the last frame (tests). */
    float[] redrawnBodies() { return new float[] {sunRedrawn, moonRedrawn}; }
    /** Geometry draws since start (tests). */
    int draws() { return draws; }
    /** Texture cache (tests). */
    BackdropTextures textures() { return textures; }
    /** True when the dimension draws the vanilla sky type at all (diagnostics). */
    static boolean normalSky(Minecraft mc) {
        return mc.level != null && mc.level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL;
    }
}
