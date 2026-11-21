# Receita 11 — Fogueira (só luz)

**Objetivo.** Uma luz pontual laranja que cintila, sem geometria, partícula nem shader: o motor ilumina o chão e os blocos em volta no passe `lights`. É um efeito ambiente com sustain, que fica aceso até o release.

| | |
|---|---|
| Teste no jogo | `/vfxsample receita fogueira` de noite (5 blocos à frente); depois `/vfxsample receita release` |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/Fogueira.java` (sem shader) |
| Usa | `Light.point`, `falloff`, `flicker`, `track(Light.Param.WEIGHT, ...)`, sustain com marcadores no loop |
| Custo | 1 luz no passe `lights` |

**Luz visual.** O nível de luz do jogo não muda: mobs continuam nascendo ali e plantas não crescem por causa dela. Ela clareia o que a câmera vê, só nas faces voltadas para a fonte, e não faz sombra.

## Timeline e luz

| Elemento | Valor | Para quê |
|---|---|---|
| trilha `chama` | 0 → 1 (30, EASE_OUT), 1 em 40 e 160, 0 em 200 (SMOOTH) | `WEIGHT`: acende, fica no loop sem salto, apaga no release |
| `sustain(40, 160)` | loop de 6 s | fica acesa até `release()` |
| marcadores `estalo` (70) e `estalo_2` (125) | dentro do loop | som de fogueira a cada volta |
| luz | `center(0, .6, 0)`, cor 0xffff9a40, intensidade 3, alcance 10, queda 1,2, `flicker(.35f, 6)` | fogo que cintila 6 vezes por segundo, até 35% |

**Acessibilidade.** A cintilação segue a intensidade dos efeitos de tela (0 deixa a luz firme) e cai para 1/4 com "ocultar clarões".

## Código Java

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/Fogueira.java -->
```java
package example.vfxsample.receitas;

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
 * Receita 11 — fogueira (docs/ai/receitas/11-fogueira.md).
 * Só uma luz pontual laranja que cintila, sem geometria, partícula ou shader: o motor ilumina o chão e os blocos em
 * volta no passe "lights". Efeito ambiente com sustain: fica acesa até /vfxsample receita release.
 * É luz visual: o nível de luz do jogo não muda (mobs continuam nascendo ali).
 * Teste: /vfxsample receita fogueira, de noite; depois /vfxsample receita release
 */
public final class Fogueira {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_fogueira");

    static final Timeline TIMELINE = Timeline.builder(200)
        .track("chama", keyframe(0, 0f), keyframe(30, 1f, Easing.EASE_OUT), keyframe(40, 1f), keyframe(160, 1f),
            keyframe(200, 0f, Easing.SMOOTH))
        .marker("estalo", 70)
        .marker("estalo_2", 125)
        .sustain(40, 160)                       // loop de 6 s; release apaga em 2 s
        .build();

    /** 0,6 bloco acima do centro, alcance 10, queda suave, cintilação de 35% (6 vezes por segundo). */
    static final Light CHAMA = Light.point(Point.center(0, .6, 0))
        .color(0xffff9a40)
        .intensity(3f)
        .radius(10)
        .falloff(1.2f)
        .flicker(.35f, 6)
        .track(Light.Param.WEIGHT, "chama")
        .build();

    private Fogueira() {}

    public static void register() {
        // Raio 4: o teste de frustum (10 blocos) cobre o alcance da luz.
        Effects.register(ID, new Effects.Definition(200, 4, 0xffff9a40, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(ID, TIMELINE);
        Effects.lights(ID, CHAMA);
        // Dentro do loop: estalam a cada volta.
        Effects.cue(ID, "estalo", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 1.5f, 1f));
        Effects.cue(ID, "estalo_2", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 1.2f, .9f));
    }
}
```

## Como adaptar

- **Calibre:** de noite, intensidade 2 a 3, alcance 8 a 12 e queda 1,2 a 2 ficam legíveis; de dia a mesma luz quase não aparece (como uma tocha de dia).
- **Luz presa a uma entidade:** `Light.point(Point.anchor(0, 1, 0))` com `Effects.follow`.
- **Cor que muda:** a cor é constante; para trocar de cor, use duas luzes com `weightPhase` diferentes.

## O que conferir no jogo

1. De noite, o chão e os blocos até ~10 blocos ficam laranja, mais forte perto do centro, cintilando.
2. Faces de blocos de costas para a luz ficam escuras; o céu nunca é iluminado.
3. Mobs continuam nascendo em volta (luz visual).
4. F7: "Luzes: 1 desenhadas · passe comum"; nenhum feixe, vértice ou passe de addon.
5. Intensidade dos efeitos de tela 0: a luz fica firme. "Ocultar clarões": a cintilação quase some.
6. `release`: apaga em 2 segundos.
