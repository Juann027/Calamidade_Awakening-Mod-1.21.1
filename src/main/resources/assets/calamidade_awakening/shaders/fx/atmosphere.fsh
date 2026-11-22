// Passe "atmosphere" do motor (0.8.0): um por quadro, depois de todos os passes de efeito, fora do orçamento
// de addons. A RimuLib antepõe #version 150 e common.glsl (skyAt, viewDir, scenePosAt, hash13, lum, GTime...).
//
// Céu (pixels com skyAt): o motor estima a cor que o jogo pintou (CaAtmoVanillaSky no alto, CaAtmoVanillaFog no
// horizonte) e soma a diferença até o gradiente da atmosfera. Assim o sol, a lua, as nuvens e os efeitos
// desenhados sobre o céu continuam lá. Névoa: por padrão vem da névoa do próprio jogo (eventos do NeoForge);
// com a opção vanillaFog desligada (CaAtmoB.w = 1), este passe aplica a névoa pela profundidade (scenePosAt).
uniform vec3 CaAtmoSkyTop;      // cor do zênite
uniform vec3 CaAtmoSkyHorizon;  // cor do horizonte
uniform vec3 CaAtmoFogColor;    // cor da névoa
uniform vec3 CaAtmoTint;        // tinta ambiente normalizada (luminância 1, cada canal até 4)
uniform vec3 CaAtmoVanillaSky;  // cor do céu do jogo na posição da câmera
uniform vec3 CaAtmoVanillaFog;  // cor da névoa que o jogo usou neste quadro
uniform vec4 CaAtmoA;           // x: skyBlend, y: fogDensity, z: fogStart (blocos), w: fogEnd (blocos)
uniform vec4 CaAtmoB;           // x: exposição, y: força da tinta, z: estrelas, w: névoa na tela (0/1)
uniform vec4 CaAtmoC;           // x: distância de renderização (blocos), y: máscaras de céu (0.12.0), z: 1 quando o
                                // perfil da dimensão tirou o céu do jogo (só a cor de fundo, a névoa, ficou na tela)
uniform vec4 CaAtmoMask[8];     // 0.12.0: direção (xyz) e raio angular (w, radianos) das camadas opacas do céu
uniform vec4 CaAtmoLens0;       // 0.12.0: as lentes dos buracos negros, as mesmas do passe "sky"
uniform vec4 CaAtmoLens1;
uniform vec4 CaAtmoLensExt;

#include "calamidade_fx.glsl"
#include "calamidade_sky.glsl"

// Céu que o jogo pinta: disco 16 blocos acima, com névoa FOG_SKY (cilíndrica, de 0 à distância de renderização).
vec3 atmoVanillaSky(vec3 dir) {
    return caVanillaSky(dir, CaAtmoVanillaSky, CaAtmoVanillaFog, CaAtmoC.x);
}

// 0.12.0: quanto deste pixel de céu é coberto por uma camada opaca do passe "sky" (buraco negro, interior do portal,
// disco do eclipse, astro): ali o gradiente e as estrelas da atmosfera não entram, para o disco negro não clarear.
float atmoSkyMask(vec3 dir) {
    float covered = 0.0;
    int count = int(CaAtmoC.y + 0.5);
    for (int i = 0; i < 8; i++) {
        if (i >= count) break;
        vec4 m = CaAtmoMask[i];
        covered = max(covered, 1.0 - smoothstep(m.w * 0.94, m.w, caAngle(dir, m.xyz)));
    }
    return covered;
}

// Gradiente da atmosfera: horizonte até ~37 graus de elevação, zênite acima.
vec3 atmoGradient(vec3 dir) {
    return mix(CaAtmoSkyHorizon, CaAtmoSkyTop, smoothstep(0.0, 0.6, dir.y));
}

// Névoa da atmosfera sobre o céu: máxima no horizonte e abaixo dele, some até ~27 graus.
float atmoHorizon(vec3 dir) {
    return 1.0 - smoothstep(0.0, 0.45, dir.y);
}

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    // 0.12.0: perto de um buraco negro o passe "sky" mostrou aqui a cor de outro ponto da cena; o céu e a névoa seguem
    // esse ponto (céu desviado é céu, terreno desviado é terreno).
    vec3 dir;
    bool sky;
    vec2 suv = caSkyLensUv(texCoord, CaAtmoLens0, CaAtmoLens1, CaAtmoLensExt, dir, sky);
    if (sky) {
        vec3 base = atmoVanillaSky(dir);
        vec3 target = mix(base, atmoGradient(dir), CaAtmoA.x);
        // 0.12.0: sem o céu do jogo, a tela tem só a cor de fundo (a névoa); o passe pinta o céu inteiro.
        if (CaAtmoC.z > 0.5) base = CaAtmoVanillaFog;
        if (CaAtmoB.z > 0.0 && dir.y > -0.05)
            target += vec3(0.85, 0.9, 1.0) * caStars(dir, GTime) * CaAtmoB.z;
        target = mix(target, CaAtmoFogColor, CaAtmoA.y * atmoHorizon(dir));
        // A máscara vale onde a camada foi pintada: pixel de céu de verdade, na direção sem desvio. O céu, de onde a
        // luz veio (desviada); um morro que a lente trocou por céu recebe o céu inteiro.
        float mask = skyAt(texCoord) ? atmoSkyMask(viewDir(texCoord)) : 0.0;
        color = max(color + (target - base) * (1.0 - mask), vec3(0.0));
    } else if (CaAtmoB.w > 0.5) {
        float distance = length(scenePosAt(suv));
        color = mix(color, CaAtmoFogColor, CaAtmoA.y * smoothstep(CaAtmoA.z, CaAtmoA.w, distance));
    }
    color *= CaAtmoB.x;
    color = mix(color, color * CaAtmoTint, CaAtmoB.y);
    fragColor = vec4(color, 1.0);
}
