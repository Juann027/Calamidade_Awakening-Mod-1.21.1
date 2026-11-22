package dev.calamidade_awakening.terrain;

import dev.calamidade_awakening.api.TerrainPlan;
import dev.calamidade_awakening.api.v1.EffectSource;
import dev.calamidade_awakening.config.ServerConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Protection checks of a planned terrain edit (0.11.1), run once on the server thread when the plan is ready, right
 * after {@code TerrainEditEvent}. Three layers, in order:
 * <ol>
 *   <li><b>Rules of the game:</b> world border for every source; spawn protection (the server's own check, with the
 *   operator bypass, for a player source; the same area without bypass for other sources); a player source that may
 *   not build (adventure, spectator) refuses the whole edit.</li>
 *   <li><b>Explosion events of NeoForge</b> ({@code terrainExplosionEvent}): a synthetic {@link Explosion} at the
 *   origin, with the source entity of the effect, carrying the positions the edit may change; {@code ExplosionEvent.Start}
 *   (cancelled: the edit is refused) and {@code ExplosionEvent.Detonate} (positions the listeners remove are left out of
 *   the commit). The explosion is never run: no damage, particles, sound or drops. Protection mods that already handle
 *   explosions (claims, regions) protect their areas without knowing the Calamidade.</li>
 *   <li>The result is a bit per plan entry (in plan order) that the commit checks before each write.</li>
 * </ol>
 * Positions offered to the listeners are those that would change at this moment: not air, or a fill or structure
 * write. An entry not offered (air then) is skipped by the commit if something was built there before the marker, so
 * nothing is changed without passing the listeners.
 */
final class TerrainGuard {
    private TerrainGuard() {}

    /**
     * Outcome: {@code refusal} not null when the whole edit is refused; otherwise {@code allowed} (null = every entry)
     * and how many entries each layer left out.
     */
    record Result(TerrainWorks.Reason refusal, BitSet allowed, int byRules, int byListeners, int offered) {
        static Result refused(TerrainWorks.Reason reason) { return new Result(reason, null, 0, 0, 0); }
    }

    /** Online player behind the source, or null (not a player source, or the player left). */
    static ServerPlayer player(ServerLevel level, EffectSource source, Entity entity) {
        if (entity instanceof ServerPlayer player) return player;
        if (source.kind() == EffectSource.Kind.PLAYER && source.uuid() != null)
            return level.getServer().getPlayerList().getPlayer(source.uuid());
        return null;
    }

    /** A player that may not build (adventure, spectator) cannot start terrain edits. */
    static boolean mayEdit(ServerPlayer player) {
        return player == null || (player.mayBuild() && !player.isSpectator());
    }

    /**
     * Spawn protection: for a player, the server's own check ({@code MinecraftServer.isUnderSpawnProtection}: dedicated
     * server, overworld, operators exist, the player is not an operator, Chebyshev distance to the world spawn up to
     * {@code spawn-protection}); for other sources the same area without the operator bypass (conferred with
     * {@code javap -c} in {@code DedicatedServer.isUnderSpawnProtection}).
     */
    static boolean underSpawnProtection(ServerLevel level, BlockPos pos, Player player) {
        MinecraftServer server = level.getServer();
        if (player != null) return server.isUnderSpawnProtection(level, pos, player);
        if (!server.isDedicatedServer() || level.dimension() != Level.OVERWORLD) return false;
        int radius = server.getSpawnProtectionRadius();
        if (radius <= 0 || server.getPlayerList().getOps().isEmpty()) return false;
        return spawnArea(level.getSharedSpawnPos(), pos, radius);
    }

    /** True when {@code pos} is within {@code radius} blocks (Chebyshev, horizontal) of {@code spawn}: vanilla's square. */
    static boolean spawnArea(BlockPos spawn, BlockPos pos, int radius) {
        return Math.max(Math.abs(pos.getX() - spawn.getX()), Math.abs(pos.getZ() - spawn.getZ())) <= radius;
    }

    /** Runs the three layers for a plan (server thread). */
    static Result check(ServerLevel level, TerrainPlan plan, Vec3 origin, EffectSource source, Entity entity,
                        ServerConfig.Terrain rules) {
        ServerPlayer player = player(level, source, entity);
        if (!mayEdit(player)) return Result.refused(TerrainWorks.Reason.PERMISSION);
        boolean events = rules.explosionEvent();
        boolean spawn = rules.spawnProtection();
        WorldBorder border = level.getWorldBorder();
        BitSet allowed = new BitSet(plan.total());
        List<BlockPos> offered = events ? new ArrayList<>() : null;
        int[] offeredIndex = events ? new int[plan.total()] : null;
        int byRules = 0, global = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (TerrainPlan.Section s : plan.sections()) {
            LevelChunkSection section = events ? sectionNow(level, s) : null;
            for (int i = 0; i < s.size(); i++, global++) {
                pos.set(s.blockX(i), s.blockY(i), s.blockZ(i));
                if (!border.isWithinBounds(pos) || (spawn && underSpawnProtection(level, pos, player))) {
                    byRules++;
                    continue;
                }
                if (!events) {
                    allowed.set(global);
                    continue;
                }
                byte action = s.action()[i];
                boolean writes = action == TerrainPlan.FILL || action == TerrainPlan.STRUCTURE;
                if (!writes && section != null) {
                    int index = s.index()[i] & 0xFFFF;
                    BlockState state = section.getBlockState(index & 15, (index >> 8) & 15, (index >> 4) & 15);
                    if (state.isAir()) continue;   // nothing to change now; skipped later if something is built there
                }
                offeredIndex[offered.size()] = global;
                offered.add(pos.immutable());
            }
        }
        int byListeners = 0;
        if (events) {
            Explosion explosion = new Explosion(level, entity, origin.x, origin.y, origin.z, radius(plan, origin), false,
                Explosion.BlockInteraction.DESTROY, offered);
            if (NeoForge.EVENT_BUS.post(new ExplosionEvent.Start(level, explosion)).isCanceled())
                return Result.refused(TerrainWorks.Reason.EXPLOSION);
            NeoForge.EVENT_BUS.post(new ExplosionEvent.Detonate(level, explosion, new ArrayList<>()));
            LongOpenHashSet kept = new LongOpenHashSet(explosion.getToBlow().size());
            for (BlockPos p : explosion.getToBlow()) kept.add(p.asLong());
            for (int i = 0; i < offered.size(); i++) {
                if (kept.contains(offered.get(i).asLong())) allowed.set(offeredIndex[i]);
                else byListeners++;
            }
            if (!offered.isEmpty() && byListeners == offered.size()) return Result.refused(TerrainWorks.Reason.PROTECTED);
            return new Result(null, allowed, byRules, byListeners, offered.size());
        }
        if (byRules == 0) return new Result(null, null, 0, 0, 0);
        if (byRules == plan.total()) return Result.refused(TerrainWorks.Reason.PROTECTED);
        return new Result(null, allowed, byRules, 0, 0);
    }

    /** Section of a plan section if its chunk is loaded and FULL, else null (never loads chunks). */
    private static LevelChunkSection sectionNow(ServerLevel level, TerrainPlan.Section s) {
        LevelChunk chunk = TerrainWriter.fullChunk(level, s.x(), s.z());
        if (chunk == null) return null;
        int index = level.getSectionIndexFromSectionY(s.y());
        return index < 0 || index >= level.getSectionsCount() ? null : chunk.getSection(index);
    }

    /** Radius of the synthetic explosion: distance from the origin to the farthest corner of the planned area. */
    private static float radius(TerrainPlan plan, Vec3 origin) {
        double dx = Math.max(Math.abs(plan.minX() - origin.x), Math.abs(plan.maxX() + 1 - origin.x));
        double dy = Math.max(Math.abs(plan.minY() - origin.y), Math.abs(plan.maxY() + 1 - origin.y));
        double dz = Math.max(Math.abs(plan.minZ() - origin.z), Math.abs(plan.maxZ() + 1 - origin.z));
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
