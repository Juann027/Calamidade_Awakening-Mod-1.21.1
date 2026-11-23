# Testes em GPU real e multiplayer

Até a 0.4.0, toda a validação gráfica usou Mesa llvmpipe (renderização por software) com servidor integrado. Este roteiro cobre o que faltava: desempenho em placa de vídeo física e sincronização entre dois clientes. Ele usa as ferramentas da 0.5.0 e leva cerca de 40 minutos.

## Ferramentas

| Ferramenta | Onde | Uso |
|---|---|---|
| F7 | Cliente | Painel com GPU, FPS, mediana e p95 do tempo de quadro, perfil, eventos residentes e desenhados, volumes, vértices e passes; desde a 0.9, protagonistas (HERO) e passes que usaram, feixes e luzes desenhados e qual orçamento pagou cada passe. |
| `/calamidade_client record <s>` | Cliente | Grava cada quadro em `calamidade_bench/frames-AAAAMMDD-HHMMSS.csv` e mostra mediana e p95 no chat. Até 300 s. |
| `/calamidade bench <n> [efeito]` | Servidor (permissão 2) | Cria n instâncias (1 a 64) num arco 14 blocos à frente do jogador, sem câmera. Padrão: `calamidade_awakening:nexus`. Desde a 0.10 a fonte é SCRIPT: com a reserva padrão para jogadores, chega a 48 (`playerReserve = 0` na config do servidor libera as 64). |
| `/calamidade bench stop` | Servidor | Encerra as instâncias do benchmark. |
| `/calamidade status` | Servidor | Instâncias ativas no servidor (limite 64); desde a 0.10, por tipo de fonte, os limites da config do servidor e as recusas. |

## Parte 1 — desempenho em GPU física

**Preparação.** Mundo plano novo, criativo, sem outros mods de renderização. Janela 1920 × 1080 (repita depois em 1440p se tiver monitor). Gráficos Fancy, distância de renderização 12, limite de FPS em "Ilimitado" e VSync desligado. Pressione F7 e confira que a linha GPU mostra a sua placa (por exemplo, NVIDIA ou AMD), não "llvmpipe" nem "Microsoft Basic Render".

**Medição.** Para cada perfil (F8 alterna LOW, BALANCED, HIGH) e para cada carga 0, 1, 3, 6 e 12:

1. Fique parado, olhando para a frente, no mesmo lugar em todas as rodadas.
2. Rode `/calamidade bench <n>` (pule para a carga 0).
3. Logo em seguida, rode `/calamidade_client record 8`. O Núcleo do Horizonte dura 10 s, então 8 s cobrem formação, ruptura e eco.
4. Anote mediana e p95 do chat. O CSV fica salvo para análise.
5. `/calamidade bench stop` e espere 2 s antes da próxima rodada.

São 15 rodadas. Repita a carga 6 com `calamidade_awakening:prism` e com `vfxsample:signal` para comparar geometria pura, volume e shader de addon.

**Como ler.** O orçamento de qualidade limita os efeitos desenhados (3/6/12) e os volumes (1/2/4). Com 12 instâncias em BALANCED, a coluna "desenhados" deve parar em 6 e "volumes" em 2. Se o p95 subir muito acima da mediana (mais de 2×), há travadas e não só custo médio. Isso costuma vir de compilação de shader no primeiro uso; repita a rodada para confirmar.

**Variações.** Repita BALANCED com 6 instâncias em Fast e Fabulous, e com Sodium/Embeddium e Iris/Oculus se o seu público usa esses mods. Anote se algum efeito desaparece ou fica com cor errada.

## Parte 2 — dois clientes em servidor dedicado

**Preparação.** Servidor dedicado NeoForge 21.1.267 (ou 21.1.265) com RimuLib, Calamidade 0.10.0 e o vfx-sample 1.7.0, os mesmos JARs nos dois clientes (a 0.10.0 trocou o pacote de início por `event_v2`; use a mesma versão nos dois lados). Para testar sozinho no mesmo PC, use `./gradlew runServer` (ou o instalador de servidor do NeoForge), defina `online-mode=false` em `server.properties` **apenas para este teste local** e abra dois clientes com nomes diferentes: por exemplo, `./gradlew runClient` e uma instância do seu launcher com os mesmos mods. Dê permissão aos dois com `/op`. Chame os jogadores de A (conjura) e B (observa).

| # | Ação | Resultado esperado |
|---|---|---|
| 1 | A conjura o Núcleo do Horizonte com Y; B está a 20 blocos, olhando. | B vê o efeito inteiro, na mesma posição, e ouve os quatro sons. Só A recebe a câmera. |
| 2 | B se afasta a mais de 128 blocos durante o efeito e volta antes do fim. | Ao sair, o efeito some para B; ao voltar, reaparece na fase correta, não do começo. |
| 3 | A conjura; B entra no servidor no meio da execução e vai até o local. | B vê o efeito a partir da fase atual. Não recebe câmera. |
| 4 | A usa `/vfxsample follow` e anda; B observa em F5. | O sinal acompanha A sem atraso perceptível nos dois clientes. |
| 5 | A usa `/vfxsample follow`; durante o efeito, A atravessa um portal do Nether. | O efeito termina para B e não aparece na nova dimensão de A. |
| 6 | A conjura o Sinal de Maré pelo Atlas perto de três mobs e de B. | No impacto (2 s), os mobs e B (com `pvp=true`, o padrão) levam dano leve e são empurrados para longe do centro; A não. Os dois clientes veem o empurrão no mesmo momento do impact frame. |
| 7 | Sem o Focus na mão, A conjura o Núcleo do Horizonte com Y e, logo depois, o Pulso Prismático com K. | O Pulso sai normalmente (recarga por habilidade). Uma segunda tentativa de Núcleo durante os 10 s é recusada. |
| 8 | B conjura enquanto A está em recarga. | B conjura normalmente; a recarga é de cada jogador. |
| 9 | `/calamidade bench 64` e depois A tenta conjurar. | Desde a 0.10: o bench para em 48 (fonte SCRIPT; 16 vagas reservadas para jogadores) e A conjura normalmente. Com `playerReserve = 0` na config do servidor, `/calamidade status` mostra 64/64 e a conjuração de A é recusada sem erro; após `bench stop`, volta a funcionar. |
| 10 | A pressiona O durante o Núcleo do Horizonte. | A volta à própria visão; B continua vendo o efeito até o fim. |
| 11 | Em Mods → Calamidade_Awakening → Configurar, B põe a intensidade em 0 e desliga o tremor; A conjura o Núcleo do Horizonte e o Sinal de Maré. | B vê a geometria e as ondas, sem bloom, clarão, dessaturação nem tremor. A continua vendo tudo. |
| 12 | Em qualidade LOW, A conjura o Sinal de Maré. | O shader do cartão aparece; os anéis dourados não (sem vaga), mas o bloom do pico ainda acontece. O F7 mostra 1 passe de addon. |
| 13 | A conjura o Sinal de Maré pelo Atlas e deixa a câmera rodar; B observa. | Câmera de A alinhada às fases: aproximação até cerca de 1,9 s, corte curto e próximo no impacto (2 s) e travelling lateral no eco até cerca de 5 s. Os anéis ficam mais fortes no impacto e enfraquecem no eco; zoom blur e aberração breves no impacto; dano e empurrão no mesmo instante do impact frame, para A e B. |
| 14 | A roda `/vfxsample farol start` e espera 15 s; B está a 20 blocos. | O feixe acende em 2 s e fica aceso; o anel no chão se abre a cada 2 s, com um som de ametista a cada volta, nos dois clientes. Nada se apaga sozinho depois dos 7 s da definição. |
| 15 | Com o farol em loop, B se afasta mais de 256 blocos e volta; depois B sai do servidor e entra de novo. | Ao voltar ou entrar, B vê o farol aceso e em loop, com o anel na mesma fase que A vê (diferença de até um tick). |
| 16 | A roda `/vfxsample farol release`. | Som de beacon desligando; o feixe apaga em 3 s nos dois clientes, ao mesmo tempo. `/calamidade status` volta ao valor anterior. Um segundo `release` responde que não há farol em loop. |
| 17 | A acende outro farol e roda `release`; B entra no alcance (ou no servidor) durante os 3 s de saída. | B vê o farol já apagando, nunca aceso em loop, e ele termina junto com o de A. |
| 18 | A roda `/vfxsample farol start` e depois `/vfxsample farol stop`. | O farol some na hora, sem a fase de apagar. |
| 19 | Em Configurar, B desliga **Efeitos de movimento**; A conjura o Sinal de Maré. | B não vê o zoom blur no impacto, mas vê a aberração, o bloom e o tremor (se ligado). A vê tudo. Com a intensidade em 0, B também perde a aberração e a vinheta quente do farol. |
| 20 | Opcional, para addons: shader de teste com `uniform vec4 Pontos[4];` que pinta a tela com `Pontos[3]`, preenchido por `u.f4("Pontos[3]", ...)` e, numa segunda rodada, por `u.vec4s("Pontos", dados)`. | O elemento 3 recebe o valor nas duas formas. Registre o driver usado. |

Anote qualquer diferença entre A e B: posição, fase, som, momento do impacto e, no farol, a fase do anel e o momento em que começa a apagar. Uma diferença de até um tick (50 ms) entre clientes é esperada pela latência.

## Parte 3 — atmosfera e receitas (0.8.0)

Mesma preparação da parte 2 (pode ser um cliente só nos casos sem B). Mundo no Overworld, de dia, com terreno variado e água à vista. Deixe o F7 aberto: a linha **"Atmosfera: N ativas · passe sim/não"** é a referência. Capítulo: [ATMOSFERA-0.8.md](ATMOSFERA-0.8.md); receitas: [ai/receitas/](ai/receitas/README.md).

| # | Ação | Resultado esperado |
|---|---|---|
| 21 | A roda `/vfxsample receita ceu` e espera 10 s. | Em ~4 s o céu fica roxo no alto e laranja no horizonte; o sol, a lua e as nuvens continuam visíveis. A névoa quente engrossa até ~8 s e as estrelas aparecem até ~10 s, cintilando. A cena fica um pouco mais escura e quente. F7: "1 ativas · passe sim"; ao fim dos 30 s, volta suave a "0 ativas · passe não". |
| 22 | Durante o caso 21, A olha para o terreno a 30–100 blocos, para a água e para mobs e partículas próximos. | Terreno, água e nuvens distantes recebem a névoa laranja na distância real; mobs e partículas próximos continuam nítidos. Nenhum "contorno" de outra cor no fim da distância de renderização. |
| 23 | Durante o caso 21, A vira de costas para o ponto onde rodou o comando. | O céu continua tingido (a atmosfera não depende do efeito estar na tela). |
| 24 | Com o `ceu` ativo, A conjura o Sinal de Maré. | No impacto (2 s), o céu puxa para o dourado e a cena clareia por cerca de 1 s, depois volta ao roxo e laranja sem salto. F7: "2 ativas" durante o impacto. |
| 25 | `/calamidade bench 6 vfxsample:receita_ceu`. | F7: "6 ativas · passe sim" (só as duas mais fortes entram na mistura, o resultado é o mesmo céu). Anote mediana e p95 com `/calamidade_client record 8` e compare com a carga 0. |
| 26 | B está a 20 blocos de A no caso 21; depois B se afasta além de 384 blocos e, por fim, de 512. | Perto, B vê o mesmo céu que A. Entre 384 e 512 blocos o efeito some aos poucos; além de 512, B vê o céu normal. |
| 27 | Em Configurar, B põe a intensidade dos efeitos de tela em 0; A repete o caso 21 e o 24. | B não vê a cena escurecer nem a tinta quente, nem o clarão do Sinal; vê o céu tingido, as estrelas e a névoa. |
| 28 | B liga "ocultar clarões" (Acessibilidade) e repete o caso 24. | No impacto do Sinal, o brilho de B quase não muda; nenhuma piscada. No `ceu`, a cena escurece aos poucos (cerca de meio segundo), não de uma vez. |
| 29 | B desliga **Névoa da atmosfera pela névoa do jogo** e repete o caso 22. | A névoa continua laranja, agora desenhada na tela: o terreno fica igual, mas partículas e vaga-lumes na frente de terreno distante ficam com a névoa do fundo (esperado nesse modo). Religue depois. |
| 30 | Com o `ceu` ativo, A mergulha na água; depois roda `/effect give @s minecraft:blindness 5`. | Debaixo d'água e cego, vale a névoa do jogo (azul da água, escuridão da cegueira), sem a laranja. O céu tingido volta ao sair da água. |
| 31 | A roda `/vfxsample farol start`, espera 10 s, roda `release`. | Névoa âmbar sobe em ~2 s e fica constante durante o loop (sem pulsar na volta); no release ela some junto com o feixe, sem salto. |
| 32 | A roda cada receita: `/vfxsample receita anel`, `cubo`, `bolha`, `impacto`, `feixe`, `ambiente` (e depois `release` e `stop`). | Cada uma conforme a seção "O que conferir no jogo" do seu arquivo em `docs/ai/receitas/`. Nenhuma mensagem de shader que não compila no log (`latest.log`). |
| 33 | Em LOW (F8), A roda `cubo` e, ao mesmo tempo, conjura o Sinal de Maré. | F7: passes de pós no máximo 1 (orçamento LOW), o resto recusado sem erro; a atmosfera do Sinal não conta no orçamento. |
| 34 | Intensidade 0 com as receitas `impacto` e `feixe`. | `impacto`: nenhum quadro de duas cores e nenhuma vinheta; o tremor segue a opção de tremor. `feixe`: o feixe aparece (é visual do efeito), sem bloom. |
| 35 | **Efeitos de movimento** desligado; A conjura o Sinal de Maré e roda `impacto`. | O Sinal perde o zoom blur (como na 0.7); as receitas não mudam (nenhuma usa zoom ou rastro). |
| 36 | Opcional: `ceu` no Nether e no End. | Registre como o céu fica (a estimativa do céu do jogo foi feita para o Overworld). |

Registre para cada caso o que viu e, nos casos 22, 24 e 25, uma captura de tela.

## Parte 4 — feixes, luzes e protagonista (0.9.0)

Mesma preparação. O que só uma GPU de verdade mostra vem primeiro: a **aparência** dos feixes e da luz (cor, brilho, bordas, cintilação) e o **custo sob carga**. Deixe o F7 aberto: linhas "Protagonistas (HERO)", "Feixes" e "Luzes". Capítulos: [FEIXES-0.9.md](FEIXES-0.9.md), [LUZ-0.9.md](LUZ-0.9.md), [PRIORIDADE-0.9.md](PRIORIDADE-0.9.md).

| # | Ação | Resultado esperado |
|---|---|---|
| 37 | De noite (`/time set midnight`), A roda `/vfxsample receita pilar`. | O feixe desce do céu em menos de 1 s, núcleo branco e halo âmbar, três pulsos descendo. Ao tocar o chão, o terreno em volta acende em âmbar (até ~12 blocos do feixe) e aparece um disco de luz. F7: "Feixes: 1 … passe comum", "Luzes: 2 … passe comum". Captura. |
| 38 | O mesmo de dia. | O feixe aparece (mais claro sobre o céu); a luz no chão é discreta, como uma tocha de dia. |
| 39 | A se posiciona com um morro ou parede entre ele e o pilar. | A parte do feixe atrás do terreno some; a borda do terreno recorta o feixe sem halo "vazando" por cima. O chão escondido não é iluminado. |
| 40 | A roda `/vfxsample receita laser` e vira a câmera devagar e depressa, mirando parede, chão e céu; B observa de lado. | O laser sai abaixo da mira e acompanha a câmera sem atraso visível; o ponto de impacto brilha e cintila. B vê o mesmo laser seguindo a cabeça de A. Em primeira pessoa, o laser é um traço fino saindo de baixo da mira (não um cone); em F5, ele tem a largura real. |
| 41 | `/vfxsample receita teia`, de perto e de longe (60 blocos). | Oito fios saem do centro e chegam às pontas; pulsos ciano correm para fora; ondulação leve presa nas pontas. De longe, os fios ficam com um pixel, sem serrilhar nem piscar. Nenhum anel em volta de fios que apontam para a câmera. |
| 42 | De noite, `/vfxsample receita fogueira`, depois `release`. | Chão e blocos até ~10 blocos em laranja, cintilando; faces de costas para a luz escuras; o céu nunca iluminado. Mobs continuam nascendo em volta (luz visual). `release` apaga em 2 s. |
| 43 | `/vfxsample receita onda_luz`. | O anel de luz no chão acompanha a frente da onda dourada até ~22 blocos; paredes no caminho acendem quando a onda passa. |
| 44 | A mergulha na água e roda `fogueira` e `pilar` com o centro debaixo d'água e depois na margem. | Debaixo d'água a luz some na distância da névoa da água (não atravessa a água turva); o feixe continua visível de perto. Nada fica "aceso" além da névoa. |
| 45 | `/calamidade bench 60` e, logo depois, `/vfxsample receita protagonista`, em BALANCED. | F7: 6 desenhados, "Protagonistas (HERO): 1 desenhadas · 2/2 passes", "Feixes: 7 … passe HERO", "Luzes: 3 … passe HERO". O protagonista aparece inteiro (lança, raios, disco, anel, céu vermelho). `/calamidade_client record 8` com e sem o protagonista: anote mediana e p95. |
| 46 | Repita o 45 em LOW e em HIGH (F8). | LOW: "1/1 passes", feixes sim, luzes não. HIGH: "2/3 passes", tudo. |
| 47 | Dois jogadores rodam `protagonista` em lugares diferentes e um terceiro HERO mais longe. | Os dois mais próximos da câmera são desenhados; o terceiro não (F7: "2 desenhadas"). |
| 48 | Em Configurar, intensidade dos efeitos de tela 0; A roda `pilar`, `teia` e `fogueira`. | Feixes só com o núcleo (sem halo nem pulsos); a luz no chão continua, firme (sem cintilação). |
| 49 | "Ocultar clarões" ligado; A roda `fogueira` e conjura o Sinal de Maré. | A cintilação da fogueira cai para quase nada; a luz verde-água do Sinal sobe em alguns ticks, sem piscar. |
| 50 | Em HIGH, `/calamidade bench 12 vfxsample:receita_teia` (96 feixes) e `/calamidade bench 12 vfxsample:receita_fogueira` (12 luzes); depois `bench 64 vfxsample:receita_teia`. | F7: até 96 feixes e 12 luzes; com 64 teias, "Feixes: 96 desenhados (máx. 128)" (12 desenhados × 8). Anote mediana e p95: é o pior caso realista do passe de feixes. |
| 51 | Com Sodium/Embeddium e Iris/Oculus (se o público usa). | Anote se feixes e luzes aparecem; shader packs que trocam o pipeline podem não passar pelo pós-processamento. |

Registre para cada caso o que viu e, nos casos 37, 40, 42, 45 e 50, uma captura e (45 e 50) os tempos de quadro.

## Parte 5 — orientação, fontes, limites e gatilhos (0.10.0)

Mesma preparação (servidor dedicado, dois clientes A e B). Deixe o F7 aberto: linha nova "Fontes: N jogadores · N mobs · N blocos · N scripts". Capítulo: [INTEGRACAO-0.10.md](INTEGRACAO-0.10.md); receitas 14 a 17.

| # | Ação | Resultado esperado |
|---|---|---|
| 52 | A roda `/vfxsample receita leque` virado para o sul, o leste e o noroeste; B observa de lado. | O leque abre sempre à frente de A; o feixe central aponta para onde a mira de A aponta. B vê o leque na mesma direção. F7: "Feixes: 5", "Luzes: 1", "Fontes: 1 jogadores". |
| 53 | A pega `/give @s vfxsample:cajado` (sobrevivência) e clica com o botão direito olhando reto, para cima e para baixo. | O raio sai da mão direita para onde a mira aponta; a barra mostra a recarga (1,5 s) e um segundo clique não dispara; a durabilidade cai 1 por raio. Um mob no caminho leva dano. B vê o mesmo raio. |
| 54 | A bate num mob com o cajado. | Um risco dourado atravessa o mob na direção do golpe e um anel de luz abre no chão. |
| 55 | A coloca `vfxsample:pilar_periodico` e `vfxsample:pilar_redstone` (virado para A), com uma alavanca ao lado do segundo. | O periódico pulsa a cada 3 s. A alavanca faz o de redstone pulsar na hora e a cada 1 s; o feixe inclinado vai para a face que estava virada para A. Desligar para os pulsos. F7: "Fontes: … blocos". |
| 56 | A quebra o pilar de redstone no meio de um pulso. | O efeito some na hora (não espera o fim do pulso). |
| 57 | A se afasta até o chunk dos pilares descarregar (mais que a distância de simulação; num servidor com `spawnChunkRadius` padrão, construa longe do spawn) e roda `/calamidade status`; depois volta. | Longe: nenhuma instância de bloco. Ao voltar, o periódico volta a pulsar. |
| 58 | A, em sobrevivência, deixa um zumbi bater nele; B observa de lado. | Cada golpe solta um feixe verde curto do peito do zumbi na direção de A. F7: "Fontes: … mobs". `/vfxsample zumbi off` desliga. |
| 59 | Cinco zumbis batendo em A ao mesmo tempo. | `/calamidade status`: nunca mais de 4 instâncias por zumbi; FPS estável. |
| 60 | `/calamidade bench 64`, depois A conjura e roda `/vfxsample receita leque`. | O bench para em 48; A ainda conjura (reserva de jogadores). `/calamidade status`: "48 scripts", "1 jogadores", recusas por reserva contadas. |
| 61 | Na config do servidor (`serverconfig/calamidade_awakening-server.toml`), `maxPerBlock = 1` e `playerReserve = 0`; reinicie o mundo e repita 55 e 60. | O pilar de redstone tem no máximo 1 pulso vivo; o bench chega a 64 e a conjuração de A é recusada sem erro. |
| 62 | Um mod que usa o Calamidade como dependência (`examples/consumidor`, `docs/ai/usar-como-dependencia.md`) com NeoForge 21.1.265: `./gradlew runClient`. | O jogo abre com o consumidor, o Calamidade e a RimuLib; bater num mob solta o feixe do consumidor. |

Registre para cada caso o que viu e, nos casos 52, 53, 55 e 58, uma captura.

## Parte 6 — Terrain API (0.11.0)

Mesma preparação (servidor dedicado, dois clientes A e B, operador nos dois). Use um mundo de teste ou faça backup: os casos mexem no terreno. Deixe o F7 aberto: linhas novas "Terreno: N zonas quentes · N dublês · passe …" e "Malhas de dublê: N · vértices · última montagem". Capítulo: [TERRENO-0.11.md](TERRENO-0.11.md); receitas 18 a 21. Depois de cada caso, `/calamidade terrain undo` devolve o terreno.

| # | Ação | Resultado esperado |
|---|---|---|
| 63 | A roda `/vfxsample receita cratera_simples` num campo; B observa de 20 blocos. | No clarão monocromático do tick 10, o chão vira uma cratera de raio ~7 com borda de blackstone, terra grossa, basalto, magma, pedregulho, tufo e andesito; brasa e fissuras luminosas nas faces, luz laranja, fumaça leve; em ~10 s esfria e fica a cratera queimada. B vê a mesma cratera e o mesmo desenho de borda (mesma seed). Não sobra um quadro com o céu aparecendo no buraco. |
| 64 | `/calamidade terrain list`, depois `/calamidade terrain undo` (e `undo último`, `undo <id>`). | A lista mostra a edição (efeito, posição, blocos, há quantos segundos, quem); o undo devolve exatamente o terreno de antes, com a luz certa. Um bloco que A colocou dentro da cratera antes do undo continua lá. |
| 65 | A roda `meteoro`, `raio_trincheira` (virado para três direções) e `torre_caindo` (virado para dois lados). | Meteoro: esfera em chamas com rastro cai, cratera, esfera de ouro bruto no centro, o dublê esmaece sobre ela. Raio: o rasgo segue a direção do olhar, faixa incandescente no fundo. Torre: a malha cai, a torre fica de pé girada com o olhar (porta para o lado de A), poeira no pouso. F7 na queda: "1 dublês"; torre: "Malhas de dublê: 1". |
| 66 | Antes da cratera, A coloca um baú com itens, uma fornalha acesa, um bloco de bedrock, `reinforced_deepslate` e água ao lado do ponto de impacto; roda `cratera_simples` em cima. | Baú, fornalha (com itens e fogo), bedrock e reinforced deepslate continuam no lugar (podem ficar flutuando); a água da borda fica presa atrás da casca, nada escorre para a cratera. |
| 67 | `/gamerule mobGriefing false`; um mod de teste ou um comando de addon começa uma edição com fonte mob (por exemplo um chefe). Depois `true`. | Com a regra desligada, nenhum bloco muda (`/calamidade status`: recusa por mobGriefing); ligada, a cratera aparece. Edições de jogadores não são afetadas. |
| 68 | Na config do servidor, `terrainEnabled = false`; depois `terrainDimensions = ["minecraft:the_nether"]` no overworld; depois `terrainMaxVolume = 500`. | Cada caso: o efeito toca, nenhum bloco muda, `/calamidade status` conta a recusa (config, dimensão, volume). |
| 69 | Um mod de proteção de terreno (claims) que escute `TerrainEditEvent`, ou um claim existente sobre a área. | A edição dentro do claim é recusada inteira (nenhum bloco); fora dele, normal. |
| 70 | A roda `meteoro` e sai do alcance (mais de 256 blocos, ou troca de dimensão) antes do impacto; volta 3 s depois. B fica perto o tempo todo. | Com B perto, o chunk continua carregado e o commit acontece; A, ao voltar, vê a cratera com a esfera de ouro e o calor ainda esfriando (entrada tardia). |
| 71 | B entra no servidor (login) depois do commit de um `meteoro` de A, ainda no calor. | B recebe o chunk já com a cratera e vê o calor do ponto em que está; nenhum clarão nem dublê "atrasado". |
| 72 | Carga: 5 `cratera_simples` em sequência rápida em lugares diferentes, e uma edição grande (`raio 24`, por exemplo pelo mod de teste ou um addon) perto de A e B. | O servidor não trava (`/calamidade status` mostra "em escrita" por poucos ticks); o terreno aparece em poucos ticks, sob o clarão; nenhum chunk fica com buracos de luz preta. Anote o MSPT (`/tick query` ou F3) e o tempo de quadro de A e B. |
| 73 | Intensidade dos efeitos de tela 0; "ocultar clarões"; "efeitos de movimento" desligado. | Intensidade 0: a cratera ainda aparece com os blocos queimados e o dublê desenhado, mas sem brasa, fumaça, aura nem rastro. Ocultar clarões: sem pulso da brasa nem cintilação do rastro (o clarão do impacto também some, então a troca dos blocos fica visível). Movimento desligado: sem tremor de calor. |
| 74 | Com Sodium/Embeddium (se o público usa). | Anote se as seções reenviadas aparecem na hora (a troca por seção usa o pacote do jogo) e se o passe `terrain` aparece. |

Registre para cada caso o que viu e, nos casos 63, 65, 66 e 72, uma captura e (72) os tempos.

## Parte 7 — compatibilidade da Terrain API (0.11.1)

Antes, rode os testes automáticos (`./gradlew testeJogo` e, numa máquina com tela, `./gradlew testeCliente`); com `-PtesteMods=<pasta com JARs>` eles rodam com outros mods instalados. Os casos abaixo são o que eles não cobrem: mods reais de proteção, jogadores de verdade, GPU física. Mesma preparação da parte 6 (servidor dedicado, A e B, mundo de teste). Capítulo: [TERRENO-0.11.md](TERRENO-0.11.md#compatibilidade-0111).

| # | Ação | Resultado esperado |
|---|---|---|
| 75 | Com **FTB Chunks**, depois com **Open Parties and Claims** e, se usar, **GriefDefender**: B reivindica uma área (com a proteção contra explosões ligada no mod); A roda `cratera_simples` com o centro na borda do claim. Repita com `terrainExplosionEvent = false`. | Ligada: a cratera abre só fora do claim e para na borda dele (os blocos do claim ficam intactos; o calor pode brilhar por cima por alguns segundos). `Start` cancelado por algum mod: nada muda e `/calamidade status` conta "evento de explosão". Desligada: a cratera ignora o claim (só o `TerrainEditEvent` vale). Anote o nome e a versão de cada mod e o que aconteceu. |
| 76 | Com o mod de luz que o servidor usa (ScalableLux, Starlight ou outro), A roda `meteoro` de dia e `cratera_simples` à noite, ao lado de um buraco do mesmo tamanho feito com `/fill ~ ~ ~ ~ ~ ~ air`. | Nenhuma mancha escura ou clara dentro nem em volta das crateras; a cratera tem a mesma luz do buraco do `/fill`. Nenhum erro no log do servidor. Moonrise: o jogo não abre com a RimuLib 1.3.0 (conflito conhecido). |
| 77 | A roda `cratera_simples`; o servidor é parado com `stop` 10 s depois e aberto de novo. | A cratera está lá depois do reinício, com a borda queimada e a luz certa. |
| 78 | Numa vila: A põe uma cama e uma mesa de profissão no caminho de uma cratera, roda a cratera e espera um dia; depois `/calamidade terrain undo`. | Os aldeões perdem a cama e a profissão quando os blocos somem (procuram outros) e as recuperam depois do desfazer. Um portal do Nether com a moldura cavada se desfaz (vanilla). |
| 79 | Com **Distant Horizons** no cliente de A, num servidor **sem** DH: A roda uma cratera grande, se afasta além da distância de renderização e olha para trás; depois reconecta. Repita com DH também no servidor, se usar. | Perto, a cratera aparece sempre. Na distância do LOD, anote se a versão distante mostra a cratera (com DH no servidor, ela deve aparecer depois que o chunk é salvo, segundos depois) ou o terreno antigo, e se muda depois de reconectar. |
| 80 | Com **Sodium** ou **Embeddium** numa GPU física (e, se usar, Iris/Oculus com um shader pack): casos 63 e 72. | A cratera aparece na hora e sem manchas (testado em llvmpipe); anote o tempo de quadro da cratera grande. Com shader pack, anote se o passe `terrain` (brasa, fumaça) aparece. |
| 81 | Servidor dedicado com `spawn-protection=16` e um operador: B (não operador) e A (operador) rodam `cratera_simples` com o centro na borda da área do spawn; depois B em modo aventura roda uma cratera longe do spawn. | B: nada muda dentro da área do spawn, a cratera abre fora. A (operador): a cratera abre inteira. B em aventura: o efeito toca, nenhum bloco muda (`/calamidade status`: recusa por permissão). |

Registre para cada caso o que viu, com nome e versão de cada mod; nos casos 75, 76 e 79, uma captura.

## Parte 8 — custo por efeito e por passe (0.11.2, antes dos perfis novos)

Objetivo: medir na GPU física quanto custa **cada efeito desenhado** e **cada passe de addon**, acima dos tetos de hoje (6 desenhados e 2 passes no BALANCED), para fixar os padrões de LOW, BALANCED, HIGH e ULTRA. A medição é automática: o comando roda sempre a mesma sequência, sob o perfil interno de teste **UNLIMITED** (sem teto de desenhados nem de passes; não aparece no F8), e grava mediana e p95 do tempo de quadro de cada passo. É o mesmo código do teste automatizado (`xvfb-run -a ./gradlew testeMedicao`), então os números se comparam diretamente com os da renderização por software em VALIDACAO.md.

**Preparação (uma vez):**

1. Instale o JAR do motor desta versão, a RimuLib 1.3.0 e o **vfx-sample 1.8.0** (a carga usa as receitas anel, cubo, teia e céu; sem o exemplo, o motor usa os próprios efeitos e a parte de passes não roda).
2. Crie um mundo **superplano** em **criativo, com comandos permitidos**, e saia dele. Em `saves/<mundo>/serverconfig/calamidade_awakening-server.toml`, mude `playerReserve` para `0` (sem isso o bench para em 48 instâncias e o passo de 60 fica incompleto). Entre de novo.
3. Vídeo: **Taxa de quadros máxima: Ilimitada**, **VSync desligado**, gráficos Fancy, a resolução e a distância de renderização que você usa normalmente (anote as duas). Feche navegador, gravador de tela e outros programas pesados.
4. Fique em pé no chão, olhe para o horizonte (um pouco para baixo, para os anéis aparecerem no chão) e não mexa mais o mouse.

**Medição (cerca de 2,5 minutos):**

| # | Ação | Resultado esperado |
|---|---|---|
| 82 | `/calamidade_client debug measure all` e não mexa em nada até a mensagem final. | O F7 abre e mostra "Medição N/12: grupo · passo · aquecendo/gravando". Primeiro o grupo **efeitos**: base sem efeitos, depois 6, 12, 24, 48 e 60 instâncias (anel, cubo, teia e céu alternados, num arco à frente), todas desenhadas. Depois o grupo **passes**: base, e 12 cubos desenhados com o orçamento de passes de addon em 1, 2, 4, 8 e 12. Cada passo aquece 4 s e grava 8 s. No fim: "Medição concluída: 12 passos → cost-AAAAMMDD-HHMMSS.txt", e o bench para sozinho. |
| 83 | Repita o caso 82 uma vez (segunda rodada). | Os números das duas rodadas devem ficar perto; se um passo destoar muito, anote o que estava acontecendo (salvamento automático, outro programa). |
| 84 | Opcional: repita com Sodium ou Embeddium e, se usar, Iris/Oculus com um shader pack. | Anote quais mods e o pack. |

**O que mandar:** os arquivos `calamidade_bench/cost-*.txt` e `cost-*.csv` das duas rodadas (a primeira linha de cada `.txt` já traz a GPU, a janela, o limite de quadros, o VSync e a distância). Para voltar ao normal: `playerReserve = 16` no mesmo arquivo; o perfil de teste termina sozinho no fim da medição (ou com `/calamidade_client debug off`, ou F8).

Comandos avulsos, para olhar uma carga com calma: `/calamidade_client debug unlimited` (sem tetos), `/calamidade_client debug unlimited <desenhados> <passes>` (tetos escolhidos), `/calamidade_client debug off`; `/calamidade bench <n> mix` (anel, cubo, teia e céu alternados; as instâncias recomeçam sozinhas até `bench stop`).

## Parte 9 — Atlas no mundo e qualidade automática (0.11.2)

Antes, os testes automáticos: `./gradlew testeJogo`, depois `xvfb-run -a ./gradlew testeAtlas` e `testeMedicao` (com o vfx-sample compilado). Os casos abaixo são o que eles não cobrem: GPU física, mouse e teclado de verdade, sobrevivência, dois jogadores. Capítulos: [ATLAS-0.11.2.md](ATLAS-0.11.2.md) e [PRIORIDADE-0.9.md](PRIORIDADE-0.9.md).

| # | Ação | Resultado esperado |
|---|---|---|
| 85 | N; ande para trás, para os lados, pule, dê a volta nas placas, vire de costas. | O astrolábio fica parado no mundo: diminui ao se afastar, aparece de lado e (esmaecido) por trás, sai e volta ao campo de visão no mesmo lugar. A abertura mostra as estrelas, o anel crescendo, as linhas e os cartões se formando. |
| 86 | N encostado numa parede, num corredor 1 × 2 e numa sala 3 × 3. | As placas ficam fora da parede (mais perto ou viradas para o lado livre); no corredor aparece "espaço apertado". |
| 87 | Em sobrevivência e em criativo: uma pedra e uma vaca logo atrás de uma placa; clique esquerdo, direito (com blocos na mão) e meio, e segure o esquerdo na placa enquanto vira para um bloco. | Nada quebra, a vaca não leva dano, nenhum bloco é colocado, a mão não balança, o item e o slot não mudam. Soltar o botão longe da placa não minera. Fora das placas tudo volta ao normal. |
| 88 | Abas: Habilidades (conjurar um cartão; usar e roda trocam a página com addons), Qualidade (trocar modo, mover sliders, Restaurar padrões), Acessibilidade (cada opção), Terreno, Diagnóstico (Bench 24, Parar, Gravar 8 s). | Cada mudança vale na hora e fica no `calamidade_awakening-client.toml` depois de reiniciar o jogo. O resultado da gravação (mediana, p95) aparece no painel. Sem permissão 2, os botões de bench ficam apagados. Texto nunca sai dos cartões, em GUI 2, 3 e 4. |
| 89 | B (Recall) olhando para outro lado; segure Alt e clique com o cursor; Alt+Tab com Alt segurado. | O Atlas vem para a frente da mira. Com Alt, a câmera para, o cursor aponta e clica; soltar Alt devolve a câmera sem salto. Voltando à janela, o mouse não fica solto. |
| 90 | Com o Atlas aberto: morra, troque de dimensão, saia do servidor, afaste-se mais de 24 blocos, abra o inventário e a pausa. | Fecha nos quatro primeiros; com inventário e pausa fica visível e não responde. Nenhum erro no log. |
| 91 | AUTO: VSync desligado, FPS ilimitado, `/calamidade bench 48 mix` olhando a carga; depois `bench stop` e `bench 6 mix`. | Com carga pesada o F7 mostra "desceu de …" se o p95 passar de 20 ms (alvo 60 FPS); com folga e o limite cortando efeitos (bench 48 em BALANCED), "subiu de …" depois de uns 8 s. Nunca troca durante `/vfxsample receita protagonista`. Não fica trocando para cima e para baixo. |
| 92 | AUTO numa máquina com placa integrada (se houver). | Desce até LOW ou BALANCED e fica. Anote o FPS. |

Registre para cada caso o que viu; nos casos 85, 87 e 91, uma captura ou vídeo curto.

## Parte 10 — céu, eventos globais e céu por dimensão (0.12.0)

Motor 0.12.0 e vfx-sample 1.9.0 nos dois lados (servidor e clientes: canais novos). Teste de dia e de noite, com F7 aberto. Os casos 99 e 100 precisam de **dois jogadores**.

| # | Passos | Esperado |
|---|---|---|
| 93 | `/vfxsample receita buraco_negro` olhando para montanhas, ao anoitecer. | O buraco surge à frente, cresce até ~64 graus: sombra negra, anel de fótons fino e brilhante, disco largo inclinado atravessando a sombra, metade de trás dobrada por cima; estrelas e cristas distantes entortam em arco sem recorte; o terreno perto não entorta; colapsa com clarão. F7 "Céu: 1 camadas · passe sim". Anote o FPS antes e no auge. |
| 94 | `/vfxsample receita portal_galaxia` e `lua_gigante`. | Portal: fissura que pisca e solta arcos, anel abrindo, galáxia girando por dentro, fecha. Lua: nasce cortada pelo horizonte, cruza o céu com anéis e muda de fase. |
| 95 | Acessibilidade: repita 93 e 94 com intensidade dos efeitos de tela 0, com "ocultar clarões" e com "efeitos de movimento" desligado. | Intensidade 0: sem clarões nem escurecimento; camadas iguais. Ocultar clarões: sem clarão do colapso, fissura sem piscar. Movimento desligado: disco e galáxia parados, lente mais fraca. |
| 96 | De dia, olhando o sol: `/vfxsample evento eclipse start`; espere 20 s. | O sol quadrado do jogo vira um disco redondo; a lua só aparece onde cobre o sol; na totalidade, coroa, estrelas, mundo a ~15% do brilho, raios caindo ao longe (escondidos pelos morros na frente), trovão. F7 "Eventos globais: 1", "Céu: 2 camadas". Nenhum quadrado ou borda onde o sol estava. |
| 97 | Durante a totalidade: vá ao Nether (acima do teto e embaixo), ao End e à dimensão de teste (`/vfxsample dimensao`); morra e renasça; saia e entre de novo no servidor. | O evento aparece em todas (Nether e End: num ponto fixo ao sul; embaixo do teto do Nether, só o escurecimento). Depois do respawn e do relogin, na totalidade de novo, sem recomeçar. |
| 98 | Reinicie o servidor (dedicado) durante a totalidade; entre. `/calamidade world list`. | O eclipse continua na totalidade; a lista mostra o tempo crescendo de onde parou. `/vfxsample evento eclipse release`: o sol volta em 16 s, com o anel de diamante (sem clarão com "ocultar clarões"). |
| 99 | **Dois jogadores**: A no Overworld, B no Nether (ou na dimensão de teste); `start` do eclipse. | Os dois veem o mesmo eclipse ao mesmo tempo, cada um no próprio céu; os raios caem em volta de cada um, no mesmo ritmo. `stop`: some para os dois. |
| 100 | **Dois jogadores** na dimensão de teste: A roda `/vfxsample ceu ameaca`, depois `caos`; B entra na dimensão depois. | Os dois veem a mesma transição de 5 s; B chega no estado atual. Em "caos" o céu do jogo (sol, lua, estrelas) some e fica o do motor. Reinicie o servidor: o estado continua. Volte para o Overworld: céu normal. |
| 101 | `/calamidade world list`, `stop <id>`, `release <id>` sem permissão e com permissão 2; `worldEventsMax = 1` na config do servidor e dois eventos. | Sem permissão, o comando não existe. Com o limite em 1, o segundo evento só entra se tiver prioridade maior (troca o primeiro). |
| 102 | Opcional: com Iris/Oculus e um shader pack. | Anote o que acontece (o pack troca o céu; o sol pode não ser apagado). |

## Parte 11 — chefes com dois jogadores (0.13.0)

Servidor **dedicado** com motor 0.13.0 e vfx-sample 1.10.0, dois clientes (A e B) com os mesmos JARs, os dois operadores, F7 aberto. Cerca de 15 minutos. Anote o resultado de cada caso em VALIDACAO.md (seção 0.13.0) com o SHA-256 dos JARs.

| # | Passos | Esperado |
|---|---|---|
| 103 | A e B juntos: A roda `/vfxsample chefe`; os dois batem no golem até 40% de vida. | Os dois veem a mesma barra segmentada, o nome da fase, as marcas e o fantasma do dano; a troca despertar → combate → (rugido) → fúria aparece para os dois no mesmo momento, com o clarão; eclipse em área para os dois. |
| 104 | **Entrada tardia:** B sai do servidor antes do 103; entra durante a fúria, a menos de 64 blocos. | B vê a barra já na fase "fúria" (vermelha, rótulo certo) em até 0,5 s, sem a barra vanilla por cima. |
| 105 | **Alcance:** B se afasta 100 blocos e volta (andando, não por tp). | A barra de B some ao passar de 64 blocos e volta na fase atual; a de A não muda. |
| 106 | **Logout:** B sai durante a luta e entra de novo perto do golem. | Ao sair, nada fica preso no cliente de B; ao voltar, a barra reaparece na fase atual. A continua vendo a luta normalmente. |
| 107 | **Dimensão do jogador:** B entra num portal do Nether durante a luta e volta. | No Nether, a barra de B some em até 0,5 s (pode aparecer por esse instante); ao voltar e chegar a 64 blocos, ela reaparece na fase atual. A não é afetado. |
| 108 | **Dois chefes:** A roda `/vfxsample chefe` de novo a uns 20 blocos do primeiro. | Duas barras, cada uma com a sua fase; `/calamidade boss list` mostra dois; ferir um não mexe na barra do outro. |
| 109 | **Dimensão do chefe:** `/execute in minecraft:the_nether run tp @e[type=iron_golem,limit=1,sort=nearest] ~ 100 ~` (a partir do Overworld, perto do golem). | No Overworld a barra some para A e B e a aura e o eclipse do golem são soltos (tocam a saída e acabam); quem estiver no Nether perto do destino passa a ver a barra na **mesma fase** e o sustain de novo; `/calamidade boss list` mostra um só controlador desse golem. |
| 110 | **Fim:** mate um chefe (`/kill` no golem) e use `/calamidade boss detach` no outro. | Morte: fase "derrota", o eclipse é solto, a barra some para os dois quando o golem é removido. Detach: a barra some na hora para os dois. Nada fica no céu nem no F7 (linha de chefes vazia). |

## Registro dos resultados

Copie para VALIDACAO.md, na seção da versão testada: modelo da GPU e driver (linha GPU do F7), resolução, a tabela de mediana e p95 por perfil e carga, o resultado de cada caso da parte 2 e o SHA-256 dos JARs testados (`sha256sum dist/*.jar` no Linux, `Get-FileHash` no PowerShell). Guarde os CSVs junto com o relatório.
