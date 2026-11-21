package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import dev.calamidade_awakening.api.v1.client.AddonLayers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 12 — onda de luz (docs/ai/receitas/12-onda-de-luz.md).
 * A onda de choque da receita 1 (o mesmo shader, receita_anel.fsh) com um anel de luz no chão que se expande junto:
 * a trilha "raio" cresce na mesma velocidade da onda (0,45 bloco por tick desde o impacto), então a luz acompanha a
 * frente. Um clarão de luz no centro marca o impacto.
 * Teste: /vfxsample receita onda_luz
 */
public final class OndaDeLuz {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_onda_luz");

    /** Velocidade da onda, em blocos por tick: a mesma no shader e na trilha "raio". */
    static final float VELOCIDADE = .45f;

    static final Timeline TIMELINE = Timeline.builder(60)
        .phase("carga", 0, 12, 4, 2)
        .phase("onda", 12, 60, 0, 20)
        .phase("clarao", 12, 24, 1, 10)
        // Linear de propósito: raio = 0,45 x (idade - 12), igual à frente de caDistanceWave no shader.
        .track("raio", keyframe(12, 0f), keyframe(60, VELOCIDADE * 48))
        .track("forca", keyframe(12, 3f), keyframe(36, 2f), keyframe(60, 0f, Easing.SMOOTH))
        .marker("impacto", 12)
        .build();

    /** Anel de luz de 3 blocos de largura que segue a frente da onda. */
    static final Light ANEL = Light.ring(Point.center(0, .2, 0), 0, 3)
        .color(0xffffc060)
        .height(3)
        .track(Light.Param.RADIUS, "raio")
        .track(Light.Param.INTENSITY, "forca")
        .weightPhase("onda")
        .build();

    /** Clarão no centro, só no impacto. */
    static final Light CLARAO = Light.point(Point.center(0, 1, 0)).color(0xffffe0a0).intensity(3f).radius(8)
        .weightPhase("clarao").build();

    private OndaDeLuz() {}

    public static void register() {
        // Raio 10: o frustum (25 blocos) cobre os 21,6 blocos que a onda percorre.
        Effects.register(ID, new Effects.Definition(60, 10, 0xffffc060, Effects.Shape.NONE, Effects.Material.NONE,
            null, null, new Effects.Impact(12, 3, .45f, Effects.ImpactStyle.TINTED_FLASH)));
        Effects.timeline(ID, TIMELINE);
        Effects.lights(ID, ANEL, CLARAO);
        Effects.cue(ID, "impacto", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, .6f, 1.2f));
    }

    /** Só no cliente: a onda visível, com o shader da receita 1. */
    public static final class Client {
        private Client() {}

        public static void setup() {
            AddonLayers.post(ID, post -> {
                var t = post.timeline();
                if (t.weight("onda") <= 0) return;
                post.pass("vfxsample:receita_anel", u -> {
                    u.v3("AnelCentro", post.center());
                    u.f4("AnelEstado", (float) t.since("impacto"), t.weight("onda"), post.time().opacity(), VELOCIDADE);
                });
            });
        }
    }
}
