// Passe "lights" do motor (0.9.0): luz visual sobre o que a câmera vê, de todas as luzes do quadro de uma vez. Um
// passe por quadro, ANTES dos passes de efeito e da atmosfera (a névoa da atmosfera cobre a luz na distância),
// cobrado do orçamento HERO quando algum efeito HERO desenhado tem luzes, senão do comum. Não muda o nível de luz do
// jogo: só a imagem. A RimuLib antepõe #version 150 e common.glsl (skyAt, scenePosAt, OutSize...).
//
// As luzes chegam numa textura RGBA32F (CaLightData, 4 texels por luz, uma luz por linha). Linha i:
//   0: posição, início do segmento ou centro (relativo à câmera, blocos), alcance ou raio (blocos)
//   1: fim do segmento (igual à posição nas outras formas), expoente da queda
//   2: cor × intensidade × peso, forma (0 ponto, 1 segmento, 2 disco, 3 anel)
//   3: largura do anel ou borda do disco (blocos), alcance vertical do disco ou anel (blocos)
uniform sampler2D CaLightData;
uniform vec4 CaLightInfo;   // x: número de luzes, y: início da névoa do jogo, z: fim da névoa do jogo, w: névoa cilíndrica (1)

#include "calamidade_fx.glsl"

vec4 lightTexel(int k, int i) {
    return texelFetch(CaLightData, ivec2(k, i), 0);
}

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    // Céu: nunca iluminado.
    if (skyAt(texCoord)) {
        fragColor = vec4(color, 1.0);
        return;
    }
    vec3 p = scenePosAt(texCoord);
    vec2 px = 1.0 / OutSize;
    vec3 n = caNormalFromNeighbors(p, scenePosAt(texCoord - vec2(px.x, 0.0)), scenePosAt(texCoord + vec2(px.x, 0.0)),
                                   scenePosAt(texCoord - vec2(0.0, px.y)), scenePosAt(texCoord + vec2(0.0, px.y)));
    // A névoa do jogo já cobriu este pixel; a luz some junto (mesma curva do fog.glsl do jogo).
    float dist = CaLightInfo.w > 0.5 ? max(length(p.xz), abs(p.y)) : length(p);
    float fog = CaLightInfo.z > CaLightInfo.y ? smoothstep(CaLightInfo.y, CaLightInfo.z, dist) : 0.0;
    if (fog >= 1.0) {
        fragColor = vec4(color, 1.0);
        return;
    }
    int count = int(CaLightInfo.x + 0.5);
    vec3 light = vec3(0.0);
    for (int i = 0; i < 64; i++) {
        if (i >= count) break;
        vec4 t0 = lightTexel(0, i);
        vec4 t1 = lightTexel(1, i);
        vec4 t2 = lightTexel(2, i);
        int shape = int(t2.w + 0.5);
        float k;
        if (shape == 0) {
            k = caPointLight(p, n, t0.xyz, t0.w, t1.w);
        } else if (shape == 1) {
            k = caSegmentLight(p, n, t0.xyz, t1.xyz, t0.w, t1.w);
        } else {
            vec4 t3 = lightTexel(3, i);
            k = caGroundLight(p, n, t0.xyz, t0.w, t3.x, t3.y, shape == 3 ? 1.0 : 0.0);
        }
        light += t2.rgb * k;
    }
    fragColor = vec4(caLitColor(color, light * (1.0 - fog)), 1.0);
}
