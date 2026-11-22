package dev.calamidade_awakening.client.holo;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * The Atlas pass inside the world (0.11.2, {@code RenderLevelStageEvent} AFTER_LEVEL): the real camera projection, an
 * identity model-view (vertices already carry view * layout * model, relative to the camera), depth test against the
 * scenery without writing and without clearing the depth buffer (a wall in front hides a panel), no fog, no culling
 * (panels seen from behind). {@link #end} gives back exactly the state the rest of the frame expects: projection and
 * vertex sorting, model-view, depth mask and function, cull, blending, fog start and shader colour. Uses the game's
 * own {@code position_tex_color} and {@code position_color} shaders. Adapted from the RespectCraft holographic menu
 * ({@code Holo3D.beginWorld/endWorld}, stage 4.8.4-A, same author).
 */
final class HoloGl {
    private final Matrix4f savedProjection = new Matrix4f();
    private VertexSorting savedSorting;
    private float savedFogStart;
    private boolean active;
    private BufferBuilder batch;

    void begin(Matrix4f projection) {
        savedProjection.set(RenderSystem.getProjectionMatrix());
        savedSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        RenderSystem.applyModelViewMatrix();
        depthTestNoWrite();
        savedFogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        active = true;
    }

    void end() {
        if (!active) return;
        active = false;
        for (BufferBuilder open : new BufferBuilder[] {batch, stars}) {
            if (open == null) continue;
            MeshData mesh = open.build();
            if (mesh != null) mesh.close();
        }
        batch = stars = null;
        RenderSystem.setShaderFogStart(savedFogStart);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
    }

    /** Panel quad with its surface texture (premultiplied colour). {@code mv} = view * layout * model. */
    void texturedPanel(Matrix4f mv, float w, float h, int texture, float u1, float v0, float opacity) {
        if (opacity <= .003F) return;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        depthTestNoWrite();
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float o = Math.min(1F, opacity);
        buf.addVertex(mv, 0F, 0F, 0F).setUv(0F, 1F).setColor(o, o, o, o);
        buf.addVertex(mv, 0F, h, 0F).setUv(0F, v0).setColor(o, o, o, o);
        buf.addVertex(mv, w, h, 0F).setUv(u1, v0).setColor(o, o, o, o);
        buf.addVertex(mv, w, 0F, 0F).setUv(u1, 1F).setColor(o, o, o, o);
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.defaultBlendFunc();
    }

    private static void depthTestNoWrite() {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
    }

    // ------------------------------------------------------------------ additive light (stars, lines, glow)

    void beginLight() { batch = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR); }

    void endLight() {
        if (batch == null) return;
        MeshData mesh = batch.build();
        batch = null;
        if (mesh == null) return;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        depthTestNoWrite();
        BufferUploader.drawWithShader(mesh);
        RenderSystem.defaultBlendFunc();
    }

    /** Constellation lines: additive colour. */
    /** Segment between two eye-space points with a width in eye units and colours at the ends. */
    void line(Vector3f p1, Vector3f p2, float width, int c1, int c2) {
        if (batch == null) return;
        float dx = p2.x - p1.x, dy = p2.y - p1.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-5F) return;
        float nx = -dy / len * width / 2F, ny = dx / len * width / 2F;
        vertex(p1.x + nx, p1.y + ny, p1.z, c1);
        vertex(p1.x - nx, p1.y - ny, p1.z, c1);
        vertex(p2.x - nx, p2.y - ny, p2.z, c2);
        vertex(p2.x + nx, p2.y + ny, p2.z, c2);
    }

    /** Stars and dust: billboards with a radial glow texture made once at runtime (no asset). */
    void beginStars() { stars = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR); }

    void endStars() {
        if (stars == null) return;
        MeshData mesh = stars.build();
        stars = null;
        if (mesh == null) return;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, glow());
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        depthTestNoWrite();
        BufferUploader.drawWithShader(mesh);
        RenderSystem.defaultBlendFunc();
    }

    /** A star facing the camera at an eye-space point: soft halo, bright core and two diffraction streaks. */
    void star(Vector3f p, float size, int color, float alpha) {
        if (stars == null || alpha <= .003F) return;
        billboard(p, size * 3F, size * 3F, withAlpha(color, alpha * .55F));
        billboard(p, size * .9F, size * .9F, withAlpha(0xFFFFFF, alpha));
        billboard(p, size * 5F, size * .3F, withAlpha(color, alpha * .7F));
        billboard(p, size * .3F, size * 5F, withAlpha(color, alpha * .7F));
    }

    private void billboard(Vector3f p, float rx, float ry, int argb) {
        stars.addVertex(p.x - rx, p.y + ry, p.z).setUv(0F, 0F).setColor(argb);
        stars.addVertex(p.x - rx, p.y - ry, p.z).setUv(0F, 1F).setColor(argb);
        stars.addVertex(p.x + rx, p.y - ry, p.z).setUv(1F, 1F).setColor(argb);
        stars.addVertex(p.x + rx, p.y + ry, p.z).setUv(1F, 0F).setColor(argb);
    }

    private BufferBuilder stars;
    private static net.minecraft.client.renderer.texture.DynamicTexture glow;

    /** 32 × 32 white radial falloff (alpha = (1 - r)^2), linear filtering. */
    private static int glow() {
        if (glow == null) {
            var image = new com.mojang.blaze3d.platform.NativeImage(32, 32, false);
            for (int y = 0; y < 32; y++)
                for (int x = 0; x < 32; x++) {
                    float dx = (x + .5F) / 16F - 1F, dy = (y + .5F) / 16F - 1F;
                    float r = Math.min(1F, (float) Math.sqrt(dx * dx + dy * dy));
                    int a = Math.round((1F - r) * (1F - r) * 255F);
                    image.setPixelRGBA(x, y, (a << 24) | 0xFFFFFF);
                }
            glow = new net.minecraft.client.renderer.texture.DynamicTexture(image);
            glow.setFilter(true, false);
        }
        return glow.getId();
    }

    private void vertex(float x, float y, float z, int argb) {
        batch.addVertex(x, y, z).setColor(argb);
    }

    static int withAlpha(int rgb, float alpha) {
        return (Math.clamp(Math.round(alpha * 255F), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }
}
