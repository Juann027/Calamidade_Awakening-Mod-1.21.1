package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import dev.calamidade_awakening.api.v1.client.AddonLayers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 2 — cubo de grade gigante (docs/ai/receitas/02-cubo-de-grade.md).
 * Um cubo de 16 blocos desenhado por raio-caixa no shader: grade nas faces da frente e do fundo, arestas mais
 * fortes e uma linha de varredura. A grade fica presa ao cubo (coordenadas relativas ao centro), sem deslizar.
 * Teste: /vfxsample receita cubo
 */
public final class CuboGrade {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_cubo");

    /** Meia-aresta final, em blocos. */
    static final float MEIA_ARESTA = 8f;

    /** 200 ticks: monta em 40 (com overshoot no tamanho), fica, desmonta nos últimos 50. */
    static final Timeline TIMELINE = Timeline.builder(200)
        .phase("montagem", 0, 40, 30, 0)
        .phase("desmonte", 150, 200, 0, 50)
        .track("grade", keyframe(0, 0f), keyframe(40, 1f, Easing.EASE_OUT), keyframe(150, 1f), keyframe(200, 0f, Easing.SMOOTH))
        .track("tamanho", keyframe(0, .2f), keyframe(40, 1f, Easing.OVERSHOOT))
        .marker("pronto", 40)
        .build();

    private CuboGrade() {}

    public static void register() {
        // Raio 12: o teste de frustum usa 2,5 x raio, o que cobre a diagonal do cubo (8 x raiz de 3).
        Effects.register(ID, new Effects.Definition(200, 12, 0xff40f0ff, Effects.Shape.NONE, Effects.Material.NONE,
            null, null).withRange(256));
        Effects.timeline(ID, TIMELINE);
        Effects.cue(ID, "pronto", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.5f, .8f));
    }

    public static final class Client {
        private Client() {}

        public static void setup() {
            AddonLayers.post(ID, post -> {
                var t = post.timeline();
                float grade = t.value("grade");
                if (grade <= .001f) return;
                post.pass("vfxsample:receita_cubo", u -> {
                    u.v3("CuboCentro", post.center());
                    u.f4("CuboEstado", MEIA_ARESTA * t.value("tamanho"), grade, post.time().opacity(), (float) post.realAge());
                });
            });
        }
    }
}
