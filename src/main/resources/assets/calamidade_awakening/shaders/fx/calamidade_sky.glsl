// calamidade_sky.glsl — interno do motor (0.12.0): a lente gravitacional dos buracos negros e o céu da atmosfera,
// iguais nos passes "sky" e "atmosphere". Depende do common.glsl da RimuLib (viewDir, project, skyAt, scenePosAt) e de calamidade_fx.glsl
// (incluído antes). Não é para addons: a lente vem das camadas da Sky API.
//
// Cada lente: vec4(direção, raio de Einstein em radianos); ext = (alcance da lente 0, alcance da lente 1, lentes, 0).

// De onde vem a cor que este pixel mostra (uv), a direção desviada (dir) e se o pixel mostra céu (sky). O terreno a
// menos de ~12 blocos não entorta (a lente está longe, atrás dele); o terreno distante entorta um terço do céu e pode
// mostrar o céu de trás. Um pixel de céu continua céu (a luz de trás do buraco vem do céu, não de um morro na frente): onde o desvio
// cairia num morro, o passe "sky" usa o céu do jogo naquela direção. Os dois passes chamam esta função com os mesmos
// dados, então o passe de atmosfera trata como céu exatamente os pixels que mostram céu.
vec2 caSkyLensUv(vec2 uv, vec4 lens0, vec4 lens1, vec4 ext, out vec3 dir, out bool sky) {
    vec3 rd0 = viewDir(uv);
    dir = rd0;
    bool skyOrig = skyAt(uv);
    sky = skyOrig;
    int count = int(ext.z + 0.5);
    bool lensed = false;
    for (int i = 0; i < 2; i++) {
        if (i >= count) break;
        vec4 lens = i == 0 ? lens0 : lens1;
        float extent = i == 0 ? ext.x : ext.y;
        mat3 basis = caSkyBasis(lens.xyz, 0.0);
        vec2 local = caSkyLocal(dir, basis);
        if (length(local) >= extent) continue;
        dir = caSkyFromLocal(caLensSource(local, lens.w, extent), basis);
        lensed = true;
    }
    if (!lensed) return uv;
    vec3 pj = project(dir * 1000.0);
    vec2 target = pj.z > 0.0 ? clamp(pj.xy, vec2(0.001), vec2(0.999)) : uv;
    // Terreno: um terço do desvio, sumindo para perto de quem olha: o relevo ondula em volta do buraco sem virar um
    // anel recortado de morros no céu.
    float near = skyOrig ? 1.0 : 0.35 * smoothstep(12.0, 64.0, length(scenePosAt(uv)));
    if (near < 1.0) dir = normalize(mix(rd0, dir, near));
    vec2 result = mix(uv, target, near);
    sky = skyOrig || skyAt(result);
    return result;
}

// Céu da Atmosphere numa direção, como diferença a somar ao que está na tela: o alvo (gradiente misturado ao céu do
// jogo, estrelas, névoa no horizonte) menos o que o jogo pintou ali (a estimativa caVanillaSky, ou só a cor de fundo,
// a névoa, quando o perfil da dimensão tirou o céu do jogo). Somar a diferença mantém o sol, a lua e as nuvens. O passe
// "sky" aplica isto antes de escurecer o mundo e de pintar as camadas (assim um disco negro continua negro e a coroa
// do eclipse não perde brilho); sem passe "sky" no quadro, o passe de atmosfera aplica.
vec3 caAtmoSkyDelta(vec3 dir, vec3 vanillaSky, vec3 vanillaFog, float renderDistance, bool replaced, vec3 top,
                    vec3 horizon, vec3 fogColor, float blend, float fogDensity, float stars, float seconds) {
    vec3 base = caVanillaSky(dir, vanillaSky, vanillaFog, renderDistance);
    vec3 target = mix(base, mix(horizon, top, smoothstep(0.0, 0.6, dir.y)), blend);
    if (replaced) base = vanillaFog;
    if (stars > 0.0 && dir.y > -0.05) target += vec3(0.85, 0.9, 1.0) * caStars(dir, seconds) * stars;
    target = mix(target, fogColor, fogDensity * (1.0 - smoothstep(0.0, 0.45, dir.y)));
    return target - base;
}

// ------------------------------------------------------------------------------- fundos de céu (0.14.0, Backdrop)
// Os fundos são desenhados como geometria logo depois do céu do jogo (AFTER_SKY), antes destes passes: a tela já os
// mostra. Os passes só precisam saber a cor e a cobertura de cada fundo numa direção, para compor com eles o céu da
// Atmosphere, o apagamento do sol e da lua do jogo e o preenchimento da lente. As consultas são as do BackdropMath
// (Java), com a mesma amostragem sem filtro da geometria (texelFetch = vizinho mais próximo).
// Slot 0 é o de baixo, slot 1 o de cima. CaBdN: x tipo (0 nenhum, 1 cubemap, 2 panorama ou gradiente), y opacidade,
// z giro (rad), w livre. Sem fundo, CaBd0.x = CaBd1.x = 0 e nada disto roda: os passes seguem o caminho da 0.12.0.
uniform sampler2D CaBdTex0;
uniform sampler2D CaBdTex1;
uniform vec4 CaBd0;
uniform vec4 CaBd1;
uniform vec3 CaBdTint0;
uniform vec3 CaBdTint1;

bool caBackdropOn() {
    return CaBd0.x > 0.5 || CaBd1.x > 0.5;
}

// BackdropMath.cubeUv: face pelo maior eixo (empate: x, depois y), coordenadas locais e célula do atlas 3 × 2.
vec2 caCubeUv(vec3 d) {
    vec3 a = abs(d);
    float face, u, v;
    if (a.x >= a.y && a.x >= a.z) {
        if (d.x > 0.0) { face = 1.0; u = d.z / a.x; v = -d.y / a.x; }
        else { face = 3.0; u = -d.z / a.x; v = -d.y / a.x; }
    } else if (a.y >= a.z) {
        if (d.y > 0.0) { face = 4.0; u = d.x / a.y; v = d.z / a.y; }
        else { face = 5.0; u = d.x / a.y; v = -d.z / a.y; }
    } else {
        if (d.z > 0.0) { face = 2.0; u = -d.x / a.z; v = -d.y / a.z; }
        else { face = 0.0; u = d.x / a.z; v = -d.y / a.z; }
    }
    vec2 local = clamp(vec2(u, v) * 0.5 + 0.5, 0.0, 1.0);
    return vec2((mod(face, 3.0) + local.x) / 3.0, (floor(face / 3.0) + local.y) / 2.0);
}

// BackdropMath.panoramaUv: sul no meio, oeste à direita, zênite em cima.
vec2 caPanoramaUv(vec3 d) {
    vec3 n = normalize(d);
    float u = 0.5 + atan(-n.x, n.z) / 6.28318530718;
    return vec2(u - floor(u), 0.5 - asin(clamp(n.y, -1.0, 1.0)) / 3.14159265359);
}

// Texel de um fundo numa direção do mundo: gira de volta pelo giro do fundo (BackdropMath.turn(d, -giro)).
vec4 caBackdropTexel(sampler2D tex, vec4 info, vec3 d) {
    float c = cos(-info.z), s = sin(-info.z);
    vec3 t = vec3(d.x * c - d.z * s, d.y, d.x * s + d.z * c);
    vec2 uv = info.x < 1.5 ? caCubeUv(t) : caPanoramaUv(t);
    ivec2 size = textureSize(tex, 0);
    return texelFetch(tex, clamp(ivec2(uv * vec2(size)), ivec2(0), size - 1), 0);
}

// A cor que a tela teria com os fundos desenhados sobre "below" na direção d, como a geometria faz (mistura por
// alfa: textura × opacidade); coverage recebe a cobertura combinada, 1 - (1 - a0)(1 - a1).
vec3 caBackdropOver(vec3 below, vec3 d, out float coverage) {
    vec3 color = below;
    float clear = 1.0;
    if (CaBd0.x > 0.5) {
        vec4 t = caBackdropTexel(CaBdTex0, CaBd0, d);
        float a = clamp(t.a * CaBd0.y, 0.0, 1.0);
        color = mix(color, t.rgb * CaBdTint0, a);
        clear *= 1.0 - a;
    }
    if (CaBd1.x > 0.5) {
        vec4 t = caBackdropTexel(CaBdTex1, CaBd1, d);
        float a = clamp(t.a * CaBd1.y, 0.0, 1.0);
        color = mix(color, t.rgb * CaBdTint1, a);
        clear *= 1.0 - a;
    }
    coverage = 1.0 - clear;
    return color;
}

// caAtmoSkyDelta com fundos: o que estava "debaixo" da atmosfera passa a ser o fundo composto sobre a estimativa do
// céu do jogo (e sobre a névoa, quando o perfil tirou o céu do jogo). Com o céu do jogo estimado exatamente, o
// resultado na tela é mix(tela, gradiente, blend) + estrelas + névoa no horizonte, com ou sem fundo, opaco ou parcial:
// a atmosfera tinge o céu que aparece, e o que ficou por cima do fundo (nuvens, sol e lua redesenhados) continua.
vec3 caAtmoSkyDeltaOver(vec3 dir, vec3 vanillaSky, vec3 vanillaFog, float renderDistance, bool replaced, vec3 top,
                        vec3 horizon, vec3 fogColor, float blend, float fogDensity, float stars, float seconds) {
    float coverage;
    vec3 base = caBackdropOver(caVanillaSky(dir, vanillaSky, vanillaFog, renderDistance), dir, coverage);
    vec3 target = mix(base, mix(horizon, top, smoothstep(0.0, 0.6, dir.y)), blend);
    if (replaced) base = caBackdropOver(vanillaFog, dir, coverage);
    if (stars > 0.0 && dir.y > -0.05) target += vec3(0.85, 0.9, 1.0) * caStars(dir, seconds) * stars;
    target = mix(target, fogColor, fogDensity * (1.0 - smoothstep(0.0, 0.45, dir.y)));
    return target - base;
}

// ------------------------------------------------------------------------------- objetos do céu (0.14.0, fase 3)
// Estrelas, constelações e nebulosas (e, depois, imagens) são desenhadas em AFTER_SKY, depois dos fundos, em alfa
// pré-multiplicado (sky_objects.glsl). Quando um destes passes roda no quadro, o motor desenha os mesmos objetos uma
// segunda vez numa camada de cobertura do tamanho da tela (CaElTex, RGBA8, limpa em zero): rgb = a luz que os objetos
// trouxeram, a = a cobertura combinada, sem o terreno na frente. Cada objeto já aplicou a própria reação ao véu da
// atmosfera e ao escurecimento; os passes usam a camada para não aplicar de novo:
//   atmosfera: a diferença do céu vale só para o que os objetos deixaram ver (× (1 − a));
//   escurecimento: tela × exposição + rgb × (1 − exposição) (os objetos ficam como se desenharam);
//   sol e lua do jogo: a parte visível também é × (1 − a);
//   preenchimento da lente sobre um morro: o céu estimado com os objetos por cima (a camada não tem terreno).
// Sem objetos, CaEl.x = 0 e nada disto roda: os passes seguem o caminho da 0.14.0 (fase 2).
uniform sampler2D CaElTex;
uniform vec4 CaEl;          // x: 1 com a camada de cobertura deste quadro

bool caElementsOn() {
    return CaEl.x > 0.5;
}

// Luz e cobertura dos objetos do céu no pixel uv da tela (vizinho mais próximo; a camada tem o tamanho da tela).
vec4 caElementsAt(vec2 uv) {
    ivec2 size = textureSize(CaElTex, 0);
    return texelFetch(CaElTex, clamp(ivec2(uv * vec2(size)), ivec2(0), size - 1), 0);
}

// 0.14.0 (fase 4): camada de frente. Imagens e impostors com layer(FRONT) não entram na tela nem na camada de cobertura
// quando o passe sky roda: vão para esta camada (CaElFront, RGBA8, limpa em zero, mesma mistura pré-multiplicada), que o
// passe sky compõe no fim, depois das camadas procedurais, só nos pixels de céu e sem lente (na frente de um portal
// ou de um buraco negro). O passe de atmosfera não a vê: com o passe sky no quadro, o céu da
// atmosfera é pintado dentro dele, antes desta composição. É composição visual do céu, não profundidade física. CaEl.y = 1 quando ela foi desenhada no quadro.
uniform sampler2D CaElFront;

bool caFrontOn() {
    return CaEl.y > 0.5;
}

vec4 caFrontAt(vec2 uv) {
    ivec2 size = textureSize(CaElFront, 0);
    return texelFetch(CaElFront, clamp(ivec2(uv * vec2(size)), ivec2(0), size - 1), 0);
}
