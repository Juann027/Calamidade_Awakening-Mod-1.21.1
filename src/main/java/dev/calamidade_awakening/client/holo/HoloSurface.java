package dev.calamidade_awakening.client.holo;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Surface of one Atlas panel (0.11.2): a transparent {@link TextureTarget} reused between frames (reallocated only when
 * its pixel size changes) and freed in {@link #close()}. Content is drawn with {@link GuiGraphics} in the panel's
 * logical units at {@code density} pixels per unit; blending accumulates alpha, so the texture holds premultiplied
 * colour and is composed with {@code ONE, ONE_MINUS_SRC_ALPHA}. Mipmapped so text stays legible a few blocks away.
 * Adapted from the RespectCraft holographic menu ({@code HoloSurface}, stages 4.8.1 and 4.8.4-A, same author).
 */
final class HoloSurface {
    private TextureTarget target;
    private int pxW, pxH;
    private float density = 1F;
    private GuiGraphics graphics;
    private final Matrix4f savedProjection = new Matrix4f();
    private VertexSorting savedSorting;
    private final Matrix4f ortho = new Matrix4f();
    private boolean drawing;

    /** Makes sure the texture holds {@code logicalW x logicalH} units at {@code density} pixels per unit. */
    void ensure(float logicalW, float logicalH, float density) {
        this.density = density;
        int w = Math.max(1, Mth.ceil(logicalW * density));
        int h = Math.max(1, Mth.ceil(logicalH * density));
        if (target == null) {
            target = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            target.setClearColor(0F, 0F, 0F, 0F);
        } else if (w != pxW || h != pxH) {
            target.resize(w, h, Minecraft.ON_OSX);
        }
        target.setFilterMode(GL11.GL_LINEAR);
        pxW = w;
        pxH = h;
    }

    boolean ready() { return target != null; }
    int textureId() { return target.getColorTextureId(); }

    /** U of the right edge of content {@code logicalW} wide. */
    float u(float logicalW) { return Math.min(1F, logicalW * density / pxW); }
    /** V of the bottom edge (framebuffer textures start at the bottom). */
    float vBottom(float logicalH) { return Math.max(0F, 1F - logicalH * density / pxH); }

    /** Clears and starts drawing on the surface; always paired with {@link #end()}. */
    GuiGraphics begin() {
        Minecraft mc = Minecraft.getInstance();
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        savedProjection.set(RenderSystem.getProjectionMatrix());
        savedSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(ortho.setOrtho(0F, pxW, pxH, 0F, 1000F,
            net.neoforged.neoforge.client.ClientHooks.getGuiFarPlane()), VertexSorting.ORTHOGRAPHIC_Z);
        if (graphics == null) graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        graphics.pose().pushPose();
        accumulate();
        drawing = true;
        return graphics;
    }

    /** Back to the main framebuffer; restores projection, scissor and blending, then builds the mipmaps. */
    void end() {
        if (!drawing) return;
        drawing = false;
        graphics.flush();
        RenderSystem.disableScissor();
        graphics.pose().popPose();
        graphics.setColor(1F, 1F, 1F, 1F);
        RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
        RenderSystem.defaultBlendFunc();
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        int id = target.getColorTextureId();
        GlStateManager._bindTexture(id);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GlStateManager._bindTexture(0);
    }

    /** Scissor in the panel's logical units (GuiGraphics scissors use the window, not this target). */
    void scissor(float x, float y, float w, float h) {
        graphics.flush();
        // Scissor in pixels of this target.
        int left = Mth.clamp(Mth.floor(x * density), 0, pxW), right = Mth.clamp(Mth.ceil((x + w) * density), 0, pxW);
        int top = Mth.clamp(Mth.floor(y * density), 0, pxH), bottom = Mth.clamp(Mth.ceil((y + h) * density), 0, pxH);
        RenderSystem.enableScissor(left, pxH - bottom, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    void unscissor() {
        graphics.flush();
        RenderSystem.disableScissor();
    }

    /** Blending that accumulates alpha correctly on a transparent target. */
    static void accumulate() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    void close() {
        if (drawing) end();
        if (target != null) target.destroyBuffers();
        target = null;
        pxW = pxH = 0;
    }
}
