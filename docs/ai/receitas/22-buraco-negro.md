# Receita 22 — Buraco negro no céu

**Objetivo.** Um buraco negro que surge no céu, distorce o céu e o terreno distante ao redor, cresce e colapsa num clarão — sem shader próprio: uma camada `Sky.blackHole()` com trilhas e uma atmosfera de crepúsculo.

| | |
|---|---|
| Teste no jogo | `/vfxsample receita buraco_negro` (20 s), de preferência no fim da tarde ou à noite, com montanhas à frente |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/BuracoNegro.java` (sem shader) |
| Usa | `Sky.blackHole()`, `centerOffset` (ponto muito longe no referencial virado pelo yaw), trilhas `SIZE`, `PROGRESS`, `LENS`; `Atmosphere` com `WEIGHT`; `Effects.Impact`; HERO |
| Custo | 1 camada no passe `sky` (fora do orçamento de addons) com 1 lente; o passe de atmosfera calcula a mesma lente |

## Timeline

| Trilha ou marcador | Valores | Para quê |
|---|---|---|
| `tamanho` | 0 → 22 (80) → 64 (300) → 70 (330) → 0 (352) | surge, cresce e colapsa (graus de diâmetro do disco) |
| `disco` | 0 → 1 (110) … 1 (330) → 0,4 | o disco de acreção acende |
| `lente` | 0,2 → 0,6 → 0,9 → 0 | a distorção cresce com o buraco e some no colapso |
| `ceu` | peso da atmosfera | crepúsculo com estrelas para a lente ter o que entortar |
| `colapso` (350) | marcador + `Impact(350, 8, .9)` | clarão do colapso (some com "ocultar clarões") |

**Onde fica no céu.** `centerOffset(0, 450, 800)`: 800 blocos à frente e 450 acima do centro, no referencial da instância. A receita começa com `Placement.at(posição, yaw do jogador, 0)`, então o buraco nasce à frente de quem chamou; longe assim, ele quase não muda de lugar enquanto o jogador anda. Para um lugar fixo no céu, use `direction(azimute, elevação)`.

## Código Java

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/BuracoNegro.java -->
```java
package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Atmosphere;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Placement;
import dev.calamidade_awakening.api.v1.Sky;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.Optional;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 22 — buraco negro no céu (docs/ai/receitas/22-buraco-negro.md).
 * Uma camada BLACK_HOLE presa a um ponto muito longe à frente de quem chamou (centerOffset no referencial da
 * instância, virado com o yaw do jogador): surge pequeno, o disco acende, cresce até cobrir boa parte do céu
 * entortando o céu e o terreno distante com a lente, e colapsa num clarão (Impact). Atmosfera de crepúsculo e
 * estrelas para a lente ter o que entortar. HERO: o protagonista da cena.
 * Teste: /vfxsample receita buraco_negro (20 s); F7 "Céu: 1 camadas · passe sim".
 */
public final class BuracoNegro {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_buraco_negro");

    static final Timeline TIMELINE = Timeline.builder(400)
        .phase("presenca", 0, 400, 40, 30)
        .track("tamanho", keyframe(0, 0f), keyframe(80, 22f, Easing.EASE_OUT), keyframe(300, 64f, Easing.SMOOTH),
            keyframe(330, 70f), keyframe(352, 0f, Easing.EASE_IN))
        .track("disco", keyframe(20, 0f), keyframe(110, 1f, Easing.SMOOTH), keyframe(330, 1f), keyframe(352, .4f))
        .track("lente", keyframe(0, .2f), keyframe(120, .6f, Easing.SMOOTH), keyframe(330, .9f), keyframe(352, 0f))
        .track("ceu", keyframe(0, 0f), keyframe(100, 1f, Easing.SMOOTH), keyframe(352, 1f), keyframe(400, 0f, Easing.SMOOTH))
        .marker("colapso", 350)
        .build();

    /** 800 blocos à frente e 450 acima: longe o bastante para não mudar de lugar enquanto o jogador anda. */
    static final Sky BURACO = Sky.blackHole()
        .centerOffset(0, 450, 800)
        .tilt(80)
        .spin(1.4f)
        .track(Sky.Param.SIZE, "tamanho")
        .track(Sky.Param.PROGRESS, "disco")
        .track(Sky.Param.LENS, "lente")
        .build();

    static final Atmosphere CREPUSCULO = Atmosphere.builder()
        .sky(0xff06040e, 0xff5a2a40, .8f)
        .fog(0xff2a1424, .3f, 60, 240)
        .exposure(.85f)
        .stars(.9f)
        .track(Atmosphere.Param.WEIGHT, "ceu")
        .build();

    private BuracoNegro() {}

    public static void register() {
        // Alcance 1024: céu para todo jogador num raio de ~770 blocos (some suave até 1024). Impact no colapso.
        Effects.register(ID, new Effects.Definition(400, 40, 0xffffb070, Effects.Shape.NONE, Effects.Material.NONE,
            null, null, new Effects.Impact(350, 8, .9f, Effects.ImpactStyle.TINTED_FLASH))
            .withRange(1024).withPriority(Effects.Priority.HERO));
        Effects.timeline(ID, TIMELINE);
        Effects.atmosphere(ID, CREPUSCULO);
        Effects.sky(ID, BURACO); // depois da timeline: a camada lê trilhas
    }

    /** Começa virado para onde o jogador olha: o buraco nasce à frente dele. */
    static Optional<Effects.Handle> iniciar(ServerPlayer player) {
        return Effects.start(ID, player.serverLevel(), Placement.at(player.position(), player.getYRot(), 0).withCaster(player));
    }
}
```

## O que conferir no jogo

1. O buraco surge pequeno à frente, a sombra negra com um anel fino e muito brilhante colado nela; o céu em volta escurece de leve.
2. O disco largo atravessa a sombra de lado a lado, um lado mais claro; por cima da sombra aparece a metade de trás do disco dobrada.
3. Estrelas e cristas de montanhas distantes perto do buraco entortam em arco; o terreno a poucos blocos de você não entorta.
4. Vire de costas: o céu continua (F7 "Céu: 1 camadas · passe sim"); olhe de volta: nada saltou.
5. No colapso, o buraco encolhe a nada com um clarão; com "ocultar clarões", sem clarão.
6. "Efeitos de movimento" desligado: o disco para de girar e a lente fica com metade da força.
7. Em LOW o passe continua (1 lente); `/calamidade bench 60` em volta não corta o buraco (HERO, e o céu não depende do limite de desenhados).
