package example.consumidor;

import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.EffectTriggers;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Point;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Mod mínimo que usa o Calamidade_Awakening como biblioteca (docs/ai/usar-como-dependencia.md): registra um efeito
 * no construtor e o dispara, preso ao jogador e virado para o alvo, a cada golpe que o jogador acerta.
 */
@Mod("consumidor")
public final class Consumidor {
    static final ResourceLocation GOLPE = ResourceLocation.fromNamespaceAndPath("consumidor", "golpe");

    /** Feixe curto do peito do jogador para a frente, na direção do alvo; recarga de 10 ticks. */
    static final EffectTriggers.EntityTrigger GATILHO = EffectTriggers.entity(GOLPE).offset(0, 1.2, .4).cooldown(10);

    public Consumidor() {
        Effects.register(GOLPE, new Effects.Definition(20, 3, 0xffff5020, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Beam feixe = Beam.builder(Point.center(), Point.facing(0, 0, 3)).width(.1f, .7f).color(0xffffffff, 0xffff5020).build();
        Effects.beams(GOLPE, feixe);
        Effects.lights(GOLPE, Light.along(feixe).radius(3).intensity(1.5f).build());
        NeoForge.EVENT_BUS.addListener(Consumidor::golpe);
    }

    private static void golpe(LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof Player jogador && event.getEntity() instanceof LivingEntity alvo && alvo != jogador)
            GATILHO.fire(jogador, alvo);
    }
}
