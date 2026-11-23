# Receita 19 — Meteoro que vira bloco real

**Objetivo.** O padrão **dublê → real** da 0.11.0: o objeto que cai é só desenho (uma esfera em chamas do passe `terrain` do motor, com rastro de fogo); no impacto, o servidor cava a cratera, queima a borda e coloca blocos **reais** no centro (uma esfera de ouro bruto). O dublê continua por cima dos blocos reais e esmaece na fase de revelação; o calor esfria por 12 segundos.

| | |
|---|---|
| Teste no jogo | `/vfxsample receita meteoro` (16 segundos); desfazer: `/calamidade terrain undo` |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/Meteoro.java` (sem shader); `Receitas.java` (comando) |
| Usa | `TerrainShape.crater` + `sphere` (união) com borda irregular, `TerrainEdit.Fill.solid`, `TerrainEdit.StandIn.sphere`, `Effects.Impact` com clarão tingido |
| Custo | servidor: ~1 900 blocos planejados num tick; cliente: 1 dublê e 1 zona quente no passe `terrain`, 2 luzes |

> **Caso de uso.** O preenchimento é a intenção deste efeito (o meteoro vira bloco real); uma cratera não precisa de `fill` nem de `burn`. Quando usar cada peça: [Escolher a operação](../../TERRENO-0.11.md#escolher-a-operação).

## A queda

`StandIn.sphere(2.5f).from(-18, 90, -30)` começa 90 blocos acima do ponto de impacto, 18 à esquerda e 30 atrás (no referencial da instância: gira com o yaw do jogador). A posição vai do início ao impacto entre `fall(4)` e o marcador, com aceleração (`t²`); uma trilha `track("queda")` de 0 a 1 pode substituir isso. O ponto de impacto é o centro do preenchimento (`Fill.at(0, -3.5f, 0)`), então a esfera desenhada pousa exatamente onde os blocos reais aparecem. `reveal(40)`: depois do commit, a esfera desenhada fica sobre os blocos reais e some em 40 ticks.

| Elemento | Valor | Para quê |
|---|---|---|
| fase `queda` | 0–60 | a queda |
| marcador `impacto` | tick 60 | commit; o `Impact` (7 ticks, clarão laranja) esconde a troca |
| fase `revelacao` | 60–110 | o dublê esmaece sobre os blocos reais |
| `CRATERA` | cratera 8 × 5 (perfil 2,2) + bolsão esférico de 3,5, borda irregular 1,2, casca de 2, esfera sólida de ouro bruto de raio 2,5, resfriamento 240 | o buraco, a borda queimada e o meteoro real |

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/Meteoro.java -->
```java
package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Placement;
import dev.calamidade_awakening.api.v1.TerrainEdit;
import dev.calamidade_awakening.api.v1.TerrainPalette;
import dev.calamidade_awakening.api.v1.TerrainShape;
import dev.calamidade_awakening.api.v1.Timeline;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.Optional;

/**
 * Receita 19 — meteoro: o dublê vira bloco real (docs/ai/receitas/19-meteoro.md), motor 0.11.0.
 * Uma esfera em chamas (dublê desenhado pelo passe terrain do motor) cai do céu com rastro de fogo. No impact frame,
 * o servidor cava a cratera, queima a borda e coloca uma esfera de blocos de ouro bruto no centro; o dublê fica sobre
 * os blocos reais e esmaece (revelação), e o calor esfria por 12 segundos.
 * Teste: /vfxsample receita meteoro (desfazer: /calamidade terrain undo)
 */
public final class Meteoro {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_meteoro");

    static final Timeline TIMELINE = Timeline.builder(320)
        .phase("queda", 0, 60, 4, 2)
        .phase("revelacao", 60, 110, 2, 20)
        .marker("impacto", 60)
        .build();

    /** Cratera de raio 8 e 5 de fundo, com um bolsão no centro; casca de 2 blocos; esfera de ouro bruto de raio 2,5. */
    static final TerrainEdit CRATERA = TerrainEdit.carve(TerrainShape.crater(8, 5).profile(2.2f)
            .union(TerrainShape.sphere(3.5f).offset(0, -3, 0)).rough(1.2f, 3))
        .commitAt("impacto")
        .burn(TerrainPalette.burnt(), 2)
        .fill(TerrainEdit.Fill.solid("minecraft:raw_gold_block", 2.5f).at(0, -3.5f, 0))
        .standIn(TerrainEdit.StandIn.sphere(2.5f).from(-18, 90, -30).fall(4).reveal(40).trail(22).color(0xffff7a24).build())
        .cooling(240)
        .build();

    private Meteoro() {}

    public static void register() {
        // Raio 40: o frustum (100 blocos) cobre a queda de 90 blocos. Clarão na cor do efeito no impacto.
        Effects.register(ID, new Effects.Definition(320, 40, 0xffff7a24, Effects.Shape.NONE, Effects.Material.NONE,
            null, null, new Effects.Impact(60, 7, .85f, Effects.ImpactStyle.TINTED_FLASH)).withRange(256));
        Effects.timeline(ID, TIMELINE);
        Effects.terrain(ID, CRATERA);
        Effects.cue(ID, 0, ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 3f, .5f));
        Effects.cue(ID, "impacto", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4f, .7f));
    }

    /** No chão, 22 blocos à frente do jogador; vem por trás e da esquerda dele (yaw do jogador). */
    static Optional<Effects.Handle> iniciar(ServerPlayer player) {
        return Effects.start(ID, player.serverLevel(),
            Placement.at(Receitas.chao(player, 22), player.getYRot(), 0).withCaster(player));
    }
}
```

## Variações

- **Casca em vez de esfera sólida:** `Fill.shell("minecraft:end_stone", 3)`: um meteoro oco.
- **Outro bloco:** qualquer ID do registro (`minecraft:magma_block`, um bloco do seu mod). Blocos com block entity entram sem dados.
- **Queda por trilha:** `StandIn.sphere(r).track("queda")` com uma trilha 0 → 1 que você controla (pausas, overshoot).
