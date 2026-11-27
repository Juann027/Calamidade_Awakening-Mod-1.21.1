# Light: luz visual sobre o mundo (0.9)

> **0.15 (experimental):** holofotes, projetores, Light Cookies (imagens próprias como padrão de luz), curvas, composição e gradientes estão em [LUZ-0.15.md](LUZ-0.15.md). Tudo o que está aqui continua igual.

Um fogo, um pilar de energia ou uma onda de choque que não ilumina nada em volta parece colado na tela. Até a 0.8, o motor não tinha como clarear o terreno em volta de um efeito; um addon precisaria de um shader próprio que reconstruísse a cena. A 0.9 traz a **Light**: luzes que clareiam os blocos, as entidades e o terreno que a câmera vê, desenhadas pelo motor num passe único por quadro.

> **É luz visual.** A Light muda só a imagem. Ela **não** altera o nível de luz do jogo: mobs continuam nascendo no escuro em volta de uma fogueira da Light, plantas não crescem por causa dela, e o servidor não sabe que ela existe. Ela também não faz sombra e não ilumina o que está fora da tela.

A Light é Java puro, como a Timeline, a Atmosphere e o Beam. Efeitos sem luzes se comportam exatamente como na 0.8: sem passe novo.

## Conceitos

| Forma | Fábrica | Ilumina |
|---|---|---|
| Ponto | `Light.point(ponto)` | Em todas as direções até `radius` blocos. |
| Segmento | `Light.segment(de, até)` | A partir do ponto mais próximo do segmento, até `radius` blocos dele. |
| Feixe | `Light.along(feixe)` | Como o segmento, usando as pontas, a revelação, o peso e a cor do halo de um `Beam` do mesmo efeito: a luz cresce e some junto com o feixe. |
| Disco no chão | `Light.disc(centro, raio)` | Um disco de `raio` blocos em volta do centro, com borda suave de `width` blocos (padrão 25% do raio). |
| Anel no chão | `Light.ring(centro, raio, largura)` | Uma faixa de `largura` blocos no `raio`: ondas de choque, com o raio vindo de uma trilha. |

| Parâmetro | Unidade e faixa | Efeito |
|---|---|---|
| `color` | ARGB (alfa ignorado) | Cor da luz. Padrão branco; em `along`, a cor do halo do feixe. |
| `intensity` | 0..8 | Brilho. De noite, 2 a 3 ficam legíveis. |
| `radius` | blocos, 0..128 | Alcance (ponto, segmento, feixe) ou raio (disco, anel). |
| `falloff` | 0,25..4 | Expoente de `(1 − distância / alcance)`: 1 linear, 2 suave (padrão), 4 concentrado. |
| `width`, `height` | blocos | Largura do anel ou borda do disco; alcance vertical do disco e do anel acima e abaixo do centro (padrão 3). |
| `flicker(quantidade, velocidade)` | 0..1; mudanças por segundo, 0..20 | Cintilação por ruído suave (fogo, energia instável); a intensidade fica entre `1 − quantidade` e 1. |

- **Posições** são `Point` (os mesmos das pontas de feixe: centro, mundo, âncora, conjurador, olhos, mira); veja [Feixes](FEIXES-0.9.md#pontas-point). `Param.X/Y/Z` somam um deslocamento vindo de trilha.
- **Trilhas.** Todo parâmetro numérico pode vir de uma trilha: `.track(Light.Param.RADIUS, "onda")`, limitado à faixa. Cores são constantes.
- **Peso.** Envelope da instância × fase opcional (`.weightPhase`) × trilha `WEIGHT` opcional; em `along`, também × o peso do feixe.
- **Faces.** Ponto, segmento e feixe iluminam as faces voltadas para a fonte (cosseno com um envolvimento leve, para o chão em ângulo rasante não ficar preto); faces de costas ficam escuras. Disco e anel iluminam por inteiro as faces voltadas para cima, 40% das paredes e nada dos tetos.

## Exemplo completo

A fogueira da receita 11 é só uma luz, sem geometria, partícula ou shader, acesa até o release:

```java
static final Timeline TIMELINE = Timeline.builder(200)
    .track("chama", keyframe(0, 0f), keyframe(30, 1f, Easing.EASE_OUT), keyframe(40, 1f), keyframe(160, 1f),
        keyframe(200, 0f, Easing.SMOOTH))
    .marker("estalo", 70)
    .sustain(40, 160)
    .build();

static final Light CHAMA = Light.point(Point.center(0, .6, 0))
    .color(0xffff9a40).intensity(3f).radius(10).falloff(1.2f)
    .flicker(.35f, 6)                       // até 35% mais escura, 6 vezes por segundo
    .track(Light.Param.WEIGHT, "chama")
    .build();

// Construtor comum. Ordem: register, timeline, (beams), lights, cues.
Effects.register(ID, new Effects.Definition(200, 4, 0xffff9a40, Effects.Shape.NONE, Effects.Material.NONE, null, null));
Effects.timeline(ID, TIMELINE);
Effects.lights(ID, CHAMA);
```

Uma luz que segue um feixe e uma onda de choque:

```java
Effects.beams(ID, FEIXE);                                                    // primeiro o feixe
Effects.lights(ID,
    Light.along(FEIXE).radius(12).intensity(2f).falloff(1.4f).build(),       // cresce com a revelação do feixe
    Light.ring(Point.center(0, .2, 0), 0, 3).track(Light.Param.RADIUS, "raio").build()); // raio = frente da onda
```

Receitas: [pilar](ai/receitas/08-pilar-de-luz.md), [laser](ai/receitas/09-laser.md) (luz no ponto de impacto), [fogueira](ai/receitas/11-fogueira.md), [onda de luz](ai/receitas/12-onda-de-luz.md), [protagonista](ai/receitas/13-protagonista.md). O Sinal de Maré 1.6.0 ganha uma luz breve verde-água no impacto.

## API

### Montagem

| Chamada | Regras |
|---|---|
| `Light.point(ponto)` | Padrão: branca, intensidade 1, alcance 8, queda 2. |
| `Light.segment(de, até)` | Dois `Point` diferentes; alcance padrão 4. |
| `Light.along(feixe)` | O feixe precisa estar em `Effects.beams` do mesmo efeito, registrado antes. |
| `Light.disc(centro, raio)`, `Light.ring(centro, raio, largura)` | Raio 0..128; largura 0..64. |
| `color`, `intensity`, `radius`, `falloff`, `width`, `height`, `flicker` | Faixas da tabela acima; erro imediato fora delas. |
| `track(Param, "trilha")`, `weightPhase("fase")` | `Param`: `INTENSITY`, `RADIUS`, `FALLOFF`, `WIDTH`, `HEIGHT`, `FLICKER`, `FLICKER_SPEED`, `X`, `Y`, `Z`, `WEIGHT`. |
| `build()` | Light imutável. |

### Registro (`Effects`)

| Chamada | Regras |
|---|---|
| `Effects.lights(id, luzes...)` | 1 a 8 luzes (32 desde a 0.15 fase 3), uma vez por efeito, só no seu namespace. Depois de `register`; depois de `timeline` se alguma luz lê trilhas ou fase; depois de `beams` se alguma segue um feixe (senão `IllegalArgumentException`, também no servidor dedicado). |

### Consultas

| Membro | Resultado |
|---|---|
| `sample(amostrador, envelope, segundos, seed, escalaDaCintilação)` | `Light.State` com parâmetros resolvidos, cintilação aplicada e peso. |
| `Light.flickerFactor(quantidade, velocidade, segundos, seed)` | Multiplicador da cintilação, puro e determinístico. |
| `shape()`, `from()`, `to()`, `beam()`, `color()`… `animated()`, `validate(timeline)` | Inspeção. |

## Como o motor desenha

**Um passe, antes dos efeitos.** A cada quadro, o motor resolve as luzes dos efeitos desenhados (HERO primeiro, depois por peso), até o teto de **8 / 24 / 64** luzes (LOW / BALANCED / HIGH), e enfileira o passe `calamidade_awakening:lights` **antes** do passe do motor, dos passes de addon, dos feixes e da atmosfera. Assim os efeitos ficam por cima da luz e a atmosfera (exposição, tinta, névoa na tela) a cobre na distância. Os dados vão numa textura RGBA32F (4 texels por luz).

**Por pixel:**

1. **Céu:** nunca é iluminado (`skyAt`).
2. **Posição:** `scenePosAt` reconstrói o ponto do mundo que o pixel mostra.
3. **Normal pela profundidade:** a posição dos quatro vizinhos dá a inclinação da superfície; em cada eixo vale a diferença menor, a do lado da mesma superfície, então bordas de blocos e o céu ao lado não estragam a normal (`caNormalFromNeighbors`).
4. **Luz:** atenuação por distância e cosseno com a normal (`caPointLight`, `caSegmentLight`, `caGroundLight`), somadas.
5. **Cor:** a luz é somada sobre um albedo estimado da cena (`caLitColor`: cor^0,4 × 0,8). De noite a cor da cena é quase preta, mas a superfície não; a estimativa realça superfícies escuras sem apagar a textura.
6. **Névoa do jogo:** a luz some junto com a névoa que o jogo usou no quadro (início, fim e forma registrados no evento `RenderFog`, depois de todos os ouvintes). Debaixo d'água, a luz some na distância da água; com a atmosfera, na névoa dela.

**Orçamento.** Igual ao dos feixes: um passe por quadro, pago pelo orçamento HERO quando algum efeito HERO desenhado tem luzes, senão pelo comum; os dois passes do motor (feixes primeiro, depois luzes) são pagos antes dos passes de addon. Em LOW, um efeito com feixe e luz e uma única vaga fica com o feixe. Detalhes em [Prioridade protagonista](PRIORIDADE-0.9.md).

## Acessibilidade

- A luz em si não muda com a intensidade dos efeitos de tela: ela é o aspecto do mundo, como o céu e a névoa da atmosfera.
- A **cintilação** é multiplicada pela intensidade dos efeitos de tela (0 deixa a luz firme) e cai para 1/4 com **"ocultar clarões"**.
- Luzes de impacto (o Sinal de Maré, a onda de luz) sobem em alguns ticks por trilha ou fase, não num degrau.

## Calibre

| Situação | Intensidade | Alcance | Queda |
|---|---|---|---|
| Fogueira, tocha mágica, de noite | 2 a 3 | 8 a 12 | 1,2 a 2 |
| Impacto curto | 2,5 a 3 | 6 a 9 | 1,5 |
| Pilar ou feixe grande (`along`) | 2 | 10 a 16 | 1,3 a 1,5 |
| Onda de choque (`ring`) | 2 a 3 caindo a 0 | frente da onda | largura 2 a 4 |

De dia, a mesma luz quase não aparece, como uma tocha ao sol. Para uma luz que precisa aparecer de dia, aumente a intensidade só numa fase curta.

## Diagnóstico

O F7 ganha a linha **"Luzes: N desenhadas (máx. M) · passe HERO/comum/não"**. O CSV ganha `lights` e `lightPass`. O passe de luzes entra na contagem "passes de pós-processamento".

## Custo

- **Sem luzes:** nada.
- **Com luzes:** um passe de tela cheia com 5 leituras de profundidade por pixel e um laço de até 8/24/64 luzes de poucas operações cada; pixels de céu e já cobertos pela névoa saem cedo.
- **Rede:** nada.

## Limites e cuidados

- **Só o que está na tela:** a luz clareia o que a câmera vê (blocos atrás de outros blocos ou atrás da câmera não existem para o passe) e **não faz sombra**: uma luz do outro lado de um muro também clareia o chão que aparece além dele, se estiver no alcance.
- **Frustum:** como nos feixes, o raio da definição precisa cobrir o alcance da luz (2,5 × raio em volta do centro), senão a luz some quando o centro sai da tela.
- Geometria translúcida (água, vidro) pode receber a luz do que está atrás dela, conforme a profundidade que a RimuLib entrega.
- Shader packs que substituem o pipeline do jogo podem não passar pelo pós-processamento da RimuLib.
- Coberto por testes de lógica (`LightCheck`), pela biblioteca no WebGL2, pela prévia sintética e por um roteiro automatizado em jogo com renderização por software ([VALIDACAO.md](VALIDACAO.md)). GPU física: [TESTE-REAL.md](TESTE-REAL.md), parte 4.

**Desde a 0.10.0:** luzes em pontos do centro e os deslocamentos `X`, `Y` e `Z` giram com a orientação da instância; discos e anéis continuam horizontais (só o centro gira). Veja [INTEGRACAO-0.10.md](INTEGRACAO-0.10.md).
