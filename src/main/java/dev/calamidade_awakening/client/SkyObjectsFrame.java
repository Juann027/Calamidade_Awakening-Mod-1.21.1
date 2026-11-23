package dev.calamidade_awakening.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import cyou.rimuru.rimulib.mc1211.client.render.Uniforms;
import dev.calamidade_awakening.api.BackdropMath;
import dev.calamidade_awakening.api.SkyObjectsMath;
import dev.calamidade_awakening.api.SkySpriteMath;
import dev.calamidade_awakening.api.v1.Nebula;
import dev.calamidade_awakening.api.v1.SkyBlend;
import dev.calamidade_awakening.api.v1.SkyDarkening;
import dev.calamidade_awakening.api.v1.SkyImage;
import dev.calamidade_awakening.api.v1.SkyImpostor;
import dev.calamidade_awakening.api.v1.StarField;
import dev.calamidade_awakening.config.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sky objects of the current frame (0.14.0, phase 3): {@link StarField star fields} (with constellation lines) and
 * {@link Nebula nebulae} of active effects, global events and the dimension's sky profile. Selected by importance up to
 * the profile caps ({@link SkyObjectsMath#caps}), drawn right after the {@link BackdropFrame backgrounds} in
 * {@code AFTER_SKY}, in draw order, with premultiplied alpha ({@link SkyBlend}) and the engine's core shaders
 * {@code sky_stars} and {@code sky_nebula}.
 *
 * <p><b>Batching.</b> Each star field is built once into a static {@link VertexBuffer} (constellation lines first, then
 * the stars, brightest first) and drawn with one call; a profile that allows fewer stars draws a prefix of the same
 * buffer. Nebulae share two static meshes (a sky patch and the sky sphere); the shape is in uniforms. Nothing is built,
 * allocated or drawn while no addon attaches sky objects.
 *
 * <p><b>Coverage layer.</b> When the sky or atmosphere pass will run this frame, the same objects are drawn a second time
 * into a screen-sized RGBA8 target cleared to zero (light and coverage the objects brought, without terrain in front).
 * The passes read it ({@link #uniforms}) so they do not veil or darken the objects again and so the vanilla sun and moon
 * removal and the lens fill account for them. Without objects, or without those passes, the target is not touched.
 */
final class SkyObjectsFrame {
    /** Radius of the geometry around the camera, blocks (no depth test, any value inside the near/far range). */
    static final float RADIUS = 50;
    /**
     * Vertex format of the stars: position, color, corner (UV0), brightness and twinkle phase (UV1), size and kind (UV2),
     * and for lines the great-circle tangent (Normal), padded to 36 bytes like the game's formats with a normal.
     */
    static final VertexFormat STAR_FORMAT = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION).add("Color", VertexFormatElement.COLOR)
        .add("UV0", VertexFormatElement.UV0).add("UV1", VertexFormatElement.UV1).add("UV2", VertexFormatElement.UV2)
        .add("Normal", VertexFormatElement.NORMAL).padding(1).build();
    /** Smallest half size of a star on screen, pixels. */
    static final float MIN_HALF_PIXELS = 1.1f;
    private static final int GRID = 24, SEGMENTS = 64, RINGS = 32;
    /** Frames a star field mesh stays on the GPU after it was last drawn. */
    private static final int KEEP_FRAMES = 1200;

    /**
     * One candidate from a source: the object (StarField, Nebula, SkyImage or SkyImpostor), its state without the weight
     * and its clock (seconds of the instance, or of the world for profiles). Sprites (0.14.0, phase 4) also carry their
     * sampled state, the state one tick later (direction of motion) and the key of their instance (level of detail).
     */
    record Source(Object object, float brightness, float rotation, float spin, double seconds, Object state, Object ahead, long key) {
        Source(Object object, float brightness, float rotation, float spin, double seconds) {
            this(object, brightness, rotation, spin, seconds, null, null, 0);
        }
    }

    /**
     * Adds the images and impostors of one instance or profile state as candidates (0.14.0, phase 4): sampled at
     * {@code now} and one tick later ({@code ahead}, for {@code PATH}), with the instance weight and ranking.
     */
    static void sprites(List<SkyObjectsMath.Candidate<Source>> out, List<SkyImage> images, List<SkyImpostor> impostors,
                        dev.calamidade_awakening.api.v1.Timeline.Sampler now, dev.calamidade_awakening.api.v1.Timeline.Sampler ahead,
                        boolean hero, float weight, long order, int rank, double seconds) {
        for (SkyImage image : images) {
            var state = image.sample(now, 1f);
            out.add(new SkyObjectsMath.Candidate<>(new Source(image, state.brightness(), 0, 0, seconds, state,
                image.sample(ahead, 1f), order), hero, weight, state.opacity(), order, rank, image.order()));
        }
        for (SkyImpostor impostor : impostors) {
            var state = impostor.sample(now, 1f);
            out.add(new SkyObjectsMath.Candidate<>(new Source(impostor, state.brightness(), 0, 0, seconds, state,
                impostor.sample(ahead, 1f), order), hero, weight, state.opacity(), order, rank, impostor.order()));
        }
    }

    /** One object drawn this frame: the source, its opacity, how many stars (fields) and its turn in degrees. */
    record Drawn(Source source, float opacity, int stars, double rotation) {}

    /** Static mesh of a star field: line quads first, then star quads. */
    private static final class Mesh {
        final VertexBuffer buffer;
        final int lineQuads, stars;
        long lastUsed;
        Mesh(VertexBuffer buffer, int lineQuads, int stars) { this.buffer = buffer; this.lineQuads = lineQuads; this.stars = stars; }
    }

    private final StageScene scene;
    private final WorldEventsClient world;
    private final SkyProfilesClient profiles;
    private SkyFrame sky;
    private AtmosphereFrame atmosphere;
    private ShaderInstance starShader, nebulaShader;
    private final Map<StarField, Mesh> meshes = new IdentityHashMap<>();
    private VertexBuffer cloud, sphere;
    private RenderTarget coverage, frontLayer;
    private SkyTextures textures;
    private SkySprites sprites;
    private long frame, coverageFrame = -1, frontFrame = -1;
    private int drawnImages, drawnImpostors;
    private boolean computed, blocked;
    private List<Drawn> drawn = List.of();
    private int candidates, drawnFields, drawnStars, drawnNebulae, draws;
    private long builtMeshes;

    SkyObjectsFrame(StageScene scene, WorldEventsClient world, SkyProfilesClient profiles) {
        this.scene = scene;
        this.world = world;
        this.profiles = profiles;
    }

    /** The sky pass (exposure of the last frame) and the atmosphere (veil of this frame). */
    void bind(SkyFrame sky, AtmosphereFrame atmosphere) {
        this.sky = sky;
        this.atmosphere = atmosphere;
    }

    /** The shared texture cache (owned by the {@link BackdropFrame}; 0.14.0, phase 4). */
    void textures(SkyTextures value) {
        textures = value;
        sprites = new SkySprites(value);
    }

    void spriteShader(ShaderInstance shader) { if (sprites != null) sprites.shader(shader); }
    void starShader(ShaderInstance shader) { starShader = shader; }
    void nebulaShader(ShaderInstance shader) { nebulaShader = shader; }

    /** Called at the start of every frame. */
    void invalidate() {
        computed = false;
        frame++;
    }

    /** The frame's objects in drawing order, resolved on first use (empty when none, blocked or turned off). */
    List<Drawn> drawn() {
        if (computed) return drawn;
        computed = true;
        drawn = List.of();
        candidates = drawnFields = drawnStars = drawnNebulae = 0;
        blocked = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ClientConfig.customSkies()) return drawn;
        Camera view = mc.gameRenderer.getMainCamera();
        Vec3 camera = view.getPosition();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        List<SkyObjectsMath.Candidate<Source>> all = new ArrayList<>(scene.skyObjects(camera, partial));
        all.addAll(world.skyObjects(mc.level, partial));
        all.addAll(profiles.skyObjects(mc.level, partial));
        candidates = all.size();
        if (all.isEmpty()) return drawn;
        if (BackdropFrame.skyBlocked(mc, view)) {
            blocked = true;
            return drawn;
        }
        var caps = SkyObjectsMath.caps(ClientHooks.coordinator().limits());
        var spriteCaps = SkySpriteMath.caps(ClientHooks.coordinator().limits());
        List<SkyObjectsMath.Candidate<Source>> fields = new ArrayList<>(), nebulae = new ArrayList<>(), images = new ArrayList<>(),
            impostors = new ArrayList<>();
        for (var c : all) {
            Object o = c.value().object();
            (o instanceof StarField ? fields : o instanceof Nebula ? nebulae : o instanceof SkyImage ? images : impostors).add(c);
        }
        fields = SkyObjectsMath.select(fields, caps.fields());
        nebulae = SkyObjectsMath.select(nebulae, caps.nebulae());
        images = SkyObjectsMath.select(images, spriteCaps.images());
        impostors = SkyObjectsMath.select(impostors, spriteCaps.impostors());
        int[] counts = new int[fields.size()];
        for (int i = 0; i < counts.length; i++) counts[i] = ((StarField) fields.get(i).value().object()).count();
        int[] share = SkyObjectsMath.share(counts, caps.stars());
        Map<SkyObjectsMath.Candidate<Source>, Integer> stars = new IdentityHashMap<>();
        for (int i = 0; i < counts.length; i++) stars.put(fields.get(i), share[i]);
        List<SkyObjectsMath.Candidate<Source>> chosen = new ArrayList<>(fields);
        chosen.addAll(nebulae);
        chosen.addAll(images);
        chosen.addAll(impostors);
        boolean motion = ClientConfig.motionEffects();
        List<Drawn> out = new ArrayList<>(chosen.size());
        for (var c : SkyObjectsMath.drawingOrder(chosen)) {
            Source s = c.value();
            double turn = (s.rotation() + (motion ? s.spin() * s.seconds() : 0)) % 360.0;
            int n = stars.getOrDefault(c, 0);
            out.add(new Drawn(s, c.opacity(), n, turn));
            if (s.object() instanceof StarField) {
                drawnFields++;
                drawnStars += n;
            } else if (s.object() instanceof Nebula) drawnNebulae++;
        }
        drawn = out;
        return drawn;
    }

    /** True when the sky or atmosphere pass will compose the sky this frame (they need the coverage layer). */
    private boolean passesNeedCoverage(Vec3 camera, float partial) {
        return (sky != null && sky.needsPass()) || (atmosphere != null && atmosphere.state(camera, partial).active());
    }

    /** {@code AFTER_SKY}, after the backgrounds: draws the frame's objects and, when the passes need it, the coverage layer. */
    void draw(RenderLevelStageEvent event) {
        drawnImages = drawnImpostors = 0;
        List<Drawn> list = drawn();
        if (list.isEmpty() || starShader == null || nebulaShader == null) return;
        Minecraft mc = Minecraft.getInstance();
        Camera view = mc.gameRenderer.getMainCamera();
        Vec3 camera = view.getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        prepare();
        Matrix4f modelView = event.getModelViewMatrix(), projection = event.getProjectionMatrix();
        FrameValues values = values(mc, camera, partial);
        // 0.14.0 (phase 4): images and impostors of the frame, built once for the three targets below.
        RenderTarget main = mc.getMainRenderTarget();
        boolean anySprites = sprites != null && sprites.prepare(list, new SkySprites.Context(values.timeOfDay(),
            (mc.level.getGameTime() + partial) / 20.0, mc.options.hideLightningFlash().get(), ClientConfig.screenEffects(),
            SkySpriteMath.caps(ClientHooks.coordinator().limits()), modelView, projection, main.width, main.height));
        if (anySprites) {
            drawnImages = sprites.images();
            drawnImpostors = sprites.impostors();
        }
        // FRONT goes to its own layer only when the sky pass runs and will compose it over the procedural layers.
        boolean front = anySprites && sprites.anyFront() && sky != null && sky.needsPass();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        try {
            drawAll(list, modelView, projection, values, false, front);
            if (passesNeedCoverage(camera, partial)) {
                RenderTarget target = coverageTarget(main.width, main.height);
                target.setClearColor(0, 0, 0, 0);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                drawAll(list, modelView, projection, values, true, front);
                main.bindWrite(true);
                coverageFrame = frame;
            }
            if (front) {
                RenderTarget target = frontTarget(main.width, main.height);
                target.setClearColor(0, 0, 0, 0);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                draws += sprites.draw(list, false, true, modelView, projection, sh -> frameUniforms(sh, values), true);
                main.bindWrite(true);
                frontFrame = frame;
            }
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
     * Draws the frame's objects in order to the bound target: stars and nebulae one by one, consecutive sprites as runs
     * (FRONT sprites left out when they go to the front layer).
     */
    private void drawAll(List<Drawn> list, Matrix4f modelView, Matrix4f projection, FrameValues values, boolean toCoverage,
                         boolean skipFront) {
        List<Drawn> run = new ArrayList<>();
        for (Drawn d : list) {
            Object o = d.source().object();
            if (o instanceof SkyImage || o instanceof SkyImpostor) {
                run.add(d);
                continue;
            }
            flushSprites(run, modelView, projection, values, toCoverage, skipFront);
            drawOne(d, modelView, projection, values, toCoverage);
        }
        flushSprites(run, modelView, projection, values, toCoverage, skipFront);
    }

    private void flushSprites(List<Drawn> run, Matrix4f modelView, Matrix4f projection, FrameValues values, boolean toCoverage,
                              boolean skipFront) {
        if (run.isEmpty()) return;
        if (sprites != null) draws += sprites.draw(run, skipFront, false, modelView, projection, sh -> frameUniforms(sh, values), toCoverage);
        run.clear();
    }

    /** Uniforms of sky_objects.glsl shared by every object of the frame (the sprite shader reads the rest per object). */
    private static void frameUniforms(ShaderInstance shader, FrameValues v) {
        shader.safeGetUniform("CaFrame").set(v.exposure(), v.night(), 0, 0);
        shader.safeGetUniform("CaVeilTop").set(v.top()[0], v.top()[1], v.top()[2]);
        shader.safeGetUniform("CaVeilHorizon").set(v.horizon()[0], v.horizon()[1], v.horizon()[2]);
        shader.safeGetUniform("CaVeilFog").set(v.fog()[0], v.fog()[1], v.fog()[2]);
        shader.safeGetUniform("CaVeil").set(v.blend(), v.fogDensity(), 0, 0);
    }

    /** Values shared by every object of the frame (sky_objects.glsl): exposure, night, veil and twinkle scale. */
    private record FrameValues(float exposure, float night, float[] top, float[] horizon, float[] fog, float blend,
                               float fogDensity, double timeOfDay, float twinkleScale, boolean motion) {}

    private FrameValues values(Minecraft mc, Vec3 camera, float partial) {
        boolean normal = mc.level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL;
        float night = normal ? Math.clamp(2 * mc.level.getStarBrightness(partial) * (1 - mc.level.getRainLevel(partial)), 0f, 1f) : 1;
        float exposure = sky == null ? 1 : sky.exposure();
        var atmo = atmosphere == null ? null : atmosphere.state(camera, partial);
        boolean veil = atmo != null && atmo.active();
        float[] top = veil ? new float[] {atmo.skyTop().r(), atmo.skyTop().g(), atmo.skyTop().b()} : new float[3];
        float[] horizon = veil ? new float[] {atmo.skyHorizon().r(), atmo.skyHorizon().g(), atmo.skyHorizon().b()} : new float[3];
        float[] fog = veil ? new float[] {atmo.fogColor().r(), atmo.fogColor().g(), atmo.fogColor().b()} : new float[3];
        double timeOfDay = normal ? mc.level.getTimeOfDay(partial) : Double.NaN;
        float twinkle = SkyObjectsMath.twinkleScale(ClientConfig.screenEffects(), mc.options.hideLightningFlash().get());
        return new FrameValues(exposure, night, top, horizon, fog, veil ? atmo.skyBlend() : 0, veil ? atmo.fogDensity() : 0,
            timeOfDay, twinkle, ClientConfig.motionEffects());
    }

    private void drawOne(Drawn d, Matrix4f view, Matrix4f projection, FrameValues v, boolean toCoverage) {
        Object object = d.source().object();
        boolean celestial;
        if (object instanceof StarField f) celestial = f.celestial();
        else celestial = ((Nebula) object).celestial();
        double[] m = SkyObjectsMath.frame(v.timeOfDay(), celestial, d.rotation());
        Matrix4f objectToWorld = new Matrix4f((float) m[0], (float) m[1], (float) m[2], 0, (float) m[3], (float) m[4], (float) m[5], 0,
            (float) m[6], (float) m[7], (float) m[8], 0, 0, 0, 0, 1);
        Matrix4f modelView = new Matrix4f(view).mul(objectToWorld);
        if (object instanceof StarField field) {
            Mesh mesh = meshes.get(field);
            if (mesh == null) {
                mesh = build(field);
                meshes.put(field, mesh);
            }
            mesh.lastUsed = frame;
            int quads = mesh.lineQuads + Math.min(d.stars(), mesh.stars);
            if (quads <= 0) return;
            ShaderInstance shader = starShader;
            common(shader, objectToWorld, d, v, field.blend(), field.atmosphere(), field.darkening(), field.darkeningStrength(),
                field.daylight(), field.horizon());
            double seconds = v.motion() ? d.source().seconds() % 3600.0 : 0;
            shader.safeGetUniform("CaStar").set(MIN_HALF_PIXELS, field.twinkle() * v.twinkleScale(), field.twinkleSpeed(), (float) seconds);
            drawQuads(mesh.buffer, quads, shader, modelView, projection, toCoverage);
        } else {
            Nebula nebula = (Nebula) object;
            ShaderInstance shader = nebulaShader;
            common(shader, objectToWorld, d, v, nebula.blend(), nebula.atmosphere(), nebula.darkening(), nebula.darkeningStrength(),
                nebula.daylight(), nebula.horizon());
            boolean band = nebula.shape() == Nebula.Shape.BAND;
            double[] center = SkyObjectsMath.direction(nebula.azimuth(), nebula.elevation());
            double[][] basis = SkyObjectsMath.basis(center);
            double roll = Math.toRadians(nebula.roll()), cr = Math.cos(roll), sr = Math.sin(roll);
            double[] right = {basis[0][0] * cr + basis[1][0] * sr, basis[0][1] * cr + basis[1][1] * sr, basis[0][2] * cr + basis[1][2] * sr};
            double[] up = {-basis[0][0] * sr + basis[1][0] * cr, -basis[0][1] * sr + basis[1][1] * cr, -basis[0][2] * sr + basis[1][2] * cr};
            int octaves = SkyObjectsMath.caps(ClientHooks.coordinator().limits()).octaves();
            shader.safeGetUniform("CaNebA").set(band ? 1 : 0, (float) Math.toRadians(nebula.extent()), nebula.density(), nebula.detail());
            shader.safeGetUniform("CaNebB").set(nebula.warp(), SkyObjectsMath.noiseOffset(nebula.seed()), octaves, 0);
            shader.safeGetUniform("CaNebInner").set(BeamFrame.red(nebula.inner()), BeamFrame.green(nebula.inner()),
                BeamFrame.blue(nebula.inner()), ((nebula.inner() >>> 24) & 0xFF) / 255f);
            shader.safeGetUniform("CaNebOuter").set(BeamFrame.red(nebula.outer()), BeamFrame.green(nebula.outer()),
                BeamFrame.blue(nebula.outer()), ((nebula.outer() >>> 24) & 0xFF) / 255f);
            shader.safeGetUniform("CaNebCenter").set((float) center[0], (float) center[1], (float) center[2]);
            shader.safeGetUniform("CaNebRight").set((float) right[0], (float) right[1], (float) right[2]);
            shader.safeGetUniform("CaNebUp").set((float) up[0], (float) up[1], (float) up[2]);
            VertexBuffer mesh = band ? sphere : cloud;
            drawQuads(mesh, band ? SEGMENTS * RINGS : GRID * GRID, shader, modelView, projection, toCoverage);
        }
    }

    /** Uniforms of sky_objects.glsl for one object. */
    private static void common(ShaderInstance shader, Matrix4f objectToWorld, Drawn d, FrameValues v, SkyBlend blend,
                               float atmosphere, SkyDarkening darkening, float strength, float daylight, float horizon) {
        shader.safeGetUniform("CaWorld").set(objectToWorld);
        shader.safeGetUniform("CaObjA").set(blend.ordinal(), d.opacity(), d.source().brightness(), atmosphere);
        shader.safeGetUniform("CaObjB").set(darkening.ordinal(), strength, daylight,
            horizon > 0 ? (float) Math.sin(Math.toRadians(horizon)) : 0);
        shader.safeGetUniform("CaFrame").set(v.exposure(), v.night(), 0, 0);
        shader.safeGetUniform("CaVeilTop").set(v.top()[0], v.top()[1], v.top()[2]);
        shader.safeGetUniform("CaVeilHorizon").set(v.horizon()[0], v.horizon()[1], v.horizon()[2]);
        shader.safeGetUniform("CaVeilFog").set(v.fog()[0], v.fog()[1], v.fog()[2]);
        shader.safeGetUniform("CaVeil").set(v.blend(), v.fogDensity(), 0, 0);
    }

    /**
     * Draws the first {@code quads} quads of a static buffer, like {@code VertexBuffer.drawWithShader} (javap: default
     * uniforms, apply, {@code drawElements} with the shared quad index buffer, clear) but with a count. Premultiplied
     * blending: on the screen the alpha channel is kept; on the coverage layer it accumulates the coverage.
     */
    private void drawQuads(VertexBuffer buffer, int quads, ShaderInstance shader, Matrix4f modelView, Matrix4f projection,
                           boolean toCoverage) {
        buffer.bind();
        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, Minecraft.getInstance().getWindow());
        shader.apply();
        RenderSystem.enableBlend();
        if (toCoverage) RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        else RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.drawElements(GL11.GL_TRIANGLES, quads * 6, RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS).type().asGLType);
        shader.clear();
        draws++;
    }

    private RenderTarget coverageTarget(int width, int height) {
        if (coverage == null) coverage = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        else if (coverage.width != width || coverage.height != height) coverage.resize(width, height, Minecraft.ON_OSX);
        return coverage;
    }

    /** The front layer (0.14.0, phase 4), allocated on the first frame that needs it. */
    private RenderTarget frontTarget(int width, int height) {
        if (frontLayer == null) frontLayer = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        else if (frontLayer.width != width || frontLayer.height != height) frontLayer.resize(width, height, Minecraft.ON_OSX);
        return frontLayer;
    }

    /**
     * Uniforms of the coverage layer for the sky and atmosphere passes ({@code calamidade_sky.glsl}): {@code CaEl.x} = 1
     * and {@code CaElTex} when the layer was drawn this frame, else 0 (the passes then run the path without objects).
     * A snapshot: call while queueing the pass, apply inside it.
     */
    Consumer<Uniforms> uniforms() {
        boolean on = coverageFrame == frame && coverage != null;
        int id = on ? coverage.getColorTextureId() : 0;
        // 0.14.0 (phase 4): the front layer for the sky pass (CaEl.y = 1 and CaElFront when drawn this frame).
        boolean frontOn = frontFrame == frame && frontLayer != null;
        int frontId = frontOn ? frontLayer.getColorTextureId() : 0;
        return u -> {
            if (on) u.glTexture("CaElTex", id);
            if (frontOn) u.glTexture("CaElFront", frontId);
            u.f4("CaEl", on ? 1 : 0, frontOn ? 1 : 0, 0, 0);
        };
    }

    /** Uploads the shared nebula meshes once (render thread). */
    private void prepare() {
        if (cloud != null) return;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(GRID * GRID * 4 * 20)) {
            BufferBuilder b = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            for (int j = 0; j < GRID; j++) for (int i = 0; i < GRID; i++) {
                int[][] corners = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                for (int[] c : corners) b.addVertex(0, 0, 0).setUv(c[0] * 2f / GRID - 1, c[1] * 2f / GRID - 1);
            }
            cloud = upload(b);
        }
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(SEGMENTS * RINGS * 4 * 20)) {
            BufferBuilder b = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            for (int j = 0; j < RINGS; j++) for (int i = 0; i < SEGMENTS; i++) {
                int[][] corners = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                for (int[] c : corners) {
                    double[] d = BackdropMath.panoramaDirection(c[0] / (double) SEGMENTS, c[1] / (double) RINGS);
                    b.addVertex((float) d[0] * RADIUS, (float) d[1] * RADIUS, (float) d[2] * RADIUS).setUv(0, 0);
                }
            }
            sphere = upload(b);
        }
    }

    private static VertexBuffer upload(BufferBuilder builder) {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(builder.buildOrThrow());
        VertexBuffer.unbind();
        return buffer;
    }

    /** Builds the static mesh of a field: constellation lines (pieces along the great circle), then the stars. */
    private Mesh build(StarField field) {
        List<SkyObjectsMath.Star> stars = SkyObjectsMath.stars(field);
        List<SkyObjectsMath.Segment> segments = SkyObjectsMath.segments(field);
        int pieces = 0;
        for (var s : segments) pieces += SkyObjectsMath.pieces(s);
        int quads = pieces + stars.size();
        builtMeshes++;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(Math.max(1, quads) * 4 * STAR_FORMAT.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, STAR_FORMAT);
            for (var s : segments) {
                int n = SkyObjectsMath.pieces(s);
                int half = (int) Math.clamp(Math.round(s.halfWidth() * 1e5), 1, 32767);
                for (int k = 0; k < n; k++) {
                    double t0 = k / (double) n, t1 = (k + 1) / (double) n;
                    double[] p0 = SkyObjectsMath.along(s, t0), p1 = SkyObjectsMath.along(s, t1);
                    double[] tangent = SkyObjectsMath.unit(new double[] {p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]});
                    lineVertex(b, p0, tangent, -1, (float) t0, s.argb(), half);
                    lineVertex(b, p1, tangent, -1, (float) t1, s.argb(), half);
                    lineVertex(b, p1, tangent, 1, (float) t1, s.argb(), half);
                    lineVertex(b, p0, tangent, 1, (float) t0, s.argb(), half);
                }
            }
            float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (var s : stars) {
                int gain = (int) Math.clamp(Math.round(s.intensity() * 1000), 0, 32767);
                int phase = Math.round(s.phase() * 1000);
                int half = (int) Math.clamp(Math.round(s.halfSize() * 1e5), 1, 32767);
                for (float[] c : corners)
                    b.addVertex(s.x() * RADIUS, s.y() * RADIUS, s.z() * RADIUS).setColor(s.argb()).setUv(c[0], c[1])
                        .setUv1(gain, phase).setUv2(half, 0).setNormal(0, 1, 0);
            }
            if (quads == 0) return new Mesh(null, 0, 0);
            return new Mesh(upload(b), pieces, stars.size());
        }
    }

    /** One corner of a line piece: the point on the great circle, the tangent (the width opens on screen), across ±1. */
    private static void lineVertex(BufferBuilder b, double[] p, double[] tangent, int across, float along, int argb, int half) {
        b.addVertex((float) p[0] * RADIUS, (float) p[1] * RADIUS, (float) p[2] * RADIUS)
            .setColor(argb).setUv(along, across).setUv1(1000, 0).setUv2(half, 1)
            .setNormal((float) tangent[0], (float) tangent[1], (float) tangent[2]);
    }

    /**
     * Once per frame: lets the texture cache upload what finished decoding (within the profile's limits) and frees what
     * went unused; then frees star field meshes not drawn for {@value #KEEP_FRAMES} frames (cheap).
     */
    void sweep() {
        if (textures != null) {
            textures.pump(SkySpriteMath.caps(ClientHooks.coordinator().limits()));
            textures.sweep();
        }
        if (sprites != null) sprites.sweep();
        if (frame % 300 != 0 || meshes.isEmpty()) return;
        meshes.entrySet().removeIf(e -> {
            if (frame - e.getValue().lastUsed < KEEP_FRAMES) return false;
            if (e.getValue().buffer != null) e.getValue().buffer.close();
            return true;
        });
    }

    /** Releases every mesh and the coverage layer (resource reload, leaving the world, shader reload). */
    void clear() {
        for (Mesh m : meshes.values()) if (m.buffer != null) m.buffer.close();
        meshes.clear();
        if (cloud != null) cloud.close();
        if (sphere != null) sphere.close();
        cloud = sphere = null;
        if (coverage != null) coverage.destroyBuffers();
        coverage = null;
        coverageFrame = -1;
        if (frontLayer != null) frontLayer.destroyBuffers();
        frontLayer = null;
        frontFrame = -1;
        if (sprites != null) sprites.clear();
        computed = false;
        drawn = List.of();
    }

    /** Images and impostors drawn in the last frame (0.14.0, phase 4). */
    int[] spritesDrawn() { return new int[] {drawnImages, drawnImpostors}; }
    /** Sprites still waiting for a texture or descriptor in the last frame. */
    int spritesPending() { return sprites == null ? 0 : sprites.pending(); }
    /** Flipbooks frozen for flashes in the last frame. */
    int spritesFrozen() { return sprites == null ? 0 : sprites.frozen(); }
    /** Highest level of detail of the impostors drawn in the last frame, or −1. */
    int spriteLod() { return sprites == null ? -1 : sprites.lodLevel(); }
    /** True when the front layer was drawn this frame. */
    boolean frontDrawn() { return frontFrame == frame && frontLayer != null; }
    /** True when the front layer exists at all (tests: nothing allocated without FRONT objects). */
    boolean frontAllocated() { return frontLayer != null; }
    /** Color texture of the front layer, or 0 (tests). */
    int frontTexture() { return frontLayer == null ? 0 : frontLayer.getColorTextureId(); }
    /** The shared texture cache, or null. */
    SkyTextures textureCache() { return textures; }

    /** Star fields drawn in the last resolved frame. */
    int fields() { return drawnFields; }
    /** Stars drawn in the last resolved frame. */
    int stars() { return drawnStars; }
    /** Nebulae drawn in the last resolved frame. */
    int nebulae() { return drawnNebulae; }
    /** Active sky objects that competed in the last resolved frame. */
    int candidates() { return candidates; }
    /** True when objects were active but the game's sky was blocked. */
    boolean blocked() { return blocked; }
    /** True when the coverage layer was drawn this frame. */
    boolean coverageDrawn() { return coverageFrame == frame && coverage != null; }
    /** True when the coverage layer exists at all (tests: nothing allocated without objects). */
    boolean coverageAllocated() { return coverage != null; }
    /** Draw calls since start (tests). */
    int draws() { return draws; }
    /** Star field meshes built since start and kept now (tests). */
    long[] meshes() { return new long[] {builtMeshes, meshes.size()}; }
    /** Color texture of the coverage layer, or 0 (tests: pixel probes). */
    int coverageTexture() { return coverage == null ? 0 : coverage.getColorTextureId(); }
    /** Size of the coverage layer (tests). */
    int[] coverageSize() { return coverage == null ? new int[2] : new int[] {coverage.width, coverage.height}; }
}
