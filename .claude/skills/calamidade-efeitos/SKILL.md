---
name: calamidade-efeitos
description: Cria ou altera efeitos visuais do Calamidade_Awakening (motor de VFX para Minecraft 1.21.1, NeoForge, RimuLib) e de seus addons — definição, prioridade HERO, Timeline, Atmosphere (céu, névoa, exposição), Beam (feixes), Light (luz no mundo), Terrain API (crateras, trincheiras, borda queimada, meteoro que vira bloco, estrutura que cai), passes de pós-processamento com shader GLSL, malha de mundo, câmera, cues de gameplay no servidor e testes com F7 e bench. Use quando pedirem um efeito, habilidade, ataque, shader, feixe, anel, bolha, impact frame, céu, névoa, cratera ou destruição de terreno para o Calamidade, ou um addon que use a API dev.calamidade_awakening.api.v1.
---

# Criar efeitos no Calamidade_Awakening

Antes de tudo, leia `AGENTS.md` (regras invioláveis: API pública, rede, licenças, javap, git status). Esta skill é o fluxo de trabalho; as assinaturas exatas estão em `docs/ai/API-REFERENCIA.md` (gerada do JAR) e os exemplos completos em `docs/ai/receitas/`.

## Quando usar

- Um efeito novo num addon (ou no `examples/vfx-sample`).
- Mudar o roteiro, o visual, a câmera ou o gameplay de um efeito existente.
- Dar clima a uma luta ou área (céu, névoa, escuridão) com a Atmosphere API.
- Disparar um efeito de um item, de um bloco (block entity) ou do ataque de um mob, ou virá-lo para uma direção (0.10.0).
- Usar o Calamidade a partir de outro mod (o RespectCraft, por exemplo): dependência sem Maven e integração com rigs animados.

Não use para mudar o próprio motor (renderização interna, rede, registro): aí siga `docs/ARQUITETURA.md` e `docs/HABILIDADES.md`.

## Fluxo

Siga na ordem. Cada passo aponta para o capítulo e a receita que mostram o código.

### 1. Definição

Comece do esqueleto: `python3 scripts/novo_efeito.py <namespace> <nome> --projeto <addon> [--cartao]` (compila sem edição, registra-se sozinho em `FMLConstructModEvent`, comando `/<namespace> efeito <nome>`). Ou copie a receita mais parecida.

- `Effects.Definition(ticks, raio, argb, Shape, Material, partícula, som[, Impact])`, `.withRange(blocos)`.
- Protagonista (capítulo `docs/PRIORIDADE-0.9.md`, receita 13): `.withPriority(Effects.Priority.HERO)` só no efeito principal da cena (ataque de chefe, golpe final). Entra antes dos NORMAL, nunca é cortado pelo limite de desenhados (no máximo 2 HERO por quadro, os mais próximos) e tem orçamento de passes próprio (1/2/3). Não marque tudo como HERO: o terceiro HERO mais distante simplesmente não é desenhado.
- Raio: o teste de frustum usa 2,5 × raio em volta do centro. Cubra tudo o que o shader desenha (feixe alto, cubo grande), senão o efeito some quando o centro sai da tela.
- Capítulos: `docs/API-0.3.md`, `docs/ADDONS-0.4.md` (cartão). Receitas: todas.

### 2. Timeline

- `Timeline.builder(duração).phase(nome, início, fim, subida, queda).track(nome, keyframe(...)...).marker(nome, tick).build()`; `Effects.timeline(id, timeline)` logo depois do `register`.
- Fases para "quando" (pesos suaves 0..1), trilhas para "quanto" (valores com easing), marcadores para gameplay e tempo relativo (`t.since("impacto")`).
- Ambiente: `sustain(início, fim)` + `Handle.release()`. Trilhas iguais nas bordas do loop e no início da saída; movimento com período igual ao loop.
- Capítulo: `docs/TIMELINE-0.7.md`. Receitas: 1 (marcador), 2 e 3 (trilhas com OVERSHOOT), 6 (sustain).

### 3. Atmosfera (opcional)

- `Atmosphere.builder().sky(topo, horizonte, mistura).fog(cor, densidade, início, fim).exposure(x).ambient(cor, força).stars(x)`; `.track(Param.FOG_DENSITY, "trilha")`, `.weightPhase("fase")`; `Effects.atmosphere(id, atmosfera)` depois da timeline.
- Não precisa de shader nem de camada de cliente. Peso = envelope × fase × trilha WEIGHT × fade no fim do alcance; o motor mistura as duas mais fortes.
- Capítulo: `docs/ATMOSFERA-0.8.md`. Receita: 7. Exemplo: Sinal de Maré (céu no impacto) e Farol (névoa no sustain).

### 3b. Feixes (opcional, 0.9.0)

- `Beam.builder(Point.center(0, 0, 0), Point.center(0, 60, 0)).width(núcleo, halo).color(núcleo, halo).pulses(n, velocidade, tamanho).wave(amplitude, onda, velocidade).noise(x).reveal(0..1)`; `.track(Beam.Param.REVEAL, "trilha")`, `.weightPhase("fase")`; `Effects.beams(id, feixes...)` depois da timeline (até 16).
- Pontas (`Point`): `center(dx,dy,dz)` (centro da instância), `world(x,y,z)`, `anchor`/`anchorEye`/`anchorAim(alcance)` (entidade de `Effects.follow`), `caster`/`casterEye`/`casterAim(alcance)` (quem conjurou). Sem a entidade carregada no cliente, o feixe não é desenhado.
- Sem shader nem camada de cliente: o motor desenha todos os feixes num passe `beams`, com largura mínima de um pixel e oclusão pelo terreno. O raio da definição cobre o feixe (teste de frustum).
- Capítulo: `docs/FEIXES-0.9.md`. Receitas: 8 (pilar), 9 (laser), 10 (teia), 13 (protagonista).

### 3c. Luz no mundo (opcional, 0.9.0)

- `Light.point(Point.center(0, .6, 0)).color(argb).intensity(x).radius(blocos).falloff(1..2).flicker(quantidade, velocidade)`; `Light.segment(a, b)`; `Light.along(feixe)` (segue um feixe do mesmo efeito: pontas, revelação, peso e cor do halo); `Light.disc(centro, raio)` e `Light.ring(centro, raio, largura).height(blocos)` para o chão (ondas de choque). `.track(Light.Param.RADIUS, "onda")`, `.weightPhase("fase")`. `Effects.lights(id, luzes...)` depois de `Effects.beams` (até 8).
- Luz **visual**: clareia o que a câmera vê, com normal pela profundidade (só faces voltadas para a fonte), sem céu, sumindo com a névoa do jogo. Não muda o nível de luz, o spawn de mobs nem plantas; não faz sombra.
- Calibre de noite: intensidade 2 a 3, alcance 8 a 12, queda 1,2 a 2. Cintilação some com intensidade dos efeitos de tela 0 e cai para 1/4 com "ocultar clarões".
- Capítulo: `docs/LUZ-0.9.md`. Receitas: 8 (pilar), 11 (fogueira), 12 (onda de luz).

### 3d. Orientação, fontes e gatilhos (opcional, 0.10.0)

- **Virar o efeito:** `Effects.start(id, level, posição, yaw, pitch)` ou `Effects.start(id, level, Placement...)`. `Placement.at(pos).withLookOf(jogador)`, `.facing(alvo)`, `.arrivingFrom(atacante.getEyePosition())` (direção do golpe), `.shifted(direita, cima, frente)`, `Placement.on(mob).followingBody()` (yaw segue o corpo). Escreva pontos com `Point.facing(direita, cima, frente)`: giram com a instância. Sem orientação, tudo fica como na 0.9 (frente = sul).
- **Referencial:** eixos locais do Minecraft (x esquerda, y cima, z frente); yaw 0 = sul, pitch positivo olha para baixo. Giram: pontos do centro, deslocamentos de trilha, offset da âncora, `ctx.facing`/`ctx.forward()` nos cues, `post.facing`/`world.facing` nas camadas. Não giram: `Point.world`, offsets de pontos de entidade, discos e anéis de luz (só o centro), `Shape`/`Material` embutidos.
- **Gatilhos (`EffectTriggers`, `static final`):** `item(id).offset(.3, -.25, .4).cooldown(30).durability(1)` com `use(...)` no `Item#use` e `attack(...)` no `hurtEnemy` (pontos EYES, CASTER, AIM, TARGET); `blockEntity(id).redstone().period(20)` / `.periodic(60)` / `.proximity(6)` com um `Component` por block entity e `tick()` no ticker do servidor (a instância acaba ao quebrar o bloco ou descarregar o chunk); `entity(id).offset(0, 1.3, .4).cooldown(20)` com `fire(mob)` ou `fire(mob, alvo)`.
- **Fontes e limites:** toda instância tem `EffectSource` (PLAYER, ENTITY, BLOCK, SCRIPT). Padrão do servidor: 16 por jogador, 4 por mob, 2 por bloco, 16 das 64 vagas só para jogadores. Acima do limite, `Optional.empty()` sem erro: trate o vazio. Recarga por fonte e efeito: `Placement.withCooldown`, `Effects.cooldownReady/cooldownLeft/startCooldown`.
- **GeckoLib/rigs:** a animação roda no cliente, o efeito começa no servidor no mesmo tick do `triggerAnim`; alinhe marcadores da Timeline aos keyframes (segundos × 20). O Calamidade não depende de GeckoLib.
- Capítulo: `docs/INTEGRACAO-0.10.md`. Receitas: 14 (leque orientado), 15 (cajado), 16 (pilares), 17 (zumbi). Dependência sem Maven: `docs/ai/usar-como-dependencia.md`.

### 3e. Terreno (opcional, 0.11.0)

- `Effects.terrain(id, TerrainEdit.carve(forma).commitAt("marcador")...build())` depois de `register` e `timeline` (até 4 por efeito). Formas (`TerrainShape`, referencial da instância): `sphere(r)`, `crater(r, fundo).profile(2).clearance(h)`, `trench(comprimento, r)` (ao longo da frente: gira com o yaw), `cylinder(r, altura)`, `union`, `minus`, `offset(direita, cima, frente)` e, na forma final, `rough(amplitude, célula)`.
- Borda queimada: `.burn(TerrainPalette.burnt(), 1..4)` (ou um `TerrainPalette.builder()` com faixas FLOOR, WALL, RIM e pesos). Blocos reais no centro: `.fill(TerrainEdit.Fill.solid("minecraft:raw_gold_block", r).at(0, -3, 0))`. Estrutura: `.structure("ns:caminho")` (`data/ns/structure/caminho.nbt`, girada em 90° pelo yaw) e `.structureOffset(...)`.
- Dublê que cai: `.standIn(TerrainEdit.StandIn.sphere(r).from(direita, cima, frente).fall(tick).reveal(ticks).build())` ou `StandIn.structure()`; o motor desenha esfera, rastro, malha, poeira e luz. Calor: `.cooling(ticks)` ou `.heatTrack("trilha")`, `.glow`, `.smoke`, `.cracks`, `.noHeat()`.
- **Sob o impact frame:** ponha o marcador no tick do `Effects.Impact` da definição, com 4 a 8 ticks. O raio da definição cobre a queda e a cratera (frustum 2,5 × raio).
- Servidor: regras da config `terrain` (volume 150 000, 16 384 blocos por tick, dimensões, `mobGriefing`, block entities só com `.replacingBlockEntities()` e permissão), tag `calamidade_awakening:terrain_protected`, `TerrainEditEvent` cancelável, `/calamidade terrain undo|list`. Não troque blocos em massa num cue com `setBlock`: use a API.
- Capítulo: `docs/TERRENO-0.11.md`. Receitas: 18 (raio e trincheira), 19 (meteoro), 20 (cratera simples), 21 (torre que cai).

### 4. Pós-processamento e shader

- Cliente (`FMLClientSetupEvent`): `AddonLayers.post(id, post -> { var t = post.timeline(); post.pass("ns:programa", u -> {...}); post.bloom(...); })`.
- Shader em `assets/<ns>/shaders/fx/<programa>.fsh`. A RimuLib antepõe `#version 150` e `common.glsl`. Leia `DiffuseSampler`, some o efeito, escreva `fragColor`. Centro e posições são relativos à câmera (`post.center()`, `scenePosAt`).
- Biblioteca: `#include "calamidade_fx.glsl"` (copie o arquivo para o seu namespace): `caRayBox`, `caRaySphere`, `caSegmentGlow`, `caWorldGrid`, `caRainbow`, `caEnvelope`, `caDistanceWave`, `caStars`. Documentação: `docs/ai/glsl.md`.
- Trilhas como uniforms: `post.uploadTracks(u, "Track_")`.
- Capítulo: `docs/AVANCADO-0.6.md`. Receitas: 1 (profundidade), 2 (raio-caixa), 3 (raio-esfera), 4 (tela inteira), 5 (segmento).
- Malha de mundo em vez de shader: `AddonLayers.world` (`triangle`, `ribbon`; confira `remainingVertices()`). Receita: 6.

### 5. Câmera

- `AddonLayers.film(id, film -> List.of(FilmApi.cut(...), FilmApi.sweep(...)))`, com cortes em `film.timeline().start("fase")`. Só toca para o conjurador (`Effects.start(id, jogador, posição)` ou cartão).
- Capítulo: `docs/AVANCADO-0.6.md`. Exemplo: `SampleClient` (Sinal de Maré).

### 6. Cues (gameplay no servidor)

- `Effects.cue(id, "marcador", ctx -> ...)`: dano (`ctx.living(raio)`), empurrão, som, custo. Roda no mesmo tick do visual, uma vez por instância (a cada volta dentro de um loop).
- Valide permissões e PvP no cue. O motor é cosmético.
- Capítulo: `docs/GAMEPLAY-0.5.md`. Receitas: 1, 3, 4, 6.

### 7. Teste

1. `./gradlew build` (inclui `checkShaders`, `checkApiDocs`, `checkRecipes`), `./gradlew dist`, build do addon. Feixes e luzes: `node scripts/previa_passes.cjs` mostra os passes reais numa cena sintética antes do jogo.
2. No jogo: comando do efeito; **F7** (passes de pós, vértices, "Fontes", "Protagonistas (HERO)", "Feixes", "Luzes", "Terreno: zonas quentes · dublês · passe", "Malhas de dublê", "Atmosfera: N ativas · passe sim/não"); **F8** em LOW, BALANCED e HIGH; intensidade dos efeitos de tela em 0; "ocultar clarões" ligado; "Efeitos de movimento" desligado. Efeitos orientados: teste virado para vários lados; gatilhos: `/calamidade status` (ativas por fonte e recusas).
3. Carga: `/calamidade bench <n> <efeito>` e `/calamidade_client record 30` (CSV em `calamidade_bench/`).
4. Roteiro: `docs/TESTE-REAL.md`. Registre o que mediu em `docs/VALIDACAO.md`.

## Orçamento e limites

| Recurso | LOW | BALANCED | HIGH |
|---|---|---|---|
| Efeitos desenhados por quadro | 3 | 6 | 12 |
| Passes de addon (cartões + `post.pass`) | 1 | 2 | 4 |
| Passes de efeitos HERO (orçamento próprio) | 1 | 2 | 3 |
| Efeitos HERO desenhados (os mais próximos) | 2 | 2 | 2 |
| Vértices de malha | 12 000 | 48 000 | 96 000 |
| Passos de raymarch do motor | 8 | 14 | 22 |

- `post.pass` devolve `false` sem vaga; o callback roda mesmo assim, então bloom, tremor e clarão continuam valendo. Um shader de cartão já gasta uma vaga.
- Passe de atmosfera: 1 por quadro, do motor, fora do orçamento.
- Passe de feixes: 1 por quadro para todos os feixes (16/48/128), cobrado do orçamento HERO se houver efeito HERO com feixes, senão do comum.
- Passe de luzes: 1 por quadro para todas as luzes (8/24/64), antes dos efeitos e da atmosfera, mesmo esquema de orçamento. Os passes de feixes e luzes são pagos antes dos passes de addon; um efeito NORMAL com feixe e luz em LOW (1 vaga comum) só tem o feixe.
- Passe `terrain` (0.11.0): 1 por quadro para o calor e os dublês (2/4/8 zonas, 4/8/8 dublês), pago depois dos feixes e antes das luzes; malhas de estrutura 1/2/4 por quadro.
- Duração até 12000 ticks; raio até 512; alcance 16..1024; 64 instâncias por servidor (0.10: 16 por jogador, 4 por mob, 2 por bloco e 16 vagas reservadas para jogadores, configuráveis no servidor).
- Acessibilidade obrigatória: passes próprios de tela inteira × `post.screenEffects()`; nada de piscar com `!post.flashAllowed()`; nada de zoom ou borrão com `!post.motionEffects()`.

## Erros comuns

- Registrar fora da construção do mod (cartões são fechados no `RegisterEvent` de itens). Use o construtor do `@Mod` ou `FMLConstructModEvent`.
- Ordem errada: `cue` antes de `timeline`, ou atmosfera que lê trilha antes da timeline.
- Nome de fase ou trilha com maiúscula ou hífen (`[a-z0-9_]`).
- Grade ou padrão que "nada" com a câmera: use coordenadas relativas a um ponto fixo (o centro do efeito), não `scenePosAt` direto.
- `#include` de um arquivo que não está no namespace do shader (a RimuLib não procura no namespace do motor).
- Usar tipos da RimuLib ou do Minecraft sem conferir com `javap` (veja `AGENTS.md`).
- Escrever "frente" com `Point.center(0, 0, d)` esperando a direção do olhar sem passar orientação: sem `Placement`/yaw, a frente é o sul.
- Ignorar o `Optional` vazio de um `start` (limite por fonte, reserva, recarga): não é erro, é recusa.
- Começar efeitos ou dano a partir de keyframes de animação no cliente: o efeito começa no servidor.
- Terreno sem marcador no impact frame (a troca dos blocos fica visível) ou raio da definição menor que a queda do dublê (o dublê some quando o centro sai da tela).
- Esperar que a Terrain API carregue chunks: chunks sem ninguém perto são pulados (`/forceload` em testes).
