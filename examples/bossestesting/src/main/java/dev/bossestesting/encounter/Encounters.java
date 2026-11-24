package dev.bossestesting.encounter;

import dev.bossestesting.entity.GuilherminoBoss;
import dev.bossestesting.entity.GuilherminoFinale;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Live encounters of the server, keyed by id; rebuilt from the boss entity after a restart. */
public final class Encounters {
    private static final Map<UUID, Encounter> LIVE = new LinkedHashMap<>();

    private Encounters() {}

    /** The encounter of a boss, created on first use (or recreated after a restart from the saved id). */
    public static Encounter of(GuilherminoBoss boss, ServerLevel level) {
        UUID id = boss.encounterId();
        if (id == null) {
            id = UUID.randomUUID();
            boss.setEncounterId(id);
        }
        Encounter e = LIVE.get(id);
        if (e == null || e.ended()) {
            e = new Encounter(id, level, boss, boss instanceof GuilherminoFinale ? Encounter.Stage.PHASE2 : Encounter.Stage.PHASE1);
            LIVE.put(id, e);
            e.restored(boss);
        }
        return e;
    }

    public static Encounter create(ServerLevel level, GuilherminoBoss boss, Encounter.Stage stage) {
        UUID id = UUID.randomUUID();
        boss.setEncounterId(id);
        Encounter e = new Encounter(id, level, boss, stage);
        LIVE.put(id, e);
        return e;
    }

    public static Collection<Encounter> all() { return new ArrayList<>(LIVE.values()); }

    public static void tick(MinecraftServer server) {
        for (Encounter e : all()) {
            e.tick(server);
            if (e.ended()) LIVE.remove(e.id());
        }
    }

    /** Ends every encounter (command "clear"). */
    public static int clearAll(MinecraftServer server) {
        int n = 0;
        for (Encounter e : all()) { e.abort(server); n++; }
        LIVE.clear();
        return n;
    }

    /** Forge events that reach every encounter: logouts and dimension changes stop the music right away. */
    public static final class Events {
        private Events() {}

        @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) for (Encounter enc : all()) enc.forget(p, false);
        }

        @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) for (Encounter enc : all()) enc.forget(p, true);
        }

        @SubscribeEvent public static void stopping(ServerStoppingEvent e) { LIVE.clear(); }
    }
}
