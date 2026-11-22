// calamidade_sky.glsl — interno do motor (0.12.0): a lente gravitacional dos buracos negros, igual nos passes "sky" e
// "atmosphere". Depende do common.glsl da RimuLib (viewDir, project, skyAt, scenePosAt) e de calamidade_fx.glsl
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
