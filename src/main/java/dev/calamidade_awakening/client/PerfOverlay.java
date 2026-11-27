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
    /** Fase 4: F7 page (1 general, 2 coverage and lights, 3 sky and terrain); F7 cycles hidden, 1, 2, 3. */
    private static int page = 1;
    static final int PAGES = 3;
    private static String renderer;
    // Fase 4: where the scene went (instances and declared lights) and the coverage counters of the lights pass.
    private static StageScene.Coverage coverage = StageScene.Coverage.NONE;
    private static int[] coverageCounters = new int[11];

    /** Fase 4: coverage of the frame (set every frame by the backend). */
    static void coverage(StageScene.Coverage c, int[] counters) { coverage = c; coverageCounters = counters; }

    /** Fase 4 (tests): instance coverage of the last frame. */
    public static StageScene.Coverage coverage() { return coverage; }

    /**
     * Fase 4 (tests): lights from light-only instances, not resolved, kept by cap, added by density, cookies added by
     * density, density per tile, cells of the candidates, cells kept, coverage on, AUTO relief on, lights that entered the
     * kept set in the frame (churn).
     */
    public static int[] coverageCounters() { return coverageCounters.clone(); }

    /** Fase 4: the last frame used extras AUTO relief can turn off (density admission or sampled cookies). */
    static boolean lastRelievable() { return coverageCounters[3] > 0 || coverageCounters[4] > 0 || lightDetail[3] > 0; }

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
    /** 0.13.0: rig anchors and custom boss bars of the frame (set by client.boss); null hides the line. */
    public static java.util.function.Supplier<Component> bossLine = () -> null;
    /** Active atmospheres and whether the atmosphere pass was queued (0.8.0); outside the addon budget. */
    static void atmosphere(int active, boolean pass) { atmospheres = active; atmospherePass = pass; }
    /** HERO instances drawn and the passes they took from their own budget (0.9.0). */
    static void heroes(int drawn, int passes) { heroes = drawn; heroPasses = passes; }
    /** Beams drawn and the pool that paid for the beams pass (0.9.0). */
    static void beams(int drawn, dev.calamidade_awakening.api.EnginePasses.Pool pool) { beams = drawn; beamPool = pool; }
    // 0.15.0: lifecycle of resident instances and details of the lights pass (set every frame by their owners).
    private static int[] lifecycle = new int[3];
    private static int[] lightDetail = new int[13];
    private static long cookieBytes;

    /** Resident sustained instances in their loop, released ones, instances dropped by reconciliation (0.15.0). */
    static void lifecycle(int held, int out, int reconciled) { lifecycle = new int[] {held, out, reconciled}; }

    /**
     * Lights of the frame (0.15.0): requested, culled (frustum or fog), over the cap, textured, cookie fallbacks,
     * cookie cap; atlas images ready, waiting, failed and uploads; atlas bytes.
     */
    static void lightDetail(int requested, int culled, int capped, int textured, int fallback, int cookieCap, int[] atlas,
                            long bytes, int large) {
        lightDetail = new int[] {requested, culled, capped, textured, fallback, cookieCap, atlas[0], atlas[1], atlas[2], atlas[3],
            atlas[5], atlas[6], large};
        cookieBytes = bytes;
    }

    private static int[] lightBudget = new int[26];

    /**
     * Fase 3 details of the lights pass: culled with no visible contribution, cookie lights kept, cookies over the
     * budget, cookies not loaded yet, frame blends dropped, tiles on, average lights per tile ×10, max lights per tile,
     * max cookies per tile; images resident at 128/256/512/1024/2048, reduced, resolution swaps, entered reduced,
     * deferred uploads, standard and large atlas occupation %, atlas pressure, KiB uploaded; effective lights and
     * cookie caps; CRITICAL cookie lights kept and how many sampled their image.
     */
    static void lightBudget(int[] values) { lightBudget = values; }

    /** Last fase 3 light details (tests), layout of {@link #lightBudget(int[])}. */
    public static int[] lightBudget() { return lightBudget.clone(); }

    /**
     * Last light details (tests): requested, culled, capped, textured, fallback, cap, ready, waiting, failed, uploads;
     * since fase 2 also large versions resident, large versions loading, lights sampling a large version this frame.
     */
    public static int[] lightDetail() { return lightDetail.clone(); }

    /** Bytes of the light cookie atlas (0 when not allocated), tests and F7. */
    public static long cookieBytes() { return cookieBytes; }

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
    /**
     * Sprites of the frame (0.14.0, phase 4): images and impostors drawn, front layer, sprite textures, estimated memory
     * and budget, uploads this frame, pending loads, failures, refusals, frozen flipbooks and the last problem.
     */
    static void skySprites(int images, int impostors, boolean front, int textures, long memory, long budget, int uploads,
                           int pending, int failures, int refusals, int frozen, String problem) {
        spImages = images; spImpostors = impostors; spFront = front; spTextures = textures; spMemory = memory; spBudget = budget;
        spUploads = uploads; spPending = pending; spFailures = failures; spRefusals = refusals; spFrozen = frozen; spProblem = problem;
    }
    private static int spImages, spImpostors, spTextures, spUploads, spPending, spFailures, spRefusals, spFrozen;
    private static long spMemory, spBudget;
    private static boolean spFront;
    private static String spProblem = "";
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

    /** Shows the panel (measurements), first page. */
    public static void show() { show(1); }

    /** Fase 4: shows one page of the panel (1 general, 2 coverage and lights, 3 sky and terrain). */
    public static void show(int which) {
        visible = true;
        page = Math.clamp(which, 1, PAGES);
    }

    /** Hides the panel (0.12.0: tests take screenshots with and without it). */
    public static void hide() { visible = false; }

    static void tick() {
        // Fase 4: F7 cycles hidden → general → coverage and lights → sky and terrain → hidden.
        while (TOGGLE.consumeClick()) {
            if (!visible) { visible = true; page = 1; }
            else if (page < PAGES) page++;
            else visible = false;
        }
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

    /** The F7 lines of every page (0.11.2: also shown live in the Atlas diagnostics tab). */
    public static List<Component> lines() { return lines(0); }

    /**
     * Fase 4: the lines of one page (0 = all): 1 general (frame, quality, instances, passes, totals), 2 coverage and
     * lights (where the scene went, limits, cookies, resolution, tiles), 3 sky and terrain.
     */
    public static List<Component> lines(int which) {
        var all = sections();
        var out = new ArrayList<Component>();
        for (var e : all) if (which == 0 || e.getKey() == 0 || e.getKey() == which) out.add(e.getValue());
        return out;
    }

    /** Fase 4: lines tagged with the page they belong to (0 = every page). */
    private static final class Sectioned extends ArrayList<Component> {
        final List<java.util.Map.Entry<Integer, Component>> tagged = new ArrayList<>();
        int section;
        @Override public boolean add(Component c) {
            tagged.add(java.util.Map.entry(section, c));
            return super.add(c);
        }
    }

    private static List<java.util.Map.Entry<Integer, Component>> sections() {
        var lines = new Sectioned();
        sectionsInto(lines);
        return lines.tagged;
    }

    private static void sectionsInto(Sectioned lines) {
        Minecraft mc = Minecraft.getInstance();
        double[] window = new double[frameCount];
        for (int i = 0; i < frameCount; i++) window[i] = frames[i] / 1e6;
        Arrays.sort(window);
        lines.add(Component.translatableWithFallback(PREFIX + "title", "Calamidade_Awakening · diagnostics (%s)",
            TOGGLE.getTranslatedKeyMessage()));
        lines.add(Component.translatableWithFallback(PREFIX + "gpu", "GPU: %s", renderer()));
        lines.add(Component.translatableWithFallback(PREFIX + "frame", "%s FPS · frame %s ms · p95 %s ms · %s",
            mc.getFps(), format(percentile(window, .5)), format(percentile(window, .95)), lastLimits.label()));
        Component quality = qualityLine.get();
        if (quality != null) lines.add(quality);
        section(lines, 1);
        Component boss = bossLine.get();
        if (boss != null) lines.add(boss);
        lines.add(Component.translatableWithFallback(PREFIX + "scene", "Events: %s resident · %s drawn · %s volumes",
            lastResident, lastEffects, lastVolumes));
        lines.add(Component.translatableWithFallback(PREFIX + "sources", "Sources: %s players · %s mobs · %s blocks · %s scripts",
            lastSources[0], lastSources[1], lastSources[2], lastSources[3]));
        lines.add(Component.translatableWithFallback(PREFIX + "lifecycle", "Sustained: %s looping · %s releasing · %s dropped (server lists)",
            lifecycle[0], lifecycle[1], lifecycle[2]));
        lines.add(Component.translatableWithFallback(PREFIX + "cost", "Vertices %s · post passes %s",
            lastVertices, lastPasses));
        lines.add(Component.translatableWithFallback(PREFIX + "heroes", "Protagonists (HERO): %s drawn · %s/%s passes",
            lastHeroes, lastHeroPasses, lastLimits.heroPasses()));
        lines.add(Component.translatableWithFallback(PREFIX + "beams", "Beams: %s drawn (max %s) · pass %s",
            lastBeams, lastLimits.beams(), pool(lastBeamPool)));
        lines.add(Component.translatableWithFallback(PREFIX + "lights", "Lights: %s drawn (max %s) · pass %s",
            lastLights, lastLimits.lights(), pool(lastLightPool)));
        section(lines, 2);
        coverageLines(lines);
        if (lightDetail[0] > 0 || cookieBytes > 0)
            lines.add(Component.translatableWithFallback(PREFIX + "lights.detail",
                "  %s requested · %s culled · %s over cap · cookies %s/%s (%s fallback, %s high-res) · atlas %s ready %s waiting %s failed, %s high-res (%s loading) · %s uploads · %s MB",
                lightDetail[0], lightDetail[1], lightDetail[2], lightDetail[3], lightDetail[5], lightDetail[4], lightDetail[12],
                lightDetail[6], lightDetail[7], lightDetail[8], lightDetail[10], lightDetail[11], lightDetail[9],
                format(cookieBytes / 1048576.0)));
        if (lightDetail[0] > 0 || cookieBytes > 0) {
            int[] b = lightBudget;
            // Fase 3: limits, why lights or cookies lost quality, effective resolutions, atlas and per-pixel cost.
            lines.add(Component.translatableWithFallback(PREFIX + "lights.budget",
                "  limits %s lights · %s cookies · %s dark (no contribution) · cookies %s kept: %s over budget, %s loading, %s single-frame (pixel pressure) · CRITICAL %s/%s textured",
                b[22], b[23], b[0], b[1], b[2], b[3], b[4], b[25], b[24]));
            lines.add(Component.translatableWithFallback(PREFIX + "lights.resolution",
                "  resolution 128:%s 256:%s 512:%s 1024:%s 2048:%s · %s reduced (atlas pressure %s) · %s swaps · %s entered reduced · %s deferred uploads · atlas %s%% · large %s%% · %s MB uploaded",
                b[9], b[10], b[11], b[12], b[13], b[14], b[20] == 1 ? "yes" : "no", b[15], b[16], b[17], b[18], b[19],
                format(b[21] / 1024.0)));
            lines.add(Component.translatableWithFallback(PREFIX + "lights.tiles",
                "  per pixel: tiles %s · %s lights/tile average · %s max · %s cookies max in one tile",
                b[5] == 1 ? "on" : "off", format(b[6] / 10.0), b[7], b[8]));
        }
        section(lines, 3);
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
        if (spImages + spImpostors + spTextures + spPending + spFailures + spRefusals > 0) {
            lines.add(Component.translatableWithFallback(PREFIX + "skysprites", "Sky images: %s · impostors: %s · front layer %s · frozen %s",
                spImages, spImpostors, spFront ? Component.translatableWithFallback(PREFIX + "yes", "yes")
                    : Component.translatableWithFallback(PREFIX + "no", "no"), spFrozen));
            lines.add(Component.translatableWithFallback(PREFIX + "skytextures",
                "Sky textures: %s · %s/%s MiB · uploads %s · pending %s · invalid %s · refused %s",
                spTextures, spMemory >> 20, spBudget >> 20, spUploads, spPending, spFailures, spRefusals));
            if (!spProblem.isEmpty()) lines.add(Component.literal(spProblem.length() > 110 ? spProblem.substring(0, 110) + "…" : spProblem));
        }
        lines.add(Component.translatableWithFallback(PREFIX + "world", "Global events: %s", worldEvents));
        section(lines, 1);
        if (!"release".equals(dev.calamidade_awakening.CalamidadeAwakening.BUILD))
            lines.add(Component.literal("Build: " + dev.calamidade_awakening.CalamidadeAwakening.BUILD));
        if (skyState != null)
            lines.add(Component.translatableWithFallback(PREFIX + "skystate", "Dimension sky: %s", skyState));
        if (CostProbe.running()) lines.add(Component.literal(CostProbe.status()));
        if (recording != null) lines.add(Component.translatableWithFallback(PREFIX + "recording", "Recording: %s s left",
            Math.max(0, (recordingEnds - System.currentTimeMillis() + 999) / 1000)));
    }

    /** Switches the section the next lines belong to (the anonymous list of {@link #sections}). */
    private static void section(Sectioned lines, int section) { lines.section = section; }

    /**
     * Fase 4: where each part of the scene went, from the resident instances to the drawn lights, with the limits that
     * applied (configured, default, custom) and the reason of every loss.
     */
    private static void coverageLines(Sectioned lines) {
        var c = coverage;
        int[] k = coverageCounters;
        boolean on = k[8] == 1;
        lines.add(Component.translatableWithFallback(PREFIX + "coverage.instances",
            "Coverage first %s · instances: %s resident · %s eligible · %s drawn (cap %s) · %s light-only · %s excluded",
            on ? "on" : "off", c.resident(), c.eligible(), c.drawn(), lastLimits.effects(), c.lightOnly(), c.excluded()));
        lines.add(Component.translatableWithFallback(PREFIX + "coverage.declared",
            "  lights declared %s: %s in drawn · %s in light-only · %s in excluded (range, fade, anchor) · %s in culled instances",
            c.declared(), c.declaredDrawn(), c.declaredLightOnly(), c.declaredExcluded(),
            Math.max(0, c.declared() - c.declaredDrawn() - c.declaredLightOnly() - c.declaredExcluded())));
        lines.add(Component.translatableWithFallback(PREFIX + "coverage.pass",
            "  pass: %s candidates (%s from light-only, %s over the CPU ceiling) · %s culled · %s dark · %s by cap + %s by density (%s/tile) · %s over cap · %s drawn",
            lightDetail[0], k[0], k[1], lightDetail[1], lightBudget[0], k[2], k[3], k[5], lightDetail[2], lastLights));
        lines.add(Component.translatableWithFallback(PREFIX + "coverage.cookies",
            "  cookies: %s by budget + %s by density · %s fallback · distribution: %s of %s world cells kept · AUTO relief %s",
            Math.max(0, lightDetail[3] - k[4]), k[4], lightDetail[4], k[7], k[6], k[9] == 1 ? "on" : "off"));
        dev.calamidade_awakening.api.QualityProfile profile = null;
        for (var each : dev.calamidade_awakening.api.QualityProfile.values()) if (each.name().equals(lastLimits.name())) profile = each;
        if (profile != null) {
            var d = profile.defaults;
            lines.add(Component.translatableWithFallback(PREFIX + "coverage.limits",
                "  limits %s: drawn %s · lights %s · cookies %s · beams %s · passes %s (defaults %s/%s/%s/%s/%s)%s",
                profile.name(), lastLimits.effects(), lastLimits.lights(), lastLimits.cookies(), lastLimits.beams(), lastLimits.marks(),
                d.effects(), d.lights(), Math.min(d.cookies(), d.lights()), d.beams(), d.passes(),
                lastLimits.effects() != d.effects() || lastLimits.lights() != d.lights() || lastLimits.beams() != d.beams()
                    || lastLimits.marks() != d.passes() ? " · CUSTOM (/calamidade_client quality defaults)" : ""));
        }
    }

    static void draw(GuiGraphics graphics) {
        if (!visible) return;
        Minecraft mc = Minecraft.getInstance();
        var lines = new ArrayList<Component>(lines(page));
        lines.add(1, Component.translatableWithFallback(PREFIX + "page", "Page %s/%s (%s: next) · %s",
            page, PAGES, TOGGLE.getTranslatedKeyMessage(), Component.translatableWithFallback(PREFIX + "page." + page,
                page == 1 ? "general" : page == 2 ? "coverage and lights" : "sky and terrain")));
        // Fase 4: long lines wrap at the GUI width instead of leaving the screen; nothing is drawn below its bottom.
        int max = Math.max(120, graphics.guiWidth() - 12);
        var rows = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (Component line : lines) rows.addAll(mc.font.split(line, max));
        int fit = Math.max(1, (graphics.guiHeight() - 8) / 10);
        if (rows.size() > fit) rows = new ArrayList<>(rows.subList(0, fit));
        int width = 0;
        for (var row : rows) width = Math.max(width, mc.font.width(row));
        int x = 4, y = 4;
        graphics.fill(x - 2, y - 2, x + width + 2, y + rows.size() * 10, 0xc0101820);
        for (var row : rows) {
            graphics.drawString(mc.font, row, x, y, 0xffd2e9f4, false);
            y += 10;
        }
    }

    private static Component pool(dev.calamidade_awakening.api.EnginePasses.Pool pool) {
        return switch (pool) {
            case HERO -> Component.translatableWithFallback(PREFIX + "pool.hero", "HERO");
            case COMMON -> Component.translatableWithFallback(PREFIX + "pool.common", "common");
            case ENGINE -> Component.translatableWithFallback(PREFIX + "pool.engine", "engine");
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
