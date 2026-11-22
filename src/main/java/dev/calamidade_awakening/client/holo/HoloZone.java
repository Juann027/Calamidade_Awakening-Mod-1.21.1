package dev.calamidade_awakening.client.holo;

/**
 * Interactive region recorded while a panel is drawn (panel local units). Clicks use the list of the last frame drawn,
 * so what the player sees is exactly what answers. Adapted from the RespectCraft holographic menu ({@code HoloZone}).
 */
final class HoloZone {
    /** Captured drag: keeps receiving the aim (on the panel plane) until release, even outside the region. */
    interface Drag {
        void move(float x, float y);
        /** {@code click} = released almost without moving. */
        void release(float x, float y, boolean click);
    }

    float x, y, w, h;
    Runnable click;
    Drag drag;
    /** Wheel over the region (changes a value); receives the wheel step. */
    java.util.function.DoubleConsumer scroll;

    HoloZone set(float x, float y, float w, float h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        click = null; drag = null; scroll = null;
        return this;
    }

    boolean contains(float px, float py) { return px >= x && py >= y && px < x + w && py < y + h; }
}
