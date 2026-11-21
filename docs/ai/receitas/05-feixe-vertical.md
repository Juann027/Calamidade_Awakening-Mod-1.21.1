# Receita 5 — Feixe vertical

**Objetivo.** Um pilar de luz de 64 blocos com núcleo e halo, feito no shader com `caSegmentGlow`. A largura mínima de um pixel mantém o feixe visível e estável de longe; o terreno na frente o esconde.

| | |
|---|---|
| Teste no jogo | `/vfxsample receita feixe` (14 blocos à frente, no chão) |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/FeixeVertical.java`, `examples/vfx-sample/src/main/resources/assets/vfxsample/shaders/fx/receita_feixe.fsh` |
| Usa | trilhas `altura` e `largura`, raio grande na definição (frustum), `caSegmentGlow`, `initPixel`/`gPix`, `post.bloom` por distância |
| Custo | 1 passe analítico |

## Timeline

| Elemento | Ticks | Para quê |
|---|---|---|
| trilha `altura` | 0 → 64 (20, EASE_OUT) | o feixe sobe do chão |
| trilha `largura` | 0,2 → 0,6 (18, OVERSHOOT) → 0,45 (70) → 0 (100, EASE_IN) | pulso de abertura e afinamento final |
| fases `acender` e `apagar` | 0–20 e 70–100 | disponíveis para câmera e som |

## Código Java

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/FeixeVertical.java -->
```java
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
```

## Shader

<!-- arquivo: examples/vfx-sample/src/main/resources/assets/vfxsample/shaders/fx/receita_feixe.fsh -->
```glsl
// Receita 5 — feixe vertical. A RimuLib antepõe #version 150 e common.glsl.
uniform vec3 FeixeBase;    // base do feixe, relativa à câmera
uniform vec4 FeixeEstado;  // x: altura (blocos), y: meia-largura do núcleo (blocos), z: opacidade, w: segundos

#include "calamidade_fx.glsl"

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    if (FeixeEstado.x > 0.01 && FeixeEstado.y > 0.0) {
        initPixel(texCoord);                                  // common.glsl: gPix = ângulo de um pixel
        vec3 rd = viewDir(texCoord);
        float cena = length(scenePosAt(texCoord));            // o terreno na frente esconde o feixe
        vec3 topo = FeixeBase + vec3(0.0, FeixeEstado.x, 0.0);
        vec2 brilho = caSegmentGlow(vec3(0.0), rd, FeixeBase, topo, FeixeEstado.y, FeixeEstado.y * 6.0, gPix, cena);
        float ondula = 0.85 + 0.15 * sin(FeixeEstado.w * 6.0);
        vec3 nucleo = vec3(1.0, 0.97, 0.9);
        vec3 halo = vec3(1.0, 0.75, 0.35);
        color += (nucleo * brilho.x * 1.5 + halo * brilho.y * 0.55 * ondula) * FeixeEstado.z;
    }
    fragColor = vec4(color, 1.0);
}
```

## Como adaptar

- **Raio laser horizontal:** troque `topo` por qualquer ponto; o segmento pode ter qualquer direção.
- **Vários feixes:** mande os pontos com `u.vec4s("Pontos", dados)` (xyz + largura) e some `caSegmentGlow` num laço curto. Cada feixe custa algumas operações por pixel.
- **Feixe preso a uma entidade:** `Effects.follow` move a base; o passe recebe a base nova em `post.center()` a cada quadro.
- **Raio da definição:** 2,5 × raio precisa alcançar o topo, senão o feixe some quando a base sai da tela.

## O que conferir no jogo

1. O feixe sobe em 1 segundo com um pulso de largura; núcleo quase branco, halo dourado ondulando de leve.
2. Afastando-se até ~100 blocos, ele continua visível como uma linha fina, sem serrilhar nem piscar.
3. Uma parede entre você e o feixe o esconde; acima da parede ele continua.
4. Bloom só perto do feixe (até ~48 blocos).
