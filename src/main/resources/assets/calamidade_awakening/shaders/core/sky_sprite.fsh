#version 150
#moj_import <calamidade_awakening:sky_objects.glsl>

in vec2 vCorner;
in vec3 vWorld;
flat in vec4 vObjA;
flat in vec4 vObjB;
flat in vec4 vTint;
flat in vec4 vWeights;
flat in vec4 vRect0;
flat in vec4 vRect1;
flat in vec4 vRect2;
flat in vec4 vRect3;

uniform sampler2D Sampler0;  // textura do sprite, alfa pré-multiplicado na carga (SkyTextures)

out vec4 fragColor;

// Um retângulo da textura no canto vCorner, preso meio texel do nível amostrado para dentro: os mipmaps são feitos por
// quadro (SkyTextures: caixa 2 × 2 só até o nível em que todo quadro continua inteiro, vTint.w níveis), então nenhum
// texel lido pertence ao quadro vizinho em tamanho nenhum, sem margem. Derivadas do quadrilátero inteiro (sem costura).
vec4 caSpriteTexel(vec4 r, vec2 dx, vec2 dy, vec2 size) {
    vec2 span = r.zw - r.xy;
    vec2 gx = dx * span, gy = dy * span;
    float rho = max(length(gx * size), length(gy * size));
    float level = min(max(ceil(log2(max(rho, 1e-8))), 0.0), vTint.w);
    vec2 inset = 0.5 * exp2(level) / size;
    vec2 lo = min(r.xy, r.zw) + inset, hi = max(r.xy, r.zw) - inset;
    vec2 uv = clamp(r.xy + span * vCorner, lo, max(lo, hi));
    return textureGrad(Sampler0, uv, gx, gy);
}

void main() {
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 dx = dFdx(vCorner), dy = dFdy(vCorner);
    // Soma ponderada em alfa pré-multiplicado: a mistura de quadros e de vistas não escurece as bordas.
    vec4 acc = vWeights.x * caSpriteTexel(vRect0, dx, dy, size);
    if (vWeights.y > 0.0) acc += vWeights.y * caSpriteTexel(vRect1, dx, dy, size);
    if (vWeights.z > 0.0) acc += vWeights.z * caSpriteTexel(vRect2, dx, dy, size);
    if (vWeights.w > 0.0) acc += vWeights.w * caSpriteTexel(vRect3, dx, dy, size);
    float alpha = clamp(acc.a, 0.0, 1.0);
    vec3 color = alpha > 1e-5 ? acc.rgb / alpha : vec3(0.0);
    vec4 pixel = caObjPixelWith(vObjA, vObjB, color * vTint.rgb, alpha, 1.0, normalize(vWorld));
    if (pixel.r + pixel.g + pixel.b + pixel.a < 1.0 / 1024.0) discard;
    fragColor = pixel;
}
