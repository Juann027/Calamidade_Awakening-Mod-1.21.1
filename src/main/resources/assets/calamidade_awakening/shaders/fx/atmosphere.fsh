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
uniform vec4 CaAtmoC;           // x: distância de renderização (blocos)

// Céu que o jogo pinta: disco 16 blocos acima, com névoa FOG_SKY (cilíndrica, de 0 à distância de renderização).
vec3 atmoVanillaSky(vec3 dir) {
    if (dir.y <= 0.0) return CaAtmoVanillaFog;
    float horizontal = 16.0 * sqrt(max(1.0 - dir.y * dir.y, 0.0)) / max(dir.y, 1e-3);
    return mix(CaAtmoVanillaSky, CaAtmoVanillaFog, smoothstep(0.0, max(CaAtmoC.x, 16.0), horizontal));
}

// Gradiente da atmosfera: horizonte até ~37 graus de elevação, zênite acima.
vec3 atmoGradient(vec3 dir) {
    return mix(CaAtmoSkyHorizon, CaAtmoSkyTop, smoothstep(0.0, 0.6, dir.y));
}

// Névoa da atmosfera sobre o céu: máxima no horizonte e abaixo dele, some até ~27 graus.
float atmoHorizon(vec3 dir) {
    return 1.0 - smoothstep(0.0, 0.45, dir.y);
}

// Campo de estrelas procedural: células de ~0,3 grau sobre a esfera, uma em ~70 com estrela, cintilando.
float atmoStars(vec3 dir, float seconds) {
    vec3 p = dir * 180.0;
    vec3 cell = floor(p);
    float h = hash13(cell);
    if (h < 0.986) return 0.0;
    vec3 jitter = vec3(hash13(cell + 17.1), hash13(cell + 31.7), hash13(cell + 47.3)) * 0.6 + 0.2;
    float d = length(p - (cell + jitter));
    float core = max(0.0, 1.0 - d / 0.38);
    float twinkle = 0.7 + 0.3 * sin(seconds * (1.5 + 5.0 * hash13(cell + 5.3)) + h * 91.0);
    return core * core * twinkle * (0.4 + 0.6 * (h - 0.986) / 0.014);
}

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    if (skyAt(texCoord)) {
        vec3 dir = viewDir(texCoord);
        vec3 base = atmoVanillaSky(dir);
        vec3 target = mix(base, atmoGradient(dir), CaAtmoA.x);
        if (CaAtmoB.z > 0.0 && dir.y > -0.05)
            target += vec3(0.85, 0.9, 1.0) * atmoStars(dir, GTime) * CaAtmoB.z;
        target = mix(target, CaAtmoFogColor, CaAtmoA.y * atmoHorizon(dir));
        color = max(color + (target - base), vec3(0.0));
    } else if (CaAtmoB.w > 0.5) {
        float distance = length(scenePosAt(texCoord));
        color = mix(color, CaAtmoFogColor, CaAtmoA.y * smoothstep(CaAtmoA.z, CaAtmoA.w, distance));
    }
    color *= CaAtmoB.x;
    color = mix(color, color * CaAtmoTint, CaAtmoB.y);
    fragColor = vec4(color, 1.0);
}
