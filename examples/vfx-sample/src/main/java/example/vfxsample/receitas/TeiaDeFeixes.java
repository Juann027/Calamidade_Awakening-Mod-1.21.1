package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 10 — teia de feixes (docs/ai/receitas/10-teia.md).
 * Um centro 4 blocos acima do chão ligado por 8 feixes a pontos em volta, com pulsos que correm do centro para
 * fora e uma ondulação leve. Os 8 feixes vão num único passe do motor, junto com os de qualquer outro efeito.
 * Teste: /vfxsample receita teia
 */
public final class TeiaDeFeixes {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_teia");

    static final Timeline TIMELINE = Timeline.builder(160)
        .track("tecer", keyframe(0, 0f), keyframe(30, 1f, Easing.SMOOTH))
        .track("pulsos", keyframe(0, 0f), keyframe(30, 0f), keyframe(50, 3f), keyframe(130, 3f), keyframe(150, 0f))
        .build();

    /** 8 fios do centro a um círculo de 8 blocos, alternando chão e 2,5 blocos de altura. */
    static final Beam[] FIOS = new Beam[8];
    static {
        for (int k = 0; k < FIOS.length; k++) {
            double angulo = k * Math.PI * 2 / FIOS.length;
            FIOS[k] = Beam.builder(Point.center(0, 4, 0),
                    Point.center(8 * Math.cos(angulo), k % 2 == 0 ? 0 : 2.5, 8 * Math.sin(angulo)))
                .width(.1f, .7f)
                .color(0xffffffff, 0xff60d0ff)
                .pulses(2, .8f, 1.6f)               // do centro para fora (velocidade positiva: do início ao fim)
                .wave(.12f, 3f, .3f)
                .track(Beam.Param.REVEAL, "tecer")
                .track(Beam.Param.PULSE_COUNT, "pulsos")
                .build();
        }
    }

    private TeiaDeFeixes() {}

    public static void register() {
        Effects.register(ID, new Effects.Definition(160, 10, 0xff60d0ff, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(ID, TIMELINE);
        Effects.beams(ID, FIOS);
        Effects.cue(ID, 0, ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2f, .6f));
    }
}
