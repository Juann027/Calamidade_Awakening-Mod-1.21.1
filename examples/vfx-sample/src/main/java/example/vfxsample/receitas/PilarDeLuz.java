package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Beam;
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
 * Receita 8 — pilar de luz (docs/ai/receitas/08-pilar-de-luz.md).
 * Um feixe desce do céu até o chão (Beam com revelação) e ilumina o terreno em volta: uma luz ao longo do feixe
 * (Light.along, que cresce junto com ele) e um disco de luz no chão quando ele chega. Nenhum shader nem camada de
 * cliente: o motor desenha os passes "beams" e "lights".
 * Teste: /vfxsample receita pilar (de noite fica mais bonito)
 */
public final class PilarDeLuz {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_pilar");

    static final Timeline TIMELINE = Timeline.builder(140)
        .phase("chao", 16, 140, 4, 50)
        .track("nascer", keyframe(0, 0f), keyframe(18, 1f, Easing.EASE_IN))
        .track("brilho", keyframe(0, 1f), keyframe(18, 1.8f, Easing.OVERSHOOT), keyframe(40, 1.2f, Easing.SMOOTH))
        .marker("chegada", 18)
        .build();

    /** Do céu (120 blocos acima) ao chão: a revelação faz o feixe "nascer" de cima para baixo. */
    static final Beam FEIXE = Beam.builder(Point.center(0, 120, 0), Point.center(0, 0, 0))
        .width(.7f, 5f)
        .color(0xffffffff, 0xffffb347)
        .pulses(3, 2f, 10f)                     // três pulsos descendo, 2 blocos por tick
        .track(Beam.Param.REVEAL, "nascer")
        .track(Beam.Param.INTENSITY, "brilho")
        .build();

    /** Luz ao longo do feixe: mesmas pontas, revelação, peso e cor do halo. */
    static final Light LUZ_FEIXE = Light.along(FEIXE).radius(12).intensity(2f).falloff(1.4f).build();

    /** Disco de luz no chão, só depois que o feixe chega (fase "chao"). */
    static final Light LUZ_CHAO = Light.disc(Point.center(0, .2, 0), 7).color(0xffffc070).intensity(1.6f).height(3)
        .weightPhase("chao").build();

    private PilarDeLuz() {}

    public static void register() {
        // Raio 48: o teste de frustum (2,5 x raio) cobre os 120 blocos do feixe e o alcance da luz.
        Effects.register(ID, new Effects.Definition(140, 48, 0xffffb347, Effects.Shape.NONE, Effects.Material.NONE,
            null, null).withRange(384));
        Effects.timeline(ID, TIMELINE);
        Effects.beams(ID, FEIXE);                 // feixes antes das luzes que os seguem
        Effects.lights(ID, LUZ_FEIXE, LUZ_CHAO);
        Effects.cue(ID, 0, ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2f, .8f));
        Effects.cue(ID, "chegada", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 2f, .7f));
    }
}
