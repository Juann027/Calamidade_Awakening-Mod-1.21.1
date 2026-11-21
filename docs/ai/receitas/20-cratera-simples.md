# Receita 20 — Cratera simples

**Objetivo.** A Terrain API no mínimo, para estudo: uma escavação em tigela com borda irregular e casca queimada, no marcador `impacto` (tick 10), sob um clarão curto. Sem dublê, sem feixe, sem luz própria: o calor sobre as faces da cratera e a luz laranja que esfria são do motor.

| | |
|---|---|
| Teste no jogo | `/vfxsample receita cratera_simples` (12 segundos); desfazer: `/calamidade terrain undo`; lista: `/calamidade terrain list` |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/CrateraSimples.java`; `Receitas.java` (comando) |
| Usa | `TerrainShape.crater(7, 4).rough(1, 3)`, `TerrainEdit.burn(TerrainPalette.burnt(), 2)`, calor padrão (200 ticks) |
| Custo | servidor: ~1 300 blocos planejados num tick; cliente: 1 zona quente e 1 luz |

## O que acontece no servidor

1. **Início da instância:** regras do servidor (`terrainEnabled`, dimensões, `mobGriefing` para fontes mob) e o plano numa thread de trabalho (forma, origem, orientação e seed; nenhum acesso ao mundo).
2. **Plano pronto (1 ou 2 ticks depois):** confere `terrainMaxVolume` e dispara o `TerrainEditEvent` uma vez, com a área; um mod de proteção pode cancelar.
3. **Marcador `impacto`:** escreve direto nas seções dos chunks, até `terrainBlocksPerTick` blocos por tick (padrão 16 384, dividido entre as edições), sem drops, vizinhos nem física. No fim de cada lote, cada seção alterada vai uma vez aos jogadores que a veem.
4. **Depois:** o snapshot compacto fica `terrainUndoMinutes` (10) para `/calamidade terrain undo`.

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/CrateraSimples.java -->
```java
package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.TerrainEdit;
import dev.calamidade_awakening.api.v1.TerrainPalette;
import dev.calamidade_awakening.api.v1.TerrainShape;
import dev.calamidade_awakening.api.v1.Timeline;
import net.minecraft.resources.ResourceLocation;

/**
 * Receita 20 — só a cratera (docs/ai/receitas/20-cratera-simples.md), motor 0.11.0, para estudo.
 * Escavação com borda irregular e casca queimada no marcador "impacto" (tick 10), sob um clarão curto; o calor padrão
 * (200 ticks) esfria. Sem dublê, sem feixe, sem luz própria: a luz que esfria é a do motor.
 * Teste: /vfxsample receita cratera_simples (desfazer: /calamidade terrain undo)
 */
public final class CrateraSimples {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_cratera_simples");

    static final Timeline TIMELINE = Timeline.builder(240).marker("impacto", 10).build();

    static final TerrainEdit CRATERA = TerrainEdit.carve(TerrainShape.crater(7, 4).rough(1, 3))
        .commitAt("impacto")
        .burn(TerrainPalette.burnt(), 2)
        .build();

    private CrateraSimples() {}

    public static void register() {
        // Raio 12: o frustum (30 blocos) cobre a cratera de 7 com a casca.
        Effects.register(ID, new Effects.Definition(240, 12, 0xffff5a14, Effects.Shape.NONE, Effects.Material.NONE,
            null, null, new Effects.Impact(10, 5, .7f, Effects.ImpactStyle.MONOCHROME)));
        Effects.timeline(ID, TIMELINE);
        Effects.terrain(ID, CRATERA);
    }
}
```

## Variações

- **Fundo plano:** `.profile(10)`. **Cone:** `.profile(1)`.
- **Tirar morros e árvores dentro da borda:** `.clearance(12)` (padrão: a profundidade).
- **Outra paleta:** `TerrainPalette.builder().add(Band.WALL, "minecraft:packed_ice", 3)...build()` (gelo, areia vitrificada, cristais do seu mod).
- **Poupar um pilar no meio:** `crater(7, 4).minus(TerrainShape.cylinder(1.5f, 12))`.
