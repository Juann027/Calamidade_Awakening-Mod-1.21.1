# Receita 2 — Cubo de grade gigante (raio-caixa)

**Objetivo.** Um cubo de 16 blocos de aresta, feito só no shader: o raio de cada pixel é intersectado com a caixa (`caRayBox`), as faces da frente e do fundo recebem uma grade em coordenadas do cubo, as arestas brilham mais e uma faixa de varredura sobe. Monta com overshoot e desmonta.

| | |
|---|---|
| Teste no jogo | `/vfxsample receita cubo` (centro 22 blocos à frente, 8 acima do chão) |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/CuboGrade.java`, `examples/vfx-sample/src/main/resources/assets/vfxsample/shaders/fx/receita_cubo.fsh` |
| Usa | trilhas com `OVERSHOOT`, `post.realAge()`, `caRayBox`, `caBoxNormal`, `caWorldGrid`, `initPixel`/`gPix` |
| Custo | 1 passe; o shader é analítico (sem raymarch) |

## Timeline

| Elemento | Ticks | Para quê |
|---|---|---|
| trilha `grade` | 0 → 1 (40, EASE_OUT), 1 até 150, → 0 (200, SMOOTH) | intensidade da grade |
| trilha `tamanho` | 0,2 → 1 (40, OVERSHOOT) | escala da meia-aresta |
| fases `montagem` e `desmonte` | 0–40 e 150–200 | disponíveis para câmera ou som |
| marcador `pronto` | 40 | som no servidor |

## Código Java

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/CuboGrade.java -->
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
 * Receita 2 — cubo de grade gigante (docs/ai/receitas/02-cubo-de-grade.md).
 * Um cubo de 16 blocos desenhado por raio-caixa no shader: grade nas faces da frente e do fundo, arestas mais
 * fortes e uma linha de varredura. A grade fica presa ao cubo (coordenadas relativas ao centro), sem deslizar.
 * Teste: /vfxsample receita cubo
 */
public final class CuboGrade {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_cubo");

    /** Meia-aresta final, em blocos. */
    static final float MEIA_ARESTA = 8f;

    /** 200 ticks: monta em 40 (com overshoot no tamanho), fica, desmonta nos últimos 50. */
    static final Timeline TIMELINE = Timeline.builder(200)
        .phase("montagem", 0, 40, 30, 0)
        .phase("desmonte", 150, 200, 0, 50)
        .track("grade", keyframe(0, 0f), keyframe(40, 1f, Easing.EASE_OUT), keyframe(150, 1f), keyframe(200, 0f, Easing.SMOOTH))
        .track("tamanho", keyframe(0, .2f), keyframe(40, 1f, Easing.OVERSHOOT))
        .marker("pronto", 40)
        .build();

    private CuboGrade() {}

    public static void register() {
        // Raio 12: o teste de frustum usa 2,5 x raio, o que cobre a diagonal do cubo (8 x raiz de 3).
        Effects.register(ID, new Effects.Definition(200, 12, 0xff40f0ff, Effects.Shape.NONE, Effects.Material.NONE,
            null, null).withRange(256));
        Effects.timeline(ID, TIMELINE);
        Effects.cue(ID, "pronto", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.5f, .8f));
    }

    public static final class Client {
        private Client() {}

        public static void setup() {
            AddonLayers.post(ID, post -> {
                var t = post.timeline();
                float grade = t.value("grade");
                if (grade <= .001f) return;
                post.pass("vfxsample:receita_cubo", u -> {
                    u.v3("CuboCentro", post.center());
                    u.f4("CuboEstado", MEIA_ARESTA * t.value("tamanho"), grade, post.time().opacity(), (float) post.realAge());
                });
            });
        }
    }
}
```

## Shader

<!-- arquivo: examples/vfx-sample/src/main/resources/assets/vfxsample/shaders/fx/receita_cubo.fsh -->
```glsl
// Receita 2 — cubo de grade gigante (raio-caixa). A RimuLib antepõe #version 150 e common.glsl.
uniform vec3 CuboCentro;  // centro relativo à câmera (post.center())
uniform vec4 CuboEstado;  // x: meia-aresta (blocos), y: trilha "grade" (0..1), z: opacidade, w: idade real (ticks)

#include "calamidade_fx.glsl"

// Luz de um ponto p na superfície do cubo: grade de 2 blocos, arestas e uma faixa de varredura vertical.
float cuboFace(vec3 p, vec3 bmin, vec3 bmax, float meia) {
    vec3 n = caBoxNormal(p, bmin, bmax);
    vec3 local = p - CuboCentro;                       // grade presa ao cubo, não à câmera
    float px = gPix * length(p);                       // tamanho do pixel em blocos nesta distância
    float grade = caWorldGrid(local + meia, n, 2.0, 0.04, px);
    vec3 q = meia - abs(local) + abs(n) * 1e6;         // distância às arestas pelos dois eixos da face
    float aresta = 1.0 - smoothstep(0.08, 0.08 + 2.0 * px, min(min(q.x, q.y), q.z));
    float altura = mix(-meia, meia, fract(CuboEstado.w / 60.0));
    float varredura = exp(-abs(local.y - altura) * 2.0);
    return grade * 0.8 + aresta * 1.4 + varredura * 0.25;
}

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float meia = CuboEstado.x;
    if (CuboEstado.y > 0.001 && meia > 0.01) {
        initPixel(texCoord);                           // common.glsl: preenche gPix (ângulo de um pixel)
        vec3 rd = viewDir(texCoord);
        float cena = length(scenePosAt(texCoord));
        vec3 bmin = CuboCentro - meia, bmax = CuboCentro + meia;
        vec2 t = caRayBox(vec3(0.0), rd, bmin, bmax);
        if (t.x >= 0.0) {
            float luz = 0.0;
            if (t.x > 0.0 && t.x < cena) luz += cuboFace(rd * t.x, bmin, bmax, meia);        // face da frente
            if (t.y < cena) luz += 0.45 * cuboFace(rd * t.y, bmin, bmax, meia);              // face do fundo
            float volume = clamp((min(t.y, cena) - t.x) / (2.0 * meia), 0.0, 1.0);           // véu leve dentro
            color += vec3(0.25, 0.95, 1.0) * (luz + volume * 0.06) * CuboEstado.y * CuboEstado.z;
        }
    }
    fragColor = vec4(color, 1.0);
}
```

## Como adaptar

- **Caixa não cúbica:** mande `bmin`/`bmax` por uniform (dois `f3`) em vez da meia-aresta.
- **Grade que não desliza:** use sempre coordenadas relativas a um ponto fixo do mundo (aqui o centro). `scenePosAt` e `post.center()` são relativos à câmera.
- **Sem moiré ao longe:** passe o tamanho do pixel (`gPix * distância`); `caWorldGrid` afina e apaga a grade sozinha.
- **Raio da definição:** o teste de frustum usa 2,5 × raio em volta do centro; cubra a diagonal da caixa, senão ela some quando o centro sai da tela.

## O que conferir no jogo

1. O cubo cresce com um pequeno exagero e assenta em ~2 segundos; arestas mais claras que a grade.
2. Andando em volta, a grade fica presa ao cubo (não "nada" com a câmera). As faces de trás aparecem mais fracas através das da frente.
3. Entrando no cubo, a face da frente some e o interior mostra as faces de trás.
4. Blocos entre você e o cubo o escondem corretamente; de longe, a grade não cintila.
5. F7: 1 passe enquanto `grade` > 0.
