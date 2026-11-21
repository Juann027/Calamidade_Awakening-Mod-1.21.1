package dev.calamidade_awakening.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.calamidade_awakening.api.v1.TerrainEdit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Structure stand-ins (0.11.0): the blocks of the edit's structure template drawn as one moving mesh while they fall
 * and while they fade over the real blocks after the commit. The mesh is tessellated once per template (block models,
 * faces hidden between solid neighbours culled, directional shade, full brightness because it is on fire), uploaded to
 * a {@link VertexBuffer} and cached; each frame only a translation and an alpha change. At most 1/2/4 meshes per frame
 * (LOW/BALANCED/HIGH). Drawn after the translucent blocks; the build time and the vertex count show in F7.
 */
final class TerrainMeshes {
    private static final int[] PER_FRAME = {1, 2, 4};
    private static final long IDLE_MS = 30_000;

    private record Cached(VertexBuffer buffer, int vertices, long[] used) {}

    private final TerrainClient state;
    private final Map<Long, Cached> cache = new HashMap<>();
    private double lastBuildMs;

    TerrainMeshes(TerrainClient state) { this.state = state; }

    /** Draws the structure stand-ins of the plan this frame. Render thread, at AFTER_TRANSLUCENT_BLOCKS. */
    void render(RenderLevelStageEvent event, StageScene.Plan plan, float partial) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 camera = event.getCamera().getPosition();
        double now = mc.level.getGameTime() + partial;
        int limit = PER_FRAME[plan.quality().ordinal()];
        int drawn = 0, vertices = 0;
        long wall = System.currentTimeMillis();
        for (StageScene.Sample sample : plan.effects()) {
            var edits = sample.kind().terrain();
            for (int i = 0; i < edits.size() && drawn < limit; i++) {
                TerrainEdit edit = edits.get(i);
                if (edit.standIn() == null || edit.standIn().kind() != TerrainEdit.StandIn.Kind.STRUCTURE) continue;
                TerrainClient.Entry entry = state.get(sample.event().order(), i);
                if (entry == null || entry.structure == null) continue;
                Vec3 center = camera.add(sample.relative());
                TerrainClient.Commit commit = state.commit(sample.event(), sample.kind(), edit, i, now, center, sample.basis());
                Vec3 origin = commit != null ? commit.origin() : center;
                var basis = commit != null ? commit.basis() : sample.basis();
                TerrainClient.Pose pose = TerrainClient.standIn(edit, sample.kind().timeline(), sample.localAge(), origin,
                    basis, entry.structure);
                if (pose == null) continue;
                Cached mesh = mesh(entry.structure);
                if (mesh == null) continue;
                mesh.used()[0] = wall;
                draw(event, mesh, pose.position().subtract(camera), pose.opacity(), pose.progress() >= 1);
                drawn++;
                vertices += mesh.vertices();
            }
        }
        PerfOverlay.meshes(drawn, vertices, lastBuildMs);
        // Meshes idle for a while are released.
        for (Iterator<Cached> it = cache.values().iterator(); it.hasNext(); ) {
            Cached c = it.next();
            if (wall - c.used()[0] > IDLE_MS) {
                c.buffer().close();
                it.remove();
            }
        }
    }

    /** Releases every cached mesh (level change). */
    void clear() {
        for (Cached c : cache.values()) c.buffer().close();
        cache.clear();
    }

    private Cached mesh(TerrainClient.Structure structure) {
        Cached cached = cache.get(structure.key());
        if (cached != null) return cached;
        long t0 = System.nanoTime();
        Map<Integer, BlockState> blocks = new HashMap<>();
        for (int i = 0; i < structure.positions().length; i++) {
            int v = structure.values()[i];
            if (v >= 0 && v < structure.palette().length) blocks.put(structure.positions()[i], structure.palette()[v]);
        }
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.create(42);
        PoseStack pose = new PoseStack();
        int vertices = 0;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(Math.max(4096, blocks.size() * 6 * 4 * 32))) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            for (var e : blocks.entrySet()) {
                BlockState block = e.getValue();
                if (block.isAir() || block.getRenderShape() != RenderShape.MODEL) continue;
                int packed = e.getKey();
                int x = packed & 63, y = (packed >> 6) & 63, z = (packed >> 12) & 63;
                var model = dispatcher.getBlockModel(block);
                pose.pushPose();
                pose.translate(x, y, z);
                for (Direction direction : Direction.values()) {
                    int nx = x + direction.getStepX(), ny = y + direction.getStepY(), nz = z + direction.getStepZ();
                    BlockState neighbour = nx < 0 || ny < 0 || nz < 0 || nx > 63 || ny > 63 || nz > 63 ? null
                        : blocks.get(nx | (ny << 6) | (nz << 12));
                    if (neighbour != null && neighbour.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) continue;
                    random.setSeed(42);
                    for (BakedQuad quad : model.getQuads(block, direction, random)) vertices += quad(builder, pose, quad);
                }
                random.setSeed(42);
                for (BakedQuad quad : model.getQuads(block, null, random)) vertices += quad(builder, pose, quad);
                pose.popPose();
            }
            MeshData data = builder.build();
            if (data == null) return null;
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(data);
            VertexBuffer.unbind();
            cached = new Cached(buffer, vertices, new long[] {System.currentTimeMillis()});
        }
        cache.put(structure.key(), cached);
        lastBuildMs = (System.nanoTime() - t0) / 1e6;
        return cached;
    }

    /** One quad with the game's directional shade, a foliage tint for tinted quads and full brightness. */
    private static int quad(BufferBuilder builder, PoseStack pose, BakedQuad quad) {
        float shade = !quad.isShade() ? 1 : switch (quad.getDirection()) {
            case DOWN -> .5f;
            case NORTH, SOUTH -> .8f;
            case WEST, EAST -> .6f;
            default -> 1f;
        };
        float r = shade, g = shade, b = shade;
        if (quad.isTinted()) { r *= .45f; g *= .72f; b *= .3f; }
        builder.putBulkData(pose.last(), quad, r, g, b, 1, LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        return 4;
    }

    private static void draw(RenderLevelStageEvent event, Cached mesh, Vec3 offset, float alpha, boolean landed) {
        RenderType type = RenderType.translucent();
        ShaderInstance shader = GameRenderer.getRendertypeTranslucentShader();
        if (shader == null) return;
        Matrix4f view = new Matrix4f(event.getModelViewMatrix()).translate((float) offset.x, (float) offset.y, (float) offset.z);
        type.setupRenderState();
        try {
            // Over the real blocks after the landing: win the depth test against the identical faces.
            if (landed) {
                RenderSystem.enablePolygonOffset();
                RenderSystem.polygonOffset(-1f, -10f);
            }
            RenderSystem.setShaderColor(1f, .93f, .86f, alpha);
            if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(0f, 0f, 0f);
            mesh.buffer().bind();
            mesh.buffer().drawWithShader(view, event.getProjectionMatrix(), shader);
            VertexBuffer.unbind();
        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            if (landed) {
                RenderSystem.polygonOffset(0f, 0f);
                RenderSystem.disablePolygonOffset();
            }
            type.clearRenderState();
        }
    }
}
