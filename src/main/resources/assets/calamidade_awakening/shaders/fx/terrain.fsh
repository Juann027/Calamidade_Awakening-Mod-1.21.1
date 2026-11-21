// Passe "terrain" do motor (0.11.0): calor sobre a área editada pela Terrain API e dublês esféricos em queda, de todas
// as instâncias do quadro de uma vez. Um passe por quadro, logo depois do passe "lights", cobrado do orçamento HERO
// quando algum efeito HERO desenhado precisa dele, senão do comum. A RimuLib antepõe #version 150 e common.glsl
// (scenePosAt, viewDir, skyAt, fbm3, vnoise2, initPixel/gPix...).
//
// A área quente vem da MESMA forma analítica e da mesma seed do plano do servidor (TerrainShape.distance e
// TerrainNoise, aritmética inteira de 32 bits): o cliente não recebe a lista de blocos.
//
// Dados numa textura RGBA32F (CaTerrainData, 23 texels por linha). Zonas nas linhas 0..zonas-1:
//   0: origem (relativa à câmera, blocos), calor 0..1
//   1: eixo esquerdo do referencial (mundo), alcance da forma (blocos)
//   2: eixo de cima, espessura da casca (blocos, >= 1)
//   3: eixo da frente, amplitude da borda irregular (blocos)
//   4: célula do ruído (blocos), seed baixa (16 bits), seed alta (16 bits), número de primitivas
//   5: cor da brasa (linear), quantidade de fumaça
//   6: força das fissuras, segundos desde o commit, 0, 0
//   7 + 2k: primitiva k: (tipo + 4 se subtrai, a, b, c); 8 + 2k: (x, y, z, d)  — tipos: 0 esfera, 1 cratera,
//           2 trincheira, 3 cilindro (os mesmos parâmetros de TerrainShape.Primitive)
// Dublês nas linhas seguintes:
//   0: centro (relativo à câmera), raio (blocos)
//   1: direção da queda (unitária), opacidade
//   2: cor do fogo (linear), comprimento do rastro (blocos)
//   3: modo (0 esfera desenhada aqui, 1 só aura e rastro: a malha da estrutura é desenhada no mundo), 0, 0, 0
uniform sampler2D CaTerrainData;
uniform vec4 CaTerrainInfo;   // x: zonas, y: dublês, z: tempo (s), w: qualidade (0 LOW, 1 BALANCED, 2 HIGH)
uniform vec4 CaTerrainLook;   // x: intensidade dos efeitos de tela, y: clarões permitidos (1), z: efeitos de movimento (1)

#include "calamidade_fx.glsl"

vec4 terrainTexel(int k, int i) {
    return texelFetch(CaTerrainData, ivec2(k, i), 0);
}

#include "calamidade_terrain.glsl"

// Distância com sinal à forma da zona i (negativa dentro), borda irregular incluída.
float caTerrainShape(int i, vec3 local, int count, float amp, float scale, uint seed) {
    float added = 1e9, cut = 1e9;
    for (int k = 0; k < 8; k++) {
        if (k >= count) break;
        vec4 a = terrainTexel(7 + 2 * k, i);
        vec4 b = terrainTexel(8 + 2 * k, i);
        float d = caTerrainPrimitive(local, a, b);
        if (a.x > 3.5) cut = min(cut, d); else added = min(added, d);
    }
    float d = cut < 1e8 ? max(added, -cut) : added;
    if (amp > 0.0) d -= amp * (2.0 * caTerrainFbm(local / scale, seed) - 1.0);
    return d;
}

// Faixa incandescente no fundo das trincheiras da zona i (0..1).
float caTerrainTrail(int i, vec3 local, int count) {
    float strip = 0.0;
    for (int k = 0; k < 8; k++) {
        if (k >= count) break;
        vec4 a = terrainTexel(7 + 2 * k, i);
        if (int(mod(a.x + 0.5, 4.0)) != 2 || a.x > 3.5) continue;
        vec4 b = terrainTexel(8 + 2 * k, i);
        vec3 q = local - b.xyz;
        float hlen = a.z * 0.5;
        float along = 1.0 - smoothstep(hlen, hlen + a.y, abs(q.z));
        float lateral = q.x / max(a.y * 0.45, 0.3);
        strip = max(strip, exp(-lateral * lateral) * along * smoothstep(0.2 * a.y, -0.6 * a.y, q.y));
    }
    return strip;
}

// Cor da brasa conforme o calor: vermelho escuro quando esfria, a cor da zona e branco-amarelado no auge.
vec3 caTerrainEmber(vec3 glow, float heat) {
    vec3 cold = vec3(0.35, 0.03, 0.01);
    vec3 hot = mix(glow, vec3(1.0, 0.85, 0.55), 0.35);
    return mix(cold, mix(glow, hot, smoothstep(0.6, 1.0, heat)), smoothstep(0.0, 0.6, heat));
}

void main() {
    vec2 uv = texCoord;
    vec3 rd = viewDir(uv);
    initPixel(uv);
    int zones = int(CaTerrainInfo.x + 0.5);
    int standIns = int(CaTerrainInfo.y + 0.5);
    float time = CaTerrainInfo.z;
    int quality = int(CaTerrainInfo.w + 0.5);
    float intensity = CaTerrainLook.x;
    bool flashes = CaTerrainLook.y > 0.5;
    bool motion = CaTerrainLook.z > 0.5;

    // Tremor de calor (efeito de movimento): desvia a leitura da cena sobre as zonas quentes.
    if (motion && quality > 0 && intensity > 0.0) {
        vec2 shift = vec2(0.0);
        for (int i = 0; i < 8; i++) {
            if (i >= zones) break;
            vec4 t0 = terrainTexel(0, i);
            vec4 t1 = terrainTexel(1, i);
            vec4 t2 = terrainTexel(2, i);
            vec2 hit = caRaySphere(vec3(0.0), rd, t0.xyz + t2.xyz * t1.w * 0.4, t1.w * 0.8);
            if (hit.y <= 0.0) continue;
            float chord = (hit.y - hit.x) / max(t1.w * 1.6, 1e-3);
            float n = vnoise2(uv * vec2(90.0, 50.0) + vec2(0.0, -time * 3.0)) - 0.5;
            shift += vec2(n, -n * 0.5) * 0.004 * chord * t0.w * intensity;
        }
        uv = clamp(uv + shift, vec2(0.001), vec2(0.999));
    }

    vec3 color = texture(DiffuseSampler, uv).rgb;
    bool sky = skyAt(texCoord);
    vec3 p = scenePosAt(texCoord);
    float sceneDist = length(p);
    vec3 n = vec3(0.0, 1.0, 0.0);
    if (!sky) {
        vec2 px = 1.0 / OutSize;
        n = caNormalFromNeighbors(p, scenePosAt(texCoord - vec2(px.x, 0.0)), scenePosAt(texCoord + vec2(px.x, 0.0)),
                                  scenePosAt(texCoord - vec2(0.0, px.y)), scenePosAt(texCoord + vec2(0.0, px.y)));
    }

    vec3 emission = vec3(0.0);
    float burn = 0.0;
    for (int i = 0; i < 8; i++) {
        if (i >= zones) break;
        vec4 t0 = terrainTexel(0, i);
        vec4 t1 = terrainTexel(1, i);
        vec4 t2 = terrainTexel(2, i);
        vec4 t3 = terrainTexel(3, i);
        vec4 t4 = terrainTexel(4, i);
        vec4 t5 = terrainTexel(5, i);
        vec4 t6 = terrainTexel(6, i);
        float heat = t0.w;
        float extent = t1.w, thickness = max(t2.w, 1.0);
        uint seed = uint(t4.y + 0.5) | (uint(t4.z + 0.5) << 16u);
        int count = int(t4.w + 0.5);
        // Fumaça leve: corda do raio numa esfera sobre a cratera, cortada pela cena.
        if (t5.w > 0.0 && intensity > 0.0) {
            vec3 c = t0.xyz + t2.xyz * extent * 0.45;
            vec2 hit = caRaySphere(vec3(0.0), rd, c, extent * 0.95);
            if (hit.y > 0.0) {
                float a = hit.x, b = min(hit.y, sceneDist);
                if (b > a) {
                    vec3 mid = rd * (0.5 * (a + b)) - t0.xyz;
                    float up = dot(mid, t2.xyz);
                    float density = (b - a) / (extent * 1.9) * t5.w * heat * 0.55;
                    float swirl = quality > 0 ? fbm3(mid * 0.18 + vec3(0.0, -time * 0.5, time * 0.13)) : 0.5;
                    density *= (0.35 + swirl) * smoothstep(-2.0, extent * 0.35, up);
                    vec3 smoke = mix(vec3(0.07, 0.065, 0.06), caTerrainEmber(t5.rgb, heat) * 0.6, 0.25 * heat);
                    color = mix(color, smoke, clamp(density, 0.0, 0.5) * intensity);
                }
            }
        }
        if (sky) {
            // Logo depois do commit, o cliente ainda remonta as seções e o buraco mostra o céu por alguns quadros:
            // onde o raio atravessa a forma escavada e só acha céu, pinta o fundo quente da cratera.
            if (t6.y < 1.5) {
                vec2 hit = caRaySphere(vec3(0.0), rd, t0.xyz, extent);
                if (hit.y > 0.0) {
                    float inside = 0.0;
                    for (int k = 0; k < 8; k++) {
                        vec3 q = rd * mix(hit.x, hit.y, (float(k) + 0.5) / 8.0) - t0.xyz;
                        vec3 l = vec3(dot(q, t1.xyz), dot(q, t2.xyz), dot(q, t3.xyz));
                        if (l.y < 0.5 && caTerrainShape(i, l, count, t3.w, max(t4.x, 0.5), seed) < 0.0) inside = 1.0;
                    }
                    vec3 floorColor = vec3(0.09, 0.05, 0.04) + caTerrainEmber(t5.rgb, heat) * 0.35 * heat;
                    color = mix(color, floorColor, inside * (1.0 - smoothstep(1.0, 1.5, t6.y)));
                }
            }
            continue;
        }
        vec3 rel = p - t0.xyz;
        float reach = extent + thickness + 2.0;
        if (dot(rel, rel) > reach * reach) continue;
        // Ponto um pouco para dentro da face: o bloco cuja face o pixel mostra.
        vec3 inner = rel - n * 0.3;
        vec3 local = vec3(dot(inner, t1.xyz), dot(inner, t2.xyz), dot(inner, t3.xyz));
        float d = caTerrainShape(i, local, count, t3.w, max(t4.x, 0.5), seed);
        float w = d < 0.0 ? 1.0 : 1.0 - smoothstep(0.0, thickness + 0.3, d);
        if (w <= 0.0) continue;
        // Brasa irregular, fissuras luminosas e um pulso lento (parado com "ocultar clarões").
        float mottle = fbm3(local * 0.85 + vec3(float(seed & 1023u)));
        float crack = 1.0 - smoothstep(0.0, 0.06 + 0.04 * (1.0 - heat), abs(vnoise3(local * 0.45 + vec3(5.3, 1.7, 9.1)) - 0.5));
        float pulse = flashes ? 0.88 + 0.12 * sin(time * 2.3 + mottle * 6.0) : 1.0;
        vec3 ember = caTerrainEmber(t5.rgb, heat);
        float glow = pow(heat, 1.5) * (0.22 + 0.78 * smoothstep(0.3, 0.8, mottle)) * pulse;
        emission += ember * glow * w * 0.85;
        emission += mix(ember, vec3(1.0, 0.9, 0.6), 0.5) * crack * t6.x * heat * heat * w * 1.5;
        emission += vec3(1.0, 0.72, 0.35) * caTerrainTrail(i, local, count) * heat * w * 2.2;
        burn = max(burn, w * heat);
    }
    // Brasa, fissuras e escurecimento são efeitos de luz: seguem a intensidade dos efeitos de tela (0 desliga).
    color *= 1.0 - 0.45 * burn * intensity;
    color += emission * intensity;

    // Dublês: esfera em chamas desenhada aqui (ou só a aura de uma estrutura) e o rastro de fogo.
    for (int j = 0; j < 8; j++) {
        if (j >= standIns) break;
        int row = zones + j;
        vec4 s0 = terrainTexel(0, row);
        vec4 s1 = terrainTexel(1, row);
        vec4 s2 = terrainTexel(2, row);
        vec4 s3 = terrainTexel(3, row);
        vec3 c = s0.xyz;
        float radius = s0.w, alpha = s1.w;
        vec3 dir = s1.xyz;
        vec3 fire = s2.rgb;
        float tail = s2.w;
        if (s3.x < 0.5) {
            vec2 hit = caRaySphere(vec3(0.0), rd, c, radius);
            if (hit.y > 0.0 && hit.x < sceneDist && hit.x > 0.0) {
                vec3 q = rd * hit.x - c;
                vec3 nrm = q / radius;
                float flow = fbm3(nrm * 2.6 + dir * time * 1.4 + vec3(0.0, time * 0.6, 0.0));
                vec3 crust = vec3(0.12, 0.045, 0.02);
                vec3 surface = mix(crust, fire * 1.8, smoothstep(0.38, 0.72, flow));
                surface += fire * 1.6 * smoothstep(0.2, 1.0, dot(nrm, dir));
                surface += vec3(1.0, 0.8, 0.5) * pow(1.0 - max(dot(-rd, nrm), 0.0), 3.0) * 0.8;
                color = mix(color, surface, alpha);
            }
        }
        // Aura e rastro: efeitos de luz, seguem a intensidade dos efeitos de tela.
        float t;
        float dist = rayPointDist(vec3(0.0), rd, c, t);
        float aura = dist > radius ? exp(-(dist - radius) / max(radius * 0.7, 0.3)) : 1.0;
        aura *= caDepthFade(t, sceneDist, radius);
        emission = fire * aura * (s3.x < 0.5 ? 0.55 : 0.3);
        if (tail > 0.0) {
            vec2 g = caSegmentGlow(vec3(0.0), rd, c, c - dir * tail, radius * 0.55, radius * 1.4, gPix, sceneDist);
            vec3 hitTail = caRaySegment(vec3(0.0), rd, c, c - dir * tail);
            float fade = 1.0 - hitTail.y;
            float flicker = flashes ? 0.85 + 0.15 * vnoise2(vec2(hitTail.y * tail * 0.8 - time * 9.0, float(j))) : 1.0;
            emission += (mix(fire, vec3(1.0, 0.9, 0.6), g.x * 0.6) * g.x * 1.2 + fire * g.y * 0.5) * fade * flicker;
        }
        color += emission * alpha * intensity;
    }
    fragColor = vec4(color, 1.0);
}
