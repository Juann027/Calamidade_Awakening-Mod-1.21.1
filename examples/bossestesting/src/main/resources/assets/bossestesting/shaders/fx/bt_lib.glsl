// BossesTesting: shared helpers of the addon's post passes (common.glsl of RimuLib is already prepended).
// All positions are camera-relative blocks (the space of scenePosAt); the camera is at the origin.

// Distance from the view ray (origin 0, unit rd) to point c, and the ray parameter of the closest approach.
vec2 btRayPoint(vec3 rd, vec3 c) {
    float t = dot(c, rd);
    return vec2(length(c - rd * max(t, 0.0)), t);
}

// Closest approach between the view ray and segment a-b: x = distance, y = ray t, z = segment s (0..1).
vec3 btRaySeg(vec3 rd, vec3 a, vec3 b) {
    vec3 ab = b - a;
    float abab = dot(ab, ab);
    float abrd = dot(ab, rd);
    float aab = dot(a, ab);
    float ard = dot(a, rd);
    float den = abab - abrd * abrd;
    float s = den > 1e-5 ? clamp((ard * abrd - aab) / den, 0.0, 1.0) : 0.0;
    vec3 p = a + ab * s;
    float t = max(dot(p, rd), 0.0);
    return vec3(length(p - rd * t), t, s);
}

// Path length of the ray through a spherical shell (outer radius r, thickness w) centered at c, front half only
// when `front` is 1 (useful for a dome seen from outside). Returns (length, t of the first surface).
vec2 btShell(vec3 rd, vec3 c, float r, float w) {
    vec2 q = btRayPoint(rd, c);
    float d = q.x;
    if (d > r) return vec2(0.0, -1.0);
    float outer = sqrt(max(r * r - d * d, 0.0));
    float ri = max(r - w, 0.0);
    float inner = sqrt(max(ri * ri - d * d, 0.0));
    return vec2(2.0 * (outer - inner), q.y - outer);
}

float btLinearDepth(vec2 uv) {
    return skyAt(uv) ? FAR : length(scenePosAt(uv));
}

// Ink edges from depth discontinuities (relative), 0..1.
float btEdges(vec2 uv) {
    vec2 px = 1.0 / OutSize;
    float c = btLinearDepth(uv);
    float l = btLinearDepth(uv - vec2(px.x, 0.0));
    float r = btLinearDepth(uv + vec2(px.x, 0.0));
    float d = btLinearDepth(uv - vec2(0.0, px.y));
    float u = btLinearDepth(uv + vec2(0.0, px.y));
    float g = abs(l + r - 2.0 * c) + abs(d + u - 2.0 * c);
    return clamp(g / (c * 0.06 + 0.2), 0.0, 1.0);
}

// Screen-space lightning: distance from uv to a jagged bolt from a to b (uv units), seeded.
float btBolt(vec2 uv, vec2 a, vec2 b, float seed, float jag) {
    vec2 ab = b - a;
    float len = length(ab);
    if (len < 1e-4) return 1e3;
    vec2 dir = ab / len, nrm = vec2(-dir.y, dir.x);
    vec2 rel = uv - a;
    float s = clamp(dot(rel, dir) / len, 0.0, 1.0);
    float k = s * 9.0;
    float i0 = floor(k), f = fract(k);
    float o0 = (hash12(vec2(i0, seed)) - 0.5), o1 = (hash12(vec2(i0 + 1.0, seed)) - 0.5);
    float env = sin(s * PI);
    float off = mix(o0, o1, f) * jag * env * len;
    return abs(dot(rel, nrm) - off) + max(0.0, abs(dot(rel, dir) - s * len) - 1e-3);
}
