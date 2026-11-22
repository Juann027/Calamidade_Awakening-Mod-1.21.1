# AGENTS.md — guia para IAs e agentes

Este arquivo é o ponto de entrada para qualquer agente (Claude, Codex, Copilot, Cursor...) que vá ler, alterar ou estender este repositório. Leia inteiro antes de editar. Os capítulos humanos ficam em `docs/`; o material feito para agentes fica em `docs/ai/`.

## O que é o projeto

**Calamidade_Awakening** é um motor modular de efeitos visuais para Minecraft Java 1.21.1, NeoForge 21.1.267, Java 21, construído sobre a biblioteca **RimuLib 1.3.0** (pós-processamento `FxApi`, câmera `FilmApi`, menus). Ele sincroniza instâncias de efeitos entre servidor e clientes, seleciona o que desenhar por prioridade e orçamento de qualidade, e oferece a addons (outros mods) uma API pública para registrar efeitos com roteiro (**Timeline**), clima de cena (**Atmosphere**), passes de shader próprios, malhas de mundo, câmera, cues de gameplay no servidor e cartões no menu (Atlas e Focus). Desde a 0.9: feixes (**Beam**), luz visual sobre o mundo (**Light**) e prioridade protagonista (**HERO**). Desde a 0.10: **orientação por instância** (`Placement`, yaw e pitch, seguir o corpo da âncora), **fontes e limites** (`EffectSource`: jogador, mob, bloco, script; limites por fonte e vagas reservadas para jogadores na config do servidor; recarga por fonte) e **gatilhos prontos** (`EffectTriggers`: item, block entity, entidade), além de um modelo para outros mods dependerem do motor sem Maven. Desde a 0.11: **Terrain API** (`TerrainShape`, `TerrainPalette`, `TerrainEdit`): crateras, trincheiras e estruturas que caem, escritas direto nas seções dos chunks no marcador da Timeline (normalmente o do impact frame), com borda queimada, blocos reais no lugar do dublê, calor visual no cliente, regras de segurança na config do servidor e desfazer. Fora da Terrain API, o motor é cosmético: dano e consequências vêm dos cues dos addons, e a Light não muda o nível de luz do jogo.

Desde a 0.11.1, a Terrain API passa pelos eventos de explosão do NeoForge (mods de proteção territorial), pela borda do mundo, pela proteção de spawn e pelo modo de jogo do jogador, atualiza a casca externa da edição e é coberta por testes em jogo no repositório (`./gradlew testeJogo` e `testeCliente`).

Versão atual: motor **0.11.1**; exemplo `examples/vfx-sample` **1.8.0** (depende de `[0.11.0,0.12.0)`). NeoForge 21.1.267 no build; conferido com 21.1.265 (`-Pneoforge.version=21.1.265`).

## Mapa do repositório

| Caminho | Conteúdo |
|---|---|
| `src/main/java/dev/calamidade_awakening/api/v1/` | **API pública** (estável): `Effects`, `Timeline`, `Atmosphere`, `Beam`, `Light`, `Point`, `Addons`; desde a 0.10 `Placement`, `EffectSource` e `EffectTriggers`; desde a 0.11 `TerrainShape`, `TerrainPalette`, `TerrainEdit` e `TerrainEditEvent`. |
| `src/main/java/dev/calamidade_awakening/api/v1/client/` | API pública de cliente: `AddonLayers` (world, hud, panel, post, film, start). |
| `src/main/java/dev/calamidade_awakening/api/` | Núcleo puro interno (sem Minecraft quando possível): `VisualKind` (registro), `FrameBudget` (seleção, HERO), `PassBudget` (orçamentos comum e HERO), `EnginePasses` (pagamento e teto de feixes e luzes), `Quality`, `CueClock`, `Cooldowns` (por fonte e efeito), `AtmosphereMath`, `Basis` (referencial de yaw e pitch), `SourceLimits` (admissão por fonte e reserva), `TriggerLogic` (modos do gatilho de bloco); desde a 0.11 `TerrainNoise` (hash e ruído, iguais no GLSL), `TerrainPlanner` e `TerrainPlan` (plano por seção). |
| `src/main/java/dev/calamidade_awakening/client/` | Cliente: `ClientHooks` (eventos), `StageScene` (instâncias, seleção e resolução de `Point`), `RimuStageBackend` (passes e orçamento), `AtmosphereFrame` (atmosfera e névoa), `BeamFrame` e `LightFrame` (feixes e luzes do quadro), `TerrainClient`, `TerrainFrame` e `TerrainMeshes` (0.11: estado das edições, passe `terrain`, malhas de estrutura), `DataTexture` (dados por textura RGBA32F), `PerfOverlay` (F7), `StageFilms`, `StarAtlas`, `geometry/`. |
| `src/main/java/dev/calamidade_awakening/net/` | Rede e servidor: `StageNetwork` (instâncias, cues, alcance, fontes, limites, recarga), pacotes `StageEvent` (canal `event_v2`: orientação e fonte), `TerrainSync` (canal `terrain_v1`, 0.11), `EndEffect`, `ReleaseEffect`, `CastRequest`, `BenchCommand`. |
| `src/main/java/dev/calamidade_awakening/terrain/` | Servidor da Terrain API (0.11): `TerrainWorks` (regras, plano, evento, commit, histórico), `TerrainJob` (commit e desfazer em lotes), `TerrainWriter` (escrita direta nas `LevelChunkSection`, só chunks FULL), `TerrainSnapshot`, `TerrainCommand` (`/calamidade terrain`); desde a 0.11.1 `TerrainGuard` (borda do mundo, spawn, modo de jogo, `ExplosionEvent` sintético) e `TerrainBorder` (casca externa: forma, vizinho e fluido). |
| `src/testeJogo/` | (0.11.1) Mod de teste em jogo `calamidade_teste`, fora do JAR: `Roteiro` (servidor dedicado: proteção, luz, mapas de altura, POI, contadores, casca externa, custo, chunks FULL, reinício), `RoteiroCliente` (luz no cliente, capturas de dia e de noite, modo aventura, block entity), `Medidor` (MSPT). |
| `src/main/java/dev/calamidade_awakening/config/` | `ClientConfig`: acessibilidade (screenEffects, cameraShake, motionEffects, vanillaFog). `ServerConfig` (0.10): limites por fonte (maxPerPlayer, maxPerEntity, maxPerBlock, playerReserve); desde a 0.11 a seção `terrain` (terrainEnabled, terrainMaxVolume, terrainBlocksPerTick, terrainDimensions, terrainBlockEntities, terrainMobGriefing, terrainUndoMinutes, terrainUndoEdits, terrainMaxStructure; desde a 0.11.1 terrainExplosionEvent e terrainSpawnProtection). |
| `src/main/resources/assets/calamidade_awakening/shaders/fx/` | Shaders de pós do motor: `awakening_field.fsh`, `lights.fsh`, `beams.fsh`, `atmosphere.fsh`, `terrain.fsh` (0.11) e as bibliotecas **`calamidade_fx.glsl`** e `calamidade_terrain.glsl` (interna: ruído e formas iguais ao Java). |
| `src/main/resources/data/calamidade_awakening/tags/block/terrain_protected.json` | Blocos que a Terrain API nunca troca (além de bedrock, dureza < 0 e block entities). |
| `src/test/java/` | Testes executáveis sem framework: `FrameBudgetCheck` (inclui `TimelineCheck`, `AtmosphereCheck`, `PriorityCheck`, `BeamCheck`, `LightCheck`, `OrientationCheck`, `StageEventCheck`, `SourceCheck`, `TriggerCheck`, `TerrainCheck`), `FrustumCheck`. |
| `examples/vfx-sample/` | Addon de exemplo, projeto Gradle separado: Sinal de Maré, Farol e as **21 receitas** (`receitas/`), entre elas o cajado (item), os pilares (blocos), o zumbi com feixe (mob vanilla) e, desde a 1.8.0, o raio que abre trincheira, o meteoro, a cratera simples e a torre que cai (estrutura `data/vfxsample/structure/torre.nbt`). |
| `examples/consumidor/` | Mod mínimo que depende do Calamidade **sem Maven** (JAR do release do GitHub com SHA-256); igual ao modelo de `docs/ai/usar-como-dependencia.md`. |
| `docs/` | Capítulos (português). `MANUAL-GERAL.md` é gerado: não edite à mão. |
| `docs/ai/` | Para agentes: `API-REFERENCIA.md` (gerado), `glsl.md`, `receitas/`, `usar-como-dependencia.md`. |
| `scripts/` | `novo_efeito.py`, `ApiReferencia.java`, `testar_glsl.cjs`, `previa_passes.cjs`, `gerar_manual.py`, `gerar_torre.py` (0.11: a estrutura da receita 21). |
| `.claude/skills/calamidade-efeitos/` | Skill de projeto com o fluxo completo de criação de efeitos. |

## Regras invioláveis

1. **API pública.** Só `dev.calamidade_awakening.api.v1` (e `.client`) é API. Nunca remova nem mude a assinatura de um membro público: addons compilados contra versões anteriores precisam continuar carregando. Para evoluir, **adicione**: novo construtor mantendo o antigo, componente de record com construtor de compatibilidade, método `default` em interface. Todo membro público precisa de Javadoc com unidade (ticks, blocos, 0..1), limites e, quando ajudar, um exemplo curto: o build falha sem isso. Depois de mudar a API, rode `./gradlew apiDocs` e faça commit de `docs/ai/API-REFERENCIA.md`.
2. **Rede.** Canais atuais: `event_v2` (0.10: orientação e fonte), `terrain_v1` (0.11: estado das edições de terreno), `end_effect`, `request_v2`, `release`. Mudar ou criar pacote exige nova versão do motor e o aviso "servidor e clientes na mesma versão" em `MUDANCAS.md` e nas notas de release. Nada de pacotes por tick: o cliente deriva tudo do relógio da instância (Timeline, Atmosphere, feixes, luzes, edições de terreno e camadas são registrados nos dois lados); `terrain_v1` vai uma vez por mudança de estado e a quem chega depois.
3. **Licenças e terceiros.** Nunca versione nem anexe a releases JARs de terceiros (`libs/*.jar` e `dist/*.jar` estão no `.gitignore`). A RimuLib é baixada do CurseForge pela task `rimulib`, conferida por SHA-256 (`gradle.properties`). Código do Minecraft, do NeoForge e da RimuLib não é copiado para cá; use as APIs.
4. **Confira com javap antes de usar.** Todo tipo, método ou campo novo da RimuLib, do NeoForge ou do Minecraft é conferido no JAR antes do primeiro uso (nomes e assinaturas mudam entre versões e os mapeamentos não são os do seu treino):
   - RimuLib: `javap -p -cp libs/rimulib-1.3.0-neoforge.jar cyou.rimuru.rimulib.mc1211.client.api.FxApi\$Frame`
   - NeoForge e Minecraft: `javap -cp build/moddev/artifacts/neoforge-21.1.267-merged.jar net.minecraft.sounds.SoundEvents` (o arquivo existe depois do primeiro build)
   - Use `javap -c` para comportamento (por exemplo, como o `#include` de shader é resolvido) e registre conclusões novas em `docs/VALIDACAO.md`.
5. **Git.** Rode `git status` antes de todo `git add` e adicione arquivos pelo nome. Nunca `git add -A` às cegas, nunca commit de `build/`, `run/`, `libs/*.jar`, `dist/*.jar`. Um commit por etapa de trabalho, com mensagem que diga o porquê.
6. **Terreno.** Só a Terrain API (`Effects.terrain`) troca blocos a partir de um efeito: ela aplica as regras do servidor (config `terrain`, dimensões, `mobGriefing`, `TerrainEditEvent`, desde a 0.11.1 borda do mundo, spawn, modo de jogo e `ExplosionEvent` do NeoForge, tag `terrain_protected`, block entities só com opção explícita) e guarda o snapshot de desfazer. Quem muda o escritor (`TerrainWriter`, `TerrainJob`, `TerrainBorder`) mantém o que `LevelChunk.setBlockState` faz (luz pelo mesmo ramo do vanilla, mapas de altura, POI, salvamento) e roda `./gradlew testeJogo` e `testeCliente`. Não escreva blocos em massa num cue com `level.setBlock` para "cratera": use a API. Ler o mundo fora da thread do servidor é proibido; o plano é puro justamente para rodar em paralelo sem ler o mundo.
7. **Acessibilidade.** Efeitos de tela passam pelos métodos de `AddonLayers.Post` (que aplicam intensidade, tremor, clarões e movimento). Passes próprios que mudam a tela inteira multiplicam a força por `post.screenEffects()`, evitam piscar quando `!post.flashAllowed()` e pulam zoom/borrão quando `!post.motionEffects()`. A exposição da atmosfera já é limitada pelo motor.
8. **Orçamento.** Não contorne o orçamento por quadro (tabela abaixo) nem o de blocos por tick da Terrain API (config do servidor). Prefira shaders analíticos (raio-caixa, raio-esfera, segmentos) a raymarch, e reduza passos em `post.quality() == LOW`.
9. **Versão.** Ao mudar a versão do motor, mude também `version` em `src/main/resources/META-INF/neoforge.mods.toml`, o JAR que o exemplo usa em `examples/vfx-sample/build.gradle` e o intervalo do manifesto do exemplo; o `checkModVersion` falha se não baterem. No release, atualize versão e SHA-256 do JAR em `examples/consumidor/build.gradle` (o build é reproduzível: o hash do `dist/` local é o do release) e rode `./gradlew syncRecipes`.
10. **Build verde.** `./gradlew build` precisa passar ao fim de cada etapa, e o exemplo também (`./gradlew dist` e `./gradlew -p examples/vfx-sample build`).

## Comandos

| Comando | O que faz |
|---|---|
| `./gradlew build` | Compila e roda `checkBudget` (Java puro: orçamento, prioridade HERO, timeline, atmosfera, feixes, luzes, cues, registro), `checkFrustum`, `checkShaders` (glslangValidator em todo shader fx do motor e do exemplo; só avisa se o glslang não estiver instalado, exceto no CI), `checkApiDocs`, `checkRecipes` e `checkModVersion` (versão do `neoforge.mods.toml` igual à do `build.gradle` e aceita pelo exemplo). Baixa a RimuLib se faltar. |
| `./gradlew dist` | Build + copia o JAR para `dist/` (de onde o exemplo compila). |
| `./gradlew -p examples/vfx-sample build` | Compila o exemplo contra `dist/calamidade_awakening-0.11.1.jar`. |
| `./gradlew testeJogo` | (0.11.1) Teste em jogo da Terrain API num servidor dedicado sem tela (mundo plano em `run/teste-jogo`, duas execuções para o reinício); relatório em `run/teste-jogo/calamidade-teste.txt`. `-PtesteMods=<pasta>` instala outros mods. |
| `xvfb-run -a ./gradlew testeCliente` | (0.11.1) Teste em jogo no cliente, no mundo deixado pelo `testeJogo`: luz recalculada, capturas de dia e de noite, modo aventura, block entity. Relatório em `run/teste-cliente/`. |
| `./gradlew build -Pneoforge.version=21.1.265` | Compila e testa contra outra versão do NeoForge 21.1 (também vale para o exemplo). |
| `./gradlew -p examples/consumidor build` | Baixa o Calamidade do release do GitHub e a RimuLib, confere o SHA-256 e compila o consumidor (`-Pcalamidade.url=file:///…` para testar um JAR local). |
| `./gradlew apiDocs` | Regenera `docs/ai/API-REFERENCIA.md` (javap no JAR + Javadoc). |
| `./gradlew syncRecipes` | Copia o código do exemplo para os blocos de `docs/ai/receitas/*.md` e do consumidor para `docs/ai/usar-como-dependencia.md`. |
| `node scripts/testar_glsl.cjs` | Executa os testes de `calamidade_fx.glsl` numa GPU (WebGL2 no Chromium headless; requer Node e Playwright). |
| `node scripts/previa_passes.cjs [cena]` | Roda os passes `beams` e `lights` do motor (shader real, `common.glsl` da RimuLib) sobre uma cena sintética com profundidade e grava PNGs em `build/previa/`. |
| `python3 scripts/novo_efeito.py <ns> <nome> --projeto <dir> [--cartao]` | Gera o esqueleto de um efeito de addon que compila sem edição. |
| `python3 scripts/gerar_manual.py` | Regenera `docs/MANUAL-GERAL.md` a partir dos capítulos. |
| `python3 scripts/gerar_torre.py` | Regera a estrutura `torre.nbt` da receita 21 (byte a byte igual). |
| `./gradlew runClient` | Cliente de desenvolvimento (precisa de tela). |

No jogo: **F7** painel de diagnóstico (passes, vértices, HERO, feixes, luzes, atmosferas e, desde a 0.10, fontes), **F8** troca a qualidade, `/calamidade_client record <s>` grava tempos de quadro em CSV, `/calamidade bench <n> [efeito]` cria carga (fonte SCRIPT: até 48 com a reserva padrão), `/calamidade status` mostra ativas por fonte, limites e recusas (e, desde a 0.11, o terreno), `/calamidade terrain list` e `/calamidade terrain undo [último|id]` (0.11) listam e desfazem edições de terreno, `/vfxsample receita <nome>` mostra cada receita.

## Como criar um efeito novo em 7 passos

1. **Esqueleto.** `python3 scripts/novo_efeito.py meumod raio_solar --projeto caminho/do/addon [--cartao]`. Ele gera a classe (registro automático em `FMLConstructModEvent`, comando `/meumod efeito raio_solar`), o shader e as traduções. Alternativa: copie a receita mais parecida de [docs/ai/receitas/](docs/ai/receitas/README.md).
2. **Roteiro (Timeline).** Fases com nome para o que acontece, trilhas para valores que variam, marcadores para momentos de gameplay; `sustain` para efeitos ambientes que só param com `release()`. Capítulo: [TIMELINE-0.7.md](docs/TIMELINE-0.7.md). Receitas: 1 (marcador), 6 (sustain).
3. **Visual.** Escolha a camada certa: feixes (`Effects.beams`) e luz no mundo (`Effects.lights`) sem shader; clima de cena (`Effects.atmosphere`); passe de pós com shader próprio (`AddonLayers.post` + `post.pass`, usando `calamidade_fx.glsl`); malha de mundo (`AddonLayers.world`); efeitos de tela prontos (`post.bloom`, `vignette`...). Efeito principal da cena: `withPriority(Effects.Priority.HERO)`. Capítulos: [FEIXES-0.9.md](docs/FEIXES-0.9.md), [LUZ-0.9.md](docs/LUZ-0.9.md), [PRIORIDADE-0.9.md](docs/PRIORIDADE-0.9.md), [AVANCADO-0.6.md](docs/AVANCADO-0.6.md), [ATMOSFERA-0.8.md](docs/ATMOSFERA-0.8.md), [glsl.md](docs/ai/glsl.md). Receitas: 1 a 5 (shaders), 6 (malha), 7 (atmosfera), 8 a 10 (feixes), 11 e 12 (luz), 13 (HERO).
4. **Terreno (opcional, 0.11).** Para cavar, queimar a borda, trocar o dublê por blocos reais ou derrubar uma estrutura: `Effects.terrain(id, TerrainEdit.carve(TerrainShape...).commitAt("impacto").burn(TerrainPalette.burnt(), 2)...build())`, depois da timeline; ponha o marcador no tick do `Effects.Impact` (4 a 8 ticks de clarão). Capítulo: [TERRENO-0.11.md](docs/TERRENO-0.11.md). Receitas: 18 a 21.
5. **Gameplay, câmera e disparo.** Dano e consequências em `Effects.cue(id, "marcador", ctx -> ...)` no servidor (`ctx.forward()` e `ctx.facing(...)` dão a direção da instância); câmera própria com `AddonLayers.film` alinhada às fases. Para começar o efeito de um item, bloco ou mob, use `EffectTriggers` (receitas 15 a 17); para virá-lo, `Placement` ou `Effects.start(..., yaw, pitch)` e pontos `Point.facing` (receita 14). Capítulos: [GAMEPLAY-0.5.md](docs/GAMEPLAY-0.5.md), [AVANCADO-0.6.md](docs/AVANCADO-0.6.md), [INTEGRACAO-0.10.md](docs/INTEGRACAO-0.10.md).
6. **Acessibilidade e orçamento.** Releia as regras 7 e 8. Confira `post.passesLeft()` antes de passes extras.
7. **Teste.** `./gradlew build`, `./gradlew dist`, build do addon; `node scripts/previa_passes.cjs` para ver feixes e luzes sem o jogo; no jogo: o comando do efeito, **F7** (passes, vértices, HERO, feixes, luzes, atmosfera), **F8** em LOW/BALANCED/HIGH, intensidade dos efeitos de tela em 0, "ocultar clarões" ligado, e `/calamidade bench` para carga. Roteiro completo: [TESTE-REAL.md](docs/TESTE-REAL.md). Registre resultados em [VALIDACAO.md](docs/VALIDACAO.md).

## Limites e orçamento

| Item | Limite |
|---|---|
| Duração de efeito de addon | 1..12000 ticks (20 ticks = 1 s) |
| Raio da definição | (0, 512] blocos; também a extensão do teste de frustum (2,5 × raio) |
| Alcance de distribuição | 16..1024 blocos (padrão 128) |
| Instâncias simultâneas | 64 no servidor; 96 residentes por cliente |
| Por fonte (0.10, config do servidor) | 16 por jogador, 4 por mob, 2 por bloco; SCRIPT sem limite por fonte; 16 das 64 vagas só para jogadores. Acima do limite: `Optional.empty()`, sem erro |
| Orientação (0.10) | yaw qualquer (normalizado para [-180, 180)); pitch [-90, 90]; offsets de `Placement` até 1024 blocos |
| Recarga por fonte e efeito (0.10) | 0..72000 ticks |
| Efeitos desenhados por quadro | 3 / 8 / 16 / 32 (LOW / BALANCED / HIGH / ULTRA, 0.11.2; modo AUTO padrão escolhe o perfil; limites editáveis no config de cliente até 64); HERO entram primeiro e ocupam vagas |
| Efeitos HERO desenhados | no máximo 2 por quadro, os mais próximos; nunca cortados pelo limite acima |
| Passes de addon por quadro | 1 / 3 / 6 / 12 (até 16 no config), divididos entre shaders de cartão e `post.pass` dos efeitos NORMAL, por prioridade |
| Passes HERO por quadro | 1 / 2 / 3 / 4, orçamento próprio dos efeitos `withPriority(Priority.HERO)` |
| Vértices de malha por quadro | 12 000 / 48 000 / 96 000, divididos entre os efeitos visíveis |
| Feixes (`Effects.beams`) | até 16 por efeito; 16 / 64 / 128 / 128 por quadro, dos efeitos mais importantes; um passe `beams` por quadro, cobrado do orçamento HERO se um efeito HERO desenhado tem feixes, senão do comum |
| Luzes (`Effects.lights`) | até 8 por efeito; 8 / 32 / 64 / 64 por quadro, dos efeitos mais importantes; um passe `lights` por quadro, antes dos efeitos e da atmosfera, mesmo esquema de orçamento dos feixes. Luz visual: não muda o nível de luz do jogo |
| Passe de atmosfera | 1 por quadro, do motor, fora do orçamento de addons; só com atmosfera ativa |
| Mistura de atmosferas | as 2 de maior peso |
| Nomes de fase, trilha e marcador | `[a-z0-9_]`, até 64 caracteres |
| Impact | duração 1..20 ticks, força (0, 1] |
| `ctx.living(raio)` | raio até 128 blocos |
| Cartão | recarga 1..72000 ticks; tomadas com FOV 25..110 e câmera a até 128 blocos |
| Terreno (0.11) | até 4 edições por efeito; forma até 8 primitivas, alcance até 128 blocos, borda irregular até 8; casca 1..4; preenchimento até raio 16; estrutura até 32 por eixo (config); padrão do servidor: 150 000 blocos por edição, 16 384 blocos por tick divididos entre as edições, desfazer por 10 minutos (16 snapshots) |
| Passe `terrain` (0.11) | 1 por quadro; 2/4/8 zonas quentes e 4/8/8 dublês; pago antes das luzes (HERO ou comum); malhas de estrutura 1/2/4 por quadro |

## Convenções de código

- Javadoc em inglês no código; documentação, mensagens de commit e textos de jogo em português (com tradução `en_us`).
- Coordenadas de cliente: `post.center()` e os uniforms de shader são **relativos à câmera** (o espaço de `scenePosAt`); `World` usa deslocamentos a partir do centro da instância.
- Shaders fx: a RimuLib antepõe `#version 150` e `common.glsl` (veja [glsl.md](docs/ai/glsl.md)); escreva `fragColor` com a cor final da cena (leia `DiffuseSampler`, some o efeito, escreva tudo).
- Registro na ordem: `Effects.register` → `Effects.timeline` → `Effects.atmosphere` / `Effects.beams` → `Effects.lights` → `Effects.cue`; cartões com `Addons.publish` durante a construção do mod; camadas de cliente em `FMLClientSetupEvent`. Gatilhos (`EffectTriggers`) podem ser `static final`: o efeito só precisa estar registrado quando disparam.
- Referencial das instâncias (0.10): eixos locais do Minecraft (`^x ^y ^z`): x à esquerda, y para cima, z para a frente; yaw 0 olha para o sul, pitch positivo olha para baixo. Sem orientação, o referencial é o dos eixos do mundo (comportamento da 0.9).
- O Calamidade **não depende de GeckoLib** nem de outros mods de animação: código de rig fica no mod do usuário (veja INTEGRACAO-0.10.md).
- Testes novos de lógica pura entram no `checkBudget` (`src/test/java`, sem JUnit).
- Terreno (0.11): formas no referencial da instância (x à esquerda, y para cima, z para a frente); um bloco é cavado quando a distância no seu centro é <= 0. O ruído da borda (`TerrainNoise`) é aritmética inteira de 32 bits e tem cópia fiel em `calamidade_terrain.glsl`: mudou um, mude o outro e os casos do `testar_glsl.cjs` (valores tirados do Java).
