# Mudanças

**Estado: 0.9.0 compilada (motor e vfx-sample 1.6.0) e executada em jogo por um roteiro automatizado com renderização por software; teste em GPU física e com dois jogadores pendentes. A 0.8.0 foi testada pelo autor numa RTX 4060, sem problemas visuais.** Veja docs/VALIDACAO.md e o roteiro em docs/TESTE-REAL.md.

# 0.9.0 — Beam API, Light API e prioridade protagonista

- **Prioridade protagonista** (docs/PRIORIDADE-0.9.md): `Effects.Definition.withPriority(Effects.Priority.HERO)`. Instâncias HERO entram antes das NORMAL, nunca são cortadas pelo limite de efeitos desenhados (3/6/12) e são no máximo 2 por quadro, as mais próximas. Têm orçamento de passes próprio (1/2/3 em LOW/BALANCED/HIGH), separado do comum dos addons (1/2/4). Sem HERO, a seleção e o orçamento são os da 0.8 (o `PriorityCheck` compara com o algoritmo antigo). O capítulo explica para leigos a diferença entre "limite de efeitos desenhados" e "complexidade de um efeito", com os números do F7 da 0.8.0.
- **Beam** (`dev.calamidade_awakening.api.v1.Beam`, docs/FEIXES-0.9.md): feixe imutável entre dois **`Point`** (centro da instância, posição fixa, âncora ou conjurador, nos pés, nos olhos ou no bloco mirado), com largura e cor do núcleo e do halo, pulsos, ondulação, ruído e revelação progressiva; todo parâmetro numérico por trilha da Timeline. `Effects.beams(id, feixes...)`, até 16 por efeito.
- **Passe `beams`**: todos os feixes do quadro de uma vez, em espaço de tela, com distância raio-segmento, largura mínima de um pixel, núcleo branco e halo saturado, oclusão pela profundidade e fade perto da câmera. Teto de 16/48/128 feixes por quadro, dos efeitos mais importantes. Halo e pulsos seguem a intensidade dos efeitos de tela.
- **Light** (`dev.calamidade_awakening.api.v1.Light`, docs/LUZ-0.9.md): luz visual pontual, de segmento, ao longo de um feixe (`Light.along`, que segue pontas, revelação e peso) e de área no chão (disco ou anel), com queda, cintilação e trilhas. `Effects.lights(id, luzes...)`, até 8 por efeito. **Não muda o nível de luz do jogo, o spawn de mobs nem o crescimento de plantas.**
- **Passe `lights`**: antes dos efeitos e da atmosfera; reconstrói a posição de cada pixel, estima a normal pela profundidade dos vizinhos (só faces voltadas para a fonte), não ilumina o céu e some com a névoa que o jogo usa no quadro. Teto de 8/24/64 luzes. A cintilação segue a intensidade dos efeitos de tela e cai para 1/4 com "ocultar clarões".
- **Orçamento dos passes do motor**: `beams` e `lights` contam como passes do efeito, uma vez por quadro: pagos pelo orçamento HERO quando um HERO desenhado os usa, senão pelo comum, antes dos cartões e de `post.pass`. Os dados vão em texturas RGBA32F (128 feixes em uniforms passariam do mínimo do OpenGL), ligadas com `Uniforms.glTexture` (conferido com `javap -c`).
- **F7**: "Protagonistas (HERO): N desenhadas · X/Y passes", "Feixes: N desenhados (máx. M) · passe HERO/comum/não" e "Luzes: …". O CSV ganha `heroes`, `heroPasses`, `beams`, `beamPass`, `lights` e `lightPass`.
- **`calamidade_fx.glsl` versão 2**: `caRaySegment`, `caBeamCore`, `caBeamHalo`, `caPulseTrain`, `caBeamTaper`, `caDepthFade`, `caNormalFromNeighbors`, `caPointLight`, `caSegmentLight`, `caGroundLight` e `caLitColor`; 106 casos no WebGL2 (`scripts/testar_glsl.cjs`).
- **`scripts/previa_passes.cjs`**: roda os passes `beams` e `lights` reais (com o `common.glsl` da RimuLib) sobre uma cena sintética com profundidade e grava PNGs. A prévia achou e corrigiu, antes do jogo, anéis falsos na ondulação de feixes vistos de frente, um halo forte demais e uma fogueira quase invisível de noite.
- **Kit para IAs**: Javadoc completo, referência gerada, AGENTS.md, skill e guia de GLSL atualizados; 6 receitas novas em `docs/ai/receitas/` (13 no total).
- **Exemplo 1.6.0**: receitas pilar, laser, teia, fogueira, onda_luz e protagonista; o Sinal de Maré ganha uma luz breve no impacto; o Núcleo do Horizonte não muda. Dependência `[0.9.0,0.10.0)`.
- **Compatibilidade.** Protocolo de rede igual ao da 0.7.0 e da 0.8.0 (event, end_effect, request_v2, release): feixes, luzes e prioridade são registrados nos dois lados e derivados do relógio da instância. `Effects.Definition` ganhou o componente `priority` e manteve o construtor da 0.8. O exemplo 1.5.0 compila sem mudança contra a 0.9.0, e as 128 referências ao motor do seu bytecode (compilado contra a 0.8.0) existem no JAR 0.9.0. Addons com intervalo `[0.8.0,0.9.0)` só precisam ampliá-lo.

# 0.8.0 — Atmosphere API e kit para IAs

- **Atmosphere** (`dev.calamidade_awakening.api.v1.Atmosphere`, docs/ATMOSFERA-0.8.md): objeto imutável, montado por builder, com céu (topo, horizonte e mistura), névoa por distância (cor, densidade, início e fim em blocos), exposição, tinta ambiente e estrelas procedurais. Todo parâmetro numérico pode vir de uma trilha da Timeline (`track(Param.FOG_DENSITY, "nevoa")`); o peso segue o envelope da instância, uma fase opcional (`weightPhase`) e uma trilha `WEIGHT`. `Atmosphere.mix` mistura as duas de maior peso por interpolação ponderada. Java puro, coberto pelo `AtmosphereCheck`.
- **Integração.** `Effects.atmosphere(id, atmosfera)` depois de `register` (e da timeline, se lê trilhas). O cliente resolve a atmosfera de toda instância ativa (sem frustum), com fade nos últimos 25% do alcance, e desenha um único passe `atmosphere` por quadro, depois dos passes de efeito e fora do orçamento de addons, só quando há atmosfera ativa. Céu por diferença sobre o céu estimado do jogo (sol, lua, nuvens e efeitos preservados), estrelas, névoa no horizonte, exposição e tinta.
- **Névoa pela névoa do jogo.** O NeoForge 21.1.267 oferece `ViewportEvent.ComputeFogColor` e `ViewportEvent.RenderFog`; o motor usa os dois (prioridade LOWEST, só sem fluido, cegueira ou escuridão, nunca mais rala que a do jogo), então terreno, entidades, partículas, água e nuvens recebem a névoa na distância real. Nova opção **"Névoa da atmosfera pela névoa do jogo"** (`vanillaFog`): desligada, o passe desenha a névoa pela profundidade, para shader packs que ignoram a névoa do jogo.
- **Acessibilidade.** Exposição e tinta seguem a intensidade dos efeitos de tela; com "ocultar clarões", a exposição muda no máximo 0,025 por tick. Novos `Post.screenEffects()` e `Post.motionEffects()` para passes próprios.
- **F7:** "Atmosfera: N ativas · passe sim/não"; o CSV ganha `atmospheres` e `atmospherePass`.
- **Biblioteca GLSL** `calamidade_fx.glsl` (via `#include` da RimuLib, conferido com `javap -c`): raio-caixa, raio-esfera, brilho de segmento com largura mínima por pixel, grades sem moiré, arco-íris, envelope, onda por distância e estrelas. 55 testes executados numa GPU (`scripts/testar_glsl.cjs`).
- **Kit para IAs.** `AGENTS.md` e `CLAUDE.md` na raiz; skill de projeto `.claude/skills/calamidade-efeitos`; `docs/ai/` com a referência da API gerada do JAR (`API-REFERENCIA.md`), o guia de GLSL e 7 receitas que são efeitos reais do exemplo (`/vfxsample receita <nome>`); `scripts/novo_efeito.py` gera o esqueleto de um efeito que compila sem edição. Javadoc revisado em toda a api.v1.
- **Verificações novas no build:** `checkShaders` (glslangValidator em todo shader fx do motor e do exemplo, montado como a RimuLib monta), `checkApiDocs` (referência atualizada e todo membro público com Javadoc) e `checkRecipes` (código das receitas igual ao do exemplo) e `checkModVersion` (versão do manifesto do motor igual à do build e aceita pelo exemplo).
- **Correção:** o `neoforge.mods.toml` do motor ainda declarava `0.7.0`; com isso o exemplo 1.5.0 (que exige `[0.8.0,0.9.0)`) era recusado no carregamento. O teste em jogo automatizado achou o problema; agora o manifesto declara `0.8.0` e o `checkModVersion` impede a divergência.
- **RimuLib fora do repositório.** O JAR deixou de ser versionado e de ser anexado a releases; o build o baixa do CurseForge e confere o SHA-256. O release v0.7.0 foi limpo.
- **Exemplo 1.5.0.** As 7 receitas; atmosfera curta e dourada no impacto do Sinal de Maré; névoa âmbar no sustain do Farol (trilha `nevoa`). Dependência `[0.8.0,0.9.0)`.
- **Compatibilidade.** O protocolo de rede é o da 0.7.0 (canais event, end_effect, request_v2 e release); a atmosfera não envia nada pela rede. Efeitos sem atmosfera se comportam como na 0.7. O exemplo 1.4.0 compila sem mudança contra a 0.8.0, e as 107 referências ao motor do seu bytecode (compilado contra a 0.7.0) existem no JAR 0.8.0. Addons com intervalo `[0.7.0,0.8.0)` só precisam ampliá-lo.

# 0.7.0 — Timeline API

- **Timeline** (`dev.calamidade_awakening.api.v1.Timeline`, docs/TIMELINE-0.7.md): objeto imutável, montado por builder, com fases nomeadas (`weight` e `progress` com entrada e saída suaves), trilhas de valores com keyframes e easings `LINEAR`, `SMOOTH`, `EASE_IN`, `EASE_OUT`, `EASE_IN_OUT` e `OVERSHOOT`, marcadores e sustain com release. `localAge` converte idade real em idade da timeline. Sem tipos do Minecraft.
- **Integração.** `Effects.timeline(id, timeline)` entre o `register` e os cues; `Effects.cue(id, "marcador", cue)`; `Context` ganha `timeline()`, `localAge()` e `age()`. Cues dentro do loop disparam a cada volta.
- **Sustain e release na rede.** `Handle.release()` (método default, não quebra implementações). O servidor guarda a idade de liberação e a envia no novo pacote `release`, inclusive a quem entra depois. `stop()` continua encerrando na hora. Com sustain, a instância só expira depois do fim do release. **Servidor e clientes precisam estar na mesma versão.**
- **Cliente.** Amostrador da timeline em `Time`, `World`, `Hud` e `Post` (fase, trilha, marcador e idade local). `Post.age()` passa a ser a idade local (igual à real sem sustain) e `Post.realAge()` a real. `Post.uploadTracks(u, prefixo)` envia cada trilha como uniform float. `Film` recebe a timeline (`film.timeline().start("fase")`).
- **Efeitos de tela.** `Post.aberration`, `zoomBlur`, `vignette(argb, f)` (via `addVignette`), `afterimage` e `grain`, combinados pelo máximo e escalados pela intensidade configurada. Nova opção **Efeitos de movimento** (`motionEffects`, padrão ligado) desliga zoom blur e afterimage.
- **Arrays de uniforms.** Conferido com `javap -c`: nomes como `"Pontos[3]"` chegam inalterados ao `glGetUniformLocation`, e `vec4s` envia arrays de vec4 numa chamada. Registrado em docs/AVANCADO-0.6.md.
- **Limpeza.** Saiu o atributo obsoleto `bus = EventBusSubscriber.Bus.MOD`; o FML já escolhe o barramento pelo tipo do evento.
- **Exemplo 1.4.0.** Sinal de Maré com fases `formacao`, `impacto` e `eco`, trilha `intensidade`, marcador `impacto` no tick 40 ligado ao dano, zoom blur e aberração no impacto e câmera alinhada às fases. Novo `vfxsample:farol`, feixe em loop: `/vfxsample farol start`, `release` e `stop`.
- **Compatibilidade.** Efeitos sem timeline se comportam como na 0.6. O exemplo 1.3.1 da 0.6 compila sem mudança contra a 0.7.0, e o seu bytecode compilado contra a 0.6.2 resolve todas as referências no JAR 0.7.0. Addons com intervalo `[0.6.x,0.7.0)` só precisam ampliá-lo.

# 0.6.2 — prioridade, alcance, duração e acessibilidade

- **Post sempre chamado.** O callback de `AddonLayers.post` roda para toda instância visível, mesmo quando o orçamento de passes acabou; `pass` retorna false, mas os ajustes de tela valem. O shader de cartão também deixou de interromper o laço.
- **Alcance por definição.** `Effects.Definition(...).withRange(blocos)`, de 16 a 1024 (padrão 128). O servidor distribui e o cliente seleciona pelo alcance de cada efeito.
- **Duração e raio.** Efeitos de addon vão até 12000 ticks (10 min) e raio 512. Acima de 1200 ticks, as rampas automáticas ficam em 168 ticks de entrada e 336 de saída; abaixo disso, nada muda.
- **Acessibilidade centralizada.** `calamidade_awakening-client.toml` (e tela na lista de mods): intensidade dos efeitos de tela (0 a 1) e tremor ligado/desligado. Vale para o motor e para todos os addons pelos métodos de `Post`.
- Testes novos em `checkBudget`: alcance por candidato, envelope longo e contínuo, validação de alcance e duração.

# 0.6.1 — correções da revisão

- **Orçamento por passe.** `AddonLayers.Post` não expõe mais o frame cru da RimuLib. `post.pass(...)` consome uma vaga do orçamento do quadro (1/2/4) a cada chamada e recusa passes além dele. `post.passesLeft()` informa quanto resta. Shaders de cartão e posts dividem o orçamento na ordem de prioridade dos efeitos, num único laço.
- **Efeitos de tela.** Métodos `bloom`, `shake`, `desaturate`, `contrast` e `flash`, que combinam pelo máximo. `flash` respeita a opção de ocultar clarões.
- **F7 com contagem real.** O painel soma os passes realmente enfileirados, inclusive os pedidos antes de uma falha.
- **Cues após cancelamento.** Se um cue chama `ctx.handle().stop()`, nenhum cue posterior da mesma instância roda, nem no mesmo tick.
- Exemplo 1.3.1 adaptado: o passe de anéis só é pedido a partir do tick 36.

# 0.6.0

## Ganchos avançados para addons (docs/AVANCADO-0.6.md)
- `AddonLayers.post(id, post -> ...)`: entrega o `FxApi.Frame` da RimuLib por instância, com centro, idade, tempo, qualidade e preferência de clarões. Permite uniforms próprios, vários passes por fase e ajuste do pós-processamento. Usa as mesmas vagas de orçamento dos shaders de cartão.
- `AddonLayers.film(id, film -> shots)`: câmera própria com tomadas da RimuLib, substituindo a lista linear do cartão. O motor continua cuidando do pular, do cancelar e do encerramento.
- `AddonLayers.start(id, start -> ...)`: evento de início em todos os clientes, inclusive entradas tardias.
- Falhas nesses callbacks são registradas no log sem travar o quadro.

## Exemplo
- Sinal de Maré 1.3.0: passe `signal_ripple` com uniforms próprios, câmera com corte e travelling, e som para quem observa.
- O exemplo agora compila contra a RimuLib (usa `FilmApi` e o frame do `FxApi`) e a declara no manifesto.

# 0.5.0

### Gameplay pelos addons (cues de servidor)
- `Effects.cue(id, tick, cue)` e `Effects.onImpact(id, cue)`: funções que rodam no servidor no tick da instância, para dano, empurrão e outras consequências sincronizadas com o visual.
- `Effects.Context` traz mundo, centro, conjurador, âncora, seed, handle e `living(raio)`.
- O Sinal de Maré (vfx-sample 1.2.0) causa dano leve e empurra as criaturas no impacto.
- Guia: docs/GAMEPLAY-0.5.md.

### Ferramentas para GPU real e multiplayer
- F7: painel com GPU, FPS, mediana e p95 do tempo de quadro, eventos, vértices e passes.
- `/calamidade_client record <s>`: grava os quadros em CSV.
- `/calamidade bench <n> [efeito]`, `/calamidade bench stop`, `/calamidade status`.
- Roteiro de teste: docs/TESTE-REAL.md.

### Recarga por habilidade
- A recarga passa a ser por jogador **e** por habilidade (classe `Cooldowns`, coberta pelo `checkBudget`).

### Atlas
- Os títulos encolhem para caber no cartão e, se ainda não couberem, são cortados com reticências.
- A órbita decorativa ficou tracejada e mais discreta, e aparece junto com a abertura.

### Documentação
- O manual é gerado por `scripts/gerar_manual.py` a partir dos capítulos em docs/; não o edite à mão.
- O capítulo 1 agora está em português (docs/JOGAR.md); o README continua em inglês.
- Versões corrigidas: JAR 0.5.0 e exemplo 1.2.0, gerados pelo build.
- O roadmap está em ordem; "ferramentas criativas" virou 0.6.
- O estudo da SA Series saiu do manual e virou leitura complementar.

### Compatibilidade
- O protocolo de rede não mudou. Addons da 0.4 só precisam ampliar o intervalo de dependência para incluir 0.5.
