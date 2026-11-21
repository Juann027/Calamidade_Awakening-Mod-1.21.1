# Terrain API: destruição, crateras e objetos que viram blocos reais (0.11)

> Capítulo em construção durante a 0.11.0. Esta primeira parte registra a decisão do Passo 0; o restante do capítulo
> (formas, paleta, commit sob o impact frame, dublê → real, estruturas, segurança, desfazer e custo medido) entra nas
> etapas seguintes.

## Passo 0 — CarveApi da RimuLib 1.3.0 ou escrita direta nas seções?

A RimuLib 1.3.0 traz uma `CarveApi` (`cyou.rimuru.rimulib.mc1211.api.CarveApi`) para cortar terreno em massa. Antes de
escrever código, ela foi conferida com `javap -p` e `javap -c` (bytecode de `CarveApi`, `CarveApi$Cut` e da classe
interna `terrain.Carving`). O que ela faz de fato:

| Pergunta | O que o bytecode mostra |
|---|---|
| **Forma do corte** | Só cortes de **coluna**: `column`, `flatten`, `slit`, `wedge`, `line` e `lens` recebem uma pegada 2D e um `Floor.at(x, z) → y` (altura do chão por coluna; `UNTOUCHED = Integer.MIN_VALUE` deixa a coluna intacta). Tudo acima do chão da coluna sai; `Surface.at(x, y, z, estado)` troca só a camada de cima. Não há teto, cavidade fechada, cápsula inclinada nem casca 3D. |
| **`prepare()` e `ready()`** | `Cut.prepare()` só põe o corte na lista `PREPARING` (exige `atOnce(true)`). A cada tick, `Carving.prepare(long)` roda **na thread do servidor**, com prazo em nanossegundos: calcula os chãos do chunk, copia as seções não vazias (`copy` = `new LevelChunkSection(SectionCopy.states(getStates()), getBiomes())` + contadores por um accessor de mixin), tira uma impressão de cada seção e manda o corte rodar **sobre as cópias** num pool próprio (`BUILD`, 2 a 8 threads, `CompletableFuture.supplyAsync`). `ChunkEdits.watch/stamp` marca o chunk para detectar edição posterior à cópia. `ready()` devolve `allBuilt`. |
| **Aplicação** | Por **chunk inteiro**: `swap(level, chunk, seções[])` troca o vetor de `LevelChunkSection` do chunk pelas cópias cortadas, conferindo a impressão. |
| **Luz e reenvio** | `relight(level, chunk)`: refaz os heightmaps e as fontes de luz do céu da coluna (por um invoker de mixin em `ChunkSkyLightSources`), `ThreadedLevelLightEngine.propagateLightSources(chunkPos)`, envia um `ClientboundLevelChunkWithLightPacket` do chunk inteiro e, depois de `lightChunk(chunk, true)`, um `ClientboundLightUpdatePacket` do chunk inteiro. |
| **Orçamento** | Estático e **compartilhado por todos os cortes do servidor**: `TICK_BUDGET_NS = 30 000 000` (30 ms de thread do servidor por tick, somados entre todos os cortes ativos) e `CHUNKS_PER_TICK = 12`; `perTick(n)` conta chunks, não blocos. |
| **Blocos com BlockEntity** | `removeBlockEntities(chunk)` tira as block entities dentro do corte. `keepBlocks(Predicate)` registra um predicado **global** (lista estática `CORES`) que vale para todo corte de qualquer mod que peça `keepBlocks()`. |
| **Desfazer e evento** | Não existem. |

### Decisão: escrita direta nas `LevelChunkSection`, com planejador próprio; a `CarveApi` não é usada

1. **Formas 3D.** A 0.11 precisa de esfera (que pode ser uma cavidade sob o chão), tigela com perfil configurável,
   cápsula inclinada pelo `Placement`, cilindro com topo e combinação de formas, mais uma **casca 3D com faixas** (fundo,
   parede, borda externa). O modelo de chão por coluna da `CarveApi` não expressa nada disso além da tigela aberta.
2. **Orçamento em blocos, configurável por servidor e por edição.** O da `CarveApi` é em tempo (30 ms compartilhados) e
   em chunks inteiros; um corte grande de outro mod consumiria o orçamento das nossas edições e vice-versa.
3. **Segurança.** As regras da 0.11 (nunca bedrock, dureza < 0, blocos com BlockEntity salvo opção explícita, tag
   `calamidade_awakening:terrain_protected`, `mobGriefing`, dimensões permitidas, evento cancelável, desfazer) exigem
   decidir bloco a bloco. A `CarveApi` remove block entities e só aceita um predicado global, que mudaria o comportamento
   dos cortes de outros mods.
4. **Desfazer.** Precisamos do estado anterior de cada posição alterada; com a troca de seções inteiras da `CarveApi` o
   snapshot teria de ser a seção inteira (até 4096 estados) mesmo quando poucos blocos mudam.

O que foi aproveitado é a **técnica** que a `CarveApi` confirma: escrever direto nas seções, sem `Level.setBlock`, e
resolver luz e reenvio por lote. A implementação é nossa:

- **Plano puro em paralelo, sem ler o mundo.** O plano (posições ordenadas, faixa da casca, bloco de destino e lotes por
  seção) depende só da forma, da orientação, da seed e da origem: é Java puro e roda numa thread de trabalho durante a
  cena, antes do marcador. Nenhuma leitura de mundo acontece fora da thread do servidor; não há cópia de seção porque o
  preparo paralelo não precisa do mundo.
- **Commit na thread do servidor, no marcador da Timeline.** As decisões que dependem do mundo (sólido, fluido,
  protegido, BlockEntity) e as escritas acontecem no tick do marcador e nos seguintes, com orçamento de blocos por tick.
  Cada escrita é `LevelChunkSection.setBlockState(x, y, z, estado, false)` (sem trava), mais o que
  `LevelChunk.setBlockState` faz de essencial (conferido com `javap -c`): `Heightmap.update` dos quatro heightmaps,
  `ChunkSkyLightSources.update`, `LevelLightEngine.updateSectionStatus` quando a seção muda de vazia para não vazia e
  `checkBlock` só quando `LightEngine.hasDifferentLightProperties` diz que a luz muda, `ServerLevel.onBlockStateChange`
  (POI) e `setUnsaved`. Ficam de fora, de propósito: `onRemove`/`onPlace`, drops, atualização de vizinhos e de forma,
  física.
- **Reenvio por seção.** No fim de cada lote, cada seção alterada vai uma vez aos jogadores que acompanham o chunk
  (`ChunkMap.getPlayers(pos, false)`), num `ClientboundSectionBlocksUpdatePacket` com as posições alteradas. O cliente
  recalcula a luz local como faz com qualquer bloco recebido; a luz dos jogadores na borda da distância de visão segue o
  caminho normal do jogo (`ChunkHolder.broadcastChanges` envia as seções de luz que o motor de luz marcou).

O detalhamento (formas, paleta, segurança, desfazer, custo medido) segue nas próximas seções deste capítulo.
