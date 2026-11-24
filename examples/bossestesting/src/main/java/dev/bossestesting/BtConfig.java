package dev.bossestesting;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config (bossestesting-server.toml in the world's serverconfig folder). */
public final class BtConfig {
    /** How much of the arena the boss may destroy through the Terrain API. */
    public enum Destruction { OFF, LOW, NORMAL, EPIC }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<Destruction> DESTRUCTION;
    public static final ModConfigSpec.BooleanValue DEMO_PROFILE;
    public static final ModConfigSpec.DoubleValue HEALTH_PHASE1;
    public static final ModConfigSpec.DoubleValue HEALTH_PHASE2;
    public static final ModConfigSpec.DoubleValue DAMAGE_SCALE;
    public static final ModConfigSpec.IntValue ARENA_RADIUS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("destruction");
        DESTRUCTION = b.comment("Terrain destruction of the boss: OFF (only visuals), LOW, NORMAL or EPIC.",
                "Always subject to the mobGriefing game rule, the engine's terrain config, protected blocks and claims.")
            .defineEnum("level", Destruction.NORMAL);
        DEMO_PROFILE = b.comment("Demonstration profile for test worlds: forces EPIC destruction while true",
                "(/bossestesting destruction demo on|off). mobGriefing and protections still apply.")
            .define("demoProfile", false);
        b.pop();
        b.push("balance");
        HEALTH_PHASE1 = b.comment("Max health of the first form (JarGolem).").defineInRange("healthPhase1", 600.0, 50.0, 100000.0);
        HEALTH_PHASE2 = b.comment("Max health of the second form (Finale).").defineInRange("healthPhase2", 800.0, 50.0, 100000.0);
        DAMAGE_SCALE = b.comment("Multiplier of every damage the boss deals.").defineInRange("damageScale", 1.0, 0.0, 10.0);
        ARENA_RADIUS = b.comment("Radius of the fight in blocks: participants, music, sky and bar.").defineInRange("arenaRadius", 64, 24, 160);
        b.pop();
        SPEC = b.build();
    }

    private BtConfig() {}

    /** Effective destruction level: the demo profile forces EPIC. */
    public static Destruction destruction() {
        if (!SPEC.isLoaded()) return Destruction.NORMAL;
        return DEMO_PROFILE.get() ? Destruction.EPIC : DESTRUCTION.get();
    }

    public static double damageScale() { return SPEC.isLoaded() ? DAMAGE_SCALE.get() : 1; }
    public static int arenaRadius() { return SPEC.isLoaded() ? ARENA_RADIUS.get() : 64; }
    public static double healthPhase1() { return SPEC.isLoaded() ? HEALTH_PHASE1.get() : 600; }
    public static double healthPhase2() { return SPEC.isLoaded() ? HEALTH_PHASE2.get() : 800; }
}
