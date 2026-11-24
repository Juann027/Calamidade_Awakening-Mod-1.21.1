// Mechanical crisis: chromatic split, tearing scan bands (motion effects only), electric arcs from the reactor and
// a pulsing alarm vignette.
#include "bt_lib.glsl"

uniform vec3 BtCenter;   // camera-relative reactor position
uniform vec4 BtGlitch;   // x glitch 0..1, y arcs 0..1, z seconds, w alarm 0..1
uniform vec4 BtFlags;    // x motion allowed 0/1, y flashes allowed 0/1, z arc count (1..6), w seed
uniform vec3 BtArc;      // arc color
uniform vec3 BtAlarm;    // alarm color

void main() {
    vec2 uv = texCoord;
    float g = BtGlitch.x;
    float t = BtGlitch.z;
    float band = floor(uv.y * 38.0);
    float tear = BtFlags.x * g * step(0.86, hash12(vec2(band, floor(t * 14.0)))) * (hash12(vec2(band, 3.0)) - 0.5) * 0.06;
    vec2 u2 = uv + vec2(tear, 0.0);
    float split = g * 0.006;
    vec3 col = vec3(sampleScene(u2 + vec2(split, 0.0)).r, sampleScene(u2).g, sampleScene(u2 - vec2(split, 0.0)).b);
    // Arcs from the projected reactor toward the screen edges, flickering (steady when flashes are hidden).
    vec3 cp = project(BtCenter);
    if (cp.z > 0.0 && BtGlitch.y > 0.001) {
        vec2 aspect = vec2(OutSize.x / OutSize.y, 1.0);
        float flick = BtFlags.y > 0.5 ? floor(t * 18.0) : 0.0;
        float arcs = 0.0;
        int count = int(clamp(BtFlags.z, 1.0, 6.0));
        for (int i = 0; i < 6; i++) {
            if (i >= count) break;
            float s = float(i) + BtFlags.w;
            vec2 h = hash22(vec2(s, flick));
            float a = h.x * TAU;
            float len = (0.08 + 0.22 * h.y) / max(cp.z * 0.05, 1.0);
            vec2 end = cp.xy + vec2(cos(a), sin(a)) * len / aspect;
            float dist = btBolt(uv * aspect, cp.xy * aspect, end * aspect, s * 7.0 + flick, 0.45);
            arcs += 0.0018 / (dist + 0.0016);
        }
        col += BtArc * clamp(arcs, 0.0, 3.0) * BtGlitch.y;
    }
    vec2 v = uv - 0.5;
    float vig = smoothstep(0.25, 0.75, length(v * vec2(1.2, 1.0)));
    float beat = BtFlags.y > 0.5 ? (0.5 + 0.5 * sin(t * 9.0)) : 0.6;
    col = mix(col, col * 0.6 + BtAlarm * 0.5, vig * BtGlitch.w * beat);
    fragColor = vec4(col, 1.0);
}
