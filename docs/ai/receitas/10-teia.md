# Receita 10 — Teia de feixes

**Objetivo.** Um centro 4 blocos acima do chão ligado por 8 feixes a pontos em volta, com pulsos correndo do centro para fora e uma ondulação leve. Mostra vários feixes num efeito, todos num único passe do motor, e trilhas em parâmetros inteiros (`PULSE_COUNT`).

| | |
|---|---|
| Teste no jogo | `/vfxsample receita teia` (12 blocos à frente, no chão) |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/TeiaDeFeixes.java` (sem shader) |
| Usa | vários `Beam` montados num laço, `pulses`, `wave`, `track(Beam.Param.PULSE_COUNT, ...)` |
| Custo | 8 feixes no passe `beams` (o teto do quadro é 16/48/128) |

## Timeline e feixes

| Elemento | Valor | Para quê |
|---|---|---|
| trilha `tecer` | 0 → 1 (30, SMOOTH) | `REVEAL` dos 8 fios: a teia se estende do centro para fora |
| trilha `pulsos` | 0 até 30, 3 em 50–130, 0 em 150 | `PULSE_COUNT` (arredondado): os pulsos começam depois que a teia se forma |
| fios | `center(0, 4, 0)` → círculo de 8 blocos, alternando chão e 2,5 de altura | `width(.1f, .7f)`, halo ciano, `pulses(2, .8f, 1.6f)`, `wave(.12f, 3f, .3f)` |

**Pulsos e ondulação** andam com a idade real da instância (não saltam em loops); a ondulação some a 2 blocos de cada ponta, então os fios ficam presos ao centro e às âncoras.

## Código Java

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/TeiaDeFeixes.java -->
```java
package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 10 — teia de feixes (docs/ai/receitas/10-teia.md).
 * Um centro 4 blocos acima do chão ligado por 8 feixes a pontos em volta, com pulsos que correm do centro para
 * fora e uma ondulação leve. Os 8 feixes vão num único passe do motor, junto com os de qualquer outro efeito.
 * Teste: /vfxsample receita teia
 */
public final class TeiaDeFeixes {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_teia");

    static final Timeline TIMELINE = Timeline.builder(160)
        .track("tecer", keyframe(0, 0f), keyframe(30, 1f, Easing.SMOOTH))
        .track("pulsos", keyframe(0, 0f), keyframe(30, 0f), keyframe(50, 3f), keyframe(130, 3f), keyframe(150, 0f))
        .build();

    /** 8 fios do centro a um círculo de 8 blocos, alternando chão e 2,5 blocos de altura. */
    static final Beam[] FIOS = new Beam[8];
    static {
        for (int k = 0; k < FIOS.length; k++) {
            double angulo = k * Math.PI * 2 / FIOS.length;
            FIOS[k] = Beam.builder(Point.center(0, 4, 0),
                    Point.center(8 * Math.cos(angulo), k % 2 == 0 ? 0 : 2.5, 8 * Math.sin(angulo)))
                .width(.1f, .7f)
                .color(0xffffffff, 0xff60d0ff)
                .pulses(2, .8f, 1.6f)               // do centro para fora (velocidade positiva: do início ao fim)
                .wave(.12f, 3f, .3f)
                .track(Beam.Param.REVEAL, "tecer")
                .track(Beam.Param.PULSE_COUNT, "pulsos")
                .build();
        }
    }

    private TeiaDeFeixes() {}

    public static void register() {
        Effects.register(ID, new Effects.Definition(160, 10, 0xff60d0ff, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(ID, TIMELINE);
        Effects.beams(ID, FIOS);
        Effects.cue(ID, 0, ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2f, .6f));
    }
}
```

## Como adaptar

- **Ligar a entidades:** troque as pontas por `Point.caster(...)` ou `Point.anchor(...)`; o fio some enquanto a entidade não está carregada no cliente.
- **Mais fios:** até 16 feixes por efeito; acima disso, divida em dois efeitos.
- **Onda vista de frente:** de frente para a câmera, a ondulação diminui sozinha (o motor evita anéis falsos).

## O que conferir no jogo

1. Os 8 fios saem do centro e chegam às pontas em ~1,5 s; depois, pulsos ciano correm do centro para fora.
2. Os fios ondulam de leve, presos nas pontas; não há anéis em volta de fios que apontam para a câmera.
3. F7: "Feixes: 8 desenhados".
4. Em LOW, com outros efeitos com feixes, o teto de 16 feixes corta primeiro os dos efeitos menos importantes.
