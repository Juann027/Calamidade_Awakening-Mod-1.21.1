# GLSL para efeitos do Calamidade_Awakening

Os passes de pós-processamento (`post.pass("ns:programa", ...)`, shaders de cartão e os do motor) são shaders de fragmento em tela cheia, compilados pela RimuLib 1.3.0. Este guia diz o que já vem pronto, como incluir a biblioteca do Calamidade e as convenções de espaço e unidade.

## Como a RimuLib monta o shader

Conferido com `javap -c` em `cyou.rimuru.rimulib.mc1211.client.render.ShaderProgram` (RimuLib 1.3.0):

1. O arquivo é `assets/<namespace>/shaders/fx/<programa>.fsh` (programa `"vfxsample:receita_anel"` → `assets/vfxsample/shaders/fx/receita_anel.fsh`).
2. `readSource` junta: `common.glsl` da RimuLib (que começa com `#version 150`), uma linha `#line 1 1` e o seu arquivo.
3. Cada linha do seu arquivo que, sem espaços nas pontas, começa com `#include "` e termina com `"` é trocada pelo conteúdo de `assets/<namespace do shader>/shaders/fx/<arquivo>`; se não existir, de `assets/rimulib/shaders/fx/<arquivo>`. Depois do texto incluído vem um `#line` que restaura a numeração do seu arquivo.
4. Não há recursão (um arquivo incluído não pode incluir outro) nem busca no namespace do motor.
5. O vertex shader é fixo: um triângulo de tela cheia com `out vec2 texCoord`; a saída é `out vec4 fragColor`.

**Consequência para addons:** para usar `calamidade_fx.glsl`, copie-o para `assets/<seu_namespace>/shaders/fx/calamidade_fx.glsl`. O `scripts/novo_efeito.py` faz isso, e o `checkShaders` do build falha se uma cópia dentro deste repositório divergir da do motor. A biblioteca tem `#define CA_FX_VERSION 3` (0.12.0; 2 na 0.9.0) para você conferir a versão.

## O que o common.glsl já oferece

| Nome | Uso |
|---|---|
| `DiffuseSampler`, `DepthSampler` | Cor da cena até aqui e profundidade. |
| `texCoord`, `fragColor`, `OutSize` | Coordenada 0..1 do pixel, saída e tamanho da tela em pixels. |
| `GTime` | Segundos do relógio do mundo: `(tempo do mundo mod 24000 + fração) / 20` (conferido com `javap -c` em `FxApi$Stage`), volta a 0 a cada 20 minutos. Para animação ligada à instância, mande `post.age()` ou `post.realAge()`. |
| `skyAt(uv)` | `true` quando o pixel é céu (sem geometria). |
| `scenePosAt(uv)` | Posição do que o pixel mostra, **relativa à câmera**, em blocos (céu: direção × 100000). |
| `viewDir(uv)` | Direção normalizada do raio do pixel (origem na câmera = `vec3(0.0)`). |
| `sampleScene(uv)` | Cor da cena com a coordenada limitada à borda. |
| `initPixel(uv)` e `gPix` | `initPixel` preenche `gPix`, o ângulo de um pixel; `gPix * distância` = tamanho do pixel em blocos. |
| `project(p)` | Posição de mundo relativa à câmera → (uv, profundidade). |
| `hash11`, `hash12`, `hash13`, `hash22`, `vnoise2`, `vnoise3`, `fbm2`, `fbm3` | Ruído. |
| `raySphere`, `rayCylinderY`, `rayPlane`, `raySegmentDist`, `sdCapsule`... | Interseções e distâncias básicas. |
| `line(d, w)`, `halo(d, w)`, `lum(c)`, `saturate(x)`, `rot(a)`, `tonemap(c)`, `prism7(h)` | Utilidades. |
| `PI`, `TAU`, `FAR` | Constantes. |

Não declare funções com esses nomes. Tudo da biblioteca do Calamidade começa com `ca`.

## calamidade_fx.glsl

Arquivo: `src/main/resources/assets/calamidade_awakening/shaders/fx/calamidade_fx.glsl`. Uso: `#include "calamidade_fx.glsl"` numa linha própria, depois dos uniforms.

| Função | Devolve | Notas |
|---|---|---|
| `vec2 caRayBox(ro, rd, bmin, bmax)` | (tEntrada, tSaída) em blocos; `vec2(-1.0)` se erra ou está atrás | Caixa alinhada aos eixos. Dentro dela, tEntrada = 0. `rd` normalizada. |
| `vec3 caBoxNormal(p, bmin, bmax)` | normal externa (eixo dominante) | Para um ponto na superfície da caixa. |
| `vec2 caRaySphere(ro, rd, centro, raio)` | (t0, t1); `vec2(-1.0)` se erra | Dentro da esfera, t0 = 0. |
| `vec2 caSegmentGlow(ro, rd, a, b, núcleo, halo, gPix, cena)` | (núcleo, halo) em 0..1 | Meias-larguras em blocos. Largura mínima de um pixel com intensidade compensada; some atrás do terreno (`cena` = `length(scenePosAt(uv))`). |
| `float caGrid2(p, célula, largura, pixelMundo)` | 0..1 | Linhas num plano; afina e apaga sem moiré quando o pixel cresce. |
| `float caWorldGrid(p, n, célula, largura, pixelMundo)` | 0..1 | Grade sobre qualquer superfície, pelos dois eixos perpendiculares à normal `n`. Passe `p` relativo a um ponto fixo (o centro do efeito) para não deslizar com a câmera. |
| `vec3 caRainbow(h)` | cor | Matiz em voltas: 0 vermelho, 1/3 verde, 2/3 azul. |
| `float caLuma(c)` | luminância | Pesos Rec. 709. |
| `float caEnvelope(x, a, b, c, d)` | 0..1 | Sobe de a a b, platô, desce de c a d (smoothstep); a == b ou c == d viram degraus. |
| `float caDistanceWave(dist, tempo, velocidade, largura, alcance)` | 0..1 | Anel na distância velocidade × tempo, que some perto do alcance; 0 antes de tempo 0. |
| `float caStars(dir, segundos)` | 0..1 | Campo de estrelas procedural na direção `dir`; usado pelo passe de atmosfera. |
| `vec3 caRaySegment(ro, rd, a, b)` | (t no raio, s no segmento 0..1, distância) | Versão 2 (0.9.0). Pontos mais próximos entre o raio e o segmento AB; base do passe `beams`. |
| `float caBeamCore(d, meiaLargura, pixelMundo)` | 0..1 | Núcleo de feixe com largura mínima de um pixel e intensidade compensada. |
| `float caBeamHalo(d, meiaLargura, pixelMundo)` | 0..1 | Halo h²/(d²+h²), cortado entre 2,5 e 5 meias-larguras (vários feixes não acendem a tela inteira). |
| `float caPulseTrain(x, espaçamento, tamanho)` | 0..1 | Pulsos gaussianos ao longo de um feixe; `x` em blocos já deslocado pelo movimento. |
| `float caBeamTaper(s, comprimento, pontas)` | 0..1 | 0 nas pontas, 1 a `pontas` blocos delas: prende ondulação e ruído às pontas. |
| `float caDepthFade(t, cena, suave)` | 0..1 | Oclusão suave pela profundidade: 1 na frente da cena, 0 a `suave` blocos atrás. |
| `vec3 caNormalFromNeighbors(p, esq, dir, baixo, cima)` | normal virada para a câmera | Versão 2. Normal pela profundidade: posições (`scenePosAt`) do pixel e dos 4 vizinhos; usa a diferença menor em cada eixo (bordas e céu não estragam). |
| `float caPointLight(p, n, luz, alcance, queda)` | 0..1 | `(1 - d/alcance)^queda` vezes o cosseno com envolvimento 0,3; faces de costas, nada. |
| `float caSegmentLight(p, n, a, b, alcance, queda)` | 0..1 | Igual, a partir do ponto mais próximo do segmento AB (luz de feixe). |
| `float caGroundLight(p, n, centro, raio, largura, altura, anel)` | 0..1 | Disco (anel = 0) ou anel (1) de luz no chão; alcance vertical `altura`; faces para cima 100%, paredes 40%, tetos 0. |
| `vec3 caLitColor(base, luz)` | cor | `base + base^0,4 × 0,8 × luz`: soma a luz sobre o albedo estimado (realça superfícies escuras de noite). |
| `vec3 caSkyDir(azimute, elevação)` | direção | Versão 3 (0.12.0). Radianos, convenção do Minecraft (0 = sul, PI/2 = oeste); igual a `Sky.Direction.of`. |
| `float caAngle(a, b)` | radianos | Ângulo entre direções, estável perto de 0 e de PI. |
| `mat3 caSkyBasis(centro, giro)` | (direita, cima, centro) | Base do céu em volta de uma direção, "cima" para o zênite; `giro` em radianos. |
| `vec2 caSkyLocal(dir, base)` | coordenadas locais | Projeção azimutal equidistante: o comprimento é o ângulo até o centro (círculos no céu continuam círculos). |
| `vec3 caSkyFromLocal(local, base)` | direção | Inversa de `caSkyLocal`. |
| `vec2 caLensSource(theta, einstein, alcance)` | coordenadas locais | Lente de massa pontual: de onde vem a luz vista em `theta`; some sem quina até `alcance`. |
| `float caDisc(r, raio, borda)` | 0..1 | Disco com borda suave. |
| `float caNoise2(p)`, `float caFbm2(p, oitavas)` | 0..1 | Ruído de valor e soma de 2 a 4 oitavas, sem depender do `common.glsl`. |
| `vec3 caVanillaSky(dir, céu, névoa, distância)` | cor | Estimativa do céu que o jogo pinta numa direção (a do passe de atmosfera). |

Testes: `node scripts/testar_glsl.cjs` compila a biblioteca em GLSL ES 3.00 e executa os casos numa GPU (WebGL2 no Chromium headless), comparando com valores esperados. O `checkShaders` do Gradle compila cada shader do motor e do exemplo em GLSL 1.50, com o `common.glsl` real e os `#include` resolvidos como a RimuLib faz. `node scripts/previa_passes.cjs` roda os passes `beams` e `lights` do motor, com o `common.glsl` real, sobre uma cena sintética com profundidade e grava PNGs em `build/previa/` (prévia da aparência e da oclusão sem o jogo); `node scripts/previa_passes.cjs ceu` (0.12.0) roda os passes `sky` e `atmosphere` sobre montanhas com o sol do jogo.

## Convenções

- **Espaço:** tudo relativo à câmera. A origem do raio é `vec3(0.0)`, a direção é `viewDir(texCoord)`, o centro do efeito chega por uniform de `post.center()`.
- **Oclusão:** compare o `t` da sua interseção com `length(scenePosAt(texCoord))`; o terreno na frente esconde o efeito.
- **Saída:** leia `texture(DiffuseSampler, texCoord).rgb`, some ou misture o efeito e escreva `fragColor = vec4(cor, 1.0)`. O pós-processamento da RimuLib (bloom, clarões, vinheta) vem depois.
- **Unidades nos uniforms:** blocos para distâncias, ticks para tempo da instância (`post.age()`, `t.since(...)`), segundos para `GTime` (que dá a volta a cada 20 minutos).
- **Uniforms:** `u.f1..f4`, `u.v3(nome, Vec3)`, `u.vec4s(nome, float[])` (array de vec4 numa chamada), `post.uploadTracks(u, "Track_")` (uma `uniform float Track_<trilha>` por trilha). Uniform que o shader não declara é ignorado.
- **Portabilidade:** escreva literais float (`1.0`, não `1`): o driver do jogador pode ser estrito.
- **Custo:** prefira soluções analíticas (as funções acima) a raymarch. Se precisar marchar, use poucos passos e reduza com `post.quality()`.

## Esqueleto mínimo

```glsl
// A RimuLib antepõe #version 150 e common.glsl.
uniform vec3 Centro;   // post.center(): relativo à câmera
uniform vec4 Estado;   // x: peso da fase, y: opacidade, z: raio (blocos), w: ticks desde o marcador

#include "calamidade_fx.glsl"

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    vec3 rd = viewDir(texCoord);
    vec2 t = caRaySphere(vec3(0.0), rd, Centro, Estado.z);
    if (t.x > 0.0 && t.x < length(scenePosAt(texCoord))) {
        vec3 n = normalize(rd * t.x - Centro);
        color += vec3(0.4, 0.9, 1.0) * pow(1.0 - abs(dot(n, rd)), 3.0) * Estado.x * Estado.y;
    }
    fragColor = vec4(color, 1.0);
}
```

```java
AddonLayers.post(ID, post -> {
    var t = post.timeline();
    post.pass("meumod:casca", u -> {
        u.v3("Centro", post.center());
        u.f4("Estado", t.weight("impacto"), post.time().opacity(), 4f, (float) t.since("impacto"));
    });
});
```
