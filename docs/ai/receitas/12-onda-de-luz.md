# Receita 12 — Onda de luz

**Objetivo.** A onda de choque da receita 1 com um anel de luz no chão que se expande junto. A trilha `raio` cresce na mesma velocidade da frente da onda do shader (0,45 bloco por tick desde o impacto), então a luz acompanha a onda; um clarão de luz no centro marca o impacto.

| | |
|---|---|
| Teste no jogo | `/vfxsample receita onda_luz` (6 blocos à frente, no chão) |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/OndaDeLuz.java`; shader `examples/vfx-sample/src/main/resources/assets/vfxsample/shaders/fx/receita_anel.fsh` (o mesmo da receita 1) |
| Usa | `Light.ring` com `track(Light.Param.RADIUS, ...)`, `Light.point` com `weightPhase`, passe próprio (`AddonLayers.post`), Impact |
| Custo | 1 passe de addon (a onda) + 2 luzes no passe `lights` |

## Timeline e luzes

| Elemento | Valor | Para quê |
|---|---|---|
| marcador `impacto` | 12 | som e início da onda |
| fase `onda` | 12–60, queda 20 | peso do anel e do passe |
| fase `clarao` | 12–24, subida 1, queda 10 | clarão de luz no centro |
| trilha `raio` | 0 em 12 → 21,6 em 60 (LINEAR) | `RADIUS` do anel = 0,45 × (idade − 12), a frente de `caDistanceWave` |
| trilha `forca` | 3 → 2 (36) → 0 (60, SMOOTH) | `INTENSITY` do anel: some no fim |
| `ANEL` | `Light.ring(center(0, .2, 0), 0, 3)`, altura 3 | faixa de luz de 3 blocos na frente da onda |

**Por que LINEAR:** a frente da onda no shader anda com velocidade constante; outra curva faria a luz correr na frente ou atrás dela.

## Código Java

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/OndaDeLuz.java -->
```java
package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import dev.calamidade_awakening.api.v1.client.AddonLayers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 12 — onda de luz (docs/ai/receitas/12-onda-de-luz.md).
 * A onda de choque da receita 1 (o mesmo shader, receita_anel.fsh) com um anel de luz no chão que se expande junto:
 * a trilha "raio" cresce na mesma velocidade da onda (0,45 bloco por tick desde o impacto), então a luz acompanha a
 * frente. Um clarão de luz no centro marca o impacto.
 * Teste: /vfxsample receita onda_luz
 */
public final class OndaDeLuz {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_onda_luz");

    /** Velocidade da onda, em blocos por tick: a mesma no shader e na trilha "raio". */
    static final float VELOCIDADE = .45f;

    static final Timeline TIMELINE = Timeline.builder(60)
        .phase("carga", 0, 12, 4, 2)
        .phase("onda", 12, 60, 0, 20)
        .phase("clarao", 12, 24, 1, 10)
        // Linear de propósito: raio = 0,45 x (idade - 12), igual à frente de caDistanceWave no shader.
        .track("raio", keyframe(12, 0f), keyframe(60, VELOCIDADE * 48))
        .track("forca", keyframe(12, 3f), keyframe(36, 2f), keyframe(60, 0f, Easing.SMOOTH))
        .marker("impacto", 12)
        .build();

    /** Anel de luz de 3 blocos de largura que segue a frente da onda. */
    static final Light ANEL = Light.ring(Point.center(0, .2, 0), 0, 3)
        .color(0xffffc060)
        .height(3)
        .track(Light.Param.RADIUS, "raio")
        .track(Light.Param.INTENSITY, "forca")
        .weightPhase("onda")
        .build();

    /** Clarão no centro, só no impacto. */
    static final Light CLARAO = Light.point(Point.center(0, 1, 0)).color(0xffffe0a0).intensity(3f).radius(8)
        .weightPhase("clarao").build();

    private OndaDeLuz() {}

    public static void register() {
        // Raio 10: o frustum (25 blocos) cobre os 21,6 blocos que a onda percorre.
        Effects.register(ID, new Effects.Definition(60, 10, 0xffffc060, Effects.Shape.NONE, Effects.Material.NONE,
            null, null, new Effects.Impact(12, 3, .45f, Effects.ImpactStyle.TINTED_FLASH)));
        Effects.timeline(ID, TIMELINE);
        Effects.lights(ID, ANEL, CLARAO);
        Effects.cue(ID, "impacto", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, .6f, 1.2f));
    }

    /** Só no cliente: a onda visível, com o shader da receita 1. */
    public static final class Client {
        private Client() {}

        public static void setup() {
            AddonLayers.post(ID, post -> {
                var t = post.timeline();
                if (t.weight("onda") <= 0) return;
                post.pass("vfxsample:receita_anel", u -> {
                    u.v3("AnelCentro", post.center());
                    u.f4("AnelEstado", (float) t.since("impacto"), t.weight("onda"), post.time().opacity(), VELOCIDADE);
                });
            });
        }
    }
}
```

## Como adaptar

- **Só a luz:** apague a camada de cliente; o anel de luz sozinho já desenha uma onda no chão.
- **Onda mais rápida:** mude `VELOCIDADE`; a trilha `raio` usa a mesma constante.
- **Anel em volta de um chefe:** `Light.ring(Point.anchor(0, .2, 0), 6, 2)` com `Effects.follow`.

## O que conferir no jogo

1. No impacto, um clarão de luz no chão e o anel dourado da onda saem juntos; a luz acompanha a frente da onda até ~22 blocos.
2. Paredes no caminho recebem a luz quando a onda passa (40% nas faces verticais); tetos não.
3. F7: "Luzes: 2" no impacto e "Luzes: 1" depois que o clarão some.
4. Com "ocultar clarões", o clarão do motor (Impact) some, mas a luz no chão continua.
