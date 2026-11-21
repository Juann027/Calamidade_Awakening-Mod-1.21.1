# Receita 9 — Laser preso ao jogador

**Objetivo.** Um laser dos olhos do jogador até o bloco que ele mira, que acompanha a mira em todos os clientes sem pacote novo: a instância segue o jogador (`Effects.follow`) e as pontas do feixe são `Point.anchorEye` e `Point.anchorAim`, resolvidas a cada quadro no cliente a partir da posição e da rotação da entidade.

| | |
|---|---|
| Teste no jogo | `/vfxsample receita laser` (5 segundos; mire em paredes, no chão e no céu) |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/LaserMira.java` (sem shader); `Receitas.java` (início com `Effects.follow`) |
| Usa | `Point.anchorEye`, `Point.anchorAim`, ruído, pulsos, `Light.point` com cintilação, `Light.along` |
| Custo | 1 feixe e 2 luzes; um raio de mira por quadro (compartilhado pelo feixe e pela luz) |

## Timeline, feixe e luzes

| Elemento | Valor | Para quê |
|---|---|---|
| trilha `disparo` | 0 → 1 (4, EASE_OUT) | `REVEAL`: o tiro sai dos olhos e chega ao alvo em 4 ticks |
| trilha `carga` | 0,6 → 1,5 (4, OVERSHOOT) → 1 (12) | `INTENSITY`: pico no acerto |
| fase `acertou` | 4–100 | peso da luz no ponto de impacto (só depois que o tiro chega) |
| feixe | `anchorEye(0, -.3, 0)` → `anchorAim(48)`, `width(.12f, .9f)`, `pulses(4, 2.5f, 1.5f)`, `noise(.04f)` | vermelho, estalando de leve |
| `ALVO` | `Light.point(Point.anchorAim(48))`, alcance 5, cintilação 30% a 10 Hz | brilho onde o laser acerta |
| `TRILHA` | `Light.along(FEIXE)`, alcance 3 | luz fraca ao longo do feixe |

**Mira sem acerto:** `anchorAim` usa a ponta do alcance (48 blocos) quando o olhar não encontra bloco; mirando o céu, o laser some ao longe.

**Primeira pessoa:** com a ponta junto dos olhos, o motor limita a largura angular do feixe (~0,25 grau de núcleo, ~0,7 grau de halo) e apaga o trecho a menos de ~1 bloco da câmera: quem atira vê um traço fino até o alvo, não um cone. Quem vê de fora vê a largura real (0,12 e 0,9 bloco).

## Código Java

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/LaserMira.java -->
```java
package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 9 — laser preso ao jogador (docs/ai/receitas/09-laser.md).
 * A instância segue o jogador (Effects.follow) e o feixe vai dos olhos dele até o primeiro bloco que ele mira
 * (Point.anchorEye e Point.anchorAim): vire a câmera e o laser acompanha, em todos os clientes, sem pacote novo.
 * Uma luz pontual cintila onde o laser acerta e outra, fraca, segue o feixe.
 * Teste: /vfxsample receita laser (5 segundos; mire em paredes e no chão)
 */
public final class LaserMira {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_laser");

    static final Timeline TIMELINE = Timeline.builder(100)
        .phase("acertou", 4, 100, 2, 15)
        .track("disparo", keyframe(0, 0f), keyframe(4, 1f, Easing.EASE_OUT))
        .track("carga", keyframe(0, .6f), keyframe(4, 1.5f, Easing.OVERSHOOT), keyframe(12, 1f, Easing.SMOOTH))
        .build();

    /** Dos olhos (um pouco abaixo, para não sair do meio da tela) até o bloco mirado, a até 48 blocos. */
    static final Beam FEIXE = Beam.builder(Point.anchorEye(0, -.3, 0), Point.anchorAim(48))
        .width(.12f, .9f)
        .color(0xffffffff, 0xffff3050)
        .pulses(4, 2.5f, 1.5f)
        .noise(.04f)                            // estala de leve
        .track(Beam.Param.REVEAL, "disparo")    // o tiro sai dos olhos e chega ao alvo em 4 ticks
        .track(Beam.Param.INTENSITY, "carga")
        .build();

    /** Brilho no ponto de impacto, só depois que o tiro chega (fase "acertou"). */
    static final Light ALVO = Light.point(Point.anchorAim(48)).color(0xffff5060).intensity(2.5f).radius(5)
        .flicker(.3f, 10).weightPhase("acertou").build();

    /** Luz fraca ao longo do feixe. */
    static final Light TRILHA = Light.along(FEIXE).radius(3).intensity(1.2f).build();

    private LaserMira() {}

    public static void register() {
        // Raio 24 em volta do jogador: o frustum (60 blocos) cobre o feixe de 48.
        Effects.register(ID, new Effects.Definition(100, 24, 0xffff3050, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(ID, TIMELINE);
        Effects.beams(ID, FEIXE);
        Effects.lights(ID, ALVO, TRILHA);
        Effects.cue(ID, 0, ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 1f, 1.6f));
    }
}
```

## Como adaptar

- **Laser de um mob:** `Effects.follow(ID, mob, Vec3.ZERO)` com as mesmas pontas; a mira segue a cabeça do mob.
- **Do conjurador até um alvo fixo:** `Effects.start(ID, jogador, alvo)` e `Beam.builder(Point.casterEye(0, -.3, 0), Point.center())`.
- **Dano:** feixes são visuais; o dano vem de um cue no servidor que faz o próprio raio (`level.clip`) e aplica o efeito.

## O que conferir no jogo

1. O laser sai abaixo da mira e acompanha o movimento da câmera sem atraso visível; outro jogador vê o mesmo laser seguindo a cabeça de quem atira.
2. O ponto de impacto brilha e cintila na parede ou no chão; o feixe é escondido por blocos entre a câmera e ele.
3. Em primeira pessoa, o laser é um traço fino saindo de baixo da mira; de fora (F5 ou outro jogador), ele tem a largura real.
4. F7: "Feixes: 1", "Luzes: 2".
5. Mirando o céu, o feixe vai até 48 blocos e a luz de impacto fica no ar (sem superfície, não ilumina nada).
