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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Receita 15 — cajado: uma arma que dispara efeitos (docs/ai/receitas/15-cajado.md), motor 0.10.0.
 * Usar (botão direito) dispara um raio dos olhos para a frente, virado pelo olhar do jogador (gatilho de item, ponto
 * EYES): Point.facing(0, 0, 24) é sempre "24 blocos à frente". Recarga de 30 ticks (aparece na barra) e 1 ponto de
 * durabilidade por raio. Bater num alvo marca o golpe no alvo, virado na direção do ataque (ponto TARGET).
 * O dano do raio vem do cue, que usa ctx.forward() para saber para onde o raio aponta.
 * Teste: /give @s vfxsample:cajado; clique com o botão direito; bata num mob
 */
public final class Cajado extends Item {
    public static final ResourceLocation RAIO_ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "cajado_raio");
    public static final ResourceLocation GOLPE_ID = ResourceLocation.fromNamespaceAndPath("vfxsample", "cajado_golpe");
    static final double ALCANCE = 24;

    static final Timeline RAIO_TIMELINE = Timeline.builder(24)
        .track("disparo", keyframe(0, 0f), keyframe(3, 1f, Easing.EASE_OUT))
        .track("forca", keyframe(0, 1.6f), keyframe(6, 1f, Easing.SMOOTH), keyframe(24, 0f, Easing.EASE_IN))
        .marker("acerto", 3)
        .build();

    /** Da mão (um pouco à direita e abaixo dos olhos) até 24 blocos à frente, no referencial do olhar. */
    static final Beam RAIO = Beam.builder(Point.center(), Point.facing(0, 0, ALCANCE))
        .width(.14f, .8f)
        .color(0xffffffff, 0xffff8a20)
        .pulses(3, 3f, 2f)
        .noise(.05f)
        .track(Beam.Param.REVEAL, "disparo")
        .track(Beam.Param.INTENSITY, "forca")
        .build();

    static final Light RAIO_LUZ = Light.along(RAIO).radius(3.5f).intensity(1.6f).build();

    static final Timeline GOLPE_TIMELINE = Timeline.builder(20)
        .track("onda", keyframe(0, .5f), keyframe(14, 4f, Easing.EASE_OUT))
        .track("brilho", keyframe(0, 1f), keyframe(20, 0f, Easing.EASE_IN))
        .build();

    /** Risco curto atravessando o alvo na direção do golpe (de trás para a frente do referencial). */
    static final Beam GOLPE = Beam.builder(Point.facing(0, 0, -1.2), Point.facing(0, 0, 1.6))
        .width(.08f, .6f)
        .color(0xffffffff, 0xffffc040)
        .track(Beam.Param.INTENSITY, "brilho")
        .build();

    static final Light GOLPE_ANEL = Light.ring(Point.center(0, -.8, 0), .5f, 1.2f).color(0xffffb040).intensity(2f)
        .track(Light.Param.RADIUS, "onda").track(Light.Param.WEIGHT, "brilho").build();

    /** Gatilhos de item: imutáveis, guardados em campos estáticos. */
    static final EffectTriggers.ItemTrigger USAR = EffectTriggers.item(RAIO_ID)
        .offset(.3, -.25, .4)   // sai da mão direita, não do meio da tela
        .cooldown(30)
        .durability(1);
    static final EffectTriggers.ItemTrigger BATER = EffectTriggers.item(GOLPE_ID).at(EffectTriggers.ItemTrigger.Spot.TARGET);

    static final DeferredRegister.Items ITENS = DeferredRegister.createItems("vfxsample");
    public static final DeferredItem<Cajado> ITEM = ITENS.registerItem("cajado", Cajado::new,
        new Item.Properties().durability(250));

    public Cajado(Item.Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return USAR.use(level, player, hand);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        BATER.attack(stack, target, attacker);
        return super.hurtEnemy(stack, target, attacker);
    }

    /** Construtor comum do mod: efeitos (register, timeline, beams, lights, cues) e o registro do item. */
    public static void register(net.neoforged.bus.api.IEventBus bus) {
        Effects.register(RAIO_ID, new Effects.Definition(24, 10, 0xffff8a20, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(RAIO_ID, RAIO_TIMELINE);
        Effects.beams(RAIO_ID, RAIO);
        Effects.lights(RAIO_ID, RAIO_LUZ);
        Effects.cue(RAIO_ID, "acerto", Cajado::acerto);

        Effects.register(GOLPE_ID, new Effects.Definition(20, 3, 0xffffc040, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Effects.timeline(GOLPE_ID, GOLPE_TIMELINE);
        Effects.beams(GOLPE_ID, GOLPE);
        Effects.lights(GOLPE_ID, GOLPE_ANEL);
        Effects.cue(GOLPE_ID, 0, ctx -> ctx.level().playSound(null, BlockPos.containing(ctx.center()),
            SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1f, 1.4f));

        ITENS.register(bus);
    }

    /** Dano leve em quem estiver no caminho do raio: a até 1,2 bloco da linha que sai do centro para a frente. */
    private static void acerto(Effects.Context ctx) {
        ctx.level().playSound(null, BlockPos.containing(ctx.center()), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, .8f, 1.3f);
        Vec3 frente = ctx.forward();
        var fontes = ctx.level().damageSources();
        var fonte = ctx.caster() == null ? fontes.magic() : fontes.indirectMagic(ctx.caster(), ctx.caster());
        for (LivingEntity alvo : ctx.living(ALCANCE)) {
            Vec3 ate = alvo.getBoundingBox().getCenter().subtract(ctx.center());
            double aoLongo = ate.dot(frente);
            if (aoLongo < 0 || aoLongo > ALCANCE || ate.subtract(frente.scale(aoLongo)).length() > 1.2) continue;
            alvo.hurt(fonte, 3);
        }
    }
}
