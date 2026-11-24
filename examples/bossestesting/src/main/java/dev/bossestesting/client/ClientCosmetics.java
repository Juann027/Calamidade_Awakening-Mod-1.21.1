package dev.bossestesting.client;

import dev.bossestesting.ai.Brain;
import dev.bossestesting.anim.Clip;
import dev.bossestesting.entity.GuilherminoBoss;
import dev.bossestesting.fx.Fx;
import dev.bossestesting.fx.Sfx;
import dev.calamidade_awakening.api.v1.RigAnchors;
import dev.calamidade_awakening.api.v1.client.AnchorCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client-only cosmetics, never gameplay:
 * <ul>
 * <li>GeckoLib custom instructions of the locomotion and idle clips of phase 2 (arm spasms, servo snarls, foot drags,
 * shield-charge stomps): sparks, dust and local sounds. Attack instructions are ignored here, because the server
 * already runs those events (no duplicates).</li>
 * <li>Explicit sync of the events that are NOT GeckoLib instructions ({@link Clip#external()}): the footsteps of the
 * first form's walk and run. The client knows when the loop started and fires the documented ticks of each cycle.</li>
 * </ul>
 */
public final class ClientCosmetics {
    private ClientCosmetics() {}

    private static final class Loco { Clip clip; int start; int lastCycleTick = -1; }
    private static final Map<GuilherminoBoss, Loco> LOCO = new WeakHashMap<>();

    /** GeckoLib custom instruction from a boss controller ("guilhermino2:left_foot_drag_strike"). */
    public static void instruction(GuilherminoBoss boss, String instruction) {
        String id = instruction.substring(instruction.indexOf(':') + 1);
        switch (id) {
            case "left_arm_spasm", "left_arm_spasm_2" -> sparks(boss, "wires_l", Fx.P2_HOT, 10);
            case "twitch_head" -> sound(boss, SoundEvents.PISTON_CONTRACT, .5f, 1.6f);
            case "servo_snarl" -> { sound(boss, SoundEvents.PISTON_EXTEND, .8f, .7f); sound(boss, SoundEvents.IRON_GOLEM_REPAIR, .5f, .6f); }
            case "left_foot_drag_strike" -> { step(boss, "foot_l", 1.1f); sound(boss, SoundEvents.GRINDSTONE_USE, .6f, .5f); }
            case "left_knee_buckle" -> { sound(boss, SoundEvents.PISTON_CONTRACT, .7f, .6f); sparks(boss, "wires_l", Fx.P2_RED, 6); }
            case "right_foot_strike" -> step(boss, "foot_r", 1);
            case "left_foot_stomp" -> step(boss, "foot_l", 1.4f);
            case "right_foot_stomp" -> step(boss, "foot_r", 1.4f);
            default -> { } // attack events: the server owns them
        }
    }

    /** Client tick: locomotion footsteps of the clips whose events are external. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        for (GuilherminoBoss boss : mc.level.getEntitiesOfClass(GuilherminoBoss.class, new AABB(mc.player.blockPosition()).inflate(96))) {
            Clip clip = boss.clip();
            int start;
            if (clip == null) {
                if (boss.flag(GuilherminoBoss.F_RUNNING) && boss.runClip() != null) clip = boss.runClip();
                else if (boss.walkAnimation.isMoving()) clip = boss.walkClip();
                else { LOCO.remove(boss); continue; }
            }
            Loco l = LOCO.computeIfAbsent(boss, k -> new Loco());
            if (l.clip != clip) { l.clip = clip; l.start = boss.tickCount; l.lastCycleTick = -1; }
            start = boss.clip() != null ? boss.clientClipStart : l.start;
            if (clip.external().isEmpty() || clip.loop() != dev.bossestesting.anim.Loop.LOOP) continue;
            int cycle = Math.floorMod(boss.tickCount - start - Brain.TRANSITION, clip.ticks());
            for (var e : clip.events().entrySet()) {
                if (!clip.external().contains(e.getKey()) || e.getValue() != cycle || cycle == l.lastCycleTick) continue;
                step(boss, e.getKey().startsWith("left") ? "foot_l" : "foot_r", clip == boss.runClip() ? 1.3f : 1);
            }
            l.lastCycleTick = cycle;
        }
    }

    static Vec3 anchor(GuilherminoBoss boss, String name) {
        try {
            return AnchorCapture.resolve(boss, name, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)).position();
        } catch (RuntimeException e) {
            return RigAnchors.approximate(boss, name).position();
        }
    }

    static void step(GuilherminoBoss boss, String foot, float strength) {
        var level = boss.level();
        Vec3 p = anchor(boss, foot);
        BlockState ground = level.getBlockState(BlockPos.containing(p.x, p.y - .3, p.z));
        if (!ground.isAir()) for (int i = 0; i < 10 * strength; i++)
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), p.x + (level.random.nextDouble() - .5) * 1.4, p.y + .1,
                p.z + (level.random.nextDouble() - .5) * 1.4, 0, .15, 0);
        level.addParticle(Sfx.smoke(0xff6a6058), p.x, p.y + .2, p.z, 0, .02, 0);
        sound(boss, SoundEvents.IRON_GOLEM_STEP, 1.4f * strength, .5f);
        sound(boss, SoundEvents.ANVIL_LAND, .12f * strength, .45f);
    }

    static void sparks(GuilherminoBoss boss, String anchor, int color, int n) {
        var level = boss.level();
        Vec3 p = anchor(boss, anchor);
        for (int i = 0; i < n; i++) level.addParticle(Sfx.arc(color), p.x, p.y, p.z, (level.random.nextDouble() - .5) * .3, level.random.nextDouble() * .2, (level.random.nextDouble() - .5) * .3);
        for (int i = 0; i < n / 2; i++) level.addParticle(Sfx.ember(color), p.x, p.y, p.z, (level.random.nextDouble() - .5) * .4, level.random.nextDouble() * .3, (level.random.nextDouble() - .5) * .4);
        sound(boss, SoundEvents.TRIDENT_THUNDER.value(), .15f, 2f);
    }

    static void sound(GuilherminoBoss boss, SoundEvent sound, float volume, float pitch) {
        boss.level().playLocalSound(boss.getX(), boss.getY() + 2, boss.getZ(), sound, SoundSource.HOSTILE, volume, pitch, false);
    }
}
