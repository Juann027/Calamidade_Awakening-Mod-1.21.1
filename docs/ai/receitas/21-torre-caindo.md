# Receita 21 — Torre que cai e fica

**Objetivo.** Uma construção que cai do céu, destrói o entorno e fica posicionada no mundo. A estrutura é um template `.nbt` comum (`data/vfxsample/structure/torre.nbt`, uma torre de pedra 5 × 10 × 5 feita para este exemplo por `scripts/gerar_torre.py`). Enquanto cai, ela é uma **malha** desenhada no cliente a partir dos blocos do template (montada uma vez e guardada em cache); no impacto, o servidor abre uma cratera sob ela, queima a borda e coloca a torre de verdade, enterrada 2 blocos e girada em passos de 90° pelo yaw do jogador. Poeira, luz e calor no pouso.

| | |
|---|---|
| Teste no jogo | `/vfxsample receita torre_caindo` (12 segundos; vire para vários lados e repita); desfazer: `/calamidade terrain undo` |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/TorreCaindo.java`; `data/vfxsample/structure/torre.nbt`; `scripts/gerar_torre.py` |
| Usa | `TerrainEdit.structure`, `structureOffset`, `StandIn.structure()`, cratera com `clearance` |
| Custo | servidor: ~700 blocos planejados num tick; cliente: 1 malha (vértices no F7), aura e rastro no passe `terrain`, 1 zona quente, 2 luzes |

> **Caso de uso.** A torre é uma construção que deve **ficar** no mundo. Destroços, poeira ou fogo que só precisam aparecer durante a cena não são estruturas: use o dublê, a poeira do motor, feixes, luz ou camadas do addon. Quando usar cada peça: [Escolher a operação](../../TERRENO-0.11.md#escolher-a-operação).

## Estruturas

| Regra | Detalhe |
|---|---|
| Onde fica | O centro horizontal do template (girado) vai na origem, a camada mais baixa no nível da origem, mais `structureOffset(direita, cima, frente)` no referencial do yaw arredondado para 90°. |
| Rotação | `round(yaw / 90)` passos no sentido horário (yaw 90, olhando para o oeste, é um passo): posições e estados (`BlockState.rotate`) giram juntos. |
| Tamanho | Até `terrainMaxStructure` (padrão 32) por eixo; maior que isso, a edição é recusada. |
| Ar do template | Blocos de ar salvos no template limpam o interior (a torre não fica cheia de terra); `structure_void` é ignorado. |
| Block entities | Blocos com dados no template (o barril com 3 pães) recebem os dados. |
| Dublê | Os blocos girados vão ao cliente uma vez (canal `terrain_v1`); a malha é desenhada depois dos blocos translúcidos, 1/2/4 por quadro (LOW/BALANCED/HIGH), e esmaece sobre a torre real (`reveal`). |

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/TorreCaindo.java -->
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
 * Receita 21 — torre que cai e fica (docs/ai/receitas/21-torre-caindo.md), motor 0.11.0.
 * A estrutura data/vfxsample/structure/torre.nbt (uma torre de pedra 5 × 11 × 5 feita para este exemplo) cai como
 * malha de blocos (dublê de estrutura, montada uma vez no cliente). No impacto o servidor abre uma cratera sob ela,
 * queima a borda e coloca a torre de verdade, enterrada 2 blocos, girada em passos de 90 graus pelo yaw do jogador;
 * poeira, luz e calor no pouso. A torre fica no mundo.
 * Teste: /vfxsample receita torre_caindo (desfazer: /calamidade terrain undo)
 */
public final class TorreCaindo {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_torre_caindo");

    static final Timeline TIMELINE = Timeline.builder(240)
        .phase("queda", 0, 50, 2, 2)
        .marker("impacto", 50)
        .build();

    static final TerrainEdit TORRE = TerrainEdit.carve(TerrainShape.crater(6.5f, 3).clearance(12).rough(.8f, 2.5f))
        .commitAt("impacto")
        .burn(TerrainPalette.burnt(), 1)
        .structure("vfxsample:torre")
        .structureOffset(0, -2, 0)
        .standIn(TerrainEdit.StandIn.structure().from(8, 70, -24).fall(4).reveal(30).trail(14).color(0xffffa040).build())
        .cooling(160)
        .smoke(.7f)
        .build();

    private TorreCaindo() {}

    public static void register() {
        // Raio 32: o frustum (80 blocos) cobre a queda de 70 blocos.
        Effects.register(ID, new Effects.Definition(240, 32, 0xffffa040, Effects.Shape.NONE, Effects.Material.NONE,
            null, null, new Effects.Impact(50, 6, .8f, Effects.ImpactStyle.TINTED_FLASH)).withRange(256));
        Effects.timeline(ID, TIMELINE);
        Effects.terrain(ID, TORRE);
        Effects.cue(ID, "impacto", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 4f, .5f));
    }

    /** No chão, 20 blocos à frente do jogador, virada pelo yaw dele. */
    static Optional<Effects.Handle> iniciar(ServerPlayer player) {
        return Effects.start(ID, player.serverLevel(),
            Placement.at(Receitas.chao(player, 20), player.getYRot(), 0).withCaster(player));
    }
}
```

## Variações

- **Sua construção:** salve com um bloco de estrutura (`/give @s structure_block`) e coloque em `data/<seu_mod>/structure/<nome>.nbt`; use `structure("seu_mod:nome")`.
- **Pousar sem cratera:** uma forma pequena (`TerrainShape.sphere(1)`), já que a estrutura sobrescreve tudo onde tem blocos.
- **Dublê esférico em vez da malha:** `StandIn.sphere(r)` (por exemplo, uma cápsula de pouso que vira um abrigo).
