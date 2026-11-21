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
