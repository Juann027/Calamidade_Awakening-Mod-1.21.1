# Receita 3 — Bolha arco-íris (raio-esfera)

**Objetivo.** Uma bolha de sabão com película iridescente: `caRaySphere` dá a entrada e a saída do raio, a cor vem do ângulo de visão (fresnel → `caRainbow`), há um brilho especular e a casca de trás aparece mais fraca. Cresce, sobe devagar e estoura com som.

| | |
|---|---|
| Teste no jogo | `/vfxsample receita bolha` (7 blocos à frente, na linha do olhar) |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/BolhaArcoIris.java`, `examples/vfx-sample/src/main/resources/assets/vfxsample/shaders/fx/receita_bolha.fsh` |
| Usa | trilhas `raio` e `brilho`, marcador com cue, centro deslocado no cliente (`post.center().add(...)`), `caRaySphere`, `caRainbow` |
| Custo | 1 passe analítico |

## Timeline

| Elemento | Ticks | Para quê |
|---|---|---|
| trilha `raio` | 0 → 4 (30, OVERSHOOT) → 4,2 (100) → 5,2 (108, EASE_OUT) | tamanho, com o "inchaço" do estouro |
| trilha `brilho` | 0 → 1 (20) … 1 (100) → 0 (110, EASE_IN) | entrada e sumiço |
| marcador `estouro` | 100 | som de bolha no servidor |

## Código Java

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/BolhaArcoIris.java -->
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
 * Receita 3 — bolha arco-íris (docs/ai/receitas/03-bolha-arco-iris.md).
 * Uma esfera por raio-esfera com película iridescente (cores pelo ângulo de visão), brilho especular e a face de
 * trás mais fraca. Cresce com overshoot, sobe devagar e estoura.
 * Teste: /vfxsample receita bolha
 */
public final class BolhaArcoIris {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_bolha");

    /** Subida da bolha, em blocos por tick. */
    static final double SUBIDA = .015;

    static final Timeline TIMELINE = Timeline.builder(110)
        .phase("crescer", 0, 30, 20, 0)
        .phase("estouro", 100, 110, 0, 10)
        .track("raio", keyframe(0, 0f), keyframe(30, 4f, Easing.OVERSHOOT), keyframe(100, 4.2f), keyframe(108, 5.2f, Easing.EASE_OUT))
        .track("brilho", keyframe(0, 0f), keyframe(20, 1f), keyframe(100, 1f), keyframe(110, 0f, Easing.EASE_IN))
        .marker("estouro", 100)
        .build();

    private BolhaArcoIris() {}

    public static void register() {
        Effects.register(ID, new Effects.Definition(110, 6, 0xffb0e0ff, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(ID, TIMELINE);
        Effects.cue(ID, "estouro", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.PLAYERS, 2f, .6f));
    }

    public static final class Client {
        private Client() {}

        public static void setup() {
            AddonLayers.post(ID, post -> {
                var t = post.timeline();
                float raio = t.value("raio"), brilho = t.value("brilho");
                if (raio <= .01f || brilho <= .001f) return;
                // A bolha sobe: o centro do passe sai do centro da instância.
                var centro = post.center().add(0, SUBIDA * post.age(), 0);
                post.pass("vfxsample:receita_bolha", u -> {
                    u.v3("BolhaCentro", centro);
                    u.f4("BolhaEstado", raio, brilho, post.time().opacity(), (float) (post.realAge() / 20.0));
                });
            });
        }
    }
}
```

## Shader

<!-- arquivo: examples/vfx-sample/src/main/resources/assets/vfxsample/shaders/fx/receita_bolha.fsh -->
```glsl
// Receita 3 — bolha arco-íris (raio-esfera). A RimuLib antepõe #version 150 e common.glsl.
uniform vec3 BolhaCentro;  // centro relativo à câmera
uniform vec4 BolhaEstado;  // x: raio (blocos), y: trilha "brilho" (0..1), z: opacidade, w: segundos (giro das cores)

#include "calamidade_fx.glsl"

// Película fina: as cores mudam com o ângulo entre a normal e o raio de visão (fresnel).
vec3 bolhaPelicula(vec3 n, vec3 rd) {
    float fresnel = pow(1.0 - abs(dot(n, rd)), 3.0);
    float matiz = fresnel * 0.9 + n.y * 0.2 + BolhaEstado.w * 0.05;
    return caRainbow(matiz) * (0.12 + 0.88 * fresnel);
}

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float raio = BolhaEstado.x;
    if (raio > 0.01 && BolhaEstado.y > 0.001) {
        vec3 rd = viewDir(texCoord);
        float cena = length(scenePosAt(texCoord));
        vec2 t = caRaySphere(vec3(0.0), rd, BolhaCentro, raio);
        if (t.y > 0.0) {
            vec3 luz = vec3(0.0);
            if (t.x > 0.0 && t.x < cena) {                       // casca da frente
                vec3 n = normalize(rd * t.x - BolhaCentro);
                luz += bolhaPelicula(n, rd);
                vec3 sol = normalize(vec3(0.4, 0.8, 0.3));
                luz += vec3(1.0) * pow(max(dot(reflect(rd, n), sol), 0.0), 60.0) * 0.8;
            }
            if (t.y < cena) {                                    // casca de trás, vista por dentro
                vec3 n = normalize(rd * t.y - BolhaCentro);
                luz += 0.4 * bolhaPelicula(-n, rd);
            }
            color += luz * BolhaEstado.y * BolhaEstado.z;
        }
    }
    fragColor = vec4(color, 1.0);
}
```

## Como adaptar

- **Escudo em vez de bolha:** troque a película por `caWorldGrid` na normal (hexágonos pedem uma função própria) e prenda o centro a uma entidade com `Effects.follow`.
- **Movimento:** o centro do passe pode sair do centro da instância (aqui sobe `SUBIDA` bloco/tick). Para algo que o servidor precisa saber (dano), mova a instância de verdade com `follow`.
- **Cores:** `matiz` decide o arco-íris; some uma constante para mudar a paleta ou multiplique `fresnel` para faixas mais finas.

## O que conferir no jogo

1. A bolha cresce com overshoot, sobe devagar e mostra cores que mudam com o ângulo, mais fortes na borda.
2. Um ponto de brilho (sol fixo no alto) aparece na casca.
3. Entrando na bolha, só a casca de trás aparece.
4. No tick 100 ela incha, toca o som de estouro e some em meio segundo.
