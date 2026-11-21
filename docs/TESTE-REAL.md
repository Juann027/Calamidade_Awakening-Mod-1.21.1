# Testes em GPU real e multiplayer

Até a 0.4.0, toda a validação gráfica usou Mesa llvmpipe (renderização por software) com servidor integrado. Este roteiro cobre o que faltava: desempenho em placa de vídeo física e sincronização entre dois clientes. Ele usa as ferramentas da 0.5.0 e leva cerca de 40 minutos.

## Ferramentas

| Ferramenta | Onde | Uso |
|---|---|---|
| F7 | Cliente | Painel com GPU, FPS, mediana e p95 do tempo de quadro, perfil, eventos residentes e desenhados, volumes, vértices e passes. |
| `/calamidade_client record <s>` | Cliente | Grava cada quadro em `calamidade_bench/frames-AAAAMMDD-HHMMSS.csv` e mostra mediana e p95 no chat. Até 300 s. |
| `/calamidade bench <n> [efeito]` | Servidor (permissão 2) | Cria n instâncias (1 a 64) num arco 14 blocos à frente do jogador, sem câmera. Padrão: `calamidade_awakening:nexus`. |
| `/calamidade bench stop` | Servidor | Encerra as instâncias do benchmark. |
| `/calamidade status` | Servidor | Instâncias ativas no servidor (limite 64). |

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

**Preparação.** Servidor dedicado NeoForge 21.1.267 com RimuLib, Calamidade 0.8.0 e o vfx-sample 1.5.0, os mesmos JARs nos dois clientes (o protocolo é o da 0.7.0, que acrescentou o canal `release`; use a mesma versão nos dois lados). Para testar sozinho no mesmo PC, use `./gradlew runServer` (ou o instalador de servidor do NeoForge), defina `online-mode=false` em `server.properties` **apenas para este teste local** e abra dois clientes com nomes diferentes: por exemplo, `./gradlew runClient` e uma instância do seu launcher com os mesmos mods. Dê permissão aos dois com `/op`. Chame os jogadores de A (conjura) e B (observa).

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
| 9 | `/calamidade bench 64` e depois A tenta conjurar. | `/calamidade status` mostra 64/64; a conjuração de A é recusada sem erro. Após `bench stop`, volta a funcionar. |
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

## Registro dos resultados

Copie para VALIDACAO.md, na seção da versão testada: modelo da GPU e driver (linha GPU do F7), resolução, a tabela de mediana e p95 por perfil e carga, o resultado de cada caso da parte 2 e o SHA-256 dos JARs testados (`sha256sum dist/*.jar` no Linux, `Get-FileHash` no PowerShell). Guarde os CSVs junto com o relatório.
