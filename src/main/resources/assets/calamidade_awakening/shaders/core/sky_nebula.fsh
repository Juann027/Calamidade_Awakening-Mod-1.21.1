#version 150
#moj_import <calamidade_awakening:sky_objects.glsl>

in vec3 vLocal;
in vec3 vWorld;

uniform vec4 CaNebA;        // x: forma, y: raio ou meia largura (rad), z: densidade, w: escala do ruído
uniform vec4 CaNebB;        // x: distorção (warp), y: deslocamento da semente, z: oitavas (qualidade)
uniform vec4 CaNebInner;    // cor onde o gás é denso (alfa escala a densidade)
uniform vec4 CaNebOuter;    // cor nas bordas finas
uniform vec3 CaNebCenter;
uniform vec3 CaNebRight;
uniform vec3 CaNebUp;

out vec4 fragColor;

float caNebHash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.71, 0.113, 0.419));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float caNebNoise(vec3 x) {
    vec3 i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(caNebHash(i), caNebHash(i + vec3(1, 0, 0)), f.x),
                   mix(caNebHash(i + vec3(0, 1, 0)), caNebHash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(caNebHash(i + vec3(0, 0, 1)), caNebHash(i + vec3(1, 0, 1)), f.x),
                   mix(caNebHash(i + vec3(0, 1, 1)), caNebHash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float caNebFbm(vec3 p, int octaves) {
    float sum = 0.0, amp = 0.5, norm = 0.0;
    for (int k = 0; k < 6; k++) {
        if (k >= octaves) break;
        sum += amp * caNebNoise(p);
        norm += amp;
        p = p * 2.03 + vec3(1.7, 9.2, 3.4);
        amp *= 0.5;
    }
    return sum / norm;
}

void main() {
    vec3 d = normalize(vLocal);
    int octaves = int(CaNebB.z + 0.5);
    float r;
    vec3 q;
    if (CaNebA.x < 0.5) {
        // Nuvem: distância angular ao centro em raios, ruído nas coordenadas locais (giram com o roll).
        r = acos(clamp(dot(d, CaNebCenter), -1.0, 1.0)) / max(CaNebA.y, 1e-4);
        if (r > 1.15) discard;
        q = vec3(dot(d, CaNebRight), dot(d, CaNebUp), dot(d, CaNebCenter)) / max(CaNebA.y, 1e-4);
    } else {
        // Faixa: afastamento do grande círculo em meias larguras; ruído na própria direção.
        r = abs(asin(clamp(dot(d, CaNebCenter), -1.0, 1.0))) / max(CaNebA.y, 1e-4);
        if (r > 2.6) discard;
        q = d / max(CaNebA.y, 1e-4) * 0.5;
    }
    vec3 p = q * CaNebA.w * 0.5 + vec3(CaNebB.y);
    float w = caNebFbm(p, max(octaves - 1, 1));
    p += CaNebB.x * vec3(w - 0.5, 0.6 - w, w * 0.8 - 0.3) * 2.0;
    float n = caNebFbm(p, octaves);
    float edge = CaNebA.x < 0.5 ? 1.0 - smoothstep(0.25, 1.0, r + (n - 0.5) * 0.6)
                                : 1.0 - smoothstep(0.0, 2.2, r + (n - 0.5) * 0.9);
    float density = smoothstep(0.08, 0.95, edge * (0.25 + 0.75 * n));
    float t = smoothstep(0.15, 0.85, density);
    vec4 tint = mix(CaNebOuter, CaNebInner, t);
    vec4 pixel = caObjPixel(tint.rgb, clamp(density * CaNebA.z * tint.a, 0.0, 1.0), 1.0, normalize(vWorld));
    if (pixel.r + pixel.g + pixel.b + pixel.a < 1.0 / 1024.0) discard;
    fragColor = pixel;
}
