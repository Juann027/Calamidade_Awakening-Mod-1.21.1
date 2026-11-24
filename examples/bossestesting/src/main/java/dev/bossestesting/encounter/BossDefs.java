package dev.bossestesting.encounter;

import dev.bossestesting.BossesTesting;
import dev.bossestesting.fx.Fx;
import dev.calamidade_awakening.api.v1.Boss;
import dev.calamidade_awakening.api.v1.Bosses;
import dev.calamidade_awakening.api.v1.WorldEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Boss API controllers. One per form: the first is detached and the second attached in the same server tick of the
 * swap, so there is never a second bar. Phases carry the skies (AREA global events, released by the engine when the
 * phase ends, the boss dies or the controller is detached) and the bar look; the addon drives them by signals.
 */
public final class BossDefs {
    public static final ResourceLocation PHASE1 = BossesTesting.id("guilhermino_bananino");
    public static final ResourceLocation PHASE2 = BossesTesting.id("guilhermino_finale");
    public static final ResourceLocation STYLE_P1 = BossesTesting.id("bar_bananino");
    public static final ResourceLocation STYLE_P2 = BossesTesting.id("bar_finale");

    private BossDefs() {}

    public static void register() {
        Bosses.define(Boss.builder(PHASE1)
            .bar(Boss.Bar.of(Component.translatable("boss.bossestesting.bananino")).color(Boss.BarColor.YELLOW).style(STYLE_P1).range(96))
            .phase("combate", p -> p.worldEvent(Fx.SKY_P1, WorldEvents.Scope.AREA).label("boss.bossestesting.phase.combate"))
            .phase("furia", p -> p.worldEvent(Fx.SKY_P1_FURY, WorldEvents.Scope.AREA).color(Boss.BarColor.RED).label("boss.bossestesting.phase.furia"))
            .phase("sobrecarga", p -> p.worldEvent(Fx.SKY_TRANSFORM, WorldEvents.Scope.AREA).invulnerable().color(Boss.BarColor.RED)
                .label("boss.bossestesting.phase.sobrecarga"))
            .transition("combate", "furia", Boss.Trigger.signal("furia"))
            .transition(Boss.ANY, "sobrecarga", Boss.Trigger.signal("sobrecarga"))
            .minDwell(0)
            .build());

        Bosses.define(Boss.builder(PHASE2)
            .bar(Boss.Bar.of(Component.translatable("boss.bossestesting.finale")).color(Boss.BarColor.BLUE).style(STYLE_P2).range(96))
            .phase("finale", p -> p.worldEvent(Fx.SKY_P2, WorldEvents.Scope.AREA).sustain(Fx.AURA_P2).label("boss.bossestesting.phase.finale"))
            .phase("sobrecarregado", p -> p.worldEvent(Fx.SKY_OVERLOAD, WorldEvents.Scope.AREA).sustain(Fx.AURA_OVERLOAD).color(Boss.BarColor.RED)
                .label("boss.bossestesting.phase.sobrecarregado"))
            .phase("derrota", p -> p.label("boss.bossestesting.phase.derrota"))
            .transition("finale", "sobrecarregado", Boss.Trigger.signal("overload_on"))
            .transition("sobrecarregado", "finale", Boss.Trigger.signal("overload_off"))
            .transition(Boss.ANY, "derrota", Boss.Trigger.death())
            .minDwell(0)
            .build());
    }
}
