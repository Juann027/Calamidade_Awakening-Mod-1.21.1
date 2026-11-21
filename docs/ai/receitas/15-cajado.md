# Receita 15 — Cajado (gatilho de item)

**Objetivo.** Uma arma que dispara efeitos com o gatilho de item da 0.10.0 (`EffectTriggers.item`): usar dispara um raio dos olhos para a frente, virado pelo olhar do jogador; bater num alvo marca o golpe no alvo, virado na direção do ataque. O gatilho cuida da fonte (o jogador), do conjurador, da recarga (do motor e mostrada na barra) e do custo de durabilidade.

| | |
|---|---|
| Teste no jogo | `/give @s vfxsample:cajado`; botão direito (raio); bata num mob (golpe). Em sobrevivência, o cajado perde 1 de durabilidade por raio |
| Arquivos | `examples/vfx-sample/src/main/java/example/vfxsample/receitas/Cajado.java`; `assets/vfxsample/models/item/cajado.json` (textura vanilla `blaze_rod` por nome) |
| Usa | `EffectTriggers.item(...).offset(...).cooldown(30).durability(1)`, pontos `EYES` e `TARGET`, `Point.facing`, `Light.along`, `Light.ring`, cue com `ctx.forward()` |
| Custo | raio: 1 feixe e 1 luz; golpe: 1 feixe e 1 luz |

## Gatilhos

| Campo | Valor | Efeito |
|---|---|---|
| `USAR` | `item(RAIO_ID).offset(.3, -.25, .4).cooldown(30).durability(1)` | ponto `EYES` (padrão): nos olhos, virado por yaw e pitch do olhar; o offset põe a saída na mão direita |
| `BATER` | `item(GOLPE_ID).at(Spot.TARGET)` | no meio do alvo, virado dos olhos do atacante para ele |
| no `Item` | `use` → `USAR.use(level, player, hand)`; `hurtEnemy` → `BATER.attack(stack, alvo, atacante)` | o gatilho devolve `consume`/`fail` no servidor e `success` no cliente |

Os outros pontos: `CASTER` (preso aos pés, yaw seguindo o corpo, para auras) e `AIM` (no bloco mirado, até `reach`).

## Timeline, feixes, luzes e cue

| Elemento | Valor | Para quê |
|---|---|---|
| `cajado_raio`: trilha `disparo` | 0 → 1 (3 ticks) | `REVEAL`: o raio chega ao alcance em 3 ticks |
| `cajado_raio`: trilha `forca` | 1,6 → 1 → 0 | `INTENSITY`: pico no disparo, some até o tick 24 |
| `cajado_raio`: marcador `acerto` | tick 3 | cue de dano: 3 de dano mágico em quem está a até 1,2 bloco da linha que sai do centro para a frente (`ctx.forward()`) |
| `RAIO` | `Point.center()` → `Point.facing(0, 0, 24)` | "24 blocos à frente" no referencial do olhar |
| `cajado_golpe` | risco `facing(0, 0, -1,2)` → `facing(0, 0, 1,6)` e anel no chão que abre de 0,5 a 4 blocos | o golpe atravessa o alvo na direção do ataque |

<!-- arquivo: examples/vfx-sample/src/main/java/example/vfxsample/receitas/Cajado.java -->
```java
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
```

## Como adaptar

- **Carregar e soltar:** chame `USAR.fire(jogador, mão, null)` de `Item#releaseUsing` em vez de `use`.
- **Disparar de outro lugar:** `fire(usuário, mão, alvo)` funciona para mobs que seguram o item (a fonte vira o mob).
- **Sem barra de recarga:** a recarga do motor vale do mesmo jeito; o gatilho só põe o item em recarga para jogadores.
- **Raio que segue a mira enquanto dura:** `at(Spot.CASTER)` e pontas `Point.anchorEye`/`anchorAim` (receita 9).

## O que conferir no jogo

1. O raio sai da mão direita e vai para onde a mira aponta, também olhando para cima e para baixo.
2. Logo depois do raio, o item mostra a recarga na barra e um segundo clique não dispara nada até ela acabar.
3. Em sobrevivência a durabilidade cai 1 por raio; em criativo não cai.
4. Um mob no caminho do raio leva dano no tick 3; o golpe corpo a corpo marca o alvo com o risco e o anel.
5. F7: "Feixes: 1", "Luzes: 1", "Fontes: 1 jogadores".
