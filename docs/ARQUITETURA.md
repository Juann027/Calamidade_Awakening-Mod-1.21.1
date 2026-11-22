# Arquitetura do Calamidade_Awakening

```mermaid
flowchart TD
    A["Habilidade registrada"] --> B["Servidor: StageEvent"]
    B --> C["StageScene: timeline"]
    C --> D["FrameCoordinator: orçamento"]
    D --> E["MeshRenderer: geometria"]
    D --> F["RimuStageBackend: volumes"]
    B --> G["StageFilms: câmera"]
    B --> I["Servidor: cues de gameplay"]
    E --> H["Composição final"]
    F --> H
    G --> H
```

## Um conjunto de definições

VisualKind mantém um catálogo imutável de definições após a inicialização. Cada habilidade possui ID numérico estável, nome de recursos, título, cor, duração, raio, estilo de geometria, material de volume e opções de cinema e cena orbital. AwakeningSkills cria os itens do menu a partir do mesmo catálogo. O cliente e servidor devem usar a mesma versão.

## Servidor e rede

StageNetwork usa a API pública NetApi da RimuLib. CastRequest contém a chave com namespace da habilidade (por exemplo, `calamidade_awakening:prism`). O servidor valida esse ID, jogador, recarga (por jogador e por habilidade, em Cooldowns) e limite de sequências, calcula a origem com AimApi e emite StageEvent. A cada tick, antes de remover instâncias vencidas, o servidor executa os cues de gameplay agendados no relógio de cada instância.

| Campo | Finalidade |
|---|---|
| visual | Chave com namespace no pacote, convertida em ID local ao receber |
| order | Identidade e ordenação da sequência |
| tick | Relógio de início |
| caster | Identidade do jogador |
| dimension | Dimensão autoritativa |
| center | Centro mundial |
| seed | Aleatoriedade reproduzível |

O servidor mantém até 64 sequências ativas. Envia ao conjurador e observadores dentro do alcance da definição (128 blocos nos efeitos internos, 16 a 1024 nos de addons), inclusive quem entra durante a execução. O cliente deduplica por order, descarta eventos expirados e verifica a dimensão. Ele calcula progresso a partir do relógio de mundo, produzindo a mesma fase sem pacotes por quadro.

Desde a 0.7.0, um efeito de addon pode ter uma Timeline (TIMELINE-0.7.md). Servidor e cliente convertem a idade real em idade local com a mesma função pura (`Timeline.localAge`); cues, progresso, opacidade, acento de impacto e camadas de addon usam a idade local. Com sustain, a instância repete o trecho em loop e não expira pela duração. `Handle.release()` grava no servidor a idade de liberação, que vai aos clientes no pacote `release` (ReleaseEffect), também logo depois do StageEvent para quem entra mais tarde. A instância expira quando a saída termina. Os canais são event (event_v2 desde a 0.10.0), end_effect, release e request_v2; cliente e servidor precisam estar na mesma versão.

Desde a 0.8.0, um efeito de addon pode ter uma Atmosphere (ATMOSFERA-0.8.md), registrada nos dois lados como a Timeline e sem tráfego de rede. A cada quadro, `StageScene.atmospheres` resolve a atmosfera de toda instância ativa (sem frustum nem orçamento) na idade local, com peso pelo envelope e um fade no fim do alcance; `AtmosphereFrame` mistura as duas de maior peso, aplica a acessibilidade, alimenta os eventos de névoa do NeoForge (`ComputeFogColor` e `RenderFog`) e enfileira o passe `atmosphere` depois dos passes de efeito, fora do orçamento de addons. O shader do passe usa a biblioteca `calamidade_fx.glsl`, incluída pela RimuLib.

Desde a 0.9.0, a seleção (`FrameBudget.select`) põe as instâncias HERO na frente (no máximo 2, as mais próximas) e preenche o resto pelas NORMAL como antes; o orçamento de passes (`PassBudget`) tem dois montantes, comum e HERO. Efeitos de addon podem ter feixes (`Beam`) e luzes (`Light`), registrados nos dois lados e sem rede. A cada quadro, `BeamFrame` e `LightFrame` percorrem os efeitos desenhados em ordem de prioridade, resolvem os `Point` em `StageScene.point` (centro, mundo, âncora, conjurador, olhos ou mira, com um raio de mira por entidade e alcance), amostram as trilhas, aplicam o teto do quadro (`EnginePasses.cap`) e enviam os dados numa textura RGBA32F (`DataTexture`, ligada por `Uniforms.glTexture`). `RimuStageBackend` paga os dois passes (`EnginePasses.pay`: HERO se um HERO os usa, senão comum) antes dos passes de addon e os enfileira nesta ordem: `lights`, `awakening_field`, cartões e `post.pass`, `beams`, `atmosphere`. O passe de luzes recebe a névoa de terreno que o jogo usou no quadro (`ViewportEvent.RenderFog`, registrada depois de todos os ouvintes).

Desde a 0.10.0, uma instância tem orientação e fonte. `Placement` (ou as sobrecargas de `Effects.start`/`follow`) vira um `StageNetwork.Spawn` com posição ou âncora, yaw, pitch, a opção de seguir o corpo, a fonte (`EffectSource`), a recarga e o bloco vinculado. Antes de criar a instância, o servidor confere a recarga por fonte e efeito (`Cooldowns`, agora com chaves de qualquer fonte) e a admissão (`SourceLimits.admit`: total de 64, vagas reservadas para jogadores e limite por fonte, lidos de `ServerConfig`); recusas devolvem `Optional.empty()` e são contadas para o `/calamidade status`. O pacote de início passou a ser `event_v2`, com yaw, pitch, a opção de seguir o corpo e o tipo da fonte. O referencial é um `Basis` puro (eixos locais do Minecraft: x à esquerda, y para cima, z para a frente; identidade com yaw e pitch 0): o servidor o usa no centro das instâncias presas e no `Context` dos cues (com `yBodyRot` da âncora quando segue o corpo), e o cliente o calcula a cada quadro em `StageScene.basis` (com `Mth.rotLerp` de `yBodyRotO` e `yBodyRot`, como o renderizador), guarda-o no `Sample` e gira com ele os pontos do centro e os deslocamentos de trilha de feixes e luzes. Uma instância vinculada a uma block entity termina quando `BlockEntity.isRemoved()` (bloco quebrado ou trocado, chunk descarregado). Os gatilhos de `EffectTriggers` só montam `Placement`s; o de bloco decide com a máquina de estados pura `TriggerLogic`.

Desde a 0.11.0, um efeito pode ter edições de terreno (TERRENO-0.11.md). `Effects.terrain` registra `TerrainEdit`s nos dois lados e um cue do motor no marcador de cada uma. No servidor, `TerrainWorks.started` (chamado por `StageNetwork.start` antes do primeiro despacho) aplica as regras da config (`ServerConfig.terrain()`), carrega a estrutura e manda o `TerrainPlanner` (puro: forma, origem, orientação, seed) para uma thread de trabalho; quando o plano fica pronto, confere o volume, dispara o `TerrainEditEvent` e, desde a 0.11.1, chama o `TerrainGuard` (borda do mundo, proteção de spawn, modo de jogo do jogador e uma `Explosion` sintética nunca executada pelos `ExplosionEvent.Start` e `Detonate` do NeoForge), que devolve um bit por entrada do plano. O cue do marcador cria um `TerrainJob.Commit`, que `TerrainWorks.tick` (depois dos cues, no mesmo tick) avança com o orçamento de blocos por tick: o `TerrainWriter` escreve direto nas `LevelChunkSection` e, no fim de cada lote, reenvia cada seção alterada num `ClientboundSectionBlocksUpdatePacket` (desde a 0.11.1, seguido dos dados de cada block entity nova). Desde a 0.11.1, depois da última escrita, o `TerrainBorder` dá aos blocos logo fora da área escrita `updateShape`, `neighborChanged` e tick de fluido, uma vez, no mesmo orçamento. O commit guarda um `TerrainSnapshot` (índices e IDs de estado antes e depois) para `TerrainJob.Undo`. O estado de cada edição vai aos clientes pelo canal `terrain_v1` (`TerrainSync`: comitida, recusada, blocos do dublê de estrutura), uma vez por mudança e logo depois do StageEvent para quem chega depois. No cliente, `TerrainClient` guarda esses estados e prevê o commit no marcador; `TerrainFrame` monta o passe `terrain` (zonas quentes calculadas no shader pela mesma forma e seed, dublês esféricos) e as luzes do calor, pagos antes do passe de luzes; `TerrainMeshes` desenha a malha em cache das estruturas que caem em `RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKS`.

## Cliente e orçamento

StageScene mantém até 96 sequências residentes. Para cada quadro, FrameBudget calcula prioridade baseada em distância e intensidade. FrameCoordinator gera uma seleção que GeometryEffects e RimuStageBackend compartilham.

Os limites de Quality controlam número de efeitos, número de volumes, passos do shader, vértices, segmentos por primitiva e partículas por efeito. O orçamento de vértices é dividido entre efeitos de geometria no quadro. Efeitos distantes usam menos segmentos e partículas. A seleção testa os limites do efeito contra os planos do frustum antes de priorizar os candidatos. RenderFrameEvent.Pre e mudanças de estado invalidam a seleção para o próximo quadro.

## Geometria

MeshRenderer conecta a geometria a AFTER_PARTICLES. O renderer emite triângulos com posições relativas à câmera, usa teste de profundidade e blend aditivo, e salva/restaura os estados gráficos que altera. Um buffer é compartilhado pelos efeitos selecionados. O material energy é registrado pelo RegisterShadersEvent.

Mesh fornece triangles, quad, ring, billboard, ribbon, beam e sphere. Anéis, feixes e esferas reduzem a subdivisão antes de emitir os vértices. Se a forma mínima não couber, ela é omitida. GeometryEffects compõe essas primitivas com envelopes, rotação e amostragem determinística.

## Volumes, lente e cena orbital

RimuStageBackend registra uma única camada via FxApi. Não cria outro RenderTarget. Quando há volumes relevantes ou uma cena orbital, envia um passe com os parâmetros de até quatro marcas e número de passos do perfil.

O shader `assets/calamidade_awakening/shaders/fx/awakening_field.fsh` usa o ambiente da RimuLib:

- imagem e profundidade da cena;
- raio por pixel e reconstrução da posição;
- projeção de centro do efeito;
- interseção raio-esfera, limites de amostragem por profundidade;
- quatro materiais (cristal, véu refrativo, lente, nebulosa);
- composição de emissividade, deslocamento, aberração cromática e núcleo escuro;
- cena orbital procedural opcional com planeta, estrelas e nuvens.

Amostras volumétricas só são executadas para raios que atravessam o volume e segmento visível. Quando não há volumes nem cena, não há passe de campo. A camada pode contribuir ao bloom compartilhado quando só há geometria, e ao flash e tremor na fase de liberação, com atenuação por distância.

## Câmera

StageFilms utiliza FilmApi.follow, cut, sweep e play. As tomadas são calculadas com base no raio e duração da definição. Somente o conjurador local recebe a cinematográfica automática. O skip da RimuLib e a tecla O cancelam a câmera. A cena espacial usa FilmApi.weight e time, portanto sua contribuição termina quando a tomada perde controle da câmera.

## Dependência e recursos

A versão de referência usa RimuLib 1.3.0, NeoForge 21.1.267 e JDK 21. A RimuLib é instalada separadamente; a API pública é descrita em [docs.rimuru.cyou](https://docs.rimuru.cyou/rimulib/).

## Atlas Estelar

StarAtlas registra a posição e a orientação da câmera uma vez na abertura. Cartões, órbita e textos usam coordenadas de mundo e profundidades diferentes. A seleção cruza o raio da câmera com o plano de cada cartão e testa os blocos até o ponto alcançado. O clique envia CastRequest para o mesmo caminho de validação das teclas de habilidade. O servidor mantém o controle da recarga e da execução. Cada cliente vê seu próprio atlas.

## Horizon Core na 0.2.0

A sequência usa 200 ticks do evento do servidor. Durante a formação, quatro fitas espirais convergem para o centro. A compressão fecha as fitas e o volume em torno de uma fenda. A ruptura começa no tick 120. O filme segura o enquadramento durante a ruptura e recua a partir do tick 150. A tecla O libera a câmera e os demais clientes continuam vendo o efeito.

O shader reconstrói a posição da superfície visível e calcula a distância tridimensional ao ponto de impacto. A borda branca e o rastro violeta chegam mais tarde a superfícies elevadas. A onda descreve uma expansão esférica sobre as superfícies visíveis. Uma simulação de propagação por blocos conectados teria outro comportamento em túneis e atrás de paredes. O sinal é composto no passe existente.

Quatro sons posicionais originais são agendados nos ticks 4, 69, 120 e 145. Um cliente que entra tarde só ouve os próximos sinais. O índice temporal do cliente reinicia na troca de mundo.

O Atlas interpola coordenadas locais na abertura e usa a mesma função de posição no desenho e no teste do clique. Uma seleção válida envia o pedido imediatamente. O cartão marca a escolha brevemente e o servidor decide a execução.

## Diagnóstico (0.5.0)

PerfOverlay (tecla F7) mostra o renderizador OpenGL, FPS, mediana e percentil 95 dos últimos 240 quadros, perfil de qualidade, sequências residentes, efeitos e volumes desenhados, vértices emitidos e passes de pós-processamento. Os renderizadores escrevem os contadores durante o quadro e o painel publica os valores no início do quadro seguinte, então ele sempre mostra o último quadro completo. `/calamidade_client record <segundos>` grava os mesmos dados por quadro em `calamidade_bench/frames-*.csv` na pasta do jogo. No servidor, `/calamidade bench <n> [efeito]` cria uma carga reproduzível e `/calamidade status` informa as instâncias ativas. O roteiro de uso está em [TESTE-REAL.md](TESTE-REAL.md).
