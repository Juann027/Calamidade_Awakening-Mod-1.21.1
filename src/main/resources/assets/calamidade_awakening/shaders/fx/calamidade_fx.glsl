// calamidade_fx.glsl — biblioteca de GLSL do Calamidade_Awakening (motor 0.9.0, biblioteca versão 2).
//
// USO: numa linha própria de um shader fx, depois dos uniforms:
//     #include "calamidade_fx.glsl"
// A RimuLib 1.3.0 (ShaderProgram.readSource, conferido com javap -c) troca essa linha pelo arquivo
// assets/<namespace do shader>/shaders/fx/calamidade_fx.glsl e, se não achar, por assets/rimulib/shaders/fx/.
// Não há recursão nem busca no namespace do motor: um addon COPIA este arquivo para o próprio namespace
// (scripts/novo_efeito.py faz isso; o checkShaders confere que as cópias do repositório são idênticas).
//
// REGRAS: todas as funções começam com "ca" para não colidir com common.glsl da RimuLib (que já vem antes deste
// arquivo). Distâncias em blocos; posições relativas à câmera (o espaço de scenePosAt e de post.center()).
// GLSL portátil: só literais float e funções comuns a GLSL 1.50 e GLSL ES 3.00. scripts/testar_glsl.cjs executa
// cada função numa GPU (WebGL2) e compara com valores esperados.

#define CA_FX_VERSION 2

// ------------------------------------------------------------------------------------------- interseções

// Raio contra caixa alinhada aos eixos (método das placas).
// ro: origem do raio; rd: direção NORMALIZADA; bmin, bmax: cantos da caixa (bmin < bmax), mesmo espaço de ro.
// Devolve (tEntrada, tSaída) em blocos ao longo do raio, com tEntrada = 0 se ro está dentro da caixa;
// vec2(-1.0) quando o raio erra ou a caixa está inteira atrás da origem.
// Ex.: vec2 t = caRayBox(vec3(0.0), viewDir(texCoord), centro - 8.0, centro + 8.0);
vec2 caRayBox(vec3 ro, vec3 rd, vec3 bmin, vec3 bmax) {
    vec3 s = vec3(rd.x < 0.0 ? -1.0 : 1.0, rd.y < 0.0 ? -1.0 : 1.0, rd.z < 0.0 ? -1.0 : 1.0);
    vec3 inv = 1.0 / (s * max(abs(rd), vec3(1e-8)));
    vec3 t0 = (bmin - ro) * inv;
    vec3 t1 = (bmax - ro) * inv;
    vec3 tmin = min(t0, t1);
    vec3 tmax = max(t0, t1);
    float tNear = max(max(tmin.x, tmin.y), tmin.z);
    float tFar = min(min(tmax.x, tmax.y), tmax.z);
    if (tFar < max(tNear, 0.0)) return vec2(-1.0);
    return vec2(max(tNear, 0.0), tFar);
}

// Normal externa da caixa num ponto p da sua superfície (eixo em que p está mais perto de uma face).
vec3 caBoxNormal(vec3 p, vec3 bmin, vec3 bmax) {
    vec3 c = (bmin + bmax) * 0.5;
    vec3 q = (p - c) / max((bmax - bmin) * 0.5, vec3(1e-6));
    vec3 a = abs(q);
    if (a.x >= a.y && a.x >= a.z) return vec3(q.x < 0.0 ? -1.0 : 1.0, 0.0, 0.0);
    if (a.y >= a.z) return vec3(0.0, q.y < 0.0 ? -1.0 : 1.0, 0.0);
    return vec3(0.0, 0.0, q.z < 0.0 ? -1.0 : 1.0);
}

// Raio contra esfera. rd NORMALIZADA. Devolve (t0, t1), t0 <= t1, em blocos; t0 = 0 se ro está dentro;
// vec2(-1.0) quando erra ou a esfera está atrás. Raio tangente: t0 == t1.
// Ex.: vec2 t = caRaySphere(vec3(0.0), viewDir(texCoord), post_center, 6.0);
vec2 caRaySphere(vec3 ro, vec3 rd, vec3 center, float radius) {
    vec3 oc = ro - center;
    float b = dot(oc, rd);
    float c = dot(oc, oc) - radius * radius;
    float h = b * b - c;
    if (h < 0.0) return vec2(-1.0);
    h = sqrt(h);
    float t1 = -b + h;
    if (t1 < 0.0) return vec2(-1.0);
    return vec2(max(-b - h, 0.0), t1);
}

// ------------------------------------------------------------------------------------------- brilho de segmento

// Brilho de um segmento de luz AB visto pelo raio (ro, rd normalizada): x = núcleo, y = halo, ambos 0..1.
// core, halo: meias-larguras em blocos. pixelAngle: tamanho angular de um pixel (chame initPixel(texCoord) do
// common.glsl e passe gPix). maxT: distância da cena nesse pixel (length(scenePosAt(texCoord))), para o
// segmento sumir atrás do terreno.
// Largura mínima por pixel: longe da câmera o núcleo nunca fica mais fino que um pixel (não serrilha nem some);
// em troca, sua intensidade cai na proporção core / largura_real, para o feixe não engordar com a distância.
vec2 caSegmentGlow(vec3 ro, vec3 rd, vec3 a, vec3 b, float core, float halo, float pixelAngle, float maxT) {
    vec3 ba = b - a;
    vec3 oa = ro - a;
    float baba = dot(ba, ba);
    float bard = dot(ba, rd);
    float denom = baba - bard * bard;
    float s = denom > 1e-6 ? clamp((dot(ba, oa) - bard * dot(rd, oa)) / denom, 0.0, 1.0) : 0.0;
    vec3 q = a + ba * s;
    float t = max(dot(q - ro, rd), 0.0);
    if (t > maxT) return vec2(0.0);
    float d = length(ro + rd * t - q);
    float pix = max(pixelAngle * t, 1e-5);
    float w = max(core, pix);
    float coreV = (1.0 - smoothstep(max(w - pix, 0.0), w + pix, d)) * (core / w);
    float h = max(halo, pix);
    float haloV = h * h / (d * d + h * h) * (halo / h);
    return vec2(coreV, haloV);
}

// ------------------------------------------------------------------------------------------- feixes (versão 2)

// Pontos mais próximos entre o raio (ro, rd NORMALIZADA) e o segmento AB.
// Devolve vec3(t, s, d): t = distância ao longo do raio até o ponto mais próximo (>= 0, blocos), s = posição no
// segmento (0 em A, 1 em B) e d = distância entre os dois pontos (blocos). Raio paralelo ao segmento: s é a projeção
// da origem do raio. A == B: s = 0 (um ponto).
// Ex.: vec3 r = caRaySegment(vec3(0.0), viewDir(texCoord), inicio, fim);  // r.z < largura: o pixel está no feixe
vec3 caRaySegment(vec3 ro, vec3 rd, vec3 a, vec3 b) {
    vec3 ba = b - a;
    vec3 oa = ro - a;
    float baba = dot(ba, ba);
    float bard = dot(ba, rd);
    float denom = baba - bard * bard;
    float s = 0.0;
    if (denom > 1e-6 * max(baba, 1e-6)) s = clamp((dot(ba, oa) - bard * dot(rd, oa)) / denom, 0.0, 1.0);
    else if (baba > 1e-12) s = clamp(dot(oa, ba) / baba, 0.0, 1.0);
    vec3 q = a + ba * s;
    float t = max(dot(q - ro, rd), 0.0);
    return vec3(t, s, length(ro + rd * t - q));
}

// Núcleo de um feixe: 1 dentro da meia-largura halfWidth (blocos) a uma distância d (blocos) do eixo, borda suave
// de um pixel. pixelWorld = tamanho do pixel em blocos naquela distância (gPix * t). Largura mínima de um pixel:
// quando o núcleo é mais fino, ele fica com um pixel e a intensidade cai na proporção halfWidth / pixel.
float caBeamCore(float d, float halfWidth, float pixelWorld) {
    if (halfWidth <= 0.0) return 0.0;
    float px = max(pixelWorld, 1e-5);
    float w = max(halfWidth, px);
    return (1.0 - smoothstep(max(w - px, 0.0), w + px, d)) * (halfWidth / w);
}

// Halo de um feixe: h² / (d² + h²) com h = meia-largura (blocos), igual a 1 no eixo e 0,5 em d = h, cortado de 2,5h a
// 5h (vários feixes não acendem a tela inteira). Mesma largura mínima de um pixel e compensação do núcleo.
float caBeamHalo(float d, float halfWidth, float pixelWorld) {
    if (halfWidth <= 0.0) return 0.0;
    float h = max(halfWidth, max(pixelWorld, 1e-5));
    return h * h / (d * d + h * h) * (halfWidth / h) * (1.0 - smoothstep(2.5 * h, 5.0 * h, d));
}

// Trem de pulsos ao longo de um feixe: x = posição em blocos a partir do início, já deslocada pelo movimento
// (x = s * comprimento - velocidade * tempo); spacing = distância entre centros (blocos); size = comprimento de cada
// pulso (blocos). Centros em (k + 0,5) * spacing. Devolve 0..1: 1 no centro, exp(-2) a size/2 dele; 0 se size ou
// spacing <= 0.
float caPulseTrain(float x, float spacing, float size) {
    if (size <= 0.0 || spacing <= 0.0) return 0.0;
    float local = (fract(x / spacing) - 0.5) * spacing;
    float u = local / (0.5 * size);
    return exp(-2.0 * u * u);
}

// Afinamento nas pontas: 0 nas pontas do feixe, 1 a partir de ends blocos delas (smoothstep). s: 0..1 no feixe;
// len: comprimento (blocos). Multiplique ondulação e ruído laterais por ele para as pontas ficarem presas.
float caBeamTaper(float s, float len, float ends) {
    if (ends <= 0.0) return 1.0;
    return smoothstep(0.0, ends, s * len) * smoothstep(0.0, ends, (1.0 - s) * len);
}

// Oclusão pela profundidade: 1 quando o ponto do efeito (distância t ao longo do raio) está na frente da cena
// (sceneDist = length(scenePosAt(texCoord))), caindo a 0 quando está soft blocos atrás dela. Um feixe que entra no
// chão some suave; um feixe atrás de um morro some.
float caDepthFade(float t, float sceneDist, float soft) {
    float x = clamp((t - sceneDist) / max(soft, 1e-3), 0.0, 1.0);
    return 1.0 - x * x * (3.0 - 2.0 * x);
}

// ------------------------------------------------------------------------------------------- grades

// Linhas de grade num plano. p: coordenadas no plano (blocos); cell: tamanho da célula (blocos);
// width: meia-largura da linha (blocos); pixelWorld: tamanho do pixel em blocos naquela profundidade
// (gPix * distância). Devolve 0..1. Linhas mais finas que um pixel ficam com um pixel e mais fracas, e a
// grade inteira some quando o pixel chega a meia célula (sem moiré).
float caGrid2(vec2 p, float cell, float width, float pixelWorld) {
    vec2 q = abs(fract(p / cell + 0.5) - 0.5) * cell;
    float d = min(q.x, q.y);
    float px = max(pixelWorld, 1e-5);
    float w = max(width, px);
    float line = (1.0 - smoothstep(max(w - px, 0.0), w + px, d)) * (width / w);
    return line * (1.0 - smoothstep(0.25 * cell, 0.5 * cell, px));
}

// Grade em coordenadas de mundo sobre qualquer superfície. p: ponto num referencial FIXO do mundo (por exemplo
// scenePosAt(texCoord) - centro do efeito, para a grade não deslizar com a câmera); n: normal aproximada da
// superfície (a grade usa os dois eixos perpendiculares ao eixo dominante de n, então faces de caixa não acendem
// inteiras). Demais parâmetros como em caGrid2.
float caWorldGrid(vec3 p, vec3 n, float cell, float width, float pixelWorld) {
    vec3 a = abs(n);
    vec2 uv = a.x >= a.y && a.x >= a.z ? p.yz : (a.y >= a.z ? p.xz : p.xy);
    return caGrid2(uv, cell, width, pixelWorld);
}

// ------------------------------------------------------------------------------------------- cor

// Matiz arco-íris: h em voltas (0 vermelho, 1/3 verde, 2/3 azul, 1 vermelho de novo), saturação e valor totais.
vec3 caRainbow(float h) {
    return clamp(abs(fract(h + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

// Luminância relativa (pesos Rec. 709).
float caLuma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// ------------------------------------------------------------------------------------------- tempo

// Envelope suave: 0 antes de a, sobe (smoothstep) até b, 1 até c, desce até d, 0 depois.
// Requer a <= b <= c <= d; a == b ou c == d viram degraus. Use em ticks (post.age()) ou em qualquer unidade.
float caEnvelope(float x, float a, float b, float c, float d) {
    float up = b > a ? smoothstep(a, b, x) : step(a, x);
    float down = d > c ? 1.0 - smoothstep(c, d, x) : 1.0 - step(d, x);
    return up * down;
}

// Onda que sai de uma fonte: anel na distância speed * time, meia-largura width (blocos), que some ao se
// aproximar de maxDist. dist: distância do pixel à fonte (blocos); time: tempo desde a emissão (ticks, com
// speed em blocos por tick). Devolve 0..1, e 0 antes da emissão (time < 0).
// Ex.: float anel = caDistanceWave(length(p.xz), RippleState.x, 0.35, 0.6, 24.0);
float caDistanceWave(float dist, float time, float speed, float width, float maxDist) {
    if (time < 0.0) return 0.0;
    float front = speed * time;
    float x = (dist - front) / max(width, 1e-4);
    return exp(-x * x) * (1.0 - smoothstep(maxDist * 0.6, maxDist, front));
}

// ------------------------------------------------------------------------------------------- céu

float caHash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

// Campo de estrelas procedural na direção dir (normalizada), só acima do horizonte. O hemisfério de cima vira um
// losango contínuo (mapa octaédrico) dividido em células de ~0,35 grau; uma célula em 40 tem uma estrela, com o
// centro sorteado longe das bordas (a estrela nunca é cortada) e raio de ~0,1 grau, cintilando com seconds.
// Devolve 0..1. O passe de atmosfera do motor usa esta função.
float caStars(vec3 dir, float seconds) {
    if (dir.y <= 0.0) return 0.0;
    vec2 uv = dir.xz / (abs(dir.x) + abs(dir.y) + abs(dir.z)) * 300.0;
    vec2 cell = floor(uv);
    float h = caHash13(vec3(cell, 7.0));
    if (h < 0.975) return 0.0;
    vec2 centro = cell + 0.3 + 0.4 * vec2(caHash13(vec3(cell, 17.0)), caHash13(vec3(cell, 31.0)));
    float core = 1.0 - smoothstep(0.05, 0.3, length(uv - centro));
    float twinkle = 0.75 + 0.25 * sin(seconds * (1.5 + 5.0 * caHash13(vec3(cell, 5.0))) + h * 91.0);
    return core * twinkle * (0.55 + 0.45 * (h - 0.975) / 0.025);
}
