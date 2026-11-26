package dev.calamidade_awakening.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.calamidade_awakening.api.CookieAtlas;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The light cookie atlases of this client (0.15.0). The <b>standard atlas</b> (RGBA8, {@value CookieAtlas#SIDE} texels,
 * {@value CookieAtlas#LEVELS} mipmap levels) holds every cookie at up to 512 texels; it is created on the first cookie
 * light, never before. The <b>large atlas</b> (fase 2, same side, full mipmap chain) holds the optional 1024 or 2048
 * versions of cookies that ask for {@code Cookie.resolution(HIGH|ULTRA)}; it exists only while such a cookie is used
 * and the profile allows it ({@link CookieAtlas#largeSide}: LOW none, BALANCED 1024, HIGH and ULTRA 2048). The 512
 * version always exists too: it is what the light uses while the large one loads, when the budget is lower or the large
 * atlas is full. Both atlases go after {@value #KEEP_FRAMES} frames without use, on resource reload and when the player
 * leaves the world.
 *
 * <p>Each PNG is read, resized (halved while too big, then each side rounded up to a power of two, so mipmaps and tiling
 * never see padding), premultiplied and reduced to its mipmap chain on a worker thread; the render thread only uploads,
 * within {@value #UPLOAD_BYTES} bytes per frame (a 2048 image goes up in bands over several frames, and is used only
 * once complete). An image is uploaded once and shared by every light that uses it. When an atlas is full, images unused
 * for {@value #EVICT_FRAMES} frames are evicted; if none can go, the new image waits (its lights use their fallback).
 * Missing, unreadable or oversized images are logged once and counted for F7. Render thread only, except the decoding
 * jobs.
 */
final class LightCookies {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    /** Largest PNG side accepted, pixels. */
    static final int MAX_SOURCE = 4096;
    static final int KEEP_FRAMES = 1200, EVICT_FRAMES = 60;
    /** Bytes uploaded per frame at most (one standard image always fits; large images go in bands). */
    static final int UPLOAD_BYTES = 4 << 20;
    /** Mipmap levels of the large atlas (2048 down to 1). */
    static final int LARGE_LEVELS = 12;

    enum Status { PENDING, WAITING, READY, FAILED }

    /** Decoded image: mip levels of the whole cell (ABGR ints, premultiplied), stored image size and average. */
    record Decoded(int[][] levels, int cellSize, int width, int height, float r, float g, float b) {}

    /** One stored version of an image (standard or large): cell, size and upload progress. */
    static final class Version {
        Status status = Status.PENDING;
        CompletableFuture<Decoded> job;
        Decoded decoded;
        CookieAtlas.Cell cell;
        int width, height, side;
        // Upload progress: level and next row of that level.
        int level, row;

        boolean ready() { return status == Status.READY; }
    }

    /** One cookie image. */
    static final class Entry {
        final String texture;
        final Version std = new Version();
        Version large;
        int wanted = 512;
        long lastUsed, lastLarge;
        /** Average premultiplied color of the image (rgb) and its luminance; 0.5 until it loaded. */
        float avgR = .5f, avgG = .5f, avgB = .5f, avgLuma = .5f;

        Entry(String texture) { this.texture = texture; }

        boolean ready() { return std.ready(); }
        /** The version to sample now: the large one when complete, else the standard one. */
        Version best() { return large != null && large.ready() ? large : std; }
        boolean isLarge() { return large != null && large.ready(); }
        Status status() { return std.status; }
    }

    private final Map<String, Entry> entries = new HashMap<>();
    private final Set<String> reported = new HashSet<>();
    private final CookieAtlas atlas = new CookieAtlas();
    private final CookieAtlas largeAtlas = new CookieAtlas(2048, 1024);
    private int texture, largeTexture, allowedLarge = 512;
    private long frame, lastCookieFrame, lastLargeFrame;
    private int uploadedThisFrame, uploadsTotal, failures;
    private long bytesTotal;
    private IntBuffer staging;

    /**
     * Entry of an image (starts loading it on first use), marked as used this frame; {@code side} is the resolution the
     * light asks for (512, 1024 or 2048).
     */
    Entry get(String location, int side) {
        Entry e = entries.get(location);
        if (e == null) {
            e = new Entry(location);
            entries.put(location, e);
            start(e.std, location, 512);
        }
        e.lastUsed = frame;
        lastCookieFrame = frame;
        if (side > 512) {
            e.wanted = Math.max(e.wanted, side);
            e.lastLarge = frame;
            lastLargeFrame = frame;
            int target = Math.min(e.wanted, allowedLarge);
            if (target > 512 && (e.large == null || (e.large.side != target && e.large.status != Status.FAILED))) {
                freeLarge(e);
                e.large = new Version();
                start(e.large, location, target);
            }
        }
        return e;
    }

    private void start(Version v, String location, int side) {
        v.side = side;
        v.job = CompletableFuture.supplyAsync(() -> decode(location, side), Util.backgroundExecutor());
    }

    /**
     * Per frame, before the lights are queued: the large budget of the profile, finished decoding jobs, uploads within
     * the byte budget, and release of idle memory.
     */
    void pump(int largeSide) {
        frame++;
        uploadedThisFrame = 0;
        if (largeSide != allowedLarge) {
            allowedLarge = largeSide;
            // Lower budget: large versions above it go (their lights use the 512 version or ask again below).
            for (Entry e : entries.values()) if (e.large != null && e.large.side > allowedLarge) freeLarge(e);
            if (allowedLarge <= 512) deleteLarge();
        }
        if (entries.isEmpty()) {
            if (texture != 0 && frame - lastCookieFrame > KEEP_FRAMES) deleteAtlas();
            return;
        }
        List<Entry> idle = null;
        for (Entry e : entries.values()) {
            finishJob(e, e.std);
            if (e.large != null) finishJob(e, e.large);
            if (e.std.status == Status.WAITING) place(e, e.std, false);
            if (e.large != null && e.large.status == Status.WAITING && e.std.ready()) place(e, e.large, true);
            if (frame - e.lastUsed > KEEP_FRAMES) (idle == null ? idle = new ArrayList<>() : idle).add(e);
            else if (e.large != null && frame - e.lastLarge > KEEP_FRAMES) freeLarge(e);
        }
        if (idle != null) for (Entry e : idle) drop(e);
        if (largeTexture != 0 && frame - lastLargeFrame > KEEP_FRAMES) deleteLarge();
        if (entries.isEmpty() && texture != 0 && frame - lastCookieFrame > KEEP_FRAMES) deleteAtlas();
    }

    private void finishJob(Entry e, Version v) {
        if (v.status != Status.PENDING || v.job == null || !v.job.isDone()) return;
        try {
            v.decoded = v.job.join();
            v.status = Status.WAITING;
            if (v == e.std) {
                e.avgR = v.decoded.r(); e.avgG = v.decoded.g(); e.avgB = v.decoded.b();
                e.avgLuma = .2126f * e.avgR + .7152f * e.avgG + .0722f * e.avgB;
            }
        } catch (Exception failure) {
            v.status = Status.FAILED;
            failures++;
            String message = failure.getCause() != null ? failure.getCause().getMessage() : failure.getMessage();
            note(e.texture + v.side, "light cookie " + e.texture + " (" + v.side + ") skipped: " + message);
        }
        v.job = null;
    }

    /** Allocates (evicting idle images if needed) and uploads a decoded version, within the frame's byte budget. */
    private void place(Entry e, Version v, boolean large) {
        Decoded d = v.decoded;
        CookieAtlas target = large ? largeAtlas : atlas;
        if (v.cell == null) {
            CookieAtlas.Cell cell = target.allocate(d.width(), d.height());
            if (cell == null) {
                Entry victim = null;
                for (Entry o : entries.values()) {
                    Version ov = large ? o.large : o.std;
                    long used = large ? o.lastLarge : o.lastUsed;
                    if (o != e && ov != null && ov.cell != null && frame - used > EVICT_FRAMES && (victim == null
                        || used < (large ? victim.lastLarge : victim.lastUsed))) victim = o;
                }
                if (victim != null) {
                    if (large) freeLarge(victim); else drop(victim);
                    cell = target.allocate(d.width(), d.height());
                }
                if (cell == null) {
                    note(e.texture + (large ? "L" : "S"), "light cookie atlas" + (large ? " (large)" : "") + " full: "
                        + e.texture + " waits (its lights use " + (large ? "the 512 version" : "their fallback") + ")");
                    return;
                }
            }
            v.cell = cell;
            v.level = 0;
            v.row = 0;
            if (large) { if (largeTexture == 0) largeTexture = create(LARGE_LEVELS); }
            else if (texture == 0) texture = create(CookieAtlas.LEVELS);
        }
        if (upload(large ? largeTexture : texture, v, d)) {
            v.width = d.width();
            v.height = d.height();
            v.decoded = null;
            v.status = Status.READY;
            uploadsTotal++;
        }
    }

    private void freeLarge(Entry e) {
        if (e.large == null) return;
        if (e.large.job != null) e.large.job.cancel(false);
        if (e.large.cell != null) largeAtlas.free(e.large.cell);
        e.large = null;
    }

    private void drop(Entry e) {
        if (e.std.cell != null) atlas.free(e.std.cell);
        if (e.std.job != null) e.std.job.cancel(false);
        freeLarge(e);
        entries.remove(e.texture);
    }

    private void note(String key, String message) {
        if (reported.add(key + "|" + message)) LOGGER.warn(message);
    }

    /** GL texture of the standard atlas, 0 when it does not exist. */
    int texture() { return texture; }

    /** GL texture of the large atlas, 0 when it does not exist. */
    int largeTexture() { return largeTexture; }

    /** Releases everything (resource reload, leaving the world); images load again on use. */
    void clear() {
        for (Entry e : entries.values()) {
            if (e.std.job != null) e.std.job.cancel(false);
            if (e.large != null && e.large.job != null) e.large.job.cancel(false);
        }
        entries.clear();
        reported.clear();
        deleteLarge();
        deleteAtlas();
    }

    /**
     * F7: images ready, waiting or loading, failed; uploads since start; quarters of the standard atlas in use; large
     * versions ready; large versions loading or waiting.
     */
    int[] counts() {
        int ready = 0, waiting = 0, failed = 0, large = 0, largeWaiting = 0;
        for (Entry e : entries.values()) {
            if (e.ready()) ready++;
            else if (e.std.status == Status.FAILED) failed++;
            else waiting++;
            if (e.large != null) { if (e.large.ready()) large++; else if (e.large.status != Status.FAILED) largeWaiting++; }
        }
        return new int[] {ready, waiting, failed, uploadsTotal, atlas.usedQuarters(), large, largeWaiting};
    }

    /** Bytes of both atlases (0 when none exists). */
    long bytes() { return (texture == 0 ? 0 : CookieAtlas.BYTES) + (largeTexture == 0 ? 0 : largeBytes()); }

    /** Bytes uploaded since start. */
    long uploadedBytes() { return bytesTotal; }

    static long largeBytes() {
        long total = 0;
        for (int k = 0; k < LARGE_LEVELS; k++) total += (long) (CookieAtlas.SIDE >> k) * (CookieAtlas.SIDE >> k) * 4;
        return total;
    }

    // ---------------------------------------------------------------------------------------------- GL

    private static int create(int levels) {
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int id;
        try {
            if (unpackBuffer != 0) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            id = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, levels - 1);
            for (int k = 0; k < levels; k++) {
                int side = CookieAtlas.SIDE >> k;
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, k, GL11.GL_RGBA8, side, side, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                    (IntBuffer) null);
            }
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            if (unpackBuffer != 0) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        }
        return id;
    }

    private void deleteAtlas() {
        if (texture != 0) {
            GL11.glDeleteTextures(texture);
            texture = 0;
        }
        atlas.clear();
        for (Entry e : entries.values()) freeLarge(e);
        for (Entry e : entries.values()) if (e.std.cell != null) {
            // The data is gone with the texture: decode again on use.
            e.std.cell = null;
            e.std.status = Status.FAILED == e.std.status ? Status.FAILED : Status.PENDING;
        }
        entries.values().removeIf(e -> e.std.status == Status.PENDING && e.std.job == null);
        if (largeTexture == 0) staging = null;
    }

    private void deleteLarge() {
        for (Entry e : entries.values()) freeLarge(e);
        if (largeTexture != 0) {
            GL11.glDeleteTextures(largeTexture);
            largeTexture = 0;
        }
        largeAtlas.clear();
    }

    /**
     * Uploads what fits of a version in this frame's byte budget, from where it stopped; true when it is complete. A
     * standard image always goes in one frame (at most ~1.4 MB with its mipmaps).
     */
    private boolean upload(int id, Version v, Decoded d) {
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        try {
            if (unpackBuffer != 0) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            if (staging == null) staging = BufferUtils.createIntBuffer(UPLOAD_BYTES / 4);
            boolean small = d.cellSize() <= CookieAtlas.BIG;
            while (v.level < d.levels().length) {
                int side = d.cellSize() >> v.level;
                int budgetRows = small ? side : Math.max(1, (UPLOAD_BYTES - uploadedThisFrame) / (side * 4));
                if (!small && uploadedThisFrame >= UPLOAD_BYTES) return false;
                int rows = Math.min(side - v.row, Math.min(budgetRows, staging.capacity() / side));
                staging.clear();
                staging.put(d.levels()[v.level], v.row * side, rows * side);
                staging.flip();
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, v.level, v.cell.x() >> v.level, (v.cell.y() >> v.level) + v.row,
                    side, rows, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, staging);
                uploadedThisFrame += rows * side * 4;
                bytesTotal += rows * side * 4L;
                v.row += rows;
                if (v.row >= side) { v.level++; v.row = 0; }
            }
            return true;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
            if (unpackBuffer != 0) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        }
    }

    // ---------------------------------------------------------------------------------------------- worker

    /** Worker: reads the PNG, fits it to {@code side} (power-of-two sides), premultiplies and builds the mip chain. */
    static Decoded decode(String location, int side) {
        ResourceLocation id = ResourceLocation.tryParse(location);
        if (id == null) throw new IllegalStateException("bad location");
        NativeImage image;
        try (InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).open()) {
            image = NativeImage.read(in);
        } catch (java.io.FileNotFoundException missing) {
            throw new IllegalStateException("PNG not found in any resource pack");
        } catch (java.io.IOException bad) {
            throw new IllegalStateException("PNG could not be read (corrupt or not a PNG): " + bad.getMessage());
        }
        try {
            int w = image.getWidth(), h = image.getHeight();
            if (w > MAX_SOURCE || h > MAX_SOURCE) throw new IllegalStateException("PNG larger than " + MAX_SOURCE + " pixels");
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = premultiply(image.getPixelRGBA(x, y));
            return chain(px, w, h, side);
        } finally {
            image.close();
        }
    }

    /** Pure part of the decode (also used by the tests): standard version (largest side 512). */
    static Decoded chain(int[] px, int w, int h) { return chain(px, w, h, 512); }

    /**
     * Pure part of the decode: halves while above {@code side}, resamples each side up to its power of two (bilinear,
     * premultiplied), places the image at the top-left of its cell and builds the cell's mip chain (2 × 2 box) and the
     * average color.
     */
    static Decoded chain(int[] px, int w, int h, int side) {
        while ((w > side || h > side) && w >= 2 && h >= 2) {
            int nw = Math.max(1, w / 2), nh = Math.max(1, h / 2);
            px = box(px, w, h, nw, nh);
            w = nw; h = nh;
        }
        int[] fit = CookieAtlas.fit(w, h, side);
        if (fit[0] != w || fit[1] != h) {
            px = bilinear(px, w, h, fit[0], fit[1]);
            w = fit[0]; h = fit[1];
        }
        int cell = side <= 512 ? CookieAtlas.cellFor(w, h) : (w <= 1024 && h <= 1024 ? 1024 : 2048);
        int levels = Math.min(side <= 512 ? CookieAtlas.LEVELS : LARGE_LEVELS, Integer.numberOfTrailingZeros(cell) + 1);
        int[][] out = new int[levels][];
        int[] level0 = new int[cell * cell];
        double r = 0, g = 0, b = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int c = px[y * w + x];
            level0[y * cell + x] = c;
            r += c & 255; g += (c >> 8) & 255; b += (c >> 16) & 255;
        }
        out[0] = level0;
        for (int k = 1; k < levels; k++) {
            int s = cell >> (k - 1);
            out[k] = box(out[k - 1], s, s, s / 2, s / 2);
        }
        double area = 255.0 * w * h;
        return new Decoded(out, cell, w, h, (float) (r / area), (float) (g / area), (float) (b / area));
    }

    /** ABGR straight alpha to ABGR premultiplied. */
    static int premultiply(int abgr) {
        int a = (abgr >>> 24) & 255;
        if (a == 255) return abgr;
        int r = (abgr & 255) * a / 255, g = ((abgr >> 8) & 255) * a / 255, b = ((abgr >> 16) & 255) * a / 255;
        return a << 24 | b << 16 | g << 8 | r;
    }

    /** 2 × 2 box from {@code w × h} to {@code nw × nh} (odd edges clamp), per channel. */
    static int[] box(int[] src, int w, int h, int nw, int nh) {
        int[] out = new int[nw * nh];
        for (int y = 0; y < nh; y++) for (int x = 0; x < nw; x++) {
            int sr = 0, sg = 0, sb = 0, sa = 0;
            for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
                int c = src[Math.min(h - 1, y * 2 + dy) * w + Math.min(w - 1, x * 2 + dx)];
                sr += c & 255; sg += (c >> 8) & 255; sb += (c >> 16) & 255; sa += (c >>> 24) & 255;
            }
            out[y * nw + x] = ((sa + 2) / 4) << 24 | ((sb + 2) / 4) << 16 | ((sg + 2) / 4) << 8 | ((sr + 2) / 4);
        }
        return out;
    }

    /** Bilinear resample (texel centers, edges clamped) of a premultiplied image to {@code nw × nh}. */
    static int[] bilinear(int[] src, int w, int h, int nw, int nh) {
        int[] out = new int[nw * nh];
        for (int y = 0; y < nh; y++) {
            double sy = Math.clamp((y + .5) * h / nh - .5, 0, h - 1);
            int y0 = (int) sy, y1 = Math.min(h - 1, y0 + 1);
            double fy = sy - y0;
            for (int x = 0; x < nw; x++) {
                double sx = Math.clamp((x + .5) * w / nw - .5, 0, w - 1);
                int x0 = (int) sx, x1 = Math.min(w - 1, x0 + 1);
                double fx = sx - x0;
                int c00 = src[y0 * w + x0], c10 = src[y0 * w + x1], c01 = src[y1 * w + x0], c11 = src[y1 * w + x1];
                int v = 0;
                for (int sh = 0; sh < 32; sh += 8) {
                    double a = ((c00 >>> sh) & 255) * (1 - fx) + ((c10 >>> sh) & 255) * fx;
                    double b = ((c01 >>> sh) & 255) * (1 - fx) + ((c11 >>> sh) & 255) * fx;
                    v |= (int) Math.round(a * (1 - fy) + b * fy) << sh;
                }
                out[y * nw + x] = v;
            }
        }
        return out;
    }
}
