# Receita 16 — Pilares com efeito (gatilho de block entity)

**Objetivo.** Blocos que disparam efeitos com o gatilho de block entity da 0.10.0 (`EffectTriggers.blockEntity`): um pilar que pulsa com redstone, virado pela propriedade `facing`, e um que pulsa sozinho a cada 3 segundos. O componente lê o sinal, decide quando começar e soltar, e prende a instância ao bloco: quebrar o pilar ou descarregar o chunk encerra o efeito na hora, sem código no `setRemoved`.

| | |
|---|---|
| Teste no jogo | `/give @s vfxsample:pilar_redstone` e `vfxsample:pilar_periodico` (ou a aba Redstone do criativo); coloque e ligue uma alavanca ao lado do primeiro |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/Pilares.java`; blockstates, modelos (texturas vanilla de quartzo por nome) e loot tables em `examples/vfx-sample/src/main/resources/` |
| Usa | `EffectTriggers.blockEntity(...).redstone().period(20)`, `.periodic(60)`, `BlockTrigger.Component`, `Point.facing`, `Light.ring` e `Light.point` |
| Custo | pulso: 2 feixes e 1 luz; ritmo: 1 feixe e 1 luz; no máximo 2 instâncias por bloco (padrão do servidor) |

## Modos do gatilho de bloco

| Modo | Quando começa | Efeito com sustain | `period` |
|---|---|---|---|
| `PERIODIC` | a cada `period` ticks, desde o primeiro tick | começa uma vez e fica preso | 1..72000 |
| `REDSTONE` | na borda de subida do sinal (`hasNeighborSignal`) | preso enquanto ligado; `release()` ao desligar | 0 = uma vez por borda; mais = repete enquanto ligado |
| `PROXIMITY` | quando um jogador (não espectador) entra no raio | preso enquanto houver jogador; `release()` quando sai | como no REDSTONE |

O centro fica na base do bloco mais `offset(direita, cima, frente)` (padrão `0, 1, 0`, o topo), virado pela propriedade `facing`/`horizontal_facing` do bloco (ou por `yaw(...)` fixo). Uma partida recusada pelo servidor (recarga ou limites) é tentada de novo no máximo a cada `max(period, 20)` ticks.

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/Pilares.java -->
```java
package example.vfxsample.receitas;

import com.mojang.serialization.MapCodec;
import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.EffectTriggers;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 16 — pilares com efeito (docs/ai/receitas/16-pilares.md), motor 0.10.0.
 * Dois blocos com block entity e o gatilho de bloco do motor (EffectTriggers.blockEntity):
 * <ul>
 * <li>vfxsample:pilar_redstone pulsa na borda de subida do sinal de redstone e de novo a cada segundo enquanto
 * estiver ligado; o pulso tem um feixe vertical e um inclinado para a frente do bloco (a face virada para quem o
 * colocou: o gatilho usa a propriedade facing como yaw).</li>
 * <li>vfxsample:pilar_periodico pulsa sozinho a cada 3 segundos.</li>
 * </ul>
 * A instância fica presa ao bloco: quebrar o pilar ou descarregar o chunk encerra o efeito na hora. A fonte é o bloco
 * (no máximo 2 instâncias por bloco, pelo padrão do servidor).
 * Teste: /give @s vfxsample:pilar_redstone e vfxsample:pilar_periodico; coloque, ligue uma alavanca ao lado do primeiro
 */
public final class Pilares {
    public static final ResourceLocation PULSO_ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "pilar_pulso");
    public static final ResourceLocation RITMO_ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "pilar_ritmo");

    static final Timeline PULSO_TIMELINE = Timeline.builder(30)
        .track("subir", keyframe(0, 0f), keyframe(6, 1f, Easing.EASE_OUT))
        .track("anel", keyframe(0, .5f), keyframe(24, 6f, Easing.EASE_OUT))
        .track("brilho", keyframe(0, 1f), keyframe(30, 0f, Easing.EASE_IN))
        .build();

    /** O centro fica no topo do bloco (offset 0, 1, 0 do gatilho); o feixe sobe 8 blocos. */
    static final Beam PULSO_VERTICAL = Beam.builder(Point.center(), Point.center(0, 8, 0))
        .width(.2f, 1.2f)
        .color(0xffffffff, 0xffff3030)
        .track(Beam.Param.REVEAL, "subir")
        .track(Beam.Param.INTENSITY, "brilho")
        .build();

    /** Inclinado para a frente do bloco: Point.facing gira com o facing do pilar. */
    static final Beam PULSO_FRENTE = Beam.builder(Point.center(), Point.facing(0, 4, 4))
        .width(.1f, .7f)
        .color(0xffffffff, 0xffff6040)
        .pulses(2, 1f, 1f)
        .track(Beam.Param.REVEAL, "subir")
        .track(Beam.Param.INTENSITY, "brilho")
        .build();

    static final Light PULSO_ANEL = Light.ring(Point.center(0, -1, 0), .5f, 1f).color(0xffff3030).intensity(2.2f)
        .track(Light.Param.RADIUS, "anel").track(Light.Param.WEIGHT, "brilho").build();

    static final Timeline RITMO_TIMELINE = Timeline.builder(50)
        .phase("pulso", 0, 50, 8, 30)
        .track("subir", keyframe(0, 0f), keyframe(12, 1f, Easing.SMOOTH))
        .build();

    static final Beam RITMO_FEIXE = Beam.builder(Point.center(), Point.center(0, 5, 0))
        .width(.15f, .9f)
        .color(0xffffffff, 0xff40a0ff)
        .pulses(1, .4f, 1.5f)
        .track(Beam.Param.REVEAL, "subir")
        .weightPhase("pulso")
        .build();

    static final Light RITMO_LUZ = Light.point(Point.center(0, .5, 0)).color(0xff40a0ff).intensity(2f).radius(6)
        .weightPhase("pulso").build();

    /** Gatilhos de bloco: imutáveis, criados uma vez; cada block entity guarda o seu componente. */
    static final EffectTriggers.BlockTrigger REDSTONE = EffectTriggers.blockEntity(PULSO_ID).redstone().period(20);
    static final EffectTriggers.BlockTrigger PERIODICO = EffectTriggers.blockEntity(RITMO_ID).periodic(60);

    static final DeferredRegister.Blocks BLOCOS = DeferredRegister.createBlocks("vfxsample");
    static final DeferredRegister.Items ITENS = DeferredRegister.createItems("vfxsample");
    static final DeferredRegister<BlockEntityType<?>> TIPOS = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "vfxsample");

    public static final DeferredBlock<Pilar> PILAR_REDSTONE = BLOCOS.registerBlock("pilar_redstone",
        p -> new Pilar(p, true), BlockBehaviour.Properties.ofFullCopy(Blocks.CHISELED_QUARTZ_BLOCK));
    public static final DeferredBlock<Pilar> PILAR_PERIODICO = BLOCOS.registerBlock("pilar_periodico",
        p -> new Pilar(p, false), BlockBehaviour.Properties.ofFullCopy(Blocks.QUARTZ_PILLAR));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PilarEntidade>> TIPO = TIPOS.register("pilar",
        () -> BlockEntityType.Builder.of(PilarEntidade::new, PILAR_REDSTONE.get(), PILAR_PERIODICO.get()).build(null));

    static {
        ITENS.registerSimpleBlockItem(PILAR_REDSTONE);
        ITENS.registerSimpleBlockItem(PILAR_PERIODICO);
    }

    /** Bloco com facing horizontal (virado para quem o colocou) e block entity que só tica no servidor. */
    public static final class Pilar extends BaseEntityBlock {
        final boolean redstone;
        private final MapCodec<Pilar> codec;

        Pilar(BlockBehaviour.Properties properties, boolean redstone) {
            super(properties);
            this.redstone = redstone;
            this.codec = simpleCodec(p -> new Pilar(p, redstone));
            registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.NORTH));
        }

        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }

        @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(BlockStateProperties.HORIZONTAL_FACING);
        }

        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PilarEntidade(pos, state); }

        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return level.isClientSide() ? null : createTickerHelper(type, TIPO.get(), PilarEntidade::tick);
        }
    }

    /** Block entity com o componente do gatilho; o componente não precisa de NBT nem de setRemoved. */
    public static final class PilarEntidade extends BlockEntity {
        private final EffectTriggers.BlockTrigger.Component efeito;

        public PilarEntidade(BlockPos pos, BlockState state) {
            super(TIPO.get(), pos, state);
            boolean redstone = state.getBlock() instanceof Pilar pilar && pilar.redstone;
            this.efeito = (redstone ? REDSTONE : PERIODICO).attach(this);
        }

        static void tick(Level level, BlockPos pos, BlockState state, PilarEntidade self) { self.efeito.tick(); }
    }

    private Pilares() {}

    /** Construtor comum do mod: efeitos e registros (blocos, itens e o tipo da block entity). */
    public static void register(IEventBus bus) {
        Effects.register(PULSO_ID, new Effects.Definition(30, 4, 0xffff3030, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(PULSO_ID, PULSO_TIMELINE);
        Effects.beams(PULSO_ID, PULSO_VERTICAL, PULSO_FRENTE);
        Effects.lights(PULSO_ID, PULSO_ANEL);
        Effects.cue(PULSO_ID, 0, ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, .7f, 1.2f));

        Effects.register(RITMO_ID, new Effects.Definition(50, 3, 0xff40a0ff, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(RITMO_ID, RITMO_TIMELINE);
        Effects.beams(RITMO_ID, RITMO_FEIXE);
        Effects.lights(RITMO_ID, RITMO_LUZ);

        BLOCOS.register(bus);
        ITENS.register(bus);
        TIPOS.register(bus);
    }
}
```

## Como adaptar

- **Aproximação:** `EffectTriggers.blockEntity(ID).proximity(6)`; com um efeito de sustain (receita 6), o bloco fica aceso enquanto alguém está perto.
- **Recarga por bloco:** `.cooldown(ticks)`; a recarga é do bloco (dimensão e posição), não do jogador.
- **Bloco sem facing:** o yaw é 0 (sul); use `.yaw(graus)` para fixar outro.
- **Parar à mão:** `componente.stop()`; o vínculo com o bloco continua valendo para quebra e descarregamento.

## O que conferir no jogo

1. O pilar periódico pulsa a cada 3 segundos sozinho.
2. Ligar a alavanca faz o pilar de redstone pulsar na hora e de novo a cada segundo; o feixe inclinado vai para a face que estava virada para você ao colocar o bloco.
3. Desligar para os pulsos; quebrar o pilar no meio de um pulso apaga o efeito na hora.
4. Ao se afastar até o chunk descarregar, `/calamidade status` não mostra instâncias de bloco; ao voltar, o periódico volta a pulsar.
5. F7: "Fontes: … blocos".
