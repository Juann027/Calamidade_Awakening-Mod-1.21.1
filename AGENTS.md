# AGENTS.md — guia para IAs e agentes

Este arquivo é o ponto de entrada para qualquer agente (Claude, Codex, Copilot, Cursor...) que vá ler, alterar ou estender este repositório. Leia inteiro antes de editar. Os capítulos humanos ficam em `docs/`; o material feito para agentes fica em `docs/ai/`.

## O que é o projeto

**Calamidade_Awakening** é um motor modular de efeitos visuais para Minecraft Java 1.21.1, NeoForge 21.1.267, Java 21, construído sobre a biblioteca **RimuLib 1.3.0** (pós-processamento `FxApi`, câmera `FilmApi`, menus). Ele sincroniza instâncias de efeitos entre servidor e clientes, seleciona o que desenhar por prioridade e orçamento de qualidade, e oferece a addons (outros mods) uma API pública para registrar efeitos com roteiro (**Timeline**), clima de cena (**Atmosphere**), passes de shader próprios, malhas de mundo, câmera, cues de gameplay no servidor e cartões no menu (Atlas e Focus). Desde a 0.9: feixes (**Beam**), luz visual sobre o mundo (**Light**) e prioridade protagonista (**HERO**). O motor é cosmético: dano e consequências vêm dos cues dos addons, e a Light não muda o nível de luz do jogo.

Versão atual: motor **0.9.0**; exemplo `examples/vfx-sample` **1.6.0** (depende de `[0.9.0,0.10.0)`).

## Mapa do repositório

| Caminho | Conteúdo |
|---|---|
| `src/main/java/dev/calamidade_awakening/api/v1/` | **API pública** (estável): `Effects`, `Timeline`, `Atmosphere`, `Beam`, `Light`, `Point`, `Addons`. |
| `src/main/java/dev/calamidade_awakening/api/v1/client/` | API pública de cliente: `AddonLayers` (world, hud, panel, post, film, start). |
| `src/main/java/dev/calamidade_awakening/api/` | Núcleo puro interno (sem Minecraft quando possível): `VisualKind` (registro), `FrameBudget` (seleção, HERO), `PassBudget` (orçamentos comum e HERO), `EnginePasses` (pagamento e teto de feixes e luzes), `Quality`, `CueClock`, `Cooldowns`, `AtmosphereMath`. |
| `src/main/java/dev/calamidade_awakening/client/` | Cliente: `ClientHooks` (eventos), `StageScene` (instâncias, seleção e resolução de `Point`), `RimuStageBackend` (passes e orçamento), `AtmosphereFrame` (atmosfera e névoa), `BeamFrame` e `LightFrame` (feixes e luzes do quadro), `DataTexture` (dados por textura RGBA32F), `PerfOverlay` (F7), `StageFilms`, `StarAtlas`, `geometry/`. |
| `src/main/java/dev/calamidade_awakening/net/` | Rede e servidor: `StageNetwork` (instâncias, cues, alcance), pacotes `StageEvent`, `EndEffect`, `ReleaseEffect`, `CastRequest`, `BenchCommand`. |
| `src/main/java/dev/calamidade_awakening/config/` | `ClientConfig`: acessibilidade (screenEffects, cameraShake, motionEffects, vanillaFog). |
| `src/main/resources/assets/calamidade_awakening/shaders/fx/` | Shaders de pós do motor: `awakening_field.fsh`, `lights.fsh`, `beams.fsh`, `atmosphere.fsh` e a biblioteca **`calamidade_fx.glsl`**. |
| `src/test/java/` | Testes executáveis sem framework: `FrameBudgetCheck` (inclui `TimelineCheck`, `AtmosphereCheck`, `PriorityCheck`, `BeamCheck`, `LightCheck`), `FrustumCheck`. |
| `examples/vfx-sample/` | Addon de exemplo, projeto Gradle separado: Sinal de Maré, Farol e as **13 receitas** (`receitas/`). |
| `docs/` | Capítulos (português). `MANUAL-GERAL.md` é gerado: não edite à mão. |
| `docs/ai/` | Para agentes: `API-REFERENCIA.md` (gerado), `glsl.md`, `receitas/`. |
| `scripts/` | `novo_efeito.py`, `ApiReferencia.java`, `testar_glsl.cjs`, `previa_passes.cjs`, `gerar_manual.py`. |
| `.claude/skills/calamidade-efeitos/` | Skill de projeto com o fluxo completo de criação de efeitos. |

## Regras invioláveis

1. **API pública.** Só `dev.calamidade_awakening.api.v1` (e `.client`) é API. Nunca remova nem mude a assinatura de um membro público: addons compilados contra versões anteriores precisam continuar carregando. Para evoluir, **adicione**: novo construtor mantendo o antigo, componente de record com construtor de compatibilidade, método `default` em interface. Todo membro público precisa de Javadoc com unidade (ticks, blocos, 0..1), limites e, quando ajudar, um exemplo curto: o build falha sem isso. Depois de mudar a API, rode `./gradlew apiDocs` e faça commit de `docs/ai/API-REFERENCIA.md`.
2. **Rede.** Canais atuais: `event`, `end_effect`, `request_v2`, `release`. Mudar ou criar pacote exige nova versão do motor e o aviso "servidor e clientes na mesma versão" em `MUDANCAS.md` e nas notas de release. Nada de pacotes por tick: o cliente deriva tudo do relógio da instância (Timeline, Atmosphere e camadas são registrados nos dois lados).
3. **Licenças e terceiros.** Nunca versione nem anexe a releases JARs de terceiros (`libs/*.jar` e `dist/*.jar` estão no `.gitignore`). A RimuLib é baixada do CurseForge pela task `rimulib`, conferida por SHA-256 (`gradle.properties`). Código do Minecraft, do NeoForge e da RimuLib não é copiado para cá; use as APIs.
4. **Confira com javap antes de usar.** Todo tipo, método ou campo novo da RimuLib, do NeoForge ou do Minecraft é conferido no JAR antes do primeiro uso (nomes e assinaturas mudam entre versões e os mapeamentos não são os do seu treino):
   - RimuLib: `javap -p -cp libs/rimulib-1.3.0-neoforge.jar cyou.rimuru.rimulib.mc1211.client.api.FxApi\$Frame`
   - NeoForge e Minecraft: `javap -cp build/moddev/artifacts/neoforge-21.1.267-merged.jar net.minecraft.sounds.SoundEvents` (o arquivo existe depois do primeiro build)
   - Use `javap -c` para comportamento (por exemplo, como o `#include` de shader é resolvido) e registre conclusões novas em `docs/VALIDACAO.md`.
5. **Git.** Rode `git status` antes de todo `git add` e adicione arquivos pelo nome. Nunca `git add -A` às cegas, nunca commit de `build/`, `run/`, `libs/*.jar`, `dist/*.jar`. Um commit por etapa de trabalho, com mensagem que diga o porquê.
6. **Acessibilidade.** Efeitos de tela passam pelos métodos de `AddonLayers.Post` (que aplicam intensidade, tremor, clarões e movimento). Passes próprios que mudam a tela inteira multiplicam a força por `post.screenEffects()`, evitam piscar quando `!post.flashAllowed()` e pulam zoom/borrão quando `!post.motionEffects()`. A exposição da atmosfera já é limitada pelo motor.
7. **Orçamento.** Não contorne o orçamento por quadro (tabela abaixo). Prefira shaders analíticos (raio-caixa, raio-esfera, segmentos) a raymarch, e reduza passos em `post.quality() == LOW`.
8. **Versão.** Ao mudar a versão do motor, mude também `version` em `src/main/resources/META-INF/neoforge.mods.toml`, o JAR que o exemplo usa em `examples/vfx-sample/build.gradle` e o intervalo do manifesto do exemplo; o `checkModVersion` falha se não baterem.
9. **Build verde.** `./gradlew build` precisa passar ao fim de cada etapa, e o exemplo também (`./gradlew dist` e `./gradlew -p examples/vfx-sample build`).

## Comandos

| Comando | O que faz |
|---|---|
| `./gradlew build` | Compila e roda `checkBudget` (Java puro: orçamento, prioridade HERO, timeline, atmosfera, feixes, luzes, cues, registro), `checkFrustum`, `checkShaders` (glslangValidator em todo shader fx do motor e do exemplo; só avisa se o glslang não estiver instalado, exceto no CI), `checkApiDocs`, `checkRecipes` e `checkModVersion` (versão do `neoforge.mods.toml` igual à do `build.gradle` e aceita pelo exemplo). Baixa a RimuLib se faltar. |
| `./gradlew dist` | Build + copia o JAR para `dist/` (de onde o exemplo compila). |
| `./gradlew -p examples/vfx-sample build` | Compila o exemplo contra `dist/calamidade_awakening-0.9.0.jar`. |
| `./gradlew apiDocs` | Regenera `docs/ai/API-REFERENCIA.md` (javap no JAR + Javadoc). |
| `./gradlew syncRecipes` | Copia o código do exemplo para os blocos de `docs/ai/receitas/*.md`. |
| `node scripts/testar_glsl.cjs` | Executa os testes de `calamidade_fx.glsl` numa GPU (WebGL2 no Chromium headless; requer Node e Playwright). |
| `node scripts/previa_passes.cjs [cena]` | Roda os passes `beams` e `lights` do motor (shader real, `common.glsl` da RimuLib) sobre uma cena sintética com profundidade e grava PNGs em `build/previa/`. |
| `python3 scripts/novo_efeito.py <ns> <nome> --projeto <dir> [--cartao]` | Gera o esqueleto de um efeito de addon que compila sem edição. |
| `python3 scripts/gerar_manual.py` | Regenera `docs/MANUAL-GERAL.md` a partir dos capítulos. |
| `./gradlew runClient` | Cliente de desenvolvimento (precisa de tela). |

No jogo: **F7** painel de diagnóstico (passes, vértices, HERO, feixes, luzes, atmosferas), **F8** troca a qualidade, `/calamidade_client record <s>` grava tempos de quadro em CSV, `/calamidade bench <n> [efeito]` cria carga, `/vfxsample receita <nome>` mostra cada receita.

## Como criar um efeito novo em 6 passos

1. **Esqueleto.** `python3 scripts/novo_efeito.py meumod raio_solar --projeto caminho/do/addon [--cartao]`. Ele gera a classe (registro automático em `FMLConstructModEvent`, comando `/meumod efeito raio_solar`), o shader e as traduções. Alternativa: copie a receita mais parecida de [docs/ai/receitas/](docs/ai/receitas/README.md).
2. **Roteiro (Timeline).** Fases com nome para o que acontece, trilhas para valores que variam, marcadores para momentos de gameplay; `sustain` para efeitos ambientes que só param com `release()`. Capítulo: [TIMELINE-0.7.md](docs/TIMELINE-0.7.md). Receitas: 1 (marcador), 6 (sustain).
3. **Visual.** Escolha a camada certa: feixes (`Effects.beams`) e luz no mundo (`Effects.lights`) sem shader; clima de cena (`Effects.atmosphere`); passe de pós com shader próprio (`AddonLayers.post` + `post.pass`, usando `calamidade_fx.glsl`); malha de mundo (`AddonLayers.world`); efeitos de tela prontos (`post.bloom`, `vignette`...). Efeito principal da cena: `withPriority(Effects.Priority.HERO)`. Capítulos: [FEIXES-0.9.md](docs/FEIXES-0.9.md), [LUZ-0.9.md](docs/LUZ-0.9.md), [PRIORIDADE-0.9.md](docs/PRIORIDADE-0.9.md), [AVANCADO-0.6.md](docs/AVANCADO-0.6.md), [ATMOSFERA-0.8.md](docs/ATMOSFERA-0.8.md), [glsl.md](docs/ai/glsl.md). Receitas: 1 a 5 (shaders), 6 (malha), 7 (atmosfera), 8 a 10 (feixes), 11 e 12 (luz), 13 (HERO).
4. **Gameplay e câmera.** Dano e consequências em `Effects.cue(id, "marcador", ctx -> ...)` no servidor; câmera própria com `AddonLayers.film` alinhada às fases. Capítulos: [GAMEPLAY-0.5.md](docs/GAMEPLAY-0.5.md), [AVANCADO-0.6.md](docs/AVANCADO-0.6.md).
5. **Acessibilidade e orçamento.** Releia as regras 6 e 7. Confira `post.passesLeft()` antes de passes extras.
6. **Teste.** `./gradlew build`, `./gradlew dist`, build do addon; `node scripts/previa_passes.cjs` para ver feixes e luzes sem o jogo; no jogo: o comando do efeito, **F7** (passes, vértices, HERO, feixes, luzes, atmosfera), **F8** em LOW/BALANCED/HIGH, intensidade dos efeitos de tela em 0, "ocultar clarões" ligado, e `/calamidade bench` para carga. Roteiro completo: [TESTE-REAL.md](docs/TESTE-REAL.md). Registre resultados em [VALIDACAO.md](docs/VALIDACAO.md).

## Limites e orçamento

| Item | Limite |
|---|---|
| Duração de efeito de addon | 1..12000 ticks (20 ticks = 1 s) |
| Raio da definição | (0, 512] blocos; também a extensão do teste de frustum (2,5 × raio) |
| Alcance de distribuição | 16..1024 blocos (padrão 128) |
| Instâncias simultâneas | 64 no servidor; 96 residentes por cliente |
| Efeitos desenhados por quadro | 3 / 6 / 12 (LOW / BALANCED / HIGH); HERO entram primeiro e ocupam vagas |
| Efeitos HERO desenhados | no máximo 2 por quadro, os mais próximos; nunca cortados pelo limite acima |
| Passes de addon por quadro | 1 / 2 / 4, divididos entre shaders de cartão e `post.pass` dos efeitos NORMAL, por prioridade |
| Passes HERO por quadro | 1 / 2 / 3, orçamento próprio dos efeitos `withPriority(Priority.HERO)` |
| Vértices de malha por quadro | 12 000 / 48 000 / 96 000, divididos entre os efeitos visíveis |
| Feixes (`Effects.beams`) | até 16 por efeito; 16 / 48 / 128 por quadro, dos efeitos mais importantes; um passe `beams` por quadro, cobrado do orçamento HERO se um efeito HERO desenhado tem feixes, senão do comum |
| Luzes (`Effects.lights`) | até 8 por efeito; 8 / 24 / 64 por quadro, dos efeitos mais importantes; um passe `lights` por quadro, antes dos efeitos e da atmosfera, mesmo esquema de orçamento dos feixes. Luz visual: não muda o nível de luz do jogo |
| Passe de atmosfera | 1 por quadro, do motor, fora do orçamento de addons; só com atmosfera ativa |
| Mistura de atmosferas | as 2 de maior peso |
| Nomes de fase, trilha e marcador | `[a-z0-9_]`, até 64 caracteres |
| Impact | duração 1..20 ticks, força (0, 1] |
| `ctx.living(raio)` | raio até 128 blocos |
| Cartão | recarga 1..72000 ticks; tomadas com FOV 25..110 e câmera a até 128 blocos |

## Convenções de código

- Javadoc em inglês no código; documentação, mensagens de commit e textos de jogo em português (com tradução `en_us`).
- Coordenadas de cliente: `post.center()` e os uniforms de shader são **relativos à câmera** (o espaço de `scenePosAt`); `World` usa deslocamentos a partir do centro da instância.
- Shaders fx: a RimuLib antepõe `#version 150` e `common.glsl` (veja [glsl.md](docs/ai/glsl.md)); escreva `fragColor` com a cor final da cena (leia `DiffuseSampler`, some o efeito, escreva tudo).
- Registro na ordem: `Effects.register` → `Effects.timeline` → `Effects.atmosphere` / `Effects.beams` → `Effects.lights` → `Effects.cue`; cartões com `Addons.publish` durante a construção do mod; camadas de cliente em `FMLClientSetupEvent`.
- Testes novos de lógica pura entram no `checkBudget` (`src/test/java`, sem JUnit).
