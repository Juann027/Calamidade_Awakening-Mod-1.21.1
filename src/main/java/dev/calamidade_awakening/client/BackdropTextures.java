package dev.calamidade_awakening.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.BackdropMath;
import dev.calamidade_awakening.api.v1.Backdrop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Textures of the custom sky backgrounds on this client (0.14.0), render thread only. A cubemap or panorama PNG is
 * loaded through the game's {@code TextureManager} the first time a background with it is drawn (one GL texture per
 * PNG, shared by every background that uses it, sampled without filtering); a gradient becomes a small generated
 * panorama texture (4 × 512), so every kind goes through the same two geometries and the same shader lookup.
 *
 * <p>Each entry also keeps a coarse equirectangular copy of the colors and coverage on the CPU ({@link #GRID_W} ×
 * {@link #GRID_H}, read once from the PNG): the fog color at the horizon and the coverage at the vanilla sun and moon
 * come from it without reading the GPU. Everything is released on resource reload and when the player leaves the
 * world, and built again on the next use. A PNG that cannot be read is logged once and its background is skipped.
 */
final class BackdropTextures {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    static final int GRID_W = 128, GRID_H = 64;
    private static final int GRADIENT_W = 4, GRADIENT_H = 512;

    /** One loaded texture: its location, the GL id, the CPU grid (RGBA 0..1, row 0 at the zenith) and its kind. */
    static final class Entry {
        final ResourceLocation location;
        final boolean cube;
        final float[] grid;
        final boolean opaque;
        Entry(ResourceLocation location, boolean cube, float[] grid) {
            this.location = location;
            this.cube = cube;
            this.grid = grid;
            boolean all = true;
            for (int i = 3; i < grid.length; i += 4) if (grid[i] < .999f) { all = false; break; }
            opaque = all;
        }

        /** GL texture id (the game's texture object; 0 when it went away). */
        int id() {
            AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(location);
            return t == null ? 0 : t.getId();
        }

        /** Color and coverage of the texture in a direction (texture space, before the turn), nearest grid cell. */
        float[] sample(double x, double y, double z) {
            double[] uv = BackdropMath.panoramaUv(x, y, z);
            int gx = Math.clamp((int) (uv[0] * GRID_W), 0, GRID_W - 1), gy = Math.clamp((int) (uv[1] * GRID_H), 0, GRID_H - 1);
            int k = (gy * GRID_W + gx) * 4;
            return new float[] {grid[k], grid[k + 1], grid[k + 2], grid[k + 3]};
        }
    }

    private final Map<String, Entry> entries = new HashMap<>();
    private final Set<String> failed = new HashSet<>();
    private final Set<ResourceLocation> generated = new HashSet<>();
    private int loads;

    /** The texture of a background, loading it on first use; null when it cannot be read. */
    Entry get(Backdrop backdrop) {
        String key = key(backdrop);
        Entry entry = entries.get(key);
        if (entry != null || failed.contains(key)) return entry;
        try {
            entry = backdrop.kind() == Backdrop.Kind.GRADIENT ? gradient(backdrop, key) : load(backdrop);
            entries.put(key, entry);
            loads++;
        } catch (Exception failure) {
            failed.add(key);
            LOGGER.warn("Backdrop texture {} could not be loaded; the background is skipped: {}", key, failure.toString());
        }
        return entry;
    }

    private static String key(Backdrop b) {
        return b.kind() == Backdrop.Kind.GRADIENT
            ? String.format(java.util.Locale.ROOT, "gradient_%08x_%08x_%08x", b.top(), b.horizon(), b.bottom())
            : b.texture();
    }

    private Entry load(Backdrop backdrop) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation location = ResourceLocation.parse(backdrop.texture());
        boolean cube = backdrop.kind() == Backdrop.Kind.CUBEMAP;
        float[] grid = new float[GRID_W * GRID_H * 4];
        try (InputStream in = mc.getResourceManager().getResourceOrThrow(location).open(); NativeImage image = NativeImage.read(in)) {
            int w = image.getWidth(), h = image.getHeight();
            if (cube && (w * 2 != h * 3))
                throw new IllegalArgumentException("a cubemap PNG must be 3 × 2 square faces, got " + w + " × " + h);
            for (int gy = 0; gy < GRID_H; gy++) for (int gx = 0; gx < GRID_W; gx++) {
                double[] d = BackdropMath.panoramaDirection((gx + .5) / GRID_W, (gy + .5) / GRID_H);
                double[] uv = cube ? BackdropMath.cubeUv(d[0], d[1], d[2]) : BackdropMath.panoramaUv(d[0], d[1], d[2]);
                int px = Math.clamp((int) (uv[0] * w), 0, w - 1), py = Math.clamp((int) (uv[1] * h), 0, h - 1);
                int abgr = image.getPixelRGBA(px, py);
                int k = (gy * GRID_W + gx) * 4;
                grid[k] = (abgr & 0xFF) / 255f;
                grid[k + 1] = (abgr >> 8 & 0xFF) / 255f;
                grid[k + 2] = (abgr >> 16 & 0xFF) / 255f;
                grid[k + 3] = (abgr >>> 24) / 255f;
            }
        }
        AbstractTexture texture = mc.getTextureManager().getTexture(location);
        texture.setFilter(false, false);
        return new Entry(location, cube, grid);
    }

    private Entry gradient(Backdrop b, String key) {
        float[] grid = new float[GRID_W * GRID_H * 4];
        for (int gy = 0; gy < GRID_H; gy++) {
            float[] c = BackdropMath.gradient(b.top(), b.horizon(), b.bottom(), Math.cos(Math.PI * (gy + .5) / GRID_H));
            for (int gx = 0; gx < GRID_W; gx++) System.arraycopy(c, 0, grid, (gy * GRID_W + gx) * 4, 4);
        }
        NativeImage image = new NativeImage(GRADIENT_W, GRADIENT_H, false);
        for (int y = 0; y < GRADIENT_H; y++) {
            // Row y of a panorama is at elevation 90 − 180 (y + .5) / H, so its height is cos(π (y + .5) / H).
            float[] c = BackdropMath.gradient(b.top(), b.horizon(), b.bottom(), Math.cos(Math.PI * (y + .5) / GRADIENT_H));
            int abgr = Math.round(c[3] * 255) << 24 | Math.round(c[2] * 255) << 16 | Math.round(c[1] * 255) << 8 | Math.round(c[0] * 255);
            for (int x = 0; x < GRADIENT_W; x++) image.setPixelRGBA(x, y, abgr);
        }
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(CalamidadeAwakening.ID, "backdrop/" + key);
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(false, false);
        Minecraft.getInstance().getTextureManager().register(location, texture);
        generated.add(location);
        return new Entry(location, false, grid);
    }

    /**
     * Releases everything: generated gradient textures are closed and unregistered, PNG textures are released from the
     * {@code TextureManager} (closed; loaded again on the next use), and the CPU grids are dropped.
     */
    void clear() {
        var manager = Minecraft.getInstance().getTextureManager();
        for (Entry e : entries.values()) manager.release(e.location);
        entries.clear();
        generated.clear();
        failed.clear();
    }

    /** Loaded entries (F7 and tests). */
    int size() { return entries.size(); }
    /** Loads since the game started (tests: reuse means this does not grow). */
    int loads() { return loads; }
}
