# Receita 6 — Efeito ambiente com sustain (vaga-lumes)

**Objetivo.** Um efeito que fica até alguém mandar parar: 48 vaga-lumes em malha de mundo surgem, ficam em loop e só somem depois do `release`. Mostra como escrever um loop sem emenda e um release sem salto.

| | |
|---|---|
| Teste no jogo | `/vfxsample receita ambiente`, depois `/vfxsample receita release` (ou `stop`) |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/AmbienteVagalumes.java` (sem shader: malha de mundo + efeitos de tela) |
| Usa | `sustain(40, 120)`, cue dentro do loop e cue no início da saída, `AddonLayers.world` com `ribbon`, `Handle.release()` |
| Custo | 48 × 12 = 576 vértices por quadro; nenhum passe |

## Timeline

| Elemento | Ticks | Para quê |
|---|---|---|
| `sustain(40, 120)` | loop de 80 ticks | repete até `release()`; a saída começa em 120 |
| trilha `densidade` | 0 → 1 (40) … 1 (120) → 0 (200) | mesmo valor nas bordas do loop e no início da saída |
| trilha `pulso` | 0 (40) → 1 (80) → 0 (120) | brilho que respira a cada volta; vale 0 nas duas bordas |
| marcador `pulso` | 80, dentro do loop | som a cada volta |
| marcador `sumir` | 120, início da saída | som uma vez, no release |

**Regra do loop sem emenda.** O motor não suaviza saltos. Tudo que depende da idade local precisa valer o mesmo em 40 e em 120: as trilhas têm os mesmos valores nas bordas, e o movimento usa uma fase de período 80 ticks (`(idade - 40) × 2π / 80`).

## Código Java

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/AmbienteVagalumes.java -->
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
import net.minecraft.world.phys.Vec3;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 6 — efeito ambiente com sustain (docs/ai/receitas/06-ambiente-sustain.md).
 * Vaga-lumes em malha de mundo (AddonLayers.world) que surgem em 40 ticks, repetem o trecho [40, 120) até o
 * release e somem em 80 ticks. O movimento tem período igual ao loop (80 ticks), então a volta não salta.
 * Teste: /vfxsample receita ambiente, depois /vfxsample receita release (ou stop).
 */
public final class AmbienteVagalumes {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_ambiente");

    static final int LOOP_INICIO = 40, LOOP_FIM = 120;
    static final int VAGALUMES = 48;

    static final Timeline TIMELINE = Timeline.builder(200)
        .phase("surgir", 0, LOOP_INICIO, 30, 0)
        .phase("sumir", LOOP_FIM, 200, 0, 70)
        // Mesmo valor em 40, 120 e no início da saída: loop e release sem salto.
        .track("densidade", keyframe(0, 0f), keyframe(LOOP_INICIO, 1f, Easing.SMOOTH), keyframe(LOOP_FIM, 1f),
            keyframe(200, 0f, Easing.SMOOTH))
        .track("pulso", keyframe(LOOP_INICIO, 0f), keyframe(80, 1f, Easing.SMOOTH), keyframe(LOOP_FIM, 0f, Easing.SMOOTH))
        .marker("pulso", 80)      // dentro do loop: o cue toca a cada volta
        .marker("sumir", LOOP_FIM) // início da saída: toca uma vez, no release
        .sustain(LOOP_INICIO, LOOP_FIM)
        .build();

    private AmbienteVagalumes() {}

    public static void register() {
        Effects.register(ID, new Effects.Definition(200, 6, 0xffd8ff70, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(ID, TIMELINE);
        Effects.cue(ID, "pulso", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, .8f, 1.6f));
        Effects.cue(ID, "sumir", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.AMBIENT, .8f, .7f));
    }

    public static final class Client {
        private Client() {}

        public static void setup() {
            AddonLayers.world(ID, world -> {
                var t = world.timeline();
                float densidade = t.value("densidade");
                if (densidade <= .001f) return;
                // Fase periódica no loop: em 40 e em 120 vale o mesmo ângulo, então a volta é contínua.
                double fase = (t.localAge() - LOOP_INICIO) * Math.TAU / (LOOP_FIM - LOOP_INICIO);
                float semente = world.time().seed();
                for (int i = 0; i < VAGALUMES && world.remainingVertices() >= 12; i++) {
                    double a = hash(i, semente) * Math.TAU, raio = 1 + 5 * hash(i + 97, semente);
                    double altura = .3 + 2.7 * hash(i + 191, semente), volta = Math.floor(hash(i + 7, semente) * 3) + 1;
                    Vec3 p = new Vec3(Math.cos(a + .3 * Math.sin(fase * volta + i)) * raio,
                        altura + .4 * Math.sin(fase * volta + i * 1.7),
                        Math.sin(a + .3 * Math.sin(fase * volta + i)) * raio);
                    float pisca = (float) (.35 + .65 * Math.max(0, Math.sin(fase * volta * 2 + i * 2.3)));
                    int cor = argb(.9f * densidade * pisca, 0xd8ff70);
                    // Dois laços cruzados formam um ponto de luz visível de quase todo ângulo.
                    Vec3 cima = new Vec3(0, .09, 0);
                    world.ribbon(p.subtract(cima), p.add(cima), new Vec3(1, 0, 0), .18, cor);
                    world.ribbon(p.subtract(cima), p.add(cima), new Vec3(0, 0, 1), .18, cor);
                }
            });
            AddonLayers.post(ID, post -> {
                var t = post.timeline();
                float perto = (float) Math.clamp(1 - post.distance() / 16, 0, 1);
                post.vignette(0xff0c1a08, t.value("densidade") * perto * .25f);
                post.bloom(t.value("densidade") * .1f + t.value("pulso") * .06f);
            });
        }

        private static double hash(int i, float seed) {
            double x = Math.sin(i * 12.9898 + seed * 78.233) * 43758.5453;
            return x - Math.floor(x);
        }

        private static int argb(float alpha, int rgb) {
            return (Math.round(Math.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xffffff);
        }
    }
}
```

## Como adaptar

- **Duração do loop:** mude `LOOP_INICIO`/`LOOP_FIM` e mantenha as trilhas iguais nas bordas.
- **Liberar pelo servidor:** guarde o `Effects.Handle` (como `Receitas` faz) e chame `release()` quando a condição acabar; `stop()` corta na hora.
- **Mais partículas:** confira `world.remainingVertices()` antes de emitir; o orçamento de vértices é dividido entre os efeitos visíveis e muda com a qualidade (F8).
- **Atmosfera junto:** um efeito ambiente combina bem com `Effects.atmosphere` (névoa leve que segue a trilha `densidade`), como o Farol do exemplo.

## O que conferir no jogo

1. Os vaga-lumes surgem em 2 segundos e piscam e flutuam sem nenhum salto na volta do loop (a cada 4 segundos, com um som de cristal).
2. `/vfxsample receita release`: som de encantamento uma vez e sumiço suave em 4 segundos, sem salto.
3. `/vfxsample receita stop`: some na hora.
4. Um segundo jogador que chega depois vê o mesmo estado (loop ou saída).
5. F7: vértices sobem ~576; nenhum passe de pós-processamento.
