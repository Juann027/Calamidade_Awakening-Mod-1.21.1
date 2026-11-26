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
 * The light cookie atlas of this client (0.15.0): one RGBA8 texture of {@value CookieAtlas#SIDE} texels with
 * {@value CookieAtlas#LEVELS} mipmap levels that holds every cookie image, so the single lights pass reads all cookies
 * through one sampler. Created on the first cookie light, never before (no memory and no GL work while no cookie is
 * used); deleted again after {@value #KEEP_FRAMES} frames without cookie lights, on resource reload and when the player
 * leaves the world.
 *
 * <p>Each PNG is read, halved to fit {@value CookieAtlas#BIG} texels, premultiplied and reduced to its mipmap chain on
 * a worker thread (2 × 2 box per level inside its own cell, so levels never mix cells); the render thread only uploads,
 * at most {@value #UPLOADS_PER_FRAME} image per frame. An image is uploaded once and shared by every light that uses it.
 * When the atlas is full, images unused for {@value #EVICT_FRAMES} frames are evicted; if none can go, the new image
 * waits (its lights use their fallback). Missing, unreadable or oversized images are logged once and counted for F7.
 * Render thread only, except the decoding jobs.
 */
final class LightCookies {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    /** Largest PNG side accepted before halving, pixels. */
    static final int MAX_SOURCE = 4096;
    static final int KEEP_FRAMES = 1200, EVICT_FRAMES = 60, UPLOADS_PER_FRAME = 1;

    enum Status { PENDING, WAITING, READY, FAILED }

    /** One cookie image: where it lives in the atlas and its average color (fallback). */
    static final class Entry {
        final String texture;
        Status status = Status.PENDING;
        CompletableFuture<Decoded> job;
        Decoded decoded;
        CookieAtlas.Cell cell;
        int width, height, generation;
        long lastUsed;
        /** Average premultiplied color of the image (rgb) and its luminance; 0.5 until it loaded. */
        float avgR = .5f, avgG = .5f, avgB = .5f, avgLuma = .5f;

        Entry(String texture) { this.texture = texture; }

        boolean ready() { return status == Status.READY; }
    }

    /** Decoded image: mip levels of the whole cell (ABGR ints, premultiplied), image size and average. */
    record Decoded(int[][] levels, int cellSize, int width, int height, float r, float g, float b) {}

    private final Map<String, Entry> entries = new HashMap<>();
    private final Set<String> reported = new HashSet<>();
    private final CookieAtlas atlas = new CookieAtlas();
    private int texture;
    private long frame, lastCookieFrame;
    private int generation, uploadsThisFrame, uploadsTotal, failures;
    private IntBuffer staging;

    /** Entry of an image (starts loading it on first use), marked as used this frame. */
    Entry get(String location) {
        Entry e = entries.get(location);
        if (e == null) {
            e = new Entry(location);
            e.generation = generation;
            entries.put(location, e);
            String loc = location;
            e.job = CompletableFuture.supplyAsync(() -> decode(loc), Util.backgroundExecutor());
        }
        e.lastUsed = frame;
        lastCookieFrame = frame;
        return e;
    }

    /** Per frame, before the lights are queued: finishes decoding jobs, uploads (capped) and releases idle memory. */
    void pump() {
        frame++;
        uploadsThisFrame = 0;
        if (entries.isEmpty()) {
            if (texture != 0 && frame - lastCookieFrame > KEEP_FRAMES) deleteAtlas();
            return;
        }
        List<Entry> idle = null;
        for (Entry e : entries.values()) {
            if (e.status == Status.PENDING && e.job != null && e.job.isDone()) {
                try {
                    e.decoded = e.job.join();
                    e.status = Status.WAITING;
                } catch (Exception failure) {
                    fail(e, failure.getCause() != null ? failure.getCause().getMessage() : failure.getMessage());
                }
                e.job = null;
            }
            if (e.status == Status.WAITING && uploadsThisFrame < UPLOADS_PER_FRAME) place(e);
            if (frame - e.lastUsed > KEEP_FRAMES) (idle == null ? idle = new ArrayList<>() : idle).add(e);
        }
        if (idle != null) for (Entry e : idle) drop(e);
        if (entries.isEmpty() && texture != 0 && frame - lastCookieFrame > KEEP_FRAMES) deleteAtlas();
    }

    private void place(Entry e) {
        Decoded d = e.decoded;
        CookieAtlas.Cell cell = atlas.allocate(d.width(), d.height());
        if (cell == null) {
            // Full: evict the least recently used image idle for EVICT_FRAMES, then try once more.
            Entry victim = null;
            for (Entry o : entries.values())
                if (o.ready() && frame - o.lastUsed > EVICT_FRAMES && (victim == null || o.lastUsed < victim.lastUsed)) victim = o;
            if (victim != null) {
                atlas.free(victim.cell);
                victim.cell = null;
                victim.status = Status.PENDING;   // decoded again if it comes back
                victim.job = null;
                entries.remove(victim.texture);
                cell = atlas.allocate(d.width(), d.height());
            }
            if (cell == null) {
                note(e.texture, "light cookie atlas full: " + e.texture + " waits (its lights use their fallback)");
                return;
            }
        }
        if (texture == 0) createAtlas();
        upload(cell, d);
        e.cell = cell;
        e.width = d.width();
        e.height = d.height();
        e.avgR = d.r(); e.avgG = d.g(); e.avgB = d.b();
        e.avgLuma = .2126f * d.r() + .7152f * d.g() + .0722f * d.b();
        e.decoded = null;
        e.status = Status.READY;
        uploadsThisFrame++;
        uploadsTotal++;
    }

    private void drop(Entry e) {
        if (e.cell != null) atlas.free(e.cell);
        if (e.job != null) e.job.cancel(false);
        entries.remove(e.texture);
    }

    private void fail(Entry e, String message) {
        e.status = Status.FAILED;
        failures++;
        note(e.texture, "light cookie " + e.texture + " skipped: " + message);
    }

    private void note(String key, String message) {
        if (reported.add(key + "|" + message)) LOGGER.warn(message);
    }

    /** GL texture of the atlas, 0 when it does not exist. */
    int texture() { return texture; }

    /** Releases everything (resource reload, leaving the world); images load again on use. */
    void clear() {
        for (Entry e : entries.values()) if (e.job != null) e.job.cancel(false);
        entries.clear();
        reported.clear();
        atlas.clear();
        generation++;
        deleteAtlas();
    }

    /** F7: images ready, waiting or loading, failed; atlas bytes (0 when not allocated); uploads since start. */
    int[] counts() {
        int ready = 0, waiting = 0, failed = 0;
        for (Entry e : entries.values()) {
            if (e.ready()) ready++;
            else if (e.status == Status.FAILED) failed++;
            else waiting++;
        }
        return new int[] {ready, waiting, failed, uploadsTotal, atlas.usedQuarters()};
    }

    long bytes() { return texture == 0 ? 0 : CookieAtlas.BYTES; }

    // ---------------------------------------------------------------------------------------------- GL

    private void createAtlas() {
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try {
            if (unpackBuffer != 0) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, CookieAtlas.LEVELS - 1);
            for (int k = 0; k < CookieAtlas.LEVELS; k++) {
                int side = CookieAtlas.SIDE >> k;
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, k, GL11.GL_RGBA8, side, side, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                    (IntBuffer) null);
            }
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            if (unpackBuffer != 0) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        }
    }

    private void deleteAtlas() {
        if (texture != 0) {
            GL11.glDeleteTextures(texture);
            texture = 0;
        }
        atlas.clear();
        for (Entry e : entries.values()) if (e.ready()) { e.status = Status.PENDING; e.cell = null; }
        // Entries without data load again on use.
        entries.values().removeIf(e -> e.status == Status.PENDING && e.job == null);
        staging = null;
    }

    private void upload(CookieAtlas.Cell cell, Decoded d) {
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
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            if (staging == null) staging = BufferUtils.createIntBuffer(CookieAtlas.BIG * CookieAtlas.BIG);
            for (int k = 0; k < d.levels().length; k++) {
                int side = d.cellSize() >> k;
                staging.clear();
                staging.put(d.levels()[k], 0, side * side);
                staging.flip();
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, k, cell.x() >> k, cell.y() >> k, side, side, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, staging);
            }
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

    /** Worker: reads the PNG, halves it to fit a big cell, premultiplies and builds the mip chain of its cell. */
    static Decoded decode(String location) {
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
            return chain(px, w, h);
        } finally {
            image.close();
        }
    }

    /** Pure part of the decode (also used by the tests): halvings, cell, mip chain and average color. */
    static Decoded chain(int[] px, int w, int h) {
        int n = CookieAtlas.halvings(w, h);
        for (int i = 0; i < n; i++) {
            int nw = Math.max(1, w / 2), nh = Math.max(1, h / 2);
            px = box(px, w, h, nw, nh);
            w = nw; h = nh;
        }
        int cell = CookieAtlas.cellFor(w, h);
        int levels = Math.min(CookieAtlas.LEVELS, Integer.numberOfTrailingZeros(cell) + 1);
        int[][] chain = new int[levels][];
        int[] level0 = new int[cell * cell];
        double r = 0, g = 0, b = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int c = px[y * w + x];
            level0[y * cell + x] = c;
            r += c & 255; g += (c >> 8) & 255; b += (c >> 16) & 255;
        }
        chain[0] = level0;
        for (int k = 1; k < levels; k++) {
            int side = cell >> (k - 1);
            chain[k] = box(chain[k - 1], side, side, side / 2, side / 2);
        }
        double area = 255.0 * w * h;
        return new Decoded(chain, cell, w, h, (float) (r / area), (float) (g / area), (float) (b / area));
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
}
