package dev.calamidade_awakening.client.geometry;

public record Color(float r, float g, float b, float a) {
    public Color fade(double opacity) {
        return new Color(r, g, b, (float) (a * Math.clamp(opacity, 0, 1)));
    }
    public Color mix(Color other, double t) {
        float p = (float) Math.clamp(t, 0, 1);
        return new Color(r + (other.r - r) * p, g + (other.g - g) * p,
            b + (other.b - b) * p, a + (other.a - a) * p);
    }
}
