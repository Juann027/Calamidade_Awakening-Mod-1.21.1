// Thermal distortion around a capsule (a beam, a hot trench, a reactor): rising shimmer, a faint warm glow.
#include "bt_lib.glsl"

uniform vec3 BtA;        // capsule start, camera-relative
uniform vec3 BtB;        // capsule end
uniform vec4 BtHeat;     // x radius, y strength 0..1, z seconds, w glow 0..1
uniform vec3 BtTint;     // glow color

void main() {
    vec2 uv = texCoord;
    vec3 rd = viewDir(uv);
    float sceneDist = skyAt(uv) ? FAR : length(scenePosAt(uv));
    vec3 q = btRaySeg(rd, BtA, BtB);
    float r = max(BtHeat.x, 0.05);
    float inside = q.y < sceneDist + r ? 1.0 - smoothstep(0.0, r, q.x) : 0.0;
    vec3 base;
    if (inside <= 0.0 || BtHeat.y <= 0.0) { fragColor = vec4(sampleScene(uv), 1.0); return; }
    vec2 g = uv * OutSize / 18.0;
    float t = BtHeat.z;
    vec2 n = vec2(vnoise2(g + vec2(0.0, t * 2.4)), vnoise2(g * 1.3 + vec2(t * 1.1, t * 2.9))) - 0.5;
    float dist = max(q.y, 1.0);
    vec2 offs = n * inside * BtHeat.y * (0.018 / (1.0 + dist * 0.03));
    base = sampleScene(uv + offs);
    base += BtTint * inside * inside * BtHeat.w * 0.35;
    fragColor = vec4(base, 1.0);
}
