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
