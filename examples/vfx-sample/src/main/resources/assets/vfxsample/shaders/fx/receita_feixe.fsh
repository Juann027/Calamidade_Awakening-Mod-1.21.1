// Receita 5 — feixe vertical. A RimuLib antepõe #version 150 e common.glsl.
uniform vec3 FeixeBase;    // base do feixe, relativa à câmera
uniform vec4 FeixeEstado;  // x: altura (blocos), y: meia-largura do núcleo (blocos), z: opacidade, w: segundos

#include "calamidade_fx.glsl"

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    if (FeixeEstado.x > 0.01 && FeixeEstado.y > 0.0) {
        initPixel(texCoord);                                  // common.glsl: gPix = ângulo de um pixel
        vec3 rd = viewDir(texCoord);
        float cena = length(scenePosAt(texCoord));            // o terreno na frente esconde o feixe
        vec3 topo = FeixeBase + vec3(0.0, FeixeEstado.x, 0.0);
        vec2 brilho = caSegmentGlow(vec3(0.0), rd, FeixeBase, topo, FeixeEstado.y, FeixeEstado.y * 6.0, gPix, cena);
        float ondula = 0.85 + 0.15 * sin(FeixeEstado.w * 6.0);
        vec3 nucleo = vec3(1.0, 0.97, 0.9);
        vec3 halo = vec3(1.0, 0.75, 0.35);
        color += (nucleo * brilho.x * 1.5 + halo * brilho.y * 0.55 * ondula) * FeixeEstado.z;
    }
    fragColor = vec4(color, 1.0);
}
