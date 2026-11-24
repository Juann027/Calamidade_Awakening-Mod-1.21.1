package dev.bossestesting.command;

import dev.bossestesting.ai.Phase1Brain;
import dev.bossestesting.encounter.BossDefs;
import dev.bossestesting.encounter.Encounter;
import dev.bossestesting.encounter.Encounters;
import dev.bossestesting.entity.GuilherminoFinale;
import dev.bossestesting.entity.JarGolem;
import dev.bossestesting.registry.ModEntities;
import dev.calamidade_awakening.api.v1.Bosses;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Creates the boss (commands and the self test): the only way it enters a world. */
public final class Spawner {
    private Spawner() {}

    public record Spawned<T>(T boss, Vec3 ground, int height) {}

    /** Ground under a horizontal spot (motion-blocking surface, leaves ignored). */
    public static Vec3 ground(ServerLevel level, Vec3 spot) {
        int gx = (int) Math.floor(spot.x), gz = (int) Math.floor(spot.z);
        return new Vec3(gx + .5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz), gz + .5);
    }

    /** First form high above the spot (up to 38 blocks of clear air), attached and descending (spawn_landing). */
    public static Spawned<JarGolem> phase1(ServerLevel level, Vec3 spot, float yaw) {
        Vec3 ground = ground(level, spot);
        int clear = 0;
        while (clear < 44 && level.isInWorldBounds(BlockPos.containing(ground.x, ground.y + clear + 6, ground.z))
            && level.getBlockState(BlockPos.containing(ground.x, ground.y + clear + 1, ground.z)).getCollisionShape(level, BlockPos.ZERO).isEmpty()) clear++;
        int height = Math.max(0, Math.min(38, clear - 6));
        JarGolem boss = ModEntities.JAR_GOLEM.get().create(level);
        if (boss == null) throw new IllegalStateException("JarGolem not created");
        boss.moveTo(ground.x, ground.y + height, ground.z, yaw, 0);
        boss.setYBodyRot(yaw);
        boss.applyConfiguredHealth();
        level.addFreshEntity(boss);
        Encounters.create(level, boss, Encounter.Stage.PHASE1);
        Bosses.attach(boss, BossDefs.PHASE1);
        ((Phase1Brain) boss.brain()).land(ground.y);
        return new Spawned<>(boss, ground, height);
    }

    /** Second form directly on the ground (reveal and music start at once). */
    public static Spawned<GuilherminoFinale> phase2(ServerLevel level, Vec3 spot, float yaw) {
        Vec3 ground = ground(level, spot);
        GuilherminoFinale boss = ModEntities.FINALE.get().create(level);
        if (boss == null) throw new IllegalStateException("Finale not created");
        boss.moveTo(ground.x, ground.y, ground.z, yaw, 0);
        boss.setYBodyRot(yaw);
        boss.applyConfiguredHealth(1);
        level.addFreshEntity(boss);
        Encounters.create(level, boss, Encounter.Stage.PHASE2);
        Bosses.attach(boss, BossDefs.PHASE2);
        boss.phase2().reveal();
        return new Spawned<>(boss, ground, 0);
    }
}
