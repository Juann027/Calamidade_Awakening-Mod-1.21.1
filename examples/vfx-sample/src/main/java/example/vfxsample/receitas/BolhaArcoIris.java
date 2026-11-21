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
 * Receita 3 — bolha arco-íris (docs/ai/receitas/03-bolha-arco-iris.md).
 * Uma esfera por raio-esfera com película iridescente (cores pelo ângulo de visão), brilho especular e a face de
 * trás mais fraca. Cresce com overshoot, sobe devagar e estoura.
 * Teste: /vfxsample receita bolha
 */
public final class BolhaArcoIris {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_bolha");

    /** Subida da bolha, em blocos por tick. */
    static final double SUBIDA = .015;

    static final Timeline TIMELINE = Timeline.builder(110)
        .phase("crescer", 0, 30, 20, 0)
        .phase("estouro", 100, 110, 0, 10)
        .track("raio", keyframe(0, 0f), keyframe(30, 4f, Easing.OVERSHOOT), keyframe(100, 4.2f), keyframe(108, 5.2f, Easing.EASE_OUT))
        .track("brilho", keyframe(0, 0f), keyframe(20, 1f), keyframe(100, 1f), keyframe(110, 0f, Easing.EASE_IN))
        .marker("estouro", 100)
        .build();

    private BolhaArcoIris() {}

    public static void register() {
        Effects.register(ID, new Effects.Definition(110, 6, 0xffb0e0ff, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(ID, TIMELINE);
        Effects.cue(ID, "estouro", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.PLAYERS, 2f, .6f));
    }

    public static final class Client {
        private Client() {}

        public static void setup() {
            AddonLayers.post(ID, post -> {
                var t = post.timeline();
                float raio = t.value("raio"), brilho = t.value("brilho");
                if (raio <= .01f || brilho <= .001f) return;
                // A bolha sobe: o centro do passe sai do centro da instância.
                var centro = post.center().add(0, SUBIDA * post.age(), 0);
                post.pass("vfxsample:receita_bolha", u -> {
                    u.v3("BolhaCentro", centro);
                    u.f4("BolhaEstado", raio, brilho, post.time().opacity(), (float) (post.realAge() / 20.0));
                });
            });
        }
    }
}
