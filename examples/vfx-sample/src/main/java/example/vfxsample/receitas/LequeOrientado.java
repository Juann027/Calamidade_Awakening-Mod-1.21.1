package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Placement;
import dev.calamidade_awakening.api.v1.Point;
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
 * Receita 14 — leque de feixes virado para onde o jogador olha (docs/ai/receitas/14-leque.md), motor 0.10.0.
 * Os cinco feixes são escritos com Point.facing(direita, cima, frente): o mesmo efeito, iniciado com o yaw do jogador
 * (Placement.at(posição, yaw, 0)), abre sempre à frente dele. Sem orientação, a frente seria o sul.
 * Teste: /vfxsample receita leque (vire para vários lados e repita)
 */
public final class LequeOrientado {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_leque");

    static final Timeline TIMELINE = Timeline.builder(60)
        .phase("abrir", 0, 60, 2, 20)
        .track("abrir", keyframe(0, 0f), keyframe(10, 1f, Easing.EASE_OUT))
        .build();

    /** Ângulos do leque, em graus a partir da frente (negativo à esquerda). */
    static final double[] ANGULOS = {-40, -20, 0, 20, 40};
    static final double ALCANCE = 14;

    static Beam feixe(double graus) {
        double a = Math.toRadians(graus);
        return Beam.builder(Point.facing(0, 1, .5), Point.facing(Math.sin(a) * ALCANCE, 1, Math.cos(a) * ALCANCE))
            .width(.1f, .7f)
            .color(0xffffffff, graus == 0 ? 0xffffd040 : 0xff40c0ff)
            .pulses(2, 1.2f, 1.5f)
            .track(Beam.Param.REVEAL, "abrir")   // os feixes crescem a partir do jogador
            .weightPhase("abrir")
            .build();
    }

    static final Beam[] FEIXES = {feixe(ANGULOS[0]), feixe(ANGULOS[1]), feixe(ANGULOS[2]), feixe(ANGULOS[3]), feixe(ANGULOS[4])};

    /** Luz na ponta do feixe central, que também gira com o leque. */
    static final Light PONTA = Light.point(Point.facing(0, 1, ALCANCE)).color(0xffffd040).intensity(2.2f).radius(6)
        .weightPhase("abrir").build();

    private LequeOrientado() {}

    public static void register() {
        // Raio 8: o frustum (20 blocos) cobre o leque de 14.
        Effects.register(ID, new Effects.Definition(60, 8, 0xff40c0ff, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(ID, TIMELINE);
        Effects.beams(ID, FEIXES);
        Effects.lights(ID, PONTA);
        Effects.cue(ID, 0, ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, .8f, 1.6f));
    }

    /** Começa aos pés do jogador, virado com o yaw dele (pitch 0: o leque fica na horizontal). */
    static Optional<Effects.Handle> iniciar(ServerPlayer player) {
        return Effects.start(ID, player.serverLevel(), Placement.at(player.position(), player.getYRot(), 0).withCaster(player));
    }
}
