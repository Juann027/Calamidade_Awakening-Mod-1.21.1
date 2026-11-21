package dev.calamidade_awakening;

import dev.calamidade_awakening.api.TriggerLogic;
import dev.calamidade_awakening.api.TriggerLogic.Action;
import dev.calamidade_awakening.api.TriggerLogic.Mode;
import dev.calamidade_awakening.api.v1.EffectTriggers;
import dev.calamidade_awakening.api.v1.Effects;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/** 0.10.0: block trigger modes (pure state machine) and the validation of the ready-made triggers. */
final class TriggerCheck {
    static void run() {
        periodic();
        periodicSustained();
        redstoneOnce();
        redstoneRepeat();
        redstoneSustained();
        retryAfterRefusal();
        proximity();
        configuration();
        System.out.println("TriggerCheck PASS");
    }

    /** Ticks at which the logic answered START, for a fixed input. */
    private static List<Long> starts(TriggerLogic logic, Mode mode, boolean active, boolean sustained, int period, long from, long to) {
        List<Long> ticks = new ArrayList<>();
        for (long t = from; t < to; t++) if (logic.step(mode, active, sustained, false, period, t) == Action.START) ticks.add(t);
        return ticks;
    }

    private static void periodic() {
        var logic = new TriggerLogic();
        require(starts(logic, Mode.PERIODIC, false, false, 40, 100, 221).equals(List.of(100L, 140L, 180L, 220L)),
            "periodic starts at once and every period, input ignored");
        var fast = new TriggerLogic();
        require(starts(fast, Mode.PERIODIC, true, false, 1, 0, 5).size() == 5, "period 1 starts every tick");
    }

    private static void periodicSustained() {
        var logic = new TriggerLogic();
        require(logic.step(Mode.PERIODIC, true, true, false, 40, 0) == Action.START, "sustained: first start");
        for (long t = 1; t < 200; t++)
            require(logic.step(Mode.PERIODIC, true, true, true, 40, t) == Action.NONE, "held while running");
        // The held instance went away (stopped by a command): started again once the period passed.
        require(logic.step(Mode.PERIODIC, true, true, false, 40, 200) == Action.START, "restarted after it ended");
    }

    private static void redstoneOnce() {
        var logic = new TriggerLogic();
        require(logic.step(Mode.REDSTONE, false, false, false, 0, 0) == Action.NONE, "unpowered");
        require(logic.step(Mode.REDSTONE, true, false, false, 0, 1) == Action.START, "rising edge");
        for (long t = 2; t < 100; t++) require(logic.step(Mode.REDSTONE, true, false, false, 0, t) == Action.NONE, "once per edge");
        require(logic.step(Mode.REDSTONE, false, false, false, 0, 100) == Action.NONE && !logic.active(), "falling edge, one-shot");
        require(logic.step(Mode.REDSTONE, true, false, false, 0, 101) == Action.START && logic.active(), "next edge starts again");
    }

    private static void redstoneRepeat() {
        var logic = new TriggerLogic();
        require(starts(logic, Mode.REDSTONE, true, false, 20, 10, 75).equals(List.of(10L, 30L, 50L, 70L)),
            "repeats every period while powered");
        require(logic.step(Mode.REDSTONE, false, false, false, 20, 75) == Action.NONE
            && starts(logic, Mode.REDSTONE, false, false, 20, 76, 200).isEmpty(), "stops when unpowered");
    }

    private static void redstoneSustained() {
        var logic = new TriggerLogic();
        require(logic.step(Mode.REDSTONE, true, true, false, 0, 0) == Action.START, "sustained rising edge");
        require(logic.step(Mode.REDSTONE, true, true, true, 0, 1) == Action.NONE, "held while powered");
        require(logic.step(Mode.REDSTONE, false, true, true, 0, 50) == Action.RELEASE, "released on the falling edge");
        require(logic.step(Mode.REDSTONE, false, true, true, 0, 51) == Action.NONE, "released only once");
        var idle = new TriggerLogic();
        idle.step(Mode.REDSTONE, true, true, false, 0, 0);
        require(idle.step(Mode.REDSTONE, false, true, false, 0, 5) == Action.NONE, "nothing to release");
    }

    private static void retryAfterRefusal() {
        var logic = new TriggerLogic();
        // Server refuses every start (cooldown or limits): running stays false.
        require(starts(logic, Mode.REDSTONE, true, true, 0, 0, 61).equals(List.of(0L, 20L, 40L, 60L)),
            "sustained retries every " + TriggerLogic.RETRY + " ticks, not every tick");
        var slow = new TriggerLogic();
        require(starts(slow, Mode.PERIODIC, true, true, 100, 0, 201).equals(List.of(0L, 100L, 200L)),
            "a longer period spaces the retries");
    }

    private static void proximity() {
        var logic = new TriggerLogic();
        require(logic.step(Mode.PROXIMITY, true, false, false, 0, 0) == Action.START, "player arrives");
        require(logic.step(Mode.PROXIMITY, true, false, false, 0, 1) == Action.NONE, "stays");
        require(logic.step(Mode.PROXIMITY, false, false, false, 0, 2) == Action.NONE, "leaves");
        require(logic.step(Mode.PROXIMITY, true, false, false, 0, 3) == Action.START, "arrives again");
        logic.reset();
        require(!logic.active() && logic.step(Mode.PROXIMITY, true, false, false, 0, 4) == Action.START,
            "after a reload the player already there counts as an arrival");
    }

    private static void configuration() {
        var id = ResourceLocation.fromNamespaceAndPath("triggertest", "pulse");
        Effects.register(id, new Effects.Definition(40, 2, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null));
        // Triggers may be built before the effect is registered (static final fields); the effect is checked on fire.
        var early = EffectTriggers.item(ResourceLocation.fromNamespaceAndPath("triggertest", "later"));
        require(early.effect().getPath().equals("later"), "trigger before its effect");
        expect(() -> EffectTriggers.item(null), "null effect");
        var item = EffectTriggers.item(id);
        require(item.spot() == EffectTriggers.ItemTrigger.Spot.EYES && item.cooldown() == 0 && item.durability() == 0, "item defaults");
        var staff = item.at(EffectTriggers.ItemTrigger.Spot.TARGET).cooldown(30).durability(2).offset(.3, -.2, .5);
        require(staff.spot() == EffectTriggers.ItemTrigger.Spot.TARGET && staff.cooldown() == 30 && staff.durability() == 2
            && item.cooldown() == 0, "item copies");
        expect(() -> item.reach(.1), "reach below 0.5");
        expect(() -> item.cooldown(-1), "negative cooldown");
        expect(() -> item.durability(2000), "durability cost above 1024");
        expect(() -> item.offset(Double.NaN, 0, 0), "NaN offset");
        var block = EffectTriggers.blockEntity(id);
        require(block.mode() == EffectTriggers.BlockTrigger.Mode.PERIODIC && block.period() == 40 && block.radius() == 8, "block defaults");
        expect(() -> block.period(0), "periodic period 0");
        expect(() -> block.periodic(0), "periodic(0)");
        require(block.periodic(60).period() == 60, "periodic(60)");
        require(block.redstone().period() == 0 && block.redstone().period(20).period() == 20, "redstone periods");
        require(block.proximity(6).radius() == 6 && block.proximity(6).mode() == EffectTriggers.BlockTrigger.Mode.PROXIMITY, "proximity");
        expect(() -> block.proximity(200), "radius above 128");
        expect(() -> block.pitch(95), "pitch above 90");
        expect(() -> block.cooldown(80000), "cooldown above 72000");
        var mob = EffectTriggers.entity(id).offset(0, 1.3, .4).cooldown(20).followBody(false).pitch(10);
        require(mob.cooldown() == 20 && mob.effect().equals(id), "entity trigger");
        expect(() -> EffectTriggers.entity(id).pitch(-91), "entity pitch below -90");
    }

    private static void expect(Runnable action, String test) {
        try {
            action.run();
        } catch (IllegalArgumentException | NullPointerException expected) {
            return;
        }
        throw new AssertionError("accepted: " + test);
    }

    private static void require(boolean condition, String test) {
        if (!condition) throw new AssertionError(test);
    }
}
