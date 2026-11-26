// Passe "lights" do motor (0.9.0): luz visual sobre o que a câmera vê, de todas as luzes do quadro de uma vez. Um
// passe por quadro, ANTES dos passes de efeito e da atmosfera (a névoa da atmosfera cobre a luz na distância),
// cobrado do orçamento HERO quando algum efeito HERO desenhado tem luzes, senão do comum. Não muda o nível de luz do
// jogo: só a imagem. A RimuLib antepõe #version 150 e common.glsl (skyAt, scenePosAt, OutSize...).
//
// As luzes chegam numa textura RGBA32F (CaLightData, 12 texels por luz desde a 0.15.0, uma luz por linha). Linha i:
//   0: posição, início do segmento ou centro (relativo à câmera, blocos), alcance ou raio (blocos)
//   1: fim do segmento (ponto, disco, anel: a posição; holofote e projetor: direção unitária), expoente da queda
//   2: cor × intensidade × peso (× primeira cor do gradiente; OVERRIDE: sem o peso), código (bits 0-2 forma: 0 ponto,
//      1 segmento, 2 disco, 3 anel, 4 holofote, 5 projetor; 3-4 curva; 5-7 composição 0 ADD, 1 SOFT, 2 SUBTRACT,
//      3 EMISSIVE, 4 OVERRIDE; 8 cookie amostrado, 9 cookie colorido, 10 repetir, 11 tem cookie, 12 atlas grande,
//      13-14 filtro 0 suave, 1 nítido, 2 pixelado; 15-17 paradas do gradiente de distância)
//   3: largura do anel ou borda do disco, alcance vertical do disco ou anel, tan(meia abertura) do holofote, suavidade
//   4: direita da imagem (unitária), meia largura do projetor       (só luzes orientadas, com gradiente ou modo novo)
//   5: cima da imagem (unitária), meia altura do projetor
//   6: transformação do cookie: cos × escala, sen × escala, deslocamento u, deslocamento v
//   7: retângulo do quadro atual no atlas (u0, v0, du, dv; origem no canto superior esquerdo)
//   8: retângulo do quadro seguinte
//   9-11: paradas 2 a 4 do gradiente (cor × ganho); w: mistura dos quadros, nível máximo de mipmap, nitidez
//   12: força da composição (OVERRIDE: × peso da instância), detalhe da superfície, contraste da máscara, viés de mip
// Código < 4 (formas da 0.9 com curva e composição padrão): o mesmo caminho da 0.14, sem textura nem texels extras.
// Ordem da composição de um pixel, fixa e independente da ordem das luzes: cena iluminada por ADD, SOFT e a parte não
// emissiva de EMISSIVE; escurecida por SUBTRACT; substituída por OVERRIDE (cobertura 1 - prod(1 - w), alvo = média
// ponderada); por fim a parte emissiva somada (o excesso acima do branco transborda para os outros canais).
uniform sampler2D CaLightData;
uniform sampler2D CaCookieAtlas;
uniform sampler2D CaCookieLarge;
uniform vec4 CaLightInfo;   // x: número de luzes, y: início da névoa do jogo, z: fim da névoa do jogo, w: névoa cilíndrica (1)
uniform vec4 CaCookieInfo;  // x: lado dos atlas de cookies (texels), y: 1 com o atlas padrão, z: 1 com o atlas grande

#include "calamidade_fx.glsl"

vec4 lightTexel(int k, int i) {
    return texelFetch(CaLightData, ivec2(k, i), 0);
}

// Curvas de atenuação (0.15.0); x = distância / alcance.
float attenuate(float x, float falloff, int curve) {
    if (x >= 1.0) return 0.0;
    x = max(x, 0.0);
    if (curve == 0) return pow(1.0 - x, max(falloff, 0.01));
    float w = (1.0 - x * x);
    w *= w;
    if (curve == 1) return w;
    if (curve == 2) {
        float k = 2.0 * falloff * x;
        return w / (1.0 + k * k);
    }
    return 1.0 - smoothstep(0.8, 1.0, x);
}

// Envolvimento de 0,3 do caPointLight: faces de lado ~23%, de costas nada.
float wrapNdl(vec3 n, vec3 toLight) {
    return max((dot(n, toLight) + 0.3) / 1.3, 0.0);
}

// Borda suave de uma coordenada centrada (|s| <= 0,5 dentro); soft 0..1 da meia largura.
float edge(float s, float soft) {
    float a = abs(s);
    return 1.0 - smoothstep(0.5 - 0.5 * max(soft, 1e-3), 0.5, a);
}

vec3 ramp(vec3 c0, vec3 c1, vec3 c2, vec3 c3, int n, float t) {
    float x = clamp(t, 0.0, 1.0) * float(n - 1);
    if (x < 1.0) return mix(c0, c1, x);
    if (x < 2.0) return mix(c1, c2, x - 1.0);
    return mix(c2, c3, x - 2.0);
}

vec4 atlasGrad(bool large, vec2 a, vec2 ga, vec2 gb) {
    return large ? textureGrad(CaCookieLarge, a, ga, gb) : textureGrad(CaCookieAtlas, a, ga, gb);
}

vec4 atlasLod0(bool large, vec2 a) {
    return large ? textureLod(CaCookieLarge, a, 0.0) : textureLod(CaCookieAtlas, a, 0.0);
}

// Amostra o cookie em st (centrado: a imagem inteira em -0,5..0,5), com as derivadas de st por pixel em gx e gy.
// filter: 0 suave (trilinear), 1 nítido (núcleo duro por texel de perto, mipmaps de longe), 2 pixelado (texel mais
// próximo). sharp 0..1, bias em níveis; o nível nunca passa de maxLod (quadros de uma folha nunca se misturam) e a
// leitura fica meio texel do nível para dentro do retângulo (nada do vizinho do atlas).
vec4 cookieSample(vec2 st, vec2 gx, vec2 gy, vec4 xf, vec4 rect, bool repeatWrap, int filtering, float maxLod, float sharp,
                  float bias, bool large) {
    mat2 m = mat2(xf.x, xf.y, -xf.y, xf.x);
    vec2 uv = m * st + 0.5 + xf.zw;
    vec2 dx = m * gx, dy = m * gy;
    if (!repeatWrap && (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0)) return vec4(0.0);
    vec2 f = repeatWrap ? fract(uv) : uv;
    // y da imagem para cima; linhas da textura de cima para baixo.
    f.y = 1.0 - f.y;
    dx.y = -dx.y;
    dy.y = -dy.y;
    vec2 a = rect.xy + f * rect.zw;
    float side = CaCookieInfo.x;
    if (filtering == 2) {
        a = (floor(a * side) + 0.5) / side;
        return atlasLod0(large, a);
    }
    vec2 ga = dx * rect.zw, gb = dy * rect.zw;
    float len0 = max(length(ga), length(gb)) * side;
    if (filtering == 1 && len0 < 1.0) {
        // Nítido de perto: cada texel tem núcleo duro e só a borda é misturada, numa faixa de ~1 pixel da tela (sharp 1)
        // a ~4 pixels (sharp 0). Sem degraus e sem cintilação: a faixa acompanha o tamanho do pixel.
        vec2 t = a * side - 0.5;
        vec2 base = floor(t);
        vec2 fr = t - base;
        float w = clamp(len0 * mix(4.0, 1.0, sharp), 1e-3, 0.5);
        fr = clamp((fr - 0.5) / (2.0 * w) + 0.5, 0.0, 1.0);
        a = (base + fr + 0.5) / side;
        a = clamp(a, rect.xy + 0.5 / side, rect.xy + rect.zw - 0.5 / side);
        return atlasLod0(large, a);
    }
    float scale = exp2(clamp(bias - 0.5 * sharp, -1.0, 4.0));
    ga *= scale;
    gb *= scale;
    float len = len0 * scale;
    float lim = exp2(maxLod);
    if (len > lim) {
        float k = lim / len;
        ga *= k;
        gb *= k;
        len = lim;
    }
    // Meio texel do nível lido, para o filtro não pegar o vizinho do atlas.
    float inset = 0.5 * max(len, 1.0) / side;
    a = clamp(a, rect.xy + inset, rect.xy + rect.zw - inset);
    return atlasGrad(large, a, ga, gb);
}

// Contraste da máscara em volta de meia cobertura que preserva 0 e 1 (transparente continua sem efeito).
float contrastCurve(float x, float c) {
    if (abs(c - 1.0) < 1e-3) return x;
    float a = pow(clamp(x, 0.0, 1.0), c), b = pow(clamp(1.0 - x, 0.0, 1.0), c);
    return a / max(a + b, 1e-6);
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
    vec3 pl = scenePosAt(texCoord - vec2(px.x, 0.0)), pr = scenePosAt(texCoord + vec2(px.x, 0.0));
    vec3 pd = scenePosAt(texCoord - vec2(0.0, px.y)), pu = scenePosAt(texCoord + vec2(0.0, px.y));
    vec3 n = caNormalFromNeighbors(p, pl, pr, pd, pu);
    // A névoa do jogo já cobriu este pixel; a luz some junto (mesma curva do fog.glsl do jogo).
    float dist = CaLightInfo.w > 0.5 ? max(length(p.xz), abs(p.y)) : length(p);
    float fog = CaLightInfo.z > CaLightInfo.y ? smoothstep(CaLightInfo.y, CaLightInfo.z, dist) : 0.0;
    if (fog >= 1.0) {
        fragColor = vec4(color, 1.0);
        return;
    }
    int count = int(CaLightInfo.x + 0.5);
    vec3 light = vec3(0.0), soft = vec3(0.0), dark = vec3(0.0), emit = vec3(0.0), overSum = vec3(0.0);
    float overW = 0.0, overKeep = 1.0;
    bool extended = false;
    // Sombreamento da superfície (textura sob a luz), usado só por DETAIL de EMISSIVE e OVERRIDE.
    // Calculado na primeira luz estendida: o caminho da 0.9 não paga o pow.
    float shade = 1.0;
    for (int i = 0; i < 64; i++) {
        if (i >= count) break;
        vec4 t0 = lightTexel(0, i);
        vec4 t1 = lightTexel(1, i);
        vec4 t2 = lightTexel(2, i);
        int code = int(t2.w + 0.5);
        if (code < 4) {
            // Caminho da 0.9 a 0.14: ponto, segmento, disco e anel com a curva e a composição padrão.
            float k;
            if (code == 0) {
                k = caPointLight(p, n, t0.xyz, t0.w, t1.w);
            } else if (code == 1) {
                k = caSegmentLight(p, n, t0.xyz, t1.xyz, t0.w, t1.w);
            } else {
                vec4 t3 = lightTexel(3, i);
                k = caGroundLight(p, n, t0.xyz, t0.w, t3.x, t3.y, code == 3 ? 1.0 : 0.0);
            }
            light += t2.rgb * k;
            continue;
        }
        if (!extended) {
            extended = true;
            shade = clamp(dot(pow(max(color, vec3(0.0)), vec3(0.4)), vec3(0.2126, 0.7152, 0.0722)) * 1.6, 0.15, 1.6);
        }
        // Vizinhos mais próximos (para as derivadas dos cookies, sem pular bordas de profundidade).
        vec3 ddx = (dot(pr - p, pr - p) < dot(p - pl, p - pl)) ? pr - p : p - pl;
        vec3 ddy = (dot(pu - p, pu - p) < dot(p - pd, p - pd)) ? pu - p : p - pd;
        int shape = code & 7;
        int curve = (code >> 3) & 3;
        int blend = (code >> 5) & 7;
        bool large = ((code >> 12) & 1) == 1 && CaCookieInfo.z > 0.5;
        bool sampled = ((code >> 8) & 1) == 1 && (large || CaCookieInfo.y > 0.5);
        bool colored = ((code >> 9) & 1) == 1;
        bool repeatWrap = ((code >> 10) & 1) == 1;
        bool square = ((code >> 11) & 1) == 1;
        int filtering = (code >> 13) & 3;
        int stops = (code >> 15) & 7;
        vec4 t3 = lightTexel(3, i);
        float radius = t0.w;
        float k = 0.0;
        float x = 0.0;          // posição no gradiente de distância
        vec2 st = vec2(0.0), gx = vec2(0.0), gy = vec2(0.0);
        bool projected = false;
        if (shape <= 1) {
            vec3 l = t0.xyz;
            if (shape == 1) {
                vec3 ba = t1.xyz - t0.xyz;
                float baba = dot(ba, ba);
                l = t0.xyz + ba * (baba > 1e-12 ? clamp(dot(p - t0.xyz, ba) / baba, 0.0, 1.0) : 0.0);
            }
            vec3 d = l - p;
            float len = length(d);
            x = radius > 0.0 ? len / radius : 1.0;
            k = attenuate(x, t1.w, curve) * (len > 1e-4 ? wrapNdl(n, d / len) : 1.0);
        } else if (shape <= 3) {
            k = caGroundLight(p, n, t0.xyz, radius, t3.x, t3.y, shape == 3 ? 1.0 : 0.0);
            vec3 q = p - t0.xyz;
            x = radius > 0.0 ? length(q.xz) / radius : 1.0;
            if (shape == 2 && square) {
                vec4 t4 = lightTexel(4, i), t5 = lightTexel(5, i);
                float s = 1.0 / max(2.0 * radius, 1e-3);
                st = vec2(dot(q, t4.xyz), dot(q, t5.xyz)) * s;
                gx = vec2(dot(ddx, t4.xyz), dot(ddx, t5.xyz)) * s;
                gy = vec2(dot(ddy, t4.xyz), dot(ddy, t5.xyz)) * s;
                projected = true;
            }
        } else {
            vec3 dir = t1.xyz;
            vec4 t4 = lightTexel(4, i), t5 = lightTexel(5, i);
            vec3 q = p - t0.xyz;
            float z = dot(q, dir);
            if (z <= 0.0) continue;
            if (shape == 4) {
                // Holofote: cone (redondo) ou pirâmide de base quadrada (com cookie).
                float len = length(q);
                x = radius > 0.0 ? len / radius : 1.0;
                float tanHalf = max(t3.z, 1e-3);
                vec2 tq = vec2(dot(q, t4.xyz), dot(q, t5.xyz)) / (z * 2.0 * tanHalf);
                float mask;
                if (square) {
                    mask = edge(tq.x, t3.w) * edge(tq.y, t3.w);
                    st = tq;
                    vec3 qx = q + ddx, qy = q + ddy;
                    float zx = max(dot(qx, dir), 1e-4), zy = max(dot(qy, dir), 1e-4);
                    gx = vec2(dot(qx, t4.xyz), dot(qx, t5.xyz)) / (zx * 2.0 * tanHalf) - tq;
                    gy = vec2(dot(qy, t4.xyz), dot(qy, t5.xyz)) / (zy * 2.0 * tanHalf) - tq;
                    projected = true;
                } else {
                    float rr = length(tq) * 2.0;
                    mask = 1.0 - smoothstep(1.0 - max(t3.w, 1e-3), 1.0, rr);
                }
                k = attenuate(x, t1.w, curve) * mask * wrapNdl(n, -q / max(len, 1e-4));
            } else {
                // Projetor planar: caixa de raios paralelos.
                x = radius > 0.0 ? z / radius : 1.0;
                vec2 half_ = max(vec2(t4.w, t5.w), vec2(1e-3));
                vec2 tq = vec2(dot(q, t4.xyz), dot(q, t5.xyz)) / (2.0 * half_);
                float mask = edge(tq.x, t3.w) * edge(tq.y, t3.w);
                st = tq;
                gx = vec2(dot(ddx, t4.xyz), dot(ddx, t5.xyz)) / (2.0 * half_);
                gy = vec2(dot(ddy, t4.xyz), dot(ddy, t5.xyz)) / (2.0 * half_);
                projected = square;
                k = attenuate(x, t1.w, curve) * mask * wrapNdl(n, -dir);
            }
        }
        if (k <= 0.0) continue;
        vec3 c = t2.rgb;
        if (stops >= 2) c = ramp(t2.rgb, lightTexel(9, i).rgb, lightTexel(10, i).rgb, lightTexel(11, i).rgb, stops, x);
        vec4 t12 = lightTexel(12, i);
        // Cobertura e cor da máscara (sem cookie amostrado: cobertura 1, cor branca).
        float cover = 1.0;
        vec3 f = vec3(1.0), tint = vec3(1.0);
        if (projected && sampled) {
            vec4 xf = lightTexel(6, i);
            vec4 r0 = lightTexel(7, i);
            float mixF = lightTexel(9, i).w, maxLod = lightTexel(10, i).w, sharp = lightTexel(11, i).w;
            vec4 cs = cookieSample(st, gx, gy, xf, r0, repeatWrap, filtering, maxLod, sharp, t12.w, large);
            if (mixF > 0.001)
                cs = mix(cs, cookieSample(st, gx, gy, xf, lightTexel(8, i), repeatWrap, filtering, maxLod, sharp, t12.w, large), mixF);
            float raw = colored ? cs.a : dot(cs.rgb, vec3(0.2126, 0.7152, 0.0722));
            cover = contrastCurve(raw, t12.z);
            // COLOR: a cor da imagem (despré-multiplicada) vezes a cobertura; MASK: só a cobertura.
            tint = colored ? cs.rgb / max(cs.a, 1e-4) : vec3(1.0);
            f = tint * cover;
        }
        if (blend == 4) {
            // OVERRIDE: c = cor × intensidade (sem o peso), t12.x = força × peso da instância.
            float w = clamp(t12.x * cover * k, 0.0, 1.0);
            if (w > 0.0) {
                vec3 target = c * tint * mix(1.0, shade, t12.y);
                overSum += w * target;
                overW += w;
                overKeep *= 1.0 - w;
            }
            continue;
        }
        vec3 add = c * f * k;
        if (blend == 0) light += add;
        else if (blend == 1) soft += add;
        else if (blend == 2) dark += add;
        else {
            // EMISSIVE: a parte t12.x sai da superfície (detalhe opcional), o resto é ADD.
            light += add * (1.0 - t12.x);
            emit += add * t12.x * mix(1.0, shade, t12.y);
        }
    }
    if (!extended) {
        // Só luzes da 0.9: a mesma conta final da 0.14.
        fragColor = vec4(caLitColor(color, light * (1.0 - fog)), 1.0);
        return;
    }
    vec3 total = light + (vec3(1.0) - exp(-soft));
    vec3 lit = caLitColor(color, total * (1.0 - fog));
    lit *= vec3(1.0) - clamp(dark * (1.0 - fog), 0.0, 0.95);
    if (overW > 0.0) lit = mix(lit, overSum / overW, (1.0 - overKeep) * (1.0 - fog));
    if (emit.r + emit.g + emit.b > 0.0) {
        lit += emit * (1.0 - fog);
        // Núcleo quente: o que passa do branco num canal transborda para os outros (neon que estoura em branco).
        float over = max(max(lit.r, max(lit.g, lit.b)) - 1.0, 0.0);
        lit += vec3(over * 0.5);
    }
    fragColor = vec4(lit, 1.0);
}
