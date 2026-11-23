package dev.calamidade_awakening.api.v1.client;

import dev.calamidade_awakening.api.v1.Boss;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom boss bar styles (0.13.0, client). A boss whose {@link Boss.Bar#style} names a registered style is drawn by
 * its renderer instead of the vanilla bar; everything else (unknown style, no style, or the accessibility option
 * "force vanilla bars") keeps the vanilla bar. The engine registers {@link Boss.Bar#SEGMENTED}.
 *
 * <p>Renderers draw only; they receive a ready {@link View} (smoothed progress, damage ghost, flash already reduced
 * by the accessibility settings, scale) and return the height they used. No packets: the view is computed from the
 * vanilla bar and the one {@code boss_v1} packet per phase change.
 *
 * <pre>{@code
 * // FMLClientSetupEvent:
 * BossBars.style(id("meumod:runas"), (graphics, x, y, bar) -> {
 *     graphics.fill(x, y, x + (int) (182 * bar.progress()), y + 5, bar.argb());
 *     return 12;
 * });
 * }</pre>
 */
public final class BossBars {
    private static final Map<ResourceLocation, Renderer> STYLES = new ConcurrentHashMap<>();

    /**
     * What a style draws.
     *
     * @param name         bar title (from the vanilla bar)
     * @param progress     health fraction 0..1, smoothed like the vanilla bar
     * @param ghost        trailing fraction 0..1 of recent damage (equal to progress when there is none)
     * @param color        bar color of the current phase
     * @param argb         accent color (high-contrast white when that option is on)
     * @param overlay      vanilla divisions
     * @param label        phase label, or null (also null when the "phase labels" option is off)
     * @param marks        health thresholds of the phases, high to low, inside (0, 1); empty without segments
     * @param flash        0..1 strength of the phase-change flash (already reduced or off by accessibility)
     * @param vulnerable   false while the current phase cancels damage
     * @param scale        bar scale 0.5..2 (accessibility)
     * @param highContrast accessibility option: draw solid, high-contrast colors
     * @param phase        index of the current phase
     */
    public record View(Component name, float progress, float ghost, Boss.BarColor color, int argb, Boss.BarOverlay overlay,
                       Component label, float[] marks, float flash, boolean vulnerable, float scale, boolean highContrast,
                       int phase) {}

    /** Draws a bar. */
    @FunctionalInterface
    public interface Renderer {
        /**
         * Draws the bar with its top-left corner at {@code x}, {@code y} (GUI pixels; the vanilla bar is 182 wide) and
         * returns the height used, in GUI pixels, so the next bar goes below it.
         */
        int draw(GuiGraphics graphics, int x, int y, View bar);
    }

    private BossBars() {}

    /** Registers (or replaces) a style (client setup). */
    public static void style(ResourceLocation id, Renderer renderer) {
        STYLES.put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(renderer, "renderer"));
    }

    /** Renderer of a style, or null. */
    public static Renderer style(ResourceLocation id) { return id == null ? null : STYLES.get(id); }
}
