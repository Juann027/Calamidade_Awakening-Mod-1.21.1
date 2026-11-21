package dev.calamidade_awakening.api;

/**
 * Upper limits per frame, not a claim about measured FPS.
 *
 * <p>{@code effects}: instances drawn; {@code marks}: common addon pass budget (card shaders, post passes and the
 * beams/lights passes of NORMAL effects); {@code heroPasses}: separate pass budget of HERO effects (0.9.0);
 * {@code steps}: raymarch steps of the engine field; {@code vertices}: world mesh vertices; {@code beams} and
 * {@code lights}: global caps of the beams and lights passes (0.9.0).
 */
public enum Quality {
    LOW(3, 1, 1, 8, 12_000, 24, 24, 16, 8),
    BALANCED(6, 2, 2, 14, 48_000, 48, 48, 48, 24),
    HIGH(12, 4, 3, 22, 96_000, 80, 80, 128, 64);

    public final int effects;
    public final int marks;
    /** Pass budget reserved for HERO effects, separate from {@link #marks} (0.9.0). */
    public final int heroPasses;
    public final int steps;
    public final int vertices, segments, particles;
    /** Most beams drawn per frame by the engine's beams pass (0.9.0). */
    public final int beams;
    /** Most lights drawn per frame by the engine's lights pass (0.9.0). */
    public final int lights;
    Quality(int effects, int marks, int heroPasses, int steps, int vertices, int segments, int particles,
            int beams, int lights) {
        this.effects = effects; this.marks = marks; this.heroPasses = heroPasses; this.steps = steps;
        this.vertices = vertices; this.segments = segments; this.particles = particles;
        this.beams = beams; this.lights = lights;
    }
    public Quality next() { return values()[(ordinal() + 1) % values().length]; }
}
