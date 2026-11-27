package dev.calamidade_awakening.client;

/** Acesso de teste ao decode puro do {@link LightCookies} (package-private). */
public final class LightCookiesAccess {
    private LightCookiesAccess() {}

    /** Largura e altura guardadas, lado da célula e o texel do canto mais distante da última linha guardada. */
    public static int[] chain(int[] px, int w, int h, int side) {
        var d = LightCookies.chain(px, w, h, side);
        return new int[] {d.width(), d.height(), d.cellSize(), d.levels()[0][(d.height() - 1) * d.cellSize() + d.width() - 1]};
    }

    /**
     * Fase 3: {@code k} levels smaller from the mip chain. Width, height, cell, levels, 1 when level 0 of the result is
     * exactly level k of the source (colors and alpha kept), and the side of the last level (1 texel).
     */
    public static int[] shrink(int[] px, int w, int h, int k) {
        var d = LightCookies.chain(px, w, h, 512);
        var s = LightCookies.shrink(d, k);
        boolean same = java.util.Arrays.equals(s.levels()[0], d.levels()[k]);
        int last = s.levels()[s.levels().length - 1].length;
        return new int[] {s.width(), s.height(), s.cellSize(), s.levels().length, same ? 1 : 0, last};
    }
}
