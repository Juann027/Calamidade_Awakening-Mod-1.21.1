# Atmosphere: céu, névoa, exposição e tinta (0.8)

Até a 0.7, um addon que quisesse escurecer a cena numa luta de chefe ou tingir o céu num ritual escrevia um shader de tela inteira próprio: gastava uma vaga do orçamento de passes, não conversava com a névoa do jogo e brigava com qualquer outro efeito que fizesse o mesmo. A 0.8 junta isso num objeto só, a **Atmosphere**: o clima da cena enquanto o efeito está ativo. O motor mistura as atmosferas ativas, desenha o resultado num passe único por quadro, fora do orçamento de addons, e usa a própria névoa do jogo para que terreno, entidades, partículas, água e nuvens fiquem coerentes.

A Atmosphere é Java puro, sem tipos do Minecraft, como a Timeline. Efeitos sem atmosfera se comportam exatamente como na 0.7: sem passe novo e sem mudança na névoa.

## Conceitos

| Parâmetro | Unidade e faixa | Efeito |
|---|---|---|
| `skyTop`, `skyHorizon` | ARGB (alfa ignorado) | Gradiente vertical do céu: horizonte até ~37° de elevação, zênite acima. |
| `skyBlend` | 0..1 | Quanto o gradiente substitui a cor do céu do jogo. Sol, lua, nuvens e efeitos desenhados sobre o céu continuam visíveis. |
| `fogColor` | ARGB (alfa ignorado) | Cor da névoa. |
| `fogStart`, `fogEnd` | blocos, 0 ≤ início < fim ≤ 2048 | Onde a névoa começa (limpa) e onde fica opaca. |
| `fogDensity` | 0..1 | 0 deixa a névoa do jogo como está; 1 faz a névoa ir exatamente de `fogStart` a `fogEnd` na cor `fogColor`; valores intermediários aproximam a névoa do jogo das suas distâncias e da sua cor nessa proporção. No céu, é a névoa junto ao horizonte. |
| `exposure` | multiplicador, 0..4 | Brilho da cena inteira (1 = sem mudança). |
| `ambientTint`, `ambientStrength` | ARGB e 0..1 | Tinta que multiplica a cena inteira, normalizada para manter o brilho. |
| `stars` | 0..1 | Campo de estrelas procedural no céu, cintilando. |

- **Trilhas.** Todo parâmetro numérico pode vir de uma trilha da Timeline do efeito: `.track(Atmosphere.Param.FOG_DENSITY, "nevoa")`. O valor da trilha é limitado à faixa do parâmetro (uma trilha com `OVERSHOOT` não passa de 1 na densidade). Cores são constantes.
- **Peso.** Cada instância tem um peso global de 0 a 1: o **envelope** da instância (a mesma entrada e saída suaves da opacidade), vezes o peso de uma **fase** opcional (`.weightPhase("impacto")`), vezes uma trilha `Param.WEIGHT` opcional, vezes um fade por distância (1 até 75% do alcance da definição, caindo a 0 no alcance). A atmosfera não depende de o efeito estar na tela: virar de costas não apaga o céu.
- **Mistura.** Com várias atmosferas ativas, o motor usa as **duas de maior peso** (empate: a mais antiga) e as interpola pelo peso:
  - peso final = `min(1, pa + pb)`, então uma troca (uma saindo enquanto a outra entra) mantém a força total;
  - quantidades (`skyBlend`, `fogDensity`, `ambientStrength`, `stars`, `exposure − 1`) = média ponderada × peso final; uma atmosfera sozinha com peso p vale suas quantidades × p;
  - cores e distâncias de névoa = média ponderada por quanto cada uma usa o recurso (peso × quantidade), então uma atmosfera sem névoa não puxa a cor da névoa da outra.

Nomes de trilha e de fase seguem as regras da Timeline (`[a-z0-9_]`, até 64).

## Exemplo completo

O Sinal de Maré 1.5.0 ganha um céu dourado curto, só na fase `impacto` da sua timeline (ticks 38 a 54, pico no 40, junto do dano):

```java
static final Atmosphere SIGNAL_ATMOSPHERE = Atmosphere.builder()
    .sky(0xff4a3008, 0xffffc060, .35f)   // zênite, horizonte, mistura
    .exposure(1.18f)
    .ambient(0xffffd8a0, .12f)
    .weightPhase("impacto")
    .build();

// Construtor comum do mod. Ordem: register, timeline, atmosphere, cues.
Effects.register(SIGNAL, definicao);
Effects.timeline(SIGNAL, SIGNAL_TIMELINE);
Effects.atmosphere(SIGNAL, SIGNAL_ATMOSPHERE);
Effects.cue(SIGNAL, "impacto", Sample::impact);
```

O Farol, efeito ambiente em loop, ganha névoa âmbar enquanto está aceso. A densidade vem de uma trilha com o mesmo valor nas bordas do loop e no início da saída, então nem a volta nem o release saltam:

```java
.track("nevoa", keyframe(0, 0f), keyframe(40, .55f, Easing.SMOOTH), keyframe(80, .55f), keyframe(140, 0f, Easing.SMOOTH))

static final Atmosphere BEACON_ATMOSPHERE = Atmosphere.builder()
    .fog(0xffe0a050, .55f, 6, 48)          // a densidade constante é trocada pela trilha
    .ambient(0xffffc080, .08f)
    .track(Atmosphere.Param.FOG_DENSITY, "nevoa")
    .build();
```

Um efeito pode ter **só** atmosfera, sem camada de cliente nem shader: veja a receita [céu tingido](ai/receitas/07-ceu-tingido.md) (`/vfxsample receita ceu`).

## API

### Montagem (`Atmosphere.builder()`)

| Chamada | Regras |
|---|---|
| `sky(topo, horizonte, mistura)` | Cores ARGB; mistura 0..1. |
| `fog(cor, densidade, início, fim)` | Densidade 0..1; `0 <= início < fim <= 2048` blocos. |
| `exposure(x)` | 0..4. |
| `ambient(cor, força)` | Força 0..1. |
| `stars(x)` | 0..1. |
| `track(Param, "trilha")` | Um parâmetro por trilha; uma trilha pode alimentar vários parâmetros. `Param`: `SKY_BLEND`, `FOG_DENSITY`, `FOG_START`, `FOG_END`, `EXPOSURE`, `AMBIENT_STRENGTH`, `STARS`, `WEIGHT`. |
| `weightPhase("fase")` | Multiplica o peso pelo peso da fase (0 fora dela). Uma vez só. |
| `build()` | Atmosphere imutável. Tudo começa neutro: sem céu, sem névoa, exposição 1, sem tinta, sem estrelas. |

### Registro (`Effects`)

| Chamada | Regras |
|---|---|
| `Effects.atmosphere(id, atmosfera)` | Depois de `register`. Se a atmosfera lê trilhas ou fase, depois de `timeline`, e elas precisam existir nela (senão `IllegalArgumentException` no registro, também no servidor dedicado). Uma por efeito; só efeitos do seu namespace. Não muda a ordem dos cues. |

### Consultas e matemática pura

| Membro | Resultado |
|---|---|
| `sample(amostrador, envelope)` | `Atmosphere.State` da instância naquele instante: parâmetros resolvidos e peso. |
| `Atmosphere.mix(lista)` | Estado misturado (as duas de maior peso). `State.NEUTRAL` sem nenhuma com peso. |
| `State.fogNear(perto)`, `State.fogFar(perto, longe)` | Distâncias que o motor entrega à névoa do jogo. Nunca mais longe que as do jogo. |
| `State.fogColorOver(cor)` | Cor que o motor entrega à névoa do jogo. |
| `State.screenFog(distância)` | Fração de névoa quando o motor a desenha na tela (opção `vanillaFog` desligada). |
| `validate(timeline)`, `animated()`, `track(Param)`, `tracks()`, `weightPhase()` | Inspeção. |

A referência completa, gerada do JAR, está em [ai/API-REFERENCIA.md](ai/API-REFERENCIA.md).

## Como o motor desenha

**Um passe, no fim.** A cada quadro, o motor resolve a atmosfera de toda instância ativa que tem uma (sem frustum nem orçamento) e mistura. Se o peso final passa de 0,001, ele enfileira o passe `calamidade_awakening:atmosphere` **depois** do passe do motor e dos passes de addons, para que exposição e tinta cubram a cena inteira. O passe não consome vaga do orçamento de addons (1/2/4). Sem atmosfera ativa, não há passe.

**Céu.** Nos pixels de céu (`skyAt`), o shader estima a cor que o jogo pintou ali (a cor do céu na câmera, `ClientLevel.getSkyColor`, no alto, e a névoa do quadro junto ao horizonte, como o disco de céu com a névoa `FOG_SKY`) e soma a diferença até o gradiente da atmosfera. Por isso o sol, a lua, as nuvens e efeitos de addon desenhados sobre o céu continuam lá. Estrelas são somadas (`caStars`), e a névoa da atmosfera cobre o céu perto do horizonte (some até ~27° de elevação).

**Névoa: a escolha.** O NeoForge 21.1.267 tem os eventos `ViewportEvent.ComputeFogColor` (cor) e `ViewportEvent.RenderFog` (distâncias, que só valem se o evento for cancelado; conferido no código do `ClientHooks.onFogRender`). O motor usa os dois, com prioridade `LOWEST` (depois dos outros mods):

- `ComputeFogColor`: a cor da névoa do jogo vai em direção a `fogColor` por `fogDensity`. Isso também colore o horizonte do céu, as nuvens e a cor de fundo.
- `RenderFog`, só no modo `FOG_TERRAIN`: início e fim vão em direção a `fogStart` e `fogEnd` por `fogDensity`, nunca mais longe que os do jogo.
- Nenhum dos dois age debaixo d'água, na lava, na neve em pó ou com cegueira ou escuridão: ali a névoa do jogo prevalece.

Por que a névoa do jogo e não só a do passe: o jogo aplica a névoa em cada fragmento, na distância real de cada coisa, inclusive em geometria translúcida (água, partículas, vidro, nuvens). Um passe de tela só conhece a profundidade do que está atrás, então um vaga-lume ou um feixe a 5 blocos na frente de um morro a 60 receberia a névoa do morro. Com os eventos, a névoa fica coerente com a do jogo e as malhas emissivas do motor (que não usam a névoa do jogo) continuam legíveis.

**Plano B na tela.** Alguns shader packs ignoram a névoa do jogo. Para esses casos há a opção de cliente **"Névoa da atmosfera pela névoa do jogo"** (`vanillaFog`, padrão ligado). Desligada, o motor não mexe nas distâncias do jogo e o próprio passe aplica a névoa pela profundidade (`scenePosAt`): `fogDensity × smoothstep(fogStart, fogEnd, distância)`. Nesse modo, geometria translúcida recebe a névoa do que está atrás dela.

**Exposição e tinta.** Depois do céu e da névoa, o passe multiplica a cena pela exposição e mistura a tinta normalizada pela força.

## Acessibilidade

- **Intensidade dos efeitos de tela** (`screenEffects`) escala a exposição (`1 + (exposição − 1) × intensidade`) e a força da tinta. Em 0, a atmosfera não muda o brilho nem a cor da cena; céu, estrelas e névoa continuam, porque são o aspecto do mundo e não um clarão.
- **Ocultar clarões**: a exposição exibida muda no máximo 0,025 por tick (meio ponto de brilho leva cerca de um segundo). Uma exposição de impacto curta, como a do Sinal de Maré, quase não aparece; uma atmosfera longa chega ao valor normal aos poucos. O passe continua até a exposição voltar a 1, mesmo depois de a atmosfera acabar.
- **Névoa da atmosfera pela névoa do jogo**: veja acima.

## Diagnóstico

O painel F7 ganha a linha **"Atmosfera: N ativas · passe sim/não"** (N = instâncias com atmosfera de peso maior que 0,001 no quadro anterior). O CSV de `/calamidade_client record` ganha as colunas `atmospheres` e `atmospherePass`. O passe de atmosfera não entra na contagem "passes de pós-processamento", que continua medindo o motor e os addons.

## Custo

- **Sem atmosfera:** nenhum passe. Os eventos de névoa percorrem a lista de instâncias ativas (até 96) uma vez por quadro e não mudam nada.
- **Com atmosfera:** um passe de tela cheia com poucas operações por pixel; nos pixels de céu com estrelas, quatro hashes. No processador, uma amostragem por instância com atmosfera e uma mistura por quadro, sem alocação além de uma lista pequena.
- **Rede:** nada. A atmosfera é registrada nos dois lados e derivada do relógio da instância, como a Timeline.

## Limites e cuidados

- Só as duas atmosferas de maior peso contam. Uma terceira entra quando uma das duas perde peso.
- A atmosfera vale para todos os jogadores dentro do alcance da definição (`withRange`). Para um clima de área grande, use alcance grande; o fade nos últimos 25% evita um corte seco ao sair.
- A estimativa do céu do jogo é aproximada: cores de nascer e pôr do sol e o céu de outras dimensões podem deixar uma diferença leve no `skyBlend` alto.
- A névoa da atmosfera não age em fluidos, neve em pó, cegueira e escuridão.
- Coberto por testes de lógica (`AtmosphereCheck`, registro e mistura) e por um roteiro automatizado em jogo com renderização por software (VALIDACAO.md). GPU física, multiplayer e os demais casos estão no roteiro de [TESTE-REAL.md](TESTE-REAL.md), casos 21 a 36.
