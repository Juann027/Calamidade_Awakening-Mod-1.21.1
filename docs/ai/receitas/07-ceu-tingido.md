# Receita 7 — Céu tingido com a Atmosphere API

**Objetivo.** Mudar o "clima" da cena sem escrever shader: céu roxo e laranja, névoa quente que engrossa, estrelas que aparecem, exposição mais baixa e tinta ambiente. O efeito não tem camada de cliente nenhuma; o motor desenha tudo no passe único `atmosphere`.

| | |
|---|---|
| Teste no jogo | `/vfxsample receita ceu` (30 segundos) |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/CeuTingido.java` (sem shader) |
| Usa | `Atmosphere.builder()`, `track(Param, "trilha")`, `Effects.atmosphere`, alcance 512 |
| Custo | 1 passe do motor por quadro, fora do orçamento de addons; zero quando não há atmosfera |

## Timeline e atmosfera

| Elemento | Valor | Para quê |
|---|---|---|
| trilha `nevoa` | 0 → 0,7 (160, SMOOTH) | `FOG_DENSITY` vem dela: a névoa engrossa aos poucos |
| trilha `estrelas` | 0 → 1 (200, EASE_IN) | `STARS` vem dela |
| `sky(0xff1a0838, 0xffff6a3d, .85f)` | zênite, horizonte, mistura | gradiente que substitui 85% da cor do céu do jogo |
| `fog(0xffd0603a, .7f, 24, 120)` | cor, densidade, início, fim | a densidade constante é ignorada porque a trilha manda |
| `exposure(.8f)`, `ambient(0xffff9a70, .25f)` | | cena 20% mais escura e com tom quente |
| peso | envelope da instância | entra e sai suave sozinho (14% e 28% da duração) |

**Ordem de registro:** `register` → `timeline` → `atmosphere` (a atmosfera lê trilhas, então a timeline vem antes).

## Código Java

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/CeuTingido.java -->
```java
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
```

## Como adaptar

- **Só durante uma fase:** `.weightPhase("impacto")` multiplica o peso pela fase (o Sinal de Maré do exemplo faz isso).
- **Escuridão de chefe:** `exposure(.5f)` + `ambient(0xff8090ff, .4f)` + névoa escura; com "ocultar clarões", o motor limita a velocidade de mudança da exposição.
- **Duas atmosferas ao mesmo tempo:** o motor mistura as duas de maior peso; um efeito que entra enquanto outro sai faz uma transição suave.
- **Névoa e shader packs:** a névoa vai pela névoa do próprio jogo. Se um shader pack a ignorar, o jogador desliga "Névoa da atmosfera pela névoa do jogo" e o motor a desenha na tela.

## O que conferir no jogo

1. Em ~4 segundos o céu vira roxo no alto e laranja no horizonte; o sol, a lua e as nuvens continuam visíveis.
2. A névoa quente engrossa até ~8 segundos (terreno e entidades a partir de ~24 blocos); as estrelas aparecem até ~10 segundos, cintilando.
3. A cena fica um pouco mais escura e quente; com intensidade dos efeitos de tela em 0, exposição e tinta somem, mas céu e névoa ficam.
4. F7: "Atmosfera: 1 ativas · passe sim" durante o efeito e "0 ativas · passe não" depois.
5. Olhando para o outro lado, o céu continua tingido (a atmosfera não depende de o efeito estar na tela).
6. Debaixo d'água ou com cegueira, a névoa do jogo prevalece.
