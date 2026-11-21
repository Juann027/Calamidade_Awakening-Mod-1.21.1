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
 * Receita 5 — feixe vertical (docs/ai/receitas/05-feixe-vertical.md).
 * Um feixe de luz de 64 blocos desenhado no shader com caSegmentGlow: núcleo e halo, largura mínima de um pixel
 * (visível de longe sem serrilhar) e oclusão pelo terreno.
 * Teste: /vfxsample receita feixe
 */
public final class FeixeVertical {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_feixe");

    static final Timeline TIMELINE = Timeline.builder(100)
        .phase("acender", 0, 20, 12, 0)
        .phase("apagar", 70, 100, 0, 30)
        .track("altura", keyframe(0, 0f), keyframe(20, 64f, Easing.EASE_OUT))
        .track("largura", keyframe(0, .2f), keyframe(18, .6f, Easing.OVERSHOOT), keyframe(70, .45f), keyframe(100, 0f, Easing.EASE_IN))
        .build();

    private FeixeVertical() {}

    public static void register() {
        // Raio 32: o teste de frustum (2,5 x raio em volta do centro) alcança o topo do feixe.
        Effects.register(ID, new Effects.Definition(100, 32, 0xffffe0a0, Effects.Shape.NONE, Effects.Material.NONE,
            null, null).withRange(384));
        Effects.timeline(ID, TIMELINE);
        Effects.cue(ID, 0, ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 2f, 1.2f));
    }

    public static final class Client {
        private Client() {}

        public static void setup() {
            AddonLayers.post(ID, post -> {
                var t = post.timeline();
                float altura = t.value("altura"), largura = t.value("largura");
                if (altura <= .01f || largura <= .001f) return;
                post.pass("vfxsample:receita_feixe", u -> {
                    u.v3("FeixeBase", post.center());
                    u.f4("FeixeEstado", altura, largura * .5f, post.time().opacity(), (float) (post.realAge() / 20.0));
                });
                // Brilho só perto do feixe.
                post.bloom(largura * (float) Math.clamp(1 - post.distance() / 48, 0, 1) * .3f);
            });
        }
    }
}
