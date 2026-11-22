package dev.calamidade_awakening.client;

import dev.calamidade_awakening.api.Basis;
import dev.calamidade_awakening.api.FrameBudget;
import dev.calamidade_awakening.api.SkyMath;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.api.WorldEventRules;
import dev.calamidade_awakening.api.v1.Atmosphere;
import dev.calamidade_awakening.api.v1.WorldEvents;
import dev.calamidade_awakening.net.WorldEventSync;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Global events this client knows (0.12.0). Not tied to the level: SERVER events stay through dimension changes and
 * respawns (the server resends them anyway; the key replaces the old copy), DIMENSION events count only while the
 * client is in their dimension. Cleared on logout. The age comes from the level's game time, which every dimension
 * shares with the server, so the phase is right for late arrivals. Feeds the sky pass and the atmosphere mix; the weight
 * is the opacity envelope only (no range: a global event is everywhere).
 */
final class WorldEventsClient {
    /** One known event. */
    record Event(WorldEventSync sync, VisualKind kind) {}

    private final Map<Long, Event> events = new LinkedHashMap<>();

    void receive(WorldEventSync packet) {
        VisualKind kind = VisualKind.byId(VisualKind.localId(packet.effect()));
        if (kind == null) return;
        events.put(packet.key(), new Event(packet, kind));
    }

    void end(long key) { events.remove(key); }

    void clear() { events.clear(); }

    /** Drops events that are over on this clock (the server also sends their end). */
    void tick(ClientLevel level) {
        if (level == null) return;
        long now = level.getGameTime();
        events.values().removeIf(e -> WorldEventRules.finished(e.kind().timeline(), e.kind().duration,
            WorldEventRules.age(now, e.sync().tick()), e.sync().released()));
    }

    private boolean applies(Event e, ClientLevel level) {
        return WorldEventRules.applies(e.sync().scope(), e.sync().dimension().toString(), level.dimension().location().toString());
    }

    /** Events that apply here (F7). */
    int visible(ClientLevel level) {
        if (level == null) return 0;
        int n = 0;
        for (Event e : events.values()) if (applies(e, level)) n++;
        return n;
    }

    /** True while an event with a HERO effect applies here (AUTO quality never changes during one). */
    boolean heroActive(ClientLevel level) {
        if (level == null) return false;
        for (Event e : events.values()) if (applies(e, level) && e.kind().hero()) return true;
        return false;
    }

    private double localAge(Event e, double age) {
        return WorldEventRules.localAge(e.kind().timeline(), age, e.sync().released());
    }

    /** Sky layers of the events that apply here; world events come first (rank 1 + priority). */
    List<SkyMath.Candidate<SkyFrame.Resolved>> skies(ClientLevel level, Vec3 camera, float partial, double timeOfDay) {
        if (level == null || events.isEmpty()) return List.of();
        List<SkyMath.Candidate<SkyFrame.Resolved>> out = new ArrayList<>(2);
        double now = level.getGameTime() + partial;
        for (Event e : events.values()) {
            var layers = e.kind().sky();
            if (layers.isEmpty() || !applies(e, level)) continue;
            double age = now - e.sync().tick();
            if (age < 0) continue;
            double local = localAge(e, age);
            float weight = (float) FrameBudget.envelope(local, e.kind().duration);
            if (!(weight > 0)) continue;
            var sampler = e.kind().clock().at(local);
            long seed = e.sync().seed();
            for (var layer : layers) {
                var state = layer.sample(sampler, weight);
                if (!state.visible()) continue;
                var anchor = StageScene.anchorDirection(layer, camera, e.sync().origin(), Basis.of(0, 0), timeOfDay);
                if (anchor == null) continue;
                out.add(new SkyMath.Candidate<>(new SkyFrame.Resolved(layer, state, state.direction(anchor),
                    (seed & 0xFFFFL) / 65535f, seed, age, -e.sync().key(), e.kind().hero()), e.kind().hero(),
                    state.weight(), -e.sync().key(), 1 + e.sync().priority()));
            }
        }
        return out;
    }

    /** Atmospheres of the events that apply here, for the engine's mix. */
    List<Atmosphere.State> atmospheres(ClientLevel level, float partial) {
        if (level == null || events.isEmpty()) return List.of();
        List<Atmosphere.State> out = new ArrayList<>(1);
        double now = level.getGameTime() + partial;
        for (Event e : events.values()) {
            var atmosphere = e.kind().atmosphere();
            if (atmosphere == null || !applies(e, level)) continue;
            double age = now - e.sync().tick();
            if (age < 0) continue;
            double local = localAge(e, age);
            out.add(atmosphere.sample(e.kind().clock().at(local), (float) FrameBudget.envelope(local, e.kind().duration)));
        }
        return out;
    }

    /** Scope names of the known events, for tests and diagnostics. */
    List<WorldEvents.Scope> scopes() { return events.values().stream().map(e -> e.sync().scope()).toList(); }
}
