package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.client.AddonLayers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Receita 1 — anel de impacto no chão (docs/ai/receitas/01-anel-de-impacto.md).
 * Um anel dourado corre pelo terreno a partir do centro, lido da profundidade da cena no shader.
 * Teste: /vfxsample receita anel
 */
public final class AnelImpacto {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_anel");

    /** 60 ticks: carga curta, impacto no tick 12, onda até o fim. */
    static final Timeline TIMELINE = Timeline.builder(60)
        .phase("carga", 0, 12, 4, 2)
        .phase("onda", 12, 60, 0, 20)
        .marker("impacto", 12)
        .build();

    /** Velocidade do anel, em blocos por tick (9 blocos por segundo). */
    static final float VELOCIDADE = .45f;

    private AnelImpacto() {}

    /** Construtor comum do mod: register, timeline, cues (nessa ordem). */
    public static void register() {
        // Sem forma nem material embutidos: tudo vem do passe. O Impact dá o clarão do motor no tick 12.
        Effects.register(ID, new Effects.Definition(60, 12, 0xffffc060, Effects.Shape.NONE, Effects.Material.NONE,
            null, null, new Effects.Impact(12, 3, .45f, Effects.ImpactStyle.TINTED_FLASH)));
        Effects.timeline(ID, TIMELINE);
        Effects.cue(ID, "impacto", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, .6f, 1.4f));
    }

    /** Só no cliente (FMLClientSetupEvent). */
    public static final class Client {
        private Client() {}

        public static void setup() {
            AddonLayers.post(ID, post -> {
                var t = post.timeline();
                if (t.weight("onda") <= 0) return; // nada a desenhar antes do impacto
                post.pass("vfxsample:receita_anel", u -> {
                    u.v3("AnelCentro", post.center());
                    u.f4("AnelEstado", (float) t.since("impacto"), t.weight("onda"), post.time().opacity(), VELOCIDADE);
                });
                post.bloom(t.weight("onda") * .12f);
            });
        }
    }
}
