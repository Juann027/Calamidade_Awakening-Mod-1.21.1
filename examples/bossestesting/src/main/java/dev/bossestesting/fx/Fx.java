package dev.bossestesting.fx;

import dev.bossestesting.BossesTesting;
import dev.bossestesting.ai.Brain;
import dev.bossestesting.anim.Clip;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.TerrainPalette;
import net.minecraft.resources.ResourceLocation;

/**
 * Every Calamidade effect of the fight. Timelines are built from the exported clips ({@link dev.bossestesting.anim.Anims})
 * plus {@link Brain#LEAD}, so markers fall on the same server tick as the action's gameplay. Registration order of
 * the engine: register → timeline → atmosphere/beams → lights → terrain/sky → cues.
 */
public final class Fx {
    private Fx() {}

    static ResourceLocation id(String path) { return BossesTesting.id(path); }

    // ---------------------------------------------------------------- palette
    /** Phase 1: industrial orange. */
    public static final int P1_CORE = 0xfffff0d0, P1_HOT = 0xffff9a2a, P1_DEEP = 0xffd04a08, P1_EMBER = 0xffff6a14;
    /** Phase 2: unstable cyan with red contrast. */
    public static final int P2_CORE = 0xffeaffff, P2_HOT = 0xff38e8ff, P2_DEEP = 0xff0a86b8, P2_RED = 0xffff2a3a, P2_EMBER = 0xff2ad8ff;

    // ---------------------------------------------------------------- phase 1
    public static final Variants LANDING = new Variants("landing");
    public static final ResourceLocation THRUST_L = id("thrust_l"), THRUST_R = id("thrust_r"), CAM_LANDING = id("cam_landing");
    public static final Variants SLAM = new Variants("slam");
    public static final ResourceLocation REACTOR_CHARGE = id("reactor_charge"), REACTOR_BEAM = id("reactor_beam");
    public static final Variants BEAM_SCAR = new Variants("beam_scar");
    public static final ResourceLocation PUNCH_R = id("punch_r"), PUNCH_L = id("punch_l"), DASH = id("dash"), STAGGER = id("stagger"), FURY = id("fury");
    public static final ResourceLocation TRANSFORM = id("transform"), CAM_TRANSFORM = id("cam_transform"), DEATH1 = id("death1");

    // ---------------------------------------------------------------- phase 2
    public static final ResourceLocation REVEAL = id("reveal");
    public static final Variants SLAM_V2 = new Variants("slam_v2");
    public static final ResourceLocation BARRAGE = id("barrage"), MISSILE_LOCK = id("missile_lock");
    public static final Variants MISSILE_IMPACT = new Variants("missile_impact");
    public static final ResourceLocation PUNCH_STREAM = id("punch_stream"), EXPOSED_CHARGE = id("exposed_charge"), EXPOSED_BEAM = id("exposed_beam");
    public static final Variants EXPOSED_SCAR = new Variants("exposed_scar");
    public static final ResourceLocation OVERLOAD_V2 = id("overload_v2"), OVERLOAD_VENT = id("overload_vent");
    public static final Variants CHARGED_PUNCH = new Variants("charged_punch");
    public static final ResourceLocation SHIELD_CHARGE = id("shield_charge");
    public static final Variants SHIELD_BREACH = new Variants("shield_breach");
    public static final Variants LEAP_SMASH = new Variants("leap_smash");
    public static final Variants DEATH2 = new Variants("death2");
    public static final ResourceLocation CAM_DEATH = id("cam_death"), AURA_P2 = id("aura_p2"), AURA_OVERLOAD = id("aura_overload");

    // ---------------------------------------------------------------- sky (global events of AREA scope, per boss phase)
    public static final ResourceLocation SKY_P1 = id("sky_p1"), SKY_P1_FURY = id("sky_p1_fury"), SKY_TRANSFORM = id("sky_transform"),
        SKY_P2 = id("sky_p2"), SKY_OVERLOAD = id("sky_overload");

    // ---------------------------------------------------------------- helpers

    /** Server tick of a clip event inside an effect started on the clip's first tick. */
    public static int t(Clip clip, String event) { return Brain.LEAD + clip.at(event); }

    /** Effect length that covers a whole clip plus `tail` ticks. */
    public static int len(Clip clip, int tail) { return Brain.LEAD + clip.ticks() + tail; }

    static Effects.Definition def(int ticks, double radius, int argb) {
        return new Effects.Definition(ticks, radius, argb, Effects.Shape.NONE, Effects.Material.NONE, null, null).withRange(160);
    }

    static Effects.Definition hero(int ticks, double radius, int argb, int impactTick, int impactTicks, float strength) {
        return new Effects.Definition(ticks, radius, argb, Effects.Shape.NONE, Effects.Material.NONE, null, null,
            new Effects.Impact(impactTick, impactTicks, strength, Effects.ImpactStyle.MONOCHROME), 192, Effects.Priority.HERO);
    }

    /** Scorched ground of phase 1: basalt, blackstone and embers (only the shell, one block thick). */
    static final TerrainPalette SCORCHED = TerrainPalette.builder()
        .add(TerrainPalette.Band.FLOOR, "minecraft:blackstone", 5).add(TerrainPalette.Band.FLOOR, "minecraft:basalt", 3)
        .add(TerrainPalette.Band.FLOOR, "minecraft:magma_block", 2).add(TerrainPalette.Band.FLOOR, "minecraft:coarse_dirt", 3)
        .add(TerrainPalette.Band.WALL, "minecraft:blackstone", 4).add(TerrainPalette.Band.WALL, "minecraft:tuff", 3)
        .add(TerrainPalette.Band.WALL, "minecraft:cobbled_deepslate", 3).add(TerrainPalette.Band.WALL, "minecraft:basalt", 2)
        .add(TerrainPalette.Band.RIM, "minecraft:coarse_dirt", 4).add(TerrainPalette.Band.RIM, "minecraft:gravel", 2)
        .add(TerrainPalette.Band.RIM, "minecraft:blackstone", 2)
        .build();

    /** Phase 2: ground fused by the cyan reactor (smooth basalt, deepslate, some crying obsidian veins). */
    static final TerrainPalette FUSED = TerrainPalette.builder()
        .add(TerrainPalette.Band.FLOOR, "minecraft:smooth_basalt", 5).add(TerrainPalette.Band.FLOOR, "minecraft:polished_deepslate", 2)
        .add(TerrainPalette.Band.FLOOR, "minecraft:crying_obsidian", 1).add(TerrainPalette.Band.FLOOR, "minecraft:blackstone", 3)
        .add(TerrainPalette.Band.WALL, "minecraft:cobbled_deepslate", 4).add(TerrainPalette.Band.WALL, "minecraft:smooth_basalt", 3)
        .add(TerrainPalette.Band.WALL, "minecraft:tuff", 2)
        .add(TerrainPalette.Band.RIM, "minecraft:coarse_dirt", 3).add(TerrainPalette.Band.RIM, "minecraft:gravel", 2)
        .add(TerrainPalette.Band.RIM, "minecraft:smooth_basalt", 2)
        .build();

    /** Tick (inside TRANSFORM, from its start) when the first form is swapped for the second. */
    public static int swapTick() { return FxP1.swapTick(); }

    /** Tick (inside DEATH2) of the final reactor implosion. */
    public static int deathFinalTick() { return FxP2.finalBlast(); }

    /** Registers everything (mod construction). */
    public static void register() {
        Anchors.register();
        FxP1.register();
        FxP2.register();
        FxSky.register();
    }
}
