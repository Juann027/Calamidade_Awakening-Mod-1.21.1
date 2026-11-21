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
 * Receita 9 — laser preso ao jogador (docs/ai/receitas/09-laser.md).
 * A instância segue o jogador (Effects.follow) e o feixe vai dos olhos dele até o primeiro bloco que ele mira
 * (Point.anchorEye e Point.anchorAim): vire a câmera e o laser acompanha, em todos os clientes, sem pacote novo.
 * Uma luz pontual cintila onde o laser acerta e outra, fraca, segue o feixe.
 * Teste: /vfxsample receita laser (5 segundos; mire em paredes e no chão)
 */
public final class LaserMira {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_laser");

    static final Timeline TIMELINE = Timeline.builder(100)
        .phase("acertou", 4, 100, 2, 15)
        .track("disparo", keyframe(0, 0f), keyframe(4, 1f, Easing.EASE_OUT))
        .track("carga", keyframe(0, .6f), keyframe(4, 1.5f, Easing.OVERSHOOT), keyframe(12, 1f, Easing.SMOOTH))
        .build();

    /** Dos olhos (um pouco abaixo, para não sair do meio da tela) até o bloco mirado, a até 48 blocos. */
    static final Beam FEIXE = Beam.builder(Point.anchorEye(0, -.3, 0), Point.anchorAim(48))
        .width(.12f, .9f)
        .color(0xffffffff, 0xffff3050)
        .pulses(4, 2.5f, 1.5f)
        .noise(.04f)                            // estala de leve
        .track(Beam.Param.REVEAL, "disparo")    // o tiro sai dos olhos e chega ao alvo em 4 ticks
        .track(Beam.Param.INTENSITY, "carga")
        .build();

    /** Brilho no ponto de impacto, só depois que o tiro chega (fase "acertou"). */
    static final Light ALVO = Light.point(Point.anchorAim(48)).color(0xffff5060).intensity(2.5f).radius(5)
        .flicker(.3f, 10).weightPhase("acertou").build();

    /** Luz fraca ao longo do feixe. */
    static final Light TRILHA = Light.along(FEIXE).radius(3).intensity(1.2f).build();

    private LaserMira() {}

    public static void register() {
        // Raio 24 em volta do jogador: o frustum (60 blocos) cobre o feixe de 48.
        Effects.register(ID, new Effects.Definition(100, 24, 0xffff3050, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(ID, TIMELINE);
        Effects.beams(ID, FEIXE);
        Effects.lights(ID, ALVO, TRILHA);
        Effects.cue(ID, 0, ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 1f, 1.6f));
    }
}
