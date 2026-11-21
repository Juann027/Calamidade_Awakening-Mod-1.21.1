// Passe "beams" do motor (0.9.0): todos os feixes visíveis do quadro de uma vez, em espaço de tela. Um passe por
// quadro, cobrado do orçamento HERO quando algum efeito HERO desenhado tem feixes, senão do comum. A RimuLib antepõe
// #version 150 e common.glsl (viewDir, scenePosAt, initPixel/gPix, vnoise2, TAU...).
//
// Os feixes chegam numa textura RGBA32F (CaBeamData, 6 texels por feixe, um feixe por linha), porque 128 feixes
// em uniforms passariam do mínimo garantido pelo OpenGL. Linha i:
//   0: início (relativo à câmera, blocos), meia-largura do núcleo
//   1: fim (antes da revelação), meia-largura do halo
//   2: cor do núcleo × intensidade × peso, revelação (0..1)
//   3: cor do halo × intensidade × peso × intensidade dos efeitos de tela, número de pulsos
//   4: deslocamento dos pulsos (blocos), comprimento do pulso (blocos), ganho dos pulsos, tempo do ruído
//   5: amplitude da ondulação (blocos), comprimento de onda (blocos), deslocamento da onda (blocos), amplitude do ruído
uniform sampler2D CaBeamData;
uniform vec4 CaBeamInfo;   // x: número de feixes

#include "calamidade_fx.glsl"

vec4 beamTexel(int k, int i) {
    return texelFetch(CaBeamData, ivec2(k, i), 0);
}

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    vec3 rd = viewDir(texCoord);
    initPixel(texCoord);
    float sceneDist = length(scenePosAt(texCoord));
    int count = int(CaBeamInfo.x + 0.5);
    vec3 light = vec3(0.0);
    for (int i = 0; i < 128; i++) {
        if (i >= count) break;
        vec4 t0 = beamTexel(0, i);
        vec4 t1 = beamTexel(1, i);
        vec4 t5 = beamTexel(5, i);
        vec3 a = t0.xyz;
        vec3 full = t1.xyz - a;
        float fullLen = length(full);
        vec4 t2 = beamTexel(2, i);
        float reveal = t2.w;
        vec3 b = a + full * reveal;
        float len = fullLen * reveal;
        float core = t0.w, halo = t1.w;
        float lateral = t5.x + t5.w;
        vec3 hit = caRaySegment(vec3(0.0), rd, a, b);
        float pix = max(gPix * hit.x, 1e-5);
        float reach = 5.0 * max(max(halo, core), pix) + lateral + pix;
        // Ponta acesa enquanto o feixe nasce.
        float tipT;
        float tipD = reveal < 0.999 ? rayPointDist(vec3(0.0), rd, b, tipT) : 1e9;
        if (hit.z > reach && tipD > reach) continue;
        vec4 t3 = beamTexel(3, i);
        vec4 t4 = beamTexel(4, i);
        float d = hit.z;
        if (lateral > 0.0 && len > 1e-3) {
            // Deslocamento lateral (ondulação + ruído), preso nas pontas. O eixo lateral é fixo por feixe
            // (perpendicular ao feixe e à direção do seu meio): calculado por pixel, ele giraria em volta de um feixe
            // visto de frente e a onda viraria anéis.
            vec3 dir = (b - a) / len;
            vec3 q = a + (b - a) * hit.y;
            vec3 side = cross(dir, normalize(a + b + vec3(0.0, 1e-4, 0.0)));
            float sideLen = length(side);
            if (sideLen > 1e-4) {
                side /= sideLen;
                vec3 off = rd * hit.x - q;
                float lat = dot(off, side);
                float other = length(off - side * lat);
                float along = hit.y * len;
                // De frente para a câmera, o ponto mais próximo no feixe fica mal condicionado (um passo de pixel
                // muda muito a posição ao longo dele) e a onda viraria anéis no halo: some entre ~30 e ~12 graus.
                float facing = smoothstep(0.2, 0.5, length(cross(dir, rd)));
                float taper = caBeamTaper(hit.y, len, 2.0) * facing;
                float w = t5.x * sin(TAU * (along - t5.z) / max(t5.y, 0.25));
                w += t5.w * (vnoise2(vec2(along * 0.7, t4.w * 3.0)) * 2.0 - 1.0);
                lat -= w * taper;
                d = sqrt(lat * lat + other * other);
            }
        }
        // Oclusão pelo terreno e fade perto da câmera (um feixe que sai dos olhos não enche a tela).
        float vis = caDepthFade(hit.x, sceneDist, max(2.0 * core, 0.15)) * smoothstep(0.2, 1.2, hit.x);
        float coreV = caBeamCore(d, core, pix);
        float haloV = caBeamHalo(d, halo, pix);
        // Pulsos: espaçados igualmente no comprimento cheio, correndo pela velocidade (deslocamento em blocos).
        float pulses = t3.w;
        float pulse = 0.0;
        if (pulses > 0.5 && fullLen > 1e-3)
            pulse = caPulseTrain(hit.y * len - t4.x, fullLen / pulses, t4.y) * t4.z;
        // Núcleo na cor do núcleo; halo a 55% da cor do halo no eixo, caindo com a distância.
        light += vis * (t2.rgb * coreV * (1.0 + 1.5 * pulse) + t3.rgb * haloV * (0.55 + 1.5 * pulse));
        if (tipD < reach) {
            float tipPix = max(gPix * tipT, 1e-5);
            float tip = caBeamHalo(tipD, max(halo, core * 3.0) * 0.8, tipPix) * caDepthFade(tipT, sceneDist, 0.3);
            light += (t2.rgb * 0.5 + t3.rgb) * tip * (1.0 - smoothstep(0.9, 0.999, reveal));
        }
    }
    fragColor = vec4(color + light, 1.0);
}
