# Receita 17 — Zumbi com ataque de feixe (gatilho de entidade)

**Objetivo.** Dar a um mob vanilla um ataque com efeito usando só o gatilho de entidade da 0.10.0 (`EffectTriggers.entity`) e um evento do NeoForge, sem mixin nem GeckoLib: quando um zumbi causa dano, o efeito nasce preso ao zumbi e virado para a vítima naquele instante, e o feixe sai do peito para a frente.

| | |
|---|---|
| Teste no jogo | Em sobrevivência, `/summon zombie` perto de você (ou perto de um aldeão); `/vfxsample zumbi off` desliga |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/ZumbiFeixe.java` |
| Usa | `EffectTriggers.entity(...).offset(...).cooldown(20)`, `fire(zumbi, vítima)`, `LivingDamageEvent.Post`, `Point.facing`, `Light.along` |
| Custo | 1 feixe e 1 luz por golpe; fonte ENTITY: no máximo 4 instâncias por zumbi e as 48 vagas que não são reservadas a jogadores |

## O gatilho de entidade

| Chamada | Orientação | Para quê |
|---|---|---|
| `fire(mob)` | yaw do corpo, seguido a cada tick e quadro (`followBody(true)`, padrão) | auras, investidas, golpes em área na frente do mob |
| `fire(mob, alvo)` | virado do centro para o meio do alvo no instante do golpe; continua preso ao mob | golpes direcionados; o offset fica nivelado (não inclina com a mira) |

A fonte é o próprio mob, então o limite por mob (4) e a recarga por mob (20 ticks aqui) evitam que uma horda lote o servidor; quem joga continua tendo as 16 vagas reservadas.

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/ZumbiFeixe.java -->
```java
package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.EffectTriggers;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 17 — zumbi vanilla com um ataque de feixe ao bater (docs/ai/receitas/17-zumbi.md), motor 0.10.0.
 * Quando um zumbi (ou husk, afogado, aldeão-zumbi) causa dano, o gatilho de entidade prende o efeito ao zumbi e o
 * vira para a vítima naquele instante: o feixe sai do peito e vai 3,5 blocos para a frente (Point.facing). A fonte é o
 * zumbi (no máximo 4 instâncias por mob) e a recarga é de 20 ticks por zumbi, então uma horda não lota o servidor.
 * Sem mixin nem GeckoLib: só o evento LivingDamageEvent.Post do NeoForge. /vfxsample zumbi off desliga.
 * Teste: /summon zombie perto de um aldeão (ou em sobrevivência, deixe um zumbi bater em você)
 */
public final class ZumbiFeixe {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "zumbi_feixe");

    static final Timeline TIMELINE = Timeline.builder(24)
        .track("golpe", keyframe(0, 0f), keyframe(3, 1f, Easing.EASE_OUT))
        .track("brilho", keyframe(0, 1.4f), keyframe(24, 0f, Easing.EASE_IN))
        .build();

    /** Do centro (peito do zumbi) até 3,5 blocos à frente, na direção da vítima. */
    static final Beam FEIXE = Beam.builder(Point.center(), Point.facing(0, 0, 3.5))
        .width(.12f, .8f)
        .color(0xffe8ffe0, 0xff60ff40)
        .pulses(2, 2f, 1f)
        .noise(.06f)
        .track(Beam.Param.REVEAL, "golpe")
        .track(Beam.Param.INTENSITY, "brilho")
        .build();

    static final Light LUZ = Light.along(FEIXE).radius(3).intensity(1.8f).build();

    /** Peito, um pouco à frente; recarga de 20 ticks por zumbi. */
    static final EffectTriggers.EntityTrigger GATILHO = EffectTriggers.entity(ID).offset(0, 1.25, .35).cooldown(20);

    /** Ligado por padrão; /vfxsample zumbi on|off. */
    static boolean ativo = true;

    private ZumbiFeixe() {}

    public static void register() {
        // Raio 3: o frustum (7,5 blocos) cobre o feixe de 3,5.
        Effects.register(ID, new Effects.Definition(24, 3, 0xff60ff40, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(ID, TIMELINE);
        Effects.beams(ID, FEIXE);
        Effects.lights(ID, LUZ);
        Effects.cue(ID, 0, ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, .9f, 1.3f));
        NeoForge.EVENT_BUS.addListener(ZumbiFeixe::dano);
    }

    /** Servidor: o zumbi acabou de causar dano. */
    private static void dano(LivingDamageEvent.Post event) {
        if (ativo && event.getSource().getEntity() instanceof Zombie zumbi && event.getEntity() != zumbi)
            GATILHO.fire(zumbi, event.getEntity());
    }
}
```

## Mobs animados (GeckoLib ou outro rig)

Os keyframes de animação rodam no cliente; o efeito começa no servidor. Dispare o gatilho no servidor no mesmo tick em que a animação de ataque começa (com GeckoLib, onde o mod chama `triggerAnim`) e escreva a Timeline do efeito com os tempos da animação: um marcador no keyframe do golpe para o cue de dano e fases para preparação e recuperação. O passo a passo, com código, está em [docs/INTEGRACAO-0.10.md](../../INTEGRACAO-0.10.md).

## O que conferir no jogo

1. Cada golpe do zumbi solta um feixe verde curto do peito na sua direção, com som.
2. Vários zumbis batendo ao mesmo tempo não passam de 4 instâncias cada (F7 e `/calamidade status`: "Fontes: … mobs").
3. `/vfxsample zumbi off` para os feixes; `on` liga de novo.
