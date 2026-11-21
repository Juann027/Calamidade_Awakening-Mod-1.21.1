// Receita 2 — cubo de grade gigante (raio-caixa). A RimuLib antepõe #version 150 e common.glsl.
uniform vec3 CuboCentro;  // centro relativo à câmera (post.center())
uniform vec4 CuboEstado;  // x: meia-aresta (blocos), y: trilha "grade" (0..1), z: opacidade, w: idade real (ticks)

#include "calamidade_fx.glsl"

// Luz de um ponto p na superfície do cubo: grade de 2 blocos, arestas e uma faixa de varredura vertical.
float cuboFace(vec3 p, vec3 bmin, vec3 bmax, float meia) {
    vec3 n = caBoxNormal(p, bmin, bmax);
    vec3 local = p - CuboCentro;                       // grade presa ao cubo, não à câmera
    float px = gPix * length(p);                       // tamanho do pixel em blocos nesta distância
    float grade = caWorldGrid(local + meia, n, 2.0, 0.04, px);
    vec3 q = meia - abs(local) + abs(n) * 1e6;         // distância às arestas pelos dois eixos da face
    float aresta = 1.0 - smoothstep(0.08, 0.08 + 2.0 * px, min(min(q.x, q.y), q.z));
    float altura = mix(-meia, meia, fract(CuboEstado.w / 60.0));
    float varredura = exp(-abs(local.y - altura) * 2.0);
    return grade * 0.8 + aresta * 1.4 + varredura * 0.25;
}

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float meia = CuboEstado.x;
    if (CuboEstado.y > 0.001 && meia > 0.01) {
        initPixel(texCoord);                           // common.glsl: preenche gPix (ângulo de um pixel)
        vec3 rd = viewDir(texCoord);
        float cena = length(scenePosAt(texCoord));
        vec3 bmin = CuboCentro - meia, bmax = CuboCentro + meia;
        vec2 t = caRayBox(vec3(0.0), rd, bmin, bmax);
        if (t.x >= 0.0) {
            float luz = 0.0;
            if (t.x > 0.0 && t.x < cena) luz += cuboFace(rd * t.x, bmin, bmax, meia);        // face da frente
            if (t.y < cena) luz += 0.45 * cuboFace(rd * t.y, bmin, bmax, meia);              // face do fundo
            float volume = clamp((min(t.y, cena) - t.x) / (2.0 * meia), 0.0, 1.0);           // véu leve dentro
            color += vec3(0.25, 0.95, 1.0) * (luz + volume * 0.06) * CuboEstado.y * CuboEstado.z;
        }
    }
    fragColor = vec4(color, 1.0);
}
