# Receita 4 — Impact frame de duas cores

**Objetivo.** O "quadro de impacto" de anime: no golpe, a tela vira duas cores por limiar de luminância por 6 ticks e inverte na metade. Antes, uma vinheta vermelha cresce. Acessível por construção.

| | |
|---|---|
| Teste no jogo | `/vfxsample receita impacto` |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/ImpactFrame.java`, `examples/vfx-sample/src/main/resources/assets/vfxsample/shaders/fx/receita_quadro.fsh` |
| Usa | fase curta com degrau, `post.screenEffects()` e `post.flashAllowed()` (0.8.0), `post.vignette`, `post.shake`, `caLuma` |
| Custo | 1 passe só durante os 6 ticks do quadro |

## Timeline

| Elemento | Ticks | Para quê |
|---|---|---|
| fase `carga` | 0–20 (subida 14) | vinheta vermelha crescendo |
| fase `quadro` | 20–26 (degrau na entrada, queda 2) | força do impact frame |
| marcador `golpe` | 20 | som no servidor; a inversão começa 3 ticks depois |

## Acessibilidade

Um passe próprio não passa pelos ajustes do motor automaticamente. Por isso a força é `peso × post.screenEffects()`, e com "ocultar clarões" (`!post.flashAllowed()`) ela cai para 35% e a inversão (que pisca) é desligada. `post.vignette` e `post.shake` já respeitam as opções sozinhos.

## Código Java

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/ImpactFrame.java -->
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
```

## Shader

<!-- arquivo: examples/vfx-sample/src/main/resources/assets/vfxsample/shaders/fx/receita_quadro.fsh -->
```glsl
// Receita 4 — impact frame de duas cores. A RimuLib antepõe #version 150 e common.glsl.
uniform vec3 QuadroClaro;   // cor das áreas claras
uniform vec3 QuadroEscuro;  // cor das áreas escuras
uniform vec4 QuadroEstado;  // x: força (0..1, já com a acessibilidade), y: inverter (0/1), z: limiar de luminância

#include "calamidade_fx.glsl"

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float forca = QuadroEstado.x;
    if (forca > 0.001) {
        float claro = smoothstep(QuadroEstado.z - 0.04, QuadroEstado.z + 0.04, caLuma(color));
        if (QuadroEstado.y > 0.5) claro = 1.0 - claro;
        color = mix(color, mix(QuadroEscuro, QuadroClaro, claro), forca);
    }
    fragColor = vec4(color, 1.0);
}
```

## Como adaptar

- **Outras cores:** `CLARO` e `ESCURO`. Branco e preto dão o clássico; vermelho e preto, o "golpe crítico".
- **Ligar a um golpe real:** registre a fase `quadro` na timeline do seu efeito de ataque e use `Effects.cue(id, "golpe", ...)` para o dano no mesmo tick.
- **Alternativa sem passe:** `post.desaturate` + `post.contrast` (ou `Impact(..., MONOCHROME)` na definição) dão um quadro monocromático sem gastar orçamento.

## O que conferir no jogo

1. Vinheta vermelha crescendo por 1 segundo; no golpe, som de crítico e a tela em duas cores, invertendo depois de 3 ticks, com tremor.
2. Intensidade dos efeitos de tela em 0: nenhum impact frame (a vinheta também some).
3. "Ocultar clarões": quadro fraco (35%) e sem inversão.
4. F7: 1 passe só durante o quadro.
