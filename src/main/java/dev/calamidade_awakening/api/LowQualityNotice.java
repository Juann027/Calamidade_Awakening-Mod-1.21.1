package dev.calamidade_awakening.api;

/**
 * "Reduced Visual Quality" notice (0.15.0, phase 5), pure (no Minecraft), shared by the client and the tests. Not public
 * API.
 *
 * <p>The player is told once that LOW simplifies or omits effects: when LOW is chosen by hand (F8 or the Atlas) and when
 * AUTO moves into LOW. The notice never blocks, never asks for confirmation and never stops LOW from being used. Spam
 * guards:
 * <ul>
 *   <li>Nothing is repeated while LOW stays active: only a transition into LOW can notify.</li>
 *   <li>A manual selection notifies again only after {@link #MANUAL_COOLDOWN_MS} (cycling F8 passes LOW every lap).</li>
 *   <li>An AUTO transition notifies only after {@link #AUTO_COOLDOWN_MS} since the last notice of any kind, and only
 *       when the profile spent at least {@link #AUTO_REARM_MS} above LOW first: AUTO oscillating around LOW in a heavy
 *       fight stays quiet.</li>
 * </ul>
 */
public final class LowQualityNotice {
    /** Least time between two notices for manual selections. */
    public static final long MANUAL_COOLDOWN_MS = 60_000;
    /** Least time between an AUTO notice and the previous notice. */
    public static final long AUTO_COOLDOWN_MS = 10 * 60_000;
    /** Time AUTO must spend above LOW before entering LOW again can notify. */
    public static final long AUTO_REARM_MS = 30_000;

    /** What to show. */
    public enum Kind { NONE, MANUAL, AUTO }

    private QualityProfile last;
    private long lastNotice = Long.MIN_VALUE / 2;
    private long leftLow = Long.MIN_VALUE / 2;

    /**
     * Call on a manual selection (F8, Atlas, debug command) with the profile it applies. Returns {@link Kind#MANUAL} when
     * the notice should be shown.
     */
    public Kind manual(QualityProfile profile, long nowMs) {
        boolean enter = profile == QualityProfile.LOW;
        track(profile, nowMs);
        if (!enter || nowMs - lastNotice < MANUAL_COOLDOWN_MS) return Kind.NONE;
        lastNotice = nowMs;
        return Kind.MANUAL;
    }

    /**
     * Call every frame (or on every AUTO change) with the profile in use and whether AUTO chose it. Returns
     * {@link Kind#AUTO} when AUTO has just moved into LOW and the guards allow a notice.
     */
    public Kind frame(QualityProfile profile, boolean auto, long nowMs) {
        QualityProfile before = last;
        long above = nowMs - leftLow;
        track(profile, nowMs);
        if (!auto || profile != QualityProfile.LOW || before == null || before == QualityProfile.LOW) return Kind.NONE;
        if (above < AUTO_REARM_MS || nowMs - lastNotice < AUTO_COOLDOWN_MS) return Kind.NONE;
        lastNotice = nowMs;
        return Kind.AUTO;
    }

    private void track(QualityProfile profile, long nowMs) {
        if (last == QualityProfile.LOW && profile != QualityProfile.LOW) leftLow = nowMs;
        else if (last == null && profile != QualityProfile.LOW) leftLow = nowMs - AUTO_REARM_MS;
        last = profile;
    }
}
