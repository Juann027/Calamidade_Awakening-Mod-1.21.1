package dev.calamidade_awakening.api;

/** Upper limits, not a claim about measured FPS. */
public enum Quality {
    LOW(3, 1, 8, 12_000, 24, 24),
    BALANCED(6, 2, 14, 48_000, 48, 48),
    HIGH(12, 4, 22, 96_000, 80, 80);

    public final int effects;
    public final int marks;
    public final int steps;
    public final int vertices, segments, particles;
    Quality(int effects, int marks, int steps, int vertices, int segments, int particles) {
        this.effects = effects; this.marks = marks; this.steps = steps;
        this.vertices = vertices; this.segments = segments; this.particles = particles;
    }
    public Quality next() { return values()[(ordinal() + 1) % values().length]; }
}
