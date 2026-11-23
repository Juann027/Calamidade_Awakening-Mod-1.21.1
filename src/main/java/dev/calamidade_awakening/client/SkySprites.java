package dev.calamidade_awakening.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.calamidade_awakening.api.SkyObjectsMath;
import dev.calamidade_awakening.api.SkySpriteMath;
import dev.calamidade_awakening.api.v1.SkyImage;
import dev.calamidade_awakening.api.v1.SkyImpostor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Images, flipbooks and impostors of the frame (0.14.0, phase 4), drawn by the {@link SkyObjectsFrame} in its drawing
 * order, interleaved with stars and nebulae: one quad per sprite, built on the CPU every frame into one reused dynamic
 * {@link VertexBuffer} (position on the sky sphere by gnomonic projection, corner, row of the object), per-object
 * parameters (mode, opacity, reactions, tint, up to four weighted texture rectangles) in a {@link DataTexture}, and one
 * draw call per run of consecutive sprites that share a texture. The engine's core shader {@code sky_sprite} composes
 * like {@code sky_stars} and {@code sky_nebula} ({@code sky_objects.glsl}), so the coverage layer, the Atmosphere, the
 * darkening, the vanilla sun and moon removal and the lens apply unchanged.
 *
 * <p>Textures come from the shared {@link SkyTextures}; a sprite whose texture or descriptor is not ready, missing or
 * invalid is simply not drawn this frame. Nothing is built or uploaded while no sprite is visible.
 */
final class SkySprites {
    /** Vertex format: position, corner (UV0) and the object's row in the data texture (UV2). */
    static final VertexFormat FORMAT = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION).add("UV0", VertexFormatElement.UV0).add("UV2", VertexFormatElement.UV2).build();
    /** Texels per object row of the data texture. */
    static final int TEXELS = 8;
    /** Most sprites drawn in one frame (ULTRA caps: 96 images and 16 impostors). */
    static final int MAX = 128;
    private static final int KEEP_FRAMES = 1200;

    /** A sprite ready to draw this frame: its quad in the buffer, its texture and whether it goes to the front layer. */
    record Prepared(int quad, int texture, boolean front) {}

    /** Values of the frame the geometry and the frames need. */
    record Context(double timeOfDay, double worldSeconds, boolean hideFlashes, float screenEffects, SkySpriteMath.Caps caps,
                   Matrix4f modelView, Matrix4f projection, int width, int height) {}

    private record LodKey(long instance, Object object) {}

    private static final class LodState {
        SkySpriteMath.Lod lod = SkySpriteMath.Lod.START;
        long lastUsed;
    }

    private final SkyTextures textures;
    private final DataTexture data = new DataTexture(TEXELS, MAX);
    private final float[] rows = new float[MAX * TEXELS * 4];
    private final Map<SkyObjectsFrame.Drawn, Prepared> prepared = new IdentityHashMap<>();
    private final Map<LodKey, LodState> lods = new HashMap<>();
    private final Map<SkyImage, Boolean> detected = new IdentityHashMap<>();
    private final Map<SkyImage, Integer> detectedFor = new IdentityHashMap<>();
    private ShaderInstance shader;
    private ByteBufferBuilder bytes;
    private VertexBuffer buffer;
    private int dataId, count, images, impostors, pending, frozen;
    private boolean front;
    private long frame;

    SkySprites(SkyTextures textures) { this.textures = textures; }

    void shader(ShaderInstance value) { shader = value; }
    ShaderInstance shader() { return shader; }

    /**
     * Builds this frame's quads for the sprites in {@code list} (in its order) and uploads them with their data rows.
     * Returns true when at least one sprite will be drawn.
     */
    boolean prepare(List<SkyObjectsFrame.Drawn> list, Context ctx) {
        frame++;
        prepared.clear();
        count = images = impostors = pending = frozen = 0;
        front = false;
        boolean any = false;
        for (SkyObjectsFrame.Drawn d : list) {
            Object o = d.source().object();
            if (o instanceof SkyImage || o instanceof SkyImpostor) { any = true; break; }
        }
        if (!any || shader == null) return false;
        if (bytes == null) bytes = new ByteBufferBuilder(MAX * 4 * FORMAT.getVertexSize());
        BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, FORMAT);
        Matrix4f viewProj = new Matrix4f(ctx.projection()).mul(ctx.modelView());
        for (SkyObjectsFrame.Drawn d : list) {
            if (count >= MAX) break;
            Object o = d.source().object();
            Prepared p = null;
            if (o instanceof SkyImage image) p = image(d, image, ctx, viewProj, builder);
            else if (o instanceof SkyImpostor impostor) p = impostor(d, impostor, ctx, viewProj, builder);
            if (p == null) continue;
            prepared.put(d, p);
            if (p.front()) front = true;
            if (o instanceof SkyImage) images++; else impostors++;
        }
        MeshData mesh = builder.build();
        if (mesh == null || count == 0) {
            if (mesh != null) mesh.close();
            prepared.clear();
            return false;
        }
        if (buffer == null) buffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        buffer.bind();
        buffer.upload(mesh);
        VertexBuffer.unbind();
        dataId = data.upload(rows, count);
        return true;
    }

    private Prepared image(SkyObjectsFrame.Drawn d, SkyImage img, Context ctx, Matrix4f viewProj, BufferBuilder builder) {
        SkyImage.State st = (SkyImage.State) d.source().state();
        if (st == null) return null;
        SkyTextures.Sprite tex = textures.sprite(img.texture(), img.filter() == SkyImage.Filter.SMOOTH, img.columns(), img.rows());
        if (!tex.ready()) {
            if (tex.status == SkyTextures.Status.PENDING) pending++;
            return null;
        }
        if (tex.sourceWidth % img.columns() != 0 || tex.sourceHeight % img.rows() != 0) {
            textures.invalidSprite(tex, "the " + tex.sourceWidth + " × " + tex.sourceHeight + " PNG cannot be cut into "
                + img.columns() + " × " + img.rows() + " equal frames");
            return null;
        }
        float frameW = tex.sourceWidth / (float) img.columns(), frameH = tex.sourceHeight / (float) img.rows();
        double height = Float.isNaN(img.height()) ? st.width() * frameH / frameW : img.height() * st.width() / img.width();
        // Frame of the flipbook.
        double seconds = img.clock() == SkyImage.Clock.WORLD ? ctx.worldSeconds() : d.source().seconds();
        boolean flagged = img.animatedFrames() && flashing(img, tex);
        boolean freeze = SkySpriteMath.frozen(img.flashing(), flagged, ctx.hideFlashes(), ctx.screenEffects());
        if (freeze && img.animatedFrames()) frozen++;
        SkySpriteMath.Pick pick = SkySpriteMath.frame(img.play(), img.frames(), img.fps(), img.startFrame(), seconds, st.frame(),
            img.blendFrames() && ctx.caps().blendFrames(), freeze, img.safeFrame());
        float[][] rects = {SkySpriteMath.cell(pick.a(), img.columns(), img.rows(), 0, 0, 1, 1),
            SkySpriteMath.cell(pick.b(), img.columns(), img.rows(), 0, 0, 1, 1)};
        float[] weights = {1 - pick.mix(), pick.mix()};
        // Geometry, in the object's frame (celestial: turns with the time of day), then to the world.
        double[] m = SkyObjectsMath.frame(ctx.timeOfDay(), img.celestial(), 0);
        double[] c = SkyObjectsMath.direction(st.azimuth(), st.elevation());
        double[] motion = motion(c, d.source().ahead());
        double[][] plane = SkySpriteMath.plane(c, img.up(), motion, st.roll());
        double[] corners = SkySpriteMath.corners(c, plane[0], plane[1], st.width(), height, img.pivotX(), img.pivotY());
        float[] world = toWorld(m, corners);
        if (culled(world, viewProj)) return null;
        float opacity = d.opacity() * tex.fade() * ((img.tint() >>> 24) / 255f);
        row(count, img.blend().ordinal(), opacity, d.source().brightness(), img.atmosphere(), img.darkening().ordinal(),
            img.darkeningStrength(), img.daylight(), img.horizon(), img.tint(), tex.levels, weights, rects);
        emit(builder, world, count);
        return new Prepared(count++, tex.id, img.layer() == SkyImage.Layer.FRONT);
    }

    /** True when the addon marked the sheet or the safety net finds it flashing (cached per image and texture). */
    private boolean flashing(SkyImage img, SkyTextures.Sprite tex) {
        Integer forTex = detectedFor.get(img);
        if (forTex != null && forTex == tex.id) return detected.get(img);
        float[] luma = tex.frameLuminance(img.columns(), img.rows(), img.frames());
        boolean flagged = luma != null && SkySpriteMath.flashRate(luma, img.fps(), img.play()) > SkySpriteMath.FLASH_RATE;
        if (flagged && !img.flashing())
            textures.note("flash:" + img.texture(), "Sky sheet " + img.texture() + " alternates brightness more than "
                + SkySpriteMath.FLASH_RATE + " times per second: treated as flashing(true) while flashes are hidden (mark it in the addon)");
        detected.put(img, flagged);
        detectedFor.put(img, tex.id);
        return flagged;
    }

    private Prepared impostor(SkyObjectsFrame.Drawn d, SkyImpostor imp, Context ctx, Matrix4f viewProj, BufferBuilder builder) {
        SkyImpostor.State st = (SkyImpostor.State) d.source().state();
        if (st == null) return null;
        SkyTextures.Descriptor desc = textures.descriptor(imp.descriptor(), imp.descriptorNamespace(), imp.descriptorPath());
        if (!desc.ready()) {
            if (desc.status == SkyTextures.Status.PENDING) pending++;
            return null;
        }
        SkySpriteMath.Descriptor dd = desc.value;
        SkyImage.Play play = imp.play() != null ? imp.play() : dd.play();
        float fps = Float.isNaN(imp.fps()) ? dd.fps() : imp.fps();
        double seconds = imp.clock() == SkyImage.Clock.WORLD ? ctx.worldSeconds() : d.source().seconds();
        boolean freeze = SkySpriteMath.frozen(imp.flashing(), false, ctx.hideFlashes(), ctx.screenEffects());
        if (freeze && dd.frames() > 1) frozen++;
        SkySpriteMath.Pick pick = SkySpriteMath.frame(play, dd.frames(), fps, 0, seconds, st.frame(),
            imp.blendFrames() && ctx.caps().blendFrames(), freeze, 0);
        // Orientation and the view the camera sees.
        double[] m = SkyObjectsMath.frame(ctx.timeOfDay(), imp.celestial(), 0);
        double[] c = SkyObjectsMath.direction(st.azimuth(), st.elevation());
        double[] motion = motion(c, d.source().ahead());
        double[] r = SkySpriteMath.orientation(st.yaw(), st.pitch(), imp.roll(), imp.heading(), c, motion);
        double[] angles = SkySpriteMath.viewAngles(r, c);
        SkyImpostor.ViewBlend mode = imp.viewBlend() == SkyImpostor.ViewBlend.AUTO ? ctx.caps().viewBlend() : imp.viewBlend();
        if (pick.mix() > 0 && mode == SkyImpostor.ViewBlend.QUAD) mode = SkyImpostor.ViewBlend.PAIR;
        List<SkySpriteMath.View> views = SkySpriteMath.views(angles[0], angles[1], dd.yaws(), dd.pitches(), dd.mirror(), mode);
        // Plane of the picture: the object's top projected on the sky (its front when the top points at the camera).
        double[] up = reject(new double[] {r[3], r[4], r[5]}, c);
        if (length(up) < 1e-3) up = reject(new double[] {r[6], r[7], r[8]}, c);
        up = SkyObjectsMath.unit(up);
        double[] right = cross(c, up);
        double height = st.width() / dd.aspect();
        double[] corners = SkySpriteMath.corners(c, right, up, st.width(), height, dd.pivotX(), dd.pivotY());
        float[] world = toWorld(m, corners);
        if (culled(world, viewProj)) return null;
        // Level of detail by the on-screen width, with hysteresis; a level is used only once its page is ready.
        int pageA = dd.locate(pick.a(), views.get(0).cell())[0];
        LodKey key = new LodKey(d.source().key(), imp);
        LodState state = lods.computeIfAbsent(key, k -> new LodState());
        state.lastUsed = frame;
        int levels = dd.levels().size();
        int[] below = new int[levels];
        for (int i = 1; i < levels; i++) below[i] = dd.levels().get(i).below();
        double pixels = screenWidth(world, viewProj, ctx.width(), ctx.height());
        boolean[] ready = new boolean[levels];
        int current = state.lod.level();
        int want = current;
        if (current + 1 < levels && pixels < SkySpriteMath.LOD_DOWN * below[current + 1]) want = current + 1;
        else if (current > 0 && pixels > SkySpriteMath.LOD_UP * below[current]) want = current - 1;
        if (want != current) ready[want] = page(dd, want, pageA).ready();
        state.lod = state.lod.step(pixels, below, ready);
        int level = state.lod.level();
        SkySpriteMath.Level lv = dd.levels().get(level);
        SkyTextures.Sprite tex = page(dd, level, pageA);
        if (!tex.ready()) {
            if (tex.status == SkyTextures.Status.PENDING) pending++;
            else textures.invalid(desc, "page " + lv.textures().get(pageA) + " could not be loaded: " + tex.error);
            return null;
        }
        String mismatch = SkySpriteMath.checkPage(dd, lv, lv.textures().get(pageA), tex.sourceWidth, tex.sourceHeight);
        if (mismatch != null) {
            textures.invalid(desc, mismatch);
            return null;
        }
        // Rectangles: the views of frame a and, when blending into frame b on the same page, those of frame b.
        int cellW = lv.frameW() + 2 * lv.gutter(), cellH = lv.frameH() + 2 * lv.gutter();
        float[][] rects = new float[4][];
        float[] weights = new float[4];
        int n = 0;
        boolean withB = pick.mix() > 0 && dd.locate(pick.b(), views.get(0).cell())[0] == pageA;
        float wa = withB ? 1 - pick.mix() : 1;
        for (int f = 0; f < (withB ? 2 : 1); f++) {
            int frameIndex = f == 0 ? pick.a() : pick.b();
            float fw = f == 0 ? wa : pick.mix();
            for (SkySpriteMath.View v : views) {
                if (n >= 4) break;
                int cell = dd.locate(frameIndex, v.cell())[1];
                float[] rect = SkySpriteMath.cell(cell, dd.columns(), dd.rows(), lv.gutter(), lv.gutter(), cellW, cellH);
                if (v.mirrored()) { float t = rect[0]; rect[0] = rect[2]; rect[2] = t; }
                rects[n] = rect;
                weights[n] = v.weight() * fw;
                n++;
            }
        }
        float total = 0;
        for (float w : weights) total += w;
        if (total > 0) for (int i = 0; i < 4; i++) weights[i] /= total;
        float opacity = d.opacity() * tex.fade() * ((imp.tint() >>> 24) / 255f);
        row(count, imp.blend().ordinal(), opacity, d.source().brightness(), imp.atmosphere(), imp.darkening().ordinal(),
            imp.darkeningStrength(), imp.daylight(), imp.horizon(), imp.tint(), tex.levels, weights, rects);
        emit(builder, world, count);
        return new Prepared(count++, tex.id, imp.layer() == SkyImage.Layer.FRONT);
    }

    private SkyTextures.Sprite page(SkySpriteMath.Descriptor dd, int level, int page) {
        return textures.sprite(dd.levels().get(level).textures().get(page), true, dd.columns(), dd.rows());
    }

    private static double[] motion(double[] c, Object ahead) {
        double[] later = switch (ahead) {
            case SkyImage.State s -> SkyObjectsMath.direction(s.azimuth(), s.elevation());
            case SkyImpostor.State s -> SkyObjectsMath.direction(s.azimuth(), s.elevation());
            case null, default -> null;
        };
        return later == null ? null : SkySpriteMath.motion(c, later);
    }

    private void row(int index, int blend, float opacity, float brightness, float atmosphere, int darkening, float strength,
                     float daylight, float horizon, int tint, int levels, float[] weights, float[][] rects) {
        int k = index * TEXELS * 4;
        float[] r = rows;
        r[k] = blend; r[k + 1] = Math.clamp(opacity, 0f, 1f); r[k + 2] = brightness; r[k + 3] = atmosphere;
        r[k + 4] = darkening; r[k + 5] = strength; r[k + 6] = daylight;
        r[k + 7] = horizon > 0 ? (float) Math.sin(Math.toRadians(horizon)) : 0;
        r[k + 8] = BeamFrame.red(tint); r[k + 9] = BeamFrame.green(tint); r[k + 10] = BeamFrame.blue(tint); r[k + 11] = levels;
        for (int i = 0; i < 4; i++) r[k + 12 + i] = i < weights.length ? weights[i] : 0;
        for (int i = 0; i < 4; i++) {
            float[] rect = i < rects.length && rects[i] != null ? rects[i] : rects[0];
            System.arraycopy(rect, 0, r, k + 16 + i * 4, 4);
        }
    }

    private static void emit(BufferBuilder b, float[] world, int index) {
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int k = 0; k < 4; k++)
            b.addVertex(world[k * 3], world[k * 3 + 1], world[k * 3 + 2]).setUv(uv[k][0], uv[k][1]).setUv2(index, 0);
    }

    /** Corners from the object's frame to the world, times the sky radius. */
    private static float[] toWorld(double[] m, double[] corners) {
        float[] out = new float[12];
        for (int k = 0; k < 4; k++) {
            double[] w = SkyObjectsMath.apply(m, corners[k * 3], corners[k * 3 + 1], corners[k * 3 + 2]);
            out[k * 3] = (float) w[0] * SkyObjectsFrame.RADIUS;
            out[k * 3 + 1] = (float) w[1] * SkyObjectsFrame.RADIUS;
            out[k * 3 + 2] = (float) w[2] * SkyObjectsFrame.RADIUS;
        }
        return out;
    }

    private static float[] clip(float[] world, Matrix4f viewProj) {
        float[] out = new float[16];
        Vector4f v = new Vector4f();
        for (int k = 0; k < 4; k++) {
            v.set(world[k * 3], world[k * 3 + 1], world[k * 3 + 2], 1);
            viewProj.transform(v);
            out[k * 4] = v.x; out[k * 4 + 1] = v.y; out[k * 4 + 2] = v.z; out[k * 4 + 3] = v.w;
        }
        return out;
    }

    private static boolean culled(float[] world, Matrix4f viewProj) {
        return SkySpriteMath.culled(clip(world, viewProj));
    }

    /** On-screen width of the picture (top edge), pixels; very large when an end is behind the camera. */
    private static double screenWidth(float[] world, Matrix4f viewProj, int width, int height) {
        float[] c = clip(world, viewProj);
        if (c[3] <= 1e-4f || c[7] <= 1e-4f) return 1e6;
        double x0 = c[0] / c[3] * width / 2, y0 = c[1] / c[3] * height / 2, x1 = c[4] / c[7] * width / 2, y1 = c[5] / c[7] * height / 2;
        return Math.hypot(x1 - x0, y1 - y0);
    }

    /** The prepared sprite of a drawn object this frame, or null when it is not drawn. */
    Prepared prepared(SkyObjectsFrame.Drawn d) { return prepared.get(d); }

    /** True when a prepared sprite asked for the front layer. */
    boolean anyFront() { return front; }

    /**
     * Draws prepared sprites in order (those of {@code list} that were prepared, minus FRONT ones when {@code skipFront},
     * or only FRONT ones when {@code onlyFront}), one call per run of consecutive quads with the same texture.
     * Returns the draw calls made.
     */
    int draw(List<SkyObjectsFrame.Drawn> list, boolean skipFront, boolean onlyFront, Matrix4f modelView, Matrix4f projection,
             java.util.function.Consumer<ShaderInstance> frameUniforms, boolean toLayer) {
        List<Prepared> todo = new ArrayList<>(list.size());
        for (SkyObjectsFrame.Drawn d : list) {
            Prepared p = prepared.get(d);
            if (p == null || (skipFront && p.front()) || (onlyFront && !p.front())) continue;
            todo.add(p);
        }
        if (todo.isEmpty() || buffer == null) return 0;
        int calls = 0;
        VertexFormat.IndexType type = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS).type();
        buffer.bind();
        int start = 0;
        for (int i = 1; i <= todo.size(); i++) {
            boolean split = i == todo.size() || todo.get(i).texture() != todo.get(start).texture()
                || todo.get(i).quad() != todo.get(i - 1).quad() + 1;
            if (!split) continue;
            Prepared first = todo.get(start);
            int quads = i - start;
            // setDefaultUniforms sets Sampler0..11 from RenderSystem.getShaderTexture (javap): the samplers go after it.
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, Minecraft.getInstance().getWindow());
            shader.setSampler("Sampler0", first.texture());
            shader.setSampler("CaSpriteData", dataId);
            frameUniforms.accept(shader);
            shader.apply();
            RenderSystem.enableBlend();
            if (toLayer) RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            else RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            GL11.glDrawElements(GL11.GL_TRIANGLES, quads * 6, type.asGLType, (long) first.quad() * 6 * type.bytes);
            shader.clear();
            calls++;
            start = i;
        }
        return calls;
    }

    /** Frees LOD states of objects not drawn for a while (called once per frame, cheap). */
    void sweep() {
        if (frame % 300 != 0) return;
        lods.values().removeIf(s -> frame - s.lastUsed >= KEEP_FRAMES);
        if (detected.size() > 256) { detected.clear(); detectedFor.clear(); }
    }

    /** Releases the buffers (resource reload, leaving the world); the textures are released by {@link SkyTextures}. */
    void clear() {
        if (buffer != null) buffer.close();
        buffer = null;
        if (bytes != null) bytes.close();
        bytes = null;
        prepared.clear();
        lods.clear();
        detected.clear();
        detectedFor.clear();
        count = images = impostors = pending = frozen = 0;
        front = false;
    }

    /** Images drawn in the last prepared frame. */
    int images() { return images; }
    /** Impostors drawn in the last prepared frame. */
    int impostors() { return impostors; }
    /** Sprites whose texture or descriptor was still loading. */
    int pending() { return pending; }
    /** Flipbooks frozen for flashes in the last prepared frame. */
    int frozen() { return frozen; }
    /** Level of detail of the impostors drawn, by object (tests): the highest level in use, or −1. */
    int lodLevel() {
        int max = -1;
        for (LodState s : lods.values()) if (s.lastUsed == frame) max = Math.max(max, s.lod.level());
        return max;
    }

    private static double[] reject(double[] v, double[] axis) {
        double d = v[0] * axis[0] + v[1] * axis[1] + v[2] * axis[2];
        return new double[] {v[0] - axis[0] * d, v[1] - axis[1] * d, v[2] - axis[2] * d};
    }

    private static double length(double[] v) { return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
}
