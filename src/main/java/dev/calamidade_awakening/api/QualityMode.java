package dev.calamidade_awakening.api;

/** Quality mode of the client (0.11.2): AUTO (default) or a profile forced with F8 or in the Atlas. Not public API. */
public enum QualityMode {
    AUTO(null), LOW(QualityProfile.LOW), BALANCED(QualityProfile.BALANCED), HIGH(QualityProfile.HIGH),
    ULTRA(QualityProfile.ULTRA);

    /** The forced profile, or null for AUTO. */
    public final QualityProfile profile;

    QualityMode(QualityProfile profile) { this.profile = profile; }

    /** F8 order: AUTO, LOW, BALANCED, HIGH, ULTRA, AUTO... */
    public QualityMode next() { return values()[(ordinal() + 1) % values().length]; }

    /** The mode that forces a profile. */
    public static QualityMode of(QualityProfile profile) { return valueOf(profile.name()); }
}
