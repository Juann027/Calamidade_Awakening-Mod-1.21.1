package dev.calamidade_awakening.teste;

import cyou.rimuru.rimulib.mc1211.client.api.FxApi;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import java.nio.FloatBuffer;

/**
 * Sondas do quadro para o testeBackdrop (0.14): a ordem de {@code AFTER_SKY}, da camada da RimuLib e de
 * {@code AFTER_LEVEL}, o framebuffer ligado em {@code AFTER_SKY} e a profundidade no centro da tela antes e depois do
 * fundo do motor (que é desenhado num ouvinte de prioridade alta do mesmo estágio; este roda na prioridade baixa, depois
 * dele). Na fase 1, um protótipo de fundo morava aqui; na fase 2 o fundo é o do motor (Backdrop) e ficam só as sondas.
 */
final class SondasDoQuadro {
    static volatile long seqCeu, seqNivel, seqRimu;
    private static long seq;
    static volatile float profundidadeDepois = Float.NaN, profundidadeNivel = Float.NaN;
    static volatile int fboCeu, fboPrincipal;

    static void registrar() {
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, SondasDoQuadro::estagio);
        FxApi.stage("calamidade_teste").add(frame -> seqRimu = ++seq);
    }

    private static void estagio(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            seqCeu = ++seq;
            fboCeu = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            fboPrincipal = Minecraft.getInstance().getMainRenderTarget().frameBufferId;
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
}
