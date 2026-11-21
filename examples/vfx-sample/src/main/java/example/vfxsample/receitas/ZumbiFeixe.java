package example.vfxsample.receitas;

import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.EffectTriggers;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 17 — zumbi vanilla com um ataque de feixe ao bater (docs/ai/receitas/17-zumbi.md), motor 0.10.0.
 * Quando um zumbi (ou husk, afogado, aldeão-zumbi) causa dano, o gatilho de entidade prende o efeito ao zumbi e o
 * vira para a vítima naquele instante: o feixe sai do peito e vai 3,5 blocos para a frente (Point.facing). A fonte é o
 * zumbi (no máximo 4 instâncias por mob) e a recarga é de 20 ticks por zumbi, então uma horda não lota o servidor.
 * Sem mixin nem GeckoLib: só o evento LivingDamageEvent.Post do NeoForge. /vfxsample zumbi off desliga.
 * Teste: /summon zombie perto de um aldeão (ou em sobrevivência, deixe um zumbi bater em você)
 */
public final class ZumbiFeixe {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "zumbi_feixe");

    static final Timeline TIMELINE = Timeline.builder(24)
        .track("golpe", keyframe(0, 0f), keyframe(3, 1f, Easing.EASE_OUT))
        .track("brilho", keyframe(0, 1.4f), keyframe(24, 0f, Easing.EASE_IN))
        .build();

    /** Do centro (peito do zumbi) até 3,5 blocos à frente, na direção da vítima. */
    static final Beam FEIXE = Beam.builder(Point.center(), Point.facing(0, 0, 3.5))
        .width(.12f, .8f)
        .color(0xffe8ffe0, 0xff60ff40)
        .pulses(2, 2f, 1f)
        .noise(.06f)
        .track(Beam.Param.REVEAL, "golpe")
        .track(Beam.Param.INTENSITY, "brilho")
        .build();

    static final Light LUZ = Light.along(FEIXE).radius(3).intensity(1.8f).build();

    /** Peito, um pouco à frente; recarga de 20 ticks por zumbi. */
    static final EffectTriggers.EntityTrigger GATILHO = EffectTriggers.entity(ID).offset(0, 1.25, .35).cooldown(20);

    /** Ligado por padrão; /vfxsample zumbi on|off. */
    static boolean ativo = true;

    private ZumbiFeixe() {}

    public static void register() {
        // Raio 3: o frustum (7,5 blocos) cobre o feixe de 3,5.
        Effects.register(ID, new Effects.Definition(24, 3, 0xff60ff40, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(ID, TIMELINE);
        Effects.beams(ID, FEIXE);
        Effects.lights(ID, LUZ);
        Effects.cue(ID, 0, ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, .9f, 1.3f));
        NeoForge.EVENT_BUS.addListener(ZumbiFeixe::dano);
    }

    /** Servidor: o zumbi acabou de causar dano. */
    private static void dano(LivingDamageEvent.Post event) {
        if (ativo && event.getSource().getEntity() instanceof Zombie zumbi && event.getEntity() != zumbi)
            GATILHO.fire(zumbi, event.getEntity());
    }
}
