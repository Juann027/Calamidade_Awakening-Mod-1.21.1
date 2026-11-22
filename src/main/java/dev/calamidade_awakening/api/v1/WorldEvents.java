package dev.calamidade_awakening.api.v1;

import dev.calamidade_awakening.world.WorldEventServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Global events (0.12.0): an effect seen beyond its range, by every player of a dimension or of the whole server, in
 * any dimension, including players who log in, change dimension or respawn while it runs (they get it in the right
 * phase). The effect is an ordinary registered effect: its {@link Timeline} (with sustain to loop until
 * {@link Handle#release()}), its {@link Sky} layers and its {@link Atmosphere} work as usual; its cues run on the server
 * once per tick of the event clock, in the origin level at the origin position. Built-in geometry, post layers, beams,
 * lights and terrain edits of the effect are not drawn for a global event (they belong to a place: start an
 * {@link Scope#AREA AREA} instance for them).
 *
 * <pre>{@code
 * var eclipse = WorldEvents.start(ECLIPSE, WorldEvents.Scope.SERVER, server.overworld(),
 *     WorldEvents.Options.defaults().persistent(true).priority(80));
 * // ... later, from a cue, a command or a boss death:
 * WorldEvents.get(server, ECLIPSE).ifPresent(WorldEvents.Handle::release);
 * }</pre>
 *
 * <p><b>Limits.</b> At most {@code worldEventsMax} global events at once (server config, 4 by default) and one per
 * effect. A start above the limit replaces the active event with the lowest priority when the new one has a higher
 * priority, otherwise it is refused (empty Optional, no error). <b>Persistence.</b> Events started with
 * {@link Options#persistent(boolean) persistent(true)} survive a server restart (saved with the world) and resume in the
 * same phase; the others end when the server stops. <b>Network.</b> Uses the channels {@code world_event_v1} and
 * {@code world_end_v1}: run the same engine version on the server and every client. Call everything on the server
 * thread.
 */
public final class WorldEvents {
    /** Highest priority. */
    public static final int MAX_PRIORITY = 100;

    /** Who sees the event. */
    public enum Scope {
        /** An ordinary instance at a position: players within the effect's range (what {@link Effects#start} does). */
        AREA,
        /** Every player in the origin dimension. */
        DIMENSION,
        /** Every player on the server, in any dimension, on their own sky. */
        SERVER
    }

    /**
     * Options of a start: origin position (blocks; the origin level's spawn point when null), persistence and priority
     * (0..{@value #MAX_PRIORITY}, 50 by default). Immutable; every {@code with} method returns a copy.
     *
     * @param position   origin position in the origin level, blocks, or null for its spawn point
     * @param persistent survives a server restart (DIMENSION and SERVER scopes)
     * @param priority   0..{@value #MAX_PRIORITY}: who stays when the limit is full; also drawn first
     */
    public record Options(Vec3 position, boolean persistent, int priority) {
        public Options {
            if (position != null && !(Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z)))
                throw new IllegalArgumentException("Expected a finite origin position");
            if (priority < 0 || priority > MAX_PRIORITY)
                throw new IllegalArgumentException("Expected a priority in 0.." + MAX_PRIORITY + ": " + priority);
        }

        /** Spawn point, not persistent, priority 50. */
        public static Options defaults() { return new Options(null, false, 50); }

        /** Copy with another origin position (blocks). */
        public Options at(Vec3 position) { return new Options(Objects.requireNonNull(position), persistent, priority); }

        /** Copy that survives (true) or not (false) a server restart. */
        public Options persistent(boolean value) { return new Options(position, value, priority); }

        /** Copy with another priority, 0..{@value #MAX_PRIORITY}. */
        public Options priority(int value) { return new Options(position, persistent, value); }
    }

    /**
     * Server-side control of a global event (or of the AREA instance). {@link #release()} ends the sustain loop and
     * plays the exit; {@link #stop()} ends it right away; both reach every player, including late arrivals.
     */
    public interface Handle extends Effects.Handle {
        /** Effect of the event. */
        ResourceLocation id();
        /** Scope it was started with. */
        Scope scope();
        /** True when it survives a server restart. */
        boolean persistent();
        /** Priority, 0..{@value #MAX_PRIORITY}. */
        int priority();
    }

    private WorldEvents() {}

    /**
     * Starts a global event of a registered effect. {@code origin} is the level whose players see a
     * {@link Scope#DIMENSION} event and where the cues run. {@link Scope#AREA} starts an ordinary instance at the
     * position (persistence and priority do not apply). Empty when an event of this effect is already active, when the
     * limit is full and no active event has a lower priority, or, for AREA, when {@link Effects#start} refuses. Unknown
     * effects throw {@link IllegalArgumentException}. Server thread.
     */
    public static Optional<Handle> start(ResourceLocation id, Scope scope, ServerLevel origin, Options options) {
        return WorldEventServer.start(Objects.requireNonNull(id), Objects.requireNonNull(scope), Objects.requireNonNull(origin),
            Objects.requireNonNull(options));
    }

    /** The active global event of an effect (DIMENSION or SERVER scope), if any; also after a restart. Server thread. */
    public static Optional<Handle> get(MinecraftServer server, ResourceLocation id) {
        return WorldEventServer.get(Objects.requireNonNull(server), Objects.requireNonNull(id));
    }

    /** Every active global event, oldest first. Server thread. */
    public static List<Handle> active(MinecraftServer server) {
        return WorldEventServer.active(Objects.requireNonNull(server));
    }
}
