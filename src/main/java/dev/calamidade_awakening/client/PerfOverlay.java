package dev.calamidade_awakening.client;

import dev.calamidade_awakening.api.FrameLimits;
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
    // 0.11.0: hot zones, stand-ins and the pool of the terrain pass; stand-in meshes and their vertices.
    private static int terrainZones, lastTerrainZones, terrainStandIns, lastTerrainStandIns, meshes, lastMeshes,
        meshVertices, lastMeshVertices;
    private static dev.calamidade_awakening.api.EnginePasses.Pool terrainPool = dev.calamidade_awakening.api.EnginePasses.Pool.NONE,
        lastTerrainPool = dev.calamidade_awakening.api.EnginePasses.Pool.NONE;
    private static FrameLimits lastLimits = FrameLimits.of(Quality.BALANCED);
    /** Resident instances by source kind (0.10.0): players, entities, blocks, scripts. */
    private static int[] lastSources = new int[4];

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
    /** The pass budget refused a pass this frame (0.11.2, for AUTO). */
    static void passCut(boolean cut) { passCut = cut; }
    private static boolean passCut, lastPassCut;
    private static double lastFrameMs;
    /** Duration of the last complete frame, ms (0 before the second frame). */
    static double lastFrameMs() { return lastFrameMs; }
    /** Whether the pass budget refused a pass in the last complete frame. */
    static boolean lastPassCut() { return lastPassCut; }
    /** F7 line about the quality mode (0.11.2), set by ClientHooks. */
    static java.util.function.Supplier<Component> qualityLine = () -> null;
    /** Active atmospheres and whether the atmosphere pass was queued (0.8.0); outside the addon budget. */
    static void atmosphere(int active, boolean pass) { atmospheres = active; atmospherePass = pass; }
    /** HERO instances drawn and the passes they took from their own budget (0.9.0). */
    static void heroes(int drawn, int passes) { heroes = drawn; heroPasses = passes; }
    /** Beams drawn and the pool that paid for the beams pass (0.9.0). */
    static void beams(int drawn, dev.calamidade_awakening.api.EnginePasses.Pool pool) { beams = drawn; beamPool = pool; }
    /** Lights drawn and the pool that paid for the lights pass (0.9.0). */
    static void lights(int drawn, dev.calamidade_awakening.api.EnginePasses.Pool pool) { lights = drawn; lightPool = pool; }

    /** Global events that apply to this client (0.12.0), set every client tick. */
    static void world(int count) { worldEvents = count; }
    private static int worldEvents;
    /** State of the dimension's sky profile, or null (0.12.0). */
    static void skyState(String state) { skyState = state; }
    private static String skyState;
    /** Global events that apply to this client, as F7 shows them (0.12.0, tests). */
    public static int worldEvents() { return worldEvents; }
    /** Custom backgrounds of the frame (0.14.0): drawn, active, opaque, blocked by the game's sky rules. */
    static void backdrop(int drawn, int active, boolean opaque, boolean blocked) {
        backdropDrawn = drawn; backdropActive = active; backdropOpaque = opaque; backdropBlocked = blocked;
    }
    private static int backdropDrawn, backdropActive;
    /** Sky objects of the frame (0.14.0, phase 3): fields, stars and nebulae drawn, candidates, coverage layer, blocked. */
    static void skyObjects(int fields, int stars, int nebulae, int active, boolean coverage, boolean blocked) {
        objFields = fields; objStars = stars; objNebulae = nebulae; objActive = active; objCoverage = coverage; objBlocked = blocked;
    }
    private static int objFields, objStars, objNebulae, objActive;
    private static boolean objCoverage, objBlocked;
    private static boolean backdropOpaque, backdropBlocked;

    /** Whether the atmosphere pass ran in the last complete frame (0.14.0, tests: a static background needs none). */
    public static boolean atmospherePass() { return lastAtmospherePass; }

    /** Sky layers painted in the last complete frame and whether the sky pass ran (0.12.0, tests). */
    public static int skyLayers() { return lastSkyLayers; }
    public static boolean skyPass() { return lastSkyPass; }

    /** Sky layers painted, active layers that competed and whether the sky pass was queued (0.12.0); outside the budget. */
    static void sky(int painted, int active, boolean pass) { skyLayers = painted; skyActive = active; skyPass = pass; }
    private static int skyLayers, lastSkyLayers, skyActive, lastSkyActive;
    private static boolean skyPass, lastSkyPass;

    /** Hot zones and stand-ins drawn and the pool that paid for the terrain pass (0.11.0). */
    static void terrain(int zones, int standIns, dev.calamidade_awakening.api.EnginePasses.Pool pool) {
        terrainZones = zones; terrainStandIns = standIns; terrainPool = pool;
    }
    /** Structure stand-in meshes drawn, their vertices and the build time of the newest mesh, ms (0.11.0). */
    static void meshes(int count, int vertices, double buildMs) { meshes = count; meshVertices = vertices; meshBuildMs = buildMs; }
    private static double meshBuildMs;

    /** Called from RenderFrameEvent.Pre. */
    static void frameStart(int resident, int[] bySource, FrameLimits limits) {
        lastSources = bySource;
        long now = System.nanoTime();
        long delta = previousFrame == 0 ? 0 : now - previousFrame;
        previousFrame = now;
        lastEffects = effects; lastVolumes = volumes; lastVertices = vertices; lastPasses = passes;
        lastResident = resident; lastLimits = limits;
        lastPassCut = passCut; passCut = false;
        lastFrameMs = delta / 1e6;
        lastAtmospheres = atmospheres; lastAtmospherePass = atmospherePass;
        lastHeroes = heroes; lastHeroPasses = heroPasses;
        lastBeams = beams; lastBeamPool = beamPool; lastLights = lights; lastLightPool = lightPool;
        lastTerrainZones = terrainZones; lastTerrainStandIns = terrainStandIns; lastTerrainPool = terrainPool;
        lastMeshes = meshes; lastMeshVertices = meshVertices;
        lastSkyLayers = skyLayers; lastSkyActive = skyActive; lastSkyPass = skyPass;
        skyLayers = skyActive = 0;
        skyPass = false;
        terrainZones = terrainStandIns = meshes = meshVertices = 0;
        terrainPool = dev.calamidade_awakening.api.EnginePasses.Pool.NONE;
        effects = volumes = vertices = passes = atmospheres = heroes = heroPasses = beams = lights = 0;
        beamPool = lightPool = dev.calamidade_awakening.api.EnginePasses.Pool.NONE;
        atmospherePass = false;
        if (delta <= 0) return;
        frames[frameCursor] = delta;
        frameCursor = (frameCursor + 1) % WINDOW;
        frameCount = Math.min(frameCount + 1, WINDOW);
        CostProbe.frame(delta, lastResident, lastEffects, lastPasses, lastBeams);
        if (recording != null) {
            recording.add(String.format(java.util.Locale.ROOT, "%d,%.3f,%d,%d,%d,%d,%d,%s,%d,%d,%d,%d,%d,%s,%d,%s,%d,%d,%d,%d,%d,%d,%s,%d,%d,%d,%d",
                recording.size(), delta / 1e6, lastResident, lastEffects, lastVolumes, lastVertices, lastPasses, limits.label(),
                lastAtmospheres, lastAtmospherePass ? 1 : 0, lastHeroes, lastHeroPasses, lastBeams, lastBeamPool.name(),
                lastLights, lastLightPool.name(), lastSources[0], lastSources[1], lastSources[2], lastSources[3],
                lastTerrainZones, lastTerrainStandIns, lastTerrainPool.name(), lastMeshes, lastMeshVertices,
                lastSkyLayers, lastSkyPass ? 1 : 0));
            if (System.currentTimeMillis() >= recordingEnds) finishRecording();
        }
    }

    /** Shows the panel (measurements). */
    public static void show() { visible = true; }

    /** Hides the panel (0.12.0: tests take screenshots with and without it). */
    public static void hide() { visible = false; }

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

    /** Frame times of the last {@value #WINDOW} frames, oldest first, ms (0.11.2: Atlas graph). */
    public static double[] recentFrames() {
        double[] out = new double[frameCount];
        int start = (frameCursor - frameCount + WINDOW) % WINDOW;
        for (int i = 0; i < frameCount; i++) out[i] = frames[(start + i) % WINDOW] / 1e6;
        return out;
    }

    /** Last finished recording: frames, median and p95 in ms; null before the first (0.11.2: Atlas). */
    public static double[] lastRecording() { return lastRecording; }
    private static double[] lastRecording;

    /** True while a recording runs. */
    public static boolean recording() { return recording != null; }

    /** Starts a recording of frame times (Atlas button); false when one is running. */
    public static boolean startRecording(int seconds) { return record(seconds); }

    private static void finishRecording() {
        List<String> rows = recording;
        recording = null;
        Minecraft mc = Minecraft.getInstance();
        double[] ms = rows.stream().mapToDouble(row -> Double.parseDouble(row.split(",")[1])).sorted().toArray();
        lastRecording = new double[] {ms.length, percentile(ms, .5), percentile(ms, .95)};
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = mc.gameDirectory.toPath().resolve("calamidade_bench").resolve("frames-" + stamp + ".csv");
        var lines = new ArrayList<String>();
        lines.add("# Calamidade_Awakening frame recording");
        lines.add("# renderer=" + renderer() + "; window=" + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()
            + "; seconds=" + recordingSeconds + "; frameLimit=" + mc.options.framerateLimit().get()
            + "; renderDistance=" + mc.options.renderDistance().get() + "; graphics=" + mc.options.graphicsMode().get().name());
        lines.add("frame,ms,resident,drawn,volumes,vertices,passes,quality,atmospheres,atmospherePass,heroes,heroPasses,beams,beamPass,lights,lightPass,players,entities,blocks,scripts,terrainZones,standIns,terrainPass,meshes,meshVertices,skyLayers,skyPass");
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

    /** The F7 lines (0.11.2: also shown live in the Atlas diagnostics tab). */
    public static List<Component> lines() {
        Minecraft mc = Minecraft.getInstance();
        double[] window = new double[frameCount];
        for (int i = 0; i < frameCount; i++) window[i] = frames[i] / 1e6;
        Arrays.sort(window);
        var lines = new ArrayList<Component>();
        lines.add(Component.translatableWithFallback(PREFIX + "title", "Calamidade_Awakening · diagnostics (%s)",
            TOGGLE.getTranslatedKeyMessage()));
        lines.add(Component.translatableWithFallback(PREFIX + "gpu", "GPU: %s", renderer()));
        lines.add(Component.translatableWithFallback(PREFIX + "frame", "%s FPS · frame %s ms · p95 %s ms · %s",
            mc.getFps(), format(percentile(window, .5)), format(percentile(window, .95)), lastLimits.label()));
        Component quality = qualityLine.get();
        if (quality != null) lines.add(quality);
        lines.add(Component.translatableWithFallback(PREFIX + "scene", "Events: %s resident · %s drawn · %s volumes",
            lastResident, lastEffects, lastVolumes));
        lines.add(Component.translatableWithFallback(PREFIX + "sources", "Sources: %s players · %s mobs · %s blocks · %s scripts",
            lastSources[0], lastSources[1], lastSources[2], lastSources[3]));
        lines.add(Component.translatableWithFallback(PREFIX + "cost", "Vertices %s · post passes %s",
            lastVertices, lastPasses));
        lines.add(Component.translatableWithFallback(PREFIX + "heroes", "Protagonists (HERO): %s drawn · %s/%s passes",
            lastHeroes, lastHeroPasses, lastLimits.heroPasses()));
        lines.add(Component.translatableWithFallback(PREFIX + "beams", "Beams: %s drawn (max %s) · pass %s",
            lastBeams, lastLimits.beams(), pool(lastBeamPool)));
        lines.add(Component.translatableWithFallback(PREFIX + "lights", "Lights: %s drawn (max %s) · pass %s",
            lastLights, lastLimits.lights(), pool(lastLightPool)));
        lines.add(Component.translatableWithFallback(PREFIX + "terrain", "Terrain: %s hot zones · %s stand-ins · pass %s",
            lastTerrainZones, lastTerrainStandIns, pool(lastTerrainPool)));
        lines.add(Component.translatableWithFallback(PREFIX + "meshes", "Stand-in meshes: %s · %s vertices · last build %s ms",
            lastMeshes, lastMeshVertices, format(meshBuildMs)));
        lines.add(Component.translatableWithFallback(PREFIX + "atmosphere", "Atmosphere: %s active · pass %s",
            lastAtmospheres, lastAtmospherePass
                ? Component.translatableWithFallback(PREFIX + "yes", "yes")
                : Component.translatableWithFallback(PREFIX + "no", "no")));
        lines.add(Component.translatableWithFallback(PREFIX + "sky", "Sky: %s layers (%s active, max %s) · pass %s",
            lastSkyLayers, lastSkyActive, lastLimits.skyLayers(), lastSkyPass
                ? Component.translatableWithFallback(PREFIX + "yes", "yes")
                : Component.translatableWithFallback(PREFIX + "no", "no")));
        if (backdropActive > 0 || backdropDrawn > 0)
            lines.add(Component.translatableWithFallback(PREFIX + "backdrop", "Sky background: %s drawn (%s active) · %s",
                backdropDrawn, backdropActive, backdropBlocked
                    ? Component.translatableWithFallback(PREFIX + "backdrop.blocked", "blocked (game sky hidden)")
                    : backdropOpaque ? Component.translatableWithFallback(PREFIX + "backdrop.opaque", "opaque")
                    : Component.translatableWithFallback(PREFIX + "backdrop.partial", "partial")));
        if (objActive > 0)
            lines.add(Component.translatableWithFallback(PREFIX + "skyobjects", "Sky objects: %s fields (%s stars) · %s nebulae (%s active) · coverage %s",
                objFields, objStars, objNebulae, objActive, objBlocked
                    ? Component.translatableWithFallback(PREFIX + "backdrop.blocked", "blocked (game sky hidden)")
                    : objCoverage ? Component.translatableWithFallback(PREFIX + "yes", "yes")
                    : Component.translatableWithFallback(PREFIX + "no", "no")));
        lines.add(Component.translatableWithFallback(PREFIX + "world", "Global events: %s", worldEvents));
        if (!"release".equals(dev.calamidade_awakening.CalamidadeAwakening.BUILD))
            lines.add(Component.literal("Build: " + dev.calamidade_awakening.CalamidadeAwakening.BUILD));
        if (skyState != null)
            lines.add(Component.translatableWithFallback(PREFIX + "skystate", "Dimension sky: %s", skyState));
        if (CostProbe.running()) lines.add(Component.literal(CostProbe.status()));
        if (recording != null) lines.add(Component.translatableWithFallback(PREFIX + "recording", "Recording: %s s left",
            Math.max(0, (recordingEnds - System.currentTimeMillis() + 999) / 1000)));
        return lines;
    }

    static void draw(GuiGraphics graphics) {
        if (!visible) return;
        Minecraft mc = Minecraft.getInstance();
        var lines = lines();
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

    static String renderer() {
        if (renderer == null) {
            String name = GL11.glGetString(GL11.GL_RENDERER);
            renderer = name == null ? "?" : name;
        }
        return renderer;
    }

    private static double percentile(double[] sorted, double fraction) {
        return dev.calamidade_awakening.api.FrameStats.percentile(sorted, fraction);
    }

    private static String format(double ms) { return String.format(java.util.Locale.ROOT, "%.2f", ms); }

}
