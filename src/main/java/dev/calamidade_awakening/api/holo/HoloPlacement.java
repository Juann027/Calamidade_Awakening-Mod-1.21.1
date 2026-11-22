package dev.calamidade_awakening.api.holo;

import java.util.List;

/**
 * Where to summon the Star Atlas in the world without pushing it into walls (0.11.2), pure: the sight test comes from
 * outside ({@link Sight}, a {@code level.clip} on the client) so {@code checkBudget} can use fake walls. Adapted from
 * the RespectCraft holographic menu ({@code com.respectcraft.ui.HoloPlacement}, stage 4.8.4-A, same author); no
 * RespectCraft class is used. Not public API.
 *
 * <p>The board scales around its eye: bringing it closer keeps its apparent size and readability. The search first
 * shortens the distance along the aim, then turns the board to the sides and, when nothing fits, keeps the least
 * obstructed candidate (ties: the closer one) and reports it.
 */
public final class HoloPlacement {
    /** Samples that must be clear (main cards, tabs). */
    public static final int ESSENTIAL = 0;
    /** Up to {@link #IMPORTANT_TOLERANCE} of these may be blocked (side cards). */
    public static final int IMPORTANT = 1;
    /** Tie break only (outer decoration: depth testing hides what enters a wall). */
    public static final int OPTIONAL = 2;
    static final float IMPORTANT_TOLERANCE = 0.3F;
    /** Board turns tried after shortening the distance, degrees. */
    static final float[] YAW_OFFSETS = {0F, -25F, 25F, -50F, 50F};

    private HoloPlacement() {}

    /** Clear line (no solid block) between two world points. */
    @FunctionalInterface
    public interface Sight {
        boolean clear(double fx, double fy, double fz, double tx, double ty, double tz);
    }

    /** A board point relative to the eye: right, up and forward, in units of the front distance. */
    public record Sample(float right, float up, float forward, int kind) {}

    /** Chosen anchor: board yaw (Minecraft degrees), front distance (blocks) and whether it really fits. */
    public record Result(float yaw, float distance, boolean fits) {}

    /** Forward direction of a Minecraft yaw (0 = south/+z, 90 = west/-x). */
    public static double forwardX(float yaw) { return -Math.sin(Math.toRadians(yaw)); }
    public static double forwardZ(float yaw) { return Math.cos(Math.toRadians(yaw)); }
    /** Right of whoever looks along the yaw. */
    public static double rightX(float yaw) { return -Math.cos(Math.toRadians(yaw)); }
    public static double rightZ(float yaw) { return -Math.sin(Math.toRadians(yaw)); }

    /** Searches the anchor from the eye {@code (ex, ey, ez)}: {@code preferred} and {@code min} distances in blocks. */
    public static Result choose(double ex, double ey, double ez, float yaw, float preferred, float min,
                                List<Sample> samples, Sight sight) {
        float lo = Math.min(min, preferred), step = Math.max(0.05F, preferred * 0.15F);
        Result best = null;
        float bestScore = Float.MAX_VALUE;
        for (float offset : YAW_OFFSETS) {
            float y = yaw + offset;
            for (float d = preferred; ; d = Math.max(lo, d - step)) {
                float score = score(ex, ey, ez, y, d, samples, sight);
                if (score >= 0F) return new Result(y, d, true);
                float penalty = -score + d * 0.01F + Math.abs(offset) * 0.001F;
                if (penalty < bestScore) {
                    bestScore = penalty;
                    best = new Result(y, d, false);
                }
                if (d <= lo) break;
            }
        }
        return best == null ? new Result(yaw, lo, false) : best;
    }

    /** {@code >= 0} when accepted; negative = weighted obstruction (essential 4, important 1, optional 0.25). */
    static float score(double ex, double ey, double ez, float yaw, float distance, List<Sample> samples, Sight sight) {
        double fx = forwardX(yaw), fz = forwardZ(yaw), rx = rightX(yaw), rz = rightZ(yaw);
        int essential = 0, important = 0, importantTotal = 0, optional = 0;
        for (Sample s : samples) {
            double px = ex + distance * (rx * s.right() + fx * s.forward());
            double py = ey + distance * s.up();
            double pz = ez + distance * (rz * s.right() + fz * s.forward());
            if (s.kind() == IMPORTANT) importantTotal++;
            if (sight.clear(ex, ey, ez, px, py, pz)) continue;
            switch (s.kind()) {
                case ESSENTIAL -> essential++;
                case IMPORTANT -> important++;
                default -> optional++;
            }
        }
        if (essential == 0 && important <= importantTotal * IMPORTANT_TOLERANCE) return 0F;
        return -(essential * 4F + important + optional * 0.25F + 0.001F);
    }
}
