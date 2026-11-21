package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Placement;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.TerrainEdit;
import dev.calamidade_awakening.api.v1.TerrainPalette;
import dev.calamidade_awakening.api.v1.TerrainShape;
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
 * Receita 18 — raio que abre uma trincheira (docs/ai/receitas/18-raio-trincheira.md), motor 0.11.0.
 * Um raio desce do céu e varre o chão para a frente do jogador; no impact frame (marcador "impacto", com o clarão
 * monocromático da definição) o servidor troca os blocos de uma cápsula de 28 blocos por ar e queima a borda. Depois,
 * a faixa incandescente no fundo do rasgo e o calor esfriam. A forma gira com o yaw do jogador (Placement).
 * Teste: /vfxsample receita raio_trincheira (desfazer: /calamidade terrain undo)
 */
public final class RaioTrincheira {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_raio_trincheira");

    static final Timeline TIMELINE = Timeline.builder(260)
        .track("descer", keyframe(18, 0f), keyframe(26, 1f, Easing.EASE_IN))
        .track("varrer", keyframe(24, 0f), keyframe(34, 28f, Easing.EASE_IN_OUT))
        .track("raio", keyframe(18, 0f), keyframe(26, 1f), keyframe(40, 1f), keyframe(56, 0f, Easing.SMOOTH))
        .marker("impacto", 30)
        .build();

    /** Raio do céu até a ponta de trás do rasgo; a ponta de baixo varre 28 blocos para a frente (TO_Z). */
    static final Beam RAIO = Beam.builder(Point.facing(-6, 110, -24), Point.facing(0, 0, -14))
        .width(.5f, 4.5f)
        .color(0xffffffff, 0xff9fd0ff)
        .noise(1.6f)
        .track(Beam.Param.REVEAL, "descer")
        .track(Beam.Param.TO_Z, "varrer")
        .track(Beam.Param.WEIGHT, "raio")
        .build();

    /** Cápsula de 28 blocos e raio 2,5 pouco abaixo do chão, borda irregular; casca queimada de 2 blocos. */
    static final TerrainEdit TRINCHEIRA = TerrainEdit.carve(TerrainShape.trench(28, 2.5f).offset(0, -1.2f, 0).rough(.8f, 2.5f))
        .commitAt("impacto")
        .burn(TerrainPalette.burnt(), 2)
        .cooling(220)
        .glow(0xffff6a20)
        .cracks(.9f)
        .smoke(.4f)
        .build();

    private RaioTrincheira() {}

    public static void register() {
        // Raio 48: o frustum (120 blocos) cobre o raio de 110 blocos e o rasgo. Impacto monocromático de 6 ticks no
        // marcador: o clarão esconde o instante em que os blocos somem e as seções são reenviadas.
        Effects.register(ID, new Effects.Definition(260, 48, 0xff9fd0ff, Effects.Shape.NONE, Effects.Material.NONE,
            null, null, new Effects.Impact(30, 6, .9f, Effects.ImpactStyle.MONOCHROME)).withRange(256));
        Effects.timeline(ID, TIMELINE);
        Effects.beams(ID, RAIO);
        Effects.terrain(ID, TRINCHEIRA);
        Effects.cue(ID, "impacto", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 4f, .8f));
    }

    /** Dez blocos à frente do jogador, virado com o yaw dele: o rasgo segue o olhar. */
    static Optional<Effects.Handle> iniciar(ServerPlayer player) {
        return Effects.start(ID, player.serverLevel(),
            Placement.at(Receitas.chao(player, 10), player.getYRot(), 0).withCaster(player));
    }
}
