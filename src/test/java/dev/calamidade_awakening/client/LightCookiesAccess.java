package dev.calamidade_awakening.client;

/** Acesso de teste ao decode puro do {@link LightCookies} (package-private). */
public final class LightCookiesAccess {
    private LightCookiesAccess() {}

    /** Largura e altura guardadas, lado da célula e o texel do canto mais distante da última linha guardada. */
    public static int[] chain(int[] px, int w, int h, int side) {
        var d = LightCookies.chain(px, w, h, side);
        return new int[] {d.width(), d.height(), d.cellSize(), d.levels()[0][(d.height() - 1) * d.cellSize() + d.width() - 1]};
    }
}
