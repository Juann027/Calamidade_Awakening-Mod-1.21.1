package dev.calamidade_awakening.client;

import dev.calamidade_awakening.api.Quality;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Diagnostic panel (F7) and frame-time recorder for tests on physical GPUs.
 * Counters are written by the renderers during a frame and published when the next frame starts,
 * so the panel always shows the last complete frame regardless of render stage order.
 */
public final class PerfOverlay {
    static final KeyMapping TOGGLE = new KeyMapping("key.calamidade_awakening.stats", GLFW.GLFW_KEY_F7,
        "key.categories.calamidade_awakening");
    private static final String PREFIX = "perf.calamidade_awakening.";
    private static final int WINDOW = 240;

    private static boolean visible;
    private static String renderer;

    // Written during the frame being rendered.
    private static int effects, volumes, vertices, passes, atmospheres, heroes, heroPasses;
    private static boolean atmospherePass;
    // Last complete frame.
    private static int lastEffects, lastVolumes, lastVertices, lastPasses, lastResident, lastAtmospheres;
    private static int lastHeroes, lastHeroPasses;
    private static int beams, lastBeams, lights, lastLights;
    private static dev.calamidade_awakening.api.EnginePasses.Pool beamPool = dev.calamidade_awakening.api.EnginePasses.Pool.NONE,
        lastBeamPool = dev.calamidade_awakening.api.EnginePasses.Pool.NONE,
        lightPool = dev.calamidade_awakening.api.EnginePasses.Pool.NONE,
        lastLightPool = dev.calamidade_awakening.api.EnginePasses.Pool.NONE;
    private static boolean lastAtmospherePass;
    private static Quality lastQuality = Quality.BALANCED;

    private static final long[] frames = new long[WINDOW];
    private static int frameCount, frameCursor;
    private static long previousFrame;

    private static List<String> recording;
    private static long recordingEnds;
    private static int recordingSeconds;

    private PerfOverlay() {}

    static void plan(StageScene.Plan plan) {
        effects = plan.effects().size();
        volumes = plan.volumes().size();
    }
    static void vertices(int count) { vertices = count; }
    static void passes(int count) { passes = count; }
    /** Active atmospheres and whether the atmosphere pass was queued (0.8.0); outside the addon budget. */
    static void atmosphere(int active, boolean pass) { atmospheres = active; atmospherePass = pass; }
    /** HERO instances drawn and the passes they took from their own budget (0.9.0). */
    static void heroes(int drawn, int passes) { heroes = drawn; heroPasses = passes; }
    /** Beams drawn and the pool that paid for the beams pass (0.9.0). */
    static void beams(int drawn, dev.calamidade_awakening.api.EnginePasses.Pool pool) { beams = drawn; beamPool = pool; }
    /** Lights drawn and the pool that paid for the lights pass (0.9.0). */
    static void lights(int drawn, dev.calamidade_awakening.api.EnginePasses.Pool pool) { lights = drawn; lightPool = pool; }

    /** Called from RenderFrameEvent.Pre. */
    static void frameStart(int resident, Quality quality) {
        long now = System.nanoTime();
        long delta = previousFrame == 0 ? 0 : now - previousFrame;
        previousFrame = now;
        lastEffects = effects; lastVolumes = volumes; lastVertices = vertices; lastPasses = passes;
        lastResident = resident; lastQuality = quality;
        lastAtmospheres = atmospheres; lastAtmospherePass = atmospherePass;
        lastHeroes = heroes; lastHeroPasses = heroPasses;
        lastBeams = beams; lastBeamPool = beamPool; lastLights = lights; lastLightPool = lightPool;
        effects = volumes = vertices = passes = atmospheres = heroes = heroPasses = beams = lights = 0;
        beamPool = lightPool = dev.calamidade_awakening.api.EnginePasses.Pool.NONE;
        atmospherePass = false;
        if (delta <= 0) return;
        frames[frameCursor] = delta;
        frameCursor = (frameCursor + 1) % WINDOW;
        frameCount = Math.min(frameCount + 1, WINDOW);
        if (recording != null) {
            recording.add(String.format(java.util.Locale.ROOT, "%d,%.3f,%d,%d,%d,%d,%d,%s,%d,%d,%d,%d,%d,%s,%d,%s",
                recording.size(), delta / 1e6, lastResident, lastEffects, lastVolumes, lastVertices, lastPasses, quality.name(),
                lastAtmospheres, lastAtmospherePass ? 1 : 0, lastHeroes, lastHeroPasses, lastBeams, lastBeamPool.name(),
                lastLights, lastLightPool.name()));
            if (System.currentTimeMillis() >= recordingEnds) finishRecording();
        }
    }

    static void tick() {
        while (TOGGLE.consumeClick()) visible = !visible;
    }

    /** Starts a CSV recording of frame times; returns false if one is already running. */
    static boolean record(int seconds) {
        if (recording != null) return false;
        recording = new ArrayList<>();
        recordingSeconds = seconds;
        recordingEnds = System.currentTimeMillis() + seconds * 1000L;
        visible = true;
        return true;
    }

    private static void finishRecording() {
        List<String> rows = recording;
        recording = null;
        Minecraft mc = Minecraft.getInstance();
        double[] ms = rows.stream().mapToDouble(row -> Double.parseDouble(row.split(",")[1])).sorted().toArray();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = mc.gameDirectory.toPath().resolve("calamidade_bench").resolve("frames-" + stamp + ".csv");
        var lines = new ArrayList<String>();
        lines.add("# Calamidade_Awakening frame recording");
        lines.add("# renderer=" + renderer() + "; window=" + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()
            + "; seconds=" + recordingSeconds + "; frameLimit=" + mc.options.framerateLimit().get()
            + "; renderDistance=" + mc.options.renderDistance().get() + "; graphics=" + mc.options.graphicsMode().get().name());
        lines.add("frame,ms,resident,drawn,volumes,vertices,passes,quality,atmospheres,atmospherePass,heroes,heroPasses,beams,beamPass,lights,lightPass");
        lines.addAll(rows);
        Component message;
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
            message = Component.translatableWithFallback(PREFIX + "saved", "Saved %s frames: median %s ms, p95 %s ms → %s",
                ms.length, format(percentile(ms, .5)), format(percentile(ms, .95)), file.getFileName().toString());
        } catch (IOException failure) {
            message = Component.literal("Calamidade_Awakening: " + failure.getMessage());
        }
        if (mc.player != null) mc.player.displayClientMessage(message, false);
    }

    static void draw(GuiGraphics graphics) {
        if (!visible) return;
        Minecraft mc = Minecraft.getInstance();
        double[] window = new double[frameCount];
        for (int i = 0; i < frameCount; i++) window[i] = frames[i] / 1e6;
        Arrays.sort(window);
        var lines = new ArrayList<Component>();
        lines.add(Component.translatableWithFallback(PREFIX + "title", "Calamidade_Awakening · diagnostics (%s)",
            TOGGLE.getTranslatedKeyMessage()));
        lines.add(Component.translatableWithFallback(PREFIX + "gpu", "GPU: %s", renderer()));
        lines.add(Component.translatableWithFallback(PREFIX + "frame", "%s FPS · frame %s ms · p95 %s ms · %s",
            mc.getFps(), format(percentile(window, .5)), format(percentile(window, .95)), lastQuality.name()));
        lines.add(Component.translatableWithFallback(PREFIX + "scene", "Events: %s resident · %s drawn · %s volumes",
            lastResident, lastEffects, lastVolumes));
        lines.add(Component.translatableWithFallback(PREFIX + "cost", "Vertices %s · post passes %s",
            lastVertices, lastPasses));
        lines.add(Component.translatableWithFallback(PREFIX + "heroes", "Protagonists (HERO): %s drawn · %s/%s passes",
            lastHeroes, lastHeroPasses, lastQuality.heroPasses));
        lines.add(Component.translatableWithFallback(PREFIX + "beams", "Beams: %s drawn (max %s) · pass %s",
            lastBeams, lastQuality.beams, pool(lastBeamPool)));
        lines.add(Component.translatableWithFallback(PREFIX + "lights", "Lights: %s drawn (max %s) · pass %s",
            lastLights, lastQuality.lights, pool(lastLightPool)));
        lines.add(Component.translatableWithFallback(PREFIX + "atmosphere", "Atmosphere: %s active · pass %s",
            lastAtmospheres, lastAtmospherePass
                ? Component.translatableWithFallback(PREFIX + "yes", "yes")
                : Component.translatableWithFallback(PREFIX + "no", "no")));
        if (recording != null) lines.add(Component.translatableWithFallback(PREFIX + "recording", "Recording: %s s left",
            Math.max(0, (recordingEnds - System.currentTimeMillis() + 999) / 1000)));
        int width = 0;
        for (Component line : lines) width = Math.max(width, mc.font.width(line));
        int x = 4, y = 4;
        graphics.fill(x - 2, y - 2, x + width + 2, y + lines.size() * 10, 0xc0101820);
        for (Component line : lines) {
            graphics.drawString(mc.font, line, x, y, 0xffd2e9f4, false);
            y += 10;
        }
    }

    private static Component pool(dev.calamidade_awakening.api.EnginePasses.Pool pool) {
        return switch (pool) {
            case HERO -> Component.translatableWithFallback(PREFIX + "pool.hero", "HERO");
            case COMMON -> Component.translatableWithFallback(PREFIX + "pool.common", "common");
            case NONE -> Component.translatableWithFallback(PREFIX + "no", "no");
        };
    }

    private static String renderer() {
        if (renderer == null) {
            String name = GL11.glGetString(GL11.GL_RENDERER);
            renderer = name == null ? "?" : name;
        }
        return renderer;
    }

    private static double percentile(double[] sorted, double fraction) {
        if (sorted.length == 0) return 0;
        int index = (int) Math.ceil(fraction * sorted.length) - 1;
        return sorted[Math.clamp(index, 0, sorted.length - 1)];
    }

    private static String format(double ms) { return String.format(java.util.Locale.ROOT, "%.2f", ms); }

}
