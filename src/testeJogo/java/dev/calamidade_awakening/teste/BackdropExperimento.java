package dev.calamidade_awakening.teste;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import cyou.rimuru.rimulib.mc1211.client.api.FxApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import java.nio.FloatBuffer;

/**
 * Experimento da fase 1 da 0.14 (só no mod de teste, nunca no JAR do motor): um protótipo de Backdrop desenhado como
 * cubo em volta da câmera em {@code RenderLevelStageEvent.AFTER_SKY}, para comprovar em jogo, antes de consolidar a
 * arquitetura, a ordem real em relação aos passes da RimuLib, a profundidade, a cobertura do céu do jogo, a
 * transparência e a composição com Atmosphere, lente, eclipse, sol, lua, estrelas e nuvens.
 *
 * <p>Textura 3 × 2 com uma cor por face (a mesma montagem que o Backdrop usaria, porque {@code samplerCube} não passa
 * pelo {@code glTexture} da RimuLib) e, opcionalmente, uma grade escura para ver a lente entortar o fundo. Desenhado
 * com {@code depthMask(false)}, como o céu do jogo, e sem teste de profundidade.
 */
final class BackdropExperimento {
    /** Cores das faces (RGB): +X, -X, +Y (topo), -Y, +Z, -Z. Distantes entre si e de qualquer céu do jogo. */
    static final int[] FACES = {0xC83232, 0x32B432, 0x3C50E6, 0xDCC828, 0xC832C8, 0x28C8C8};
    static final int GRADE = 0x101010;
    private static final int LADO = 64;

    /** Opacidade do fundo (0 = desligado). */
    static volatile float alfa;
    static volatile boolean grade;

    /** Ordem do último quadro: números de sequência de AFTER_SKY, AFTER_LEVEL e da camada da RimuLib. */
    static volatile long seqCeu, seqNivel, seqRimu;
    private static long seq;
    /** Profundidade lida no centro da tela em AFTER_SKY (antes e depois do desenho) e em AFTER_LEVEL. */
    static volatile float profundidadeAntes = Float.NaN, profundidadeDepois = Float.NaN, profundidadeNivel = Float.NaN;
    /** Framebuffer ligado em AFTER_SKY e o do alvo principal. */
    static volatile int fboCeu, fboPrincipal;
    static volatile int desenhos;

    private static VertexBuffer cubo;
    private static DynamicTexture textura;
    private static boolean texturaComGrade;

    static void registrar() {
        NeoForge.EVENT_BUS.addListener(BackdropExperimento::estagio);
        FxApi.stage("calamidade_teste").add(frame -> seqRimu = ++seq);
    }

    private static void estagio(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            seqCeu = ++seq;
            fboCeu = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            fboPrincipal = Minecraft.getInstance().getMainRenderTarget().frameBufferId;
            profundidadeAntes = profundidadeNoCentro();
            if (alfa > 0) desenhar(event);
            profundidadeDepois = profundidadeNoCentro();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            seqNivel = ++seq;
            profundidadeNivel = profundidadeNoCentro();
        }
    }

    private static float profundidadeNoCentro() {
        var target = Minecraft.getInstance().getMainRenderTarget();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer valor = stack.mallocFloat(1);
            GL11.glReadPixels(target.width / 2, target.height / 2, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, valor);
            return valor.get(0);
        }
    }

    private static void desenhar(RenderLevelStageEvent event) {
        preparar();
        var shader = GameRenderer.getPositionTexColorShader();
        if (shader == null) return;
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, textura.getId());
        RenderSystem.setShaderColor(1, 1, 1, alfa);
        try {
            cubo.bind();
            // Só a rotação da câmera (o fundo fica no infinito: nenhuma paralaxe quando o jogador anda).
            cubo.drawWithShader(new Matrix4f(event.getModelViewMatrix()), event.getProjectionMatrix(), shader);
            VertexBuffer.unbind();
            desenhos++;
        } finally {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        }
    }

    private static void preparar() {
        if (textura == null || texturaComGrade != grade) {
            if (textura != null) textura.close();
            NativeImage imagem = new NativeImage(LADO * 3, LADO * 2, false);
            for (int f = 0; f < 6; f++) {
                int ox = (f % 3) * LADO, oy = (f / 3) * LADO;
                for (int y = 0; y < LADO; y++) for (int x = 0; x < LADO; x++) {
                    boolean linha = grade && (x % 8 == 0 || y % 8 == 0);
                    imagem.setPixelRGBA(ox + x, oy + y, abgr(linha ? GRADE : FACES[f]));
                }
            }
            textura = new DynamicTexture(imagem);
            texturaComGrade = grade;
        }
        if (cubo != null) return;
        float r = 50;
        var b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // Cantos de cada face vistos de dentro: (eixo, sinal) → 4 vértices; uv no retalho da face.
        float[][][] faces = {
            {{r, -r, -r}, {r, -r, r}, {r, r, r}, {r, r, -r}},       // +X
            {{-r, -r, r}, {-r, -r, -r}, {-r, r, -r}, {-r, r, r}},   // -X
            {{-r, r, -r}, {r, r, -r}, {r, r, r}, {-r, r, r}},       // +Y
            {{-r, -r, r}, {r, -r, r}, {r, -r, -r}, {-r, -r, -r}},   // -Y
            {{r, -r, r}, {-r, -r, r}, {-r, r, r}, {r, r, r}},       // +Z
            {{-r, -r, -r}, {r, -r, -r}, {r, r, -r}, {-r, r, -r}},   // -Z
        };
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int f = 0; f < 6; f++) {
            float u0 = (f % 3) / 3f, v0 = (f / 3) / 2f;
            for (int k = 0; k < 4; k++) {
                float[] p = faces[f][k];
                b.addVertex(p[0], p[1], p[2]).setUv(u0 + uv[k][0] / 3f, v0 + uv[k][1] / 2f).setColor(255, 255, 255, 255);
            }
        }
        cubo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        cubo.bind();
        cubo.upload(b.buildOrThrow());
        VertexBuffer.unbind();
    }

    /** RGB → ABGR opaco (formato do NativeImage). */
    static int abgr(int rgb) {
        return 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF);
    }
}
