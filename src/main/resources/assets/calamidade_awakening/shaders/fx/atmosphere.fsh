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
uniform vec4 CaAtmoC;           // x: distância de renderização (blocos), y: 1 quando o passe "sky" já pintou o céu
                                // da atmosfera neste quadro (0.12.0), z: 1 quando o perfil da dimensão tirou o céu do
                                // jogo (só a cor de fundo, a névoa, ficou na tela)
uniform vec4 CaAtmoLens0;       // 0.12.0: as lentes dos buracos negros, as mesmas do passe "sky"
uniform vec4 CaAtmoLens1;
uniform vec4 CaAtmoLensExt;

#include "calamidade_fx.glsl"
#include "calamidade_sky.glsl"

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    // 0.12.0: perto de um buraco negro o passe "sky" mostrou aqui a cor de outro ponto da cena; o céu e a névoa seguem
    // esse ponto (céu desviado é céu, terreno desviado é terreno).
    vec3 dir;
    bool sky;
    vec2 suv = caSkyLensUv(texCoord, CaAtmoLens0, CaAtmoLens1, CaAtmoLensExt, dir, sky);
    if (sky) {
        // 0.12.0: com camadas de céu, o passe "sky" já pintou este céu por baixo delas.
        if (CaAtmoC.y < 0.5)
            color = max(color + caAtmoSkyDelta(dir, CaAtmoVanillaSky, CaAtmoVanillaFog, CaAtmoC.x, CaAtmoC.z > 0.5,
                CaAtmoSkyTop, CaAtmoSkyHorizon, CaAtmoFogColor, CaAtmoA.x, CaAtmoA.y, CaAtmoB.z, GTime), vec3(0.0));
    } else if (CaAtmoB.w > 0.5) {
        float distance = length(scenePosAt(suv));
        color = mix(color, CaAtmoFogColor, CaAtmoA.y * smoothstep(CaAtmoA.z, CaAtmoA.w, distance));
    }
    color *= CaAtmoB.x;
    color = mix(color, color * CaAtmoTint, CaAtmoB.y);
    fragColor = vec4(color, 1.0);
}
