// Telegraph decal projected on the ground through the depth buffer: danger circle or attack lane.
#include "bt_lib.glsl"

uniform vec3 BtCenter;   // camera-relative center of the decal (blocks)
uniform vec4 BtShape;    // x kind (0 circle, 1 lane), y radius (circle) or half width (lane), z lane length, w yaw (radians)
uniform vec4 BtState;    // x fill progress 0..1, y intensity 0..1, z seconds, w urgency 0..1 (faster pulses)
uniform vec3 BtColorA;   // main color
uniform vec3 BtColorB;   // accent color

void main() {
    vec3 base = sampleScene(texCoord);
    if (skyAt(texCoord) || BtState.y <= 0.001) { fragColor = vec4(base, 1.0); return; }
    vec3 p = scenePosAt(texCoord) - BtCenter;
    float band = 1.0 - smoothstep(1.2, 3.2, abs(p.y));
    if (band <= 0.0) { fragColor = vec4(base, 1.0); return; }
    float cy = cos(BtShape.w), sy = sin(BtShape.w);
    // Local frame: z forward along the yaw (Minecraft yaw 0 = south = +z), x to the left.
    vec2 q = vec2(p.x * cy + p.z * sy, -p.x * sy + p.z * cy);
    float t = BtState.z * (1.0 + 2.0 * BtState.w);
    float fill = BtState.x;
    float mask = 0.0, edge = 0.0, inside = 0.0, stripes = 0.0;
    float pix = max(length(p + BtCenter) * 0.002, 0.03);
    if (BtShape.x < 0.5) {
        float R = BtShape.y;
        float r = length(q);
        inside = 1.0 - smoothstep(R - pix, R + pix, r);
        float ang = atan(q.y, q.x);
        // Outer ring with rotating segments, an inner ring that closes in as the strike approaches.
        float seg = step(0.35, fract(ang / TAU * 24.0 + t * 0.25));
        edge = exp(-abs(r - R) / (0.12 + pix)) * (0.55 + 0.45 * seg);
        float closing = R * (1.0 - fill);
        edge += 0.8 * exp(-abs(r - closing) / (0.1 + pix)) * step(0.02, fill);
        float sweep = step(r, R * fill);
        stripes = step(0.5, fract((q.x + q.y) * 0.45 - t * 1.6)) * inside;
        float rings = 0.5 + 0.5 * sin(r * 2.2 - t * 6.0);
        mask = inside * (0.18 + 0.35 * sweep + 0.12 * rings);
        // Chevrons pointing to the center at 8 directions.
        float chev = abs(fract(ang / TAU * 8.0) - 0.5);
        float arrow = 1.0 - smoothstep(0.0, 0.06, abs(fract(r * 0.35 + chev * 1.2 + t * 0.8) - 0.5) - 0.38);
        mask += 0.25 * arrow * inside * step(R * 0.25, r);
    } else {
        float W = BtShape.y, L = BtShape.z;
        float lx = abs(q.x);
        inside = (1.0 - smoothstep(W - pix, W + pix, lx)) * step(0.0, q.y) * (1.0 - smoothstep(L - pix, L + pix, q.y));
        edge = exp(-abs(lx - W) / (0.1 + pix)) * step(0.0, q.y) * step(q.y, L);
        float sweep = step(q.y, L * fill);
        stripes = step(0.5, fract(q.y * 0.5 - lx * 0.35 - t * 2.2)) * inside;
        mask = inside * (0.2 + 0.4 * sweep);
        edge += 0.9 * exp(-abs(q.y - L * fill) / (0.15 + pix)) * inside;
    }
    float pulse = 0.75 + 0.25 * sin(t * (6.0 + 10.0 * BtState.w));
    float k = BtState.y * band * pulse;
    base *= 1.0 - 0.25 * inside * k;
    base += BtColorA * (mask + 0.5 * stripes * 0.45) * k;
    base += mix(BtColorA, BtColorB, 0.5) * edge * k * 1.4;
    fragColor = vec4(base, 1.0);
}
