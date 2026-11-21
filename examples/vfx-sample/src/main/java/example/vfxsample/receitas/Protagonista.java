package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Atmosphere;
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
import java.util.ArrayList;
import java.util.List;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 13 — protagonista (docs/ai/receitas/13-protagonista.md).
 * Um efeito grande com prioridade HERO: lança de luz do céu, seis raios no chão, luzes (ao longo da lança, um disco e
 * um anel que corre no golpe) e atmosfera. Como HERO, ele entra antes dos efeitos NORMAL, nunca é cortado pelo
 * limite de desenhados e paga os passes de feixes e de luzes com o orçamento HERO (1/2/3), então aparece inteiro
 * mesmo com /calamidade bench 60 ao redor (em BALANCED e HIGH; em LOW, o único passe HERO vai para os feixes).
 * Teste: /calamidade bench 60, depois /vfxsample receita protagonista, e F7
 */
public final class Protagonista {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_protagonista");

    static final Timeline TIMELINE = Timeline.builder(240)
        .phase("carga", 0, 60, 30, 4)
        .phase("brilho", 60, 240, 0, 80)
        .track("nascer", keyframe(0, 0f), keyframe(60, 1f, Easing.EASE_IN))
        .track("raios", keyframe(60, 0f), keyframe(72, 1f, Easing.EASE_OUT))
        .track("onda", keyframe(60, 0f), keyframe(120, 40f, Easing.EASE_OUT))
        .track("forca_onda", keyframe(60, 3f), keyframe(120, 0f, Easing.SMOOTH))
        .marker("golpe", 60)
        .build();

    /** Lança: do céu ao chão, larga e dourada; pulsos descendo. */
    static final Beam LANCA = Beam.builder(Point.center(0, 200, 0), Point.center(0, 0, 0))
        .width(1.2f, 8f)
        .color(0xffffffff, 0xffffb030)
        .pulses(4, 3f, 14f)
        .track(Beam.Param.REVEAL, "nascer")
        .build();

    /** Seis raios do pé da lança, de 16 blocos, que se abrem no golpe. */
    static final List<Beam> RAIOS = new ArrayList<>();
    static {
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3 + .3;
            RAIOS.add(Beam.builder(Point.center(0, .5, 0), Point.center(16 * Math.cos(a), .5, 16 * Math.sin(a)))
                .width(.25f, 1.6f).color(0xffffc040).noise(.08f)
                .track(Beam.Param.REVEAL, "raios").weightPhase("brilho").build());
        }
    }

    static final Light LUZ_LANCA = Light.along(LANCA).radius(16).intensity(2.5f).falloff(1.3f).build();
    static final Light ANEL = Light.ring(Point.center(0, .2, 0), 0, 4).color(0xffffd070).height(4)
        .track(Light.Param.RADIUS, "onda").track(Light.Param.INTENSITY, "forca_onda").weightPhase("brilho").build();
    static final Light DISCO = Light.disc(Point.center(0, .2, 0), 10).color(0xffffa040).intensity(1.2f).height(4)
        .weightPhase("brilho").build();

    /** Céu escuro e vermelho, névoa quente, cena um pouco mais escura: a lança se destaca. */
    static final Atmosphere CEU = Atmosphere.builder()
        .sky(0xff20060a, 0xffff7030, .6f)
        .fog(0xff802010, .35f, 24, 160)
        .exposure(.85f)
        .ambient(0xffff9060, .2f)
        .build();

    private Protagonista() {}

    public static void register() {
        // HERO: a cena gira em torno deste efeito. Raio 80: o frustum (200 blocos) cobre a lança inteira.
        Effects.register(ID, new Effects.Definition(240, 80, 0xffffb030, Effects.Shape.NONE, Effects.Material.NONE,
            null, null, new Effects.Impact(60, 6, .6f, Effects.ImpactStyle.TINTED_FLASH))
            .withRange(512)
            .withPriority(Effects.Priority.HERO));
        Effects.timeline(ID, TIMELINE);
        Effects.atmosphere(ID, CEU);
        var feixes = new ArrayList<Beam>(RAIOS);
        feixes.addFirst(LANCA);
        Effects.beams(ID, feixes.toArray(Beam[]::new));
        Effects.lights(ID, LUZ_LANCA, ANEL, DISCO);
        Effects.cue(ID, 0, ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 3f, .5f));
        Effects.cue(ID, "golpe", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2f, .8f));
    }
}
