# Céu fantástico, eventos globais e céu por dimensão (0.12)

Até a 0.11, o céu de um efeito era o gradiente da Atmosphere: cor, névoa, estrelas. Um buraco negro, um portal ou um eclipse exigiam um shader próprio de tela inteira, que gastava uma vaga do orçamento de addons, só valia para quem estava no alcance da instância e não sabia esconder o sol do jogo. A 0.12 traz três peças:

- **Sky API**: camadas de céu por direção (buraco negro com lente, portal para outra galáxia, eclipse, fenda, astro gigante, aurora e raios ao longe), todas procedurais, com todos os parâmetros vindos de trilhas da Timeline, pintadas num passe único do motor.
- **Eventos globais (`WorldEvents`)**: um efeito visto por todos os jogadores de uma dimensão ou do servidor inteiro, em qualquer dimensão, inclusive por quem entra depois, opcionalmente persistente.
- **Céu por dimensão (`SkyProfiles`)**: um céu persistente para cada dimensão, com estados nomeados e transições suaves, sincronizado e salvo no mundo.

Tudo continua determinístico pelo relógio: nada é enviado por tick. **Servidor e clientes precisam estar na 0.12.0** (canais novos `world_event_v1`, `world_end_v1` e `sky_profile_v1`).

## Sky API

```java
static final Sky BURACO = Sky.blackHole()
    .centerOffset(0, 450, 800)          // 800 blocos à frente e 450 acima, no referencial da instância
    .tilt(80)                           // disco quase de lado
    .track(Sky.Param.SIZE, "tamanho")   // cresce e colapsa pela timeline
    .track(Sky.Param.PROGRESS, "disco")
    .track(Sky.Param.LENS, "lente")
    .build();

Effects.register(ID, definicao);   // ordem: register, timeline, atmosphere, sky
Effects.timeline(ID, TIMELINE);
Effects.sky(ID, BURACO);           // até 6 camadas por efeito
```

### Onde fica (âncora)

| Âncora | Montagem | Uso |
|---|---|---|
| `DIRECTION` | `direction(azimute, elevação)` | Lugar fixo no céu. Convenção do Minecraft: azimute 0 = sul, 90 = oeste, 180 = norte, -90 = leste; elevação 90 = zênite. Eixos do mundo: não gira com a instância. |
| `WORLD` | `toward(x, y, z)` | Um ponto do mundo. Longe (milhares de blocos), quase não se move quando o jogador anda. |
| `CENTER` | `centerOffset(x, y, z)` | Um ponto relativo ao centro da instância, no referencial dela (x esquerda, y cima, z frente): gira com `Placement`. |
| `SUN`, `MOON` | `onSun()`, `onMoon()` | Sobre o sol ou a lua do jogo, seguindo a hora. Nas dimensões sem sol do jogo (Nether, End), num ponto fixo (azimute 0 ou 180, elevação 50). |

As trilhas `AZIMUTH` e `ELEVATION` somam graus à direção da âncora (uma lua que nasce e cruza o céu, receita 24).

### Tipos

| Tipo | Desenho | Tamanho | Primária / secundária | Progresso |
|---|---|---|---|---|
| `BLACK_HOLE` | sombra (1/5 do tamanho), anel de fótons fino e brilhante, disco largo inclinado atravessando a sombra, a metade de trás dobrada por cima, céu em volta engolido, lente | diâmetro do disco | miolo quente / borda do disco | quanto do disco acende |
| `PORTAL` | fissura que pisca e solta arcos (abertura < 0,06), anel com arcos de energia, outro céu por dentro: nebulosa, estrelas densas, galáxia girando | diâmetro aberto | anel / nebulosa | abertura |
| `ECLIPSE` | disco que cobre um sol (redesenhado redondo), coroa e serpentinas na totalidade, proeminências, anel de diamante; sobre a lua, a sombra vermelha da Terra | diâmetro do disco | coroa / último raio de sol (ou umbra) | cobertura |
| `RIFT` | rachadura serrilhada com brilho interno, vazio escuro por dentro, galhos, cintilação | comprimento | brilho / vazio | abertura |
| `CELESTIAL_BODY` | esfera iluminada com fases, borda de atmosfera e, com `tilt`, anéis | diâmetro | superfície / borda e faixas | fração iluminada |
| `AURORA` | cortinas ondulando ao longo do horizonte | largura em azimute | cor de baixo / de cima | atividade |
| `DISTANT_BOLTS` | raios caindo ao longe (70 a 300 blocos) em volta de cada jogador, com clarão no céu | largura do setor (180 = em volta) | núcleo / brilho | atividade (1 ≈ dois por segundo) |

Parâmetros por trilha (`Sky.Param`): `AZIMUTH`, `ELEVATION`, `SIZE` (graus), `ROTATION` (graus), `SPIN` (graus por tick, aplicado à idade real: não salta no laço), `TILT`, `INTENSITY`, `PROGRESS`, `LENS`, `DARKEN`, `WEIGHT`. Peso da camada = envelope da instância × fade nos últimos 25% do alcance × fase opcional × `WEIGHT`.

**Raios ao longe** são determinísticos pela semente e pela idade da instância (`SkyMath.bolts`): todo cliente vê o mesmo ritmo, cada um em volta de si, sem rede. São só visuais; raios reais com dano continuam sendo trabalho de cues.

### Como o motor desenha

**Um passe, o primeiro do quadro.** O passe `sky` vem antes das luzes, dos efeitos, dos feixes e da atmosfera, fora do orçamento de addons, e só existe com camada ativa (ou enquanto o escurecimento volta a 1). Assim, feixes e efeitos ficam na frente do céu e o eclipse escurece o mundo debaixo deles. Por pixel:

1. **Lente** dos buracos negros (`calamidade_sky.glsl`, a mesma conta no passe de atmosfera): `beta = theta (1 - E²/|theta|²)`, sumindo sem quina até o alcance. Pixel de céu continua céu (onde cairia num morro, entra o céu do jogo naquela direção); o terreno distante entorta um terço; o terreno a menos de ~12 blocos não entorta.
2. **Sol e lua do jogo apagados** onde uma camada pede (`hidesVanilla`, padrão das camadas presas ao sol ou à lua, exceto fenda, aurora e raios), e **o céu da Atmosphere** ativa (gradiente, estrelas, névoa no horizonte) pintado aqui, debaixo das camadas. Com o passe `sky` no quadro, o passe de atmosfera não mexe mais nos pixels de céu (só névoa na tela, exposição e tinta).
3. **Escurecimento do mundo** (`darken`; no eclipse, vezes a fração coberta do sol à quarta potência).
4. **Camadas**, só em pixels de céu (os raios também sobre o terreno, escondidos pela profundidade).

**Limites:** 2, 4, 6 e 8 camadas por quadro (LOW, BALANCED, HIGH, ULTRA), eventos globais primeiro (a maior prioridade antes), depois o céu da dimensão, depois HERO, depois peso; 1 lente em LOW, 2 nos outros.

**Por que o céu da atmosfera mudou de passe.** O passe de atmosfera soma ao céu a diferença entre o gradiente e o céu do jogo. Depois do escurecimento do eclipse, essa diferença tirava de novo o brilho do dia; e como o framebuffer do jogo tem 8 bits por canal (corta em 1 entre os passes), a coroa virava um anel laranja e as estrelas sumiam — o teste em jogo mostrou isso, a prévia em ponto flutuante não. Pintando o céu da atmosfera antes do escurecimento e das camadas, o disco negro continua negro e a coroa continua acesa. A prévia agora também usa 8 bits entre os passes.

### O sol e a lua do jogo (conferido com javap)

O NeoForge 21.1 só deixa trocar o céu **inteiro** de uma dimensão: `IDimensionSpecialEffectsExtension.renderSky` devolvendo `true` faz o `LevelRenderer` não desenhar nada do céu. `RenderLevelStageEvent.AFTER_SKY` vem depois e não cancela nada; registrar de novo o efeito do Overworld valeria para todo mundo e o último mod venceria. Por isso:

- **Em qualquer dimensão**, o passe `sky` apaga o sol e a lua subtraindo os mesmos texels que o jogo somou (`SRC_ALPHA, ONE`), no mesmo quadrado (meio lado 30 a 100 blocos para o sol, 20 para a lua, pose `Ry(-90)·Rx(hora·360)`), só quando o caminho vanilla desenhou o sol naquele quadro (céu NORMAL, sem névoa de mundo, sem cegueira, efeitos de dimensão sem `renderSky` próprio). Onde a soma estourou em 1 (o miolo do sol), entra a estimativa do céu do jogo — o disco do eclipse cobre essa parte.
- **Na sua dimensão**, `ProfileSkyEffects` usa `renderSky` para o motor ser dono do céu inteiro (veja "Céu por dimensão").

### Acessibilidade

- **Intensidade dos efeitos de tela** escala o escurecimento (em 0, o mundo não escurece) e os clarões (raios, nascimento do portal, anel de diamante). As camadas em si são o aspecto do céu e ficam, como as estrelas da atmosfera.
- **Ocultar clarões:** o escurecimento muda no máximo 0,025 por tick (a regra da atmosfera); sem anel de diamante, sem clarão do portal, a fissura não pisca, os raios entram e saem sem estrobo, a 60%.
- **Efeitos de movimento** desligado: param o giro do disco, do portal e do astro; a lente cai para metade; a aurora anda a 20%.

## Eventos globais

```java
var eclipse = WorldEvents.start(ECLIPSE, WorldEvents.Scope.SERVER, server.overworld(),
    WorldEvents.Options.defaults().persistent(true).priority(80));
WorldEvents.get(server, ECLIPSE).ifPresent(WorldEvents.Handle::release);   // termina o laço e toca a saída
```

| Escopo | Quem vê |
|---|---|
| `AREA` | Uma instância comum na posição (`Effects.start`): quem está no alcance. |
| `DIMENSION` | Todo jogador na dimensão de origem. |
| `SERVER` | Todo jogador do servidor, em qualquer dimensão, no próprio céu. |

- **O efeito é um efeito comum:** Timeline (com sustain para durar até o `release()`), Sky e Atmosphere funcionam como sempre; os cues rodam no servidor, no nível e na posição de origem. Geometria embutida, camadas de cliente, feixes, luzes e terreno não são desenhados num evento global (pertencem a um lugar: use uma instância `AREA` junto).
- **Relógio e entrada tardia:** o evento corre no tempo do jogo do servidor, que toda dimensão compartilha. Quem faz login, troca de dimensão ou renasce recebe o estado (início, release) e cai na fase certa. A cada tick o servidor confere quem já recebeu cada evento (os três eventos do NeoForge também reenviam na hora).
- **Limite e prioridade:** no máximo `worldEventsMax` (config SERVER, padrão 4, até 16) e um por efeito. Cheio, um início com prioridade maior troca o de menor prioridade (o mais antigo no empate); senão é recusado (`Optional.empty()`). No cliente, camadas de eventos vêm antes das do céu da dimensão e das de efeitos comuns, a maior prioridade primeiro: um eclipse global aparece mesmo em LOW (2 camadas).
- **Persistência:** `persistent(true)` guarda o evento com o mundo (`data/calamidade_awakening_world_events.dat` do Overworld): início, release e progresso dos cues. Ao reiniciar, ele volta na mesma fase (o tempo do jogo também foi salvo) e nenhum cue roda duas vezes. Os outros acabam com o servidor.
- **Comando** (permissão 2): `/calamidade world list`, `/calamidade world stop <id>`, `/calamidade world release <id>` (`<id>` é o efeito `ns:caminho` ou a chave `#3` da lista).
- **F7:** "Eventos globais: N" (os que se aplicam à dimensão do jogador).

## Céu por dimensão

```java
static final SkyProfile CEU = SkyProfile.builder()
    .state("calmo", SkyProfile.State.builder().layers(Sky.aurora().build()).build())
    .state("caos", SkyProfile.State.builder().layers(Sky.blackHole().direction(0, 32).size(60).build())
        .atmosphere(Atmosphere.builder().sky(0xff05020c, 0xff3a1030, 1f).stars(1).build())
        .vanillaSky(false).build())
    .initial("calmo")
    .build();

SkyProfiles.define(ARENA, CEU);                         // construtor comum, dois lados
SkyProfiles.setState(server, ARENA, "caos", 100);       // servidor: 5 s de transição
```

- Cada estado: até 6 camadas constantes (sem trilhas: o perfil não tem timeline; o giro corre no tempo do mundo), atmosfera constante opcional e `vanillaSun`, `vanillaMoon`, `vanillaSky`.
- **Transição:** smoothstep em N ticks (0 a 12000); uma troca no meio de outra parte do estado que aparece mais. Camadas e atmosferas dos dois estados misturadas pelo peso.
- **Sincronia:** `sky_profile_v1`, uma vez por mudança e a quem entra na dimensão depois; **salvo** nos dados da própria dimensão e retomado ao reiniciar.
- **F7:** "Céu da dimensão: <estado>".
- **Perfis em JSON de datapack** ficam para depois (ROADMAP): exigiriam um codec para toda a Sky e a Atmosphere, recarga e o envio do perfil inteiro aos clientes.

### Como criar uma dimensão com céu próprio

1. **Datapack do mod:** `data/<ns>/dimension_type/<nome>.json` com `"effects": "<ns>:<nome>"` e `data/<ns>/dimension/<nome>.json` com o gerador.
2. **Perfil:** `SkyProfiles.define(<ns>:<nome>, perfil)` no construtor comum.
3. **Efeitos da dimensão (cliente, barramento do mod):** `RegisterDimensionSpecialEffectsEvent` → `event.register(<ns>:<nome>, new ProfileSkyEffects(new DimensionSpecialEffects.OverworldEffects()))`. Nos estados com `vanillaSky(false)` (a partir da metade da transição), o jogo não desenha o céu dele e o motor pinta o gradiente da atmosfera do estado (ou as cores do jogo), as estrelas e as camadas. Fora disso, tudo é delegado ao efeito vanilla (névoa, nuvens, chuva, mapa de luz).
4. **Trocar estados** no servidor com `SkyProfiles.setState`.

**Aviso do jogo:** uma dimensão vinda de datapack faz o Minecraft 1.21.1 mostrar, uma vez por mundo, "Os mundos com ajustes experimentais são incompatíveis" ao abrir o mundo (também num mundo antigo que ganhou o mod). "Sei o que estou fazendo!" abre o mundo; a mensagem não volta para ele. Num servidor dedicado não há tela.

Sem o passo 3, `vanillaSun(false)` e `vanillaMoon(false)` funcionam em qualquer dimensão (o passe `sky` apaga o sol e a lua); só o céu inteiro precisa dos efeitos próprios. Exemplo completo: receita 26 e a dimensão `vfxsample:dimensao_teste`.

## Diagnóstico e custo

- **F7:** "Céu: N camadas (M ativas, máx. K) · passe sim/não", "Eventos globais: N", "Céu da dimensão: estado". CSV de `/calamidade_client record` ganha `skyLayers` e `skyPass`.
- **Custo:** um passe de tela cheia quando há camada; cada camada só trabalha perto da própria direção (recorte pelo ângulo antes de montar a base); ruído de 2 a 4 oitavas pelo perfil; a lente custa duas amostras da cena. Medição em VALIDACAO.md (1, 4 e 8 camadas).
- **Rede:** um pacote pequeno por início, release e fim de evento global e por mudança de estado de céu, e um para quem chega depois. Nada por tick.

## Limites e cuidados

- Camadas são pintadas só em pixels de céu: nuvens e terreno na frente as escondem (exceto os raios, sobre o terreno, e a lente, que entorta o terreno distante).
- Sobre o sol, a camada some à noite com ele. Para um eclipse visível de noite, use `direction(...)` e `hidesVanilla(false)`.
- O passe subtrai o sol que o jogo desenhou do jeito vanilla. Shader packs que trocam o céu (Iris/Oculus) e mods que reescrevem `renderSky` fazem o motor não apagar nada (a conferência por reflexão detecta `renderSky` próprio; shader packs não).
- No Nether, o teto esconde o céu: um eclipse global aparece acima dele e, debaixo, só o escurecimento.
- Coberto por `SkyCheck`, `WorldEventCheck`, `WorldEventSyncCheck` e `SkyProfileCheck` no `checkBudget`, 34 casos no `testar_glsl.cjs`, a prévia (`node scripts/previa_passes.cjs ceu`) e os testes em jogo `testeJogo` (eventos e perfis no servidor dedicado, com reinício) e `testeCeu` (cliente). Roteiro manual: TESTE-REAL.md, parte 10.
