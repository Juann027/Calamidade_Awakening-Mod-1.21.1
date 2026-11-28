# Validação, medições e limites

## 0.15 Light Engine — phase 5: final quality budgets, English, rebranding preparation (English)

Same branch and environment as phase 4 (**Mesa llvmpipe, software rendering; no physical GPU measurement in this
phase**). The user's manual tests on an RTX 4060 were satisfactory and are enough to continue development, but formal
GPU benchmarks have not been completed or submitted: the phase 5 budgets are requested defaults, not validated
performance targets. Focused validation only; sky, terrain writer, boss and shader code did not change (only text in
`scripts/novo_efeito.py`'s generated shader), so `checkSkyReference`, `testSky`, `testStars`, `testImages`,
`testBackdrop`, `testBoss`, `testOrphans` and `testClient` were not repeated (earlier results reused).

| Test | Result |
|---|---|
| `./gradlew build` | green: `checkBudget` (with the new `ScenarioCheck` and `NoticeCheck`, updated `LimitsCheck` and `CoverageCheck`), `checkFrustum`, `checkShaders` (21 shaders), `checkApiCompat` (0.12.0: 89 classes / 1038 members; 0.13.0: 113 / 1242; nothing removed), `checkApiDocs` (regenerated with English headings), `checkRecipes`, `checkModVersion` (engine 0.15.0, vfx-sample 1.14.1) |
| `./gradlew -p examples/vfx-sample build` | green (1.14.1) |
| `checkShaders` with a freshly scaffolded effect (`novo_efeito.py vfxsample solar_ray --card`, then removed) | 22 shaders compiled; the scaffolded Java compiled against the 0.15.0 JAR without edits |
| `./gradlew testGame` (`testeJogo`) | **82/82** (run here; needed network once to resolve `DevLaunch`); includes `/calamidade world list` showing the translated `loop` state |
| `xvfb-run -a ./gradlew testLight -Pluz.modo=fase4` | **23/23** with the phase 5 budgets (in-game caps LOW 32/24 read back from the config); arena in four profiles with and without coverage, LOW with 1 drawn effect, restore defaults, stability, memory, CRITICAL reticles 60/60 ticks, mixed lighting LOW and ULTRA (ULTRA: 48 of 48 cookies sampled), cinematic, clustered overload, life cycle, F7. Uses the Portuguese command aliases throughout (`luz carga`, `cobertura arena`, `evento eclipse`…), so it also proves legacy command compatibility in game |
| `xvfb-run -a ./gradlew testLight` (default mode) | **16/17**: LOW budget check passes with the new values (24 cookies sampled at most, lights over 32 dropped); **FAIL** "culling: a light outside the frustum is discarded before the cap" (`[1, 0, 0, 1, …]`: the runes projector behind the camera is not culled). The **same check fails on the phase 4 commit `9dbe7ff`** (16/17, `[1, 0, 0, 1, 0, 96, …]`), run in a separate worktree in this session: it predates phase 5 (this mode was last recorded passing at phase 1) and is left open; it was not hidden or changed |

**Budgets.** `LimitsCheck`: LOW 32/24, BALANCED 80/60, HIGH 176/132, ULTRA 192/192; safety maxima 192/192; cookies applied
never above lights. **Migration.** `CoverageCheck`: phase 4 lights (24/64/128) and cookies (16/48/96/128) are earlier
defaults; LOW 20 lights, LOW 12 cookies, HIGH 100 cookies, ULTRA 64 cookies and ULTRA 160 lights are not (they stay);
`CAPS_VERSION` 4; no current default is an earlier one. **ULTRA 192 cookies.** `ScenarioCheck`: 192 spread textured
lights keep all 192 cookies (phase 4: 142). **Coverage scenes** (`ScenarioCheck`, settled selection, phase 4 → 5,
lights / cookies): arena LOW 28→34 / 20→28, combat LOW 28→36 / 16→24, mixed LOW 29→34 / 18→22; BALANCED and above keep
all of combat and mixed; ULTRA keeps every light and cookie of the three scenes; every CRITICAL light and cookie kept in
every profile; phase 5 never keeps less than phase 4. Analysis: `docs/LUZ-0.15.md`, "Phase 5".

**LOW notice.** `NoticeCheck`: manual LOW notifies, other profiles never; no repeat while LOW stays; manual cooldown 60 s;
AUTO entering LOW notifies once, 120 s in LOW without repeats, 200 oscillations BALANCED↔LOW without any notice, a new
notice only after re-arm (30 s above LOW) and cooldown (10 min); a manual notice also holds the AUTO cooldown; forced
profiles never produce an AUTO notice. In game, the notice is a chat message (not verified visually in this phase).

**Commands.** A standalone Brigadier check (brigadier 1.3.10, the version in the game) ran the `Cmd` alias helper:
`light runes`, `luz runas`, `luz runes soltar`, `light runas release`, `light load 128 same spread`,
`luz carga 128 mesmo espalhado`, `luz carga 64 same`, `chefe` and `boss` reach the same commands with the same arguments.
A first version based on Brigadier `redirect` lost the `<n>` argument after an alias (`carga <n> mesmo`); it was replaced
by shared child nodes before any commit.

**Localization.** `en_us.json` and `pt_br.json` of the engine have the same keys (no key missing in `en_us`); the
vfx-sample `en_us.json` has every key used by the code (new keys are English only; `pt_br` falls back to English). A scan
of string literals in `src/main/java` found no remaining Portuguese user-facing text; remaining Portuguese literals are
legitimate: command aliases (`último`, `ultimo`, vfx-sample aliases), persistent identifiers (effect IDs like
`vfxsample:luz_runas`, timeline track names like `entrada`/`giro`, sky state names), optional `pt_br` values and the
historical in-game test mod `src/testeJogo` (Portuguese report text, unchanged except for the strings it matches).
**Docs.** Every relative link and anchor in the edited documents resolves. Chapters still in Portuguese are listed in
the README, "Documentation status".

**Gradle aliases.** `./gradlew tasks --all` lists `testGame`, `testLight`, `testBoss`, `testSky`, `testOrphans`,
`checkSkyReference`…; each depends on its Portuguese task; registration fails the build if an English name collides with
an existing task.

**Not done in this phase:** physical GPU benchmarks; a visual check of the LOW notice and of the Atlas in a real client;
two-player tests; translation of the remaining Portuguese chapters (Timeline, Atmosphere, Beams, Light 0.9, Terrain, Sky,
Custom Sky, Bosses, Integration, recipe pages, this file's history, TESTE-REAL).


## 0.15 Light Engine — fase 4: coverage first (integridade visual dos perfis)

Mesma branch, mesmo ambiente (**Mesa llvmpipe, software, 1280 × 720; nenhuma medida em GPU física**). Testes
direcionados; nada de céu, terreno, chefes ou shaders mudou, então `checkCeuReferencia`, `testeCeu`, `testeEstrelas`,
`testeImagens`, `testeBackdrop` e `testeChefe` não foram repetidos (resultados anteriores reaproveitados; nenhum shader
`.fsh` foi alterado nesta fase).

| Teste | Resultado |
|---|---|
| `./gradlew build` (`checkBudget` com `CoverageCheck` novo: arena de 12 grupos — fase 3 empilha 24 luzes em ≤ 2 grupos, fase 4 cobre os 12 com o mesmo teto; CRITICAL nunca trocada por cobertura; 200 quadros com scores oscilando 10% sem nenhuma troca; densidade: 192 espalhadas admitidas sem passar do limite por tile, empilhadas não, o tile mais carregado do teto nunca sobe, teto total, histerese temporal de 8 quadros; migração 3 reconhece BALANCED 6/24/32, HIGH 12, ULTRA 24, LOW 8 e preserva LOW 1 e ULTRA 64; AUTO: alívio antes da descida, alívio mantido no bloqueio e retirado depois, sem a flag igual à 0.11.2; `checkApiCompat`, `checkShaders`, `checkApiDocs`, `checkRecipes`) | verde |
| `./gradlew testeJogo` | **82/82** (servidor; nenhuma mudança de rede ou servidor) |
| `xvfb-run -a ./gradlew testeLuz -Pluz.modo=fase4` | **23/23** (abaixo) |
| `xvfb-run -a ./gradlew testeLuz -Pluz.modo=fase3` (coverage desligado: o caminho da fase 3) | **19/19**; o A/B de tiles agora repete a captura quando o controle (sem tiles × sem tiles) acusa cena em movimento: espalhado 3 tentativas (controle 6, 152, 64; on × off 0, 16, 0), sobreposto 3 (controle 1, 14, 29; on × off 0, 17, 0). Quando o controle é 0 a diferença é 0; o ruído vem da cena (chunks, entidades), não dos tiles |

**Por que a cena sumia** (reprodução do teste na RTX 4060, arena de 192 luzes = 12 instâncias × 16, 84 cookies):

| Perfil | Fase 3: instâncias que iluminam | candidatas | desenhadas | células da cena | Fase 4: instâncias que iluminam | candidatas | desenhadas | cookies | células |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| LOW | 3 de 12 | 48 | 24 | 8 | **12** (3 + 9 só luz) | **192** | 43 (+19 densidade) | 26 de 84 (+10) | 17 de 24 |
| BALANCED | 8 de 12 | 128 | 64 | 15 | **12** (8 + 4) | **192** | 80 (+16) | 56 de 84 (+8) | **24 de 24** |
| HIGH | 12 | 192 | 128 | 24 | 12 | 192 | 142 (+14) | **84 de 84** | 24 de 24 |
| ULTRA | 12 | 192 | 192 | 24 | 12 | 192 | 192 | 84 de 84 | 24 de 24 |

"Luzes eliminadas antes do passe" (declaradas − candidatas): fase 3 LOW 144, BALANCED 64; fase 4: **0 em todos os
perfis**. Com o config do teste real (LOW com 1 efeito desenhado): fase 3 **16 candidatas** (as 16 de uma instância —
exatamente o F7 relatado), fase 4 **192 candidatas, 38 desenhadas**, tile mais carregado 16 (o teto de 24 do LOW não é
ultrapassado). Capturas: `docs/luz-previas/fase4-arena-perfis.jpg` (os quatro perfis, mesma câmera, fase 3 × fase 4) e
`docs/luz-previas/fase4-cenas.jpg` (caso real, combate, cinema, mista, estresse).

Outras verificações do `fase4`:

- Restaurar padrões do LOW (`ClientConfig.restoreDefaults`): volta a 3 efeitos desenhados, `coverage` intacto.
- Estabilidade: câmera parada, **0** luzes entram ou saem em 60 ticks; câmera girando ±25° por 80 ticks: fase 3 0 de
  24, fase 4 **5 entradas em 48 luzes** (antes da histerese temporal: 136 e depois 56; a admissão agora espera 8
  quadros e tolera 25% a mais para quem já estava).
- Memória: atlas padrão fixo em 22 369 536 bytes, **nenhum upload** em 100 ticks com os 84 cookies da arena.
- Combate (LOW): as 4 luzes CRITICAL das 2 retículas texturizadas em **60/60** ticks com 64 decorativas.
- Iluminação mista (LOW e ULTRA): nenhuma falha de imagem; LOW com 2 fallbacks por orçamento (contados).
- Cinema (LOW): 3 instâncias desenhadas (volume e 6 feixes), 5 só com luz (40 luzes delas no passe).
- Estresse LOW, 192 luzes: grade 48 desenhadas (24 + 24 densidade, máx 9 por tile), anel 45 (+21, máx 9),
  aglomerado 33 (+9, máx 14: o tile cheio não recebe mais).
- Ciclo de vida: `cobertura parar` zera instâncias e luzes. F7 página 2 com as linhas novas; nenhuma linha sai da tela
  (quebra na largura, captura `f4_f7_cobertura`).

Tempo de quadro na arena (llvmpipe, 2 repetições intercaladas de 4 s; **tendência, não medida**): LOW 55–60 ms
(40–48 luzes), BALANCED 65–66 ms (71–77), HIGH 72–81 ms (137), ULTRA 80–84 ms (192); p95 65–97 ms, p99 69–102 ms.
O custo cresce com as luzes desenhadas, como esperado; o custo de **coverage on × off no mesmo perfil** fica para a
RTX 4060 (cenários `f4_arena` × `f4_arena_sem_coverage` do benchmark, TESTE-REAL parte 12).

**Pendente em GPU física:** frametime de coverage on × off por perfil, tiles com luzes espalhadas, sobrepostas e
animadas em 1280 × 720 e 1920 × 1080, AUTO com alívio sob carga real, legibilidade do F7 em outras escalas, revisão
visual de LOW e BALANCED por uma pessoa (a captura de software não substitui o olho). Não há promessa de equivalência
visual absoluta: no LOW a arena aparece inteira, com menos luzes por peça e as peças de trás mais fracas.

## 0.15 Light Engine — fase 3: orçamento adaptativo de luzes

Mesma branch, mesmo ambiente (**Mesa llvmpipe, software, 1280 × 720; nenhuma medida em GPU física**). Testes
direcionados; os históricos sem relação com o passe de luzes não foram repetidos.

| Teste | Resultado |
|---|---|
| `./gradlew build` (`checkBudget`: `LightCheck` fase 3 — atlas em quadtree 512/256/128 sem sobreposição e 16 células de 128 que voltam a ser uma de 512, best fit, seleção por prioridade, histerese sem piscar em 100 quadros alternados, demanda, resolução sem troca enquanto o desejo oscila e uma troca só para um desejo estável, tiles conservadores, máscara de 24 bits exata, redução pela cadeia de mipmaps idêntica ao nível original; `LimitsCheck` fase 3 — orçamentos por perfil, cookies ≤ luzes, construtores antigos com a política antiga, migração distinguível; `checkApiCompat`, `checkShaders` (21 shaders), `checkApiDocs`, `checkRecipes`) | verde |
| Prévia, 30 cenas de luz e feixe (formas da 0.9, cookies da fase 1, modos e filtros da fase 2) × shader da fase 2 | **idênticas pixel a pixel** (caminho abaixo de 17 luzes) |
| `./gradlew testeJogo` | 82/82 (mundo base; sem regressão) |
| `xvfb-run -a ./gradlew testeLuz -Pluz.modo=fase3` | **19/19** (abaixo) |

`testeLuz -Pluz.modo=fase3`:

- Limites efetivos: LOW 24/16, BALANCED 64/48, HIGH 128/96, ULTRA 192/128.
- Cargas exatas em ULTRA: 16, 32, 64, 96, 128 (mesmo PNG) e 192 mista (96 cookies + 96 convencionais) — todas
  pedidas, nenhuma fora da visão, todas desenhadas, cookies = min(128, pedidos). 128 cookies do mesmo PNG = 1 imagem,
  nenhum upload em 100 ticks.
- HIGH com 128 cookies: 96 amostrados, 32 contados como "acima do orçamento" (motivo separado no F7).
- Prioridade no LOW: retícula `CRITICAL` (2 luzes) texturizada em 60/60 ticks contra 28 decorativas; 16 amostrados.
- Tiles × laço completo, 64 luzes espalhadas e 64 sobrepostas: **capturas idênticas** (diferença máxima 0; controle
  sem tiles × sem tiles também 0). Luzes por tile: média 6,1 (espalhadas) e 8,0 (sobrepostas, máximo 64).
- Pressão do atlas (`adaptativa`, 32 PNGs de 512): 17 imagens em 128, 6 em 256, 9 em 512; 13 entraram reduzidas,
  14 trocas até assentar, nenhuma luz no fallback; atlas padrão fixo em 22 369 536 bytes; **nenhuma troca nem upload
  nos 400 ticks seguintes**.
- Recarga de recursos solta os atlas.

Mediana do tempo de quadro com e sem tiles (llvmpipe, 3 s cada: **tendência, não medida**):

| Cena (HIGH, 64 luzes) | Com tiles | Sem tiles |
|---|---|---|
| espalhadas | 74,8 ms | 241,0 ms |
| sobrepostas | 83,2 ms | 236,4 ms |

Em software o passe domina o quadro, então a queda é exagerada em relação a uma GPU; o sentido (o laço por pixel era o
gargalo com dezenas de luzes) é o esperado. As sobrepostas também caíram porque o resto da tela (a maior parte) deixou de
percorrer as 64 luzes. A viabilidade dos orçamentos 128/192 em GPU real é a medida pendente:
[TESTE-REAL.md, Parte 11, fase 3](TESTE-REAL.md#parte-11--benchmark-da-light-engine-0150-rtx-4060).

**Encontrado e corrigido nos testes:** (1) o pixel achava o tile pelo `texCoord` e as luzes eram cortadas numa fronteira
de linha de tiles: o pixel agora projeta a própria posição com a mesma matriz da CPU (concordância por construção); (2)
`int(m + 0,5)` acima de 2²³ arredondava o bit 0 das máscaras (luzes sumiam); agora `int(m)`; (3) o despejo do atlas
cheio removia entradas do mapa durante a iteração (`ConcurrentModificationException`), defeito antigo que só a pressão
do atlas expôs.

Capturas: [luz-previas/fase3-0150.jpg](luz-previas/fase3-0150.jpg) (128 cookies em ULTRA, prioridade no LOW, 64
espalhadas com tiles, adaptativa).

**Não validado aqui:** desempenho em GPU real (orçamentos propostos); nitidez da troca de resolução em movimento
(verificada por construção e por captura parada); a migração da config num arquivo antigo real (lógica coberta pelo
`LimitsCheck`, mas não por um cliente com um `calamidade_awakening-client.toml` de 0.14).

## 0.15 Light Engine — fase 2: composição avançada e nitidez

Mesma branch, mesmo ambiente (**Mesa llvmpipe, software; nenhuma medida em GPU física**).

| Teste | Resultado |
|---|---|
| `./gradlew build` (`LightCheck` com a fase 2: enums só crescem no fim, defaults, `pixelated()` = `PIXELATED`, faixas, trilhas `BLEND_STRENGTH`, potência de 2, orçamento de resolução, atlas grande, imagem 222×192 guardada 256×256 sem borda; `checkApiCompat`, `checkShaders`, `checkApiDocs`, `checkRecipes`) | verde |
| Prévia, formas da 0.9 (`fogueira`, `pilar`, `onda`, `onda_dia`, `laser`) × shader da 0.14 | idênticas pixel a pixel |
| Prévia, cenas de cookie da fase 1 (`cookie_*`, `holofote`, `curvas`, `gradiente`, `subtrair_dia`) × shader da fase 1 | idênticas pixel a pixel |
| Prévia, `OVERRIDE` força 0 × cena sem luz | idênticas pixel a pixel (transparência e força 0 não mexem na superfície) |
| Prévia, filtros ampliados | SMOOTH borra, SHARP com núcleo duro e ~1 px de borda, PIXELATED em degraus |
| `xvfb-run -a ./gradlew testeLuz -Pluz.modo=fase2` | 19/19: cookie padrão não cria o atlas grande (21,3 MB); HIGH: a luz ULTRA usa 2048 e a STANDARD do mesmo PNG fica em 512 (42,7 MB); nenhum upload repetido; BALANCED refaz em 1024; LOW solta o atlas grande; as dez demos novas de noite e de dia sem falha; recarga solta os dois atlas |
| `xvfb-run -a ./gradlew testeCeu` (regressão do céu depois das mudanças de ciclo de vida) | 20/20 |
| Benchmark focado (`-Pluz.modo=bench -Pluz.reps=2 -Pluz.amostra=6`, 7 cenários) | abaixo |

Capturas: [luz-previas/fase2-0150.jpg](luz-previas/fase2-0150.jpg).

Benchmark focado (mediana das medianas, ms, 2 repetições curtas: **tendência, não medida**; carga constante em todas as
janelas, conferida quadro a quadro):

| Cenário | LOW | BALANCED | HIGH | Carga |
|---|---|---|---|---|
| vazio | 22,5 | 23,6 | 21,9 | sem passe |
| 8 luzes convencionais | 55,9 | 59,1 | 59,2 | 3 / 8 / 8 luzes |
| 1 cookie ADD | 66,2 | 60,9 | 59,3 | 1 luz |
| 3 projetores ADD + EMISSIVE + OVERRIDE | 63,5 | 66,1 | 66,3 | 3 luzes (LOW: 2 cookies + 1 fallback) |
| 5 OVERRIDE | 68,0 | 68,3 | 67,6 | 5 luzes (LOW: 2 + 3 fallback) |
| 512 + 2048 (alta resolução) | 59,0 | 60,1 | 61,8 | 2 luzes, 1 em alta resolução (BALANCED 1024, LOW 512) |
| 5 modos sobrepostos | 68,4 | 69,9 | 66,7 | 5 luzes |

Leitura em software: uma luz no caminho estendido custa ~1,5–2 ms por luz em 1280×720 (o laço por pixel), com ou sem
modo novo; EMISSIVE/OVERRIDE não custam mais que ADD; alta resolução não muda o custo por pixel (só a memória). O
caminho convencional continua o da 0.14 (prévia idêntica). Medição definitiva: RTX 4060, [TESTE-REAL.md, Parte 11](TESTE-REAL.md#parte-11--benchmark-da-light-engine-0150-rtx-4060).

**Não feito nesta fase (adiado):** bloom seletivo (precisaria de um buffer HDR; o motor oferece `post.bloom` nas camadas
de addon); versões grandes de 1024 e 2048 da mesma imagem ao mesmo tempo (uma versão grande por PNG); nitidez em
movimento verificada só por construção (faixa de 1 pixel, níveis limitados) e por captura parada: conferir na GPU real.

## 0.15 Light Engine

**Experimental, branch `claude/great-pascal-pq0xtj` (26/11/2026), não publicada.** Ambiente de todas as medidas: container Linux, **Mesa llvmpipe (LLVM 20.1.2, 256 bits), renderização por software**, Xvfb 1280 × 720, mundo plano do `testeJogo`, noite, mesmo ponto e mesma câmera, vfx-sample em `mods/`. **Nenhuma medida em GPU física**: os números abaixo servem para comparar versões no mesmo ambiente e ver tendências; não provam desempenho em hardware real (em GPU, um passe de tela cheia com 64 luzes custa uma fração de milissegundo; em llvmpipe, dezenas).

### Executado nesta sessão

| Teste | Resultado |
|---|---|
| `./gradlew build` (checkBudget com `LightCheck` novo: cookies, folhas, rampas, orientação, atlas; `checkShaders`, `checkApiCompat`, `checkApiDocs`, `checkRecipes`) | verde |
| `node scripts/previa_passes.cjs` — formas da 0.9 no shader novo × shader da 0.14 (`fogueira`, `pilar`, `onda`, `onda_dia`, `laser`) | **idênticas pixel a pixel** (diferença máxima 0) |
| Prévia das cenas novas (`cookie_runas`, `cookie_hex_muro`, `cookie_causticas`, `cookie_vitral`, `holofote`, `curvas`, `gradiente`, `subtrair_dia`) | projeção nítida sobre chão e muro, sem plano flutuante |
| `./gradlew testeJogo` (0.14.0, base para os testes de cliente) | 82 verificações, 0 falhas |
| `xvfb-run -a ./gradlew testeOrfaos` na **0.14.0** (6 casos: fúria, reabrir, kill, sobreposição, stop, `/kill @e` com respawn) | 6/6: o caminho direto do Sentinela **não** reproduz o órfão |
| `xvfb-run -a ./gradlew testeOrfaos` na **0.15.0** (os 6 + dono encerrado com handle perdido, vigia, reconciliação `live_v1`, comandos `fx`/`world list`) | 10/10 |
| `xvfb-run -a ./gradlew testeLuz` na 0.15.0 | 17/17: atlas 0 bytes antes do primeiro cookie, 21,3 MB depois, 1 upload por PNG (nenhum repetido em 100 ticks), PNG ausente → falha contada + fallback sem crash, LOW amostra no máximo 2 cookies, luz atrás da câmera descartada antes do teto, recarga solta o atlas, as 10 demos desenham sem falha |
| Rodada final na 0.15.0 (mudou servidor, rede e chefes): `./gradlew build dist`, `./gradlew -p examples/vfx-sample build`, `./gradlew testeJogo`, `xvfb-run -a ./gradlew testeChefe` | verde; `testeJogo` 82/82, `testeChefe` 15/15 |

Não executados nesta etapa (o código que exercitam não mudou): `checkCeuReferencia`, `testeCeu`, `testeBackdrop`, `testeEstrelas`, `testeImagens`, `testeAtlas`, `testeMedicao`, `testeCliente`.

Capturas das demos (llvmpipe): [luz-previas/demos-0150.jpg](luz-previas/demos-0150.jpg).

### Regressão: 0.14.0 × 0.15.0 sem funcionalidades novas

Mesmo roteiro (`RoteiroLuz`, modo A/B rápido) numa worktree da 0.14.0 (`68fa976`) e na 0.15.0, **intercalados 3 vezes** (0.14, 0.15, 0.14, ...). Janela de 5 s depois de 2,5 s de aquecimento; carga com `/calamidade bench` (as instâncias renascem). Média por rodada em ms:

| Perfil | Cenário | 0.14.0 (3 rodadas) | 0.15.0 (3 rodadas) | Δ médio | Luzes desenhadas |
|---|---|---|---|---|---|
| BALANCED | vazio | 27,3 · 23,6 · 24,0 | 27,3 · 25,3 · 23,1 | +1,1% | 0 |
| BALANCED | 8 fogueiras | 65,0 · 61,7 · 59,2 | 71,1 · 64,6 · 59,5 | +5,0% | 8 |
| BALANCED | 24 fogueiras | 65,7 · 61,6 · 61,3 | 65,0 · 63,4 · 58,2 | −1,1% | 8 (teto) |
| BALANCED | luzes + feixes + shaders | 56,2 · 54,7 · 52,3 | 55,3 · 58,4 · 53,4 | +2,4% | 4 |
| HIGH | vazio | 23,8 · 22,2 · 22,9 | 22,2 · 24,3 · 21,5 | −1,3% | 0 |
| HIGH | 8 fogueiras | 61,7 · 62,0 · 62,0 | 56,6 · 65,2 · 57,4 | −3,5% | 8 |
| HIGH | 24 fogueiras | 67,8 · 68,2 · 68,0 | 68,1 · 78,9 · 66,5 | +4,7% | 16 |
| HIGH | luzes + feixes + shaders | 54,3 · 52,6 · 54,3 | 52,4 · 61,3 · 55,9 | +5,2% | 4 |

Os Δ têm sinais misturados e ficam dentro da dispersão entre rodadas da mesma versão (até ~±8%, por exemplo HIGH 24 fogueiras na 0.15: 66,5 a 78,9). **Conclusão: sem regressão material** no caminho sem luzes novas. Passes por quadro iguais (0 ou 1 de luzes); alocação na thread de render por quadro igual dentro do ruído (≈ 108–185 KB nas duas versões; o extra da 0.15 é a esfera de culling por luz). Uma primeira rodada isolada (não intercalada) mostrou +10% até no cenário vazio, sem passe de luzes: era variação entre execuções, e por isso a medida oficial é a intercalada. Mesmo assim, o shader passou a terminar com a conta exata da 0.14 quando não há luz estendida no quadro.

### Luzes novas (0.15.0, rodada completa, uma execução)

| Perfil | vazio | cookie ×1 | cookies variados (2 a 4 desenhadas) | estresse 4 × 8 luzes (16 cookies) | mistura com cookies | Sentinela na fúria |
|---|---|---|---|---|---|---|
| LOW | 25,2 | 56,3 | 60,6 | 67,3 (8 desenhadas, 2 cookies, 2 fallback) | 63,0 | 79,6 |
| BALANCED | 24,2 | 58,9 | 62,0 | 127,5 (32, 8 cookies, 8 fallback) | 56,5 | 75,5 |
| HIGH | 24,5 | 49,6 | 52,9 | 106,9 (32, 16 cookies) | 51,0 | 69,6 |

p95 e o resto das colunas (luzes, passes, KB alocados, pedidas, descartadas, cookies, fallback, MB do atlas) estão nas linhas `MEDIDA` de `run/teste-luz/calamidade-luz.txt`. Leitura: **uma luz com cookie custa o mesmo que uma convencional** no ruído do llvmpipe (o custo dominante é existir o passe); o estresse com 32 luzes por pixel é o pior caso de software e mostra por que o teto por perfil existe (LOW corta em 8 luzes e 2 cookies, sem passe extra). Memória do atlas: 21,3 MB fixos enquanto houver cookie em uso; uploads: 1 por imagem (22 ao fim do roteiro inteiro, com uma recarga de recursos no meio).

### Auditoria da metodologia do benchmark

Pergunta: por que BALANCED sai mais pesado que HIGH em vários cenários, e LOW às vezes mais pesado que os dois? Resposta:
**a metodologia da rodada completa não separava o perfil do ruído**; nenhum problema do motor foi encontrado, por isso o
motor não mudou.

**1. Limites aplicados (conferidos no código).** `FrameCoordinator.limits()` → `QualityProfile.limits(ClientConfig.caps)`;
o `prepararTesteLuz` apaga `config/`, então valem os padrões: LOW 3 efeitos desenhados / 8 luzes / 2 cookies, BALANCED
8 / 32 / 8, HIGH 16 / 64 / 16 (cookies = `CookieAtlas.textured(luzes)`). O perfil também muda o `tier` que os shaders de
addon leem (passos de raymarch do cubo e da teia), então em `mistura_*` o perfil muda mais que as luzes.

**2. Carga real por cenário.** Vários cenários têm **a mesma carga nos três perfis**, e mesmo assim variam tanto quanto os
perfis entre si — o piso de ruído:

| Cenário (0.15, rodada completa) | Carga em LOW / BAL / HIGH | ms LOW / BAL / HIGH |
|---|---|---|
| vazio | sem passe nos três | 25,2 / 24,2 / 24,5 |
| cookie_1 | 1 luz, 1 cookie nos três | 56,3 / 58,9 / 49,6 (±9% com carga igual) |
| mistura_com_cookies | 2 luzes, 2 cookies nos três | 63,0 / 56,5 / 51,0 (cai na ordem de execução) |
| convencionais_8 | 3 / 8 / 8 luzes | 55,8 / 64,0 / 64,5 (BAL = HIGH, coerente) |
| cookie_estresse_32 | 8 luzes·2 cookies / 32·8+8 fallback / 32·16 | 67,3 / 127,5 / 106,9 |
| chefe_furia | 0 / 1 / 1 luz | 79,6 / 75,5 / 69,6 (luta não determinística) |

A rodada 0.14.0 mostra o mesmo padrão sem nenhum código novo (BALANCED `convencionais_8` 58,2 contra HIGH 53,9 com as
mesmas 8 luzes; BALANCED `vazio` 21,8 contra HIGH 22,3).

**3. Fallback.** Não explica o BALANCED mais caro no estresse: no shader, uma luz em fallback percorre o caminho estendido
**sem** `textureGrad` (mais barato que a amostrada); na CPU, só a mesma consulta de mapa da amostrada. BALANCED tinha 8
amostradas + 8 fallback, HIGH 16 amostradas, com as mesmas 32 luzes: o esperado era BALANCED ≤ HIGH. A diferença é ruído
(40 quadros na janela, p95 tirado de 2 quadros).

**4. Defeitos da metodologia antiga** (`RoteiroLuz.benchmark`):

- **Ordem fixa** LOW → BALANCED → HIGH, cenários sempre na mesma ordem: o LOW recebe o JIT ainda frio, a fila de
  compilação de shaders do driver e os chunks ainda assentando. Na rodada completa da 0.15, a parte funcional antes do
  benchmark inclui `reloadResourcePacks()`, que recompila shaders e solta o atlas: o primeiro perfil medido paga isso.
- **Aquecimento curto:** 2,5 s depois de montar o cenário e 10 ticks depois de trocar de perfil; as fogueiras (200
  ticks, com envelope) e as demos (1 s de entrada) ainda estavam acendendo, e o bench recria instâncias que acabam
  **durante** a janela (a carga oscila).
- **Amostra curta e única:** 5 s, uma janela por perfil (40 a 100 quadros em llvmpipe); p95 de 2 a 5 quadros, sem
  repetição, sem medida de dispersão.
- **Carga lida uma vez:** luzes, passes, cookies e fallback eram lidos **no último quadro** depois da janela, não ao
  longo dela.
- **Cenário não reproduzível:** `chefe_furia` cria um chefe novo por perfil e mede uma luta (disparos a cada 60 ticks
  em momentos diferentes).
- **Montagem por perfil:** cada perfil montava e desmontava o cenário, então a carga não era idêntica entre perfis.
- **Ambiente:** llvmpipe roda o shader na CPU, nos mesmos núcleos do servidor integrado (bench, IA, ticks), do GC e do
  Xvfb; o custo de um passe de tela cheia escala com pixels × luzes de um jeito que não representa uma GPU. O A/B
  intercalado 0.14 × 0.15 (acima) continua válido para "sem regressão" no mesmo ambiente, porque alterna as versões;
  ele **não** serve para comparar perfis.

**5. Metodologia nova** (`-Pluz.modo=bench`, só no roteiro de teste): aquecimento global de todos os cenários; cenário
montado uma vez e medido nos três perfis sobre a mesma carga; ordem dos perfis girada a cada repetição e ordem dos
cenários sorteada (semente fixa); aquecimento de 8 s depois de montar e 3 s depois de trocar de perfil; `System.gc()`
fora da janela; amostra de 15 s gravada quadro a quadro com a carga de cada quadro (faixa min–max por janela); 5
repetições; resumo com mediana das medianas, IQR, mediana dos p95 e p95/p99 de todos os quadros; cenário de chefe fora.
Procedimento para a RTX 4060: [TESTE-REAL.md, Parte 11](TESTE-REAL.md#parte-11--benchmark-da-light-engine-0150-rtx-4060).
Rodada curta de validação em llvmpipe (`-Pluz.reps=1 -Pluz.aquecer=2 -Pluz.trocar=1 -Pluz.amostra=3`; confere o
roteiro, **não é medida**): com a mesma carga nos três perfis, a ordem fica coerente — `convencionais_24` 53,8 / 58,7 /
71,5 ms (3 / 8 / 16 luzes), `cookie_estresse_32` BALANCED 109,6 (8 amostrados + 8 fallback) ≈ HIGH 109,1 (16
amostrados), `vazio` 21,1 / 21,6 / 22,2; a carga de cada janela ficou constante (faixa min–max de um valor só). O
empate no estresse indica que, em software, o custo é o laço por luz e por pixel, não a amostragem do cookie.

### Não medido

Tempo isolado do passe de luzes (sem consulta de tempo da GPU na RimuLib; a diferença entre cenários serve de estimativa), GPU física, dois jogadores reais, shaderpacks, Sodium/Embeddium, o addon BossesTesting (não está neste repositório: os testes usam o Sentinela do vfx-sample e o chefe do mod de teste).

## Candidata ao release 0.14.0 (23/11/2026, não publicada)

Metadados de release: `CalamidadeAwakening.BUILD = "release"` e descrição do `neoforge.mods.toml` sem o aviso de desenvolvimento (o workflow recusa "DEVELOPMENT BUILD"); notas em `.github/release-notes/v0.14.0.md`. Só o texto do log, da lista de mods e do F7 muda: refeitos `./gradlew build dist` e o build do vfx-sample; os testes em jogo da integração abaixo valem sem repetição.

**Correção documental:** a fase 4 registrou `testeImagens` 27/27; o roteiro (`RoteiroImagens.java`, igual nas duas branches) tem 25 verificações, e a integração deu 25/25. O "27/27" foi erro de transcrição (o relatório original não foi guardado); as linhas históricas abaixo e no MANUAL-GERAL foram anotadas.

**Não testado** (nenhuma compatibilidade afirmada): GPU física, dois jogadores reais num servidor dedicado, shaderpacks (Iris/Oculus), renderizadores alternativos (Sodium/Embeddium), mod real com GeckoLib.

## Integração 0.13 + 0.14, branch `claude/integracao-0.13-0.14-u8nraa` (23/11/2026)

Merge comum de `origin/main` (`0fa6bc0`, 0.13.0 publicada) sobre `claude/ceu-personalizado` (`ab2a97d`, 0.14 fases 0–4). Motor **0.14.0** no build (não publicada; JAR de desenvolvimento), vfx-sample **1.11.0** (`[0.14.0,0.15.0)`). Nenhum canal de rede novo: os da 0.13.0, com `boss_v1`. Nova base de compatibilidade `scripts/api-base/api-v1-0.13.0.txt`, gerada do JAR do release v0.13.0 (SHA-256 conferido com o `SHA256SUMS.txt` do release).

| Teste | Resultado |
|---|---|
| `./gradlew build dist` | verde: `checkApiCompat` contra 0.12.0 e 0.13.0, `checkApiDocs`, `checkBudget`, `checkFrustum`, `checkModVersion`, `checkRecipes`; `checkShaders` só avisou (sem glslang nesta máquina) |
| `./gradlew -p examples/vfx-sample build` | verde (`vfx-sample-1.11.0.jar`) |
| `./gradlew compileTesteJogoJava` | verde |

### Validação funcional da integração (executada nesta etapa, 23/11/2026, sobre `14e418f`)

Cliente sob Xvfb (Mesa llvmpipe, por software), vfx-sample 1.11.0; um teste por vez, nenhum em paralelo.

| Teste | Resultado |
|---|---|
| `./gradlew testeJogo` (servidor dedicado, duas fases) | 82/82 |
| `xvfb-run -a ./gradlew testeChefe` | 15/15 |
| `xvfb-run -a ./gradlew testeBackdrop` | 34/34 |
| `xvfb-run -a ./gradlew testeEstrelas` | 20/20 |
| `xvfb-run -a ./gradlew testeImagens` | 25/25 |
| `xvfb-run -a ./gradlew testeCeu` | 20/20 |
| `./gradlew checkShaders` (glslangValidator 11:15.1.0, instalado nesta etapa) | 21 shaders compilados, sem erro |

Nenhuma falha; nenhuma correção foi necessária.

**Reaproveitados das branches de origem (não executados nesta etapa; o merge não mexeu em shaders, passes, escritor de terreno, perfis de qualidade nem Atlas):** `checkCeuReferencia`, `node scripts/testar_glsl.cjs`, `node scripts/previa_passes.cjs`, `testeCliente`, `testeMedicao`, `testeAtlas`.

**Pendentes:** GPU física, Iris/Oculus e Sodium/Embeddium, dois jogadores reais num servidor dedicado (herdados da 0.12, 0.13 e 0.14).

## 0.14 (Custom Sky) — fase 4: imagens, flipbooks e impostors, branch `claude/ceu-personalizado` (23/11/2026)

**O motor mudou (só aditivo):**
- API `SkyImage`, `SkyImpostor`, `Effects.images`, `Effects.impostors` e `SkyProfile.StateBuilder.images/impostors`.
- Puro: `SkySpriteMath`.
- Cliente: `SkyTextures` (substitui o `BackdropTextures`), `SkySprites` e `SkyObjectsFrame`.
- Shaders: o de núcleo `sky_sprite`; `sky_objects.glsl` recebe parâmetros; `sky.fsh` compõe a camada de frente (`CaElFront`, só com `FRONT`).
- Ferramenta: `scripts/gerar_impostor.cjs`.

A versão do build continua 0.12.0. O JAR deste branch é de desenvolvimento (SHA-256 `4039d674…`, contra `a934968a…` do release, intocado junto com a tag `v0.12.0` e a `main`). Arquitetura, ajustes em relação ao plano e guia para addons: [CEU-PERSONALIZADO.md](CEU-PERSONALIZADO.md#fase-4-imagens-flipbooks-e-impostors-implementada).

**Executado nesta sessão** (pela regra de [AGENTS.md](../AGENTS.md#quando-rodar-cada-teste-regra-permanente-desde-a-fase-3-da-014)):

| Teste | Resultado | Observação |
|---|---|---|
| `./gradlew build` | verde | `SkySpriteCheck` novo no `checkBudget`, com casos negativos; `checkShaders` com glslangValidator: 21 shaders; `checkApiDocs` |
| `./gradlew checkApiCompat` | verde | base 0.12.0 inteira presente e compatível (só adições) |
| `node scripts/referencia_ceu.cjs` | 47/47 | as 38 cenas anteriores com diferença **zero** (média 0, pior 0) depois da refatoração de `sky_objects.glsl` e do `sky.fsh`; 9 cenas novas de imagens gravadas numa rodada e reproduzidas iguais na seguinte |
| `node scripts/fundo_composicao.cjs` | 9/9 | igual à fase 2 |
| `node scripts/objetos_composicao.cjs` | 43/43 | os 24 da fase 3 e 19 novos (I1 a I8), cada um com controle negativo que falha como esperado |
| `xvfb-run ./gradlew testeImagens` (novo) | 27/27 registrado; correto: 25/25 | numa só inicialização; ver abaixo e a nota de correção |
| `xvfb-run ./gradlew testeEstrelas` | 20/20 | regressão: `SkyObjectsFrame` mudou |
| `xvfb-run ./gradlew testeBackdrop` | 34/34 | regressão: o cache de texturas do fundo mudou |
| `xvfb-run ./gradlew testeCeu` | 20/20 | regressão: `FRONT` entra no passe `sky` |
| `./gradlew dist` e `./gradlew -p examples/vfx-sample build` | verde | |
| `gerar_impostor.cjs` e `gerar_sprites_teste.py` | ok | montar, validar e exemplo; mensagens para vista faltando, yaw fora do passo, LOD errado, id inválido e PNG corrompido; recursos de teste byte a byte iguais em duas execuções |

**Falhas encontradas e corrigidas na fase** (nenhum teste foi alterado para passar):

1. Na prévia, uma folha de quadros desenhada pequena misturava o quadro vizinho mesmo com 2 px de margem: a margem só protege os primeiros níveis de mipmap.
   - Correção: mipmaps por quadro feitos na CPU e amostragem presa ao nível lido.
   - Prova: caso I6; o controle negativo com mipmaps do driver vaza 49/255.
2. A primeira rodada do `testeImagens` mostrou a imagem com a textura do cubemap. `ShaderInstance.setDefaultUniforms` grava `Sampler0..11` com `RenderSystem.getShaderTexture(i)` (javap `-c`), e o motor definia o amostrador antes dela.
   - Correção: amostradores definidos depois de `setDefaultUniforms`.
   - Prova: a rodada seguinte, completa, passou sem falhas (25 verificações; veja a nota de correção do "27/27").
3. Uma primeira tentativa de interromper essa rodada não parou o processo. A rodada antiga e a nova dividiram `run/teste-imagens`, e o relatório misturado foi descartado. A rodada válida é a completa, sozinha (o relatório desta rodada não foi guardado; o roteiro `RoteiroImagens` tem 25 verificações, idêntico na integração, que deu 25/25: o "27" é erro de transcrição, não duas verificações a mais). Isso é infraestrutura, não um defeito do motor.

**`testeImagens` (llvmpipe, 1280 × 720):**
- Sem addon: nada; nenhuma textura de sprite e nenhuma camada de frente alocada.
- Disco NORMAL com a cor exata (240,48,160) sobre o cubemap; a parte transparente do PNG não toca o fundo (pior 0).
- Silhueta COVER: 502 pixels com a cor, 9 498 com o fundo, 0 na borda.
- Flipbooks por cor sentinela: LOOP `[2, 3, 0, 1, 2, 3]`, ONCE `[3, 3, 3]`, PING_PONG `[2, 1, 0, 1, 2, 3, 2, 1]`, TRACK `[2, 2]`.
- `flashing(true)` congela no quadro seguro com "ocultar clarões" (variação 255 → 0). A folha que pisca sem marca é pega pela rede de segurança. Um flipbook normal continua animando.
- Impostor: a vista certa com a cor exata para as orientações 0, 90 e 180°. LOD: nível 0 grande, nível 1 pequeno.
- `FRONT`: a imagem fica na frente de um portal do motor com a cor exata. `BEHIND`: fica atrás dele.
- Recursos inválidos: PNG ausente, PNG corrompido, grade que não divide a imagem, descritor inválido, página ausente e descritor ausente. São 7 problemas contados uma vez cada, estáveis, e a imagem válida continua.
- Lote: **1,00 chamada de desenho por quadro** para duas imagens da mesma textura.
- Cegueira e `customSkies` desligam as imagens.
- Recarga: as texturas são liberadas e enviadas de novo.
- Memória contada (63 KiB de 256 MiB em HIGH).
- Céu do Overworld igual antes e depois (diferença 0). Capturas: [jogo](ceu-previas/imagens-fase4-jogo.jpg) e [prévia](ceu-previas/imagens-fase4-previa.jpg).

**Custo (software, llvmpipe, só tendência; GPU física pendente).** Mediana / p95 do tempo de quadro no `testeImagens`, com a câmera a 30° acima do horizonte à noite:

| Cenário | Mediana / p95 |
|---|---:|
| Sem objetos | 17,8 / 23,9 ms |
| 1 imagem | 17,1 / 22,4 ms |
| 8 imagens de 3 texturas | 19,2 / 24,6 ms |
| Flipbook | 16,7 / 21,1 ms |
| Impostor | 20,2 / 26,4 ms |
| Portal com imagem `BEHIND` | 51,1 / 58,2 ms |
| Portal com imagem `FRONT` | 54,2 / 60,3 ms |

Leitura:
- As imagens custam perto do ruído entre rodadas (1 a 4 ms).
- O portal domina por software.
- A camada de frente custa perto de 3 ms sobre o passe que já existia.

Os números do `testeEstrelas` desta rodada (por exemplo, atmosfera 44,9 ms) foram medidos com a prévia WebGL rodando ao mesmo tempo na mesma CPU e não são comparáveis com os da fase 3. Nenhum ganho ou limite é afirmado para GPU física. Os limites de envio (1 / 1 / 2 / 2 por quadro, 4 / 8 / 16 / 32 MiB) e de memória (64 / 128 / 256 / 512 MiB) continuam como pontos de partida conservadores. As texturas de teste são pequenas, então o custo de enviar texturas grandes não foi medido: fica pendente.

**Reaproveitado, não executado de novo:**
- `checkObjetosComposicao` 43/43 rodou uma vez nesta fase, no estado atual dos shaders e dos scripts da prévia; nada que ele exercita mudou depois.
- A fase 3 inteira continua registrada abaixo.

**Não rodado, com justificativa:** `testeJogo`. A fase não mexe em servidor, rede, terreno nem estado de servidor. A única execução nesta sessão foi no começo, com o código da fase 3 (`90d4097`), só para criar o mundo que os testes de cliente copiam (o container era novo). Deu 60/61; a falha foi o limite de 50 ms do desfazer da cratera grande (52,4 ms), a falha intermitente já registrada desde a 0.12.0.

**Não testado:** GPU física, Iris/Oculus e shaderpacks, Sodium/Embeddium, outros mods que desenham em `AFTER_SKY`, multiplayer com dois jogadores, texturas de 4 096 px reais (só o cálculo da redução está nos testes puros) e envio de texturas grandes. Nenhuma compatibilidade é afirmada.

## 0.14 (Custom Sky) — fase 3: estrelas, constelações e nebulosas, branch `claude/ceu-personalizado` (23/11/2026)

**O motor mudou (só aditivo):** API `StarField`, `Nebula`, `SkyBlend`, `SkyDarkening`, `Effects.stars`, `Effects.nebulae`, `SkyProfile.StateBuilder.stars/nebulae`; puro `SkyObjectsMath`; cliente `SkyObjectsFrame`; shaders de núcleo `sky_stars`, `sky_nebula` e `include/sky_objects.glsl`; passes `sky` e `atmosphere` com a camada de cobertura (`CaElTex`, só com objetos). A versão do build continua 0.12.0 até o release da 0.14; o JAR deste branch **não** é o do release (SHA-256 `42e73c98…` contra `a934968a…` do release, intocado com a tag `v0.12.0` e a `main`) e agora se identifica como build de desenvolvimento no log, na descrição da lista de mods e no F7. Arquitetura, decisão de composição e limitações: [CEU-PERSONALIZADO.md](CEU-PERSONALIZADO.md#fase-3-estrelas-constelações-e-nebulosas).

| Teste | Resultado | Observação |
|---|---|---|
| `./gradlew build` | verde | `SkyObjectsCheck` novo no `checkBudget`; `checkShaders` com glslangValidator: 19 shaders, agora incluindo os de núcleo (`#moj_import` resolvido como o NeoForge) |
| `./gradlew checkApiCompat` | verde | 89 classes e 1038 membros da base 0.12.0 presentes e compatíveis |
| `./gradlew checkCeuReferencia` | 38/38 | as 26 cenas anteriores (15 da 0.12.0, 11 com fundos) com diferença **zero**; 12 cenas novas de objetos do céu |
| `./gradlew checkFundoComposicao` | 9/9 | igual à fase 2 |
| `./gradlew checkObjetosComposicao` (novo) | 24/24 | shaders de núcleo reais em WebGL2 + passes reais; números e controles negativos em CEU-PERSONALIZADO.md |
| `xvfb-run ./gradlew testeEstrelas` (novo) | 20/20 | duas rodadas completas; a primeira deu 19/20 por uma sonda errada do teste (olhava só a face sul do cubemap), corrigida sem mudança no motor |
| `xvfb-run ./gradlew testeBackdrop` | 34/34 | regressão da fase 2 |
| `xvfb-run ./gradlew testeCeu` | 20/20 | regressão do céu procedural da 0.12.0 |
| `./gradlew testeJogo` | 61/61 | |
| `./gradlew -p examples/vfx-sample build` | verde | |

**`testeEstrelas` (llvmpipe, 1280 × 720):** sem addon nenhum objeto, nenhuma malha, nenhum desenho e nenhuma camada de cobertura alocada; estrela `NORMAL` vermelha com cor exata (255,32,16) sobre o cubemap e o fundo intacto ao lado; catálogo com constelação numa malha; campo `LUMINOUS` à noite sem nenhum pixel mais escuro que o fundo (738 pixels acesos) e sumindo de dia (0 pixels); nebulosa `NORMAL` a 0,5 com mistura por alfa coerente (pior 1,0/255 em 7 563 pixels, alfa máximo 0,38) e sem tocar a skybox fora dela; nebulosa `COVER` só na silhueta; Atmosphere sobre a nebulosa medida (55,30,86) = esperada; camada de cobertura desenhada só nos quadros com passe; estrelas `REVEAL` no eclipse global do exemplo, de dia (0 pontos antes, 732 na totalidade); nada com cegueira; `customSkies`; tetos por perfil 4 000 / 16 000 / 40 000 / 60 000 (o campo tem 60 000; ULTRA aceita 131 072); **1,00 chamada de desenho por quadro** para um campo de 40 000 estrelas; recarga de recursos libera e remonta as malhas; céu do Overworld igual antes e depois (diferença zero). Capturas: [jogo](ceu-previas/estrelas-fase3-jogo.jpg) e [prévia](ceu-previas/estrelas-fase3-previa.jpg).

**Custo (software, llvmpipe, só tendência; GPU física pendente):** mediana / p95 do tempo de quadro, segunda rodada (a primeira entre parênteses): sem objetos 7,4 / 9,6 ms (8,6); 6 000 estrelas 8,7 / 10,7 (10,3); 40 000 estrelas (HIGH) 11,8 / 14,4 (15,7); nuvem 12,3 / 15,3 (12,4); faixa (esfera inteira, ruído de 5 oitavas) 18,0 / 22,4 (17,4); atmosfera sozinha 30,0 / 36,3; 6 000 estrelas + atmosfera, com a camada de cobertura, 30,8 / 35,9. Leitura: a camada de cobertura com 6 000 estrelas custa perto de 1 ms sobre o passe de atmosfera que já existia; a faixa é o objeto mais caro por software (cobre a tela inteira com ruído por pixel), seguida da nuvem; o custo das estrelas cresce com o número desenhado, que os perfis limitam. Os números variam ~1 a 4 ms entre rodadas; nenhum ganho ou limite é afirmado para GPU física.

**Não testado:** GPU física, Iris/Oculus e shaderpacks, Sodium/Embeddium, outros mods que desenham em `AFTER_SKY`, multiplayer com dois jogadores. Nenhuma compatibilidade é afirmada.

## 0.14 (Custom Sky) — fase 2: Backdrop, branch `claude/ceu-personalizado` (22/11/2026)

**O motor mudou (só aditivo):** API `Backdrop`, `Effects.backdrop`, `SkyProfile.StateBuilder.backdrop`; cliente `BackdropFrame` e `BackdropTextures`; passes `sky` e `atmosphere` com os fundos; opção de cliente `customSkies`. A versão do build continua 0.12.0 até o release da 0.14, então `build/libs/calamidade_awakening-0.12.0.jar` deste branch **não** é mais igual ao anexo do release (SHA-256 `c1d90a41…` contra `a934968a…` do release, que não foi tocado, nem a tag `v0.12.0`, nem a `main`). Arquitetura e resultados: [CEU-PERSONALIZADO.md](CEU-PERSONALIZADO.md#fase-2-backdrop-implementado).

| Teste | Resultado | Observação |
|---|---|---|
| `./gradlew build` | verde | `BackdropCheck` novo no `checkBudget`; `checkShaders` não verificou nada nesta máquina (glslangValidator ausente; os shaders compilam no WebGL da prévia) |
| `./gradlew checkApiCompat` | verde | 89 classes e 1038 membros da base 0.12.0 presentes e compatíveis (94 classes no JAR) |
| `./gradlew checkCeuReferencia` | 26/26 | as 15 cenas da 0.12.0 com diferença **zero** (sem fundo, o caminho dos passes é o da 0.12.0); 11 cenas novas com fundos |
| `./gradlew checkFundoComposicao` (novo) | 9/9 | Atmosphere sobre fundo opaco, parcial e com buraco de alfa, com e sem passe `sky`: erro máximo 0,5 a 1,0/255 contra mix(tela, gradiente, blend); sol apagado sob fundo opaco 0,0/255 e sob fundo parcial 8/255 (o apagamento da 0.12.0 sem fundo, medido no mesmo teste, erra 16/255); eclipse 0,5/255. Controles negativos (o comportamento do protótipo da fase 1): 77,5/255 e 217/255, como deviam |
| `xvfb-run ./gradlew testeBackdrop` | 34/34 | terceira rodada; as duas primeiras falharam por erros do teste (fotos antes da entrada de 168 ticks do envelope dos efeitos longos, sondas na face de cima, a névoa da barra do dragão no End, métrica do horizonte), sem mudança no motor para isso. Lista abaixo |
| `xvfb-run ./gradlew testeCeu` | 20/20 | regressão do céu procedural em jogo (eclipse em três dimensões, respawn, relogin, dimensão de teste) |
| `./gradlew testeJogo` | 61/61 | nesta rodada, a cratera grande deu pior tick de 33,6 ms (limite 50). **Isso não resolve a falha anterior** (88,2 e 51,0 ms, abaixo): a causa continua não isolada e o caso continua pendente |
| `./gradlew -p examples/vfx-sample build` | verde | |

**`testeBackdrop` (llvmpipe, 1280 × 720):** nada desenhado nem carregado sem addon; ordem `AFTER_SKY` → RimuLib → `AFTER_LEVEL`, framebuffer principal, profundidade 1 depois do fundo; as cinco faces visíveis do cubemap na direção documentada (cor exata); fundo estático sem passe `sky` nem `atmosphere`; giro de 90°; panorama com alfa 0,5; gradiente; sol e lua cobertos pelo fundo opaco e redesenhados com `vanillaSunMoon` (alfa 1,00); Atmosphere sobre o fundo (medido (37,32,92) = esperado); lente (81% dos pixels da grade deslocados); eclipse escurece o fundo e o sol redesenhado vai de 1,00 a 0,00 na totalidade; sem fundo com cegueira, escuridão, câmera em lava e em neve em pó, e na ilha central do End com a névoa da barra do dragão; horizonte: faixa 0/255 mais clara que o fundo com a névoa casada, 90/255 sem ela; terreno idêntico com e sem fundo (estado de renderização devolvido); `customSkies` desligado e ligado; textura reaproveitada entre fundos e instâncias; recarga de recursos libera as 4 texturas e recarrega só a que está em uso; transição de perfil no End com dois fundos desenhados e a tela na mistura de A e B (t = 0,55), sem o céu do jogo; céu do Overworld igual (diferença zero) antes e depois de tudo, com a hora parada. Capturas: [jogo](ceu-previas/backdrop-fase2-jogo.jpg) e [prévia](ceu-previas/backdrop-fase2-previa.jpg).

**Custo (software, llvmpipe, só tendência; GPU física pendente):** mediana do tempo de quadro em três rodadas — sem fundo 20,4 / 17,8 / 19,2 ms; cubemap 20,7 / 19,2 / 22,1 ms; panorama (esfera 64 × 32) 22,0 / 20,9 / 20,9 ms. A diferença fica dentro do ruído entre rodadas (até ~3 ms); o rasterizador por software paga cada pixel do fundo. Nenhum passe de pós é ligado por um fundo estático; com camadas ou atmosfera, os passes que já rodam fazem duas consultas de textura a mais por pixel de céu.

**Não testado:** GPU física, Iris/Oculus e shaderpacks, Sodium/Embeddium, outros mods que desenham em `AFTER_SKY`, multiplayer com dois jogadores. Nenhuma compatibilidade é afirmada.

**Pendências mantidas:** a cratera grande do `testeJogo` (abaixo; não aprovada até isolar a causa), a pulsação no Overworld e o multiplayer manual da 0.12.0, e os números de custo das receitas 18 a 21, que divergem do volume planejado e precisam de medição em jogo (revisão documental, abaixo).

## Revisão documental da Terrain API — escolha da operação e custo (22/11/2026)

**Só documentação; nenhum código mudou.** `build/libs/calamidade_awakening-0.12.0.jar` continua com o SHA-256 do release v0.12.0 (`a934968a…`). Novas seções em [TERRENO-0.11.md](TERRENO-0.11.md): "Escolher a operação" (modos, tabela de decisão, cinco exemplos comparáveis, papel das receitas 18 a 21) e "Custo de uma destruição"; regra 11 no `AGENTS.md`; skill `calamidade-efeitos`; notas nas receitas 18 a 21 (só texto, código intacto).

- **Conferido no código:** sem `burn`, o planejador marca como `SEAL` só os blocos que tocam o espaço cavado por uma face (com `burn`, toda a casca até a espessura); o commit veda fluidos e queima não sólidos em `SEAL`, sem trocar sólidos; `fill` é escrito por cima de tudo, inclusive do ar; o calor do cliente usa espessura mínima 1 (`terrain.fsh`), então aparece sem `burn`; o orçamento `terrainBlocksPerTick` conta entradas percorridas (mudem ou não), é global e atende as tarefas **em ordem de chegada**, descontando a cota inteira de cada uma (o texto antigo dizia "dividido entre as edições" e foi corrigido no capítulo, no `AGENTS.md` e na receita 20); as verificações de proteção rodam num tick só, fora do orçamento; no marcador, um plano ainda não pronto é esperado (`join`) na thread do servidor.
- **Contagens com o planejador puro** (`TerrainPlanner.plan` nas classes do build, mundo plano hipotético: sólido abaixo da origem): os números da tabela do capítulo. A mesma conta dá 73 508 planejados e 36 754 escritos para a esfera r24 com casca de 2, iguais aos registrados em jogo na 0.11.1. Os exemplos novos do capítulo foram construídos e validados (`validate`) contra essas classes.
- **Divergência anotada, não corrigida:** as linhas "Custo" das receitas 18 a 21 ("~1 700", "~1 900", "~1 300", "~700 blocos planejados") ficam abaixo do volume planejado que o planejador dá para as mesmas formas (2 112..2 216, 3 493..3 616, 2 359..2 481 e 2 705..2 776 sem a estrutura, em 40 origens, yaws e seeds). Podem ter sido medidas como blocos escritos num mundo específico; precisam de uma medição em jogo antes de mudar o texto.
- **Cratera grande do `testeJogo` (não resolvido, continua não aprovado; registro da rodada abaixo mantido):** a edição do teste é `sphere(24)` com `burn(…, 2)` e `noHeat()`, **sem preenchimento**; o marcador é o tick 5. Hipóteses, nenhuma comprovada: (a) o pior tick é o das verificações de proteção (fora do orçamento; 19 a 22 ms na 0.11.1 para essas 73 508 posições) somado a algo da máquina; (b) com a máquina carregada, o plano não ficou pronto até o tick 5 e espera do plano, verificações e primeiro lote de 16 384 entradas caíram no mesmo tick; (c) variação de JIT, coletor de lixo ou CPU compartilhada. O relatório atual não diz qual tick foi o pior; um próximo passo seria registrar o tempo por tick e por fase no `Medidor`. O limite de 50 ms não foi mudado.
- **Verificações desta revisão:** `./gradlew build` verde (`checkBudget` com `TerrainCheck`, `checkRecipes`, `checkApiDocs`, `checkApiCompat`, `checkModVersion`; `checkShaders` sem glslangValidator nesta máquina, só aviso); `python3 scripts/gerar_manual.py`; links e âncoras dos arquivos alterados conferidos por script; `git diff` só com arquivos de documentação. Não rodados, porque nenhum código executável mudou: `testeJogo`, `testeCliente`, `testeCeu`, `testeBackdrop`, `checkCeuReferencia` e o build do vfx-sample.

## 0.14 (Custom Sky) — fases 0 e 1, branch `claude/ceu-personalizado` (22/11/2026)

**Nenhum código do motor mudou:** `build/libs/calamidade_awakening-0.12.0.jar` continua byte a byte igual ao anexo do release v0.12.0 (`a934968a…`). Esta rodada criou guardas de regressão e um experimento só no mod de teste. Arquitetura e resultados: [CEU-PERSONALIZADO.md](CEU-PERSONALIZADO.md).

| Teste | Resultado | Observação |
|---|---|---|
| `./gradlew build` | verde | `checkShaders` não verificou nada nesta máquina (glslangValidator ausente; só aviso, como previsto fora do CI) |
| `./gradlew checkApiCompat` (novo, no `check`) | verde | autoteste 7/7; 89 classes e 1038 membros da base 0.12.0 |
| `./gradlew checkCeuReferencia` (novo) | 15/15 | duas gerações seguidas com diferença zero; escurecer a fenda em 20% é acusado nas duas cenas que a mostram |
| `xvfb-run ./gradlew testeBackdrop` (novo) | 13/13 | três rodadas; a primeira deu 12/13 por uma hipótese minha de ordem dos passes, corrigida pelo `javap -c` |
| `xvfb-run ./gradlew testeCeu` | 20/20 | regressão do céu em jogo |
| `./gradlew testeJogo` | **60/61, duas rodadas** | falhou só o limite de tempo "cratera grande com a casca externa": pior tick 88,2 ms e depois 51,0 ms, contra o teto de 50 ms (o release teve 61/61 em outra máquina). O motor é o mesmo JAR; o mais provável é a máquina (4 núcleos compartilhados), mas a causa não foi isolada e o caso **não está aprovado** nesta rodada |
| `./gradlew -p examples/vfx-sample build` | verde | |

Pendências da 0.12.0 (multiplayer com dois jogadores, pulsação no Overworld) continuam abertas, como na seção abaixo.

## Versão 0.13.0 — Bosses (publicada: release v0.13.0)

**Publicada** (workflow Release `45229440917`, tag `v0.13.0` no commit `2c90f60`). Compilada e testada por software (servidor dedicado e cliente Mesa llvmpipe sob xvfb). Base: `8cfd93a`
(0.12.0); nada da 0.14. GPU física, shaderpacks e dois jogadores reais pendentes.

### Depois do release v0.13.0 (23/11/2026)

Workflow Release `45229440917` (sucesso), tag `v0.13.0` no commit `2c90f60`. Anexos só `calamidade_awakening-0.13.0.jar`,
`vfx-sample-1.10.0.jar` e `SHA256SUMS.txt`; baixados, `sha256sum -c` OK e **iguais aos do candidato** (`a427a90d…`,
`56b2832a…`): o build do workflow reproduziu o local. Sem "DEVELOPMENT" no `neoforge.mods.toml` e sem classes da 0.14 ou
GeckoLib nos JARs publicados. `examples/consumidor` passou para a 0.13.0 (faixa `[0.13.0,0.14.0)`, SHA-256 publicado) e
`./gradlew -p examples/consumidor clean build`, sem endereço alternativo e com o JAR apagado de `libs/`, baixou
`calamidade_awakening-0.13.0.jar` do release, conferiu o SHA-256 e compilou.

### Candidato final 0.13.0 (23/11/2026, quarta sessão)

**Commit de preparação `1789722`** sobre `59080b5` (histórico preservado): `neoforge.mods.toml` sem o comentário e o
sufixo DEVELOPMENT BUILD (descrição igual à dos releases anteriores); "em desenvolvimento" → "preparada para
publicação" em `MUDANCAS.md`, `AGENTS.md`, `docs/ROADMAP.md`, `docs/BOSSES-0.13.md` e aqui; manual regerado;
`syncRecipes` e `apiDocs` sem diferença. `build.gradle`, versões, dependências, `release.yml`, notas da v0.13.0, receitas
e vfx-sample sem mudança. **Ainda não publicada:** sem merge na `main`, sem tag, sem workflow.

Rodada consolidada de release, uma vez:

| Verificação | Resultado |
|---|---|
| Build limpo numa worktree nova de `1789722` (sem `build/`, `libs/`, `dist/`): `./gradlew clean build dist --no-build-cache` | verde: RimuLib baixada do CurseForge e conferida (`15ccd495…`), `checkApiDocs`, `checkBudget` (todos PASS, `BossCheck` incluso), `checkFrustum`, `checkModVersion` (0.13.0; vfx-sample 1.10.0 exige `[0.13.0,0.14.0)`), `checkRecipes`, `checkShaders` (13 shaders, glslangValidator 15.1) |
| `./gradlew -p examples/vfx-sample clean build --no-build-cache` | verde |
| Marcas de desenvolvimento | nenhuma ("DEVELOPMENT BUILD", "do not distribute", "em desenvolvimento") em nenhum arquivo dos dois JARs |
| Metadados do motor | `version="0.13.0"`; Minecraft `[1.21.1]`, NeoForge `[21.1,21.2)`, RimuLib `[1.3.0,2.0)`, todos `BOTH`; nenhuma outra dependência (sem GeckoLib) |
| Metadados do vfx-sample | `1.10.0`; motor `[0.13.0,0.14.0)`, RimuLib `[1.3.0,2.0)` |
| Isolamento cliente/servidor | bytecode (`javap -v`) das 227 classes fora de pacotes `client`: nenhuma referência a `net/minecraft/client` ou `com/mojang/blaze3d` (controle: `BossBarClient` tem 142) |
| GeckoLib | nenhuma classe ou texto `geckolib`/`software/bernie` no JAR |
| 0.14 | nenhum dos 107 arquivos que a 0.14 acrescenta sobre `8cfd93a` existe em `1789722` |
| Recursos | 371 entradas; `assets/` igual ao da 0.12.0 exceto `lang/en_us.json` e `lang/pt_br.json`; 12 arquivos de shader |
| Reprodutibilidade local | o mesmo commit compilado de novo no repositório principal deu os mesmos SHA-256 |

**JARs candidatos (build local; não publicados):**

```
a427a90dd471c8d83abe3be8a64e034e52c2bc8413250d816ec6e19f5decb0ff  calamidade_awakening-0.13.0.jar
56b2832ae2c8c670358f597286bb742c04657ee04e5a3e95abc1329cdaf069e0  vfx-sample-1.10.0.jar
```

O workflow de release recompila no GitHub: os hashes publicados precisam ser conferidos contra estes no momento da
publicação; se diferirem (por exemplo por causa do commit de merge na `main` ou do ambiente), investigar antes de
anunciar.

Reaproveitado sem nova execução (o código Java validado não mudou; desde então só o metadado `description`, Javadoc e
documentos): `testeJogo` 82/82 e `testeChefe` 15/15 (revisão pré-release), compatibilidade da API com a 0.12.0,
consumidor isolado. Continuam pendentes: dois jogadores reais (TESTE-REAL.md, parte 11), troca de dimensão do chefe em
jogo, GeckoLib real, GPU física, shaderpacks e outros renderizadores; a barra pode ficar visível por até 0,5 s depois de
o jogador trocar de dimensão.

### Preparação final do release (23/11/2026, terceira sessão)

**Candidata ao release:** `claude/bosses-0.13` avançado de `233763a` para `50099ab` por avanço simples (push sem força,
aceito); é o mesmo commit de `claude/pensive-clarke-bfqzz1`, então as correções da revisão estão na branch candidata.
`main` (`8cfd93a`) e `claude/ceu-personalizado` (`ab2a97d`) intocados; nenhuma tag `v0.13*`. O JAR continua marcado
DEVELOPMENT BUILD.

**Nenhum teste automatizado foi executado nesta sessão**: nenhum código do motor mudou (só documentação), então valem
os resultados da revisão pré-release abaixo, todos sobre o código de `50099ab` (`testeJogo` 82/82, `testeChefe` 15/15,
`build`/`dist`, `checkShaders` 13, vfx-sample, consumidor isolado).

Revisão estática desta sessão (código do NeoForge 21.1.267 conferido no `neoforge-21.1.267-sources.jar`):

- **Chefe trocando de dimensão — sem defeito.** `Entity.changeDimension` cria a cópia, chama `restoreFrom` (que passa
  por `saveWithoutId`/`load` e copia os attachments, logo o estado do chefe), remove a antiga com `CHANGED_DIMENSION`
  (não destrói o estado) e só então `addDuringTeleport` da nova. No `EntityJoinLevelEvent` da nova, o `BossServer`
  encerra o controlador antigo (`end(false)`: sequência parada, sustain e evento de fase soltos, `boss_v1` de remoção e
  barra vanilla removida para quem via na dimensão antiga) e cria um novo, retomado no tick seguinte na nova dimensão,
  com a barra só para jogadores dela (`viewers` filtra por nível). Um controlador por UUID (mapa `ACTIVE`): sem
  duplicata. Instâncias presas à entidade antiga acabam sozinhas (`StageNetwork.lost()`: âncora removida ou em outro
  nível). Limitações: sequência em andamento termina na chegada; o handle antigo deixa de valer (documentado).
- **Dois jogadores, entrada tardia, alcance, logout, dimensão do jogador, dois chefes — lógica conferida.** Cada barra
  é um `ServerBossEvent` com UUID próprio; quem passa a ver recebe o estado `boss_v1` atual antes de ser adicionado;
  troca de fase manda um pacote a cada jogador que vê; logout tira o jogador da barra (o cliente limpa no logout);
  dimensão e respawn são revistos a cada 10 ticks. Limitação: por até 0,5 s depois de trocar de dimensão a barra pode
  continuar visível (e com progresso) para aquele jogador. Não há pacote por tick. **Dois clientes reais não foram
  testados** (não há ambiente com dois clientes aqui): roteiro manual curto em TESTE-REAL.md, parte 11 (casos 103 a 110).
- **Limpeza — sem defeito.** Morte: regra de morte no mesmo tick (sai da fase, solta o que ela mantinha), barra em 0;
  remoção da entidade (`KILLED`) → controlador encerrado e estado apagado. Sem regra de morte, o que a fase mantinha é
  solto quando a entidade é removida (~1 s). `discard`/`detach`/3 falhas → encerrado e estado apagado. Descarregar ou
  trocar de dimensão → visual solto, estado mantido. Servidor parando → mapas limpos; eventos globais da fase não são
  persistentes. Timeline: a sequência é parada (`stopSequence`) em morte, fase forçada e encerramento; sustain recebe
  `release()` (ou `stop()` se não puder). Rig Anchors no cliente: capturas por (id, nome) conferidas pela UUID,
  podadas a cada 128 quadros e limpas no logout; provider com erro é removido.

### Revisão pré-release (23/11/2026, sessão separada; branch `claude/pensive-clarke-bfqzz1` sobre `233763a`)

Auditoria estática da Boss API (persistência, fases e sinais, barra e multiplayer, anchors, limites), do
empacotamento e da documentação. Achados e o que foi feito:

| Achado | Classe | Situação |
|---|---|---|
| O controlador avançava (tempo da fase, regras, ações periódicas) com a entidade carregada mas **não processada** (chunk na borda da visão) ou com `/tick freeze`: um chefe parado podia trocar de fase sozinho | correção recomendada | corrigido (`BossServer.processed`); a barra continua seguindo vida e alcance |
| Rótulo de fase ou id de estilo com mais de 256 caracteres passava na definição e fazia o `boss_v1` falhar ao codificar (pacote ao jogador) | correção recomendada | corrigido: recusado na definição (`IllegalArgumentException`) |
| `testeChefe` falhava 2/15 neste ambiente (o mesmo com o `BossServer` da `233763a`): a 200 blocos o chunk do golem foi descarregado e recarregado; o motor retomou o controlador certo, mas o roteiro usava o handle e o objeto antigos, o `detach` não fazia nada e a barra ficava (o que também derrubava a verificação do Sentinela) | defeito do teste | roteiro corrigido (acha o controlador atual pela UUID; o detalhe registra a recarga) |
| Handle guardado depois de o chunk descarregar fica inválido (`alive()` false); nada dizia isso ao modder | documentação | Javadoc de `Bosses.Handle` e guia (seção 8 do capítulo) |
| Seções de plano do `BOSSES-0.13.md` citavam nomes que não existem (`boss/BossData`, `.through(...)`, marcador `fim`, `RigAnchors.serverPose`, `AnchorPose`, `Effects.follow`), "3 falhas pausam" (removem) e "prioridade normal" (é `LOW`); prometiam um exemplo GeckoLib na receita que não existe | documentação | corrigido; seção 8 nova: guia do modder (14 itens) só com APIs do JAR, GeckoLib como esqueleto **não testado** |
| O workflow de release publicaria um JAR ainda marcado DEVELOPMENT BUILD ou com tag diferente da versão | correção recomendada | `release.yml` confere tag × `build.gradle` e recusa o aviso |
| Faltavam as notas `.github/release-notes/v0.13.0.md` (o workflow exige) | preparação | escritas, sem afirmar compatibilidade não testada |

Sem bloqueadores encontrados no código. Conferido sem mudança: limites (`maxPerBoss` só com controlador confirmado;
mesma contagem e recarga da entidade, `equals` ignora a marca; total 64 e reserva valem; mob comum continua em 4);
persistência (estado gravado a cada tick processado, retomado ao carregar; id desconhecido descartado; morte e
`discard` apagam; descarregar e trocar de dimensão só soltam o visual; eventos globais da fase não são persistentes, então
nada fica preso no mundo depois de o chefe morrer, ser removido ou descarregar); barra (só na mesma dimensão e no
alcance, revisão a cada 10 ticks, logout, remoção por `boss_v1`; progresso só com 0,5% ou 5 ticks; nenhum pacote por
tick); anchors (pose do cliente só visual, servidor usa `approximate`; plano B estático); isolamento cliente/servidor
(`boss/`, `net/`, `api/` e `api/v1` sem classes de cliente; o `testeJogo` carrega o mod num servidor dedicado).

**Executado nesta sessão** (container novo; RimuLib baixada e conferida pela task):

| Teste | Resultado | Por quê |
|---|---|---|
| `./gradlew build` e `dist` | verdes | `BossCheck` com 3 casos novos (estilo e rótulo longos recusados, rótulo de 256 aceito); `checkApiDocs` (referência regerada: só textos de Javadoc mudaram), `checkRecipes`, `checkModVersion` |
| `./gradlew checkShaders --rerun` com glslangValidator 15.1 | 13 shaders compilados | o CI de release exige; na sessão anterior só tinha avisado |
| `./gradlew -p examples/vfx-sample build` | verde | contra o JAR desta revisão |
| `./gradlew testeJogo` (uma rodada, duas fases) | **82/82** | as 79 anteriores mais 3 novas: chefe num chunk carregado sem processar entidades fica na fase `a` com 0 ticks por 60 ticks; volta a andar quando o chunk passa a processar; com `/tick freeze` o tempo da fase não anda (0 → 0 em 30 ticks). Reinício: chefe de volta na fase `b` com 17 ticks (salvo com 10; o mundo andou 283) |
| `xvfb-run -a ./gradlew testeChefe` | 13/15 → **15/15** | 1ª: duas falhas (acima); 2ª, com o `BossServer` da `233763a`: as mesmas duas (não vinham da correção); 3ª, com diagnóstico: golem `UNLOADED_TO_CHUNK` e controlador retomado na fase `c`; 4ª, com o roteiro corrigido: 15/15 (inclui o caminho "chunk descarregado → chefe retomado na fase certa com a barra") |
| Consumidor contra a 0.13.0, **isolado** | compila | cópia de `examples/consumidor` no scratchpad com versão e SHA-256 do JAR local e `-Pcalamidade.url=file://…`; `examples/consumidor` continua apontando para o release 0.12.0 |
| JAR | conferido | 371 entradas; nenhum dos 107 arquivos que a 0.14 acrescenta sobre `8cfd93a`; nada de GeckoLib; `version="0.13.0"` e o aviso DEVELOPMENT BUILD no `neoforge.mods.toml` |

**Reaproveitado da sessão anterior (sem mudança no que valida):** compatibilidade da API com a 0.12.0 (nenhuma assinatura
mudou nesta revisão, só Javadoc); `testeCeu`, `testeCliente`, `testeMedicao`, `testeAtlas`, `testar_glsl.cjs` e
`previa_passes.cjs` (nenhum shader, passe, escritor de terreno, perfil ou Atlas mudou).

**Pendente (não bloqueante, nada afirmado sem evidência):** dois jogadores reais num servidor dedicado (entrada tardia,
alcance, troca de dimensão do jogador e do chefe, logout, dois chefes visíveis ao mesmo tempo para os dois) — só a lógica
foi testada, com um jogador; troca de dimensão **do chefe** não tem teste automatizado (arquitetura: estado copiado
com a entidade, visual solto e retomado); mod real com GeckoLib; GPU física; Iris/Oculus, Sodium/Embeddium e outros
renderizadores; um chefe que volta quando `bossMaxActive` está cheio fica sem controlador até o próximo carregamento
(estado mantido, aviso no log); o mesmo efeito de evento global em dois chefes ao mesmo tempo (o segundo fica sem ele).

#### Procedimento de publicação da 0.13.0 (cada passo só com autorização explícita)

Candidata: `claude/bosses-0.13` em `50099ab`. Notas: `.github/release-notes/v0.13.0.md` (caminho que o workflow lê).

1. Opcional, recomendado: TESTE-REAL.md parte 11 (dois jogadores) e uma GPU física; registrar aqui.
2. **Feito em `1789722`** (commit de preparação; itens abaixo conferidos). **Commit de release** em `claude/bosses-0.13` (único lugar onde o aviso sai):
   - `src/main/resources/META-INF/neoforge.mods.toml`: apagar a linha de comentário "0.13 em desenvolvimento…" e o
     sufixo ` [DEVELOPMENT BUILD of 0.13 (bosses) — not a published release; do not distribute]` da `description`;
   - texto "em desenvolvimento / não publicada" → "publicada" em `MUDANCAS.md` (linha de estado e título da 0.13),
     `docs/ROADMAP.md` (0.13), `docs/BOSSES-0.13.md` (quadro de estado), `AGENTS.md` (linhas 15 e 17) e no título
     desta seção;
   - `python3 scripts/gerar_manual.py` e `./gradlew syncRecipes`; reler `.github/release-notes/v0.13.0.md`.
3. **Build limpo** numa worktree nova do commit de release (sem `build/`, `libs/`, `dist/`):
   `./gradlew clean build dist --no-build-cache` (com glslangValidator) e `./gradlew -p examples/vfx-sample build`;
   conferir que o `neoforge.mods.toml` dentro do JAR não tem "DEVELOPMENT BUILD"; `sha256sum` dos dois JARs.
4. **PR** `claude/bosses-0.13` → `main` (sem merge automático); revisar o diff (só 0.13, nada da 0.14); merge comum.
   Depois do merge: trocar "preparada para publicação" por "publicada" só depois do release existir (commit de documentação).
5. **Release**: Actions → Release → Run workflow no `main`, tag `v0.13.0`, título `Bosses` (release "Calamidade_Awakening
   0.13.0 — Bosses"). O workflow cria a tag no commit do `main`, recusa tag ≠ `0.13.0` e o aviso de desenvolvimento.
6. Conferir anexos: só `calamidade_awakening-0.13.0.jar`, `vfx-sample-1.10.0.jar` e `SHA256SUMS.txt`; SHA-256 iguais
   aos do passo 3; `sha256sum -c` dos anexos baixados.
7. **Depois do release publicado:** `examples/consumidor/build.gradle` com `calamidadeVersao = '0.13.0'` e o SHA-256
   publicado; `./gradlew -p examples/consumidor clean build` sem endereço alternativo; registrar aqui. Antes disso o
   consumidor fica no 0.12.0 (o JAR 0.13.0 ainda não existe no GitHub).

Os JARs da revisão (`f2b352e1…` do motor no último build, depois do Javadoc de `Bosses.Handle`; o consumidor isolado
compilou contra o build anterior, `39d335c7…`, com as mesmas assinaturas) são de **desenvolvimento**: não publicar; o
JAR oficial sai do commit de release (passo 3).

### Executado (23/11/2026, sessão da implementação)

| Teste | Resultado | Cobre |
|---|---|---|
| `./gradlew build` | verde | `checkBudget` com `BossCheck` (máquina: ordem, uma transição por tick, `minDwell` sem avaliar, sequência com trava, morte quebra a trava, nada depois da morte, `force`, restauração com fase desconhecida e sequência pendente, `when` só no intervalo, histerese por permanência; anchors: corpo e cabeça na convenção do Minecraft, matriz com os eixos invertidos do modelo vanilla, matriz degenerada, Gram-Schmidt, extrapolação igual à pose estática girada, janela de 10 ticks; barra: progresso, envio de 0,5% e de 5 ticks, marcas, fantasma, clarão com acessibilidade, escala; `maxPerBoss`: 8 com controlador, 4 sem, total e reserva, jogador fora da regra, marca sem identidade nova; `Boss.builder`; `Point.socket`; ida e volta do `boss_v1`, remoção em 17 bytes), `checkApiDocs` (Javadoc em todos os membros novos), `checkRecipes`, `checkModVersion` (motor 0.13.0, vfx-sample 1.10.0 exige `[0.13.0,0.14.0)`); `checkShaders` só avisou (glslangValidator ausente; nenhum shader mudou) |
| `./gradlew dist` e `./gradlew -p examples/vfx-sample build` | verdes | o exemplo compila contra `dist/calamidade_awakening-0.13.0.jar` |
| `./gradlew testeJogo` | 79/79 | as 63 verificações anteriores (Terrain API, eventos globais, perfis, reinício) mais 16 de chefes: attach e attach repetido, fase invulnerável cancela dano, `afterTicks`, aura `sustain` com o chefe como fonte, sinal que espera o `minDwell` e sequência que trava até acabar, vida abaixo de 50%, ordem a-b-c-d sem duas transições no mesmo tick, `maxPerBoss` (8 com controlador; 4 com a marca sem controlador; comuns e marcadas na mesma contagem 4 + 4), 3 falhas de callback desligam o controlador, estado de chefe indefinido descartado, `detach` e `phase()`, `/calamidade boss list`; **reinício**: chefe descarregado na fase b com 11 ticks, mundo andou 273 ticks, voltou com 17 (tempo decorrido da fase, não do mundo); morte leva à fase de morte e o controlador some com a entidade |
| `xvfb-run -a ./gradlew testeChefe` | 15/15 | numa só inicialização, com o vfx-sample 1.10.0: a `RenderLayer` do golem captura os olhos no quadro (3 anchors resolvidos por captura); pose capturada a 0,123 bloco do anchor estático e frentes alinhadas (produto escalar 1,000: eixos do modelo vanilla corretos); fora da tela, 3 anchors carregados e, depois de 10 ticks, 3 estáticos (o feixe não some); `boss_v1` chega e o estilo segmentado substitui a barra vanilla; troca de fase chega ao cliente; "forçar barras vanilla"; escala 2 e alto contraste; linha do F7; fora do alcance (200 blocos) a barra some e volta na fase atual (entrada tardia); `detach` remove a barra; **Sentinela do Eclipse**: `/vfxsample chefe`, despertar → combate em 60 ticks, vida a 30% → rugido trava → fúria, eclipse em área (2 camadas de céu), morte → derrota, barra e controlador somem. Capturas em `run/teste-chefe/screenshots/` (anchor e feixe, barra segmentada, fase c, vanilla, escala 2 com contraste, combate e fúria do Sentinela) |
| Compatibilidade da API | sem remoções | `docs/ai/API-REFERENCIA.md` (gerada com javap do JAR) comparada com a do `8cfd93a`: 422 linhas novas; as 5 "removidas" são o cabeçalho de versão e o texto do Javadoc de `equals`/`hashCode`/`toString` de `EffectSource` e `Point`, com as mesmas assinaturas |

### Correções achadas pelos testes

- `testeJogo` (1ª rodada): um sinal enviado logo depois de uma troca de fase era descartado durante o `minDwell` (a máquina não avaliava as regras e o tick limpava os sinais). Corrigido: `BossMachine.Step.evaluated`; o sinal espera a avaliação. O cenário de reinício também foi refeito: o chefe continuava processado (chunk forçado) até o servidor parar, então o teste não separava tempo da fase de tempo do mundo; agora o chefe é descarregado antes.
- `testeJogo` (2ª rodada): a verificação da trava olhava 3 ticks depois do sinal, antes do fim do `minDwell` (o motor estava certo: b@807, c@857 = 20 de permanência + 30 de sequência). O teste passou a esperar a trava.
- `testeChefe` (1ª rodada): o cliente parou na tela de "ajustes experimentais" (dimensão do vfx-sample); o roteiro passou a respondê-la como o `RoteiroCeu`.

### Pendente

- GPU física (custo da barra personalizada, da captura de anchors e do Sentinela; tudo foi visto só por software).
- Iris/Oculus, Sodium/Embeddium e outros mods de renderização com a captura de anchors (nenhuma compatibilidade afirmada).
- Dois jogadores reais num servidor dedicado vendo a mesma barra (entrada tardia, alcance, troca de dimensão); os testes cobrem um jogador e a lógica de alcance.
- GeckoLib real: a receita mostra a captura num renderizador do addon, mas nenhum mod com GeckoLib foi testado nesta etapa.
- Continuam as pendências da 0.12.0 abaixo (pulsação no Overworld, variação de tempo da cratera grande, custos de receitas de Terrain).

### Reaproveitado ou dispensado (sem mudança no que validam)

`testeCeu`, `testeCliente`, `testeMedicao`, `testeAtlas`, `node scripts/testar_glsl.cjs` e `node scripts/previa_passes.cjs`: nenhum shader, passe de céu, escritor de terreno, perfil de qualidade ou Atlas mudou. Valem os resultados da 0.12.0 abaixo.

## Versão 0.12.0 — Sky & World Events (último release publicado)

**Compilada e testada por software (Mesa llvmpipe); GPU física e dois jogadores pendentes ([TESTE-REAL.md, parte 10](TESTE-REAL.md#parte-10--céu-eventos-globais-e-céu-por-dimensão-0120)).**

### Testes automatizados (22/11/2026)

| Teste | Resultado | Cobre |
|---|---|---|
| `./gradlew build` | verde | `checkBudget` com `SkyCheck` (direções na convenção do Minecraft e iguais a `Vec3.directionFromRotation`, ângulo, tamanho angular, trilhas, validação, registro, cobertura do eclipse, seleção por perfil, quadro do sol e da lua igual ao JOML, cantos do sol e da lua, base local, escurecimento e rampa de 34 ticks, limites 2/4/6/8, lente), `WorldEventCheck` (admissão com limite e prioridades, escopos, entrada tardia com sustain e release, fim, persistência em NBT binário, opções, raios ao longe determinísticos), `WorldEventSyncCheck`, `SkyProfileCheck` (perfis, transições, persistência, `sky_profile_v1`); `checkShaders` (13 shaders, glslangValidator 15.1), `checkApiDocs`, `checkRecipes`, `checkModVersion` |
| `node scripts/testar_glsl.cjs` | 173/173 | 34 casos novos de céu e lente, com valores do Java |
| `node scripts/previa_passes.cjs ceu` | folha em `docs/ceu-previas/` | passes reais `sky` e `atmosphere`; desde esta versão com framebuffers de 8 bits entre os passes, como o jogo |
| `./gradlew testeJogo` (servidor dedicado, duas fases) | 61/61 | 10 casos novos: início SERVER persistente, duplicado recusado, limite de 4 com prioridades (menor recusada, maior troca a menor), laço até o release com o cue a cada volta, `/calamidade world list` e `stop`, céu do End de "a" para "b"; no reinício, evento persistente retomado na fase (idade 360 → 620: continuou do salvamento), não persistente acabou, céu do End em "b", release depois de retomar encerra em 100 ticks |
| `xvfb-run ./gradlew testeCeu` (cliente, vfx-sample 1.9.0) | 20/20 | custo com 0/1/4/8 camadas (perfil de teste), eclipse global recebido e na totalidade (2 camadas, passe), F7 (linhas "Céu" e "Eventos globais"), Nether (SERVER), as três receitas novas, respawn durante o evento, relogin com o servidor integrado fechado e reaberto (evento persistente de volta na fase: idade 229 → 327), dimensão de teste com o evento e os três estados |

**O que o teste em jogo achou e foi corrigido:** (1) a coroa do eclipse saía um anel laranja e as estrelas sumiam no Overworld: o passe de atmosfera, depois do passe `sky`, subtraía de novo o brilho do céu do dia, e o framebuffer de 8 bits do jogo já tinha cortado a coroa em 1. A prévia, em ponto flutuante, não mostrava. Agora o passe `sky` pinta o céu da atmosfera antes do escurecimento e das camadas, e a prévia usa 8 bits entre os passes. (2) Na dimensão de teste, com o AUTO em LOW (2 camadas), o céu da dimensão tirava a vaga do eclipse global: eventos globais agora vêm antes do perfil. (3) Abrir um mundo com a dimensão do exemplo mostra uma vez "Os mundos com ajustes experimentais são incompatíveis" (dimensão por datapack): documentado no capítulo e na receita 26; o teste confirma a tela como o jogador faria. (4) No roteiro do servidor, uma espera de teste esperava a si mesma na thread do servidor (erro do teste, não do motor).

- **Depois do release v0.12.0** (workflow Release, tag no commit `26a9080`): anexos só `calamidade_awakening-0.12.0.jar`, `vfx-sample-1.9.0.jar` e `SHA256SUMS.txt`, com os mesmos SHA-256 do build local (`a934968a…` e `fc8809d4…`); `./gradlew -p examples/consumidor clean build`, sem endereço alternativo e com o JAR apagado de `libs/`, baixou `calamidade_awakening-0.12.0.jar` do release no GitHub, conferiu o SHA-256 e compilou.
- **Conferido de novo numa sessão separada (22/11/2026):** os três anexos baixados do release passam no `sha256sum -c SHA256SUMS.txt`; build limpo do commit da tag (`git worktree` nova, sem `build/` nem `libs/`, RimuLib baixada e conferida pela task, `--no-build-cache`): `./gradlew dist` e `./gradlew -p examples/vfx-sample build` deram os mesmos SHA-256 dos anexos (build reproduzível). Nessa mesma worktree, o consumidor compilou baixando o motor de `github.com/…/releases/download/v0.12.0/` e a RimuLib do CurseForge, sem `-Pcalamidade.url`.

SHA-256 de `dist/calamidade_awakening-0.12.0.jar`: `a934968a3d135fe0795abc806c548b556bb3dd9899b74a675e978a21e7c9e460`; de `vfx-sample-1.9.0.jar`: `fc8809d414a6eceb7543ff022e680cb189c19a98ea873475c64cef88e49771f9`

### Testes manuais do autor (relatados em 22/11/2026)

**Origem:** relato do autor na conversa de planejamento da Custom Sky Engine, depois do release. Os dados abaixo foram copiados do relato; o CSV, a GPU, a resolução, o perfil de qualidade, a cena e a carga **não foram registrados** aqui.

- Nos testes manuais feitos pelo autor, as funcionalidades avaliadas da 0.12.0 tiveram bom comportamento geral (a lista exata dos casos de TESTE-REAL.md, parte 10, executados não foi registrada).
- Uma gravação de tempos de quadro (`/calamidade_client record`): cerca de **2 553 quadros, mediana 11,41 ms, p95 18,96 ms**.
- **Limites desses números:** são o tempo de quadro inteiro durante a gravação, não o custo da Sky API. Sem as condições exatas e sem uma medição de referência (o mesmo cenário sem o efeito), não servem para comparação com outras medições nem para afirmar custo ou ganho.

### Pendentes (adiados por decisão do autor; não aprovados)

- **Multiplayer manual com dois jogadores** (TESTE-REAL.md, casos 99 e 100): não executado.
- **Leve pulsação visual no Overworld**, observada pelo autor depois de usar `/vfxsample ceu ameaca` e `/vfxsample ceu calmo` (os comandos mudam o estado da dimensão de teste, não do Overworld). O código do cliente filtra o perfil pela dimensão do nível (`SkyProfilesClient`), mas a causa da observação **não foi identificada** e **não é um bug confirmado**. Hipóteses ainda não testadas, sem conclusão: troca de perfil pelo AUTO mudando o número de camadas pintadas; rampa de exposição do `SkyFrame` (estado único entre dimensões); comportamento do próprio jogo. Mudanças futuras em SkyProfiles, Atmosphere ou estado de renderização entre dimensões devem trazer testes de regressão específicos (ver o plano da 0.14 no ROADMAP).
- GPU física (custo dos casos 93 a 98) e Iris/Oculus (caso 102), como antes.

### Custo do passe de céu (software)

Cliente sob Xvfb 1280 × 720, Mesa llvmpipe (LLVM 20.1.2, 256 bits), 4 núcleos, VSync desligado, distância 8, mundo plano de manhã, olhando para o sul a 25 graus; perfil de teste UNLIMITED (8 camadas por quadro); 1 camada = buraco negro de 50 graus com lente (cobre quase a tela), 4 = portal, astro com anéis, fenda e aurora, 8 = as 4 duas vezes. Janela de 6 s depois de 3 s de aquecimento.

| Camadas | Quadros | Mediana (ms) | p95 (ms) |
|---|---|---|---|
| 0 (sem passe) | 212 | 27,00 | 41,28 |
| 1 | 89 | 65,99 | 83,39 |
| 4 | 76 | 78,62 | 93,46 |
| 8 | 64 | 93,73 | 110,67 |

Por software vale a tendência: o passe custa um tanto fixo (uma passada de tela cheia; o buraco negro com lente amostra a cena duas vezes em quase todos os pixels) e cada camada a mais custa pouco, porque só trabalha perto da própria direção (recorte pelo ângulo antes de ler o resto da linha). Numa GPU dedicada, o esperado é o custo de um passe de pós comum; a medição na RTX 4060 fica para o TESTE-REAL (caso 93).

### Capturas

`docs/gameplay/ceu-*-0120.jpg`: eclipse no Overworld, no Nether, depois do relogin e na dimensão de teste, F7, buraco negro, portal, lua gigante, quatro camadas e os três estados da dimensão de teste.

### Etapa 1: Sky API e o sol e a lua do jogo (conferido com javap, 22/11/2026)

- **Trocar o céu no NeoForge 21.1.267.** `IDimensionSpecialEffectsExtension` tem `renderSky(ClientLevel, int, float, Matrix4f, Camera, Matrix4f, boolean, Runnable)` (padrão `false`). `LevelRenderer.renderSky` chama `level.effects().renderSky(...)` como primeira instrução e retorna se devolver `true` (`javap -c`, offsets 0 a 30): é o único jeito de substituir o céu, e substitui **tudo** (gradiente, nascer do sol, sol, lua, estrelas). Não há evento para pular só o sol: `RenderLevelStageEvent.Stage.AFTER_SKY` é disparado depois de `renderSky` (offset 453 de `renderLevel`) e não cancela nada. `RegisterDimensionSpecialEffectsEvent.register` é um `Map.put` simples, chamado em ordem de mods por `DimensionSpecialEffectsManager.init`; registrar de novo uma chave vanilla (`minecraft:overworld`) troca os efeitos de toda dimensão que a usa e o último mod vence. O motor **não** faz isso nas dimensões vanilla.
- **Geometria do sol e da lua** (`LevelRenderer.renderSky`, `javap -c`, offsets 539 a 956): mistura `blendFuncSeparate(SRC_ALPHA, ONE, ONE, ZERO)` (soma), cor `(1, 1, 1, 1 - chuva)`, pose `Ry(-90)·Rx(getTimeOfDay·360)`; sol: quadrado de meio lado 30 em y = 100, uv (0, 0) no canto (-30, -30) e (1, 1) em (30, 30), textura `textures/environment/sun.png` (32 × 32, miolo de 8 × 8 texels: ~4,3 graus de meio lado; brilho até ~11 graus); lua: meio lado 20 em y = -100, atlas `moon_phases.png` 4 × 2 com coluna `fase % 4` e linha `fase / 4 % 2`, (u0, v1) no canto (20, 20) e (u1, v0) em (-20, -20). O sol e a lua só são desenhados com `skyType() == NORMAL`, sem névoa do mundo (`isFoggyAt` ou névoa de chefe), sem lava ou neve em pó na câmera e sem cegueira ou escuridão (offsets 38 a 134).
- **Decisão:** o passe `sky` (primeiro do quadro) apaga o sol ou a lua onde uma camada ancorada neles pede (`Sky.hidesVanilla`, padrão do ECLIPSE): como a soma é aditiva, subtrai os mesmos texels (`texelFetch`, a textura do jogo pelo `TextureManager`) no mesmo quadrado, calculado com o referencial acima; onde a soma estourou em 1 (o miolo), entra a estimativa do céu do jogo, que o disco do eclipse cobre. Só quando o caminho vanilla desenhou o sol naquele quadro (as condições acima e efeitos de dimensão que **não** sobrescrevem `renderSky`, conferido por reflexão uma vez por classe). `SkyCheck` confere o referencial contra o JOML (`Matrix3f.rotateY(-π/2).rotateX`), o centro e os cantos do sol e da lua. Para dimensões próprias, a etapa 4 usa `renderSky` (o céu inteiro passa a ser do motor).
- **RimuLib 1.3.0:** `Uniforms.texture(nome, local)` carrega pelo `FxTextures` da própria RimuLib (não pelo `TextureManager`); por isso o passe usa `glTexture(nome, id)` com o id da textura do jogo. `Uniforms.vec4s` leva as máscaras do céu (até 8 vec4) ao passe de atmosfera.

## Versão 0.11.2 — Etapa 1: custo por efeito e por passe (em andamento)

**Medido por software (Mesa llvmpipe, sem GPU). Os padrões novos de LOW, BALANCED, HIGH e ULTRA esperam a mesma medição na RTX 4060 do autor ([TESTE-REAL.md, parte 8](TESTE-REAL.md#parte-8--custo-por-efeito-e-por-passe-0112-antes-dos-perfis-novos)).**

Até a 0.11.1 nunca se mediu mais de 6 efeitos desenhados nem mais de 2 passes de addon ao mesmo tempo (o teto do BALANCED). A 0.11.2 adiciona:

- **Perfil interno de teste UNLIMITED** (`FrameLimits.unlimited`), só por comando: `/calamidade_client debug unlimited [desenhados passes]` e `debug off` (o F8 também sai dele). Sem teto de desenhados (até os 96 residentes), passes comuns e HERO até 96, feixes e luzes até as linhas das texturas de dados (128 e 64). Para os addons, `post.quality()` continua HIGH: o perfil de teste não cria valor novo na API pública. O teto de 2 HERO continua. O passe do campo do motor segue com 4 marcas no máximo (o shader só tem 4), em qualquer perfil.
- **Medidor `CostProbe`**: `/calamidade_client debug measure effects|passes|all` roda sempre a mesma sequência, pede a carga ao servidor (`/calamidade bench`), aquece 4 s e grava 8 s por passo, e salva `calamidade_bench/cost-*.txt` (tabela) e `cost-*.csv` (todos os quadros). O teste automatizado e o autor rodam o mesmo código.
- **`/calamidade bench <n> mix`**: anel, cubo, teia e céu do vfx-sample alternados (efeitos do motor sem o exemplo). Desde a 0.11.2, as instâncias do bench recomeçam no mesmo lugar quando terminam, até `bench stop`, para a carga ficar constante.
- **`xvfb-run -a ./gradlew testeMedicao`**: cliente no mundo do `testeJogo`, com o vfx-sample em `mods/`, reserva de jogadores em 0 durante a medição (para o bench chegar a 60), confere que cada passo teve a carga pedida e grava a tabela, as inclinações e duas capturas.
- `checkBudget` ganhou `LimitsCheck`: perfis iguais aos tetos da `Quality`, perfil de teste, máximos de segurança, seleção de 60 candidatos (57 com 5 HERO) e estatísticas (mediana, p95, média).

### Medição por software (22/11/2026)

Cliente NeoForge 21.1.267 sob Xvfb 1280 × 720, Mesa llvmpipe (LLVM 20.1.2, 256 bits) em 4 núcleos, limite de quadros 260, VSync desligado, distância 8, gráficos Fancy, mundo plano de dia, câmera parada olhando o arco do bench. Motor desta etapa e vfx-sample 1.8.0. `testeMedicao`: 25 de 25 verificações.

| Grupo | Passo | Quadros | Desenhados | Passes de addon | Feixes | Mediana (ms) | p95 (ms) |
|---|---|---|---|---|---|---|---|
| efeitos | base, sem efeitos | 384 | 0 | 0 | 0 | 20,50 | 26,45 |
| efeitos | 6 (anel, cubo, teia, céu) | 107 | 6,0 | 4,7 | 7,9 | 74,71 | 88,72 |
| efeitos | 12 | 70 | 11,9 | 6,5 | 23,3 | 116,03 | 132,09 |
| efeitos | 24 | 43 | 23,6 | 12,0 | 46,9 | 192,23 | 211,37 |
| efeitos | 48 | 24 | 46,5 | 22,5 | 92,0 | 336,03 | 390,31 |
| efeitos | 60 | 23 | 56,6 | 27,9 | 104,3 | 392,09 | 477,05 |
| passes | base, sem efeitos | 423 | 0 | 0 | 0 | 18,85 | 22,84 |
| passes | 12 cubos, teto 1 | 193 | 11,8 | 1,0 | 0 | 41,90 | 49,19 |
| passes | 12 cubos, teto 2 | 177 | 11,8 | 2,0 | 0 | 45,70 | 55,15 |
| passes | 12 cubos, teto 4 | 155 | 11,8 | 3,9 | 0 | 50,80 | 63,54 |
| passes | 12 cubos, teto 8 | 120 | 11,8 | 7,9 | 0 | 66,12 | 79,62 |
| passes | 12 cubos, teto 12 | 93 | 11,7 | 11,7 | 0 | 86,74 | 103,04 |

Desenhados e passes são médias por quadro. Nos passos de efeitos, os passes vêm só do anel e do cubo (a teia usa o passe único de feixes; o céu, o passe de atmosfera, fora do orçamento); 60 pedidos deram 57,9 residentes porque o anel (60 ticks) recomeça a cada 3 s e passa alguns ticks fora da janela de desenho.

Tendência (mínimos quadrados, base incluída):

- **Por efeito desenhado (mistura):** +6,5 ms de mediana e +7,8 ms de p95. Entre 6 e 60, a curva é quase reta (sem degrau): o custo cresce com o número de efeitos e de passes que eles pedem (cerca de 0,47 passe por efeito nessa mistura), não com um limite interno.
- **Por passe de addon (12 cubos sempre desenhados):** +4,9 ms de mediana e +5,8 ms de p95 pela reta; entre 1 e 12 passes, +4,2 ms de mediana por passe a mais. O primeiro passe custa bem mais (de 18,9 ms sem efeitos para 41,9 ms com 12 cubos e 1 passe): é o preço fixo de ligar o pós-processamento da RimuLib (cópias de quadro) e de chamar as 12 camadas de cliente, pago uma vez.

**Como ler:** no llvmpipe cada passe de tela cheia é desenhado pela CPU, então os milissegundos absolutos e a razão entre passe e resto do quadro são muito maiores que numa GPU. Servem para a forma da curva (linear em efeitos e em passes, custo fixo do primeiro passe), não para escolher os tetos. Os tetos da Etapa 2 saem dos números da GPU física.

Capturas da carga (não versionadas): `run/teste-medicao/screenshots/medicao-60-efeitos.png` e `medicao-12-passes.png`.

### Testes em jogo nesta etapa

- `./gradlew testeJogo`: na primeira execução, 50 de 51, com a verificação de tempo "custo: cratera grande com a casca externa" em 76,7 ms de pior tick (limite 50 ms; a 0.11.1 registrou 24,6 ms); na segunda, sem mudança de código, 51 de 51. A Etapa 1 não toca no servidor de terreno: o bench só registra um `ServerTickEvent.Post` que não faz nada sem bench ativo. Leitura: variação da máquina compartilhada com JVM fria; se voltar a acontecer, o limite precisa de folga.
- `xvfb-run -a ./gradlew testeMedicao`: 25 de 25.

### Build

- `./gradlew build` e `./gradlew dist`: passaram (com `checkBudget` e o `LimitsCheck` novo). Versão 0.11.2 no `build.gradle`, no `neoforge.mods.toml` e no JAR que o exemplo usa; `docs/ai/API-REFERENCIA.md` regenerado (só o título mudou: nenhuma mudança na `api.v1`).
- `./gradlew -p examples/vfx-sample build` contra o JAR 0.11.2: passou; o `vfx-sample-1.8.0.jar` saiu idêntico ao do release (SHA-256 `10b54362…`), então o exemplo 1.8.0 publicado serve para a medição.
- Rede sem mudança.

Conferido com javap (NeoForge 21.1.267): `ClientPacketListener.sendCommand(String)`, `Minecraft.getConnection()`, `Options.framerateLimit()` e `enableVsync()`, `PlayerList.op(GameProfile)`, `ServerTickEvent$Post`.

### Medição do autor na RTX 4060 (22/11/2026)

NVIDIA GeForce RTX 4060, janela 1920 × 1009, gráficos Fancy, distância 21, mesmo comando (`/calamidade_client debug measure all`) e motor da pré-release v0.11.2-pre1. Duas rodadas:

1. **Limite de 80 FPS e VSync:** todos os passos deram mediana de 12,5 a 12,6 ms (o limite). Não mede custo, mas mostra que até 48 desenhados e 12 passes tudo coube em 80 FPS.
2. **Limite de 260 FPS, VSync ainda ligado (monitor de 144 Hz):**

| Grupo | Passo | Desenhados | Passes | Feixes | Mediana (ms) | p95 (ms) |
|---|---|---|---|---|---|---|
| efeitos | base | 0 | 0 | 0 | 6,98 | 10,41 |
| efeitos | 6 | 5,9 | 4,6 | 7,9 | 6,94 | 9,90 |
| efeitos | 12 | 11,9 | 6,5 | 23,8 | 6,94 | 10,19 |
| efeitos | 24 | 23,8 | 11,9 | 47,6 | 6,99 | 11,34 |
| efeitos | 48 | 47,7 | 23,0 | 95,2 | 7,34 | 15,70 |
| efeitos | 60 (48 residentes: reserva não estava em 0) | 47,6 | 22,9 | 95,2 | 6,96 | 15,77 |
| passes | base | 0 | 0 | 0 | 6,94 | 9,10 |
| passes | 12 cubos, 1 passe | 11,9 | 1,0 | 0 | 6,93 | 9,61 |
| passes | 12 cubos, 12 passes | 11,9 | 11,9 | 0 | 6,95 | 10,08 |

**Leitura:** com VSync a 144 Hz a mediana fica presa em 6,9 ms (o intervalo do monitor), então ela mostra folga, não o custo. Até 24 efeitos desenhados e 12 passes de addon, nada muda nem na mediana nem no p95 (dentro de 1 ms): a GPU sobra dentro de cada quadro de 144 Hz. Com 48 desenhados (23 passes, 95 feixes) a mediana sobe pouco (7,34 ms) e o p95 vai de 10,4 a 15,7 ms: começam quadros perdidos. O custo por passe isolado (12 cubos) não aparece a 144 Hz. Software e GPU concordam na forma (custo cresce com efeitos e passes, sem degrau), e a GPU mostra que o teto de hoje (6 e 2 no BALANCED) estava muito abaixo do limite desta placa.

**Padrões novos (0.11.2), a partir disso:** LOW continua 3 / 1 / 1 (placa integrada, sem medição: fica como na 0.11.1); BALANCED 8 desenhados, 3 passes, 2 HERO, 64 feixes, 32 luzes; HIGH 16, 6, 3, 128, 64; ULTRA 32, 12, 4, 128, 64 (abaixo dos 48 onde o p95 subiu). O AUTO sobe até ULTRA quando há folga e o teto corta efeitos. Pendente: uma rodada com VSync desligado, para o custo absoluto por efeito e por passe.

### Etapas 2 e 3: perfis configuráveis e qualidade automática (22/11/2026)

- **Perfis:** `QualityProfile` LOW, BALANCED, HIGH e ULTRA (padrões na seção acima, desde a medição da RTX 4060; ULTRA usa 1,5 vez os vértices do HIGH e aparece aos addons como HIGH). Cada perfil tem os seis tetos no config de cliente (`[quality.low]`, `[quality.balanced]`...), com máximos de segurança (desenhados 64, passes 16, passes HERO 8, HERO 4, feixes 128, luzes 64; HERO nunca acima dos desenhados).
- **AUTO (padrão novo, `quality.mode`):** janelas de 2 s; desce depois de 2 janelas seguidas com p95 acima de 1,2 × o alvo (`autoTargetFps`, padrão 60) com efeitos na tela em pelo menos metade dos quadros; sobe depois de 4 janelas com p95 abaixo de 0,7 × o alvo enquanto o teto cortou algo em pelo menos um quarto dos quadros; 6 s sem avaliar depois de qualquer troca (e ao entrar no AUTO); voltar ao perfil de onde desceu fica bloqueado por 60 s, o dobro a cada nova descida do mesmo perfil (até 10 min); janela que viu um efeito HERO é descartada e zera as sequências. F8: AUTO → LOW → BALANCED → HIGH → ULTRA → AUTO. F7: "Qualidade: AUTO → perfil · desceu/subiu de X há N s (p95 … ms > … ms)".
- **Testes puros (`AutoQualityCheck`, no `checkBudget`):** desce em 2 janelas e respeita o intervalo, até LOW e nunca abaixo; quadros lentos sem efeitos não descem; sobe em 4 janelas com corte, até ULTRA; quadros rápidos sem corte não sobem; nada muda durante um HERO e as sequências recomeçam depois; um caso que oscilaria (HIGH lento, BALANCED com folga e cortes) troca no máximo 7 vezes em 10 minutos e no máximo mais uma nos 5 minutos seguintes; picos de 2% dos quadros não descem. `LimitsCheck` ganhou os perfis novos, os máximos de segurança e o teto de HERO configurável.
- **Em jogo (`testeMedicao`, llvmpipe): 30 de 30.** AUTO desceu de HIGH para BALANCED em 8 s sob 24 instâncias da mistura (p95 67,3 ms > 20,0 ms), o F7 mostrou o motivo ([captura](gameplay/auto-f7-0112.jpg)); sem efeitos na tela o perfil ficou parado 20 s; com a receita protagonista (HERO, 12 s) e carga pesada, nenhuma troca enquanto ela existia, e a descida veio depois. A subida precisa de folga de quadro que o llvmpipe não tem: fica para o TESTE-REAL na GPU.

### Etapa 4 (engenharia): Atlas Estelar no mundo (22/11/2026)

O `StarAtlas` saiu; o Atlas agora é um conjunto de placas paradas no mundo (`client/holo`: `AtlasWorld`, `AtlasInput`, `Atlas`, `HoloPanel`, `HoloSurface`, `HoloZone`, `HoloGl`; puro em `api/holo`: `HoloGeometry`, `HoloPlacement`, `HoloInputArbiter`). A engenharia foi adaptada do menu holográfico da tecla K do RespectCraft (mesmo autor, que autorizou a cópia; handoffs 4.8.1, 4.8.4-A e 4.8.4-B, repositório `Juann027/MineModding_01`, commit `f63a448`), reescrita no namespace do Calamidade, sem importar nada do RespectCraft. O visual ainda é o esqueleto neutro, à espera da escolha entre as direções A, B e C ([prévias](atlas-previas/README.md); [esqueleto no jogo](atlas-previas/esqueleto-no-jogo.jpg)).

- **Conferido com javap (NeoForge 21.1.267):** `RenderLevelStageEvent.getModelViewMatrix/getProjectionMatrix`, `Stage.AFTER_LEVEL`, `Camera.getPartialTickTime`, `Entity.getViewVector(float)`, `InputEvent.MouseButton.Pre` (`getButton`, `getAction`), `MouseScrollingEvent.getScrollDeltaY`, `MouseHandler.xpos/ypos/isMouseGrabbed/grabMouse/releaseMouse` e `onPress(long, int, int, int)`/`onScroll(long, double, double)` (privados, usados pelo teste), `TextureTarget(int, int, boolean, boolean)`, `RenderTarget.resize/clear/bindWrite/getColorTextureId/setFilterMode/destroyBuffers`, `RenderSystem` (projeção, ordenação, pilha de model-view, névoa, recorte, `blendFuncSeparate`), `ClientHooks.getGuiFarPlane`, `GuiGraphics.blit(ResourceLocation, int ×4, float ×2, int ×4)`, `VertexConsumer.addVertex(Matrix4f, …)`, `DynamicTexture(NativeImage)` e `NativeImage.setPixelRGBA`. `ConfigSync.syncConfigs` manda as configs SERVER ao cliente: a aba Terreno lê a config do servidor sem pacote novo.
- **Testes puros (`HoloCheck`, no `checkBudget`):** o olho do Atlas cai na âncora e a frente segue o yaw (4 yaws); direita e baixo do layout; o mesmo ponto local pelo desenho e pelo raio da mira com a câmera em 3 lugares; placa atrás da câmera recusada; posição em campo aberto, com parede (aproxima), com pilar (gira 50°) e sem espaço (avisa); dono da entrada (placa ganha só no alcance e mais perto que o alvo do vanilla; dono decidido no aperto até soltar; fontes independentes; logout limpa).
- **Em jogo (`xvfb-run -a ./gradlew testeAtlas`, llvmpipe 1280 × 720, criativo): 25 de 25.** Entrada pelo caminho real (`MouseHandler.onPress/onScroll`). Placas paradas no mundo enquanto o jogador anda; as cinco abas; clique esquerdo numa aba com pedra atrás (pedra intacta, aba muda, slot igual); clique direito com tábuas na mão (nada colocado); roda sobre um cartão troca a página (2 páginas com o vfx-sample) sem mexer na barra, e fora das placas troca o slot; Recall; clique num cartão com uma vaca atrás (vida 10,0 → 10,0, habilidade conjurada pelo servidor, Atlas fecha); controle sem o Atlas (o mesmo clique quebra a pedra); fecha a mais de 24 blocos, na troca de dimensão e na morte; reabre depois do renascimento; N fecha com a animação.
- **Achados e corrigidos no teste:** (1) as superfícies saíam vazias: faltava a translação de profundidade da GUI (`10000 − guiFarPlane`) na model-view ao desenhar nelas; (2) os efeitos de addon têm id negativo e o filtro `id < 0` descartava todas as páginas de addons; (3) com uma página só, a roda sobre um cartão trocava de aba (agora: roda na fita troca a aba, nos painéis de Habilidades troca a página, nos outros não faz nada).

### Pendente

- Rodada da RTX 4060 com VSync desligado (custo absoluto); placa integrada para conferir o LOW.
- AUTO subindo numa GPU física (TESTE-REAL).
- Capítulo do Atlas, docs e release (Etapa 5). Direção visual escolhida pelo autor: B (Astrolábio).

## Versão 0.11.1 — compatibilidade da Terrain API (estado atual)

**Compilada e coberta pelos testes de build; executada em jogo por dois roteiros automatizados que estão no repositório: `./gradlew testeJogo` (servidor dedicado sem tela, NeoForge 21.1.267: 51 de 51 verificações, em duas execuções para o reinício) e `xvfb-run -a ./gradlew testeCliente` (cliente, Mesa llvmpipe: 10 de 10). Também com ScalableLux, C2ME, Sodium, Embeddium e Distant Horizons instalados. Mods de proteção reais, jogadores de verdade e GPU física continuam pendentes (TESTE-REAL.md, parte 7).**

### Build

Mesmo ambiente da 0.11.0 (OpenJDK 21.0.13, Gradle 9.2.1, glslangValidator 15.1.0, RimuLib 1.3.0 conferida por SHA-256).

- `./gradlew clean build dist`: passou em cada etapa, com `checkBudget`, `checkFrustum`, `checkShaders` (12 shaders compilados com o glslangValidator), `checkApiDocs`, `checkRecipes` e `checkModVersion`. O JAR não tem nenhuma classe de `src/testeJogo`.
- `./gradlew -p examples/vfx-sample build` (1.8.0, sem mudança de código, contra o JAR 0.11.1) e `./gradlew -p examples/consumidor build -Pcalamidade.url=file:///…/dist/calamidade_awakening-0.11.1.jar`: passaram.
- **Build reproduzível:** dois `clean build dist` seguidos deram o mesmo SHA-256.
- **API pública idêntica à da 0.11.0:** `javap -s` de todas as classes de `api.v1` no JAR do release v0.11.0 (baixado do GitHub, SHA-256 `db89b4b3…`, igual ao publicado) e no JAR 0.11.1: 2 586 linhas, nenhuma diferença. Só o Javadoc do `TerrainEditEvent` ganhou um parágrafo.
- Rede sem mudança (`terrain_v1`, `event_v2`, `end_effect`, `request_v2`, `release`).
- **Depois do release v0.11.1** (workflow Release, tag no commit `d4fcef3`): os anexos publicados têm os mesmos SHA-256 do build local (`a4dfa448…` e `10b54362…`), e `./gradlew -p examples/consumidor clean build`, sem endereço alternativo, baixou `calamidade_awakening-0.11.1.jar` do release no GitHub, conferiu o SHA-256 e compilou.

SHA-256 de `dist/calamidade_awakening-0.11.1.jar`: `a4dfa44853bee79344b84bddc32a443cc0c89321b4f68bca3a0db297f03e6c21`

SHA-256 de `examples/vfx-sample/build/libs/vfx-sample-1.8.0.jar` (compilado contra a 0.11.1): `10b5436279c98d34af5f73628696a3234c3900993be1df638a3a7392846ea0ac`

### Conferido com `javap -c` (NeoForge 21.1.267)

- `LevelChunk.setBlockState`: `Heightmap.update` dos quatro mapas; `updateSectionStatus` quando a seção muda de vazia para não vazia (ou o contrário); `ChunkSkyLightSources.update` e `LevelLightEngine.checkBlock` **só** quando `LightEngine.hasDifferentLightProperties`. O escritor da 0.11.0 chamava `update` das fontes do céu sempre; a 0.11.1 segue o ramo do vanilla.
- `LevelChunkSection.setBlockState`: atualiza `nonEmptyBlockCount`, `tickingBlockCount` e `tickingFluidCount` a cada troca.
- `ServerLevel.onBlockStateChange`: tira e põe pontos de interesse (`PoiTypes.forState`) pela fila de tarefas do servidor.
- `ChunkMap.tick` chama `ThreadedLevelLightEngine.tryScheduleUpdate` a cada tick: os `checkBlock` enfileirados são processados no fim do tick.
- `ChunkHolder.broadcastChanges` manda a luz só aos jogadores na borda da distância de visão e `broadcastBlockEntityIfNeeded` manda `getUpdatePacket()` de cada block entity mudada; o escritor da 0.11.0 não mandava esse segundo.
- `ServerChunkCache.getChunkNow`: `null` fora da thread do servidor; só `LevelChunk` com status `FULL` (ou o `currentlyLoading` do NeoForge, durante o evento de carga). `LevelChunk.getFullStatus` vem do nível do ticket.
- `DedicatedServer.isUnderSpawnProtection`: só no overworld, só com operadores cadastrados, operador isento, `max(|dx|, |dz|) <= spawn-protection`. `MinecraftServer.isUnderSpawnProtection` (singleplayer) devolve sempre falso. `ServerLevel.mayInteract` = proteção de spawn + `WorldBorder.isWithinBounds`.
- `Explosion(Level, Entity, x, y, z, raio, fogo, BlockInteraction, List<BlockPos>)` põe a lista em `toBlow`; `ExplosionEvent.Detonate.getAffectedBlocks()` é esse `toBlow`; `EventHooks.onExplosionStart` posta `ExplosionEvent.Start` (cancelável). O motor posta os dois eventos direto no `NeoForge.EVENT_BUS` e nunca chama `explode` nem `finalizeExplosion`.
- Distant Horizons 3.3.3: o mixin do servidor injeta no `RETURN` de `ChunkMap.save(ChunkAccess)` (bytecode do JAR do DH).

### Em jogo: servidor dedicado (`./gradlew testeJogo`)

Servidor dedicado do NeoForge 21.1.267 sem tela, mundo plano próprio (bedrock, 60 de pedra, 3 de terra e grama: superfície em y = 0), `spawn-protection=16`, um operador fictício em `ops.json`, mod de teste `calamidade_teste` (em `src/testeJogo`, fora do JAR) com crateras sem borda irregular (a forma não depende da seed). Cada cenário numa região própria, com os chunks forçados. A segunda execução reabre a mesma pasta.

| Caso | Resultado |
|---|---|
| Listener que só conhece `ExplosionEvent` protege `x ≥ cx` | `Start` e `Detonate` uma vez cada; explosão sintética com 417 posições, raio 11,7, fonte nula (script); o listener tirou 234; 0 posições mudaram do lado protegido e 183 do outro; `x = cx − 1` cavado e `x = cx` intacto; nenhuma entidade de item |
| `Start` cancelado | recusa `EXPLOSION`, nenhum bloco muda |
| `terrainExplosionEvent = false` | listener não chamado; cratera inteira (183 e 234 posições) |
| Borda do mundo no meio da cratera | 0 posições fora da borda; 183 dentro |
| Proteção de spawn 16, fonte script | 0 posições na área do spawn; 183 fora |
| Luz: cratera com casca e glowstone (417 blocos) pela Terrain API x a mesma troca por `setBlock` (`UPDATE_CLIENTS \| UPDATE_KNOWN_SHAPE`) 64 blocos ao sul | luz do céu e dos blocos igual em 37 × 31 × 37 posições; brilho igual ao meio-dia e à meia-noite (fundo 15 e 11); glowstone dá 15 |
| Mapas de altura (4 tipos) | iguais aos da cópia e a uma varredura das colunas |
| Contadores das seções | iguais aos de `recalcBlockCounts()` |
| Desfazer | blocos, luz, mapas de altura e contadores iguais aos de antes |
| Pontos de interesse | composteira, mesa de ferraria e magnetita saem com a cratera e voltam no desfazer; composteiras do preenchimento entram e saem |
| Casca externa (cerca na borda, água ao lado) | água que tocava a cratera vedada; tick de fluido na água vizinha da vedação e não nas seguintes; formas das cercas consistentes depois do commit e do desfazer (2 formas mudadas, 132 vizinhos); nenhuma água na cratera |
| Custo, esfera r24 com casca de 2 (36 754 escritos) | 9 ticks, 90,6 a 102,4 ms, 3 621 vizinhos na casca externa, pior tick 25,5 a 25,9 ms; verificações de proteção 19 a 22 ms num tick; desfazer em 7 ticks, pior tick 18,3 a 32,1 ms |
| Chunks não FULL | só o chunk FULL muda; 2 seções puladas; o chunk não FULL não é carregado e, carregado depois, está intacto |
| Reinício | depois de salvar tudo, o jogo salva de novo o chunk da cratera (`ChunkDataEvent.Save`); na segunda execução, as 224 posições e os 2 POI das composteiras estão lá |

**O que o teste achou:** (1) a casca externa não recebia atualização (cerca com a conexão velha, nenhum tick de fluido): corrigido com o `TerrainBorder`. (2) Com o ScalableLux, o commit quebrava com `NullPointerException` em `ChunkSkyLightSources.update` (o mod deixa as fontes nulas): corrigido. (3) No próprio roteiro: o servidor ocioso salva até 20 chunks modificados por tick (`ChunkMap.processUnloads`), então a marca de modificado tem de ser conferida pelo salvamento (`ChunkDataEvent.Save`), não lida depois; e o `Effects.start` recusa um centro em chunk não carregado (o cenário FULL põe o centro no chunk FULL).

### Em jogo: cliente (`xvfb-run -a ./gradlew testeCliente`)

Cliente de desenvolvimento sob Xvfb 1280 × 720, Mesa llvmpipe (LLVM 20.1.2), no mundo deixado pelo `testeJogo` (`--quickPlaySingleplayer`), câmera em modo espectador.

| Caso | Resultado |
|---|---|
| Luz recalculada pelo cliente, cratera da Terrain API x a do `setBlock` (37 × 31 × 37 posições, céu e blocos) | 0 diferenças entre as duas; 0 diferenças em relação ao servidor |
| Capturas das duas crateras do mesmo ponto, comparadas em blocos de 40 px | dia: diferença média 0,23, máxima 2,2 de 255; noite: 0,15 e 3,3 (sem manchas) |
| Jogador em modo aventura dispara a cratera | o efeito toca, recusa `PERMISSION`, nada muda; em sobrevivência, a cratera é comitada |
| Placa com texto na cratera (`terrainBlockEntities` e `replacingBlockEntities()`), depois desfazer | a placa sai (também no cliente) e volta com o texto no servidor **e no cliente** |

**O que o teste achou:** a placa devolvida pelo desfazer aparecia vazia no cliente (o servidor tinha o texto): corrigido mandando o pacote de dados de cada block entity nova. No próprio roteiro: a textura da grama gira conforme a posição, então as crateras A e B nunca são iguais pixel a pixel; a comparação é feita por blocos de 40 × 40 px (média), que acusa manchas de luz.

### Outros mods (baixados do Modrinth, SHA-512 conferido)

| Mod | Rodada | Resultado |
|---|---|---|
| ScalableLux 0.3.0-alpha.0.8+1.21.1 | `testeJogo` | antes da correção, o primeiro commit quebrava; depois, 27 de 27 na rodada da Etapa 2 (proteção e luz) |
| Moonrise 0.1.0-beta.15+2eae1b1 | `testeJogo` | **não abre:** `InjectionError` no mixin `rimulib.mc1211.world.mixins.json:ServerLevelMixin` (`rimulib$discard`), cuja chamada o Moonrise já redireciona (`redirectClose`). Conflito RimuLib x Moonrise |
| C2ME 0.4.0-alpha.0.124+1.21.1 | `testeJogo` | 51 de 51, inclusive o reinício |
| Sodium 0.8.13+mc1.21.1 | `testeCliente` | 10 de 10 (imagem de dia 0,23/2,1; de noite 0,14/3,5) |
| Embeddium 1.0.15+mc1.21.1 | `testeCliente` | 10 de 10 (dia 0,23/2,2; noite 0,12/3,2) |
| Distant Horizons 3.3.3 | `testeCliente` | 10 de 10; o LOD distante não foi conferido (veja TERRENO-0.11.md) |

Capturas: [luz de dia e de noite, Terrain API x setBlock](gameplay/luz-0111.jpg) · [Sodium de dia e Embeddium de noite](gameplay/sodium-embeddium-0111.jpg).

### Pendente

- TESTE-REAL.md, parte 7 (casos 75 a 81): FTB Chunks, Open Parties and Claims e GriefDefender reais; proteção de spawn e modo aventura com jogadores de verdade; aldeões; LOD do Distant Horizons num servidor sem DH; GPU física com Sodium, Embeddium e shader packs.
- Os pendentes da 0.11.0 (parte 6) continuam.

---

## Versão 0.11.0 — Terrain API

**Compilada e coberta pelos testes de build; executada em jogo por um roteiro automatizado em renderização por software (Mesa llvmpipe), num cliente, no NeoForge 21.1.267: 27 de 27 verificações, com as medições da Etapa 7. Dois jogadores num servidor dedicado, GPU física e os casos manuais de TESTE-REAL.md (63 a 74) continuam pendentes.**

### Build

Minecraft Java 1.21.1, NeoForge 21.1.267, RimuLib 1.3.0 (task `rimulib`, SHA-256 `15ccd4958a0412cbcf618a10962b0b6a7e1683fd66b94d07e10bc7cabeedb774`), OpenJDK 21.0.13, Gradle 9.2.1, glslangValidator 15.1.0.

- `./gradlew build`: passou em cada etapa, com `checkBudget` (`FrameBudgetCheck PASS`, com o novo `TerrainCheck`), `checkFrustum`, `checkShaders` (12 shaders: entra `terrain.fsh`, que inclui `calamidade_fx.glsl` e `calamidade_terrain.glsl`), `checkApiDocs`, `checkRecipes` e `checkModVersion`.
- `./gradlew dist` e `./gradlew -p examples/vfx-sample build`: passaram, sem avisos.
- **NeoForge 21.1.265:** `./gradlew clean compileJava checkBudget checkFrustum jar -Pneoforge.version=21.1.265` e o exemplo com a mesma opção passaram; o JAR compilado contra a 21.1.265 é byte a byte igual ao da 21.1.267. O roteiro em jogo desta versão rodou só na 21.1.267.
- `node scripts/testar_glsl.cjs`: 139 de 139 casos em WebGL2 (33 novos: hash, ruído, fbm, as quatro primitivas e a borda irregular, contra valores calculados pelo Java; hash exato, ruído em 2 × 10⁻⁶, distâncias em 2 × 10⁻⁵).

SHA-256 de `dist/calamidade_awakening-0.11.0.jar`: `db89b4b34461a71cc022232814a8faa390d3c8902af1c4966b5183ff1c4d0d1d`

SHA-256 de `examples/vfx-sample/build/libs/vfx-sample-1.8.0.jar`: `10b5436279c98d34af5f73628696a3234c3900993be1df638a3a7392846ea0ac`

**Depois do release v0.11.0:** os anexos publicados pelo workflow têm os mesmos SHA-256 do build local (`db89b4b3…` e `10b54362…`), e `./gradlew -p examples/consumidor clean build`, sem endereço alternativo, baixou `calamidade_awakening-0.11.0.jar` do release no GitHub, conferiu o SHA-256 e compilou.

### Passo 0: CarveApi

Conferida com `javap -p` e `javap -c` (`CarveApi`, `CarveApi$Cut`, `terrain.Carving`, `SectionCopy`, `SectionSink`): cortes só de coluna (um `Floor.at(x, z)` por coluna, tudo acima sai), `prepare()` só põe o corte numa fila e o preparo roda na thread do servidor copiando seções (`SectionCopy.states`) e cortando as cópias num pool de 2 a 8 threads, `ready()` = todos os chunks montados, aplicação por troca do vetor de seções do chunk, luz por `propagateLightSources` + `lightChunk(chunk, true)` e reenvio do chunk inteiro, orçamento estático de 30 ms por tick compartilhado por todos os cortes e 12 chunks por tick, block entities removidas, predicado de blocos mantidos global. **Decisão:** escrita direta nas `LevelChunkSection` com planejador próprio, sem a `CarveApi` (formas e casca 3D, orçamento em blocos, regras por bloco e desfazer); a justificativa completa está em TERRENO-0.11.md.

### Integração conferida no JAR (javap)

Minecraft 1.21.1: `LevelChunkSection.setBlockState(int, int, int, BlockState, boolean)`, `hasOnlyAir`; `LevelChunk.setBlockState` (bytecode: heightmaps `MOTION_BLOCKING`, `MOTION_BLOCKING_NO_LEAVES`, `OCEAN_FLOOR`, `WORLD_SURFACE`, `ChunkSkyLightSources.update`, `updateSectionStatus`, `LightEngine.hasDifferentLightProperties` + `checkBlock`, `onRemove`/`onPlace`, block entities), `getBlockEntity(pos, EntityCreationType)`, `removeBlockEntity`, `setUnsaved`, `getSkyLightSources`; `Heightmap.update`; `ThreadedLevelLightEngine.checkBlock`, `updateSectionStatus`; `ServerLevel.onBlockStateChange`; `ServerChunkCache.getChunkNow`, `chunkMap`; `ChunkMap.getPlayers(ChunkPos, boolean)`; `ChunkHolder.broadcastChanges` (bytecode: a luz vai aos jogadores na borda da distância de visão, os outros relightam no cliente); `ClientboundSectionBlocksUpdatePacket(SectionPos, ShortSet, LevelChunkSection)` e `SectionPos.sectionRelativePos` (bytecode: `x << 8 | z << 4 | y`); `SectionPos.asLong`, `of`; `StructureTemplateManager.get`, `StructureTemplate.save`; `NbtUtils.readBlockState`; `BlockEntity.loadWithComponents`, `saveWithFullMetadata`; `BlockState.rotate(Rotation)`, `getDestroySpeed`, `isSolid`, `hasBlockEntity`, `getFluidState`; `Block.getId`/`stateById`; `FallingBlock.isFree`; `BlockTags.LOGS`, `LEAVES`, `WITHER_IMMUNE`; `GameRules.RULE_MOBGRIEFING`; `BoundingBox`; cliente: `VertexBuffer` (`upload`, `drawWithShader`), `ByteBufferBuilder`, `BufferBuilder`, `VertexConsumer.putBulkData`, `BlockRenderDispatcher.getBlockModel`, `BakedModel.getQuads`, `BakedQuad.isTinted`/`isShade`/`getDirection`, `RenderType.translucent`, `GameRenderer.getRendertypeTranslucentShader`, `ShaderInstance.CHUNK_OFFSET`, `RenderSystem.polygonOffset`, `LightTexture.FULL_BRIGHT`, `ParticleTypes.CAMPFIRE_COSY_SMOKE`, `POOF`, `BLOCK`, `BlockParticleOption`.

NeoForge 21.1.267: `ModConfigSpec.Builder.defineListAllowEmpty`, `ICancellableEvent` e `Event` (bus 8.0.5), `NeoForge.EVENT_BUS`, `RenderLevelStageEvent` (`getModelViewMatrix`, `getProjectionMatrix`, `Stage.AFTER_TRANSLUCENT_BLOCKS`).

### Testes novos

- **`TerrainCheck`** (Java puro, no `checkBudget`): hash com dois valores fixos (conferidos por uma implementação independente em Python), faixa, continuidade, determinismo e espalhamento do ruído; distâncias das quatro primitivas, união, subtração, deslocamento e borda irregular (sempre dentro da amplitude); volumes contra a fórmula (esfera r12 e r24 em 1,5%, cilindro em 2%, trincheira em 2,5%, cratera parabólica e cone, clearance); determinismo do plano pela seed e independência da seed sem borda irregular; vedação (todo vizinho de face de um bloco cavado está no plano) e faixas da casca para espessuras 1, 2 e 3; trincheira ao longo de z (yaw 0), de x (yaw 90), da diagonal (yaw −45) e enterrada com pitch 30; lotes em ordem de distância, entradas crescentes, soma igual ao total, ordem estável, primeiro lote na seção da origem; pesos da paleta (100 000 sorteios em 2% e 5%), faixa vazia, determinismo; preenchimento sólido e oco; rotação de estrutura (bijeção nas 4 rotações, sentido horário igual ao `Rotation.CLOCKWISE_90`, canto e paleta por posição); calor, queda e revelação do dublê; validação e registro (ordem, duplicados, 5 edições).
- **GLSL:** os 33 casos novos acima.

### Compatibilidade

- O código do exemplo 1.7.0 (da 0.10.0) compila sem mudança contra o JAR 0.11.0.
- O JAR do vfx-sample 1.7.0 baixado do release v0.10.0 (SHA-256 `93741188…`, igual ao publicado): as 197 referências ao motor, em 30 classes, existem no JAR 0.11.0 com a mesma assinatura (`javap -v` contra `javap -p -s`).
- A API da 0.10 continua inteira; `Effects.terrain`, `TerrainShape`, `TerrainPalette`, `TerrainEdit`, `TerrainEditEvent` são adições.
- Rede: canal novo `terrain_v1`; `event_v2`, `end_effect`, `request_v2` e `release` iguais. Servidor e clientes na mesma versão.

### Em jogo (roteiro automatizado, Mesa llvmpipe)

Cliente de desenvolvimento do NeoForge 21.1.267 sob Xvfb 1280 × 720, Mesa llvmpipe (LLVM 20.1.2, 256 bits), 8 a 40 FPS, BALANCED, distância 8, com os JARs acima, a RimuLib e um mod temporário de teste (fora do repositório) que cria um mundo plano próprio (bedrock, 60 de pedra, 3 de terra e grama: superfície em y = 0), roda os comandos como jogador em modo espectador, chama a API e consulta o mundo pela thread do servidor, lê o F7 por reflexão e tira capturas. Quatro rodadas; as duas primeiras acharam os defeitos abaixo, a terceira e a quarta (JARs finais) passaram inteiras.

| Passo | Resultado |
|---|---|
| `cratera_simples` | Commit no tick do marcador: 2 447 planejados, 888 escritos, 1 tick, 8 pacotes de seção; centro cavado; 590 blocos da paleta na borda; F7: 0 zonas antes e 1 zona e 1 luz depois; calor some até o tick 215. |
| `/calamidade terrain undo` | 888 restaurados em 1 tick (3,3 ms); a caixa 25 × 21 × 26 em volta ficou **idêntica** à de antes (0 posições diferentes). |
| `meteoro` | Na queda, F7 "1 dublês · 0 zonas"; depois, 1 zona; 81 blocos de ouro bruto no centro (3 597 planejados, 1 393 escritos, 2,8 ms). |
| `raio_trincheira` olhando para o oeste | Rasgo de 33 blocos ao longo de x e 4 em z: segue o yaw. |
| `torre_caindo` (yaw 0 e 90) | Malha do dublê com 1 672 vértices (montada em 2,4 ms); torre colocada; barril com os 3 pães do template; barril − lanterna = (−1, −8, +1) em yaw 0 e (−1, ·, −1) em yaw 90 (rotação de 90°). |
| Proteção | Baú (block entity), bedrock e `reinforced_deepslate` dentro da cratera intactos; água posta ao lado da borda: 0 blocos de água dentro da cratera. |
| `TerrainEditEvent` cancelado | 1 evento, 1 recusa por evento, nenhum bloco mudou. |
| `mobGriefing` (fonte: um porco, mundo pacífico) | Desligado: recusa por mobGriefing, nada muda; ligado: commit. |
| Config | `terrainEnabled = false`, `terrainDimensions = [the_end]`, `terrainMaxVolume = 100`: uma recusa de cada, nenhum commit. |
| Chegada tardia | Com os chunks presos por `/forceload`, o jogador sai a 3 000 blocos (o chunk descarrega no cliente) antes do impacto e volta 2 s depois: cratera com a esfera de ouro no cliente e 1 zona quente (o calor ainda esfriando). |
| Regressão 0.10 | `leque`: 5 feixes. |

Nenhum erro de shader e nenhuma exceção do motor ou do exemplo no log; as exceções são do áudio (sem OpenAL) e da rede da Mojang, esperadas neste ambiente.

**O que o teste em jogo achou e foi corrigido:** (1) **o cliente não via a cratera** (a do servidor estava certa): o pacote de seção usa o empacotamento do jogo, `x << 8 | z << 4 | y` (conferido com `javap -c` em `SectionPos.sectionRelativePos`), e o escritor passava o do plano, `y << 8 | z << 4 | x`; o cliente aplicava os blocos transpostos. (2) Logo depois do commit, o buraco mostrava o céu por 1 a 3 quadros (seções ainda sendo remontadas): o passe `terrain` passou a pintar o fundo quente nesses pixels nos primeiros 1,5 s. (3) Brilho forte e largo demais (um disco amarelo saturado): brasa, fissuras, aura e a luz do motor mais baixas e a borda do calor mais estreita. (4) O relatório do commit lia o tempo da thread antes de somar o último lote. (5) No próprio roteiro: o zumbi do teste de `mobGriefing` sumia no modo pacífico (trocado por um porco) e o `/fill` vindo do cliente rodava fora do tick medido (veja abaixo).

Capturas: [cratera: antes, impacto, quente, esfriando, fria, desfeita](gameplay/cratera-0110.jpg) · [meteoro: queda, impacto, revelação, depois](gameplay/meteoro-0110.jpg) · [raio e trincheira](gameplay/raio-trincheira-0110.jpg) · [torre: queda, pouso, depois, girada](gameplay/torre-0110.jpg) · [proteção e água; chegada tardia](gameplay/protecao-tardia-0110.jpg).

### Medição (Etapa 7)

Mesmo mundo plano, regiões novas para cada caso, jogador parado a ~35 blocos olhando para a área. MSPT: duração de cada tick do servidor entre `ServerTickEvent.Pre` (prioridade mais alta) e `Post` (mais baixa). **No singleplayer, um comando vindo do cliente roda na fila de tarefas do servidor, fora desse intervalo**: na terceira rodada o `/fill` parecia de graça por isso. Na quarta, o `/fill` e a ignição da TNT são executados **dentro** do tick (no `Pre`, pelo mod de teste), com a duração do próprio comando medida. Tempo de quadro: intervalo entre `RenderFrameEvent.Pre` seguidos, em llvmpipe (CPU dividida entre o servidor integrado, a remontagem das seções e a rasterização): serve para comparar os casos entre si, não como número de GPU. Repouso: mediana 22,3 ms, p95 28,4 ms.

| Caso (mesmo volume: esfera de raio 12 ≈ 7 238 blocos) | Thread do servidor | Pior tick | Ticks acima de 50 ms | Quadros nos 3 s seguintes (mediana / p95 / máx.) |
|---|---|---|---|---|
| **(a) Terrain API**, esfera r12 sem casca e sem calor, 1ª | commit 13,6 ms, 1 tick (7 224 escritos, 14 pacotes de seção) | 15,2 ms | 0 | 24,5 / 41,3 / 45,9 ms |
| (a) 2ª | commit 10,7 ms, 1 tick | 12,1 ms | 0 | 24,7 / 45,6 / 48,5 ms |
| **(b) `/fill`** de ar num cubo 19 × 20 × 19 (7 220 blocos), dentro de um tick, 1ª | comando 44,7 ms | 51,6 ms | 1 | 20,7 / 25,3 / 35,9 ms |
| (b) 2ª | comando 16,0 ms | 21,1 ms | 0 | 40,8 / 61,1 / 65,7 ms |
| **(c) TNT empilhada**: 5 × 5 × 5 = 125 TNT na pedra, acesa por redstone | 1 077 ms somados em 140 ticks (explosões, entidades, drops, luz) | 107,8 ms | 3 | 28,8 / 58,7 / 66,2 ms (7 s) |
| **Cratera grande**: esfera r24 com casca de 2, em lotes de 16 384 | 63,5 ms em 5 ticks (73 379 planejados, 71 494 escritos, 56 pacotes) | 24,6 ms | 0 | 58,9 / 92,0 / 176,6 ms (4 s) |

Leitura:

- **Servidor:** para o mesmo volume, o commit da Terrain API custou 10,7 a 13,6 ms, contra 16,0 a 44,7 ms do `/fill` (que, conferido com `javap -c` em `FillCommand.fillBlocks` e `BlockInput.place`, faz `updateFromNeighbourShapes` e `setBlock(pos, estado, 2)` em cada posição e depois `blockUpdated` em todas) e cerca de 1,1 s espalhado por 7 s de explosões da TNT, com picos de 108 ms. A TNT destruiu 5 701 blocos (menos que os outros) e deixou 53 entidades de item. A cratera de raio 24 (10 vezes o volume) ficou abaixo de 25 ms por tick porque o commit é dividido em lotes: nenhum tick passou de 50 ms.
- **Cliente:** a Terrain API e o `/fill` mudam o mesmo número de seções e custam parecido para remontar; em llvmpipe o ruído entre rodadas é do tamanho da diferença (veja a 2ª do `/fill`). A TNT custa mais por mais tempo (partículas, entidades, sete segundos de mudanças). A cratera r24 remonta 56 seções em poucos ticks: a mediana dos quadros subiu para 59 ms por 4 s em renderização por software; numa GPU física esse custo é da CPU (compilação das seções) e precisa ser medido (TESTE-REAL.md, caso 72).
- **Sob o impact frame:** o commit cabe num tick até ~16 000 blocos com o orçamento padrão; acima disso o resto entra nos ticks seguintes, ainda dentro de um clarão de 4 a 8 ticks.

### Pendente

- TESTE-REAL.md, parte 6 (casos 63 a 74), com dois jogadores num servidor dedicado e numa GPU física, incluindo o custo de quadro da cratera grande e o comportamento com Sodium/Embeddium.
- Os pendentes da 0.10.0 continuam (abaixo).

---

## Versão 0.10.0 — integração com outros mods

**Compilada e coberta pelos testes de build; executada em jogo por um roteiro automatizado em renderização por software (Mesa llvmpipe), num cliente, no NeoForge 21.1.267 e no 21.1.265, com o mesmo roteiro e o mesmo resultado (53 de 53 verificações). Dois jogadores num servidor dedicado, GPU física e os casos manuais de TESTE-REAL.md (52 a 62) continuam pendentes.**

### Build

Minecraft Java 1.21.1, NeoForge 21.1.267 (e 21.1.265 com `-Pneoforge.version=21.1.265`), RimuLib 1.3.0 (baixada pela task `rimulib`, SHA-256 `15ccd4958a0412cbcf618a10962b0b6a7e1683fd66b94d07e10bc7cabeedb774`), OpenJDK 21.0.13, Gradle 9.2.1, glslangValidator 15.1.0.

- `./gradlew clean build`: passou, com `checkBudget` (`FrameBudgetCheck PASS`, com os novos `OrientationCheck`, `StageEventCheck`, `SourceCheck` e `TriggerCheck`), `checkFrustum`, `checkShaders` (11 shaders compilados), `checkApiDocs`, `checkRecipes` (agora também o modelo de dependência contra `examples/consumidor`) e `checkModVersion`. Cada etapa foi compilada e testada antes do seu commit.
- `./gradlew dist` e `./gradlew -p examples/vfx-sample build`: passaram, sem avisos.
- **NeoForge 21.1.265:** `./gradlew clean compileJava checkBudget checkFrustum -Pneoforge.version=21.1.265` e o exemplo com a mesma opção passaram; o `jar` compilado contra a 21.1.265 é byte a byte igual ao da 21.1.267.
- **Build reproduzível:** o `dist/calamidade_awakening-0.9.0.jar` refeito aqui a partir da tag tem o mesmo SHA-256 do anexo do release v0.9.0 (`4813b906…`), então o SHA-256 abaixo é o que o workflow de release vai publicar.

SHA-256 de `dist/calamidade_awakening-0.10.0.jar`: `c6229eb58a486c976429fc661b5228521ff87c96f69aaad10e0281170529bcde`

SHA-256 de `examples/vfx-sample/build/libs/vfx-sample-1.7.0.jar`: `937411881042d7cadcd5fed4a75242449b2948e1bc2ea584f7903331db1ec911`

### Consumo como dependência

- `examples/consumidor` (o modelo de `docs/ai/usar-como-dependencia.md`): com `-Pcalamidade.url=file:///…/dist/calamidade_awakening-0.10.0.jar`, a task `baixarDependencias` baixou o Calamidade e a RimuLib (do CurseForge), conferiu os dois SHA-256 e o projeto compilou; o JAR do consumidor tem só as próprias classes. Com um arquivo errado no lugar do Calamidade, a task falhou com "SHA-256 … não confere" e não deixou nada em `libs/`.
- O download do anexo do release v0.9.0 pelo endereço do GitHub (`…/releases/download/v0.9.0/calamidade_awakening-0.9.0.jar`) funcionou daqui e conferiu com o SHA-256 publicado.
- **Depois do release v0.10.0:** os anexos publicados pelo workflow têm os mesmos SHA-256 do build local (`c6229eb5…` e `93741188…`), e `./gradlew -p examples/consumidor clean build`, sem endereço alternativo, baixou `calamidade_awakening-0.10.0.jar` do release no GitHub, conferiu o SHA-256 e compilou.
- O mod de teste em jogo (abaixo) usa o mesmo esquema (`compileOnly` + `runtimeOnly` com os JARs do motor, da RimuLib e do exemplo): no `runClient` do NeoForge 21.1.267 e do 21.1.265, os três foram carregados como mods a partir do classpath.

### Escolhas desta versão

- **Referencial.** Os eixos locais do Minecraft (`^x ^y ^z`: x à esquerda, y para cima, z para a frente), com yaw e pitch na convenção do jogo. Yaw 0 e pitch 0 dão exatamente os eixos do mundo, então efeitos da 0.9 não mudam. A frente do `Basis` é igual a `Vec3.directionFromRotation(pitch, yaw)` em 13 × 24 combinações (teste).
- **Seguir o corpo.** No servidor, `LivingEntity.yBodyRot`; no cliente, `Mth.rotLerp(parcial, yBodyRotO, yBodyRot)`, como o renderizador de entidades. O `getPreciseBodyRotation` do jogo usa `Mth.lerp` simples (conferido com `javap -c`), que salta na volta de 180 para -180; por isso não foi usado.
- **Canal novo em vez de mudar o antigo.** O pacote de início passou a ser `event_v2`; um motor 0.9 não registra o canal, então versões misturadas não trocam efeitos em vez de ler bytes errados.
- **Reserva de jogadores vale para SCRIPT também.** O `/calamidade bench` (SCRIPT) chega a 48 com a reserva padrão; jogadores continuam podendo usar as 64.
- **Fim junto com o bloco.** `BlockEntity.isRemoved()` cobre quebra, troca e descarregamento: `LevelChunk.clearAllBlockEntities` chama `onChunkUnloaded` e `setRemoved` em cada block entity (conferido com `javap -c`).
- **Mob morto.** Uma instância presa a um mob dura até o mob ser removido, cerca de 20 ticks depois da morte (a animação de morte); o teste em jogo espera esse tempo.
- **Gatilhos em campos estáticos.** O efeito de um gatilho é procurado quando ele dispara, não quando é criado: os campos `static final` são inicializados quando a classe carrega, muitas vezes antes do `Effects.register` no construtor do mod.

### Testes novos

- **`OrientationCheck`**: identidade, direções conhecidas (yaw 90 é oeste; à esquerda de quem olha para o sul fica o leste; pitch 90 olha para baixo), frente igual a `Vec3.directionFromRotation`, 2000 bases aleatórias ortonormais e com a mesma orientação do mundo, ida e volta local → mundo → local, normalização do yaw, `yawTowards`/`pitchTowards`, `Point.facing`, `Placement` (cópias, `shifted`, `facing`, `arrivingFrom`, recusas) e `Effects.Context` (forma nova e formas da 0.6 e da 0.9).
- **`StageEventCheck`**: o pacote `event_v2` leva e devolve orientação, opção de seguir o corpo e tipo da fonte; leitura de valores inválidos (yaw não finito, pitch fora da faixa, seguir o corpo sem âncora, tipo de fonte desconhecido) cai em valores seguros.
- **`SourceCheck`**: igualdade das fontes (jogador e entidade com o mesmo UUID são fontes diferentes; posição de bloco copiada), regras e admissão (total, reserva, por fonte, sem reserva, reserva 64), uma simulação de 20000 inícios e fins aleatórios que nunca passa dos limites, recarga por mob e por bloco.
- **`TriggerCheck`**: modos PERIODIC, REDSTONE e PROXIMITY (bordas, repetição, release só uma vez, novas tentativas espaçadas quando o servidor recusa, recarga depois de recarregar o chunk) e validação das configurações dos três gatilhos.

### Compatibilidade

- O código do exemplo 1.6.0 compila sem mudança contra a 0.10.0 (Etapa 1).
- O JAR do vfx-sample 1.6.0 anexado ao release v0.9.0 (compilado contra a 0.9.0, SHA-256 `3ab2c335…`): as 172 referências ao motor, em 38 classes, existem no JAR 0.10.0 com a mesma assinatura (`javap -v` e `javap -p -s`). Contraprova: contra o JAR 0.8.0 o mesmo verificador acusa 44 referências que faltam (Beam, Light, prioridade).
- `Effects.Context` manteve os construtores da 0.6 e da 0.9; `AddonLayers.Post` e `World` mantiveram os seus; `Handle` ganhou só um método `default`.
- O protocolo de rede mudou (`event_v2`): servidor e clientes na mesma versão.

### Integração conferida no JAR (javap)

Minecraft 1.21.1: `LivingEntity.yBodyRot`, `yBodyRotO`, `getPreciseBodyRotation` (bytecode), `getSlotForHand`; `Mth.rotLerp`, `Mth.wrapDegrees`; `Vec3.directionFromRotation`, `atBottomCenterOf`; `Entity.getYRot`, `getXRot`, `getViewYRot`; `Item.use`, `Item.hurtEnemy`; `ItemStack.hurtAndBreak(int, LivingEntity, EquipmentSlot)` (que pula jogadores com `hasInfiniteMaterials`, conferido com `javap -c`); `ItemCooldowns.addCooldown`; `InteractionResultHolder.sidedSuccess`, `consume`, `fail`; `BlockEntity.isRemoved`, `setRemoved`; `LevelChunk.clearAllBlockEntities` (bytecode); `SignalGetter.hasNeighborSignal`; `EntityGetter.hasNearbyAlivePlayer` (filtra espectadores e mortos, conferido com `javap -c`); `BlockStateProperties.FACING` e `HORIZONTAL_FACING`; `Direction.toYRot`; `BaseEntityBlock.getRenderShape` (devolve `INVISIBLE`; o exemplo devolve `MODEL`); `BlockBehaviour.simpleCodec`; `BlockEntityType.Builder.of(...).build`; `SoundEvents.NOTE_BLOCK_CHIME` (um `Holder`).

NeoForge 21.1.267 e 21.1.265: `LivingDamageEvent.Post`, `BuildCreativeModeTabContentsEvent`, `DeferredRegister.Items`/`Blocks`, `ModConfigSpec` (`push`, `pop`, `defineInRange`), `ModConfig.Type.SERVER`, `IBlockEntityExtension.onChunkUnloaded`.

### Em jogo (roteiro automatizado, Mesa llvmpipe)

Cliente de desenvolvimento do NeoForge sob Xvfb, janela 1280 × 720, Mesa llvmpipe (LLVM 20.1.2, 256 bits), 10 a 39 FPS, qualidade BALANCED, distância 8, com os JARs desta versão (hashes acima), a RimuLib e um mod temporário de teste (fora do repositório) que cria um mundo plano (dificuldade fácil, `spawnChunkRadius 0`), roda os comandos como jogador, usa o item e ataca pelo `MultiPlayerGameMode` (o caminho real do clique), chama a API no servidor para os limites, lê o F7 e as instâncias do servidor por reflexão, mede as direções dos feixes resolvidos no cliente e tira capturas. Sem som e sem rede da Mojang. A RimuLib mostra a tela "Your graphics card can't run…" em renderizador por software; o mod de teste aperta "Ignore". O mesmo roteiro rodou no 21.1.267 e no 21.1.265: **53 de 53 verificações nas duas versões**.

| Passo | Resultado (igual nas duas versões) |
|---|---|
| `/vfxsample receita leque` com o jogador virado para o sul, o oeste e o noroeste (yaw 0, 90, -135) | O feixe central aponta para a frente do jogador com erro de 0,00°; os feixes de fora a 41,35° (40° a partir de um ponto 0,5 bloco à frente). F7: 5 feixes, 1 luz, "Fontes: 1 jogadores". |
| Seta (efeito do mod de teste: 3 feixes escritos com `Point.facing`) presa a um zumbi parado com `followingBody()`; o zumbi é virado de 180 para 90 graus | O yaw da instância no cliente é igual ao do corpo do zumbi (diferença < 1,5°) antes e depois; a seta vira 90 graus junto. Ao matar o zumbi, a instância acaba quando ele é removido. |
| Cajado em sobrevivência, olhando reto | O raio sai da mão direita (ponto a (-0,3; -0,25; 0,4) da câmera, como o offset pede) e vai reto para a frente (0,00°); durabilidade 1; a recarga aparece no item; o zumbi 8 blocos à frente perde 3 de vida pelo cue (`ctx.forward()`). |
| Segundo clique durante a recarga; terceiro depois dela, olhando para o oeste e 20° para cima | O segundo não dispara; o terceiro segue yaw e pitch do olhar (0,00°), durabilidade 2. |
| Golpe corpo a corpo num zumbi | O risco atravessa o alvo na direção do golpe (0,00°); anel de luz no chão. |
| Pilar periódico e pilar de redstone virado para o norte | O periódico pulsa sozinho; o de redstone fica quieto sem sinal, pulsa na borda de subida (1 instância), de novo 20 ticks depois (2 vivas, o limite por bloco) e para quando o sinal some. O feixe inclinado vai para o norte (0,00°). F7: "Fontes: 2 blocos". |
| Quebrar o pilar de redstone no meio de um pulso | A instância acaba no tick seguinte (o pulso duraria 30 ticks). |
| Ir a 4000 blocos e voltar | Longe: nenhuma instância de bloco no servidor. De volta: o periódico volta a pulsar. |
| Zumbi vanilla (com IA) atacando um aldeão parado | Feixe do peito do zumbi na direção da vítima (0,0° nas rodadas finais; até 3,2° numa anterior, com o zumbi se mexendo); F7: "Fontes: 1 mobs". |
| `/calamidade bench 64` | Para em 48 (SCRIPT); `/calamidade status`: "48 scripts", "recusadas 1 (reserva 1)". |
| Com o bench ativo, `/vfxsample receita leque` | O jogador começa o efeito na reserva (49 ativas); F7: "Fontes: 1 jogadores · 48 scripts". Depois que o leque aparece, ele tem 5 feixes e 1 luz em todos os quadros seguintes (12 e 11 quadros seguidos, a 8–13 FPS sob carga): não pisca. Um mob tentando o mesmo recebe `Optional.empty()`, sem erro. |
| 6 inícios no mesmo zumbi, 3 no mesmo bloco, 2 com recarga de 100 ticks no mesmo mob | 4 aceitos, 2 aceitos, 1 aceito (recarga restante: 100). `/calamidade status`: "5 mobs · 2 blocos", recusadas por reserva 2 e por fonte 3. |
| Regressão da 0.9: receita `pilar`, `protagonista` com `bench 40`, Sinal de Maré | Pilar: 1 feixe, 2 luzes. Protagonista: 1 HERO desenhado, 2/2 passes, feixes e luzes pagos pelo HERO. Sinal de Maré desenhado. |

Nenhum erro de shader, nenhuma camada desativada por falha e nenhuma exceção do motor ou do exemplo no log; as únicas exceções são do áudio (sem dispositivo OpenAL) e da rede da Mojang (`sessionserver.mojang.com`, `api.minecraftservices.com`), esperadas neste ambiente.

**O que o teste em jogo achou:** nada no motor. Na primeira rodada, 6 verificações falharam por erros do próprio roteiro: uma chamada ao servidor aninhada em outra (esperou até o tempo limite) e um bench iniciado 2 ticks depois de matar um zumbi, cujo efeito ainda vivia na animação de morte; o motor parou o bench corretamente em 47 (47 scripts + 1 mob = 48 vagas comuns), e as expectativas seguintes ficaram deslocadas. Numa rodada posterior, o zumbi-alvo do cajado perdeu 4 de vida em vez de 3: os zumbis do roteiro queimavam ao sol e um tique de fogo caiu dentro da medição. Numa terceira, o F7 lido no passo dos limites mostrava 0 feixes enquanto a captura do quadro seguinte mostrava o leque: o F7 mostra o quadro anterior e, com quadros lentos sob 48 Núcleos, o cliente roda vários ticks por quadro, então o quadro lido era de antes de o leque crescer. Para descartar um efeito piscando, o roteiro ganhou uma verificação quadro a quadro (acima), que passou. O roteiro passou a esperar a animação de morte, a não aninhar chamadas, a pôr capacete nos zumbis e a esperar o leque aparecer; as duas rodadas finais, com os JARs acima, passaram inteiras.

Capturas (21.1.267): [orientação](gameplay/orientacao-0100.jpg) · [gatilhos: cajado, pilares e zumbi](gameplay/gatilhos-0100.jpg) · [pilar de noite, limites e regressão](gameplay/limites-noite-0100.jpg).

### Pendente

- TESTE-REAL.md, parte 5 (casos 52 a 62), com dois jogadores num servidor dedicado e numa GPU física.
- O RespectCraft com o Finisher Engine usando efeitos do Calamidade (o modelo de dependência foi testado com `examples/consumidor`).
- Os pendentes da 0.9.0 continuam (abaixo).

---

## Versão 0.9.0 — Beam API, Light API e prioridade protagonista

**Compilada e coberta pelos testes de build; executada em jogo por um roteiro automatizado em renderização por software (Mesa llvmpipe), num cliente. GPU física, multiplayer e os casos manuais de TESTE-REAL.md (37 a 51) continuam pendentes.**

### Build

Minecraft Java 1.21.1, NeoForge 21.1.267, RimuLib 1.3.0 (`rimulib-1.3.0-neoforge.jar`, baixada do CurseForge pela task `rimulib`, SHA-256 `15ccd4958a0412cbcf618a10962b0b6a7e1683fd66b94d07e10bc7cabeedb774`), OpenJDK 21.0.13, Gradle 9.2.1, glslangValidator 15.1.0.

- `./gradlew clean build`: passou, com `checkBudget` (`FrameBudgetCheck PASS`, incluindo `TimelineCheck`, `AtmosphereCheck` e os novos `PriorityCheck`, `BeamCheck` e `LightCheck`), `checkFrustum`, `checkShaders` (11 shaders compilados), `checkApiDocs`, `checkRecipes` e `checkModVersion`. Cada etapa da 0.9.0 foi compilada e testada antes do seu commit.
- `./gradlew dist`: passou e gerou `dist/calamidade_awakening-0.9.0.jar`.
- `./gradlew -p examples/vfx-sample build`: passou, sem avisos, e gerou `vfx-sample-1.6.0.jar`.

SHA-256 de `dist/calamidade_awakening-0.9.0.jar`: `4813b906fd74e32b25af2168aa7446dc24252ac928bd140196c0ea801e61e256`

SHA-256 de `examples/vfx-sample/build/libs/vfx-sample-1.6.0.jar`: `3ab2c335d5c9158cfd854ad923a1732af188836bb119dd59bd3fa3fece02aa61`

Um `clean build` repetido depois do teste em jogo gerou os dois JARs byte a byte iguais aos testados.

### Escolhas desta versão

- **Dados dos passes por textura, não por uniforms.** 128 feixes × 7 vec4 passariam do mínimo de uniforms de fragmento que o OpenGL 3.2 garante (1024 componentes). Os feixes (7 texels cada) e as luzes (4 texels) vão numa textura RGBA32F por passe, lida com `texelFetch`. `Uniforms.glTexture(nome, id)` (conferido com `javap -c` em `PostFxRenderer$Binder`) liga a textura numa unidade própria, com `glBindSampler(unidade, 0)`, até 16 unidades; o upload (`DataTexture`) restaura a textura ligada, o buffer de pixel-unpack e os parâmetros de unpack que mexe, para o cache de estado do Minecraft continuar válido.
- **Ordem dos passes:** `lights`, `awakening_field`, cartões e `post.pass`, `beams`, `atmosphere`. As luzes vêm antes para os efeitos ficarem por cima delas e a atmosfera as cobrir na distância.
- **Pagamento:** feixes e luzes são pagos antes dos passes de addon, feixes primeiro; HERO paga quando um HERO desenhado os usa, senão o comum; um orçamento nunca paga pelo outro.
- **Seleção HERO:** os HERO ocupam vagas do limite de desenhados (o total continua 3/6/12) e são no máximo 2; como o menor limite é 3, um HERO nunca é cortado.
- **Névoa no passe de luzes:** `ViewportEvent.RenderFog` (`getNearPlaneDistance`, `getFarPlaneDistance`, `getFogShape`, conferidos com javap) registrado depois do ajuste da atmosfera, só no modo `FOG_TERRAIN`; o shader usa a mesma curva do `fog.glsl` do jogo (smoothstep, forma esférica ou cilíndrica).
- **Mira (`*_AIM`):** `Level.clip(new ClipContext(olho, olho + olhar × alcance, Block.OUTLINE, Fluid.NONE, entidade))` (conferido com javap), com cache por entidade e alcance no quadro.
- **Albedo estimado da luz:** `cor^0,4 × 0,8`. A prévia mostrou que `sqrt(cor) × 0,7` e um cosseno sem envolvimento deixavam uma fogueira quase invisível de noite.

### Testes novos

- **`PriorityCheck`** (Java puro): HERO nunca cortado (em LOW, BALANCED e HIGH, contra 40 NORMAL mais fortes e mais próximos), teto de 2 HERO (os mais próximos; empate pela ordem; 60 HERO desenham 2), filtros de alcance e intensidade, seleção NORMAL idêntica à da 0.8 em 500 conjuntos aleatórios sem HERO, orçamentos separados (gastar o HERO não toca o comum), `charge` limitado, `Definition` com prioridade (construtor da 0.8, `withRange` mantém a prioridade).
- **`BeamCheck`**: `Point` (fábricas, validação, igualdade), builder (padrões, faixas, nomes, trilha e fase duplas, pontas iguais), `validate`, amostragem (peso = envelope × fase × trilha; revelação limitada a 1 com OVERSHOOT; contagem de pulsos arredondada; deslocamento de ponta limitado a 256; NaN), registro (efeito desconhecido ou interno, 0 e 17 feixes, duplicado, antes da timeline, cues depois, lista imutável) e os passes compartilhados (teto por prioridade; HERO paga primeiro; nenhum orçamento paga pelo outro).
- **`LightCheck`**: formas e padrões, faixas, `along` (pontas e cor do feixe; cor explícita vence), validação, amostragem com trilhas e fase, cintilação (sempre entre `1 − quantidade` e 1, suave, determinística, diferente por seed, escalada pela intensidade e por "ocultar clarões"), registro (8 e 9 luzes, duplicado, feixe não registrado ou igual mas diferente, luz animada antes da timeline) e tetos 8/24/64.
- **GLSL:** `node scripts/testar_glsl.cjs` executou 106 casos (51 novos) de `calamidade_fx.glsl` versão 2 em WebGL2 (ANGLE/SwiftShader): 106/106. O `checkShaders` compila os 11 shaders do motor e do exemplo em GLSL 1.50 com o `common.glsl` real.
- **Prévia dos passes:** `node scripts/previa_passes.cjs` rodou `beams.fsh` e `lights.fsh` reais (com o `common.glsl` da RimuLib) sobre uma cena sintética. Achou três defeitos antes do jogo: anéis falsos na ondulação de feixes vistos de frente (o eixo lateral e a posição ao longo do feixe ficam mal definidos; a ondulação agora diminui entre ~30 e ~12 graus), halo forte demais (agora 55% da cor no eixo) e luz noturna fraca (albedo e cosseno ajustados).
- **Receitas, fora do jogo:** o registro real das 13 receitas e da luz do Sinal de Maré rodou com o classpath do jogo; todos os feixes, luzes e atmosferas foram amostrados em cada tick; o raio do anel de `onda_luz` é igual a 0,45 × (idade − 12), a frente da onda do shader.

### Compatibilidade

- O exemplo 1.5.0 da 0.8.0, sem mudança no código, compila contra o JAR 0.9.0.
- O mesmo exemplo compilado contra o JAR 0.8.0 (reconstruído da tag `v0.8.0`, com o mesmo SHA-256 do release, `786ac7f9…`): as 128 referências a classes, métodos e campos do motor, em 30 classes, existem no JAR 0.9.0 com a mesma assinatura (javap -v e javap -s). Contraprova: o exemplo 1.6.0 contra o JAR 0.8.0 acusa as 11 referências novas (Beam, Light, Point, `withPriority`, `Effects.beams` e `Effects.lights`).
- `Effects.Definition` ganhou o componente `priority`; o construtor canônico da 0.8 (9 parâmetros) continua existindo e dá `NORMAL`.
- Protocolo de rede igual ao da 0.7.0 e da 0.8.0 (event, end_effect, request_v2, release).
- Um addon cujo manifesto declara `[0.8.0,0.9.0)` não carrega com a 0.9.0 até ampliar o intervalo.

### Integração conferida no JAR (javap)

RimuLib 1.3.0: `Uniforms.glTexture(String, int)` e o `Binder` de `PostFxRenderer` (unidade própria, `glBindSampler(unidade, 0)`, até 16 unidades), `FxApi$Stage.render` (camadas desenhadas e passes executados no mesmo quadro).

NeoForge 21.1.267: `ViewportEvent.RenderFog.getFogShape()`, `com.mojang.blaze3d.shaders.FogShape` (`SPHERE`, `CYLINDER`).

Minecraft 1.21.1: `ClipContext(Vec3, Vec3, ClipContext.Block, ClipContext.Fluid, Entity)`, `BlockGetter.clip`, `HitResult.getType`/`getLocation`, `HitResult.Type.MISS`, `Entity.getEyePosition(float)`, `getPosition(float)`, `getViewVector(float)`, `getId()`, `EntityGetter.getPlayerByUUID`, e os `SoundEvents` das receitas (todos `SoundEvent`; `TRIDENT_THUNDER` e `GENERIC_EXPLODE` são `Holder` e não foram usados).

LWJGL 3.3.3: `GL30.GL_RGBA32F`, `GL21.GL_PIXEL_UNPACK_BUFFER(_BINDING)`, `GL11.glTexSubImage2D(…, FloatBuffer)`, `GL12.GL_TEXTURE_MAX_LEVEL`.

### Em jogo (roteiro automatizado, Mesa llvmpipe)

Cliente de desenvolvimento do NeoForge 21.1.267 sob Xvfb 1280 × 720, Mesa llvmpipe (LLVM 20.1.2, 256 bits), 5 a 39 FPS, qualidade BALANCED, gráficos Fancy, distância 8, com os JARs desta versão, a RimuLib e um mod temporário de teste (fora do repositório) que cria um mundo plano em criativo, roda os comandos como jogador, lê os contadores do F7 por reflexão (um tick depois da captura) e tira capturas com o F7 aberto. Sem som (sem dispositivo OpenAL) e sem rede da Mojang. A RimuLib mostra, em renderizador por software, uma tela "Your graphics card can't run…" com "Exit game" e "Ignore"; o mod de teste aperta "Ignore".

| Passo | F7 (desenhados · passes · HERO · feixes · luzes) | Visto na captura |
|---|---|---|
| Sem efeito, de dia | 0 · 0 · 0 · 0 · 0 | Cena normal. |
| `pilar` de dia | 1 · 2 · 0 · 1 comum · 2 comum | Feixe branco sobre o céu, pulsos; disco de luz amarelo no chão em volta da base. |
| `pilar` de noite | 1 · 2 · 0 · 1 comum · 2 comum | Halo âmbar, chão aceso em volta da base, a face do bloco de tijolos voltada para o feixe alaranjada. |
| `pilar` atrás de um muro de pedra | 1 · 2 · 0 · 1 comum · 2 comum | O muro esconde o feixe; só o trecho acima dele aparece, recortado pela borda. |
| `teia` | 1 · 1 · 0 · 8 comum · 0 | Oito fios ciano saindo do centro, pulsos, ondulação presa nas pontas. |
| `laser` mirando um bloco e o chão | 1 · 2 · 0 · 1 comum · 2 comum | Brilho cintilante no ponto de impacto e luz no chão em volta dele. |
| `fogueira` de noite | 1 · 1 · 0 · 0 · 1 comum | Poça de luz quente no chão (~10 blocos) e a face do pilar de pedregulho mais próximo alaranjada; nenhum feixe, vértice ou passe de addon. |
| `onda_luz` | 1 · 2 · 0 · 0 · 1 comum | Faixa de luz dourada no chão junto da onda; o clarão do centro já tinha passado (`Luzes: 1`). |
| `pilar` com intensidade dos efeitos de tela 0 | 1 · 2 · 0 · 1 comum · 2 comum | Só o núcleo branco, sem halo nem pulsos; a luz no chão continua. |
| `fogueira` e `pilar` debaixo d'água, de noite | 1 · 1 / 2 · 0 · 0 / 1 · 1 / 2 | A luz some na névoa da água; o feixe, atrás da parede do lago, não aparece. |
| `/calamidade bench 60` + `protagonista`, BALANCED, de dia | 6 · 3 · **1 HERO, 2/2** · 7 HERO · 3 HERO | 61 residentes; céu vermelho da atmosfera, lança, raios, anel e disco de luz entre os 5 Núcleos desenhados. |
| o mesmo em HIGH | 12 · 3 · **1, 2/3** · 7 HERO · 3 HERO | 12 desenhados, 4 volumes. |
| o mesmo em LOW | 3 · 2 · **1, 1/1** · 7 HERO · 0 | Feixes pagos pelo único passe HERO; luzes sem vaga, como documentado. |
| o mesmo de noite, BALANCED | 6 · 3 · 1, 2/2 · 7 HERO · 3 HERO | Lança e luzes sobre a cena escura. |
| Sinal de Maré no impacto, de noite | 1 · 3 · 0 · 0 · 1 comum | Luz verde-água no chão sob o sinal; atmosfera dourada ativa. |

Nenhum erro de compilação ou ligação dos shaders do motor e do exemplo no log, nenhuma camada desativada por falha. Na primeira rodada, os únicos avisos do compilador de shader foram 8 "used uninitialized" em variáveis internas da RimuLib (`gSeed`, `gTime`, `gPixel`); na segunda, nenhum.

**O que o teste em jogo achou e foi corrigido:** na primeira rodada, o laser visto por quem atira virava um cone largo saindo de baixo da mira (a 1–3 blocos dos olhos, o halo de 0,9 bloco ocupa dezenas de graus da tela). O passe agora limita a largura angular de feixes com uma ponta a menos de 2 blocos da câmera (~0,25 grau de núcleo, ~0,7 grau de halo); quem vê de fora continua vendo a largura real. A prévia (`node scripts/previa_passes.cjs laser`) e a segunda rodada, com os JARs finais (hashes abaixo), mostram o laser como um traço fino até o alvo; os demais resultados se repetiram.

Capturas da segunda rodada: [receitas novas](gameplay/receitas-090.jpg) · [protagonista sob carga](gameplay/protagonista-carga-090.jpg) · [fogueira de noite](gameplay/luz-noite-090.jpg) · [intensidade 0, água, protagonista de noite e Sinal de Maré](gameplay/acessibilidade-agua-090.jpg).

### Pendente

- Testes manuais de TESTE-REAL.md, casos 37 a 51, em GPU física: aparência dos feixes e da luz de dia, de noite e debaixo d'água, e custo dos passes sob carga (casos 45 e 50).
- Teste com dois jogadores num servidor dedicado (0.8.0 e 0.9.0).

---

## Versão 0.8.0 — Atmosphere API e kit para IAs

**Compilada e coberta pelos testes de build; executada em jogo por um roteiro automatizado em renderização por software (Mesa llvmpipe), num cliente. GPU física, multiplayer e os casos manuais de TESTE-REAL.md (13 a 36) continuam pendentes.**

### Build

Minecraft Java 1.21.1, NeoForge 21.1.267, RimuLib 1.3.0 (`rimulib-1.3.0-neoforge.jar`, baixada do CurseForge pela task `rimulib`, SHA-256 `15ccd4958a0412cbcf618a10962b0b6a7e1683fd66b94d07e10bc7cabeedb774`), OpenJDK 21.0.13, Gradle 9.2.1, glslangValidator 15.1.0.

- `./gradlew clean build`: passou, com `checkBudget` (`FrameBudgetCheck PASS`, incluindo `TimelineCheck` e o novo `AtmosphereCheck`), `checkFrustum`, `checkShaders` (9 shaders compilados), `checkApiDocs`, `checkRecipes` e `checkModVersion`. Cada etapa da 0.8.0 foi compilada e testada antes do seu commit.
- `./gradlew dist`: passou e gerou `dist/calamidade_awakening-0.8.0.jar`.
- `./gradlew -p examples/vfx-sample build`: passou, sem avisos, e gerou `vfx-sample-1.5.0.jar`.

SHA-256 de `dist/calamidade_awakening-0.8.0.jar`: `786ac7f9ebc1643be96c6028835421ef5da1c70f4f4c8fc29b1f7a21085bdc84`

SHA-256 de `examples/vfx-sample/build/libs/vfx-sample-1.5.0.jar`: `0a70b44990df526909c7e014bab28948ec2136830b8a08ff40ef6ca549cbadb2`

### Escolhas desta versão

- **`#include` de GLSL: sim.** `ShaderProgram.readSource` (javap -c) antepõe `common.glsl` e `#line 1 1`; `includes` troca cada linha que, aparada, começa com `#include "` e termina com `"` pelo arquivo `shaders/fx/<nome>` do namespace do shader e, se faltar, do namespace `rimulib`, seguido de `#line <n+2> 1`. Não há recursão. `Assets.open` monta o caminho `/assets/<namespace>/...` e o procura no módulo do mod daquele namespace, na RimuLib e nos demais mods, sempre com o mesmo caminho: um addon **não** alcança o namespace do motor, então copia a biblioteca para o seu. A biblioteca é `calamidade_fx.glsl`; não foi preciso o plano B (`docs/ai/glsl-snippets.md`).
- **Eventos de névoa do NeoForge: sim, os dois.** `ViewportEvent.ComputeFogColor` (get/setRed, Green, Blue) e `ViewportEvent.RenderFog` (`getMode`, `getType`, distâncias; `ICancellableEvent`). No código do `ClientHooks.onFogRender`, as distâncias só valem se o evento for cancelado; `FogRenderer.setupFog` dispara o evento depois de calcular cegueira, escuridão, fluidos e névoa grossa, então o `min` do motor nunca deixa a névoa mais rala. Prioridade `LOWEST`. A opção `vanillaFog` (padrão ligado) desliga as distâncias pelo jogo e faz o passe desenhar a névoa pela profundidade.
- **Exposição com "ocultar clarões":** no máximo 0,025 por tick (`AtmosphereMath.CALM_EXPOSURE_PER_TICK`).
- **Registro automático do esqueleto gerado:** o bytecode do FML 4.0.45 (`ModLoader.lambda$constructMods$3`) chama `constructMod` (que injeta os `@EventBusSubscriber` em `AutomaticEventSubscriber.inject`) e só depois envia `FMLConstructModEvent` ao barramento do mod. Por isso `scripts/novo_efeito.py` registra o efeito num assinante estático desse evento, sem editar a classe `@Mod`. `Rimulib.register` devolve o registro existente para um namespace já conectado, então `Addons.resources` pode ser repetido.

### Testes novos

- **`AtmosphereCheck`** (Java puro, no `checkBudget`): validação do builder (faixas, NaN, nomes, ligação dupla, fase dupla), `validate` contra a timeline, constantes e cores (alfa ignorado), trilhas (clamp na faixa, peso por fase e trilha `WEIGHT`, distâncias de névoa vindas de trilhas sempre com fim depois do início), peso zero e limiar de atividade, mistura (estado único, dois cheios, troca com força total, cores ponderadas, só as duas mais pesadas, empate pela ordem, simetria, distâncias pelo uso da névoa), `fogNear`, `fogFar`, `fogColorOver` e `screenFog`, extremos da mistura e os auxiliares do motor (fade por alcance, rampa de exposição: 1 → 2,5 leva 60 ticks, exposição pela intensidade).
- **Registro:** atmosfera para efeito desconhecido, interno ou duplicado; antes da timeline; trilha inexistente (rejeitada e não guardada); cues depois da atmosfera; amostragem da atmosfera registrada.
- **GLSL:** `node scripts/testar_glsl.cjs` executou 55 casos de `calamidade_fx.glsl` em WebGL2 (GLSL ES 3.00, ANGLE/SwiftShader no Chromium headless): 55/55. O `checkShaders` compila os 9 shaders do motor e do exemplo em GLSL 1.50 com o `common.glsl` real.
- **Receitas e exemplo, fora do jogo:** o registro real das 7 receitas, a validação das atmosferas do Sinal e do Farol contra as timelines e a mistura céu tingido + Sinal rodaram com o classpath do jogo e passaram.
- **Gerador:** `scripts/novo_efeito.py` gerou dois efeitos no vfx-sample (com e sem cartão); o exemplo compilou com eles, o `checkShaders` compilou os dois shaders novos e a recusa a sobrescrever funcionou. Os arquivos gerados foram removidos.

### Em jogo (roteiro automatizado, Mesa llvmpipe)

Cliente de desenvolvimento do NeoForge 21.1.267 sob Xvfb 1280 × 720, Mesa llvmpipe (LLVM 20.1.2, 256 bits), 2 a 10 FPS, com os JARs desta versão, a RimuLib e um mod temporário de teste (fora do repositório) que cria um mundo, roda os comandos como jogador, lê os contadores do F7 por reflexão e tira capturas. Não há som (sem dispositivo OpenAL) nem rede da Mojang.

| Passo | F7 (atmosferas · passe · passes de pós · vértices) | Visto na captura |
|---|---|---|
| Sem efeito | 0 · não · 0 · 0 | Céu e cena normais. |
| `/vfxsample receita ceu` (11 s) | 1 · sim · 0 · 0 | Céu roxo no alto e laranja no horizonte, nuvens preservadas, cena mais escura e quente, névoa laranja nas árvores distantes. |
| + `/vfxsample at` no impacto | 2 · sim · 3 · 1746 | Céu puxado para o dourado e mais claro; anéis dourados e lâminas do Sinal. |
| Depois de `receita stop` | 0 · não · 0 · 0 | Céu normal de novo. |
| `/vfxsample farol start` (6 s) | 1 · sim · 0 · 180 | Névoa âmbar no horizonte; o feixe continua nítido. |
| `farol release` (+4 s) | 0 · não · 0 · 0 | Apagado. |
| Receitas `anel`, `cubo`, `bolha`, `impacto`, `feixe` | 0 · não · 1 · 0 | Anel dourado no chão; cubo de grade com arestas e oclusão pelo terreno; bolha iridescente; quadro em duas cores; feixe com núcleo e halo. |
| Receita `ambiente` (+ `release`) | 0 · não · 0 · 576 | Vaga-lumes; depois do release, nada residente. |
| `ceu` com intensidade dos efeitos de tela 0 | 1 · sim · 0 · 0 | Céu e névoa mantidos, sem escurecimento nem tinta. |
| `ceu` com `vanillaFog` desligado | 1 · sim · 0 · 0 | Névoa desenhada pelo passe, pela profundidade. |

Nenhum erro de compilação ou ligação de shader no log, nenhuma camada desativada por falha. O roteiro rodou duas vezes; a segunda, com os JARs finais (hashes acima), repetiu os resultados. A 2 a 9 FPS, a leitura do F7, feita um tick depois da captura, às vezes cai fora de janelas curtas (na segunda rodada, 1 atmosfera e 2 passes no impacto do Sinal, e 0 passes no quadro de duas cores), sem diferença no que as capturas mostram. Na resolução cheia, as estrelas aparecem como pontos pequenos e cintilantes.

Capturas da segunda rodada: [céu tingido](gameplay/atmosfera-ceu-080.jpg) · [céu tingido + Sinal de Maré no impacto](gameplay/atmosfera-mistura-080.jpg) · [receitas anel, cubo, bolha, impacto, feixe e ambiente](gameplay/receitas-080.jpg).

### Em jogo, GPU física (teste do autor, registrado em 21/11/2026)

NVIDIA GeForce RTX 4060, qualidade BALANCED, com outros programas abertos no computador. Motor 0.8.0 e vfx-sample 1.5.0.

| Carga | Resultado |
|---|---|
| 60 × `vfxsample:receita_quadro` | F7: 60 residentes, 6 desenhados (o teto do BALANCED), 2 passes de pós (o orçamento de addons do BALANCED). FPS praticamente inalterado. |
| 6 × `vfxsample:receita_ceu` (`/calamidade bench 6 vfxsample:receita_ceu`) | Tempo de quadro: mediana 8,37 ms, p95 11,29 ms, em 933 quadros. |
| Referência: 6 × `nexus` na 0.6.2 | Mediana 8,40 ms, p95 11,56 ms. |

Leitura: o orçamento corta como projetado (60 residentes viram 6 desenhados e 2 passes), e seis atmosferas simultâneas (que somam um único passe de motor) custam o mesmo que seis Nexus da 0.6.2, dentro da variação de uma máquina com outros programas abertos.

- Teste visual detalhado (céu tingido, névoa, estrelas, Sinal de Maré e Farol com atmosfera, receitas): feito pelo autor, **sem problemas** encontrados. Nada foi corrigido antes da 0.9.0 por causa dele.
- Teste com dois jogadores num servidor dedicado: pendente.

**O que quebrou e foi corrigido por causa do teste em jogo:**

- O `neoforge.mods.toml` do motor ainda declarava a versão `0.7.0` (desde a 0.7.0 o arquivo não acompanhava o `build.gradle`). O exemplo 1.5.0, que exige `[0.8.0,0.9.0)`, era recusado no carregamento. Corrigido, com a task `checkModVersion` no build.
- O campo de estrelas era esparso e pequeno demais para aparecer (0,27% do hemisfério aceso no teste por GPU): o centro sorteado em células 3D muitas vezes ficava longe da superfície da esfera. `caStars` passou a usar um mapa octaédrico 2D do hemisfério de cima, com o centro sempre dentro da célula (0,68% do hemisfério, brilho médio 10 vezes maior).

### Compatibilidade

- O exemplo 1.4.0 da 0.7.0, sem mudança no código, compila contra o JAR 0.8.0 (o `build.gradle` dele só precisa apontar para o JAR novo).
- O mesmo exemplo compilado contra o JAR 0.7.0: as 107 referências a classes, métodos e campos do motor no bytecode existem no JAR 0.8.0 com a mesma assinatura (javap -v e javap -s).
- Protocolo de rede igual ao da 0.7.0 (event, end_effect, request_v2, release); a atmosfera não usa rede.
- Um addon cujo manifesto declara `[0.7.0,0.8.0)` não carrega com a 0.8.0 até ampliar o intervalo.

### Integração conferida no JAR (javap)

RimuLib 1.3.0: `ShaderProgram.readSource`, `includes` e `read`; `Assets.open`, `exists` e `split`; `Rimulib.register` (namespace repetido devolve o existente); `FxApi$Stage.render` envia `GTime = (tempo do mundo mod 24000 + fração) / 20`; `Uniforms.f3`.

NeoForge 21.1.267 e FML 4.0.45: `ViewportEvent.ComputeFogColor`, `ViewportEvent.RenderFog`, `FogRenderer$FogMode`, `FogType`, `EventPriority.LOWEST`, `SubscribeEvent.priority`, `FMLConstructModEvent` e a ordem de construção acima.

Minecraft 1.21.1: `ClientLevel.getSkyColor(Vec3, float)`, `GameRenderer.getRenderDistance()`, `Camera.getFluidInCamera()`, `MobEffects.BLINDNESS` e `DARKNESS` (`Holder<MobEffect>`), `LivingEntity.hasEffect(Holder)`, e os campos de `SoundEvents` usados pelas receitas (todos `SoundEvent`, não `Holder`).

### Pendente

- Testes manuais de TESTE-REAL.md, casos 13 a 36, em GPU física e com dois clientes num servidor dedicado; resposta do autor em 21/11/2026: a 0.7.0 ainda não foi testada em jogo.
- O JAR da RimuLib saiu do índice, mas continua no histórico do git (commit `2ec9d14`). Apagá-lo do histórico exige reescrever a `main` com push forçado; não foi feito sem autorização.

---

## Dependência RimuLib: fora do repositório e dos releases (21/11/2026)

Até a 0.7.0, `libs/rimulib-1.3.0-neoforge.jar` estava versionado (commit `2ec9d14`) e o workflow o anexava ao release v0.7.0. Corrigido antes da 0.8.0:

- `libs/*.jar` no `.gitignore`, sem exceção; o JAR saiu do índice (`git rm --cached`). Ele continua no histórico antigo do git: apagá-lo de lá exige reescrever a `main` com push forçado, o que não foi feito.
- O build baixa o arquivo oficial do CurseForge (`https://mediafilez.forgecdn.net/files/9676/532/rimulib-1.3.0-neoforge.jar`) pela task `rimulib` quando ele falta e falha se o SHA-256 não for `15ccd4958a0412cbcf618a10962b0b6a7e1683fd66b94d07e10bc7cabeedb774`. URL e hash ficam em `gradle.properties`.
- O workflow de release baixa o mesmo arquivo, confere o hash com `sha256sum -c` e recusa anexar qualquer JAR que não seja `calamidade_awakening-*.jar` ou `vfx-sample-*.jar`.
- O workflow `Limpar release` removeu a RimuLib do release v0.7.0, recalculou o `SHA256SUMS.txt` e trocou as notas, que agora mandam baixá-la do CurseForge. Os JARs do Calamidade e do exemplo da v0.7.0 não mudaram.

---

## Versão 0.7.0 — Timeline API

**Compilada e coberta pelos testes de build; ainda não executada em jogo.** Segundo o autor, os testes em jogo da 0.6.2 foram feitos; os resultados não estão registrados neste arquivo.

### Build

Minecraft Java 1.21.1, NeoForge 21.1.267, RimuLib 1.3.0 (`rimulib-1.3.0-neoforge.jar`, arquivo oficial do CurseForge, SHA-256 `15ccd4958a0412cbcf618a10962b0b6a7e1683fd66b94d07e10bc7cabeedb774`, igual ao da 0.6.2), OpenJDK 21.0.13 e Gradle 9.2.1.

- `./gradlew clean build`: passou, com `checkBudget` (`FrameBudgetCheck PASS`, agora incluindo `TimelineCheck`) e `checkFrustum`. Cada etapa da 0.7.0 foi compilada e testada antes do seu commit.
- `./gradlew dist`: passou e gerou `dist/calamidade_awakening-0.7.0.jar`. Um `clean build` repetido gerou o mesmo JAR, byte a byte.
- `./gradlew -p examples/vfx-sample build`: passou, sem avisos, e gerou `vfx-sample-1.4.0.jar`.
- Aviso restante: `StageNetwork` chama `LevelReader.hasChunkAt(BlockPos)`, marcado como obsoleto pela Mojang. No JAR do Minecraft, `hasChunkAt(int, int)` e `hasChunk(int, int)` também são obsoletos, e a alternativa sem aviso, `Level.isLoaded(BlockPos)`, recusa posições fora da altura do mundo, o que mudaria efeitos no céu. A chamada ficou como está.

SHA-256 de `dist/calamidade_awakening-0.7.0.jar`: `277a36b9515787da0ab6812f0bf2660ef80b2729d597a6a5ee53d44549aee259`

SHA-256 de `examples/vfx-sample/build/libs/vfx-sample-1.4.0.jar`: `e20856f59493178c93fcf59fb5dc6c93a8755b8e1fee0a5d05ed77683661558e`

### Testes novos no checkBudget

- `TimelineCheck` (Java puro): pesos das fases (subida, platô, queda, degraus, monotonia), cada easing nas pontas, no meio e com clamp, pico do `OVERSHOOT`, extremos e continuidade das trilhas, marcadores, loop do sustain com ticks parciais, transição para o release (inclusive antes do loop e com `release(início)` explícito), idade de expiração, continuidade da idade local fora dos saltos e de uma trilha com bordas iguais, e todas as validações do builder.
- `CueClock`: cue dentro do loop disparado a cada volta, cue depois do release disparado uma vez, ticks pulados pelo salto do release, fim da varredura quando a saída termina e interrupção pelo visitante (como num `stop()` dentro de um cue).
- Registro: ordem `register` → `timeline` → cues, timeline mais longa que a definição, efeito interno ou desconhecido, timeline duplicada, cue em marcador inexistente, relógio vazio para efeitos sem timeline e construtor de `Context` da 0.6.

### Compatibilidade com addons da 0.6

- O exemplo 1.3.1 da 0.6.2, sem nenhuma mudança no código, compila contra o JAR 0.7.0 (só o aviso antigo de `EventBusSubscriber.Bus`).
- O mesmo exemplo compilado contra o JAR 0.6.2: as 45 referências a classes, métodos e campos do motor no bytecode existem no JAR 0.7.0 com a mesma assinatura (conferido com `javap -v` e `javap -s`).
- Os registros que ganharam componentes (`Effects.Context`, `AddonLayers.Time` e `AddonLayers.Film`) mantêm os construtores antigos; `Post` mantém o construtor antigo; `Handle.release()` é um método default.
- Um addon cujo manifesto declara `[0.6.x,0.7.0)` não carrega com a 0.7.0 até ampliar o intervalo.

### Integração conferida no JAR (javap)

RimuLib 1.3.0:

- `client.render.Uniforms.f1(String, float)` é o envio de float usado por `Post.uploadTracks`.
- `client.render.ScreenFx`: campos públicos `float` `aberration`, `zoomBlur`, `afterimage` e `grain`; `addVignette(int, float)` só substitui a vinheta quando a nova intensidade é maior, então já combina pelo máximo.
- **Arrays de uniforms.** A implementação de `Uniforms` é `client.render.PostFxRenderer$Binder`. `f1` a `f4` passam o nome sem alteração a `ShaderProgram.location(String)`, que guarda o resultado num `HashMap` (`computeIfAbsent`) e chama `GL20.glGetUniformLocation(id, nome)` com a mesma string; localização `-1` pula o `glUniform*`. `vec4s` procura `nome + "[0]"` e depois `nome`, e envia o array inteiro com `glUniform4fv`. **Conclusão:** `"Pontos[3]"` chega ao `glGetUniformLocation` exatamente como escrito. Detalhes em [AVANCADO-0.6.md](AVANCADO-0.6.md).

NeoForge 21.1.267 (FML loader 4.0.45):

- `AutomaticEventSubscriber.inject` lê só os atributos `value` e `modid` de `@EventBusSubscriber`; não lê `bus`. Cada método `@SubscribeEvent` vai para o barramento do mod quando o tipo do evento implementa `IModBusEvent` e para o barramento do jogo nos outros casos.
- `FMLClientSetupEvent` (via `ModLifecycleEvent`), `RegisterKeyMappingsEvent` e `RegisterShadersEvent` implementam `IModBusEvent`. Remover `bus = Bus.MOD` de `ClientHooks.Setup` e do `SampleClient` não muda nada.

### Pendente

**Resposta do autor em 21/11/2026, ao iniciar a 0.8.0:** a 0.7.0 ainda não foi testada em jogo (Sinal de Maré com fases, Farol com sustain/release/stop e motionEffects). Nenhuma falha relatada; a 0.8.0 começou sobre a 0.7.0 sem correções.

Nada da 0.7.0 foi executado em jogo. Siga [TESTE-REAL.md](TESTE-REAL.md), casos 13 a 20, além dos anteriores. Pontos a observar:

- as fases e a câmera do Sinal de Maré, o dano no marcador `impacto` e o zoom blur e a aberração no impacto;
- o farol em loop, o som a cada volta, o release sincronizado nos dois clientes e a entrada tardia durante o loop e durante a saída;
- a opção Efeitos de movimento desligada;
- nomes de uniform indexados e `vec4s` num driver real.

Protocolo de rede: canais event, end_effect, request_v2 e o novo **release**. Servidor e clientes precisam estar na 0.7.0.

---

## Versões 0.5.0 a 0.6.2

**Compiladas; ainda não executadas em jogo.** As mudanças da 0.5.0 (cues, recarga por habilidade, diagnóstico, Atlas), da 0.6.0 (ganchos post, film e start), da 0.6.1 (orçamento por passe, contagem real no F7, cues após cancelamento) e da 0.6.2 (post sempre chamado, alcance por definição, duração até 12000 ticks, configuração de acessibilidade) foram escritas sem compilar e compiladas pela primeira vez na 0.6.2.

### Build

Minecraft Java 1.21.1, NeoForge 21.1.267, RimuLib 1.3.0 (`rimulib-1.3.0-neoforge.jar`, SHA-256 `15ccd4958a0412cbcf618a10962b0b6a7e1683fd66b94d07e10bc7cabeedb774`), OpenJDK 21 e Gradle 9.2.1.

- `./gradlew build`: o primeiro build falhou com um único erro de compilação. `AddonLayers.Post.pass` usava `FxApi.Uniforms`, que não existe; o tipo real é `cyou.rimuru.rimulib.mc1211.client.render.Uniforms`. Depois da correção, o build passou, com `checkBudget` (`FrameBudgetCheck PASS`) e `checkFrustum`.
- `./gradlew dist`: passou e gerou `dist/calamidade_awakening-0.6.2.jar`.
- `./gradlew -p examples/vfx-sample build`: passou e gerou `vfx-sample-1.3.1.jar`.
- Avisos restantes, sem efeito no build: `EventBusSubscriber.Bus` está marcado para remoção, em `ClientHooks` e no `SampleClient`; e `StageNetwork` usa uma API obsoleta.

SHA-256 de `dist/calamidade_awakening-0.6.2.jar`: `fd44ae6de877eddc86f11a372163474f58ffb8eccc1905f352e36922082065c5`

SHA-256 de `examples/vfx-sample/build/libs/vfx-sample-1.3.1.jar`: `08f15d46e06619cedc5c47747e6c5c55da5f3e3bdec2ada1722e06451e2f5871`

### Integração conferida no JAR (javap)

RimuLib 1.3.0, pacote `cyou.rimuru.rimulib.mc1211`:

- `client.api.FxApi.Frame`: `pass(String, Consumer<client.render.Uniforms>)`, `screen()` retorna `ScreenFx` e `partial()` retorna `float`. Não existe `FxApi.Uniforms`. `Uniforms` é uma interface com `f1`, `f2`, `f3`, `f4`, `vec4s`, `texture`, `glTexture`, `tape`, `kept` e `v3`.
- `client.render.ScreenFx`: os campos públicos `float` `bloom`, `shake`, `desaturate` e `contrast` existem, assim como o método `addFlash(int, float)`.
- `client.cinematic.Shot` é um record `(int start, int end, int blendIn, Shot.Path path)`. `Shot.Path.pose(Subject, float, float)` retorna `CameraPose`. `FilmApi.cut(int, int, Shot.Path)` e `FilmApi.sweep(int, int, int, Shot.Path)` aceitam as lambdas `(s, partial, k) -> FilmApi.pose(...)` usadas pelo motor e pelo exemplo.
- `client.cinematic.Subject`: interface com `feet`, `target`, `forward`, `eye`, `anchor`, `right`, `frame`, `aroundTarget`, `clears` e `clear`.
- Outros campos públicos do `ScreenFx`, ainda sem método em `Post`:
  - aberração: `aberration`;
  - zoom blur: `zoomBlur`;
  - vinheta: `vignette`, `vignetteR`, `vignetteG`, `vignetteB`, além do método `addVignette(int, float)`;
  - afterimage: `afterimage`;
  - grão: `grain`.
  
  O `ScreenFx` também tem `flash`, `flashR`, `flashG`, `flashB`, `bloomRadius`, `bloomWide`, `impact`, `impactMode`, `impactX`, `impactY`, `freeze`, `keepHot`, `synthetic`, `priority`, `stepFps`, `tape`, `rewind`, `rewindTear`, `tapeStyle`, `tapeHz` e `keep`, além dos métodos `addImpact(float, int, float, float)`, `reset()` e `idle()`.

NeoForge 21.1.267 (FML loader 4.0.45):

- `ModContainer.registerConfig(ModConfig.Type, IConfigSpec)` existe.
- `ModConfigSpec.Builder.translation(String)`, `defineInRange(String, double, double, double)`, que retorna `DoubleValue`, e `define(String, boolean)`, que retorna `BooleanValue`: os três existem.
- `IConfigScreenFactory.createScreen(ModContainer, Screen)` existe, e o construtor `ConfigurationScreen(ModContainer, Screen)` existe. Por isso, `ConfigurationScreen::new` serve como fábrica.

### Pendente

Nada da 0.5.0 à 0.6.2 foi executado em jogo. Siga o roteiro de [TESTE-REAL.md](TESTE-REAL.md) e registre aqui os resultados e os CSVs de quadro. Pontos a observar:

- os cues de servidor e o dano do Sinal de Maré;
- o orçamento por passe e o painel F7;
- o post chamado sem vaga;
- o alcance por definição;
- os efeitos longos;
- a tela de configuração na lista de mods e o efeito dos dois ajustes;
- a câmera própria (film);
- a aceitação de nomes de uniform indexados (`u.f4("Pontos[3]", ...)`), que o javap não mostra.

O protocolo de rede não mudou em relação à 0.4.0 (canais event, end e request_v2).

---

## Versão 0.4.0

Minecraft Java 1.21.1, NeoForge 21.1.267, RimuLib 1.3.0 e Java 21. O motor e o addon foram compilados separadamente. O projeto de exemplo compilou contra o JAR público do motor. O build passou nas verificações de orçamento e frustum. O manifesto do motor declara Minecraft, NeoForge e RimuLib.

O cliente gráfico carregou os dois JARs e um segundo addon temporário de teste em um mundo plano com servidor integrado. A execução confirmou:

- Cinco habilidades internas, o Sinal de Maré e seis habilidades de teste formaram doze entradas no Focus e quatro páginas no Atlas. Duas habilidades com o caminho signal em namespaces diferentes mantiveram identidades próprias.
- O cartão do Sinal de Maré carregou sua imagem e recebeu um atalho reconfigurável. Mirar e conjurar pelo Atlas criou a instância no servidor, iniciou a câmera e compilou o shader vfxsample:signal do JAR externo. Pular a tomada devolveu o controle da câmera.
- Durante a recarga, um segundo pedido deixou a contagem de instâncias em um. Um pedido para conjurar um efeito privado também manteve essa contagem.
- Com o Focus em mãos, a tecla H abriu o conjunto correto. O painel exibiu a instrução do addon. O teste ajustou a potência a 2 pela mesma API cliente do slider e usou o Focus para conjurar. A altura recebida foi um bloco acima do alvo padrão. O gesto de arrastar o slider com o mouse não foi automatizado.

[Cartão no Atlas](gameplay/addon-atlas-040.png) · [Conjuração no mundo](gameplay/addon-cast-040.png) · [Focus com o painel selecionado](gameplay/addon-focus-040.png). As entradas Probe no Focus pertencem ao segundo addon temporário, que não fornece artes. O pacote distribuído inclui somente o exemplo Sinal de Maré.

SHA-256 do JAR do motor: `3695eaf585c9bbc615148e27b8c038f11f244240535cefbec512e7b43861bb48`.

O cliente usou Mesa llvmpipe. Esta rodada cobre o servidor integrado. Desempenho em GPU física e sincronização entre dois clientes independentes continuam sem medição para a 0.4.0. Consulte o [guia de autoria](ADDONS-0.4.md) para reproduzir a conjuração.

---

## Versão 0.3.0

O motor e o projeto de integração foram compilados separadamente para Minecraft 1.21.1, NeoForge 21.1.267, Java 21 e RimuLib 1.3.0. O consumidor compilou contra o JAR do motor. O cliente gráfico de teste carregou ambos como JARs e usou um componente temporário de automação em um mundo com servidor integrado. O pacote de entrega contém o exemplo opcional e seu fonte.

O teste confirmou registro por namespace, início fixo, âncora na entidade correta, posição de renderização após deslocamento de três blocos, cancelamento pelo servidor, nova conjuração e expiração natural. A tecla do Pulso Prismático percorreu o caminho público de início. Para os impact frames, uma camada de observação leu os parâmetros finais na etapa de composição da RimuLib. Confirmou dessaturação e contraste na janela programada, clarão colorido numa segunda definição e ausência dos acentos com a opção de ocultar clarões ligada. O teste de orçamento também cobre os limites temporais e a queda de intensidade do impacto.

SHA-256 do JAR distribuído

`c1d8fc8cae97c7b735bd75c3bfafa6b98bee98d32c30c08632bc7ac45ae0b24f`

A execução gráfica usou Mesa llvmpipe. Duas máquinas em multiplayer, custo em GPU física, transferência de dimensão e reentrada no alcance de uma âncora móvel não foram exercitados nesta rodada. A duração de um impacto é medida em ticks. A captura do guia mostra a fase anterior ao impacto.

---

## Versão 0.2.0

Minecraft Java 1.21.1, NeoForge 21.1.267, Java 21 e RimuLib 1.3.0. O build offline reutilizou os artefatos de desenvolvimento da 1.21.1 e passou em checkBudget e checkFrustum. O JAR de distribuição foi instalado em um projeto de teste separado com RimuLib e um componente temporário de automação.

SHA-256 do JAR da versão 0.2.0

`e84ced25c50b5dddf31afb99e36c3187d7d175fd0d9fb49b093f538941e6dc08`

O teste abriu o Atlas, mirou no cartão do Horizon Core e confirmou a execução aceita pelo servidor e o início da câmera. Em outra execução, um jogador simulado conjurou a habilidade no servidor integrado. O cliente recebeu o evento de outro conjurador e permaneceu com a câmera livre. A cena inclui uma escada de quartzo construída com blocos inteiros.

Os vídeos em gameplay/horizon-core-caster.mp4 e gameplay/horizon-core-observer.mp4 usam capturas do cliente a cada dois ticks, reproduzidas a 10 quadros por segundo. As faixas de áudio dos vídeos foram montadas com os quatro arquivos do mod nos tempos da sequência. A captura de vídeo não mede a mixagem posicional da saída de áudio do jogo. Os PNGs da pasta gameplay documentam a versão 0.1.2.

### Custo observado

Mundo plano, janela 854 × 480, alcance de 3 chunks, perfil BALANCED, limite de 40 fps, Mesa llvmpipe. Os tempos abaixo são intervalos entre quadros do cliente, coletados fora da gravação em uma execução. O observador manteve a mesma posição e o mesmo enquadramento. No perfil BALANCED, no máximo dois volumes entram no shader por quadro.

| Horizon Cores simultâneos | Quadros | Mediana | Percentil 95 |
|---|---:|---:|---:|
| 0 | 149 | 25,16 ms | 27,81 ms |
| 1 | 325 | 28,15 ms | 35,32 ms |
| 3 | 253 | 36,24 ms | 46,85 ms |

A base já encosta no limite de 40 fps. Os valores incluem CPU e renderização por software. GPU física, dois clientes independentes, shaders externos e resoluções maiores seguem sem medição.

---

## Versão 0.1.2

### Build e dependências

Minecraft Java 1.21.1, NeoForge 21.1.267, RimuLib 1.3.0, Java 21 e Gradle 9.2.1. Duas compilações do mod em diretórios separados produziram JARs idênticos. Uma executou `clean build` com a etapa de preparação dos artefatos do Minecraft. Ambas passaram em `checkBudget` e `checkFrustum`.

SHA-256 de `dist/calamidade_awakening-0.1.2.jar`

`9b9e9511836ba1695c59b40b2c895669a703d7010bb5898fef057f094b50824c`

O manifesto declara Minecraft, NeoForge e RimuLib como requisitos. A validação do pacote compara os recursos e as classes do JAR com a saída do build e verifica a ausência de JARs incorporados.

### Cobertura dos testes

- O teste de geometria cobre orçamento de vértices, continuidade de anéis e esferas, prioridade dos efeitos e chaves de rede.
- O teste de visibilidade usa o frustum do Minecraft e inclui as bordas laterais e verticais, o observador dentro do volume e objetos atrás da câmera.

### Atlas no Minecraft

O JAR foi colocado na pasta mods de um projeto de teste separado. Um componente temporário acionou os controles e observou os eventos do jogo em um mundo com servidor integrado. A lista de mods continha Minecraft, NeoForge, RimuLib, Calamidade_Awakening e esse componente de teste. O componente fica fora do pacote de entrega.

A tecla N abriu e fechou o atlas com as mãos vazias. Após um deslocamento lateral de um bloco, a coordenada mundial da âncora permaneceu igual. O botão Atlas Estelar no menu de pausa abriu a mesma interface. Um clique de ataque no cartão central fechou o atlas e o cliente recebeu do servidor o evento do Pulso Prismático.

![Atlas aberto](gameplay/atlas-open.png)

![O mesmo atlas após deslocamento lateral](gameplay/atlas-side.png)

As outras quatro habilidades foram acionadas pelas teclas padrão U, I, V e Y. Foram verificados o recebimento dos eventos, a ativação da câmera nas três habilidades cinematográficas e sua liberação ao pressionar O durante Núcleo do Horizonte. As cinco capturas estão em gameplay/.

O cliente usou Mesa llvmpipe com OpenGL 4.5. O teste cobriu um jogador em servidor integrado. Continuam pendentes multiplayer com dois clientes e medições de desempenho em GPU física.
