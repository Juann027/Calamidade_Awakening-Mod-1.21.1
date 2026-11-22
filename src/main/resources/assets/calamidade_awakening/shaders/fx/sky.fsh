// Passe "sky" do motor (0.12.0): as camadas de céu (Sky API) de todos os efeitos ativos de uma vez, primeiro passe do
// quadro (antes das luzes, dos efeitos e da atmosfera), fora do orçamento de addons, só quando há camada ativa.
// A RimuLib antepõe #version 150 e common.glsl (viewDir, skyAt, scenePosAt, project, sampleScene...).
//
// Ordem por pixel:
//   1. lente gravitacional dos buracos negros (calamidade_sky.glsl, a mesma conta do passe de atmosfera): a cena é
//      amostrada com desvio (céu e terreno distante; o terreno a menos de ~12 blocos não entorta);
//   2. sol e lua do jogo apagados onde uma camada pede (subtrai a mesma textura que o jogo somou, com a geometria
//      conferida por javap em LevelRenderer.renderSky), e o céu da Atmosphere ativa (o passe de atmosfera, depois, não
//      mexe mais nos pixels de céu: assim as camadas ficam por cima dele);
//   3. escurecimento do mundo (eclipse), já com intensidade e "ocultar clarões" aplicados no Java;
//   4. camadas, só em pixels de céu, da menos para a mais importante.
//
// As camadas chegam numa textura RGBA32F (CaSkyData, 10 texels por camada, uma por linha):
//   0: direção (xyz, relativa à câmera), raio angular (radianos)
//   1: tipo (0 buraco negro, 1 portal, 2 eclipse, 3 fenda, 4 astro, 5 aurora, 6 raios ao longe), rotação (rad),
//      inclinação (rad), progresso
//   2: cor primária × intensidade, peso
//   3: cor secundária × intensidade, livre
//   4: semente (0..1), segundos da instância (parados com "efeitos de movimento" desligado), ganho de clarão,
//      âncora (0 direção, 1 sol, 2 lua)
//   5: giro acumulado (rad: estrias do disco do buraco negro, superfície do astro; o do portal já está na rotação)
//   6..9: raios ao longe (só no tipo 6), um por texel: pé relativo à câmera (x, z, blocos), envelope (0..1), semente.
//      Posições e tempos saem do SkyMath.bolts (Java, determinístico pela semente do efeito); aqui só a forma.
// A rotação (texel 1) já gira a base local da camada: em q, o eixo x é a direção da rotação.
uniform sampler2D CaSkyData;
uniform sampler2D CaSkySunTex;    // textura do sol do jogo (apagar o sol)
uniform sampler2D CaSkyMoonTex;   // atlas de fases da lua do jogo (apagar a lua)
uniform vec4 CaSkyInfo;           // x: camadas, y: exposição do mundo (escurecimento), z: qualidade (0, 1, 2), w: 1 com raios ao longe
uniform vec4 CaSkyVanilla;        // x: apagar o sol (0..1), y: apagar a lua (0..1), z: fase da lua (0..7), w: 1 - chuva (0 sem sol/lua do jogo)
uniform vec3 CaSkyAxisX;          // referencial do sol e da lua do jogo (colunas de Ry(-90)·Rx(hora·360))
uniform vec3 CaSkyAxisY;
uniform vec3 CaSkyAxisZ;
uniform vec3 CaSkyVanillaSky;     // cor do céu do jogo na câmera
uniform vec3 CaSkyVanillaFog;     // névoa do quadro
uniform vec4 CaSkyMisc;           // x: distância de renderização (blocos)
uniform vec4 CaSkyLens0;          // lente 0: direção, raio de Einstein (rad)
uniform vec4 CaSkyLens1;          // lente 1
uniform vec4 CaSkyLensExt;        // x, y: alcance das lentes (rad), z: lentes (0..2)
uniform vec3 CaSkyAtmoTop;        // céu da Atmosphere misturada do quadro: zênite, horizonte, névoa
uniform vec3 CaSkyAtmoHorizon;
uniform vec3 CaSkyAtmoFog;
uniform vec4 CaSkyAtmoA;          // x: mistura do céu, y: densidade da névoa, z: estrelas, w: 1 com atmosfera ativa
uniform vec4 CaSkyAtmoB;          // x: 1 quando o perfil da dimensão tirou o céu do jogo

#include "calamidade_fx.glsl"
#include "calamidade_sky.glsl"

// Gaussiana exp(-x²): pow() com base negativa é indefinido em GLSL (NaN em alguns drivers).
float bump(float x) {
    return exp(-x * x);
}

vec4 skyTexel(int k, int i) {
    return texelFetch(CaSkyData, ivec2(k, i), 0);
}

float octaves() {
    return CaSkyInfo.z < 0.5 ? 2.0 : (CaSkyInfo.z < 1.5 ? 3.0 : 4.0);
}

// ------------------------------------------------------------------------------- sol e lua do jogo

vec3 vanillaFill(vec3 d) {
    return caVanillaSky(d, CaSkyVanillaSky, CaSkyVanillaFog, CaSkyMisc.x);
}

// 0.14.0: fração do sol e da lua do jogo ainda visível no pixel (1 - cobertura dos fundos; 1 sem fundo) e o céu de
// preenchimento com os fundos por cima (a tela, na direção d, sem o sol e a lua).
float gVanillaVisible = 1.0;
// 0.14.0 (fase 3): luz e cobertura dos objetos do céu no ponto da tela que este pixel mostra (zero sem objetos).
vec4 gElements = vec4(0.0);
vec3 skyFill(vec3 d) {
    vec3 base = vanillaFill(d);
    if (caBackdropOn()) {
        float coverage;
        base = caBackdropOver(base, d, coverage);
    }
    if (caElementsOn()) base = base * (1.0 - gElements.a) + gElements.rgb;
    return base;
}

// O jogo soma o sol (SRC_ALPHA, ONE) com alfa 1 - chuva: tirar exatamente a mesma textura devolve o céu de baixo. Onde
// a soma estourou em 1 (o miolo do sol), o céu de baixo se perdeu: entra a estimativa do céu do jogo.
vec3 removeQuad(vec3 color, vec3 d, sampler2D tex, vec2 st, float amount) {
    ivec2 size = textureSize(tex, 0);
    vec4 t = texelFetch(tex, clamp(ivec2(st * vec2(size)), ivec2(0), size - 1), 0);
    vec3 added = t.rgb * t.a * CaSkyVanilla.w * gVanillaVisible;
    vec3 removed = max(color - added, vec3(0.0));
    float saturated = step(0.996, max(color.r, max(color.g, color.b))) * step(0.3, max(added.r, max(added.g, added.b)));
    if (gVanillaVisible < 1.0) {
        // 0.14.0: sob um fundo, a soma que estourou em 1 ficou por baixo dele e a tela não mostra mais o estouro:
        // prevê pelo céu do jogo estimado mais o sol inteiro.
        vec3 full = t.rgb * t.a * CaSkyVanilla.w, under = vanillaFill(d) + full;
        saturated = step(0.996, max(under.r, max(under.g, under.b))) * step(0.3, max(full.r, max(full.g, full.b)));
    }
    removed = mix(removed, skyFill(d), saturated);
    return mix(color, removed, amount);
}

vec3 removeVanilla(vec3 color, vec3 d) {
    if (CaSkyVanilla.w <= 0.0 || CaSkyVanilla.x + CaSkyVanilla.y <= 0.0) return color;
    vec3 l = vec3(dot(d, CaSkyAxisX), dot(d, CaSkyAxisY), dot(d, CaSkyAxisZ));
    if (CaSkyVanilla.x > 0.0 && l.y > 1e-4) {
        vec2 p = l.xz * (100.0 / l.y);
        if (abs(p.x) <= 30.0 && abs(p.y) <= 30.0)
            color = removeQuad(color, d, CaSkySunTex, (p + 30.0) / 60.0, CaSkyVanilla.x);
    }
    if (CaSkyVanilla.y > 0.0 && l.y < -1e-4) {
        vec2 p = l.xz * (-100.0 / l.y);
        if (abs(p.x) <= 20.0 && abs(p.y) <= 20.0) {
            float phase = floor(CaSkyVanilla.z + 0.5);
            float column = mod(phase, 4.0), row = mod(floor(phase / 4.0), 2.0);
            vec2 st = vec2((column + (20.0 - p.x) / 40.0) / 4.0, (row + (p.y + 20.0) / 40.0) / 2.0);
            color = removeQuad(color, d, CaSkyMoonTex, st, CaSkyVanilla.y);
        }
    }
    return color;
}

// ------------------------------------------------------------------------------- tipos de camada
// Cada um devolve vec4(luz somada, cobertura): cor = cor × (1 - cobertura) + luz. q são coordenadas locais em unidades
// do raio da camada (1 = borda nominal).

// Estrelas densas de "outro céu" (interior do portal, fenda): célula a célula, uma estrela em cada 4.
float densStars(vec2 p, float seed) {
    vec2 cell = floor(p);
    float h = caHash13(vec3(cell, seed * 91.0));
    if (h < 0.75) return 0.0;
    vec2 c = cell + 0.25 + 0.5 * vec2(caHash13(vec3(cell, 11.0 + seed)), caHash13(vec3(cell, 23.0 + seed)));
    float r = length(p - c);
    return (1.0 - smoothstep(0.02, 0.12, r)) * (h - 0.75) * 4.0;
}

// Rotação 2D (ângulo em radianos), local para não depender da convenção de rot() do common.glsl.
mat2 rot2(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, s, -s, c);
}

// Buraco negro: sombra (0,2 do raio), anel de fótons fino e muito brilhante, disco largo inclinado atravessando a
// sombra de lado a lado, a metade de trás do disco dobrada pela lente por cima da sombra (e um fio por baixo) e o céu
// em volta levemente escurecido, como se ele engolisse a luz. q em unidades do raio externo do disco.
const float BH_SHADOW = 0.2;
const float BH_INNER = 0.3;

float diskProfile(float rd) {
    return smoothstep(BH_INNER - 0.015, BH_INNER + 0.04, rd) * (1.0 - smoothstep(0.55, 1.0, rd));
}

float diskStreaks(float rd, float angle, float spin, float seed) {
    float omega = pow(BH_INNER / max(rd, 0.2), 1.5);
    return caFbm2(vec2(rd * 11.0 + seed * 7.0, (angle - spin * omega) * 2.4), octaves());
}

vec4 blackHole(vec2 q, vec4 t1, vec4 t2, vec4 t3, vec4 t4, vec4 t5) {
    float r = length(q);
    if (r > 3.2) return vec4(0.0);
    float progress = t1.w, tilt = t1.z, spin = t5.x, seed = t4.x;
    float side = tilt >= 0.0 ? 1.0 : -1.0;
    vec3 hot = t2.rgb, cool = t3.rgb;
    float shadow = BH_SHADOW;
    // Imagem direta: o plano inclinado achata o eixo vertical; quase de lado, o disco vira uma faixa larga.
    float ci = max(cos(tilt), 0.05);
    vec2 dq = vec2(q.x, q.y / ci);
    float rd = length(dq);
    float heat = clamp((1.0 - rd) / (1.0 - BH_INNER), 0.0, 1.0);
    float doppler = 1.0 - 0.5 * (q.x / max(rd, 1e-3)) * sin(abs(tilt));
    vec3 diskColor = mix(cool, hot, heat * heat) + vec3(1.0, 0.95, 0.85) * pow(heat, 5.0) * 0.9;
    float streak = diskStreaks(rd, atan(dq.y, dq.x), spin, seed);
    float diskLight = diskProfile(rd) * (0.4 + 0.9 * streak) * doppler * progress;
    // A metade de trás fica escondida atrás da sombra; a da frente passa na frente dela.
    bool far = q.y * side > 0.0;
    float direct = (far && r < shadow * 1.02) ? 0.0 : diskLight;
    // Metade de trás dobrada pela lente: um arco largo que abraça a sombra por cima e encontra o disco nas pontas.
    float sinA = side * q.y / max(r, 1e-3);
    float arcIn = shadow * 1.05;
    float top = smoothstep(-0.2, 0.3, sinA);
    float bottom = smoothstep(-0.2, 0.3, -sinA);
    float arc = 0.0;
    float ta = (r - arcIn) / 0.24;
    if (ta > 0.0 && ta < 1.0) {
        float rdA = BH_INNER + ta * (0.95 - BH_INNER);
        float sA = diskStreaks(rdA, atan(q.y, q.x) * 1.0, spin, seed + 0.31);
        arc += diskProfile(rdA) * (0.35 + 0.85 * sA) * top * (0.25 + 0.75 * abs(sin(tilt)));
    }
    float tb = (r - arcIn) / 0.12;
    if (tb > 0.0 && tb < 1.0) {
        float rdB = BH_INNER + tb * (0.8 - BH_INNER);
        arc += diskProfile(rdB) * 0.45 * bottom * (0.25 + 0.75 * abs(sin(tilt)));
    }
    float arcHeat = clamp(1.0 - (r - arcIn) / 0.24, 0.0, 1.0);
    vec3 arcColor = mix(cool, hot, arcHeat * arcHeat) + vec3(1.0, 0.95, 0.85) * pow(arcHeat, 5.0) * 0.6;
    // Anel de fótons: fio muito brilhante colado na sombra.
    float photon = bump((r - shadow * 1.02) / 0.0055) * 3.2 + bump((r - shadow * 1.02) / 0.02) * 0.5;
    vec3 light = diskColor * direct * 1.3 + arcColor * arc * progress * 1.1
        + mix(hot, vec3(1.0), 0.6) * photon * (0.4 + 0.6 * progress);
    // Brilho morno bem fraco e o céu engolido em volta.
    light += cool * 0.05 * progress * exp(-max(r - 1.0, 0.0) / 0.4) * step(shadow, r);
    float swallow = 0.5 * exp(-max(r - shadow, 0.0) / 0.85) * (1.0 - smoothstep(2.4, 3.2, r));
    float cover = max(caDisc(r, shadow, 0.004), swallow);
    return vec4(light * t2.w, cover * t2.w);
}

// Semente do portal: fissura vertical serrilhada que pisca e arcos de energia curtos que saltam dela e mudam a cada
// quarto de segundo. Sem clarões ("ocultar clarões"), a fissura fica acesa sem piscar e os arcos ficam fracos.
float portalSeed(vec2 p, float seed, float seconds, float flash) {
    float y = p.y;
    float len = 0.2;
    float taper = 1.0 - smoothstep(0.0, len, abs(y));
    float x0 = 0.018 * (caNoise2(vec2(y * 28.0 + seed * 9.0, 0.5)) - 0.5) + 0.01 * (caNoise2(vec2(y * 70.0, seed)) - 0.5);
    float crack = exp(-abs(p.x - x0) / 0.004) * taper;
    float glow = exp(-length(vec2(p.x * 1.6, p.y * 0.8)) / 0.05);
    float blinkRaw = 0.5 + 0.5 * sin(seconds * 9.0 + 2.0 * sin(seconds * 3.1));
    float blink = mix(0.8, 0.35 + 0.65 * blinkRaw, flash);
    float arcs = 0.0;
    float slot = floor(seconds * 4.0);
    for (int k = 0; k < 5; k++) {
        float fk = float(k);
        float ang = caHash13(vec3(slot, fk, seed * 50.0)) * 6.2831853;
        float alen = 0.08 + 0.16 * caHash13(vec3(slot, fk, seed * 50.0 + 1.0));
        vec2 d = vec2(cos(ang), sin(ang));
        vec2 o = vec2(x0, (caHash13(vec3(slot, fk, seed * 50.0 + 2.0)) - 0.5) * len);
        float along = dot(p - o, d);
        if (along < 0.0 || along > alen) continue;
        float across = dot(p - o, vec2(-d.y, d.x))
            - 0.02 * (caNoise2(vec2(along * 40.0 + fk * 7.0, slot)) - 0.5) * (along / alen) * 3.0;
        arcs += exp(-abs(across) / 0.003) * (1.0 - along / alen);
    }
    return (crack * 2.2 + glow * 0.9) * blink + arcs * (0.25 + 0.95 * flash) * 1.4;
}

vec4 portal(vec2 q, vec4 t1, vec4 t2, vec4 t3, vec4 t4) {
    float open = t1.w, seed = t4.x, seconds = t4.y, flash = t4.z;
    vec2 p = vec2(q.x, q.y / max(cos(t1.z), 0.15));
    float r = length(p);
    if (r > open + 0.6) return vec4(0.0);
    float a = atan(p.y, p.x);
    // Semente antes de abrir (0 < abertura < 0,15): uma fissura que pisca e solta pequenos arcos de energia.
    float seedPhase = (1.0 - smoothstep(0.06, 0.15, open)) * step(1e-4, open);
    float spark = 0.0;
    if (seedPhase > 0.0) spark = portalSeed(p * 0.55, seed, seconds, flash) * seedPhase;
    // Anel com arcos de energia (ruído ao longo do ângulo, andando com o tempo).
    float crackle = caFbm2(vec2(a * 4.0 + seed * 13.0, seconds * 1.7), octaves());
    float width = 0.025 + 0.03 * crackle;
    // O anel só aparece quando a fissura se abre (abertura > 0,06).
    float opened = smoothstep(0.04, 0.12, open);
    float ring = bump((r - open) / width) * opened;
    float birth = (1.0 - smoothstep(0.08, 0.3, open)) * opened * flash;
    vec3 light = t2.rgb * ring * (1.2 + 1.5 * crackle) * (1.0 + 2.5 * birth) + mix(t2.rgb, vec3(1.0), 0.5) * spark;
    light += t2.rgb * 0.25 * exp(-max(r - open, 0.0) / 0.12) * step(open, r) * opened * (1.0 + 3.0 * birth);
    float inside = caDisc(r, open, 0.012) * opened;
    if (inside > 0.0) {
        // Outro céu, preso ao anel (que gira com a rotação e o giro): abrir revela mais dele. Perto da borda, refração
        // em redemoinho.
        float swirl = smoothstep(open * 0.6, open, r) * 0.35;
        vec2 o = rot2(swirl * sin(seconds * 0.8 + a * 3.0)) * p;
        vec2 g = o * 1.35;
        float gr = length(g), ga = atan(g.y, g.x);
        float arms = pow(0.5 + 0.5 * cos(2.0 * ga - 4.2 * log(max(gr, 0.02)) + seed * 6.28), 3.0);
        float dust = 0.55 + 0.45 * caNoise2(g * 9.0 + seed * 5.0);
        float armLight = arms * exp(-gr * 1.6) * dust;
        float coreLight = exp(-gr * gr * 22.0) * 1.6;
        float neb = caFbm2(o * 2.6 + vec2(seed * 31.0, seconds * 0.02), octaves());
        float neb2 = caFbm2(o * 5.3 - vec2(seconds * 0.015, seed * 17.0), octaves());
        vec3 deep = vec3(0.004, 0.002, 0.018) + t3.rgb * 0.05;
        vec3 nebula = mix(t3.rgb, t2.rgb, neb2) * smoothstep(0.35, 0.85, neb) * 0.7;
        vec3 warm = vec3(1.0, 0.9, 0.75);
        vec3 armColor = mix(warm, mix(t2.rgb, vec3(1.0), 0.3), smoothstep(0.1, 0.7, gr));
        vec3 inner = deep + nebula + armColor * armLight * 1.1 + warm * coreLight
            + vec3(0.9, 0.95, 1.0) * densStars(o * 70.0, seed);
        light += inner * inside;
    }
    return vec4(light * t2.w, inside * t2.w);
}

vec4 eclipseSun(vec2 q, vec4 t1, vec4 t2, vec4 t3, vec4 t4) {
    float progress = t1.w, seed = t4.x, seconds = t4.y, flash = t4.z;
    float r = length(q);
    if (r > 6.0) return vec4(0.0);
    // O disco que cobre parte de longe (2,2 raios) na direção da rotação e chega ao centro na totalidade.
    vec2 offset = vec2(1.0, 0.0) * (1.0 - progress) * 2.2;
    float moonDisc = caDisc(length(q - offset), 1.0, 0.012);
    float total = smoothstep(0.9, 0.995, progress);
    float sunMask = caDisc(r, 0.97, 0.01);
    // Como num eclipse real, o disco só existe onde cobre o sol (a mordida); inteiro só na totalidade.
    float moon = moonDisc * max(sunMask, total);
    float sunR = 0.97;
    float limb = pow(max(1.0 - r * r / (sunR * sunR), 0.0), 0.35);
    vec3 sunLight = vec3(1.0, 0.94, 0.82) * (0.55 + 0.9 * limb) * 1.4;
    float visible = sunMask * (1.0 - moonDisc);
    // Coroa: serpentinas radiais que mudam devagar, e um brilho junto à borda.
    float a = atan(q.y, q.x);
    float streamers = caFbm2(vec2(a * 2.2 + seed * 9.0, seconds * 0.03), octaves());
    float reach = 0.25 + 0.9 * streamers * streamers;
    float corona = exp(-max(r - 1.0, 0.0) / reach) * step(0.98, r) * total;
    float glow = exp(-max(r - 1.0, 0.0) / 0.08) * step(0.99, r) * total;
    // Proeminências vermelhas na borda durante a totalidade.
    float prom = smoothstep(0.62, 0.8, caNoise2(vec2(a * 9.0 + seed * 4.0, seconds * 0.05)))
        * exp(-max(r - 1.0, 0.0) / 0.035) * step(0.995, r) * total;
    // Anel de diamante: o último ponto de sol, do lado oposto ao caminho do disco, pouco antes da totalidade.
    vec2 last = -normalize(offset + vec2(1e-5, 0.0)) * sunR;
    float window = smoothstep(0.88, 0.94, progress) * (1.0 - smoothstep(0.975, 0.99, progress));
    vec2 dl = q - last;
    float diamond = (exp(-dot(dl, dl) / 0.004) * 5.0 + exp(-dot(dl, dl) / 0.05) * 1.2
        + exp(-abs(dl.y) / 0.012) * exp(-abs(dl.x) / 0.5) * 0.8 + exp(-abs(dl.x) / 0.012) * exp(-abs(dl.y) / 0.5) * 0.8)
        * window * flash;
    vec3 light = sunLight * visible + t2.rgb * (corona * 0.85 + glow * 0.9) + t3.rgb * prom * 1.6
        + vec3(1.0, 0.97, 0.9) * diamond;
    // Antes da totalidade, um leve halo do sol que ainda aparece.
    light += vec3(1.0, 0.9, 0.7) * 0.12 * exp(-max(r - 1.0, 0.0) / 0.5) * step(1.0, r)
        * (1.0 - smoothstep(0.7, 0.99, progress));
    float cover = max(moon, sunMask);
    return vec4(light * t2.w, cover * t2.w);
}

vec4 eclipseMoon(vec2 q, vec4 t1, vec4 t2, vec4 t3, vec4 t4) {
    float progress = t1.w, seed = t4.x;
    float r = length(q);
    if (r > 1.3) return vec4(0.0);
    float disc = caDisc(r, 1.0, 0.012);
    // Lua: cinza com mares escuras; a sombra da Terra (umbra 1,4 raio, penumbra suave) atravessa com o progresso.
    float maria = smoothstep(0.45, 0.7, caFbm2(q * 2.3 + seed * 11.0, octaves()));
    float shade = pow(max(1.0 - r * r, 0.0), 0.25);
    vec3 surface = vec3(0.78, 0.78, 0.76) * (1.0 - 0.35 * maria) * (0.65 + 0.35 * shade);
    vec2 center = vec2(1.0, 0.0) * (1.0 - progress) * 2.6;
    float d = length(q - center);
    float umbra = 1.0 - smoothstep(1.3, 1.45, d);
    float penumbra = 1.0 - smoothstep(1.45, 2.1, d);
    vec3 blood = t3.rgb * 0.35 * (0.7 + 0.3 * shade);
    vec3 lit = mix(surface, surface * 0.55, penumbra * (1.0 - umbra));
    lit = mix(lit, blood, umbra);
    vec3 light = lit * disc + t3.rgb * 0.06 * exp(-max(r - 1.0, 0.0) / 0.08) * step(1.0, r) * smoothstep(0.85, 1.0, progress);
    return vec4(light * t2.w, disc * t2.w);
}

vec4 rift(vec2 q, vec4 t1, vec4 t2, vec4 t3, vec4 t4) {
    float open = t1.w, seed = t4.x, seconds = t4.y, flash = t4.z;
    float x = q.x;
    if (abs(x) > 1.15 || abs(q.y) > 0.5) return vec4(0.0);
    float taper = pow(max(1.0 - x * x, 0.0), 0.7);
    float y0 = 0.09 * (caFbm2(vec2(x * 2.6 + seed * 17.0, seed), octaves()) - 0.5) * 2.0
        + 0.025 * (caNoise2(vec2(x * 15.0 + seed * 3.0, 1.0)) - 0.5);
    float wobble = 0.6 + 0.8 * caNoise2(vec2(x * 7.0 + seed * 5.0, seconds * 0.25));
    float w = open * 0.045 * taper * wobble;
    float dist = abs(q.y - y0);
    float inside = 1.0 - smoothstep(w - 0.004, w + 0.004, dist);
    inside *= step(1e-4, w);
    float flicker = 0.85 + 0.15 * sin(seconds * 13.0 + x * 6.0) * flash;
    float edge = exp(-max(dist - w, 0.0) / (0.006 + 0.02 * open)) * taper * flicker;
    // Galhos: três trincas finas que saem da principal.
    float branches = 0.0;
    for (int b = 0; b < 3; b++) {
        float fb = float(b);
        float bx = (caHash13(vec3(seed * 40.0, fb, 1.0)) - 0.5) * 1.3;
        float dir = caHash13(vec3(seed * 40.0, fb, 2.0)) < 0.5 ? -1.0 : 1.0;
        float len = 0.12 + 0.18 * caHash13(vec3(seed * 40.0, fb, 3.0));
        float along = (x - bx) * dir;
        if (along < 0.0 || along > len) continue;
        float by = y0 + along * 0.9 * (caHash13(vec3(seed * 40.0, fb, 4.0)) - 0.5) * 2.0
            + 0.015 * (caNoise2(vec2(along * 20.0, fb)) - 0.5);
        float bw = 0.004 * (1.0 - along / len);
        branches += exp(-max(abs(q.y - by) - bw, 0.0) / 0.005) * (1.0 - along / len) * open;
    }
    vec2 o = vec2(x, (q.y - y0) / max(w, 1e-3) * 0.08);
    float swirl = caFbm2(o * 6.0 + vec2(seconds * 0.12, seed * 9.0), octaves());
    vec3 voidColor = t3.rgb * (0.4 + 0.9 * swirl) + t2.rgb * 0.25 * pow(swirl, 3.0)
        + vec3(0.9) * densStars(o * 60.0 + seed * 10.0, seed) * 0.6;
    vec3 light = voidColor * inside + t2.rgb * (edge * 1.4 + branches * 1.2) * (1.0 - inside);
    return vec4(light * t2.w, inside * t2.w);
}

vec4 celestial(vec2 q, vec4 t1, vec4 t2, vec4 t3, vec4 t4, vec4 t5) {
    float progress = t1.w, tilt = t1.z, seed = t4.x;
    float r = length(q);
    bool rings = abs(tilt) > 0.01;
    if (r > (rings ? 2.4 : 1.15)) return vec4(0.0);
    // Luz: progresso 1 = cheia (luz de frente), 0,5 = metade (do lado da rotação), 0 = nova.
    float phi = 3.14159265 * (1.0 - progress);
    vec3 L = vec3(sin(phi), 0.0, cos(phi));
    vec3 light = vec3(0.0);
    float cover = 0.0;
    if (r < 1.0 + 0.01) {
        float nz = sqrt(max(1.0 - r * r, 0.0));
        vec3 n = vec3(q, nz);
        float lambert = smoothstep(-0.04, 0.3, dot(n, L));
        float lat = asin(clamp(q.y, -1.0, 1.0));
        float lon = atan(q.x, nz) + seed * 6.28 + t5.x;
        float bands = caFbm2(vec2(lat * 6.0 + seed * 3.0, lon * 0.6), octaves());
        float craters = smoothstep(0.5, 0.75, caFbm2(vec2(lon * 1.6, lat * 1.6) + seed * 13.0, octaves()));
        vec3 albedo = t2.rgb * (0.78 + 0.22 * bands) * (1.0 - 0.3 * craters) + t3.rgb * 0.18 * bands;
        float rim = pow(1.0 - nz, 3.0) * smoothstep(-0.2, 0.6, dot(normalize(vec3(q, 0.0) + 1e-5), L));
        light = albedo * (lambert * (0.25 + 0.75 * nz) + 0.025) + t3.rgb * rim * 0.9;
        cover = caDisc(r, 1.0, 0.008);
    }
    // Atmosfera fora do disco, do lado iluminado.
    float halo = exp(-max(r - 1.0, 0.0) / 0.035) * step(1.0, r) * clamp(dot(normalize(q + 1e-5), L.xy) * 0.7 + 0.5 * L.z + 0.3, 0.0, 1.0);
    light += t3.rgb * halo * 0.7;
    if (rings) {
        float s = sin(abs(tilt));
        float rho = length(vec2(q.x, q.y / max(s, 0.05)));
        if (rho > 1.3 && rho < 2.25) {
            float band = 0.55 + 0.45 * caNoise2(vec2(rho * 30.0 + seed * 7.0, 0.5));
            float gaps = smoothstep(1.3, 1.36, rho) * (1.0 - smoothstep(2.15, 2.25, rho)) * (1.0 - 0.7 * bump((rho - 1.78) / 0.02));
            float alpha = band * gaps * 0.85;
            // A metade de trás do anel fica atrás do planeta.
            bool behind = q.y * sign(tilt) > 0.0 && r < 1.0;
            if (!behind) {
                vec3 ringColor = mix(t2.rgb, t3.rgb, 0.3) * (0.35 + 0.65 * clamp(L.z * 0.5 + 0.6, 0.0, 1.0));
                light = mix(light, ringColor, alpha);
                cover = max(cover, alpha);
            }
        }
    }
    return vec4(light * t2.w, cover * t2.w);
}

vec4 aurora(vec3 d, vec3 center, float radius, vec4 t1, vec4 t2, vec4 t3, vec4 t4) {
    float activity = t1.w, seed = t4.x, seconds = t4.y;
    float span = radius * 2.0;
    float e = asin(clamp(d.y, -1.0, 1.0));
    float e0 = asin(clamp(center.y, -1.0, 1.0));
    if (e < e0 - 0.06) return vec4(0.0);
    float a = atan(-d.x, d.z), a0 = atan(-center.x, center.z);
    float da = mod(a - a0 + 3.14159265, 6.2831853) - 3.14159265;
    float band = 1.0 - smoothstep(0.32 * span, 0.5 * span, abs(da));
    if (band <= 0.0) return vec4(0.0);
    float eb = e0 + 0.045 * sin(da * 3.0 + seconds * 0.12 + seed * 6.0) + 0.02 * sin(da * 7.0 - seconds * 0.19);
    float h = e - eb;
    float height = 0.16 + 0.22 * activity;
    float vertical = smoothstep(-0.012, 0.012, h) * exp(-max(h, 0.0) / (height * 0.42));
    float fold = caFbm2(vec2(da * 5.0 + seconds * 0.05, seed * 3.0), octaves());
    float rays = pow(caNoise2(vec2(da * 70.0 + fold * 6.0 + seconds * 0.3, seed)), 2.0) * 0.8 + 0.35;
    vec3 color = mix(t2.rgb, t3.rgb, smoothstep(0.0, height, h));
    float light = vertical * rays * band * (0.25 + 0.75 * activity) * (0.6 + 0.6 * fold);
    return vec4(color * light * t2.w, 0.0);
}

// Raios ao longe: até 4 raios por camada, cada um um caminho serrilhado de 12 segmentos do alto (280 blocos acima do
// pé) até 64 blocos abaixo da câmera, com um galho; o terreno na frente esconde (profundidade). Devolve a luz e soma o
// clarão no céu em skyFlash.
vec3 distantBolts(int j, vec3 rd, float sceneDist, vec4 t2, vec4 t3, out float skyFlash) {
    vec3 light = vec3(0.0);
    skyFlash = 0.0;
    for (int b = 0; b < 4; b++) {
        vec4 bt = skyTexel(6 + b, j);
        if (bt.z <= 0.001) continue;
        vec3 foot = vec3(bt.x, -64.0, bt.y);
        vec3 top = foot + vec3(0.0, 280.0, 0.0);
        skyFlash += bt.z;
        if (caRaySegment(vec3(0.0), rd, foot, top).z > 70.0) continue;
        float seed = bt.w * 97.0;
        float core = 0.0, halo = 0.0;
        vec3 prev = top + vec3((caHash13(vec3(seed, 0.0, 1.0)) - 0.5) * 40.0, 0.0, (caHash13(vec3(seed, 0.0, 2.0)) - 0.5) * 40.0);
        vec3 fork = prev;
        vec3 drift = vec3(0.0);
        for (int k = 1; k <= 12; k++) {
            float fk = float(k);
            // Passeio aleatório: cada vértice se afasta do anterior, e o último volta ao pé.
            drift += vec3((caHash13(vec3(seed, fk, 1.0)) - 0.5) * 22.0, 0.0, (caHash13(vec3(seed, fk, 2.0)) - 0.5) * 22.0);
            vec3 p = mix(top, foot, fk / 12.0) + (k < 12 ? drift * (1.0 - fk / 13.0) : vec3(0.0))
                + vec3(0.0, (caHash13(vec3(seed, fk, 3.0)) - 0.5) * 10.0, 0.0);
            vec3 h = caRaySegment(vec3(0.0), rd, prev, p);
            float pix = max(gPix * h.x, 1e-4);
            float vis = caDepthFade(h.x, sceneDist, 8.0);
            core += caBeamCore(h.z, 0.6, pix) * vis;
            halo += caBeamHalo(h.z, 5.0, pix) * vis;
            if (k == 4) fork = p;
            prev = p;
        }
        vec3 branchEnd = fork + vec3((caHash13(vec3(seed, 9.0, 1.0)) - 0.5) * 80.0, -70.0, (caHash13(vec3(seed, 9.0, 2.0)) - 0.5) * 80.0);
        vec3 hb = caRaySegment(vec3(0.0), rd, fork, branchEnd);
        float pixb = max(gPix * hb.x, 1e-4);
        float visb = caDepthFade(hb.x, sceneDist, 8.0);
        core += caBeamCore(hb.z, 0.4, pixb) * visb * 0.7;
        halo += caBeamHalo(hb.z, 3.5, pixb) * visb * 0.5;
        light += bt.z * (t2.rgb * min(core, 1.5) * 1.6 + t3.rgb * min(halo, 2.0) * 0.55);
    }
    return light;
}

// ------------------------------------------------------------------------------- composição

void main() {
    vec2 uv = texCoord;
    vec3 rd0 = viewDir(uv);
    int count = int(CaSkyInfo.x + 0.5);
    bool skyOrig = skyAt(uv);
    // Profundidade e tamanho do pixel só para os raios ao longe (CaSkyInfo.w = 1 quando há raios no quadro).
    float sceneDist = 0.0;
    if (CaSkyInfo.w > 0.5) {
        initPixel(uv);
        sceneDist = length(scenePosAt(uv));
    }

    // 1. Lente: o desvio de cada buraco negro, um depois do outro (o mesmo do passe de atmosfera).
    vec3 rd;
    bool sky;
    vec2 suv = caSkyLensUv(uv, CaSkyLens0, CaSkyLens1, CaSkyLensExt, rd, sky);
    bool elements = caElementsOn();
    if (elements) gElements = caElementsAt(suv);
    // Céu desviado continua céu: onde o desvio cairia num morro, entra o céu do jogo naquela direção.
    // 0.14.0: com fundos, o céu de preenchimento é o fundo sobre o céu do jogo (skyFill).
    vec3 color = skyOrig && !skyAt(suv) ? skyFill(rd) : texture(DiffuseSampler, suv).rgb;

    // 2. Sol e lua do jogo, onde uma camada pede.
    if (sky) {
        bool backdrop = caBackdropOn();
        if (backdrop) {
            // Só a parte do sol e da lua que os fundos não cobrem ainda está na tela.
            float coverage;
            caBackdropOver(vec3(0.0), rd, coverage);
            gVanillaVisible = 1.0 - coverage;
        }
        // 0.14.0 (fase 3): o que os objetos do céu cobrem também esconde o sol e a lua do jogo.
        if (elements) gVanillaVisible *= 1.0 - gElements.a;
        color = removeVanilla(color, rd);
        if (CaSkyAtmoA.w > 0.5) {
            vec3 delta = backdrop
                ? caAtmoSkyDeltaOver(rd, CaSkyVanillaSky, CaSkyVanillaFog, CaSkyMisc.x, CaSkyAtmoB.x > 0.5,
                    CaSkyAtmoTop, CaSkyAtmoHorizon, CaSkyAtmoFog, CaSkyAtmoA.x, CaSkyAtmoA.y, CaSkyAtmoA.z, GTime)
                : caAtmoSkyDelta(rd, CaSkyVanillaSky, CaSkyVanillaFog, CaSkyMisc.x, CaSkyAtmoB.x > 0.5,
                    CaSkyAtmoTop, CaSkyAtmoHorizon, CaSkyAtmoFog, CaSkyAtmoA.x, CaSkyAtmoA.y, CaSkyAtmoA.z, GTime);
            // 0.14.0 (fase 3): os objetos já se cobriram com o véu; a diferença vale para o céu que eles deixam ver.
            if (elements) delta *= 1.0 - gElements.a;
            color = max(color + delta, vec3(0.0));
        }
    }

    // 3. Escurecimento do mundo (e do céu do jogo); as camadas, depois, continuam acesas.
    // 0.14.0 (fase 3): os objetos do céu aplicaram a própria reação ao escurecimento (SkyDarkening) e ficam como estão.
    if (elements && sky) color = color * CaSkyInfo.y + gElements.rgb * (1.0 - CaSkyInfo.y);
    else color *= CaSkyInfo.y;

    // 4. Camadas, da menos importante (última linha) para a mais importante (primeira).
    for (int j = 7; j >= 0; j--) {
        if (j >= count) continue;
        vec4 t0 = skyTexel(0, j);
        vec4 t1 = skyTexel(1, j);
        int type = int(t1.x + 0.5);
        // O buraco negro é a própria lente: desenhado na direção sem desvio, só onde o pixel é céu de verdade.
        bool hole = type == 0;
        vec3 d = hole ? rd0 : rd;
        float radius = max(t0.w, 1e-5);
        if (type != 6) {
            if (hole ? !skyOrig : !sky) continue;
            // Recorte rápido pelo ângulo até o centro antes de ler o resto da linha e montar a base.
            float reach = type == 2 ? 6.0 : (type == 4 ? 2.4 : (type == 1 ? 1.7 : 3.2));
            if (type != 5 && caAngle(d, t0.xyz) > radius * reach) continue;
        }
        vec4 t2 = skyTexel(2, j);
        vec4 t3 = skyTexel(3, j);
        vec4 t4 = skyTexel(4, j);
        vec4 t5 = skyTexel(5, j);
        if (type == 6) {
            // Raios ao longe: em qualquer pixel (o terreno na frente esconde pela profundidade) e clarão no céu.
            float skyFlash;
            color += distantBolts(j, rd0, sceneDist, t2, t3, skyFlash) * t2.w;
            if (skyOrig) color += t3.rgb * 0.12 * min(skyFlash, 1.5) * t4.z * t2.w * (0.4 + 0.6 * max(rd0.y, 0.0));
            continue;
        }
        vec4 layer = vec4(0.0);
        if (type == 5) {
            layer = aurora(d, t0.xyz, radius, t1, t2, t3, t4);
        } else {
            vec2 q = caSkyLocal(d, caSkyBasis(t0.xyz, t1.y)) / radius;
            if (type == 0) layer = blackHole(q, t1, t2, t3, t4, t5);
            else if (type == 1) layer = portal(q, t1, t2, t3, t4);
            else if (type == 2) layer = t4.w > 1.5 ? eclipseMoon(q, t1, t2, t3, t4) : eclipseSun(q, t1, t2, t3, t4);
            else if (type == 3) layer = rift(q, t1, t2, t3, t4);
            else layer = celestial(q, t1, t2, t3, t4, t5);
        }
        color = color * (1.0 - clamp(layer.a, 0.0, 1.0)) + layer.rgb;
    }
    fragColor = vec4(color, 1.0);
}
