// Volumetric shockwave: a refracting dome shell with a hot rim, a ground ring of light and a dust wall.
#include "bt_lib.glsl"

uniform vec3 BtCenter;   // camera-relative center (ground level)
uniform vec4 BtWave;     // x radius, y shell thickness, z strength 0..1, w dome height scale (0 flat ring .. 1 hemisphere)
uniform vec4 BtMisc;     // x seconds, y flash 0..1, z refraction 0..1, w dust 0..1
uniform vec3 BtColor;    // emission color

void main() {
    vec2 uv = texCoord;
    vec3 rd = viewDir(uv);
    bool sky = skyAt(uv);
    vec3 scene = scenePosAt(uv);
    float sceneDist = sky ? FAR : length(scene);
    float R = BtWave.x, w = max(BtWave.y, 0.05), k = BtWave.z;
    // The dome is a squashed sphere: scale y so the shell reaches BtWave.w * R above the ground.
    float sq = max(BtWave.w, 0.05);
    vec3 rs = normalize(vec3(rd.x, rd.y / sq, rd.z));
    float stretch = length(vec3(rd.x, rd.y / sq, rd.z));
    vec3 cs = vec3(BtCenter.x, BtCenter.y / sq, BtCenter.z);
    vec2 sh = btShell(rs, cs, R, w);
    float tHit = sh.y / stretch;
    float above = step(BtCenter.y - 0.3, rd.y * tHit);
    float shell = sh.x > 0.0 && tHit > 0.0 && tHit < sceneDist ? sh.x / (2.0 * sqrt(2.0 * R * w) + 1e-3) * above : 0.0;
    shell = clamp(shell, 0.0, 1.0);
    // Refraction toward/away from the center on screen.
    vec3 cp = project(BtCenter);
    vec2 away = cp.z > 0.0 ? normalize(uv - cp.xy + 1e-5) : vec2(0.0);
    float n = vnoise2(uv * OutSize / 40.0 + BtMisc.x * 2.0);
    vec2 offs = away * shell * k * BtMisc.z * (0.012 + 0.008 * n);
    // Ground ring and dust wall behind it.
    float ring = 0.0, dust = 0.0;
    if (!sky) {
        vec3 d = scene - BtCenter;
        float rr = length(d.xz);
        float h = abs(d.y);
        float near = 1.0 - smoothstep(1.5, 4.0, h);
        float ripple = rr - R;
        ring = exp(-abs(ripple) / (w * 0.6)) * near;
        float noise = fbm2(vec2(atan(d.z, d.x) * 6.0, rr * 0.5 - BtMisc.x * 3.0));
        dust = smoothstep(0.0, w * 2.0, -ripple) * smoothstep(-R * 0.6, 0.0, ripple) * near * (0.4 + 0.6 * noise);
        offs += away * ring * k * BtMisc.z * 0.01;
    }
    vec3 base = sampleScene(uv + offs);
    vec3 dustCol = mix(vec3(0.16, 0.12, 0.09), BtColor * 0.25, 0.35);
    base = mix(base, dustCol, clamp(dust * BtMisc.w * k * 0.55, 0.0, 0.7));
    base += BtColor * (shell * 0.55 + ring * 1.2) * k;
    if (!sky) base += BtColor * BtMisc.y * exp(-length(scene - BtCenter) / 10.0) * 1.5;
    fragColor = vec4(base, 1.0);
}
