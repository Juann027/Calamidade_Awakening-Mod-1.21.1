# Histórico e evolução prevista

## Versões publicadas

### 0.1 — base do motor

- Catálogo de habilidades, menu e transporte compartilhados.
- Timeline de cliente, deduplicação e seleção por prioridade.
- Primitivas geométricas, volume, lente, cena orbital e cinematográfica.
- Cinco habilidades visuais e regulagem de qualidade.
- JAR distribuído e testes isolados de geometria. Consulte VALIDACAO.md para a cobertura da versão.

### 0.2.0 — Atlas e Horizon Core

- Abertura e foco do Atlas com seleção durante o movimento.
- Horizon Core com câmera, áudio posicional e onda em superfícies.
- Pulso Prismático com início rápido e recarga reduzida.

### 0.3.0 — integração externa

- API pública com registro por namespace, âncoras de posição e entidade, cancelamento e sincronização.
- Acento de impacto configurável em ticks, com clarão colorido ou dessaturação e contraste.
- Projeto consumidor separado e guia API-0.3.md com exemplo de registro e comandos de teste.

### 0.4.0 — addons de habilidades

- Cartões publicados pelo addon no Atlas paginado e no conjunto do Focus.
- Potência configurável com limites definidos e aplicados pelo servidor.
- Shader no namespace do addon, malha por instância, HUD, painel e tomadas de câmera.
- Exemplo completo Sinal de Maré. Consulte ADDONS-0.4.md.

### 0.5.0 — gameplay e diagnóstico

- Cues de servidor (`Effects.cue`, `Effects.onImpact`) para dano, empurrão e outras consequências no mesmo tick do visual. Consulte GAMEPLAY-0.5.md.
- Recarga por jogador e por habilidade, em vez de uma recarga única por jogador.
- Painel de diagnóstico (F7), gravação de tempos de quadro em CSV e comando `/calamidade bench` para carga reproduzível. Consulte TESTE-REAL.md.
- Atlas: títulos ajustados ao cartão e órbita decorativa mais discreta.
- O Sinal de Maré passa a demonstrar gameplay no impacto.

### 0.6.0 — ganchos avançados para addons

- `AddonLayers.post`: frame da RimuLib por instância, com uniforms próprios, vários passes e pós-processamento por fase, dentro do orçamento de qualidade.
- `AddonLayers.film`: câmera própria com tomadas da RimuLib, controlada pelo motor (pular, cancelar, encerrar).
- `AddonLayers.start`: evento de início em todos os clientes, inclusive entradas tardias.
- Sinal de Maré 1.3.0 demonstra os três ganchos. Consulte AVANCADO-0.6.md.

### 0.6.1 — correções de revisão

- `AddonLayers.Post` deixa de expor o frame cru: cada passe pedido por `post.pass(...)` consome o orçamento do quadro (1/2/4) e passes além dele são recusados. Shaders de cartão e posts dividem o orçamento na ordem de prioridade dos efeitos.
- Efeitos de tela por métodos que combinam pelo máximo (`bloom`, `shake`, `desaturate`, `contrast`, `flash`); `flash` respeita a opção de ocultar clarões.
- O painel F7 conta os passes realmente enfileirados.
- Um cue que encerra a própria instância impede os cues seguintes no mesmo tick.

### 0.6.2 — revisão de prioridade, alcance, duração e acessibilidade

- O callback `AddonLayers.post` é chamado mesmo sem vaga no orçamento; só os passes são recusados, e os ajustes de tela continuam valendo.
- Alcance de distribuição por definição (`withRange`, 16 a 1024 blocos; padrão 128).
- Duração até 12000 ticks e raio até 512 blocos para efeitos de addon; rampas automáticas limitadas em ticks acima de 1200.
- Configuração de cliente centralizada: intensidade dos efeitos de tela e tremor, aplicadas ao motor e aos addons, editáveis na lista de mods.

### 0.7.0 — Timeline API

- `Timeline`: fases com peso suave, trilhas com keyframes e seis easings, marcadores e sustain com release. Java puro, validado e coberto pelo checkBudget. Consulte TIMELINE-0.7.md.
- `Effects.timeline`, cues em marcadores, `Handle.release()` e o pacote release, enviado também a quem entra tarde. Servidor e cliente usam a idade local.
- Amostrador da timeline em `Time`, `World`, `Hud` e `Post`; `Post.uploadTracks`; `Film` recebe a timeline para alinhar cortes às fases.
- `Post.aberration`, `zoomBlur`, `vignette`, `afterimage` e `grain`; opção de cliente "Efeitos de movimento".
- Conclusão sobre arrays de uniforms registrada em AVANCADO-0.6.md; fim do atributo obsoleto `EventBusSubscriber.Bus`.
- vfx-sample 1.4.0: Sinal de Maré roteirizado pela timeline e o Farol, efeito ambiente em loop.

### 0.8.0 — Atmosphere API e kit para IAs

- `Atmosphere`: céu (gradiente e mistura), névoa por distância, exposição, tinta ambiente e estrelas, com parâmetros vindos de trilhas da Timeline e peso pelo envelope. Mistura das duas de maior peso. Consulte ATMOSFERA-0.8.md.
- Um passe `atmosphere` por quadro, fora do orçamento de addons; névoa pela névoa do jogo (eventos do NeoForge) com plano B na tela; acessibilidade de exposição e tinta; linha nova no F7.
- Biblioteca GLSL `calamidade_fx.glsl` por `#include`, testada numa GPU.
- Kit para IAs: AGENTS.md, skill de projeto, referência da API gerada do JAR, guia de GLSL, 7 receitas executáveis e `scripts/novo_efeito.py`.
- Verificações de build: `checkShaders`, `checkApiDocs`, `checkRecipes`. RimuLib baixada do CurseForge com SHA-256, fora do repositório e dos releases.
- vfx-sample 1.5.0: receitas, céu dourado no impacto do Sinal de Maré e névoa no Farol.

### 0.9.0 — Beam API, Light API e prioridade protagonista

- `Effects.Priority.HERO` (`withPriority`): o efeito principal da cena entra antes dos NORMAL, nunca é cortado pelo limite de desenhados (no máximo 2 HERO por quadro, os mais próximos) e tem orçamento de passes próprio (1/2/3). Consulte PRIORIDADE-0.9.md.
- `Beam` e `Point`: feixes entre pontos (centro, mundo, âncora, conjurador, olhos, mira) com núcleo e halo, pulsos, ondulação, ruído e revelação, por trilhas da Timeline; um passe `beams` para todos os feixes do quadro (16/48/128), com largura mínima por pixel e oclusão. Consulte FEIXES-0.9.md.
- `Light`: luz visual pontual, de segmento (ou seguindo um feixe), de disco ou de anel no chão, com cintilação; um passe `lights` antes dos efeitos e da atmosfera (8/24/64), com normal pela profundidade e a névoa do jogo. Não muda o nível de luz do jogo. Consulte LUZ-0.9.md.
- `calamidade_fx.glsl` versão 2 (11 funções novas, 106 casos no WebGL2) e `scripts/previa_passes.cjs` (prévia dos passes sem o jogo).
- F7 e CSV com HERO, feixes e luzes.
- vfx-sample 1.6.0: receitas pilar, laser, teia, fogueira, onda de luz e protagonista; luz breve no impacto do Sinal de Maré.

### 0.10.0 — integração com outros mods

- Orientação por instância: `Placement` (posição ou âncora, yaw, pitch, `facing`, `arrivingFrom`, `shifted`, `followingBody`) e `Effects.start`/`follow` com orientação; pontos do centro, `Point.facing`, deslocamentos de trilha e o deslocamento da âncora giram com a instância; o yaw pode seguir o corpo da âncora. Pacote `event_v2`. Consulte INTEGRACAO-0.10.md.
- Fontes (`EffectSource`: jogador, mob, bloco, script), limites por fonte e vagas reservadas para jogadores na config do servidor; recarga por fonte e efeito; F7 e `/calamidade status` por tipo de fonte.
- `EffectTriggers`: gatilhos de item (uso, ataque, recarga, durabilidade), de block entity (PERIODIC, REDSTONE, PROXIMITY, fim ao quebrar ou descarregar) e de entidade (corpo ou alvo), só com API comum; guia de ligação com keyframes de GeckoLib.
- Consumo como biblioteca sem Maven: modelo de `build.gradle` (release do GitHub com SHA-256) e `examples/consumidor`, conferidos pelo build; conferida com o NeoForge 21.1.265.
- vfx-sample 1.7.0: leque orientado, cajado, pilares de redstone e periódico, zumbi com feixe.

### 0.11.0 — Terrain API

- Formas de escavação (`TerrainShape`: esfera, cratera com perfil, trincheira, cilindro, união e subtração) com borda irregular por ruído determinístico da seed da instância, viradas pela `Placement`. Consulte TERRENO-0.11.md.
- Paleta de queimado por faixas (`TerrainPalette`: fundo, parede, borda) numa casca que veda fluidos e queima plantas; preenchimento real no centro (`Fill`) e estruturas `.nbt` giradas em 90°.
- Plano puro numa thread de trabalho (sem ler o mundo) e commit no marcador da Timeline, escrevendo direto nas seções dos chunks com orçamento de blocos por tick, sem drops, vizinhos nem física; reenvio por seção no fim de cada lote. Decisão do Passo 0: a `CarveApi` da RimuLib não é usada (cortes só de coluna, orçamento de tempo compartilhado, sem desfazer).
- Segurança na config do servidor (liga e desliga, volume, blocos por tick, dimensões, mobGriefing, BlockEntity só com opção explícita, tag `terrain_protected`), `TerrainEditEvent` cancelável e `/calamidade terrain undo|list` com snapshot compacto.
- Cliente: passe `terrain` com o calor (brasa, fissuras, fumaça, tremor de calor, faixa incandescente nas trincheiras) calculado da mesma forma analítica e seed, luzes que esfriam, dublês esféricos e malhas de estrutura em cache; canal novo `terrain_v1`.
- vfx-sample 1.8.0: raio que abre trincheira, meteoro que vira blocos reais, cratera simples e torre que cai e fica.

### 0.11.1 — compatibilidade da Terrain API

- Proteção territorial pelos eventos de explosão do NeoForge (`Start` e `Detonate` com uma explosão sintética), borda do mundo, proteção de spawn e modo de jogo do jogador; config `terrainExplosionEvent` e `terrainSpawnProtection`.
- Casca externa: atualização de forma, de vizinho e ticks de fluido nos blocos logo fora da edição (commit e desfazer), sem cascata; dados de block entity reenviados ao cliente.
- Compatível com ScalableLux (correção), C2ME, Sodium, Embeddium e Distant Horizons; Moonrise não abre com a RimuLib 1.3.0.
- Testes em jogo no repositório: `./gradlew testeJogo` (servidor dedicado, 51 verificações, com reinício) e `xvfb-run ./gradlew testeCliente` (10 verificações). Consulte TERRENO-0.11.md, seção "Compatibilidade (0.11.1)".

### 0.11.2 — perfis medidos, qualidade automática e Atlas no mundo

- Medição de custo por efeito e por passe no próprio jogo (`/calamidade_client debug measure`, perfil de teste UNLIMITED, `bench <n> mix`), por software e na RTX 4060 do autor; perfis novos (LOW igual, BALANCED 8, HIGH 16, ULTRA 32 desenhados) com limites no config de cliente.
- Qualidade automática AUTO (padrão): p95 do quadro, histerese, nunca durante um efeito HERO; F8 força um perfil, F7 mostra o motivo.
- Atlas Estelar refeito como placas paradas no mundo (engenharia adaptada do menu holográfico do RespectCraft), visual de astrolábio, abas de habilidades, qualidade, acessibilidade, terreno e diagnóstico. Consulte ATLAS-0.11.2.md.
- Testes em jogo novos: `testeMedicao` e `testeAtlas`.

## Medições pendentes

As ferramentas da 0.5.0 existem para fechar estes itens. O roteiro está em TESTE-REAL.md.

- Custo absoluto por efeito e por passe na RTX 4060 com VSync desligado (a medição da 0.11.2 estava presa a 144 Hz) e numa placa integrada (perfil LOW); AUTO subindo numa GPU física.
- Sincronização entre dois clientes em servidor dedicado: observador, entrada tardia, saída e retorno ao alcance, troca de dimensão.
- Pipeline em Fast, Fancy, Fabulous e com mods de renderização (Sodium/Embeddium, Iris/Oculus).
- Aparência dos feixes e da luz numa GPU física (de dia, de noite, debaixo d'água) e custo dos passes `beams` e `lights` com o teto de HIGH (128 feixes, 64 luzes).
- Gatilhos e limites por fonte com dois jogadores num servidor dedicado (TESTE-REAL.md, parte 5) e o RespectCraft rodando o Finisher Engine com efeitos do Calamidade.
- Terrain API com mods de proteção reais (FTB Chunks, Open Parties and Claims, GriefDefender), com jogadores reais na proteção de spawn e no modo aventura, o LOD do Distant Horizons num servidor sem DH e o custo da remontagem numa GPU física (TESTE-REAL.md, partes 6 e 7).

## Próximas versões

### 0.12 — Bosses

Próxima versão planejada.

- **Pontos presos a ossos:** `Point` resolvido pelo cliente a partir de ossos de rigs (GeckoLib ou outro) por um gancho registrado pelo mod do rig, sem o Calamidade depender do GeckoLib; feixes, luzes e dublês saindo da mão ou da boca do chefe.
- **BossBar API:** barra do chefe sincronizada às fases da Timeline (nome do golpe, fase, vulnerabilidade), com estilo próprio e acessibilidade de tamanho e contraste.
- **Qualidade automática:** o cliente ajusta LOW/BALANCED/HIGH pelo tempo de quadro medido (com histerese), respeitando o perfil escolhido como teto.

### Depois da 0.12

- Trail API (rastros e marcas): fitas com histórico de posições para entidades, projéteis e pontas de feixe; marcas que ficam no chão por um tempo.
- Títulos e HUD cinematográficos sincronizados à Timeline.
- Ramificações de feixes (raios que se dividem) e feixes curvos.
- Sombras simples para a Light (oclusão por profundidade na direção da fonte).
- Terrain API: edições em cadeia por trilha (um rasgo que cresce ao longo de vários ticks junto com o raio) e paletas por bioma.

### Evolução da API, sem versão definida

- Parâmetros de habilidade via arquivos de dados.
- Editor de sequências e previsão de custo.
- Trilha de áudio sincronizada e poses ligadas às fases da Timeline.
- Dados por instância enviados pelo servidor ao cliente (por exemplo, lista de alvos, força de um golpe) para os ganchos de cliente.
- Orientação seguindo o olhar (yaw e pitch da cabeça) além do corpo.
- Transição suave (crossfade) opcional no release, para loops cujas bordas não coincidem.
- Cache de malhas estáticas e LOD adaptativo ao frametime.
