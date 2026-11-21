# Beam: feixes de luz (0.9)

Até a 0.8, cada addon que queria um feixe (um laser, um pilar do céu, uma teia de energia) escrevia o próprio shader com `caSegmentGlow`, gastava uma vaga do orçamento de passes **por efeito** e repetia as mesmas contas de largura por pixel e oclusão. A 0.9 transforma o feixe num objeto do motor, o **Beam**: o addon descreve as pontas, as larguras, as cores e o movimento, e o motor desenha os feixes de todos os efeitos do quadro **num passe só**.

O Beam é Java puro, sem tipos do Minecraft, como a Timeline e a Atmosphere. Efeitos sem feixes se comportam exatamente como na 0.8: sem passe novo.

## Conceitos

| Parâmetro | Unidade e faixa | Efeito |
|---|---|---|
| `from`, `to` | `Point` (veja abaixo) | Início e fim do feixe. |
| `coreWidth`, `haloWidth` | blocos (largura inteira), 0..16 e 0..64 | Núcleo nítido e halo suave em volta. |
| `coreColor`, `haloColor` | ARGB (alfa ignorado) | Núcleo branco e halo saturado leem como luz quente. Padrão: núcleo branco, halo ciano. |
| `intensity` | 0..8 | Brilho do feixe inteiro (1 = normal). |
| `pulses(count, speed, size)` | 0..16 pulsos; blocos por tick (−8..8); blocos | Contas de luz que correm pelo feixe, igualmente espaçadas no comprimento. Velocidade negativa corre do fim para o início. |
| `wave(amplitude, wavelength, speed)` | blocos; blocos (0,25..256); blocos por tick | Ondulação lateral que corre pelo feixe. |
| `noise(amplitude)` | blocos, 0..8 | Tremor lateral (feixe instável, eletricidade). |
| `reveal` | 0..1 | Fração desenhada a partir do início: o feixe "nasce" do início ao fim, com a ponta acesa enquanto cresce. |

- **Pontas presas.** Ondulação e ruído somem nos 2 blocos de cada ponta, então um feixe que liga a mão de alguém a um alvo não "solta" das pontas.
- **Trilhas.** Todo parâmetro numérico pode vir de uma trilha da Timeline: `.track(Beam.Param.REVEAL, "nascer")`. O valor é limitado à faixa do parâmetro (`OVERSHOOT` não passa de 1 na revelação). `PULSE_COUNT` é arredondado. `FROM_X`…`TO_Z` **somam** um deslocamento (blocos, eixos do mundo) às pontas resolvidas.
- **Peso.** Envelope da instância (a mesma entrada e saída suaves da opacidade) × peso de uma fase opcional (`.weightPhase("golpe")`) × trilha `Param.WEIGHT` opcional.
- **Movimento contínuo.** Pulsos, ondulação e ruído andam com a idade **real** da instância, então não saltam na volta de um loop (sustain). Velocidade vinda de trilha muda a posição dos pulsos de uma vez; para pulsos suaves, use velocidade constante.

## Pontas: `Point`

Cada ponta é um `Point`, resolvido no cliente a cada quadro (nada vai pela rede):

| Fábrica | Onde fica |
|---|---|
| `Point.center(dx, dy, dz)` | Deslocamento do centro da instância (que já segue a entidade de `Effects.follow`). |
| `Point.world(x, y, z)` | Posição fixa no mundo, igual para toda instância (peças de arena). |
| `Point.anchor(dx, dy, dz)`, `anchorEye(...)` | Pés ou olhos da entidade de `Effects.follow`, mais o deslocamento. |
| `Point.anchorAim(alcance)` | Primeiro bloco que a entidade de `Effects.follow` mira, até `alcance` blocos (0,5..256); a ponta do alcance se não acertar nada. |
| `Point.caster(...)`, `casterEye(...)`, `casterAim(alcance)` | O mesmo para quem conjurou (`Effects.start(id, jogador, posição)` ou cartão). |

Deslocamentos usam os eixos do mundo, em blocos (até 1024). Pontas de entidade usam a posição interpolada, então acompanham o movimento sem tremer. **Enquanto a entidade não está carregada num cliente** (longe da visão dele, ou a instância não tem âncora ou conjurador), o feixe não é desenhado nesse cliente. A mira é calculada pela rotação da cabeça que o jogo já sincroniza: um laser mirado acompanha a câmera de quem atira em todos os clientes.

## Exemplo completo

O pilar da receita 8 desce do céu, ilumina o chão (capítulo [Luz](LUZ-0.9.md)) e não tem shader nem camada de cliente:

```java
static final Timeline TIMELINE = Timeline.builder(140)
    .phase("chao", 16, 140, 4, 50)
    .track("nascer", keyframe(0, 0f), keyframe(18, 1f, Easing.EASE_IN))
    .track("brilho", keyframe(0, 1f), keyframe(18, 1.8f, Easing.OVERSHOOT), keyframe(40, 1.2f, Easing.SMOOTH))
    .marker("chegada", 18)
    .build();

static final Beam FEIXE = Beam.builder(Point.center(0, 120, 0), Point.center(0, 0, 0))  // do céu ao chão
    .width(.7f, 5f)
    .color(0xffffffff, 0xffffb347)
    .pulses(3, 2f, 10f)                      // três pulsos descendo, 2 blocos por tick
    .track(Beam.Param.REVEAL, "nascer")
    .track(Beam.Param.INTENSITY, "brilho")
    .build();

// Construtor comum do mod. Ordem: register, timeline, beams, lights, cues.
Effects.register(ID, new Effects.Definition(140, 48, 0xffffb347, Effects.Shape.NONE, Effects.Material.NONE,
    null, null).withRange(384));             // raio 48: o frustum (2,5 × raio) cobre os 120 blocos
Effects.timeline(ID, TIMELINE);
Effects.beams(ID, FEIXE);
```

Outros: [laser](ai/receitas/09-laser.md) (dos olhos até a mira, preso ao jogador), [teia](ai/receitas/10-teia.md) (8 feixes com pulsos), [protagonista](ai/receitas/13-protagonista.md) (7 feixes num efeito HERO).

## API

### Montagem (`Beam.builder(de, até)`)

| Chamada | Regras |
|---|---|
| `Beam.builder(de, até)` | Dois `Point` diferentes. |
| `width(núcleo, halo)` | Larguras inteiras em blocos: núcleo 0..16, halo 0..64. Padrão 0,2 e 1,2. |
| `color(núcleo, halo)`, `color(halo)` | ARGB. A segunda forma mantém o núcleo branco. |
| `intensity(x)` | 0..8. |
| `pulses(n, velocidade, tamanho)` | n 0..16; velocidade −8..8 blocos por tick; tamanho 0..32 blocos. |
| `wave(amplitude, onda, velocidade)` | 0..8 blocos; 0,25..256 blocos; −8..8 blocos por tick. |
| `noise(amplitude)` | 0..8 blocos. |
| `reveal(fração)` | 0..1 (padrão 1). |
| `track(Param, "trilha")` | Um parâmetro por trilha. `Param`: `CORE_WIDTH`, `HALO_WIDTH`, `INTENSITY`, `PULSE_COUNT`, `PULSE_SPEED`, `PULSE_SIZE`, `WAVE_AMPLITUDE`, `WAVE_LENGTH`, `WAVE_SPEED`, `NOISE`, `REVEAL`, `WEIGHT`, `FROM_X/Y/Z`, `TO_X/Y/Z`. |
| `weightPhase("fase")` | Uma vez só. |
| `build()` | Beam imutável. |

### Registro (`Effects`)

| Chamada | Regras |
|---|---|
| `Effects.beams(id, feixes...)` | 1 a 16 feixes, uma vez por efeito, só no seu namespace. Depois de `register` e, se algum feixe lê trilhas ou fase, depois de `timeline` (senão `IllegalArgumentException`, também no servidor dedicado). Antes de `Effects.lights` quando uma luz segue um feixe. |

### Consultas

| Membro | Resultado |
|---|---|
| `sample(amostrador, envelope)` | `Beam.State`: parâmetros resolvidos, peso e deslocamentos das pontas. `State.visible()` diz se o motor desenha. |
| `from()`, `to()`, `coreWidth()`… `weightPhase()`, `animated()`, `validate(timeline)` | Inspeção. |
| `Point.source()`, `x()`, `y()`, `z()`, `reach()` | Inspeção de uma ponta. |

Assinaturas completas: [ai/API-REFERENCIA.md](ai/API-REFERENCIA.md).

## Como o motor desenha

**Um passe para todos.** A cada quadro, o motor percorre os efeitos desenhados (os HERO primeiro, depois por peso), resolve as pontas e amostra os feixes. Os que passam do teto do quadro (**16 / 48 / 128** em LOW / BALANCED / HIGH) ficam de fora, começando pelos efeitos menos importantes; um efeito que não cabe inteiro fica com os primeiros feixes. Os dados vão numa textura RGBA32F (7 texels por feixe), porque 128 feixes em uniforms passariam do mínimo que o OpenGL garante. O passe `calamidade_awakening:beams` desenha todos de uma vez, depois dos passes de addon e antes da atmosfera.

**Por pixel**, para cada feixe: pontos mais próximos entre o raio do pixel e o segmento (`caRaySegment`), deslocamento lateral (ondulação e ruído), núcleo com **largura mínima de um pixel** (`caBeamCore`: de longe, o feixe fica com um pixel e mais fraco, em vez de engordar ou sumir), halo cortado entre 2,5 e 5 meias-larguras (`caBeamHalo`, para vários feixes não acenderem a tela inteira), pulsos (`caPulseTrain`) e **oclusão pela profundidade** (`caDepthFade`: o terreno na frente esconde o feixe; um feixe que entra no chão some suave). As funções estão em `calamidade_fx.glsl` (versão 2) e têm testes no WebGL2.

**Perto da câmera.** O trecho a menos de ~1 bloco da câmera some aos poucos. E um feixe com uma ponta a menos de 2 blocos da câmera (um laser que sai dos olhos de quem atira) ganha largura angular limitada, ~0,25 grau de núcleo e ~0,7 grau de halo: na tela de quem atira, ele é um traço fino até o alvo, em vez de um cone que sai de baixo da mira. Quem vê de fora, longe da ponta, vê a largura real. O teste em jogo automatizado achou o cone; a prévia `laser` mostra a correção.

**Vistos de frente.** Quando um feixe aponta quase para a câmera, a ondulação e o ruído diminuem sozinhos (entre ~30 e ~12 graus). Sem isso, a posição ao longo do feixe fica mal definida pixel a pixel e a onda vira anéis em volta dele; a prévia em WebGL2 (`scripts/previa_passes.cjs`) achou esse defeito antes do teste em jogo.

**Orçamento.** O passe conta como passe do efeito, uma vez por quadro: é pago pelo **orçamento HERO** quando algum efeito HERO desenhado tem feixes, senão pelo **comum**, quando algum NORMAL tem. Um orçamento nunca paga pelo outro. Pago, o passe desenha os feixes das duas classes. Os passes de feixes e de luzes são pagos antes dos passes de cartão e de `post.pass`, porque um passe serve todos os efeitos. Detalhes em [Prioridade protagonista](PRIORIDADE-0.9.md).

## Acessibilidade

- **Intensidade dos efeitos de tela** (`screenEffects`) multiplica o halo e os pulsos. Em 0, só o núcleo fica: o feixe continua legível como parte do efeito, sem brilho em volta.
- Pulsos não piscam (correm) e não há clarão; "ocultar clarões" não muda o feixe. O clarão do motor no impacto (Impact) segue as regras de sempre.

## Diagnóstico

O F7 ganha a linha **"Feixes: N desenhados (máx. M) · passe HERO/comum/não"**. O CSV de `/calamidade_client record` ganha as colunas `beams` e `beamPass`. O passe de feixes entra na contagem "passes de pós-processamento".

## Custo

- **Sem feixes:** nada.
- **Com feixes:** um passe de tela cheia com um laço de até 16/48/128 feixes; cada feixe custa algumas dezenas de operações por pixel e sai cedo quando o pixel está longe dele. No processador: uma amostragem por feixe, a resolução das pontas (um raio de mira por entidade e alcance por quadro, compartilhado) e o envio de até 3 KB de dados.
- **Rede:** nada. Feixes são registrados nos dois lados e derivados do relógio da instância.

## Limites e cuidados

- **Frustum.** O teste de visibilidade usa o centro da instância e 2,5 × o raio da definição. Um feixe longo precisa de raio grande (o pilar de 120 blocos usa 48), senão o efeito inteiro some quando o centro sai da tela.
- Feixes são aditivos: sobre o céu claro de dia, cores fortes viram quase branco. De noite e contra o terreno, a cor aparece inteira.
- Feixes não fazem sombra nem iluminam nada sozinhos: para isso, `Light.along(feixe)` ([Luz](LUZ-0.9.md)).
- A oclusão usa a profundidade que a RimuLib entrega ao passe: blocos e entidades escondem o feixe; partículas e parte da geometria translúcida (água, vidro) podem não esconder.
- Coberto por testes de lógica (`BeamCheck`), pela biblioteca no WebGL2, pela prévia sintética e por um roteiro automatizado em jogo com renderização por software ([VALIDACAO.md](VALIDACAO.md)). GPU física: [TESTE-REAL.md](TESTE-REAL.md), parte 4.
