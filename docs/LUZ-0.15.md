# Light Engine (0.15, experimental): Light Cookies, holofotes, projetores e ciclo de vida

> **Versão experimental.** A 0.15.0 está no branch de desenvolvimento e não é um release. A API (`api.v1`) só cresceu:
> todo addon compilado para a 0.14 continua carregando. O protocolo de rede ganhou o canal `live_v1`: servidor e
> clientes precisam da mesma versão.

A 0.9 trouxe a [Light](LUZ-0.9.md): pontos, segmentos, discos e anéis que clareiam o que a câmera vê. A 0.15 deixa
o addon decidir **a forma** da luz: uma imagem própria (um *light cookie*) projetada sobre o terreno, as paredes e as
entidades, um holofote orientável, um projetor de raios paralelos, curvas de atenuação, modos de composição e cores
que mudam no tempo ou com a distância. Tudo continua no **mesmo passe único** de luzes por quadro.

O [VFXSample](ai/receitas/28-laboratorio-luz.md) traz dez demonstrações técnicas (`/vfxsample luz lista`). Elas são
**exemplos de uso da API, não um catálogo de estilos**: cada uma usa imagens próprias para provar que o visual vem do
addon. Copie a técnica, não a aparência.

## Em uma tela

```java
// Runas girando no chão: projetor planar 6 blocos acima do centro, apontado para baixo (padrão).
static final Light RUNAS = Light.projector(Point.center(0, 6, 0), 10, 10)   // 10 x 10 blocos
    .cookie("meumod:textures/lights/runas.png")                             // assets/meumod/textures/lights/runas.png
    .color(0xff8ab4ff).intensity(3).radius(9).falloff(.6f)                  // radius = profundidade do projetor
    .track(Light.Param.ROLL, "giro")                                        // gira a imagem
    .build();

// Holofote que varre de um lado a outro.
static final Light BUSCA = Light.spot(Point.center(0, 6, -3)).direction(0, 38).angle(26).softness(.3f)
    .color(0xfffff0c0).intensity(4.5f).radius(22)
    .track(Light.Param.YAW, "varrer").build();

Effects.lights(ID, RUNAS, BUSCA);   // construtor do mod, depois de register e timeline (até 8 luzes por efeito)
```

## Formas

| Forma | Fábrica | O que faz | Cookie |
|---|---|---|---|
| Ponto, segmento, feixe, disco, anel | `point`, `segment`, `along`, `disc`, `ring` (0.9) | como antes | só o **disco** (imagem no quadrado `2 × raio`) |
| **Holofote** | `Light.spot(ponto)` | cone de `angle` graus (1..170, padrão 40), comprimento `radius`, borda `softness` | sim: o cone vira uma pirâmide de base quadrada com a imagem inteira (projetor de slides) |
| **Projetor** | `Light.projector(ponto, largura, altura)` | caixa de raios **paralelos**: `largura × altura` blocos, profundidade `radius` | sim: a imagem tem o mesmo tamanho em qualquer distância |

**Orientação** (holofote e projetor): `direction(yaw, pitch)` no referencial da instância — yaw 0 é a frente da
instância, 90 a esquerda; pitch 90 (padrão) aponta para baixo, -90 para cima — ou `toward(ponto)`, que mira um `Point`
a cada quadro (o olho de uma entidade, um socket, `anchorAim`, uma posição do mundo). As trilhas `YAW` e `PITCH` somam
graus em cima das duas. `roll(graus)` e a trilha `ROLL` giram a imagem em volta do eixo da luz. Um disco com cookie fica
sempre horizontal; o topo da imagem segue a frente da instância.

**Associação.** As posições são `Point` (centro, mundo, âncora, olhos, mira, conjurador, socket). Uma luz presa a uma
entidade: `Effects.follow(id, entidade, offset)` ou `Placement.on(entidade)` com `Point.anchor(...)`; a um osso de
chefe: `Point.socket("boca", ...)` e `toward(Point.socketAim("boca", 20))`. Sem a entidade carregada no cliente, a luz
não é desenhada (como os feixes).

## Light Cookies

Um cookie é um PNG de **qualquer** pacote de recursos, normalmente `assets/<seu_ns>/textures/lights/*.png`:

```java
Light.Cookie.of("meumod:textures/lights/energia.png")        // MASK, CLAMP, 1 quadro, filtro suave
    .colored()                  // COLOR: as cores da imagem tingem a luz
    .repeat().scale(3)          // ladrilho: 3 cópias na largura da projeção
    .rotation(15).offset(.5f, 0)// giro e deslocamento no espaço da imagem (em imagens)
    .sheet(4, 4).fps(9).blendFrames(true)   // folha animada, quadros misturados
    .pixelated()                // texel mais próximo (pixel art), sem mipmaps
    .fallback(Light.Cookie.Fallback.NONE);  // sem a imagem, não desenha nada
```

### Semântica dos canais

O motor lê o PNG com **alfa direto** e pré-multiplica ao carregar (`rgb × a`).

| Modo | Fator sobre a luz | Use para |
|---|---|---|
| `MASK` (padrão) | **luminância** de `rgb × a` (escalar) × cor da luz | máscaras monocromáticas: branco = luz total, preto ou transparente = nada. Um PNG branco sobre transparente e um PNG em tons de cinza sem alfa funcionam igual. |
| `COLOR` | `rgb × a` por canal × cor da luz | vitrais, hologramas multicoloridos. Deixe a cor da luz **branca** para as cores exatas da imagem. |

O cookie **modula** a luz: nunca deixa a luz mais forte que `cor × intensidade` e nunca aparece como um plano
flutuante. Superfícies fora do alcance não recebem nada; superfícies iluminadas mantêm a própria textura por baixo.

### Coordenadas e bordas

- Olhando **ao longo** da luz, x da imagem vai para a direita e y para cima. Para uma luz que aponta para baixo, o
  **topo da imagem aponta para a frente da instância** (com `Placement.at(pos, yawDoJogador, 0)`, para longe do
  jogador: runas ficam legíveis).
- A imagem cobre a abertura inteira do holofote, o retângulo `largura × altura` do projetor ou o quadrado `2 × raio`
  do disco. A proporção é a da projeção, não a do PNG: para uma imagem 2:1, use um projetor 2:1.
- `scale` (trilha `COOKIE_SCALE`) repete a imagem, `rotation` (`COOKIE_ROTATION`) gira, `offset` (`COOKIE_U`,
  `COOKIE_V`) desloca, nessa ordem e em unidades de imagem.
- **Fora da imagem:** `CLAMP` (padrão) não dá luz (transparente); `REPEAT` ladrilha. A borda da **projeção** sempre
  esmaece com `softness` (0 duro, 1 a abertura toda), independente do modo.

### Folhas animadas

`sheet(colunas, linhas)` divide a imagem em quadros da esquerda para a direita e de cima para baixo, **sem margem**.
`frames(n)` usa só os primeiros n. No relógio da instância: `fps` (padrão 12) e `play(LOOP | ONCE | PING_PONG)`. Pela
Timeline: a trilha `Light.Param.FRAME` escolhe o quadro (0..n-1) e vence o relógio; a parte fracionária mistura dois
quadros com `blendFrames(true)`. A folha inteira precisa caber em 512 × 512 depois de reduzida (veja limites).

### Animação pela Timeline

| Trilha (`Light.Param`) | Efeito |
|---|---|
| `INTENSITY`, `RADIUS`, `FALLOFF`, `WEIGHT`, `X/Y/Z` | como na 0.9 |
| `YAW`, `PITCH`, `ROLL` | graus somados à orientação |
| `ANGLE`, `SOFTNESS`, `SIZE_X`, `SIZE_Y` | abertura do holofote, borda, tamanho do projetor |
| `COOKIE_SCALE`, `COOKIE_ROTATION`, `COOKIE_U`, `COOKIE_V` | transformação da imagem (rolagem contínua: trilha linear) |
| `FRAME` | quadro da folha |
| `COLOR` | posição na `colorRamp` |

Para laços (sustain) sem salto, a trilha precisa ter o mesmo valor nas duas bordas do laço — ou, para giros e
rolagens, um número inteiro de voltas (0 → 360 graus; 0 → 2 imagens).

## Cor, atenuação e composição

- `colorRamp(c0, c1[, c2, c3])` + trilha `COLOR` (0..1): **cor animada** no tempo; substitui `color`.
- `distanceGradient(c0, c1[, c2, c3])`: **gradiente multicolorido no espaço**, da fonte (0) ao alcance (1); multiplica
  a cor da luz (deixe-a branca). Distância do ponto ou segmento, ao longo do eixo do projetor, do centro do disco.
- `attenuation(...)`: `POWER` (padrão, `(1 − d/r)^falloff`, a curva da 0.9), `SMOOTH` (`(1 − (d/r)²)²`, ampla),
  `INVERSE_SQUARE` (núcleo quente, `k = 2 × falloff`, janela até 0 no alcance), `CONSTANT` (plana até 80%, depois some).
- `blend(...)`: `ADD` (padrão), `SOFT` (a soma de todas as luzes SOFT do pixel passa por `1 − e^(−x)`: multidões de
  luzes que não estouram), `SUBTRACT` (escurece até 95%: sombras projetadas, gobos, faixas de eclipse), e desde a
  fase 2 `EMISSIVE` e `OVERRIDE` (abaixo). Todos os modos são independentes da ordem.

### EMISSIVE e OVERRIDE (fase 2)

`ADD`, `SOFT` e `SUBTRACT` iluminam **a cor da cena**: num chão escuro, até uma luz forte fica apagada, e de dia quase
não aparece. Os dois modos novos tiram essa dependência. `MASK`/`COLOR` continuam dizendo **como ler a imagem**; o modo
de composição diz **o que fazer com a luz**.

| Modo | O que faz | Parâmetros (todos com trilha) |
|---|---|---|
| `EMISSIVE` | soma `cor × intensidade × cobertura × atenuação` **sem** multiplicar pela cor da superfície: cores intensas sobre chão escuro, neon, energia. O que passa do branco num canal transborda para os outros (núcleo branco). | `blendStrength` / `BLEND_STRENGTH` 0..1 (0 = exatamente ADD, 1 = totalmente emissiva, padrão): a transição convencional ↔ emissiva; `surfaceDetail` / `DETAIL` 0..1 (quanto da textura da superfície aparece na luz) |
| `OVERRIDE` | **substitui** a cor visível dentro da máscara: `mix(cena, alvo, w)`, `alvo = cor × intensidade` (× cor da imagem em `COLOR`), `w = força × cobertura × atenuação × borda × peso da instância`, limitado a 0..1 | `blendStrength` = força da sobrescrita (0 nada, 0,5 domina e deixa a superfície aparecer, 1 substitui por completo onde a máscara é cheia); `surfaceDetail` |

Regras que valem sempre: a máscara, o alfa, a atenuação, a orientação, a borda e o fade da instância continuam
funcionando; **pixel transparente nunca muda a superfície** (força 0 é idêntica a sem luz, conferido pixel a pixel).
Várias `OVERRIDE` no mesmo pixel: cobertura total `1 − Π(1 − wᵢ)` em direção à média dos alvos ponderada por `wᵢ`,
sem depender da ordem. Ordem fixa do pixel: cena iluminada por ADD, SOFT e a parte não emissiva de EMISSIVE →
escurecida por SUBTRACT → substituída por OVERRIDE → parte emissiva somada por cima.

`contrast` / `CONTRAST` (0,1..8, padrão 1) muda a **resposta da máscara** em volta de meia cobertura com uma curva que
preserva 0 e 1: 3 a 6 deixa as linhas de um cookie nítidas e quase binárias; abaixo de 1 lava a máscara.

Com `OVERRIDE`, a atenuação conta na força: para "a cor projetada domina por completo", use
`attenuation(Attenuation.CONSTANT)` (plana até 80% do alcance).

### Receitas sem GLSL

```java
// Realista ou suave: ADD, queda suave, borda larga.
Light.spot(Point.center(0, 6, 0)).angle(50).softness(.6f).attenuation(Light.Attenuation.SMOOTH)
    .color(0xffffe0b0).intensity(2.5f).radius(14).build();

// Neon: EMISSIVE, contraste alto, nítido, cor saturada; pulsa pela trilha.
Light.projector(Point.center(0, 6, 0), 9, 9).cookie(Light.Cookie.of("meumod:textures/lights/neon.png").sharpness(.8f))
    .blend(Light.Blend.EMISSIVE).contrast(2.5f).color(0xffff30d0).intensity(4.5f)
    .attenuation(Light.Attenuation.CONSTANT).track(Light.Param.INTENSITY, "pulso").build();

// Projeção emissiva que acende aos poucos (convencional -> emissiva).
... .blend(Light.Blend.EMISSIVE).track(Light.Param.BLEND_STRENGTH, "acender") ...

// Sobrescrita: retícula de ataque legível de dia e de noite.
Light.projector(Point.center(0, 7, 0), 7, 7).cookie(Light.Cookie.of("meumod:textures/lights/alvo.png").sharpness(1))
    .blend(Light.Blend.OVERRIDE).blendStrength(1).contrast(4).color(0xffff1020).intensity(1.1f)
    .attenuation(Light.Attenuation.CONSTANT).softness(.01f).build();

// Vitral que domina a grama mas deixa 40% da textura.
... .cookie(Light.Cookie.of("meumod:textures/lights/vitral.png").colored())
    .blend(Light.Blend.OVERRIDE).blendStrength(.9f).surfaceDetail(.4f) ...

// Alta resolução e nitidez: mandala de 2048 numa projeção grande.
... .cookie(Light.Cookie.of("meumod:textures/lights/mandala.png").resolution(Light.Cookie.Resolution.ULTRA).sharpness(.5f)) ...
```

Combine modos em luzes diferentes do mesmo efeito (até 8): `OVERRIDE` para a forma legível + `EMISSIVE` para o brilho
por cima é o padrão das retículas. Bloom: o motor **não** cria um buffer HDR para a luz (o quadro é de 8 bits; o
núcleo branco do EMISSIVE é o "estouro" possível sem framebuffer novo). Para halo na tela, use `post.bloom(...)` da
camada `AddonLayers.post` do seu efeito, que já respeita a intensidade dos efeitos de tela e "ocultar clarões".
Acessibilidade: a cintilação (`flicker`) já cai com a intensidade dos efeitos de tela e com "ocultar clarões"; não
anime `INTENSITY` ou `BLEND_STRENGTH` piscando acima de 3 vezes por segundo.

### Nitidez e filtragem (fase 2)

| Opção | Efeito |
|---|---|
| `filter(SMOOTH)` (padrão) | trilinear com mipmaps: suave, sem cintilar |
| `filter(SHARP)` ou `sharpness(0..1)` | de perto, cada texel tem núcleo duro e só a borda é misturada, numa faixa de ~1 pixel da tela (`sharpness` 1) a ~4 (0); de longe, mipmaps com até meio nível a mais de detalhe. Linhas finas nítidas sem degraus nem cintilação ao mover a câmera |
| `filter(PIXELATED)` ou `pixelated()` | texel mais próximo, sem mipmaps (pixel art; cintila longe e pequeno) |
| `lodBias(-1..4)` | viés de mipmap: positivo desfoca (gobo fora de foco), negativo guarda detalhe longe (mais cintilação); limitado para quadros de folha nunca se misturarem |
| `softness` da luz | a borda **da projeção**, independente da nitidez da imagem |
| `contrast` | a resposta da máscara (acima) |

Sem vazamento: a leitura fica meio texel **do nível lido** para dentro do retângulo do quadro (nada da célula vizinha
nem do quadro vizinho), e o nível máximo de mipmap mantém cada quadro com pelo menos 4 texels. Desde a fase 2, lados
que não são potência de 2 são **reamostrados uma vez na carga** para a potência de 2 de cima (por exemplo 222 × 192 →
256 × 256): antes, a borda transparente da célula escurecia o ladrilho (`REPEAT`) nos mipmaps de longe.

### Alta resolução (fase 2)

`Cookie.resolution(STANDARD | HIGH | ULTRA)` = lado máximo guardado de 512 (padrão), 1024 ou 2048. A versão de 512
sempre existe no atlas padrão; as maiores vão para um **segundo atlas** (2048 × 2048, mipmaps completos, ~21,3 MB) que
só é criado quando uma luz pede alta resolução **e** o perfil permite:

| Perfil | Lado máximo | VRAM de cookies (máx.) |
|---|---|---|
| LOW | 512 (sem atlas grande) | ~21,3 MB |
| BALANCED | 1024 (até 4 imagens) | ~42,7 MB |
| HIGH, ULTRA | 2048 (1 imagem de 2048 ou 4 de 1024) | ~42,7 MB |

Enquanto a versão grande carrega, acima do orçamento, com o atlas grande cheio ou ao baixar o perfil, a luz usa a de
512 (nunca some). O envio é em fatias de até 4 MB por quadro (uma imagem de 2048 leva ~6 quadros e só é usada
completa). Cada PNG guarda **uma** versão grande, a do maior pedido; só as luzes que pediram alta resolução a usam. O
atlas grande sai sozinho ao baixar para LOW, sem uso por ~1200 quadros, na recarga e ao sair do mundo.

## Desempenho e orçamento

- **Um passe** para todas as luzes, como na 0.9, cobrado do orçamento HERO quando algum efeito HERO desenhado tem
  luzes, senão do comum. Nenhuma luz ou textura cria passe próprio.
- **Caminho rápido.** Pontos, segmentos, discos e anéis com a curva e a composição padrão usam exatamente o shader da
  0.14 (prévia idêntica pixel a pixel) e não leem textura nenhuma.
- **Atlas único.** Todo cookie vai para uma textura de 2048 × 2048 RGBA8 com 9 níveis de mipmap (~21,4 MB de VRAM),
  células de 512 (16) ou 256 (64); cada PNG é lido, reduzido e pré-multiplicado **uma vez** numa thread de trabalho, e
  o envio à GPU é de no máximo **uma imagem por quadro**. O atlas só existe depois do primeiro cookie usado: sem
  cookies, nenhum byte e nenhum custo. Ele é solto depois de ~1200 quadros sem cookies, na recarga de recursos e ao
  sair do mundo. Imagens sem uso por 60 quadros podem ser despejadas quando o atlas enche.
- **Teto de cookies por quadro (fase 3):** próprio e configurável, independente do teto de luzes e nunca acima dele:
  LOW 16, BALANCED 48, HIGH 96, ULTRA 128 (antes: um quarto das luzes, 2/8/16/16). As luzes com cookie fora do teto
  usam o **fallback**; quem fica com a imagem é decidido por prioridade e contribuição (veja "Fase 3").
- **Fallback previsível:** `AVERAGE` (padrão) desenha a luz sem a imagem, com a intensidade multiplicada pela cobertura
  média da imagem (0,5 se ela nunca carregou), mantendo tamanho e energia; `NONE` não desenha. Durante a **primeira**
  carga a luz não aparece (alguns quadros), para não piscar do fallback para a imagem.
- **Culling conservador:** cada luz tem uma esfera envolvente (alcance; projetor: a caixa inteira) testada contra o
  frustum da câmera e contra a distância em que a névoa do jogo já cobre tudo, **antes** do teto de luzes. Uma luz
  descartada não ilumina nenhuma superfície visível.
- **Limites (fase 3):** 32 luzes por efeito (antes 8); até 192 por quadro (teto do perfil: 24/64/128/192); PNG até
  4096 de lado (reduzido à metade até caber em 512; maior é recusado); folha até 64 × 64 com 256 quadros.
- **PNG ausente ou inválido:** registrado **uma vez** no log, contado no F7, a luz usa o fallback; nada trava.
- **F7:** a linha das luzes ganhou um detalhe: pedidas, descartadas, acima do teto, cookies amostrados/teto, fallbacks,
  quantos em alta resolução, imagens prontas/esperando/com falha, versões grandes prontas e carregando, uploads e MB
  dos dois atlas.
- **Fase 2:** EMISSIVE, OVERRIDE e o detalhe só calculam no caminho estendido (o sombreamento da superfície é feito na
  primeira luz estendida do pixel); sem luzes novas, nenhum texel, textura, passe ou conta a mais. Uma luz com modo
  novo lê 13 texels em vez de 4.

## Fase 3: orçamento adaptativo de luzes

A filosofia: **preservar o desenho da projeção antes de sacrificar seus detalhes**. Mais luzes e mais cookies por
quadro, e, quando falta recurso, a perda de qualidade segue o gargalo real.

### Orçamentos

| Perfil | Luzes (antes) | Cookies (antes) | Lado máx. de alta resolução |
|---|---:|---:|---|
| LOW | 24 (8) | 16 (2) | 512 |
| BALANCED | 64 (32) | 48 (8) | 1024 |
| HIGH | 128 (64) | 96 (16) | 2048 |
| ULTRA | 192 (64) | 128 (16) | 2048 |

Valores **propostos**, verificados em llvmpipe quanto a correção e tendência, não medidos em GPU real: a medida
definitiva é a [Parte 11 do TESTE-REAL](TESTE-REAL.md#parte-11--benchmark-da-light-engine-0150-rtx-4060). Os dois
tetos são editáveis por perfil (config `quality.<perfil>.lights` e `quality.<perfil>.cookies`, Atlas → Qualidade →
ajuste fino); o aplicado é `min(cookies, luzes)`. Configurações antigas: um `lights` ainda igual ao padrão antigo do
perfil (8/32/64/64) é levado ao padrão novo uma vez (`lightCapsVersion` = 2); qualquer valor que o jogador escolheu fica.
Os demais orçamentos (efeitos desenhados, passes, feixes, vértices) não mudaram.

O limite **por efeito** subiu de 8 para 32: com 8, uma arena de runas ou um anel de projeções precisava de várias
instâncias só para existir, sem nenhum ganho de desempenho (o custo é por luz desenhada, e o teto do perfil continua
valendo para a cena inteira).

### Os quatro gargalos, tratados separadamente

| Gargalo | Onde aparece | O que o motor faz |
|---|---|---|
| **Processamento por pixel** | cada pixel percorria todas as luzes (até 192 × 3 leituras) | **tiles de tela**: com 17 luzes ou mais, a esfera envolvente de cada luz é projetada numa grade 16 × 9; cada tile recebe uma máscara de bits das luzes que o alcançam e o pixel pula as outras sem ler a linha delas. O pixel acha o seu tile projetando a posição reconstruída **com a mesma matriz** que a CPU usou (concordam por construção). Com muitas luzes sobre o mesmo tile, os tiles não ajudam: aí valem o teto, a prioridade e a simplificação abaixo |
| **Amostras de textura** | cookies sobrepostos no mesmo tile | acima de 12 cookies num tile, cookies NORMAL e DECORATIVE leem só o quadro mais próximo da folha (sem misturar dois quadros: metade das amostras). SMOOTH, SHARP e PIXELATED custam a mesma única leitura, então a filtragem não é rebaixada |
| **Capacidade do atlas** | muitos PNGs diferentes (16 células de 512) | **resolução efetiva adaptativa** (abaixo): a imagem desce para 256 ou 128 em vez de cair no fallback |
| **VRAM e uploads** | atlas grande, imagens novas | o atlas padrão é fixo (~21,3 MB, nunca cresce); o grande (~21,3 MB) só existe quando uma luz que pediu alta resolução **cobre mais de 512 pixels na tela**; envios limitados a 4 MB por quadro (pelo menos uma imagem), uma imagem por PNG compartilhada por todas as luzes |

Reduzir uma imagem de 2048 para 512 economiza memória, espaço no atlas e banda de envio; **não** reduz o custo de
processar a luz no shader (continua uma luz, uma leitura). Por isso os dois problemas têm respostas diferentes.

### Ordem da degradação

1. Descartar luzes fora da visão (frustum, névoa) e **sem contribuição visível** (intensidade × peso × cor abaixo de
   meio degrau de 8 bits). Contadas à parte no F7 ("dark").
2. Sob pressão do atlas, **reduzir a resolução efetiva** dos cookies (512 → 256 → 128), os de menor demanda primeiro.
3. Sob pressão por pixel, **ler um só quadro** das folhas animadas de luzes NORMAL e DECORATIVE.
4. Acima dos tetos, **manter as luzes mais importantes**: prioridade primeiro, depois contribuição na tela.
5. **Fallback sem textura** só como último recurso, com a política escolhida pelo addon (`AVERAGE` ou `NONE`).

### Prioridade (opt-in)

```java
static final Light RETICULA = Light.projector(Point.center(0, 7, 0), 6, 6).cookie(ATAQUE)
    .blend(Light.Blend.OVERRIDE).priority(Light.Priority.CRITICAL).build();
static final Light BRILHO_DO_CHAO = Light.disc(Point.center(), 9).priority(Light.Priority.DECORATIVE).build();
```

`DECORATIVE` (ambiente), `NORMAL` (padrão), `IMPORTANT` (efeito relevante), `CRITICAL` (informação de combate:
retículas, áreas de perigo). Reaproveita a prioridade que já existia: uma luz `NORMAL` de um efeito `HERO` conta como
`IMPORTANT`. A escolha considera prioridade, depois a contribuição (brilho × cobertura angular da esfera, que já
embute distância e tamanho na tela), só entre luzes visíveis; uma luz mantida no quadro anterior ganha 1,5 × no score
(histerese), então duas luzes parecidas não ficam trocando a cada quadro. Abaixo do teto nada muda: todas entram, e o
resultado não depende da ordem.

Uma retícula `CRITICAL` não vira mancha porque decorativas gastaram o orçamento: os cookies vão primeiro para ela. No
teste em jogo (LOW, 16 cookies, 2 luzes CRITICAL contra 28 DECORATIVE mais brilhantes), a retícula ficou texturizada em
60 de 60 ticks.

**Acessibilidade.** A luz é visual e o jogador pode reduzi-la ou desligá-la (intensidade dos efeitos de tela, perfil,
shader packs). **Nunca use a iluminação como a única indicação de perigo**: acompanhe uma retícula `CRITICAL` de som,
partículas, malha ou de um aviso de gameplay (animação de preparo, tempo) que continue legível com os efeitos desligados.

### Resolução efetiva adaptativa

O addon entrega **um** PNG; nunca uma versão por perfil. Níveis efetivos: 128, 256, 512 (atlas padrão, células em
quadtree 512 → 256 → 128, cada uma alinhada ao próprio tamanho, então os mipmaps nunca misturam células) e 1024, 2048
(atlas grande). Imagens pequenas nunca são ampliadas.

- **Demanda** de cada luz: o lado que ela precisa na tela (altura projetada da esfera envolvente; folhas: × colunas ou
  linhas), × 2 para IMPORTANT/CRITICAL, ÷ 2 para DECORATIVE. A demanda de uma imagem é a maior entre as luzes que a usam.
- **Sem pressão**, a versão padrão fica em 512 (comportamento e aparência da fase 2).
- **Sob pressão** (atlas acima de 85% ou uma imagem sem lugar nos últimos 120 quadros), cada imagem desce até a sua
  demanda, nunca abaixo de 256 para CRITICAL. A versão menor é decodificada numa thread de trabalho enquanto a atual
  continua desenhando, e a troca acontece num quadro só (libera, aloca, envia): **nunca há um quadro sem desenho**.
- Uma imagem nova que não acha lugar **entra reduzida** (a própria cadeia de mipmaps deslocada, sem decodificar de
  novo) em vez de cair no fallback.
- **Histerese:** descer depois de 30 quadros seguidos pedindo menos, subir depois de 240, nunca duas trocas em 600
  quadros; subir só se o atlas continuar abaixo de 70% com a versão maior (sem ciclo sobe-desce).
- Máscaras, transparência e cores são preservadas (a versão reduzida é exatamente um nível de mipmap da original);
  quadros de folhas continuam separados (o nível máximo lido acompanha o tamanho do quadro).

No teste em jogo (`/vfxsample luz adaptativa`: 32 PNGs de 512, 16 perto e 16 longe, que não cabem nas 16 células
grandes), o atlas assentou em 17 imagens de 128, 6 de 256 e 9 de 512, nenhuma no fallback, memória fixa em 21,3 MB, e
nenhuma troca nem upload nos 400 ticks seguintes.

### F7

Além da linha da fase 2, três linhas:

- `limits` — tetos efetivos de luzes e cookies; luzes descartadas sem contribuição; cookies mantidos, acima do
  orçamento, carregando, lendo um só quadro (pressão por pixel); quantas luzes `CRITICAL` estão texturizadas.
- `resolution` — distribuição 128/256/512/1024/2048, imagens reduzidas e se há pressão do atlas, trocas de
  resolução (realocações), imagens que entraram reduzidas, uploads adiados, ocupação dos atlas, MB enviados.
- `per pixel` — tiles ligados, luzes por tile (média e máximo), cookies no tile mais carregado.

Assim cada perda de qualidade tem motivo: **fora da visão** (descartada), **sem contribuição** (dark), **acima do teto de
luzes** (over cap), **acima do teto de cookies** (over budget → fallback), **carregando**, **resolução reduzida**
(pressão do atlas) ou **um só quadro** (pressão por pixel).

### Laboratório

`/vfxsample luz carga <16|32|64|96|128|192> <mesmo|varios|mista> [espalhado|sobreposto]` monta exatamente esse número
de luzes (instâncias de 16; `mista` = metade convencionais, então 192 = 96 cookies + 96 convencionais; `varios` usa
16 ou 32 PNGs diferentes). `/vfxsample luz adaptativa`, `prioridade`, `runas_atenuacao`, `runas_modos` e
`runas_relevo`: receita 28. A/B do custo por pixel: `/calamidade_client lighttiles off|on`.

### Por que as runas ficavam fracas abaixo do nível original

Não era a atenuação: é a combinação de projetor + POWER + profundidade. O projetor atenua por `z / profundidade` ao
longo do eixo; com a fonte 6 blocos acima e profundidade 9, o chão recebia `(1 − 6/9)^0,6` ≈ 52%, um bloco abaixo 41%,
três abaixo **zero** (z chega à profundidade). A semântica não mudou; a demo `runas` passou a usar profundidade 14 (71%
no chão, 54% três blocos abaixo), e `runas_atenuacao` mostra lado a lado a profundidade antiga, a nova e `CONSTANT`.
Para uma projeção que deve valer igual em qualquer altura (retícula), use `Attenuation.CONSTANT` ou uma profundidade
bem maior que a distância até o chão mais baixo. ADD depende da cor da superfície (terra escura absorve); EMISSIVE não.

### Limitações e decisões

- Os tiles são de CPU (16 × 9, máscara de 192 bits por tile) e não um *tiled/clustered lighting* com compute: o motor
  tem um passe de pós-processamento sem compute shaders. Custo: projetar 8 cantos por luz na CPU e enviar 4,6 KB por
  quadro. Em llvmpipe, 64 luzes caíram de ~205–250 ms para ~65–80 ms por quadro (tendência de software, não medida).
- Os tiles não ajudam quando todas as luzes cobrem os mesmos pixels (sobrepostas): aí o custo é real e os tetos e a
  prioridade decidem.
- Uma imagem guarda uma versão no atlas padrão e no máximo uma grande; duas resoluções grandes do mesmo PNG ao mesmo
  tempo continuam fora (fase 2).
- A demanda usa a esfera envolvente, não a área exata projetada; é conservadora (pede mais resolução que o necessário).
- Abaixo de 17 luzes o passe é o laço da fase 2, idêntico pixel a pixel (prévia, 30 cenas).

## Light API ou GLSL próprio?

| Quero... | Use |
|---|---|
| clarear o mundo em volta de um efeito, com uma forma (círculo de runas, grade, vitral, cáusticas, persiana) | **Light** (+ cookie) — sem shader, no passe único, com orçamento e acessibilidade do motor |
| a luz acompanhar um feixe | `Light.along(feixe)` |
| desenhar algo **no ar** (aura, raio, bolha, holograma flutuante) | partículas, `AddonLayers.world` (malha) ou um passe `post` com GLSL |
| distorcer, recolorir a tela, refração, raymarch | GLSL próprio (`AddonLayers.post`, `calamidade_fx.glsl`) |
| sombras reais, iluminação global, PBR | fora do escopo do motor (a Light não faz sombra; use `SUBTRACT` com cookie para sombras desenhadas) |

**Combinando:** a luz é desenhada antes dos passes de efeito e da atmosfera. Um holograma = malha ou shader para o
objeto + projetor com cookie para a luz que ele joga no chão + partículas para a poeira. Um modelo de chefe = a pose do
osso via `AnchorCapture` + `Light.spot(Point.socket(...)).toward(Point.socketAim(...))`. Partículas não recebem a
Light (só o que está no buffer de profundidade).

## Ciclo de vida: fonte, âncora e dono (0.15)

Toda instância tem três papéis distintos:

| Papel | O que é | Exemplo |
|---|---|---|
| **Fonte** (`EffectSource`) | quem iniciou; conta nos limites por fonte | o chefe (`EffectSource.boss`) |
| **Âncora** | o que a instância segue; posição fixa se não houver | `Placement.at(pos)` = fixa |
| **Dono** (0.15) | de quem a vida da instância depende | o **controlador** do chefe, quando a fonte é `EffectSource.boss` |

Regras desde a 0.15:

1. Quando um controlador de chefe acaba (morte, remoção, descarregamento, `detach`, falhas), **toda instância
   sustentada que ele possuía é solta** (com a saída cinematográfica), mesmo que o handle tenha se perdido —
   inclusive as iniciadas por callbacks (`ctx.start(...)`) que o addon esqueceu de soltar. Instâncias **efêmeras**
   (sem sustain) terminam sozinhas, como sempre: um golpe que sobrevive à morte do chefe continua.
2. Um **vigia** no servidor solta (uma vez, com saída) instâncias sustentadas cuja fonte é um chefe sem controlador
   vivo (log `outlived its boss controller`).
3. Para uma instância **sobreviver de propósito** ao chefe, inicie-a com outra fonte:
   `Placement.at(pos).withSource(EffectSource.script())` ou `WorldEvents.start(...)` no escopo SERVER/DIMENSION.
4. O cliente recebe a cada 5 s (canal `live_v1`) a lista das instâncias que deve mostrar e descarta as outras: um fim
   perdido não deixa mais um céu ou uma luz presos na tela.
5. Várias instâncias do mesmo efeito continuam permitidas (sobreposições, transições): o ID **não** é exclusivo.

### Diagnóstico de efeitos órfãos

| Comando (operador) | Mostra / faz |
|---|---|
| `/calamidade fx list` | toda instância e todo evento global: número, efeito, escopo (`INSTANCE`, `AREA`), dimensão, **fonte**, **dono** (e se o controlador já acabou), **âncora**, estado (`entrada`, `laço`, `saindo`, `efêmero`), posição e quem a segura (fase do chefe) |
| `/calamidade fx orphans` | só as sustentadas cujo dono acabou |
| `/calamidade fx release <n>` | soltura cinematográfica: a instância toca a saída e acaba |
| `/calamidade fx stop <n>` | encerramento imediato, sem saída |
| `/calamidade fx stopall` | encerra todas as instâncias (não os eventos globais) |
| `/calamidade world list` | eventos globais **e** os eventos de área (`Scope.AREA`), que antes não apareciam |
| F7 | `Sustentados: N em laço · N saindo · N descartados (listas do servidor)` |

Roteiro: `F7` mostra instâncias de mob? → `/calamidade fx list` → veja **dono** e **estado** → `fx release` para a
saída, `fx stop` para cortar. Um dono `(encerrado)` com estado `laço` só existe por alguns segundos (o vigia solta).

### O caso do eclipse da 0.14

O relato: um eclipse continuou depois de fechar e reabrir o mundo, os comandos globais diziam que não havia eclipse, um
segundo eclipse começou por cima, e nem `/kill @e` apagou o céu. A reprodução automatizada (`testeOrfaos`, Sentinela na
fúria, sair e reabrir, kill, sobreposição, `/kill @e` com respawn) **passou na 0.14.0**: o caminho direto do Sentinela
não deixa órfão. O que a investigação encontrou, e a 0.15 corrige:

- O eclipse do Sentinela é um evento **`Scope.AREA`**, isto é, uma instância comum com fonte de mob; `/calamidade world
  list` só listava eventos SERVER/DIMENSION, por isso dizia "nenhum evento" com o eclipse na tela, e um eclipse global
  podia começar por cima. Com alcance 1024, o eclipse de um chefe **fora de vista** (o controlador volta com o mundo)
  cobre o céu de longe.
- Uma instância sustentada iniciada com a fonte do chefe por qualquer caminho que **não** fosse o handle da fase
  (callbacks, addons como o BossesTesting) nunca era solta: com posição fixa, ela só acabava quando o chunk
  descarregava. O teste novo prova o conserto (soltura pelo dono e pelo vigia).
- O cliente não tinha como se recuperar de um fim perdido; agora a lista `live_v1` corrige em até 5 s.

## Referência rápida

`Light.spot`, `Light.projector`, `Builder.direction/toward/roll/angle/softness/size/cookie/attenuation/blend/
colorRamp/distanceGradient`, `Light.Cookie.of(...).mode/colored/wrap/repeat/sheet/frames/fps/play/blendFrames/scale/
rotation/offset/pixelated/fallback`, `Light.Projection`, `Light.rampColor`. Assinaturas exatas:
[API-REFERENCIA.md](ai/API-REFERENCIA.md). Receita: [28 — laboratório de luz](ai/receitas/28-laboratorio-luz.md).
Medições: [VALIDACAO.md](VALIDACAO.md#015-light-engine).
