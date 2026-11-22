package dev.calamidade_awakening.client;

import dev.calamidade_awakening.api.FrameLimits;
import dev.calamidade_awakening.api.FrameStats;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cost probe (0.11.2): {@code /calamidade_client debug measure effects|passes|all}. Runs a fixed sweep under the
 * internal test profile UNLIMITED and records median and p95 of the frame time of each step, so the same code gives the
 * numbers on any machine (the automated in-game test and the owner's GPU). Each step asks the server for its load with
 * {@code /calamidade bench} (permission level 2; bench instances restart when they end, so the load stays), waits
 * {@link #WARMUP_MS} and records {@link #RECORD_MS}.
 * <ul>
 *   <li>effects: 6, 12, 24, 48 and 60 drawn instances of the varied mix ({@code bench <n> mix}: ring, cube, web and
 *       sky of the vfx-sample, engine effects without it), with no cap on drawn instances nor on passes;</li>
 *   <li>passes: 12 cubes ({@code vfxsample:receita_cubo}) drawn and the common pass budget at 1, 2, 4, 8 and 12, so only
 *       the number of full-screen addon passes changes.</li>
 * </ul>
 * Both start with a base step without effects. The report goes to {@code calamidade_bench/cost-*.txt} (table) and
 * {@code cost-*.csv} (every frame).
 */
public final class CostProbe {
    static final long WARMUP_MS = 4000, RECORD_MS = 8000;
    static final int[] EFFECTS = {6, 12, 24, 48, 60};
    static final int[] PASSES = {1, 2, 4, 8, 12};
    static final int PASS_LOAD = 12;
    static final String CUBE = "vfxsample:receita_cubo";

    /** One step: what the server is asked for and the caps of the frame. */
    record Step(String group, String label, String command, FrameLimits limits) {}

    /** Result of a step: frames recorded, averages of the scene counters and the frame times in ms. */
    public record Result(String group, String label, String profile, int frames, double resident, double drawn,
                         double passes, double beams, double median, double p95, double mean) {}

    private static FrameCoordinator coordinator;
    private static List<Step> steps = List.of();
    private static int index = -1;
    private static boolean recording;
    private static long phaseEnds;
    private static final List<double[]> frames = new ArrayList<>();
    private static final List<String> csv = new ArrayList<>();
    private static final List<Result> results = new ArrayList<>();
    private static List<Result> last = List.of();
    private static Path lastReport;

    private CostProbe() {}

    static void install(FrameCoordinator frameCoordinator) { coordinator = frameCoordinator; }

    /** True while a sweep runs. */
    public static boolean running() { return index >= 0; }

    /** Results of the last finished sweep (empty before the first). */
    public static List<Result> results() { return last; }

    /** Report file of the last finished sweep, or null. */
    public static Path lastReport() { return lastReport; }

    /** One line for F7. */
    public static String status() {
        if (!running()) return "";
        Step step = steps.get(index);
        long left = Math.max(0, (phaseEnds - System.currentTimeMillis() + 999) / 1000);
        return String.format(Locale.ROOT, "Medição %d/%d: %s · %s · %s %d s", index + 1, steps.size(), step.group(),
            step.label(), recording ? "gravando" : "aquecendo", left);
    }

    /** Builds the sweep: {@code effects}, {@code passes} or {@code all}. */
    static List<Step> plan(String which) {
        List<Step> out = new ArrayList<>();
        boolean effects = !which.equals("passes"), passes = !which.equals("effects");
        if (effects) {
            out.add(new Step("efeitos", "base (0)", "calamidade bench stop", FrameLimits.unlimited()));
            for (int n : EFFECTS)
                out.add(new Step("efeitos", n + " desenhados", "calamidade bench " + n + " mix", FrameLimits.unlimited()));
        }
        if (passes) {
            out.add(new Step("passes", "base (0)", "calamidade bench stop", FrameLimits.unlimited(PASS_LOAD, 0)));
            for (int p : PASSES)
                out.add(new Step("passes", p + " passes", "calamidade bench " + PASS_LOAD + " " + CUBE,
                    FrameLimits.unlimited(PASS_LOAD, p)));
        }
        return out;
    }

    /** Starts a sweep; false when one is running or there is no player. */
    public static boolean start(String which) {
        Minecraft mc = Minecraft.getInstance();
        if (running() || mc.player == null || coordinator == null) return false;
        steps = plan(which);
        results.clear();
        csv.clear();
        csv.add("group,step,frame,ms,resident,drawn,passes,beams");
        index = -1;
        next(mc);
        return true;
    }

    /** Client tick: advances the phases. */
    static void tick() {
        if (!running()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            abort();
            return;
        }
        if (System.currentTimeMillis() < phaseEnds) return;
        if (!recording) {
            recording = true;
            frames.clear();
            phaseEnds = System.currentTimeMillis() + RECORD_MS;
            return;
        }
        finishStep();
        next(mc);
    }

    /** Called by {@link PerfOverlay} once per frame with the counters of the last complete frame. */
    static void frame(long deltaNs, int resident, int drawn, int passes, int beams) {
        if (!running() || !recording) return;
        double ms = deltaNs / 1e6;
        frames.add(new double[] {ms, resident, drawn, passes, beams});
        Step step = steps.get(index);
        csv.add(String.format(Locale.ROOT, "%s,%s,%d,%.3f,%d,%d,%d,%d", step.group(), step.label(), frames.size(), ms,
            resident, drawn, passes, beams));
    }

    private static void next(Minecraft mc) {
        index++;
        if (index >= steps.size()) {
            finish(mc);
            return;
        }
        Step step = steps.get(index);
        coordinator.test(step.limits());
        if (mc.getConnection() != null) mc.getConnection().sendCommand(step.command());
        recording = false;
        phaseEnds = System.currentTimeMillis() + WARMUP_MS;
    }

    private static void finishStep() {
        Step step = steps.get(index);
        double[] ms = new double[frames.size()];
        double resident = 0, drawn = 0, passes = 0, beams = 0;
        for (int i = 0; i < ms.length; i++) {
            double[] f = frames.get(i);
            ms[i] = f[0]; resident += f[1]; drawn += f[2]; passes += f[3]; beams += f[4];
        }
        int n = Math.max(1, ms.length);
        double[] s = FrameStats.summary(ms);
        results.add(new Result(step.group(), step.label(), step.limits().label(), ms.length, resident / n, drawn / n,
            passes / n, beams / n, s[0], s[1], s[2]));
    }

    private static void abort() {
        index = -1;
        recording = false;
        if (coordinator != null) coordinator.test(null);
    }

    private static void finish(Minecraft mc) {
        if (mc.getConnection() != null) mc.getConnection().sendCommand("calamidade bench stop");
        abort();
        last = List.copyOf(results);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path dir = mc.gameDirectory.toPath().resolve("calamidade_bench");
        Path text = dir.resolve("cost-" + stamp + ".txt"), table = dir.resolve("cost-" + stamp + ".csv");
        List<String> lines = report(mc);
        Component message;
        try {
            Files.createDirectories(dir);
            Files.write(text, lines, StandardCharsets.UTF_8);
            Files.write(table, csv, StandardCharsets.UTF_8);
            lastReport = text;
            message = Component.translatableWithFallback("perf.calamidade_awakening.measured",
                "Measurement done: %s steps → %s", last.size(), text.getFileName().toString());
        } catch (IOException failure) {
            message = Component.literal("Calamidade_Awakening: " + failure.getMessage());
        }
        if (mc.player != null) mc.player.displayClientMessage(message, false);
    }

    /** Text report: header with the machine and the table of every step. */
    public static List<String> report(Minecraft mc) {
        List<String> lines = new ArrayList<>();
        lines.add("# Calamidade_Awakening · medição de custo (/calamidade_client debug measure) · " + LocalDateTime.now());
        lines.add("# renderer=" + PerfOverlay.renderer() + "; janela=" + mc.getWindow().getWidth() + "x"
            + mc.getWindow().getHeight() + "; limiteQuadros=" + mc.options.framerateLimit().get() + "; vsync="
            + mc.options.enableVsync().get() + "; distancia=" + mc.options.renderDistance().get() + "; graficos="
            + mc.options.graphicsMode().get().name());
        lines.add("# aquecimento " + WARMUP_MS / 1000 + " s, gravação " + RECORD_MS / 1000 + " s por passo");
        lines.add(String.format(Locale.ROOT, "%-8s %-15s %-18s %7s %9s %10s %7s %7s %10s %8s %8s", "grupo", "passo",
            "perfil", "quadros", "residentes", "desenhados", "passes", "feixes", "mediana_ms", "p95_ms", "media_ms"));
        for (Result r : results)
            lines.add(String.format(Locale.ROOT, "%-8s %-15s %-18s %7d %9.1f %10.1f %7.1f %7.1f %10.2f %8.2f %8.2f",
                r.group(), r.label(), r.profile(), r.frames(), r.resident(), r.drawn(), r.passes(), r.beams(),
                r.median(), r.p95(), r.mean()));
        return lines;
    }
}
