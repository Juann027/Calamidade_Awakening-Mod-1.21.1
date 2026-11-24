package dev.bossestesting.encounter;

import dev.bossestesting.BtConfig;
import dev.bossestesting.combat.Hits;
import dev.bossestesting.entity.GuilherminoBoss;
import dev.bossestesting.entity.GuilherminoFinale;
import dev.bossestesting.entity.GuilherminoMissile;
import dev.bossestesting.entity.JarGolem;
import dev.bossestesting.fx.Fx;
import dev.bossestesting.fx.Sfx;
import dev.bossestesting.net.MusicPayload;
import dev.bossestesting.registry.ModEntities;
import dev.calamidade_awakening.api.v1.Bosses;
import dev.calamidade_awakening.api.v1.Effects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One fight, server side: who takes part (players within the arena radius, refreshed every half second, late
 * joiners included), the phase 2 music, the swap of the first form for the second, residual heat zones, missiles in
 * the air and the cleanup. Nothing here touches blocks: terrain goes through the effects (Terrain API).
 */
public final class Encounter {
    static final Logger LOG = LoggerFactory.getLogger("bossestesting");

    public enum Stage { PHASE1, TRANSFORM, PHASE2, DEATH, ENDED }

    private record Heat(Vec3 center, double radius, long until) {}

    private final UUID id;
    private final ResourceKey<Level> dimension;
    private UUID bossId;
    private Stage stage;
    private boolean music;
    private int missing;
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> listening = new HashSet<>();
    private final List<Heat> heat = new ArrayList<>();
    private final List<GuilherminoMissile> missiles = new ArrayList<>();

    Encounter(UUID id, ServerLevel level, GuilherminoBoss boss, Stage stage) {
        this.id = id;
        this.dimension = level.dimension();
        this.bossId = boss.getUUID();
        this.stage = stage;
        this.music = stage == Stage.PHASE2;
    }

    public UUID id() { return id; }
    public Stage stage() { return stage; }
    public boolean ended() { return stage == Stage.ENDED; }
    public boolean music() { return music; }
    public Set<UUID> participants() { return participants; }

    /** After a restart: the controller is saved with the entity; attach one only when it is missing. */
    void restored(GuilherminoBoss boss) {
        if (Bosses.get(boss).isEmpty()) Bosses.attach(boss, boss instanceof GuilherminoFinale ? BossDefs.PHASE2 : BossDefs.PHASE1);
    }

    public boolean isParticipant(Player p) { return participants.contains(p.getUUID()); }

    private ServerLevel level(MinecraftServer server) { return server.getLevel(dimension); }

    public GuilherminoBoss boss(MinecraftServer server) {
        ServerLevel level = level(server);
        return level != null && level.getEntity(bossId) instanceof GuilherminoBoss b ? b : null;
    }

    // ---------------------------------------------------------------- tick

    void tick(MinecraftServer server) {
        if (ended()) return;
        ServerLevel level = level(server);
        GuilherminoBoss boss = boss(server);
        if (level == null || boss == null) {
            // Unloaded with nobody around, or removed. Give the chunk a few seconds before calling it over.
            if (++missing > 200) end(server, false);
            return;
        }
        missing = 0;
        if (server.getTickCount() % 10 == 0) refresh(level, boss);
        tickHeat(level, boss);
        missiles.removeIf(m -> m.isRemoved());
    }

    private void refresh(ServerLevel level, GuilherminoBoss boss) {
        double r = BtConfig.arenaRadius();
        for (ServerPlayer p : level.players()) {
            boolean in = p.isAlive() && !p.isSpectator() && p.distanceTo(boss) <= r;
            boolean out = !p.isAlive() || p.isSpectator() || p.distanceTo(boss) > r + 16;
            if (in) participants.add(p.getUUID());
            else if (out && participants.remove(p.getUUID())) stopMusic(p, 60);
        }
        participants.removeIf(u -> level.getServer().getPlayerList().getPlayer(u) == null);
        if (music && stage != Stage.DEATH) {
            for (UUID u : participants) {
                if (listening.contains(u)) continue;
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(u);
                if (p == null) continue;
                PacketDistributor.sendToPlayer(p, MusicPayload.play(id));
                listening.add(u);
            }
        }
    }

    private void tickHeat(ServerLevel level, GuilherminoBoss boss) {
        long now = level.getGameTime();
        for (Iterator<Heat> it = heat.iterator(); it.hasNext(); ) {
            Heat h = it.next();
            if (now >= h.until()) { it.remove(); continue; }
            if (now % 10 == 0) {
                for (LivingEntity e : Hits.victims(level, new AABB(h.center(), h.center()).inflate(h.radius(), 2, h.radius()), boss)) {
                    if (e.position().distanceTo(h.center()) > h.radius() + .5) continue;
                    e.invulnerableTime = 0;
                    e.hurt(level.damageSources().hotFloor(), 2);
                    e.igniteForSeconds(2);
                }
                Sfx.burst(level, Sfx.ember(Fx.P2_EMBER), h.center().add(0, .3, 0), 6, h.radius() * .4, .1, .05);
                Sfx.burst(level, Sfx.smoke(0xff2a2a2a), h.center().add(0, .6, 0), 3, h.radius() * .4, .2, .02);
            }
        }
    }

    // ---------------------------------------------------------------- services for the actions

    /** Starts a camera effect for every participant (or the players near `pos` before anyone joined). */
    public void startCams(net.minecraft.resources.ResourceLocation cam, Vec3 pos, float yaw) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = level(server);
        if (level == null) return;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.distanceToSqr(pos) > 72 * 72) continue;
            if (!participants.isEmpty() && !participants.contains(p.getUUID()) && p.distanceToSqr(pos) > 48 * 48) continue;
            Effects.start(cam, p, pos, yaw, 0);
        }
    }

    /** Sends a Boss API signal to the controller of `boss`. */
    public void signal(Entity boss, String name) {
        if (boss instanceof LivingEntity l) Bosses.get(l).ifPresent(h -> h.signal(name));
        if (name.equals("sobrecarga")) stage = Stage.TRANSFORM;
    }

    public void heatZone(Vec3 center, double radius, int ticks) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server == null ? null : level(server);
        if (level != null) heat.add(new Heat(center, radius, level.getGameTime() + ticks));
    }

    public void trackMissile(GuilherminoMissile m) { missiles.add(m); }

    /**
     * The swap (end of the overload): the first form leaves without loot, the second appears on the same spot with
     * the same yaw and target, a new Boss API controller is attached in the same tick (one bar at a time) and the
     * reveal starts (cyan ignition and the music).
     */
    public void swapToPhase2(JarGolem old) {
        ServerLevel level = (ServerLevel) old.level();
        GuilherminoFinale next = ModEntities.FINALE.get().create(level);
        if (next == null) { LOG.error("Could not create the second form"); return; }
        next.moveTo(old.getX(), old.getY(), old.getZ(), old.getYRot(), old.getXRot());
        next.setYBodyRot(old.yBodyRot);
        next.setYHeadRot(old.getYHeadRot());
        next.setEncounterId(id);
        next.applyConfiguredHealth(1);
        next.setPersistenceRequired();
        LivingEntity target = old.getTarget();
        Bosses.get(old).ifPresent(Bosses.Handle::detach);
        old.discard();
        level.addFreshEntity(next);
        if (target != null && target.isAlive()) next.setTarget(target);
        Bosses.attach(next, BossDefs.PHASE2);
        bossId = next.getUUID();
        stage = Stage.PHASE2;
        next.phase2().reveal();
    }

    /** The phase 2 track starts now for every participant (and later for late joiners). Never restarted. */
    public void startMusic() {
        if (music) return;
        music = true;
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = level(server);
        GuilherminoBoss boss = boss(server);
        if (level != null && boss != null) refresh(level, boss);
    }

    /** Definitive defeat started: the music fades out over the death sequence. */
    public void defeated() {
        stage = Stage.DEATH;
        music = false;
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) for (UUID u : listening) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) PacketDistributor.sendToPlayer(p, MusicPayload.stop(id, 100));
        }
        listening.clear();
    }

    /** A participant left by logout or dimension change. */
    void forget(ServerPlayer p, boolean sendStop) {
        participants.remove(p.getUUID());
        if (listening.remove(p.getUUID()) && sendStop) PacketDistributor.sendToPlayer(p, MusicPayload.stop(id, 20));
    }

    private void stopMusic(ServerPlayer p, int fade) {
        if (listening.remove(p.getUUID())) PacketDistributor.sendToPlayer(p, MusicPayload.stop(id, fade));
    }

    /** The boss entity is gone for good (death sequence over, or removed). */
    public void bossGone(GuilherminoBoss boss, boolean defeated) {
        var server = boss.getServer();
        if (server != null) end(server, defeated);
    }

    /** Ends the fight: music, missiles, heat, the controller (bar and skies are released by the engine). */
    void end(MinecraftServer server, boolean defeated) {
        if (ended()) return;
        stage = Stage.ENDED;
        for (UUID u : listening) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) PacketDistributor.sendToPlayer(p, MusicPayload.stop(id, defeated ? 60 : 40));
        }
        listening.clear();
        for (GuilherminoMissile m : missiles) if (!m.isRemoved()) m.fizzle();
        missiles.clear();
        heat.clear();
        GuilherminoBoss boss = boss(server);
        if (boss != null && !boss.isDeadOrDying()) Bosses.get(boss).ifPresent(Bosses.Handle::detach);
    }

    /** Command cleanup: removes the boss without loot and ends everything. */
    void abort(MinecraftServer server) {
        GuilherminoBoss boss = boss(server);
        end(server, false);
        if (boss != null) {
            Bosses.get(boss).ifPresent(Bosses.Handle::detach);
            boss.discard();
        }
    }
}
