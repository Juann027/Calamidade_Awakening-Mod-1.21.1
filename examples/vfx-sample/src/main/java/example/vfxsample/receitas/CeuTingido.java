package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Atmosphere;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import net.minecraft.resources.ResourceLocation;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 7 — céu tingido com a Atmosphere API (docs/ai/receitas/07-ceu-tingido.md).
 * Nenhuma camada de cliente: o efeito só tem atmosfera, e o motor desenha tudo no passe "atmosphere".
 * Céu roxo e laranja, névoa quente que engrossa pela trilha "nevoa", estrelas pela trilha "estrelas", exposição
 * mais baixa e tinta ambiente. O peso segue o envelope da instância (entra e sai suave).
 * Teste: /vfxsample receita ceu (30 segundos; F7 mostra "Atmosfera: 1 ativas · passe sim").
 */
public final class CeuTingido {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_ceu");

    static final Timeline TIMELINE = Timeline.builder(600)
        .track("nevoa", keyframe(0, 0f), keyframe(160, .7f, Easing.SMOOTH))
        .track("estrelas", keyframe(0, 0f), keyframe(200, 1f, Easing.EASE_IN))
        .build();

    static final Atmosphere ATMOSFERA = Atmosphere.builder()
        .sky(0xff1a0838, 0xffff6a3d, .85f)   // zênite roxo, horizonte laranja, 85% sobre o céu do jogo
        .fog(0xffd0603a, .7f, 24, 120)        // névoa quente de 24 a 120 blocos
        .exposure(.8f)                        // cena um pouco mais escura
        .ambient(0xffff9a70, .25f)            // tinta quente leve
        .stars(1f)
        .track(Atmosphere.Param.FOG_DENSITY, "nevoa")
        .track(Atmosphere.Param.STARS, "estrelas")
        .build();

    private CeuTingido() {}

    public static void register() {
        // Alcance 512: a atmosfera vale até 384 blocos do centro e some suave nos últimos 128.
        Effects.register(ID, new Effects.Definition(600, 4, 0xffff6a3d, Effects.Shape.NONE, Effects.Material.NONE,
            null, null).withRange(512));
        Effects.timeline(ID, TIMELINE);
        Effects.atmosphere(ID, ATMOSFERA); // depois da timeline, porque lê as trilhas
    }
}
