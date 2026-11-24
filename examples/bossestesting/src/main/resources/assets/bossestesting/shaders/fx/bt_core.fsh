// Energy core of a charging reactor: a volumetric swirling sphere (analytic ray-sphere, fbm), fresnel rim and
// short radial rays, layered over the scene with depth occlusion.
#include "bt_lib.glsl"

uniform vec3 BtCenter;   // camera-relative core position
uniform vec4 BtCore;     // x radius, y charge 0..1.5, z seconds, w instability 0..1
uniform vec3 BtInner;    // hot inner color
uniform vec3 BtOuter;    // outer color

void main() {
    vec2 uv = texCoord;
    vec3 base = sampleScene(uv);
    vec3 rd = viewDir(uv);
    float sceneDist = skyAt(uv) ? FAR : length(scenePosAt(uv));
    float R = BtCore.x * (0.6 + 0.4 * min(BtCore.y, 1.5));
    vec2 q = btRayPoint(rd, BtCenter);
    if (q.y <= 0.0) { fragColor = vec4(base, 1.0); return; }
    float occl = q.y - R < sceneDist ? 1.0 : 0.0;
    float x = q.x / max(R, 1e-3);
    vec3 cp = project(BtCenter);
    vec2 d = cp.z > 0.0 ? (uv - cp.xy) * vec2(OutSize.x / OutSize.y, 1.0) : vec2(1.0);
    float ang = atan(d.y, d.x);
    float t = BtCore.z;
    float swirl = fbm2(vec2(ang * 2.0 + t * 1.7, x * 3.0 - t * 2.3) + BtCore.w * 3.0);
    float body = (1.0 - smoothstep(0.0, 1.0, x)) * (0.6 + 0.8 * swirl);
    float rim = exp(-abs(x - 1.0) * 8.0) * (0.7 + 0.6 * vnoise2(vec2(ang * 8.0, t * 6.0)));
    float rays = pow(max(0.0, sin(ang * 11.0 + t * 3.0 + swirl * 4.0)), 18.0) * exp(-max(x - 1.0, 0.0) * 1.4) * step(1.0, x);
    float glow = exp(-max(x - 1.0, 0.0) * 2.2) * 0.35;
    float k = min(BtCore.y, 1.5) * occl;
    base += (BtInner * body * 1.3 + BtOuter * (rim * 1.1 + rays * 0.8 + glow)) * k;
    fragColor = vec4(base, 1.0);
}
