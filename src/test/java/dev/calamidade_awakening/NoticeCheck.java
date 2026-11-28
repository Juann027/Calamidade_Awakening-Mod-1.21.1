package dev.calamidade_awakening;

import dev.calamidade_awakening.api.LowQualityNotice;
import dev.calamidade_awakening.api.LowQualityNotice.Kind;
import dev.calamidade_awakening.api.QualityProfile;

/** 0.15.0 phase 5, pure; run by FrameBudgetCheck: the "Reduced Visual Quality" notice and its spam guards. */
final class NoticeCheck {
    private NoticeCheck() {}

    static void run() {
        var low = QualityProfile.LOW;
        var bal = QualityProfile.BALANCED;
        // Manual: LOW selected notifies; other profiles never; F8 cycling within a minute stays quiet.
        var n = new LowQualityNotice();
        require(n.manual(bal, 0) == Kind.NONE, "manual BALANCED: nothing");
        require(n.manual(low, 1000) == Kind.MANUAL, "manual LOW: notice");
        require(n.frame(low, false, 1100) == Kind.NONE, "LOW kept: no repeat");
        require(n.manual(bal, 2000) == Kind.NONE && n.manual(low, 30_000) == Kind.NONE, "manual cooldown");
        require(n.manual(low, 1000 + LowQualityNotice.MANUAL_COOLDOWN_MS) == Kind.MANUAL, "after the cooldown: again");
        // AUTO: entering LOW notifies once; staying in LOW does not repeat.
        n = new LowQualityNotice();
        long t = 0;
        for (; t < 5000; t += 16) require(n.frame(bal, true, t) == Kind.NONE, "AUTO above LOW: nothing");
        require(n.frame(low, true, t) == Kind.AUTO, "AUTO enters LOW: notice");
        int repeats = 0;
        for (long u = t + 16; u < t + 120_000; u += 16) if (n.frame(low, true, u) != Kind.NONE) repeats++;
        require(repeats == 0, "LOW active: no repeated notices: " + repeats);
        // Oscillation around LOW (combat): quiet until it stays above LOW for AUTO_REARM_MS and the cooldown passes.
        t += 120_000;
        int notices = 0;
        for (int k = 0; k < 200; k++, t += 4000) {
            if (n.frame(bal, true, t) != Kind.NONE) notices++;
            if (n.frame(low, true, t + 2000) != Kind.NONE) notices++;
        }
        require(notices == 0, "oscillating AUTO stays quiet: " + notices);
        t += 1000;
        n.frame(bal, true, t);
        require(n.frame(low, true, t + LowQualityNotice.AUTO_REARM_MS) == Kind.AUTO,
            "after re-arm and cooldown AUTO may notify again");
        // A manual LOW counts for the AUTO cooldown too; forced profiles never produce AUTO notices.
        n = new LowQualityNotice();
        require(n.manual(low, 0) == Kind.MANUAL, "manual");
        n.frame(bal, true, 1000);
        require(n.frame(low, true, 1000 + LowQualityNotice.AUTO_REARM_MS) == Kind.NONE, "AUTO right after a manual notice: quiet");
        n = new LowQualityNotice();
        n.frame(bal, false, 0);
        require(n.frame(low, false, 60_000) == Kind.NONE, "forced profile path: no AUTO notice");
        System.out.println("NoticeCheck PASS");
    }

    private static void require(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
