// Custom impact frame: inked edges, three-tone posterization in the phase colors, radial speed lines from the
// impact point and an optional negative flip (only when flashes are allowed).
#include "bt_lib.glsl"

uniform vec3 BtCenter;   // camera-relative impact point
uniform vec4 BtFrame;    // x strength 0..1, y invert 0/1, z seconds, w speed lines 0..1
uniform vec3 BtInk;      // dark tone
uniform vec3 BtMid;      // middle tone (phase color)
uniform vec3 BtPaper;    // light tone

void main() {
    vec2 uv = texCoord;
    vec3 src = sampleScene(uv);
    float k = BtFrame.x;
    if (k <= 0.001) { fragColor = vec4(src, 1.0); return; }
    float l = lum(src);
    float edge = btEdges(uv);
    vec3 cp = project(BtCenter);
    vec2 c = cp.z > 0.0 ? cp.xy : vec2(0.5);
    vec2 d = (uv - c) * vec2(OutSize.x / OutSize.y, 1.0);
    float ang = atan(d.y, d.x);
    float rad = length(d);
    float lines = step(0.82, hash12(vec2(floor(ang * 90.0), floor(BtFrame.z * 20.0)))) * smoothstep(0.12, 0.6, rad) * BtFrame.w;
    vec3 tone = l < 0.22 ? BtInk : (l < 0.62 ? BtMid : BtPaper);
    tone = mix(tone, BtInk, edge);
    tone = mix(tone, BtPaper, lines * 0.85);
    if (BtFrame.y > 0.5) tone = vec3(1.0) - tone + BtMid * 0.15;
    fragColor = vec4(mix(src, tone, k), 1.0);
}
