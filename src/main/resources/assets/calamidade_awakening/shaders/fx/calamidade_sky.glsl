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
