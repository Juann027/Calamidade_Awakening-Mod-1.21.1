package example.vfxsample.receitas;

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
 * Receita 11 — fogueira (docs/ai/receitas/11-fogueira.md).
 * Só uma luz pontual laranja que cintila, sem geometria, partícula ou shader: o motor ilumina o chão e os blocos em
 * volta no passe "lights". Efeito ambiente com sustain: fica acesa até /vfxsample receita release.
 * É luz visual: o nível de luz do jogo não muda (mobs continuam nascendo ali).
 * Teste: /vfxsample receita fogueira, de noite; depois /vfxsample receita release
 */
public final class Fogueira {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "receita_fogueira");

    static final Timeline TIMELINE = Timeline.builder(200)
        .track("chama", keyframe(0, 0f), keyframe(30, 1f, Easing.EASE_OUT), keyframe(40, 1f), keyframe(160, 1f),
            keyframe(200, 0f, Easing.SMOOTH))
        .marker("estalo", 70)
        .marker("estalo_2", 125)
        .sustain(40, 160)                       // loop de 6 s; release apaga em 2 s
        .build();

    /** 0,6 bloco acima do centro, alcance 10, queda suave, cintilação de 35% (6 vezes por segundo). */
    static final Light CHAMA = Light.point(Point.center(0, .6, 0))
        .color(0xffff9a40)
        .intensity(3f)
        .radius(10)
        .falloff(1.2f)
        .flicker(.35f, 6)
        .track(Light.Param.WEIGHT, "chama")
        .build();

    private Fogueira() {}

    public static void register() {
        // Raio 4: o teste de frustum (10 blocos) cobre o alcance da luz.
        Effects.register(ID, new Effects.Definition(200, 4, 0xffff9a40, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(ID, TIMELINE);
        Effects.lights(ID, CHAMA);
        // Dentro do loop: estalam a cada volta.
        Effects.cue(ID, "estalo", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 1.5f, 1f));
        Effects.cue(ID, "estalo_2", ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 1.2f, .9f));
    }
}
