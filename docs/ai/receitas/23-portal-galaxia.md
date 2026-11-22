# Receita 23 — Portal para outra galáxia

**Objetivo.** Um anel que se abre no céu revelando outro céu — nebulosa, estrelas densas e uma galáxia girando — e depois se fecha. Antes de abrir, uma fissura pisca e solta arcos de energia.

| | |
|---|---|
| Teste no jogo | `/vfxsample receita portal_galaxia` (18 s) |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/PortalGalaxia.java` (sem shader) |
| Usa | `Sky.portal()`, trilha `PROGRESS` (abertura), `spin` (giro da galáxia), `tilt` (anel visto de lado) |
| Custo | 1 camada no passe `sky`, só nos pixels de céu perto do portal |

## Timeline

| Trilha | Valores | Para quê |
|---|---|---|
| `abrir` | 0 → 0,02 (8) → 0,06 (70) | a semente: fissura que pisca e solta arcos (até 0,06 o anel não aparece) |
| | → 1 (130, EASE_OUT) … 1 (280) | o anel se expande revelando o interior, que fica preso ao anel (abrir revela mais dele) |
| | → 0 (340, EASE_IN) | fecha |

## Código Java

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/PortalGalaxia.java -->
```java
package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Placement;
import dev.calamidade_awakening.api.v1.Sky;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.Optional;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 23 — portal para outra galáxia (docs/ai/receitas/23-portal-galaxia.md).
 * Uma camada PORTAL à frente de quem chamou: primeiro a fissura que pisca e solta arcos (abertura até 0,06), depois o
 * anel se expande revelando nebulosa, estrelas e uma galáxia girando, fica aberto e se fecha. Sem atmosfera nem
 * shader próprio: o passe "sky" do motor faz tudo.
 * Teste: /vfxsample receita portal_galaxia (18 s).
 */
public final class PortalGalaxia {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_portal_galaxia");

    static final Timeline TIMELINE = Timeline.builder(360)
        .track("abrir", keyframe(0, 0f), keyframe(8, .02f), keyframe(70, .06f), keyframe(130, 1f, Easing.EASE_OUT),
            keyframe(280, 1f), keyframe(340, 0f, Easing.EASE_IN))
        .build();

    static final Sky PORTAL = Sky.portal()
        .centerOffset(0, 520, 900)
        .size(40)
        .tilt(20)
        .spin(.6f)
        .track(Sky.Param.PROGRESS, "abrir")
        .build();

    private PortalGalaxia() {}

    public static void register() {
        Effects.register(ID, new Effects.Definition(360, 40, 0xffb8ecff, Effects.Shape.NONE, Effects.Material.NONE, null, null)
            .withRange(1024));
        Effects.timeline(ID, TIMELINE);
        Effects.sky(ID, PORTAL);
    }

    static Optional<Effects.Handle> iniciar(ServerPlayer player) {
        return Effects.start(ID, player.serverLevel(), Placement.at(player.position(), player.getYRot(), 0).withCaster(player));
    }
}
```

## O que conferir no jogo

1. Primeiros 3 s: uma fissura vertical serrilhada pisca e solta arcos curtos que mudam a cada quarto de segundo; com "ocultar clarões", ela fica acesa sem piscar e os arcos ficam fracos.
2. A abertura: um anel claro com arcos de energia cresce; por dentro, nebulosa violeta, estrelas e uma galáxia espiral girando.
3. O anel tem inclinação de 20 graus (elipse); a galáxia gira devagar (0,6 grau por tick); com "efeitos de movimento" desligado, para.
4. Fechando, o anel encolhe e some; sem salto no fim.
5. A atmosfera de outro efeito ativo não pinta gradiente nem estrelas sobre o interior aberto.
