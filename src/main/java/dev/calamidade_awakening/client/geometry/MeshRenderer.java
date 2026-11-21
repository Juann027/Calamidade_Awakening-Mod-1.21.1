package dev.calamidade_awakening.client.geometry;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.client.StageScene;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import java.util.List;
import dev.calamidade_awakening.api.v1.client.AddonLayers;

/** One batched world draw; the stage backend later composites the scene. */
public final class MeshRenderer {
    private ShaderInstance energy;
    public void shader(ShaderInstance shader) { energy = shader; }
    private int lastVertices;
    /** Vertices emitted by the last render call; 0 when nothing was drawn. */
    public int lastVertices() { return lastVertices; }

    public void render(RenderLevelStageEvent event, StageScene.Plan plan, float partial) {
        List<StageScene.Sample> effects = plan.effects().stream()
            .filter(s -> s.kind().geometry != VisualKind.Geometry.NONE || AddonLayers.world(s.kind().networkKey()) != null).toList();
        lastVertices = 0;
        if (effects.isEmpty()) return;
        Camera camera = event.getCamera();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        Vec3 right = new Vec3(-left.x(), -left.y(), -left.z());
        Vec3 vertical = new Vec3(up.x(), up.y(), up.z());
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int slice = plan.quality().vertices / effects.size();
        int emitted = 0;
        for (StageScene.Sample sample : effects) {
            Mesh mesh = new Mesh(builder, event.getPoseStack().last().pose(), camera.getPosition(), slice);
            GeometryEffects.render(new Frame(mesh, camera.getPosition(), right, vertical, partial), sample, plan.quality());
            var layer = AddonLayers.world(sample.kind().networkKey());
            if (layer != null) layer.accept(new AddonLayers.World(mesh,camera.getPosition().add(sample.relative()),
                sample.time(), sample.basis().yaw, sample.basis().pitch));
            emitted += slice - mesh.remaining();
        }
        lastVertices = emitted;
        MeshData data = builder.build();
        if (data == null) return;
        ShaderInstance previous = RenderSystem.getShader();
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        try {
            RenderSystem.setShader(() -> energy != null ? energy : GameRenderer.getPositionColorShader());
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            BufferUploader.drawWithShader(data);
        } finally {
            RenderSystem.setShader(() -> previous);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            RenderSystem.depthMask(depthMask);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }
}
