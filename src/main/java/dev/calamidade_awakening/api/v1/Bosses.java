package dev.calamidade_awakening.api.v1;

import dev.calamidade_awakening.boss.BossServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Boss controllers (0.13.0): register {@link Boss} definitions and attach them to entities of the addon.
 *
 * <pre>{@code
 * // Mod constructor (common):
 * Bosses.define(SENTINELA);
 * // Server, when the addon decides (a command, an altar, a spawn of its own):
 * Bosses.attach(golem, SENTINELA.id()).ifPresent(h -> h.signal("acordar"));
 * }</pre>
 *
 * <ul>
 * <li><b>Nothing automatic.</b> Defining a boss does nothing; only {@link #attach} creates a controller.</li>
 * <li><b>Server authority.</b> Phases, triggers, health, persistence and the effects that change the world run on the
 * server thread. Clients receive one {@code boss_v1} packet per state change (and on arrival); never per tick.</li>
 * <li><b>Persistence.</b> The controller is saved with the entity (phase, time in the phase, hits, pending sequence)
 * and resumes when the entity loads again. A saved boss whose definition is gone (addon removed or renamed) is
 * dropped with a warning in the log.</li>
 * <li><b>Cleanup.</b> When the entity dies, is discarded, unloads or changes dimension, the bar disappears and the
 * phase effects and global event are released. Death runs the death transition first.</li>
 * <li><b>Limits.</b> At most {@code bossMaxActive} controllers (server config, 16 by default); effects started by
 * phases use {@link EffectSource#boss} and the {@code maxPerBoss} limit (8 by default), inside the usual total of 64
 * and the player reserve.</li>
 * <li><b>Failures.</b> An exception in a callback or predicate of the addon is logged with the boss and the phase;
 * after 3 failures in a row the controller is detached (effects released, bar removed) and an error is logged.</li>
 * </ul>
 *
 * <p>Network: channel {@code boss_v1} (new in 0.13.0); run the same engine version on the server and every client.
 */
public final class Bosses {
    private static final Map<ResourceLocation, Boss> DEFINITIONS = new ConcurrentHashMap<>();

    /**
     * Control of one attached controller, server side. Methods return false once it is detached. A handle belongs to
     * one loaded entity object: when the chunk unloads or the entity changes dimension it stops being {@link #alive()},
     * and the controller resumed when the entity loads again has a new handle; get it with {@link Bosses#get(Entity)}
     * instead of keeping handles across unloads.
     */
    public interface Handle {
        /** Boss definition id. */
        ResourceLocation id();
        /** The entity. */
        LivingEntity entity();
        /** Current phase name. */
        String phase();
        /** Ticks in the current phase. */
        long phaseTicks();
        /** True while a transition sequence is playing. */
        boolean locked();
        /** True while attached to this entity object (false after detach, death, removal or unload). */
        boolean alive();

        /**
         * Sends a signal for {@link Boss.Trigger#signal} transitions. It waits while the phase is younger than
         * {@code minDwell}, is seen by the next evaluation of the rules (normally the next server tick) and is then
         * dropped, as it is when the phase changes or a sequence is playing, so a signal never fires a transition long
         * after it was sent. {@code [a-z0-9_]}.
         */
        boolean signal(String name);

        /** Forces a phase (debug, scripted fights): runs exit and entry as a normal change; false when dead or unknown. */
        boolean phase(String name);

        /** Detaches the controller: releases the phase effects and the global event, removes the bar and the saved data. */
        boolean detach();
    }

    private Bosses() {}

    /**
     * Registers a definition (mod construction, both sides). Throws {@link IllegalStateException} when the id is
     * already used.
     */
    public static void define(Boss boss) {
        Objects.requireNonNull(boss, "boss");
        if (DEFINITIONS.putIfAbsent(boss.id(), boss) != null) throw new IllegalStateException("Boss already defined: " + boss.id());
    }

    /** Definition by id. */
    public static Optional<Boss> definition(ResourceLocation id) { return Optional.ofNullable(DEFINITIONS.get(id)); }

    /**
     * Attaches a controller to a living, non-player entity on the server and enters the first phase. Returns the
     * existing handle when the same boss is already attached. Empty (no error) when the entity is dead or removed,
     * already has another boss, or {@code bossMaxActive} is reached. Unknown ids throw {@link IllegalArgumentException}.
     * Server thread.
     */
    public static Optional<Handle> attach(LivingEntity entity, ResourceLocation id) {
        Objects.requireNonNull(entity, "entity");
        Boss boss = definition(Objects.requireNonNull(id, "id")).orElseThrow(() -> new IllegalArgumentException("Unknown boss " + id));
        return BossServer.attach(entity, boss);
    }

    /** The controller attached to an entity, if any. Server thread. */
    public static Optional<Handle> get(Entity entity) { return BossServer.get(Objects.requireNonNull(entity, "entity")); }

    /** True when the entity has a controller attached (server). */
    public static boolean isBoss(Entity entity) { return get(entity).isPresent(); }

    /** Every attached controller. Server thread. */
    public static List<Handle> active(MinecraftServer server) { return BossServer.active(Objects.requireNonNull(server, "server")); }
}
