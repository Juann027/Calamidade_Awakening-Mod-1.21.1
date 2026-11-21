# Receita 14 — Leque virado para onde o jogador olha

**Objetivo.** Mostrar a orientação por instância da 0.10.0 sem gatilho nenhum: o mesmo efeito, iniciado com o yaw do jogador, abre um leque de cinco feixes sempre à frente dele. Os feixes são escritos com `Point.facing(direita, cima, frente)`, e a `Placement` dá o referencial; sem orientação, a "frente" seria o sul.

| | |
|---|---|
| Teste no jogo | `/vfxsample receita leque` (3 segundos; vire para vários lados e repita) |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/LequeOrientado.java` (sem shader); `Receitas.java` (comando) |
| Usa | `Placement.at(posição, yaw, 0)`, `Point.facing`, 5 feixes com revelação, `Light.point` na ponta do feixe central |
| Custo | 5 feixes e 1 luz (dois passes do motor: `beams` e `lights`) |

## O referencial

| Eixo local | `Point.center(x, y, z)` | `Point.facing(direita, cima, frente)` | Sem orientação (yaw 0) |
|---|---|---|---|
| esquerda | `+x` | `-direita` | leste (+X) |
| cima | `+y` | `cima` | cima |
| frente | `+z` | `frente` | sul (+Z) |

Yaw e pitch seguem a convenção do Minecraft: yaw 0 olha para o sul, 90 para o oeste, -90 para o leste, 180 para o norte; pitch positivo olha para baixo. `Point.facing(0, 1, 14)` é "14 blocos à frente, 1 acima do centro": com o yaw do jogador, é para onde ele olha.

## Timeline, feixes e luz

| Elemento | Valor | Para quê |
|---|---|---|
| trilha `abrir` | 0 → 1 (10, EASE_OUT) | `REVEAL`: os feixes crescem a partir do jogador |
| fase `abrir` | 0–60, entrada 2, saída 20 | peso dos feixes e da luz |
| feixes | `facing(0, 1, .5)` → `facing(sen(a)·14, 1, cos(a)·14)` para a = -40°, -20°, 0°, 20°, 40° | o leque; o central é dourado, os outros azuis |
| `PONTA` | `Light.point(Point.facing(0, 1, 14))`, alcance 6 | luz na ponta do feixe central |

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/LequeOrientado.java -->
```java
package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Placement;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.Optional;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 14 — leque de feixes virado para onde o jogador olha (docs/ai/receitas/14-leque.md), motor 0.10.0.
 * Os cinco feixes são escritos com Point.facing(direita, cima, frente): o mesmo efeito, iniciado com o yaw do jogador
 * (Placement.at(posição, yaw, 0)), abre sempre à frente dele. Sem orientação, a frente seria o sul.
 * Teste: /vfxsample receita leque (vire para vários lados e repita)
 */
public final class LequeOrientado {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_leque");

    static final Timeline TIMELINE = Timeline.builder(60)
        .phase("abrir", 0, 60, 2, 20)
        .track("abrir", keyframe(0, 0f), keyframe(10, 1f, Easing.EASE_OUT))
        .build();

    /** Ângulos do leque, em graus a partir da frente (negativo à esquerda). */
    static final double[] ANGULOS = {-40, -20, 0, 20, 40};
    static final double ALCANCE = 14;

    static Beam feixe(double graus) {
        double a = Math.toRadians(graus);
        return Beam.builder(Point.facing(0, 1, .5), Point.facing(Math.sin(a) * ALCANCE, 1, Math.cos(a) * ALCANCE))
            .width(.1f, .7f)
            .color(0xffffffff, graus == 0 ? 0xffffd040 : 0xff40c0ff)
            .pulses(2, 1.2f, 1.5f)
            .track(Beam.Param.REVEAL, "abrir")   // os feixes crescem a partir do jogador
            .weightPhase("abrir")
            .build();
    }

    static final Beam[] FEIXES = {feixe(ANGULOS[0]), feixe(ANGULOS[1]), feixe(ANGULOS[2]), feixe(ANGULOS[3]), feixe(ANGULOS[4])};

    /** Luz na ponta do feixe central, que também gira com o leque. */
    static final Light PONTA = Light.point(Point.facing(0, 1, ALCANCE)).color(0xffffd040).intensity(2.2f).radius(6)
        .weightPhase("abrir").build();

    private LequeOrientado() {}

    public static void register() {
        // Raio 8: o frustum (20 blocos) cobre o leque de 14.
        Effects.register(ID, new Effects.Definition(60, 8, 0xff40c0ff, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(ID, TIMELINE);
        Effects.beams(ID, FEIXES);
        Effects.lights(ID, PONTA);
        Effects.cue(ID, 0, ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, .8f, 1.6f));
    }

    /** Começa aos pés do jogador, virado com o yaw dele (pitch 0: o leque fica na horizontal). */
    static Optional<Effects.Handle> iniciar(ServerPlayer player) {
        return Effects.start(ID, player.serverLevel(), Placement.at(player.position(), player.getYRot(), 0).withCaster(player));
    }
}
```

## Como adaptar

- **Com pitch:** `Placement.at(olhos, jogador.getYRot(), jogador.getXRot())`, ou `Placement.at(olhos).withLookOf(jogador)`: o leque inclina com o olhar.
- **Preso ao jogador:** `Placement.on(jogador).followingBody()`: o centro segue os pés e o yaw segue o corpo a cada quadro.
- **Na direção de um alvo:** `Placement.at(posição).facing(alvo)`.
- **Dano no leque:** num cue, `ctx.facing(direita, cima, frente)` e `ctx.forward()` dão as mesmas direções no servidor.

## O que conferir no jogo

1. Virado para o sul, o leste e o noroeste, o leque sempre abre à frente; o feixe central aponta para onde a mira aponta (na horizontal).
2. Outro jogador vê o leque na mesma direção (a orientação vai no pacote de início).
3. F7: "Feixes: 5", "Luzes: 1", "Fontes: 1 jogadores".
