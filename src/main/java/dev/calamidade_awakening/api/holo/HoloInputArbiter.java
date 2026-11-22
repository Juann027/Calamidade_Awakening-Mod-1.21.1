package dev.calamidade_awakening.api.holo;

import java.util.Arrays;

/**
 * Owner of each input while the Star Atlas is in the world (0.11.2), pure. The owner of a button is decided <b>when it
 * goes down</b> and holds until it goes up: a press that started on a panel never becomes an attack, mining or use,
 * even if the aim leaves the panel while dragging; a press that started in the world never becomes a panel click. The
 * panel wins only within reach and strictly <b>closer</b> than the vanilla target (a block or mob in front of the
 * panel stays the world's). Adapted from the RespectCraft holographic menu ({@code com.respectcraft.ui
 * .HoloInputArbiter}, stage 4.8.4-B, same author); no RespectCraft class is used. Not public API.
 */
public final class HoloInputArbiter {
    public enum Owner { NONE, HOLO, WORLD }

    /** Mouse buttons tracked (GLFW has 8). */
    public static final int MOUSE_BUTTONS = 8;
    /** Vanilla actions (attack, use, pick block) bound to a keyboard key. */
    public static final int ATTACK = 0, USE = 1, PICK = 2;
    public static final int SOURCES = MOUSE_BUTTONS + 3;

    private final Owner[] owner = new Owner[SOURCES];

    public HoloInputArbiter() { clear(); }

    /** Source of an action ({@link #ATTACK}, {@link #USE}, {@link #PICK}) bound to the keyboard. */
    public static int keySource(int action) { return MOUSE_BUTTONS + action; }

    public static boolean validSource(int source) { return source >= 0 && source < SOURCES; }

    /** Does the panel win? {@code worldDistance} is the vanilla target distance ({@code +inf} when it hits nothing). */
    public static boolean hologramWins(boolean holoHit, double holoDistance, double worldDistance, double reach) {
        return holoHit && holoDistance >= 0.0 && holoDistance <= reach && holoDistance < worldDistance;
    }

    /** Button down: records and returns the owner until it goes up. A new press always decides again. */
    public Owner press(int source, boolean hologramWins) {
        if (!validSource(source)) return Owner.WORLD;
        owner[source] = hologramWins ? Owner.HOLO : Owner.WORLD;
        return owner[source];
    }

    /** Button up: returns who owned it (the release goes to the same owner) and frees the source. */
    public Owner release(int source) {
        if (!validSource(source)) return Owner.NONE;
        Owner o = owner[source];
        owner[source] = Owner.NONE;
        return o;
    }

    public Owner owner(int source) { return validSource(source) ? owner[source] : Owner.NONE; }

    /** Some pressed source belongs to the panels. */
    public boolean anyHolo() {
        for (Owner o : owner) if (o == Owner.HOLO) return true;
        return false;
    }

    /** Forgets every owner (logout). */
    public void clear() { Arrays.fill(owner, Owner.NONE); }
}
