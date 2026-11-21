# Receita 1 — Anel de impacto no chão

**Objetivo.** Um anel dourado que nasce no impacto e corre pelo terreno, desenhado num passe de pós-processamento que lê a profundidade da cena. É o "shockwave" mais barato que existe: nenhuma malha, um passe só.

| | |
|---|---|
| Teste no jogo | `/vfxsample receita anel` (6 blocos à frente, no chão) |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/AnelImpacto.java`, `examples/vfx-sample/src/main/resources/assets/vfxsample/shaders/fx/receita_anel.fsh` |
| Usa | Timeline (fases e marcador), `Effects.cue` no marcador, `Impact` com clarão, `AddonLayers.post`, `caDistanceWave` |
| Custo | 1 passe de addon por quadro, só depois do impacto |

## Timeline

| Elemento | Ticks | Para quê |
|---|---|---|
| fase `carga` | 0–12 (subida 4, queda 2) | reservada para um brilho de carga, se quiser |
| fase `onda` | 12–60 (queda 20) | liga o passe e apaga o anel no fim |
| marcador `impacto` | 12 | cue de som no servidor e origem do tempo do anel (`t.since("impacto")`) |

O `Impact(12, 3, .45f, TINTED_FLASH)` da definição dá o clarão do motor no mesmo tick. Ele some quando o jogador oculta clarões.

## Código Java

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/AnelImpacto.java -->
```java
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
```

## Shader

<!-- arquivo: examples/vfx-sample/src/main/resources/assets/vfxsample/shaders/fx/receita_anel.fsh -->
```glsl
// Receita 1 — anel de impacto no chão. A RimuLib antepõe #version 150 e common.glsl.
uniform vec3 AnelCentro;  // centro relativo à câmera (post.center()), mesmo espaço de scenePosAt
uniform vec4 AnelEstado;  // x: ticks desde "impacto", y: peso da fase "onda", z: opacidade, w: velocidade (blocos/tick)

#include "calamidade_fx.glsl"

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    if (!skyAt(texCoord) && AnelEstado.y > 0.0) {
        vec3 d = scenePosAt(texCoord) - AnelCentro;
        // Só no terreno perto da altura do centro (até ~3 blocos acima ou abaixo).
        float altura = 1.0 - smoothstep(2.0, 4.0, abs(d.y));
        float r = length(d.xz);
        float anel = caDistanceWave(r, AnelEstado.x, AnelEstado.w, 0.5, 24.0);
        float rastro = caDistanceWave(r, AnelEstado.x - 3.0, AnelEstado.w, 1.5, 24.0) * 0.35;
        color += vec3(1.0, 0.72, 0.3) * (anel + rastro) * altura * AnelEstado.y * AnelEstado.z * 1.6;
    }
    fragColor = vec4(color, 1.0);
}
```

## Como adaptar

- **Velocidade e alcance:** `VELOCIDADE` (blocos por tick) e o último argumento de `caDistanceWave` (24 blocos).
- **Cor:** o `vec3(1.0, 0.72, 0.3)` do shader; para várias cores, mande um `f3` a mais.
- **Gameplay:** troque o som do cue `impacto` por dano (veja `Sample.impact` e o capítulo de gameplay). O cue roda no mesmo tick do anel.
- **Encostas:** `altura` limita o anel a ±3 blocos do centro; aumente o 4.0 para morros.

## O que conferir no jogo

1. O anel nasce no tick do clarão, sobre o terreno, e se afasta a cerca de 9 blocos por segundo até ~24 blocos.
2. Paredes e blocos altos próximos não acendem acima de ~3 blocos da altura do centro.
3. Com "ocultar clarões" ligado, o clarão do impacto some; o anel continua.
4. F7: 1 passe de pós-processamento durante a onda; 0 antes do tick 12.
