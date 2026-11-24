package dev.bossestesting.fx;

import dev.bossestesting.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side layered sounds and particle bursts (one vanilla packet per call, distributed by the game to players in
 * range). Mechanical identity of phase 1 comes from layered vanilla events at low pitch; no new audio is shipped
 * besides the provided phase 2 track.
 */
public final class Sfx {
    private Sfx() {}

    public static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    public static void play(ServerLevel level, Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    // ---- named layers
    public static void heavyStep(ServerLevel l, Vec3 at) {
        play(l, at, SoundEvents.IRON_GOLEM_STEP, 2.2f, .45f);
        play(l, at, SoundEvents.ANVIL_LAND, .25f, .5f);
    }

    public static void servo(ServerLevel l, Vec3 at, float pitch) {
        play(l, at, SoundEvents.PISTON_EXTEND, 1.2f, pitch);
        play(l, at, SoundEvents.IRON_GOLEM_REPAIR, .8f, pitch * .7f);
    }

    public static void slam(ServerLevel l, Vec3 at, float pitch) {
        play(l, at, SoundEvents.MACE_SMASH_GROUND_HEAVY, 4, pitch);
        play(l, at, SoundEvents.GENERIC_EXPLODE, 4, pitch * .7f);
        play(l, at, SoundEvents.WARDEN_SONIC_BOOM, 1.6f, pitch * .6f);
    }

    public static void charge(ServerLevel l, Vec3 at, float pitch) {
        play(l, at, SoundEvents.BEACON_POWER_SELECT, 2.2f, pitch);
        play(l, at, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.6f, pitch * .8f);
    }

    public static void beam(ServerLevel l, Vec3 at, boolean cyan) {
        play(l, at, SoundEvents.WARDEN_SONIC_BOOM, 3, cyan ? .8f : .6f);
        play(l, at, SoundEvents.BEACON_ACTIVATE, 3, cyan ? 1.4f : .9f);
        play(l, at, SoundEvents.GUARDIAN_ATTACK, 2, cyan ? 1.6f : 1.2f);
    }

    public static void electric(ServerLevel l, Vec3 at, float volume) {
        play(l, at, SoundEvents.TRIDENT_THUNDER, volume, 1.6f);
        play(l, at, SoundEvents.LIGHTNING_BOLT_IMPACT, volume * .6f, 1.4f);
    }

    public static void alarm(ServerLevel l, Vec3 at) {
        play(l, at, SoundEvents.NOTE_BLOCK_BIT, 3, .5f);
        play(l, at, SoundEvents.NOTE_BLOCK_BIT, 3, .6f);
    }

    public static void explosion(ServerLevel l, Vec3 at, float volume) {
        play(l, at, SoundEvents.GENERIC_EXPLODE, volume, .75f + l.random.nextFloat() * .2f);
        play(l, at, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, volume * .6f, .6f);
    }

    // ---- particles
    public static ParticleOptions ember(int argb) { return ColorParticleOption.create(ModParticles.EMBER.get(), argb); }
    public static ParticleOptions smoke(int argb) { return ColorParticleOption.create(ModParticles.SMOKE.get(), argb); }
    public static ParticleOptions arc(int argb) { return ColorParticleOption.create(ModParticles.ARC.get(), argb); }

    public static void burst(ServerLevel l, ParticleOptions p, Vec3 at, int count, double spread, double ySpread, double speed) {
        l.sendParticles(p, at.x, at.y, at.z, count, spread, ySpread, spread, speed);
    }

    /** Debris of the ground under `at`: block fragments, dust, embers and rolling smoke. */
    public static void debris(ServerLevel l, Vec3 at, double radius, int amount, int ember, int smokeColor) {
        BlockState ground = l.getBlockState(BlockPos.containing(at.x, at.y - .5, at.z));
        if (ground.isAir()) ground = Blocks.DIRT.defaultBlockState();
        burst(l, new BlockParticleOption(ParticleTypes.BLOCK, ground), at.add(0, .4, 0), amount, radius * .45, .4, .45);
        burst(l, new BlockParticleOption(ParticleTypes.DUST_PILLAR, ground), at, amount / 2, radius * .5, .2, .3);
        burst(l, ember(ember), at.add(0, .6, 0), amount / 2, radius * .35, .6, .55);
        burst(l, smoke(smokeColor), at.add(0, .8, 0), amount / 3, radius * .4, .5, .06);
    }
}
