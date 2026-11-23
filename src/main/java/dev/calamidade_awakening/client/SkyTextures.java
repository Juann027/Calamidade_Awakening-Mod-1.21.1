package dev.calamidade_awakening.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.calamidade_awakening.api.BackdropMath;
import dev.calamidade_awakening.api.SkySpriteMath;
import dev.calamidade_awakening.api.v1.Backdrop;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The one texture cache of the custom sky on this client (0.14.0; phase 2 as {@code BackdropTextures}, generalized in
 * phase 4): every background, image, flipbook page and impostor page is one GL texture per PNG (and filter), owned and
 * released here, shared by every object that uses it. Render thread only, except the decoding jobs.
 *
 * <p><b>Backgrounds</b> keep the phase 2 behavior: loaded synchronously the first time a background is drawn, uploaded
 * like the game's {@code SimpleTexture} (no filter, no mipmaps, straight alpha, the default repeat wrap), with a coarse
 * equirectangular copy of colors and coverage on the CPU for the horizon fog and the vanilla sun and moon; gradients
 * become a small generated panorama. They are counted in the memory estimate but never refused.
 *
 * <p><b>Sprites</b> (images, sheets, impostor pages) load asynchronously: reading, decoding, halving to the GPU limit
 * (and to 4096), premultiplying, measuring the frame brightness of a sheet and, for {@code SMOOTH}, building the mipmap
 * chain on the CPU (2 × 2 box of premultiplied texels, only down to the levels where every cell of the grid stays whole:
 * {@link SkySpriteMath#gridLevels}; the same result on every driver, and frames never mix) run on a worker; the render
 * thread only uploads the levels, at most {@link SkySpriteMath.Caps#uploads} per frame within
 * {@link SkySpriteMath.Caps#uploadBytes}. A texture that would exceed the profile's memory budget first frees textures unused for
 * {@value #EVICT_FRAMES} frames, then is refused (logged once, shown in F7). Impostor descriptors load the same way.
 * Anything missing or invalid is logged once per resource, counted for F7 and skipped; the rest of the sky goes on.
 * Everything is released after {@value #KEEP_FRAMES} frames without use, on resource reload and when the player leaves
 * the world; jobs of an older generation are dropped when they finish.
 */
final class SkyTextures {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    static final int GRID_W = 128, GRID_H = 64;
    private static final int GRADIENT_W = 4, GRADIENT_H = 512;
    /** Largest side of a sprite texture, pixels (larger PNGs are halved until they fit, with a warning). */
    static final int MAX_SIDE = 4096;
    /** Luminance grid kept for the flash safety net of sheets. */
    static final int LUMA = 64;
    /** Frames a sprite texture stays after it was last used. */
    static final int KEEP_FRAMES = 1200;
    /** Textures unused for this many frames may be freed early to make room within the memory budget. */
    static final int EVICT_FRAMES = 60;
    /** Seconds a sprite fades in once its texture is ready. */
    static final double FADE_SECONDS = .25;

    // ------------------------------------------------------------------------------------------------ backgrounds

    /** One background texture: its key, GL id, CPU grid (RGBA 0..1, row 0 at the zenith) and its kind. */
    static final class Entry {
        final String key;
        final boolean cube;
        final float[] grid;
        final boolean opaque;
        int id;
        long bytes;

        Entry(String key, boolean cube, float[] grid) {
            this.key = key;
            this.cube = cube;
            this.grid = grid;
            boolean all = true;
            for (int i = 3; i < grid.length; i += 4) if (grid[i] < .999f) { all = false; break; }
            opaque = all;
        }

        /** GL texture id (0 once released). */
        int id() { return id; }

        /** Color and coverage of the texture in a direction (texture space, before the turn), nearest grid cell. */
        float[] sample(double x, double y, double z) {
            double[] uv = BackdropMath.panoramaUv(x, y, z);
            int gx = Math.clamp((int) (uv[0] * GRID_W), 0, GRID_W - 1), gy = Math.clamp((int) (uv[1] * GRID_H), 0, GRID_H - 1);
            int k = (gy * GRID_W + gx) * 4;
            return new float[] {grid[k], grid[k + 1], grid[k + 2], grid[k + 3]};
        }
    }

    private final Map<String, Entry> backdrops = new HashMap<>();
    private final Set<String> backdropFailed = new HashSet<>();
    private int backdropLoads;

    /** The texture of a background, loading it on first use; null when it cannot be read. */
    Entry get(Backdrop backdrop) {
        String key = key(backdrop);
        Entry entry = backdrops.get(key);
        if (entry != null || backdropFailed.contains(key)) return entry;
        try {
            entry = backdrop.kind() == Backdrop.Kind.GRADIENT ? gradient(backdrop, key) : load(backdrop, key);
            backdrops.put(key, entry);
            resident += entry.bytes;
            backdropLoads++;
        } catch (Exception failure) {
            backdropFailed.add(key);
            problem(key, "Backdrop texture " + key + " could not be loaded; the background is skipped: " + failure);
        }
        return entry;
    }

    private static String key(Backdrop b) {
        return b.kind() == Backdrop.Kind.GRADIENT
            ? String.format(java.util.Locale.ROOT, "gradient_%08x_%08x_%08x", b.top(), b.horizon(), b.bottom())
            : b.texture();
    }

    private Entry load(Backdrop backdrop, String key) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation location = ResourceLocation.parse(backdrop.texture());
        boolean cube = backdrop.kind() == Backdrop.Kind.CUBEMAP;
        float[] grid = new float[GRID_W * GRID_H * 4];
        NativeImage image;
        try (InputStream in = mc.getResourceManager().getResourceOrThrow(location).open()) {
            image = NativeImage.read(in);
        }
        try {
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
        } catch (RuntimeException bad) {
            image.close();
            throw bad;
        }
        Entry entry = new Entry(key, cube, grid);
        // Like SimpleTexture: one level, no filter, no clamp (default repeat), straight alpha; the image is closed.
        entry.bytes = SkySpriteMath.textureBytes(image.getWidth(), image.getHeight(), false);
        entry.id = uploadPlain(image);
        return entry;
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
        Entry entry = new Entry(key, false, grid);
        entry.bytes = SkySpriteMath.textureBytes(GRADIENT_W, GRADIENT_H, false);
        entry.id = uploadPlain(image);
        return entry;
    }

    /** Uploads like {@code SimpleTexture.doLoad}: prepare one level, upload without blur, clamp or mipmaps, close. */
    private static int uploadPlain(NativeImage image) {
        int id = TextureUtil.generateTextureId();
        TextureUtil.prepareImage(id, 0, image.getWidth(), image.getHeight());
        image.upload(0, 0, 0, 0, 0, image.getWidth(), image.getHeight(), false, false, false, true);
        return id;
    }

    /** Loaded background entries (F7 and tests). */
    int size() { return backdrops.size(); }
    /** Background loads since the game started (tests: reuse means this does not grow). */
    int loads() { return backdropLoads; }

    // ------------------------------------------------------------------------------------------------ sprites

    /** State of a sprite texture. */
    enum Status { PENDING, READY, FAILED, REFUSED }

    /** One sprite texture (image, sheet or impostor page). */
    static final class Sprite {
        final String location;
        final boolean smooth;
        final int columns, rows;
        Status status = Status.PENDING;
        int id, width, height, sourceWidth, sourceHeight, levels;
        long bytes, lastUsed, readyFrame;
        long readyNanos;
        /** Mean relative luminance of a {@link #LUMA} × {@link #LUMA} grid (premultiplied over black), or null. */
        float[] luma;
        String error;
        CompletableFuture<Decoded> job;
        Decoded decoded;
        long generation;

        Sprite(String location, boolean smooth, int columns, int rows) {
            this.location = location; this.smooth = smooth; this.columns = columns; this.rows = rows;
        }

        /** True when the texture can be drawn. */
        boolean ready() { return status == Status.READY && id != 0; }

        /** Fade-in factor 0..1 since the texture became ready. */
        float fade() {
            if (!ready()) return 0;
            double s = (System.nanoTime() - readyNanos) / 1e9;
            return (float) Math.clamp(s / FADE_SECONDS, 0, 1);
        }

        /**
         * Mean luminance of each frame of a {@code columns} × {@code rows} grid (row by row, {@code frames} of them) from
         * the luminance grid: exact when the grid divides {@value #LUMA}, else the nearest cells. Null without data.
         */
        float[] frameLuminance(int columns, int rows, int frames) {
            if (luma == null) return null;
            float[] out = new float[frames];
            for (int f = 0; f < frames; f++) {
                int c = f % columns, r = f / columns;
                int x0 = c * LUMA / columns, x1 = Math.max(x0 + 1, (c + 1) * LUMA / columns);
                int y0 = r * LUMA / rows, y1 = Math.max(y0 + 1, (r + 1) * LUMA / rows);
                double sum = 0;
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) sum += luma[y * LUMA + x];
                out[f] = (float) (sum / ((x1 - x0) * (y1 - y0)));
            }
            return out;
        }
    }

    /**
     * Output of a decoding job (worker thread): the premultiplied levels (level 0 first), the source size, halvings and
     * the luminance grid.
     */
    record Decoded(List<NativeImage> levels, int sourceWidth, int sourceHeight, int halvings, float[] luma) {
        NativeImage image() { return levels.get(0); }
        void close() { for (NativeImage i : levels) i.close(); }
        long bytes() {
            long b = 0;
            for (NativeImage i : levels) b += (long) i.getWidth() * i.getHeight() * 4;
            return b;
        }
    }

    /** One impostor descriptor: loading, parsed or failed. */
    static final class Descriptor {
        final String id;
        Status status = Status.PENDING;
        SkySpriteMath.Descriptor value;
        String error;
        CompletableFuture<SkySpriteMath.Descriptor> job;
        long generation, lastUsed;
        Descriptor(String id) { this.id = id; }
        boolean ready() { return status == Status.READY && value != null; }
    }

    private final Map<String, Sprite> sprites = new LinkedHashMap<>();
    private final Map<String, Descriptor> descriptors = new HashMap<>();
    private final ArrayDeque<Sprite> waiting = new ArrayDeque<>();
    private final Set<String> reported = new HashSet<>();
    private long generation, frame, resident, uploadsTotal, uploadBytesTotal;
    private int uploadsFrame, maxSide;
    private long uploadBytesFrame;
    private int failures, refusals;
    private String lastProblem = "";

    /**
     * The texture of a sprite cut into a {@code columns} × {@code rows} grid (1 × 1 for a whole image), requesting it on
     * first use (the job starts right away on a worker); marks it used this frame. The grid decides the mipmap levels
     * of {@code smooth} textures, so it is part of the key. Never null; check {@link Sprite#ready()}.
     */
    Sprite sprite(String location, boolean smooth, int columns, int rows) {
        String key = key(location, smooth, columns, rows);
        Sprite s = sprites.get(key);
        if (s == null) {
            s = new Sprite(location, smooth, columns, rows);
            sprites.put(key, s);
            start(s);
        }
        s.lastUsed = frame;
        return s;
    }

    private void start(Sprite s) {
        if (maxSide == 0) maxSide = Math.min(MAX_SIDE, Math.max(1024, RenderSystem.maxSupportedTextureSize()));
        int limit = maxSide;
        s.generation = generation;
        s.status = Status.PENDING;
        s.job = CompletableFuture.supplyAsync(() -> decode(s.location, s.smooth, s.columns, s.rows, limit), Util.backgroundExecutor());
        waiting.add(s);
    }

    private static String key(String location, boolean smooth, int columns, int rows) {
        return smooth ? "s|" + columns + "x" + rows + "|" + location : "p|" + location;
    }

    /** Worker: reads, decodes, halves to fit, premultiplies, measures and builds the mipmap chain of a PNG. */
    private static Decoded decode(String location, boolean smooth, int columns, int rows, int limit) {
        ResourceLocation id = ResourceLocation.parse(location);
        NativeImage image;
        try (InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).open()) {
            image = NativeImage.read(in);
        } catch (java.io.FileNotFoundException missing) {
            throw new IllegalStateException("PNG not found in any resource pack: " + location);
        } catch (java.io.IOException bad) {
            throw new IllegalStateException("PNG could not be read (corrupt or not a PNG): " + location + " — " + bad.getMessage());
        }
        int sw = image.getWidth(), sh = image.getHeight();
        int halvings = SkySpriteMath.halvings(sw, sh, limit);
        try {
            for (int i = 0; i < halvings; i++) {
                NativeImage half = halve(image);
                image.close();
                image = half;
            }
            float[] luma = premultiply(image);
            List<NativeImage> levels = new ArrayList<>();
            levels.add(image);
            int n = smooth ? SkySpriteMath.gridLevels(image.getWidth(), image.getHeight(), columns, rows) : 0;
            try {
                for (int i = 0; i < n; i++) levels.add(box(levels.get(levels.size() - 1)));
            } catch (RuntimeException failure) {
                for (int i = 1; i < levels.size(); i++) levels.get(i).close();
                throw failure;
            }
            return new Decoded(levels, sw, sh, halvings, luma);
        } catch (RuntimeException failure) {
            image.close();
            throw failure;
        }
    }

    /**
     * Next mipmap level of a premultiplied image: 2 × 2 box (sizes halved, at least 1; an odd last row or column is
     * folded into its neighbour by clamping). With cells of even size it never mixes two cells.
     */
    private static NativeImage box(NativeImage src) {
        int sw = src.getWidth(), sh = src.getHeight(), w = Math.max(1, sw / 2), h = Math.max(1, sh / 2);
        NativeImage out = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int r = 0, g = 0, b = 0, a = 0;
            for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
                int p = src.getPixelRGBA(Math.min(x * 2 + dx, sw - 1), Math.min(y * 2 + dy, sh - 1));
                r += p & 0xFF; g += p >> 8 & 0xFF; b += p >> 16 & 0xFF; a += p >>> 24;
            }
            out.setPixelRGBA(x, y, (a + 2) / 4 << 24 | (b + 2) / 4 << 16 | (g + 2) / 4 << 8 | (r + 2) / 4);
        }
        return out;
    }

    /** Box-filtered half size (straight alpha weighted by alpha, so transparent pixels do not darken edges). */
    private static NativeImage halve(NativeImage src) {
        int w = Math.max(1, src.getWidth() / 2), h = Math.max(1, src.getHeight() / 2);
        NativeImage out = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double r = 0, g = 0, b = 0, a = 0;
            for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
                int p = src.getPixelRGBA(Math.min(x * 2 + dx, src.getWidth() - 1), Math.min(y * 2 + dy, src.getHeight() - 1));
                double pa = (p >>> 24) / 255.0;
                r += (p & 0xFF) * pa; g += (p >> 8 & 0xFF) * pa; b += (p >> 16 & 0xFF) * pa; a += pa;
            }
            int abgr = a <= 0 ? 0 : (int) Math.round(a / 4 * 255) << 24 | (int) Math.round(b / a) << 16
                | (int) Math.round(g / a) << 8 | (int) Math.round(r / a);
            out.setPixelRGBA(x, y, abgr);
        }
        return out;
    }

    /** Premultiplies the image in place and returns the luminance grid (premultiplied over black). */
    private static float[] premultiply(NativeImage image) {
        int w = image.getWidth(), h = image.getHeight();
        double[] sum = new double[LUMA * LUMA];
        int[] count = new int[LUMA * LUMA];
        for (int y = 0; y < h; y++) {
            int gy = Math.min(LUMA - 1, y * LUMA / h);
            for (int x = 0; x < w; x++) {
                int p = image.getPixelRGBA(x, y);
                int a = p >>> 24;
                int r = (p & 0xFF) * a / 255, g = (p >> 8 & 0xFF) * a / 255, b = (p >> 16 & 0xFF) * a / 255;
                if (a != 255) image.setPixelRGBA(x, y, a << 24 | b << 16 | g << 8 | r);
                int k = gy * LUMA + Math.min(LUMA - 1, x * LUMA / w);
                sum[k] += SkySpriteMath.luminance(r / 255f, g / 255f, b / 255f);
                count[k]++;
            }
        }
        float[] luma = new float[LUMA * LUMA];
        for (int k = 0; k < luma.length; k++) luma[k] = count[k] == 0 ? 0 : (float) (sum[k] / count[k]);
        return luma;
    }

    /**
     * Impostor descriptor, requested on first use (read and parsed on a worker); marks it used this frame. Never null;
     * check {@link Descriptor#ready()}.
     */
    Descriptor descriptor(String id, String namespace, String path) {
        Descriptor d = descriptors.get(id);
        if (d == null) {
            d = new Descriptor(id);
            descriptors.put(id, d);
            d.generation = generation;
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(namespace, path);
            d.job = CompletableFuture.supplyAsync(() -> {
                String json;
                try (InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(location).open()) {
                    json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (java.io.FileNotFoundException missing) {
                    throw new IllegalStateException("descriptor not found in any resource pack: " + location);
                } catch (java.io.IOException bad) {
                    throw new IllegalStateException("descriptor could not be read: " + location + " — " + bad.getMessage());
                }
                return SkySpriteMath.parse(json);
            }, Util.backgroundExecutor());
        }
        d.lastUsed = frame;
        if (d.status == Status.PENDING && d.job.isDone()) {
            try {
                d.value = d.job.join();
                d.status = Status.READY;
            } catch (Exception failure) {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                d.status = Status.FAILED;
                d.error = cause.getMessage();
                failures++;
                problem("impostor:" + id, "Impostor " + id + " is invalid and is skipped: " + d.error);
            }
            d.job = null;
        }
        return d;
    }

    /** Marks an impostor invalid (a page does not match its descriptor); logged once, counted for F7. */
    void invalid(Descriptor d, String message) {
        if (d.status == Status.FAILED) return;
        d.status = Status.FAILED;
        d.error = message;
        failures++;
        problem("impostor:" + d.id, "Impostor " + d.id + " is invalid and is skipped: " + message);
    }

    /** Marks a sprite texture unusable for its object (wrong grid); logged once, counted for F7. */
    void invalidSprite(Sprite s, String message) {
        String key = "grid:" + s.location;
        if (!reported.contains(key)) failures++;
        problem(key, "Sky texture " + s.location + " is skipped: " + message);
    }

    /** Logs an informative note once (not counted as a failure). */
    void note(String key, String message) {
        if (reported.add(key)) LOGGER.info(message);
    }

    /**
     * Once per frame (render thread): finishes decoded jobs in request order, frees room within the profile's memory
     * budget and uploads within its upload limits.
     */
    void pump(SkySpriteMath.Caps caps) {
        frame++;
        uploadsFrame = 0;
        uploadBytesFrame = 0;
        int n = waiting.size();
        for (int i = 0; i < n; i++) {
            Sprite s = waiting.poll();
            if (s == null) break;
            if (!finish(s, caps)) waiting.add(s);
        }
    }

    /** Returns true when the sprite left the queue (uploaded, failed or refused). */
    private boolean finish(Sprite s, SkySpriteMath.Caps caps) {
        if (s.decoded == null) {
            if (s.job == null || !s.job.isDone()) return false;
            try {
                s.decoded = s.job.join();
            } catch (Exception failure) {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                s.status = Status.FAILED;
                s.error = cause.getMessage();
                s.job = null;
                failures++;
                problem("sprite:" + s.location, "Sky texture " + s.location + " is skipped: " + s.error);
                return true;
            }
            s.job = null;
            if (s.generation != generation) {
                s.decoded.close();
                s.decoded = null;
                return true;
            }
            if (s.decoded.halvings() > 0)
                problem("halved:" + s.location, "Sky texture " + s.location + " (" + s.decoded.sourceWidth() + " × " + s.decoded.sourceHeight()
                    + ") is larger than " + maxSide + " pixels: halved " + s.decoded.halvings() + " time(s)");
        }
        long bytes = s.decoded.bytes();
        if (resident + bytes > caps.vramBytes()) evict(caps.vramBytes() - bytes);
        if (resident + bytes > caps.vramBytes()) {
            s.status = Status.REFUSED;
            s.error = "over the texture memory budget of the quality profile (" + (caps.vramBytes() >> 20) + " MiB)";
            s.decoded.close();
            s.decoded = null;
            refusals++;
            problem("budget:" + s.location, "Sky texture " + s.location + " (" + (bytes >> 10) + " KiB) is refused: " + s.error);
            return true;
        }
        if (!SkySpriteMath.uploadFits(caps, uploadsFrame, uploadBytesFrame, bytes)) return false;
        upload(s, s.decoded);
        s.decoded = null;
        resident += bytes;
        uploadsFrame++;
        uploadBytesFrame += bytes;
        uploadsTotal++;
        uploadBytesTotal += bytes;
        return true;
    }

    private void upload(Sprite s, Decoded d) {
        NativeImage image = d.image();
        int w = image.getWidth(), h = image.getHeight();
        int levels = d.levels().size() - 1;
        long bytes = d.bytes();
        int id = TextureUtil.generateTextureId();
        // Binds the texture and allocates the levels (prepareImage sets the max level); each level is uploaded with blur
        // and mipmap filtering for SMOOTH, clamped to the edge, and closed.
        TextureUtil.prepareImage(id, levels, w, h);
        for (int i = 0; i <= levels; i++) {
            NativeImage level = d.levels().get(i);
            level.upload(i, 0, 0, 0, 0, level.getWidth(), level.getHeight(), s.smooth, true, s.smooth && levels > 0, true);
        }
        s.id = id;
        s.width = w;
        s.height = h;
        s.levels = levels;
        s.sourceWidth = d.sourceWidth();
        s.sourceHeight = d.sourceHeight();
        s.bytes = bytes;
        s.luma = d.luma();
        s.status = Status.READY;
        s.readyFrame = frame;
        s.readyNanos = System.nanoTime();
    }

    /** Frees ready sprites unused for {@value #EVICT_FRAMES} frames, oldest first, until {@code target} bytes remain. */
    private void evict(long target) {
        List<Sprite> old = new ArrayList<>();
        for (Sprite s : sprites.values()) if (s.ready() && frame - s.lastUsed >= EVICT_FRAMES) old.add(s);
        old.sort((a, b) -> Long.compare(a.lastUsed, b.lastUsed));
        for (Sprite s : old) {
            if (resident <= target) break;
            release(s);
            sprites.values().remove(s);
        }
    }

    private void release(Sprite s) {
        if (s.id != 0) {
            TextureUtil.releaseTextureId(s.id);
            resident -= s.bytes;
        }
        s.id = 0;
        if (s.decoded != null) s.decoded.close();
        s.decoded = null;
        s.status = Status.PENDING;
    }

    /** Frees sprites and descriptors unused for {@value #KEEP_FRAMES} frames; refused ones may try again. */
    void sweep() {
        if (frame % 300 != 0) return;
        boolean freed = sprites.values().removeIf(s -> {
            if (s.status == Status.PENDING || frame - s.lastUsed < KEEP_FRAMES) return false;
            release(s);
            return true;
        });
        descriptors.values().removeIf(d -> d.status != Status.PENDING && frame - d.lastUsed >= KEEP_FRAMES);
        if (freed) sprites.values().removeIf(s -> s.status == Status.REFUSED);
    }

    private void problem(String key, String message) {
        lastProblem = message;
        if (reported.add(key)) LOGGER.warn(message);
    }

    /**
     * Releases everything: background and sprite textures are deleted, CPU grids dropped, descriptors forgotten, pending
     * jobs of this generation dropped when they finish; problems may be logged again after a reload.
     */
    void clear() {
        for (Entry e : backdrops.values()) if (e.id != 0) TextureUtil.releaseTextureId(e.id);
        backdrops.clear();
        backdropFailed.clear();
        for (Sprite s : sprites.values()) release(s);
        sprites.clear();
        for (Sprite s : waiting) if (s.decoded != null) s.decoded.close();
        waiting.clear();
        descriptors.clear();
        reported.clear();
        resident = 0;
        generation++;
        failures = refusals = 0;
        lastProblem = "";
    }

    /** Sprite textures resident on the GPU. */
    int spriteTextures() {
        int n = 0;
        for (Sprite s : sprites.values()) if (s.ready()) n++;
        return n;
    }
    /** Sprite textures and descriptors still loading. */
    int pending() {
        int n = 0;
        for (Sprite s : sprites.values()) if (s.status == Status.PENDING) n++;
        for (Descriptor d : descriptors.values()) if (d.status == Status.PENDING) n++;
        return n;
    }
    /** Estimated texture memory of the cache, bytes (backgrounds included). */
    long resident() { return resident; }
    /** Uploads this frame and since start, and bytes since start (F7 and tests). */
    long[] uploads() { return new long[] {uploadsFrame, uploadsTotal, uploadBytesTotal, uploadBytesFrame}; }
    /** Resources that failed (missing, corrupt, invalid) since the last reload. */
    int failures() { return failures; }
    /** Textures refused by the memory budget since the last reload. */
    int refusals() { return refusals; }
    /** Last problem message (F7), empty when none. */
    String lastProblem() { return lastProblem; }
    /** Status of a sprite texture by location without requesting it (tests), or null. */
    Status status(String location, boolean smooth, int columns, int rows) {
        Sprite s = sprites.get(key(location, smooth, columns, rows));
        return s == null ? null : s.status;
    }
}
