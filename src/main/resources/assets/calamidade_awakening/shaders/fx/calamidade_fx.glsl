// calamidade_fx.glsl — biblioteca de GLSL do Calamidade_Awakening (motor 0.8.0, biblioteca versão 1).
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
// GLSL portátil: só literais float e funções comuns a GLSL 1.50 e GLSL ES 3.00. scripts/testar_glsl.mjs executa
// cada função numa GPU (WebGL2) e compara com valores esperados.

#define CA_FX_VERSION 1

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

// Campo de estrelas procedural na direção dir (normalizada): células de ~0,3 grau, uma em ~70 com estrela,
// cintilando com seconds. Devolve 0..1. O passe de atmosfera do motor usa esta função.
float caStars(vec3 dir, float seconds) {
    vec3 p = dir * 180.0;
    vec3 cell = floor(p);
    float h = caHash13(cell);
    if (h < 0.986) return 0.0;
    vec3 jitter = vec3(caHash13(cell + 17.1), caHash13(cell + 31.7), caHash13(cell + 47.3)) * 0.6 + 0.2;
    float d = length(p - (cell + jitter));
    float core = max(0.0, 1.0 - d / 0.38);
    float twinkle = 0.7 + 0.3 * sin(seconds * (1.5 + 5.0 * caHash13(cell + 5.3)) + h * 91.0);
    return core * core * twinkle * (0.4 + 0.6 * (h - 0.986) / 0.014);
}
