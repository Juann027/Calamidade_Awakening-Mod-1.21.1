package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.client.AddonLayers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Receita 4 — impact frame de duas cores (docs/ai/receitas/04-impact-frame.md).
 * No golpe, a tela vira duas cores por limiar de luminância (claro/escuro) por 6 ticks e inverte na metade.
 * Acessível: a força segue a intensidade dos efeitos de tela e, com "ocultar clarões", fica em 35% e sem inversão.
 * Teste: /vfxsample receita impacto
 */
public final class ImpactFrame {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_quadro");

    /** Cor das áreas claras e das escuras (ARGB). */
    static final int CLARO = 0xfffff2e0, ESCURO = 0xff16060c;

    static final Timeline TIMELINE = Timeline.builder(40)
        .phase("carga", 0, 20, 14, 0)
        .phase("quadro", 20, 26, 0, 2)
        .marker("golpe", 20)
        .build();

    private ImpactFrame() {}

    public static void register() {
        Effects.register(ID, new Effects.Definition(40, 4, 0xffff3040, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(ID, TIMELINE);
        Effects.cue(ID, "golpe", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2f, .5f));
    }

    public static final class Client {
        private Client() {}

        public static void setup() {
            AddonLayers.post(ID, post -> {
                var t = post.timeline();
                // Carga: vinheta vermelha crescendo até o golpe.
                post.vignette(0xff400008, t.weight("carga") * .45f);
                float quadro = t.weight("quadro");
                if (quadro <= 0) return;
                boolean calmo = !post.flashAllowed();
                float forca = quadro * post.screenEffects() * (calmo ? .35f : 1f);
                boolean inverter = !calmo && t.since("golpe") >= 3;
                if (forca > .001f) post.pass("vfxsample:receita_quadro", u -> {
                    u.f3("QuadroClaro", r(CLARO), g(CLARO), b(CLARO));
                    u.f3("QuadroEscuro", r(ESCURO), g(ESCURO), b(ESCURO));
                    u.f4("QuadroEstado", forca, inverter ? 1 : 0, .35f, 0);
                });
                post.shake(quadro * .3f);
            });
        }

        private static float r(int argb) { return ((argb >> 16) & 255) / 255f; }
        private static float g(int argb) { return ((argb >> 8) & 255) / 255f; }
        private static float b(int argb) { return (argb & 255) / 255f; }
    }
}
