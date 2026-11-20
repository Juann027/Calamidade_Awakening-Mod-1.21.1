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

**Preparação.** Servidor dedicado NeoForge 21.1.267 com RimuLib, Calamidade 0.6.2 e o vfx-sample. Para testar sozinho no mesmo PC, use `./gradlew runServer` (ou o instalador de servidor do NeoForge), defina `online-mode=false` em `server.properties` **apenas para este teste local** e abra dois clientes com nomes diferentes: por exemplo, `./gradlew runClient` e uma instância do seu launcher com os mesmos mods. Dê permissão aos dois com `/op`. Chame os jogadores de A (conjura) e B (observa).

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

Anote qualquer diferença entre A e B: posição, fase, som ou momento do impacto. Uma diferença de até um tick (50 ms) entre clientes é esperada pela latência.

## Registro dos resultados

Copie para VALIDACAO.md, na seção da versão testada: modelo da GPU e driver (linha GPU do F7), resolução, a tabela de mediana e p95 por perfil e carga, o resultado de cada caso da parte 2 e o SHA-256 dos JARs testados (`sha256sum dist/*.jar` no Linux, `Get-FileHash` no PowerShell). Guarde os CSVs junto com o relatório.
