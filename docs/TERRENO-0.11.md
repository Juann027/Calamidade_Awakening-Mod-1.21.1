# Terrain API: destruição, crateras e objetos que viram blocos reais (0.11)

Até a 0.10, o motor era cosmético: um meteoro do Calamidade caía, explodia em luz e deixava o chão intacto. Para cavar uma cratera, um addon teria de chamar `level.setBlock` milhares de vezes num cue (caro e sem desfazer) ou acender TNT (ainda mais caro: raios, drops, física, entidades e luz bloco a bloco, e nenhuma forma controlada). A 0.11 traz a **Terrain API**: o efeito declara uma escavação, uma borda queimada e o que fica no lugar, e o motor troca os blocos em massa, direto nas seções dos chunks, **dentro do impact frame**, como um `/fill` com ar, enquanto o clarão esconde o instante em que os blocos somem e as seções são reenviadas. Depois, um calor desenhado pelo cliente cobre a área por alguns segundos e esfria.

Quatro usos, todos com a mesma API:

1. **Crateras e rasgos:** a forma (esfera, tigela, cápsula, cilindro e combinações) com borda irregular, e uma casca de blocos "queimados" em volta.
2. **Dublê → real:** o objeto que cai é só desenho (uma esfera em chamas do motor); no impacto ele vira blocos reais no centro da cratera, e o desenho esmaece por cima.
3. **Construções que caem:** uma estrutura `.nbt` cai como malha de blocos e fica posicionada no mundo, girada com o yaw do efeito.
4. **Segurança e desfazer:** regras do servidor, um evento cancelável para mods de proteção e `/calamidade terrain undo`.

> **Mexe no mundo de verdade.** Diferente da Light e da Atmosphere, a Terrain API muda blocos no servidor: é salvo, vale para todos os jogadores e só volta com o desfazer (por 10 minutos, padrão). Use mundos de teste ao experimentar e confira as regras do servidor (seção [Segurança](#segurança)).

O protocolo de rede ganhou um canal (`terrain_v1`): **servidor e clientes precisam da mesma versão do motor.** Addons da 0.10 continuam funcionando sem mudança no código: quem não chama `Effects.terrain` não muda nada.

## Exemplo completo

O meteoro da receita 19: uma esfera em chamas cai do céu, abre uma cratera com borda queimada e vira uma esfera de ouro bruto.

```java
static final Timeline TIMELINE = Timeline.builder(320)
    .phase("queda", 0, 60, 4, 2)
    .phase("revelacao", 60, 110, 2, 20)
    .marker("impacto", 60)
    .build();

static final TerrainEdit CRATERA = TerrainEdit.carve(TerrainShape.crater(8, 5).profile(2.2f)
        .union(TerrainShape.sphere(3.5f).offset(0, -3, 0)).rough(1.2f, 3))
    .commitAt("impacto")                                        // marcador da Timeline
    .burn(TerrainPalette.burnt(), 2)                            // casca queimada de 2 blocos
    .fill(TerrainEdit.Fill.solid("minecraft:raw_gold_block", 2.5f).at(0, -3.5f, 0))   // o meteoro real
    .standIn(TerrainEdit.StandIn.sphere(2.5f).from(-18, 90, -30).fall(4).reveal(40).trail(22).color(0xffff7a24).build())
    .cooling(240)                                               // calor esfria em 12 s
    .build();

public static void register() {
    // Impact no MESMO tick do marcador, com 7 ticks: o clarão esconde a troca dos blocos.
    Effects.register(ID, new Effects.Definition(320, 40, 0xffff7a24, Effects.Shape.NONE, Effects.Material.NONE,
        null, null, new Effects.Impact(60, 7, .85f, Effects.ImpactStyle.TINTED_FLASH)).withRange(256));
    Effects.timeline(ID, TIMELINE);
    Effects.terrain(ID, CRATERA);                               // depois da timeline
}

// Servidor: começa no chão, virado com o olhar do jogador (a queda vem de trás e da esquerda dele).
Effects.start(ID, player.serverLevel(), Placement.at(posicao, player.getYRot(), 0).withCaster(player));
```

Nenhum shader, nenhuma camada de cliente: o motor desenha o dublê, o rastro, o calor, as luzes e a poeira.

## Formas (`TerrainShape`)

Todas as formas ficam no **referencial da instância** (o da `Placement` da 0.10): `x` à esquerda, `y` para cima, `z` para a frente, centradas na origem (o centro da instância). Sem orientação, a frente é o sul. Um bloco é cavado quando a distância com sinal no **centro** do bloco é ≤ 0.

| Fábrica | Forma | Limites |
|---|---|---|
| `sphere(raio)` | Bola em volta da origem. | raio (0, 64] |
| `crater(raio, fundo)` | Tigela: fundo `fundo` blocos abaixo da origem no centro, subindo até a origem na borda; profundidade a `r` do centro = `fundo × (1 − (r/raio)^perfil)`. Tudo acima do fundo até `clearance` blocos acima da origem também sai (morros e árvores dentro da borda). | raio e fundo (0, 64]; `profile(1..16)`, padrão 2 (1 cone, 2 parábola, 8+ fundo plano); `clearance(0..64)`, padrão = fundo |
| `trench(comprimento, raio)` | Cápsula de `raio` em volta de um segmento ao longo da frente local, centrada: o rasgo de um raio. Girada pelo yaw, segue o olhar; o pitch a enterra. | comprimento (0, 128], raio (0, 64] |
| `cylinder(raio, altura)` | Cilindro vertical (eixo local para cima), centrado. | raio (0, 64], altura (0, 128] |
| `a.union(b)`, `union(a, b, ...)` | Soma de formas. | até 8 primitivas no total |
| `a.minus(b)` | `a` sem `b` (um pilar poupado no meio da cratera). | `b` sem subtrações próprias |
| `offset(direita, cima, frente)` | Move a forma no referencial da instância. | ±64 por eixo |
| `rough(amplitude, célula)` | Borda irregular: a superfície entra e sai até `amplitude` blocos, com duas oitavas de ruído de valor de células de `célula` blocos, da **seed da instância**. Só na forma final (depois de `union`/`minus`). | amplitude [0, 8], célula [0,5, 64] |

O alcance total (`extent()`, raio em volta da origem que contém tudo, borda incluída) vai até 128 blocos.

**Mesma conta nos dois lados.** A distância é Java puro (`TerrainShape.distance`) e o ruído é aritmética inteira de 32 bits (`TerrainNoise`: hash de rede com viés de 32768, valor com interpolação suave, duas oitavas). O passe do cliente tem uma cópia fiel em `calamidade_terrain.glsl`; `node scripts/testar_glsl.cjs` compara 33 casos com valores calculados pelo Java (hash exato, ruído em 2 × 10⁻⁶, primitivas em 2 × 10⁻⁵). Por isso o cliente desenha o calor exatamente sobre a área cavada **sem receber a lista de blocos**.

## Paleta de queimado (`TerrainPalette`)

`TerrainEdit.Builder.burn(paleta, espessura)` cria uma **casca** de 1 a 4 blocos em volta da escavação. Faixas:

| Faixa | Onde |
|---|---|
| `FLOOR` | Blocos da casca com espaço cavado logo acima (o fundo) e as camadas mais fundas sob ele. |
| `WALL` | Os outros blocos da casca que tocam o espaço cavado (paredes e tetos) e as camadas atrás deles. |
| `RIM` | Camadas mais externas no nível da origem ou acima: o anel queimado no chão em volta da cratera. |

Cada faixa é uma lista de blocos (ID do registro) com pesos; a escolha de cada posição vem de um hash da posição e da seed (determinística, diferente por instância). `TerrainPalette.burnt()` é a paleta padrão, só vanilla: fundo de blackstone, terra grossa, basalto, magma e pedregulho de ardósia; paredes de blackstone, pedregulho, tufo, andesito, pedregulho de ardósia e um pouco de magma; borda de terra grossa, pedregulho, andesito, tufo e blackstone. Uma faixa vazia usa a lista de `WALL`.

**O que a casca troca:** só blocos **sólidos** ganham a paleta. Fluidos na casca são **vedados** com a paleta (a água e a lava da borda ficam presas atrás dela, sem escorrer para a cratera e sem atualização em cascata). Plantas, neve, tochas e outros blocos não sólidos que tocam a cratera queimam (viram ar). Folhas viram ar; troncos ficam. Ar continua ar. Sem `burn`, o motor ainda veda os fluidos que tocam a cratera (pedra para água, obsidiana para lava) e queima o que não é sólido.

**Por que fica estável:** todo bloco que toca o espaço cavado por uma face está na casca (o planejador garante, e o `TerrainCheck` confere). Então nenhum bloco de areia ou cascalho fica suspenso por causa da escavação; se a própria paleta, o preenchimento ou a estrutura escreverem um bloco de gravidade sobre o ar, o motor o assenta (pedregulho) no fim do commit, sem física.

## A edição (`TerrainEdit`)

```java
TerrainEdit.carve(forma)
    .commitAt("impacto")                  // obrigatório: marcador da Timeline
    .burn(TerrainPalette.burnt(), 2)      // casca queimada (opcional)
    .fill(TerrainEdit.Fill.solid("minecraft:raw_gold_block", 2.5f).at(0, -3, 0))   // blocos reais no centro (opcional)
    .structure("meumod:torre").structureOffset(0, -2, 0)                         // estrutura .nbt (opcional)
    .standIn(TerrainEdit.StandIn.sphere(2.5f).from(0, 80, -20).fall(5).reveal(40).build())  // dublê (opcional)
    .blocksPerTick(4096)                  // menos que o padrão do servidor (opcional)
    .replacingBlockEntities()             // trocar baús etc., se o servidor também permitir (opcional)
    .cooling(200).glow(0xffff5a14).smoke(.5f).cracks(.8f)                        // calor (padrões)
    .build();
Effects.terrain(ID, edicao1, edicao2);    // até 4 por efeito, depois de register e timeline
```

| Peça | Detalhe |
|---|---|
| `Fill.solid(bloco, raio)` / `Fill.shell(bloco, raio)` | Esfera sólida ou casca de 1 bloco, raio até 16, em `at(direita, cima, frente)`. Escrita depois da escavação (sobrescreve o ar). |
| `structure(id)` | `data/<ns>/structure/<caminho>.nbt`: centro horizontal na origem, camada mais baixa no nível da origem (mais `structureOffset`), girada `round(yaw / 90)` passos no sentido horário. Estados giram junto; block entities do template recebem seus dados; ar do template limpa o interior; `structure_void` é ignorado. Até `terrainMaxStructure` (32) por eixo. |
| `commitAt(marcador)` | O commit é um cue do motor nesse marcador: roda **uma vez por instância**, quando o relógio passa pelo tick (um release que pula o marcador não comita; num sustain, só a primeira volta). |
| Calor | `cooling(ticks)` (padrão 200): calor `(1 − t/cooling)^1,6` depois do commit; ou `heatTrack("trilha")` com valores 0..1. `noHeat()` desliga. |

Várias edições num mesmo efeito (até 4) comitam cada uma no seu marcador: um rasgo no tick 20 e a cratera final no 40, por exemplo.

## Commit sob o impact frame

```
início ──► regras + plano numa thread de trabalho ──► plano pronto: volume + TerrainEditEvent ──► marcador: commit em lotes
   (tick 0)           (sem ler o mundo)                     (1–2 ticks depois)                  (sob o clarão)
```

1. **Início da instância (servidor):** regras da config (ligado, dimensão, `mobGriefing` para fontes mob) e o plano numa thread de trabalho. O plano é **puro**: forma, origem, orientação, seed e estrutura viram uma lista ordenada de posições e ações, em lotes por seção de chunk (a seção da origem primeiro, depois pela distância). Nenhum acesso ao mundo fora da thread do servidor; não há cópia de seções porque o plano não precisa do mundo.
2. **Plano pronto:** volume máximo (`terrainMaxVolume`) e **um** `TerrainEditEvent` com a área exata (`BoundingBox` de todas as posições) e o volume. Instâncias presas a uma entidade só sabem a origem no marcador: planejam e disparam o evento ali.
3. **Marcador:** o commit começa no mesmo tick e escreve até `terrainBlocksPerTick` blocos por tick (padrão 16 384, **dividido** entre todas as edições em andamento; uma edição pode pedir menos com `blocksPerTick`). As decisões que dependem do mundo (sólido, fluido, protegido, block entity) acontecem aqui, na thread do servidor.
4. **Escrita direta:** `LevelChunkSection.setBlockState(x, y, z, estado, false)`, mais o essencial do `LevelChunk.setBlockState` (heightmaps, fontes de luz do céu, estado da seção no motor de luz, `checkBlock` só quando a luz muda, pontos de interesse, chunk marcado para salvar). Dentro da área escrita, **sem** drops, `onRemove`/`onPlace`, atualização de vizinhos e de forma, nem física (desde a 0.11.1, a casca externa recebe essas atualizações no fim; veja [Integridade do mundo](#integridade-do-mundo)). Uma edição de raio 12 (7 200 blocos) cabe num tick; a de raio 24 (73 000) em cinco.
5. **Fim de cada lote:** cada seção alterada vai **uma vez** aos jogadores que veem o chunk (`ClientboundSectionBlocksUpdatePacket`, só as posições alteradas), seguida, desde a 0.11.1, dos dados de cada block entity nova; o cliente recalcula a luz local e remonta só as seções tocadas.
6. **Fim do commit:** blocos de gravidade escritos sobre o ar são assentados; desde a 0.11.1, a casca externa recebe atualização de forma, de vizinho e ticks de fluido (em lotes, no mesmo orçamento); o snapshot de desfazer fica guardado e `/calamidade terrain list` mostra o relatório (blocos, ticks, tempo na thread do servidor, pacotes).

**Para esconder a troca:** ponha o `Effects.Impact` da definição no tick do marcador, com 4 a 8 ticks (o pacote leva ~1 tick e o cliente remonta as seções nos quadros seguintes). Nos primeiros 1,5 s depois do commit, o passe `terrain` ainda pinta o fundo quente da cratera nos pixels em que o raio atravessa a forma cavada e só acha céu (as seções sendo remontadas), então nem sem clarão aparece um buraco para o céu. Com "ocultar clarões" ligado, o clarão do impacto some e a troca fica visível: acessibilidade vem antes do truque.

## Visual da destruição (cliente)

Um passe do motor por quadro, `terrain`, logo depois do passe de luzes, para todas as instâncias desenhadas:

| Elemento | Como |
|---|---|
| **Brasa e fissuras** | Cada pixel vira posição no mundo (profundidade), recua 0,3 bloco para dentro da face e mede a distância à forma: faces da casca (distância até a espessura) e o que ficou dentro (o preenchimento) brilham na cor da brasa, com manchas e fissuras finas de ruído; o brilho segue o calor (amarelo-alaranjado no auge, vermelho escuro esfriando) e escurece a superfície enquanto está quente. |
| **Faixa incandescente** | Em cada primitiva `trench`, uma faixa clara no fundo, ao longo do eixo: o rastro do raio. |
| **Fumaça leve** | Corda do raio de visão numa esfera sobre a cratera, cortada pela cena, com ruído subindo devagar, proporcional ao calor e a `smoke`. |
| **Tremor de calor** | Desvio leve da imagem sobre a área quente (efeito de movimento). |
| **Luz** | Cada zona quente soma um disco da Light API (laranja que esfria, alcance ≈ o da forma); cada dublê, uma luz de fogo. Contam no teto de luzes e no passe `lights`. |
| **Dublê esférico** | Esfera em chamas (crosta escura, magma correndo, frente incandescente, borda clara), aura e rastro de fogo ao longo da queda (`caSegmentGlow`); depois do commit, fica sobre os blocos reais e esmaece em `reveal` ticks. |
| **Dublê de estrutura** | A malha da estrutura (montada uma vez por template a partir dos blocos que o servidor envia, faces entre vizinhos sólidos cortadas, sombra por direção, brilho máximo) desenhada em movimento depois dos blocos translúcidos; no pouso, ganha precedência de profundidade sobre a estrutura real idêntica e esmaece. Aura e rastro no passe `terrain`. |
| **Poeira** | No pouso de um dublê ou estrutura, uma rajada de fumaça, nuvens e partículas do bloco do chão (seguem a opção de partículas do jogo). |

**Orçamento:** o passe é pago como os de feixes e luzes, uma vez por quadro, do orçamento HERO quando um efeito HERO desenhado precisa dele, senão do comum, **antes** do passe de luzes (o dublê é o próprio objeto; a luz é um extra). Até 2/4/8 zonas quentes e 4/8/8 dublês por quadro (LOW/BALANCED/HIGH); malhas de estrutura 1/2/4. O raio da definição precisa cobrir a queda e a cratera (o teste de frustum usa 2,5 × raio).

**Acessibilidade:** brasa, fissuras, escurecimento, fumaça, aura e rastro seguem a intensidade dos efeitos de tela (0 desliga; o dublê esférico continua desenhado, porque é o objeto). "Ocultar clarões" para o pulso da brasa e a cintilação do rastro. "Efeitos de movimento" desligado tira o tremor de calor.

**F7:** "Terreno: N zonas quentes · N dublês · passe HERO/comum/não" e "Malhas de dublê: N · vértices · última montagem X ms"; o CSV ganha `terrainZones`, `standIns`, `terrainPass`, `meshes` e `meshVertices`.

## Segurança

Config do servidor, seção `terrain` de `serverconfig/calamidade_awakening-server.toml` (por mundo; editável pela lista de mods no singleplayer). Padrões conservadores:

| Opção | Padrão | Efeito |
|---|---|---|
| `terrainEnabled` | `true` | Desligada, nenhuma edição roda (os efeitos tocam sem cratera). |
| `terrainMaxVolume` | 150 000 | Blocos planejados por edição (escavação, casca, preenchimento, estrutura). Acima: recusada inteira. |
| `terrainBlocksPerTick` | 16 384 | Blocos escritos por tick, divididos entre as edições em andamento. |
| `terrainDimensions` | overworld, nether, end | Dimensões permitidas (`"*"` para todas). |
| `terrainBlockEntities` | `false` | Só com isto **e** `replacingBlockEntities()` na edição um bloco com block entity é trocado (os dados vão para o snapshot). |
| `terrainMobGriefing` | `true` | Edições cuja fonte é um mob seguem a regra `mobGriefing`. |
| `terrainUndoMinutes` / `terrainUndoEdits` | 10 / 16 | Quanto tempo e quantos snapshots ficam para desfazer (0 não guarda). |
| `terrainMaxStructure` | 32 | Maior estrutura por eixo. |
| `terrainExplosionEvent` (0.11.1) | `true` | Toda edição passa também pelos eventos de explosão do NeoForge (veja [Compatibilidade](#compatibilidade-0111)): mods de proteção que protegem contra explosões protegem contra crateras. |
| `terrainSpawnProtection` (0.11.1) | `true` | A área de `spawn-protection` do servidor dedicado fica intacta. |

**Nunca tocados:** bedrock, blocos indestrutíveis (dureza < 0), blocos com block entity (baús, fornalhas, blocos de outros mods; salvo as duas opções acima) e a tag de blocos `calamidade_awakening:terrain_protected` (padrão: `#minecraft:wither_immune`, portais, molduras do End, reinforced deepslate, spawners, trial spawners e vaults). Datapacks podem estender a tag. Entidades dentro da área **não** são apagadas: dano e empurrão continuam nos cues.

**`TerrainEditEvent`** (`NeoForge.EVENT_BUS`, thread do servidor, cancelável), uma vez por edição, com nível, efeito, índice, edição, origem, área, volume, fonte e entidade por trás da fonte. Mods de proteção (claims, regiões, spawn) cancelam para recusar:

```java
@SubscribeEvent
public static void protect(TerrainEditEvent event) {
    if (Claims.anyIn(event.level(), event.area())) event.setCanceled(true);
}
```

Desde a 0.11.1, depois do `TerrainEditEvent` vêm a borda do mundo, a proteção de spawn, o modo de jogo do jogador e os eventos de explosão do NeoForge ([Compatibilidade](#compatibilidade-0111)).

Recusas (config, dimensão, `mobGriefing`, evento, volume, estrutura e, desde a 0.11.1, evento de explosão, permissão e área toda protegida) não escrevem nada; o cliente recebe a recusa e não desenha o calor (o dublê, se houver, ainda cai e esmaece). `/calamidade status` mostra as edições em escrita, os snapshots e as recusas por motivo.

## Desfazer

Cada commit guarda um **snapshot compacto**: por seção, os índices alterados e os IDs de estado de antes e depois (cerca de 10 bytes por bloco; a cratera de raio 24 ocupa ~700 KB), mais os dados das block entities trocadas.

- `/calamidade terrain list` (permissão 2): snapshots do mais novo ao mais velho (número, efeito, posição, dimensão, blocos, idade, quem), edições em escrita e o relatório da última.
- `/calamidade terrain undo` ou `undo último` (ou `ultimo`): desfaz o mais novo; `undo <id>`: um específico.

O desfazer usa o mesmo escritor em lotes (sem física, reenvio por seção) e só devolve o estado antigo onde o bloco ainda é o que a edição escreveu: o que um jogador construiu depois fica. O snapshot sai da lista ao ser desfeito ou ao vencer o prazo.

## Rede

Canal novo `terrain_v1` (`TerrainSync`), nunca por tick:

| Estado | Quando | Leva |
|---|---|---|
| `COMMITTED` | No marcador e a quem chega depois | tick do commit, origem, yaw e pitch (o cliente calcula o resto da forma registrada e da seed) |
| `REFUSED` | Na recusa e a quem chega depois | nada |
| `STRUCTURE` | No início (dublê de estrutura) e a quem chega depois | estados girados (IDs) e posições relativas ao canto, uma vez por jogador e instância |

Antes do `COMMITTED` chegar, o cliente prevê o commit no marcador (instâncias fixas). Um motor 0.10 no cliente não registra o canal e só não desenha o calor; versões misturadas não são suportadas.

## Passo 0 — a decisão

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

## Custo medido

Teste automatizado em jogo (Etapa 7 da 0.11.0; detalhes e método em [VALIDACAO.md](VALIDACAO.md#medição-etapa-7)), mundo plano, Mesa llvmpipe, mesmo volume nos três casos (esfera de raio 12, ~7 200 blocos):

| Técnica | Thread do servidor | Pior tick | Ticks > 50 ms |
|---|---|---|---|
| **Terrain API** (commit no marcador) | 10,7 a 13,6 ms, 1 tick | 12,1 a 15,2 ms | 0 |
| `/fill` de ar equivalente (19 × 20 × 19) | 16,0 a 44,7 ms | 21,1 a 51,6 ms | 0 a 1 |
| TNT empilhada (125 blocos, 5 701 destruídos) | ~1,1 s em 140 ticks | 107,8 ms | 3 |
| **Terrain API**, esfera r24 + casca (71 494 blocos) | 63,5 ms em 5 lotes | 24,6 ms | 0 |

O commit é mais barato que o `/fill` porque escreve na seção sem trava, sem `updateFromNeighbourShapes`, sem `blockUpdated` em cada posição e sem pacote por bloco (um por seção); e muito mais barato que a TNT, que gasta em explosões, entidades, drops e física durante segundos. No cliente, o custo é o da remontagem das seções tocadas, como qualquer troca de blocos; a cratera grande pesa alguns quadros enquanto 56 seções são remontadas (meça na sua GPU: TESTE-REAL.md, caso 72). Por instância, o plano roda fora da thread do servidor (a esfera r24 com casca leva ~12 ms numa thread de trabalho) e o snapshot de desfazer guarda ~10 bytes por bloco escrito.

## Limites e cuidados

- **Chunks não carregados são pulados:** o commit nunca carrega chunks. Se ninguém estiver perto quando o marcador chega, as seções daquele chunk ficam como estão (o relatório conta "sections unloaded"). Para edições longe de jogadores, mantenha os chunks carregados (`/forceload`).
- **Sem física dentro da cratera, de propósito:** o volume escrito não recebe atualizações; só a casca externa (0.11.1), uma vez, sem cascata. Água vizinha da vedação recebe tick e fica parada atrás dela; a areia de fora cai só se o bloco embaixo dela virou ar. Blocos protegidos dentro da área podem ficar flutuando.
- **Mobs:** a navegação recalcula quando trava; não é avisada bloco a bloco.
- **Instâncias presas a entidades** planejam no marcador (na thread do servidor), não durante a cena.
- **Mods de renderização e de mundo:** Sodium, Embeddium, C2ME, Distant Horizons e ScalableLux testados na 0.11.1; Moonrise não abre com a RimuLib 1.3.0; shader packs (Iris/Oculus) podem não desenhar o passe `terrain` (como os outros passes de pós). Veja [Compatibilidade](#compatibilidade-0111) e TESTE-REAL.md, partes 6 e 7.
- **Não copie o mundo de outros mods.** A técnica (troca em massa sob o impact frame, dublê que vira bloco, calor que esfria) foi observada em mods de terceiros; a implementação, os shaders, as formas, a paleta e a torre do exemplo são deste projeto, só com blocos vanilla.

## Compatibilidade (0.11.1)

A Terrain API escreve direto nas `LevelChunkSection` (11 a 14 ms contra 16 a 45 ms do `/fill` e 1,1 s da TNT, na mesma cratera). O preço é pular o caminho que o Minecraft e outros mods usam para saber que um bloco mudou (`Level.setBlock`, `BlockEvent`, atualizações de vizinho). A 0.11.1 conferiu, no código e no bytecode (`javap -c`), cada sistema que depende desse caminho, corrigiu o que faltava e deixou um teste automatizado em jogo (`./gradlew testeJogo`, servidor dedicado sem tela, mod de teste em `src/testeJogo`) que compara a cratera com a mesma troca de blocos feita por `setBlock`. Resultado de cada rodada em [VALIDACAO.md](VALIDACAO.md).

### Proteção territorial

**O problema.** O `TerrainEditEvent` é próprio do Calamidade: um mod de claims não o conhece e não o cancela. Mods de proteção conhecem outro caminho: os eventos de explosão do NeoForge, que toda TNT, creeper e bola de fogo do ghast dispara.

**O que a edição faz agora**, uma vez por edição, na thread do servidor, quando o plano fica pronto (logo depois do `TerrainEditEvent`; instâncias presas a uma entidade, no marcador):

1. **Modo de jogo do jogador.** Com fonte jogador (online), `Player.mayBuild()` falso (aventura, espectador) recusa a edição inteira (motivo `PERMISSION` no `/calamidade status`). Conferido também no início da instância.
2. **Borda do mundo** (`WorldBorder.isWithinBounds`), para qualquer fonte: posições fora dela saem do plano.
3. **Proteção de spawn** (`terrainSpawnProtection`). Com fonte jogador, a conta do próprio servidor (`MinecraftServer.isUnderSpawnProtection`: só no servidor dedicado, no overworld, com algum operador cadastrado, e um operador como fonte fica isento). Para as outras fontes (mob, bloco, script), a mesma área sem isenção. Conferido com `javap -c` em `DedicatedServer.isUnderSpawnProtection`: quadrado de lado `2 × spawn-protection + 1` em volta do spawn do mundo. No singleplayer e na LAN o jogo não tem proteção de spawn, e a edição também não.
4. **Eventos de explosão** (`terrainExplosionEvent`, padrão ligado). Uma `Explosion` sintética (construtor `Explosion(Level, Entity, x, y, z, raio, fogo, BlockInteraction, List<BlockPos>)`, que põe a lista em `toBlow`, conferido com `javap -c`): centro na origem da edição, raio até o canto mais distante da área planejada, `BlockInteraction.DESTROY`, sem fogo, fonte = a entidade por trás do efeito (o jogador, o mob; nula para bloco e script). Ela carrega as posições que a edição mudaria **naquele momento** (o que não é ar, mais todo preenchimento e estrutura). Primeiro `ExplosionEvent.Start`: cancelado, a edição é recusada (motivo `EXPLOSION`). Depois `ExplosionEvent.Detonate`: toda posição que os listeners tirarem de `getAffectedBlocks()` fica fora do commit. A explosão **nunca roda** (`explode` e `finalizeExplosion` não são chamados): sem dano, partículas, som, drops ou fogo.
5. **No commit**, um bit por entrada do plano diz o que pode ser escrito. Uma posição que era ar na hora da autorização (e por isso não foi oferecida aos listeners) é pulada se alguém construir ali antes do marcador: nada muda sem passar pelos listeners. Se todas as posições forem tiradas, a edição é recusada (motivo `PROTECTED`).

O relatório de `/calamidade terrain list` conta as posições guardadas: `N guarded (A border/spawn, B explosion listeners)`.

**Mods de proteção.** Mods que tratam o `ExplosionEvent` do NeoForge passam a valer para as crateras sem saber do Calamidade. **FTB Chunks** e **Open Parties and Claims** protegem claims contra explosões tirando posições da lista de blocos afetados, que no NeoForge é a do `ExplosionEvent.Detonate`; com eles, a cratera deve parar na borda do claim, desde que a proteção contra explosões esteja ligada (no FTB Chunks, a opção do time que permite explosões; no OPAC, a proteção de explosões dos claims). **GriefDefender** e outros valem do mesmo jeito **se** a versão para NeoForge 1.21.1 tratar o `ExplosionEvent` (e não só mixins dentro da explosão vanilla, que a explosão sintética nunca executa). Nenhum desses mods foi instalado aqui: o comportamento foi provado com um listener que faz o mesmo que eles, e precisa ser confirmado com as versões reais (TESTE-REAL.md, caso 75). O `TerrainEditEvent` continua, para quem quiser recusar a edição inteira com a área e a fonte exatas.

**Efeitos colaterais a conhecer.** Todo listener de explosão vê a edição como uma explosão: mods que **regeneram** explosões (devolvem os blocos depois de um tempo) vão regenerar a cratera; mods que criam **destroços** (blocos caindo para cada posição) podem criar milhares de entidades numa cratera grande. Nesses servidores, desligue `terrainExplosionEvent` (fica só o `TerrainEditEvent`, a borda e o spawn) ou configure o outro mod para ignorar explosões sem fonte ou de raio grande. O calor desenhado pelo cliente segue a forma inteira: numa área protegida, os blocos intactos podem brilhar por alguns segundos (é só visual; mudar isso exigiria mudar o protocolo).

**Teste em jogo:** um listener de teste que só conhece o `ExplosionEvent` protege `x ≥ cx`; a cratera abre do lado de fora (183 posições) e nenhuma posição muda do lado protegido, com o bloco de `x = cx − 1` cavado e o de `x = cx` intacto. Também: `Start` cancelado (recusa, nada muda), `terrainExplosionEvent = false` (listener não chamado, cratera inteira), borda do mundo no meio da cratera e proteção de spawn de 16 num servidor dedicado com um operador (nada muda do lado protegido).

### Iluminação

**O que já era feito (conferido com `javap -c` e no teste).** Cada escrita repete o ramo de luz de `LevelChunk.setBlockState`: quando a seção passa de vazia para não vazia (ou o contrário, no fim do lote), `LevelLightEngine.updateSectionStatus`; quando `LightEngine.hasDifferentLightProperties` diz que a luz muda, `ChunkSkyLightSources.update` da coluna e `LevelLightEngine.checkBlock` da posição. O `ThreadedLevelLightEngine` do servidor enfileira cada `checkBlock` e o processa no fim de cada tick (`ChunkMap.tick` chama `tryScheduleUpdate`); a propagação atravessa seções e chunks vizinhos sozinha, como num `/fill`. Não precisa de uma fila própria por lote: a fila do jogo já é por bloco. Quem recebe a luz:

- **Jogadores perto:** recebem o pacote da seção e o cliente refaz a luz localmente (`ClientPacketListener` aplica cada bloco por `setBlock`, que chama o motor de luz do cliente), exatamente como com o `/fill`.
- **Jogadores na borda da distância de visão:** recebem a luz calculada pelo servidor pelo caminho normal (`ChunkHolder.broadcastChanges` manda as seções de luz que o motor marcou só a eles; conferido com `javap -c`).
- **Disco:** a luz é salva com o chunk, que fica marcado como modificado.

**O que foi corrigido.** Com o **ScalableLux** (motor de luz derivado do Starlight, versão para NeoForge 1.21.1), o primeiro commit quebrava com `NullPointerException`: esse mod substitui as fontes de luz do céu e deixa `LevelChunk.getSkyLightSources()` nulo, e o escritor chamava `update` em toda escrita. O vanilla só chama no ramo "a luz muda", que o mod redireciona. O escritor agora segue o mesmo ramo e confere o nulo; o `checkBlock` continua indo ao motor do mod. E um commit que falha no meio agora guarda o snapshot parcial (dá para desfazer o que foi escrito) em vez de perdê-lo junto com a exceção.

**Teste em jogo.**

| Caso | Resultado |
|---|---|
| Servidor: a mesma cratera (casca e esfera de glowstone, 417 blocos) pela Terrain API e copiada bloco a bloco com `setBlock` (o caminho do `/fill`) em outra região | luz do céu e dos blocos igual em 37 × 31 × 37 posições (0 diferenças); brilho (`getMaxLocalRawBrightness`) igual ao meio-dia e à meia-noite; glowstone dá luz 15 |
| Desfazer | luz igual à de antes do commit (0 diferenças) |
| **ScalableLux** 0.3.0-alpha.0.8 | os 27 casos do servidor passam, luz igual à do `/fill` (depois da correção acima) |
| Cliente (Xvfb, Mesa llvmpipe): luz recalculada pelo cliente na cratera da Terrain API e na do `setBlock` | 0 diferenças entre as duas e 0 diferenças em relação ao servidor |
| Cliente: capturas das duas crateras do mesmo ângulo, de dia e de noite, comparadas em blocos de 40 px (a rotação aleatória da textura da grama impede comparar pixel a pixel) | diferença média 0,23 (dia) e 0,10 (noite) de 255, máxima 2,2 e 3,2: sem manchas escuras nem claras ([imagem](gameplay/luz-0111.jpg)) |

**Mods que substituem o motor de luz.**

- **ScalableLux** (NeoForge 1.21.1): testado aqui, compatível depois da correção.
- **Moonrise** (port do sistema de chunks e do Starlight do Paper, 0.1.0-beta.15 para NeoForge 1.21.1): **não abre junto com a RimuLib 1.3.0.** O jogo para na carga com `InjectionError` no mixin `rimulib.mc1211.world.mixins.json:ServerLevelMixin` (`rimulib$discard` redireciona a mesma chamada que o `moonrise ... ServerLevelMixin.redirectClose`). É um conflito entre a RimuLib e o Moonrise, antes de o Calamidade rodar; não dá para corrigir deste lado. Avise o autor da RimuLib.
- **Starlight** (só Fabric e Forge antigos) e outros: valem as mesmas premissas do ScalableLux (o mod precisa redirecionar `checkBlock` e `updateSectionStatus`, que o escritor chama como o vanilla). Teste com o roteiro do TESTE-REAL.md, caso 76.

### Integridade do mundo

| Sistema | Antes da 0.11.1 | Teste em jogo |
|---|---|---|
| **Mapas de altura** (`WORLD_SURFACE`, `OCEAN_FLOOR`, `MOTION_BLOCKING`, `MOTION_BLOCKING_NO_LEAVES`) | já certo: `Heightmap.update` em cada escrita, como `LevelChunk.setBlockState` | iguais aos da cópia por `setBlock` e a uma varredura das colunas, depois do commit e do desfazer |
| **Pontos de interesse** (`PoiManager`) | já certo: `ServerLevel.onBlockStateChange` em cada escrita (tira o POI do estado antigo e põe o do novo, na fila de tarefas do servidor) | composteira, mesa de ferraria e magnetita somem com a cratera e voltam no desfazer; composteiras do preenchimento entram e saem; os POI sobrevivem ao reinício |
| **Chunk modificado** | já certo: `setUnsaved(true)` no fim de cada lote | depois de salvar tudo, o jogo salva de novo o chunk da cratera (`ChunkDataEvent.Save`); a cratera (224 posições) e os POI estão lá depois de fechar e abrir o servidor dedicado |
| **Contadores da seção** (não vazios, tick aleatório, fluidos) | já certo: `LevelChunkSection.setBlockState` atualiza os três a cada troca (conferido com `javap -c`) | iguais aos de `recalcBlockCounts()` em todas as seções da cratera, depois do commit e do desfazer |
| **Casca externa** | **faltava**: os blocos logo fora da área escrita não recebiam atualização de forma nem de vizinho | corrigido (abaixo): a cerca da borda solta a conexão com a que sumiu, a água vizinha da vedação ganha tick de fluido e a de mais longe não, nenhuma água entra na cratera; no desfazer, as cercas reconectam |
| **Dados de block entity no cliente** | **faltava**: o pacote de seção não leva dados de block entity; uma placa devolvida pelo desfazer (ou uma estrutura com placa, estandarte, cabeça) aparecia vazia no cliente | corrigido: no fim do lote, cada block entity nova manda o seu `getUpdatePacket()`, como `ChunkHolder.broadcastBlockEntityIfNeeded`; a placa devolvida mostra o texto no cliente |
| **Desfazer** | mesmo escritor | mesmo tratamento: mapas de altura, POI, contadores, luz, casca externa e dados de block entity |

**Casca externa.** Depois da última escrita de um commit (e do assentamento dos blocos de gravidade) ou de um desfazer, a edição percorre, em lotes dentro do mesmo orçamento de blocos por tick, as posições escritas e junta os vizinhos de fora (não escritos e em chunk FULL). Cada um recebe, uma vez:

- `updateShape` na direção de cada vizinho escrito; se a forma muda, `Block.updateOrDestroy` com `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS` (vai ao cliente, sem nova rodada de formas, sem drops);
- um `neighborChanged` vindo de um vizinho escrito (redstone, trilhos, fluidos);
- tick de fluido, se tiver fluido. As posições escritas da borda que têm fluido (a água que o desfazer devolve) também ganham o tick.

Dentro da área escrita nada é atualizado: tudo ali foi escrito pela edição, e a casca já veda os fluidos e queima o que não é sólido. Por isso não há cascata. O que um bloco de fora faz com a atualização é o comportamento normal dele: um observador virado para a cratera pulsa, a areia sobre um bloco que virou ar cai, o portal do Nether cuja moldura foi cavada se desfaz (o bloco de portal é protegido pela tag, mas a moldura não). O relatório mostra `N border updates (S shapes, F fluid ticks)`.

**Custo** (servidor dedicado, mesma esfera de raio 24 com casca de 2 da medição da 0.11.0, 36 754 blocos escritos no mundo plano): commit e casca em 9 ticks, 90,6 ms na thread do servidor ao todo, com 3 621 blocos na casca externa; pior tick 25,5 ms. Desfazer em 7 ticks, 52 ms, pior tick 18,3 ms. As verificações de proteção rodam uma vez, quando o plano fica pronto (antes do marcador): 21,9 ms para 73 508 posições planejadas, num só tick (mais o custo dos listeners de explosão instalados).

### Geração de mundo e mods de renderização

**Só chunks FULL (conferido).** O escritor, a casca externa e as verificações de proteção pegam o chunk por `ServerChunkCache.getChunkNow`, que devolve `null` fora da thread do servidor e só devolve um `LevelChunk` (status `FULL`) já carregado (conferido com `javap -c`); desde a 0.11.1, o motor também exige `LevelChunk.getFullStatus()` pelo menos `FULL` (descarta um chunk cujo nível de ticket caiu, a caminho de descarregar). Nada é carregado nem gerado: seções de chunks que não estão em `FULL` (em geração, só com luz, descarregados) são puladas e contadas no relatório (`sections unloaded`). **Teste em jogo:** com um único chunk forçado, o chunk a 2 de distância é `FULL` e o a 3 está carregado sem ser `FULL`; uma cratera no limite escreve só no primeiro, pula 2 seções do segundo, não o promove a `FULL` e, carregado depois, ele está intacto. Uma cratera cortada assim fica com uma parede reta no limite, sem casca do lado que não foi escrito.

**C2ME** (Concurrent Chunk Management Engine, port para NeoForge 0.4.0-alpha.0.124, gera, carrega e salva chunks em paralelo): a Terrain API só escreve, na thread do servidor, em chunks que já chegaram a `FULL`, e marca o chunk como modificado; a geração paralela não vê chunks a meio caminho escritos por ela. **Teste em jogo:** os 51 casos do servidor dedicado passam com o C2ME instalado, inclusive a cratera e os pontos de interesse depois de reiniciar.

**Distant Horizons** (3.3.3 para NeoForge 1.21.1): carrega junto e os 10 casos do cliente passam. As versões distantes (LOD) são refeitas a partir dos dados salvos: o mixin do DH no servidor fica no fim de `ChunkMap.save` (conferido no bytecode do DH 3.3.3). Como a edição marca o chunk como modificado, o LOD se atualiza quando o jogo salva o chunk: em segundos num servidor ocioso (o jogo salva até 20 chunks modificados por tick com tempo sobrando), senão no salvamento automático (a cada 5 minutos). Isso vale no singleplayer e em servidores com DH. **Risco:** num servidor **sem** DH, o DH do cliente só monta o LOD com o que o cliente recebe; a cratera aparece na distância normal de renderização, mas a versão distante pode mostrar o terreno antigo até o cliente recarregar aquele chunk. O dono precisa conferir (TESTE-REAL.md, caso 79).

**Sodium e Embeddium** (Sodium 0.8.13 e Embeddium 1.0.15 para NeoForge 1.21.1): as seções chegam pelo pacote do jogo e esses mods as remontam como qualquer troca de blocos. **Teste em jogo** (Xvfb, Mesa llvmpipe): com cada um, os 10 casos do cliente passam: luz igual à do `setBlock` e à do servidor, imagens de dia e de noite sem manchas, modo aventura, placa com texto depois do desfazer ([imagem: Sodium de dia, Embeddium de noite](gameplay/sodium-embeddium-0111.jpg)). O desempenho numa GPU física fica para o TESTE-REAL (caso 72). Shader packs (Iris/Oculus) não foram testados: podem não desenhar o passe `terrain`, como os outros passes de pós.

## Receitas

[18 — raio que abre uma trincheira](ai/receitas/18-raio-trincheira.md), [19 — meteoro que vira bloco real](ai/receitas/19-meteoro.md), [20 — cratera simples](ai/receitas/20-cratera-simples.md), [21 — torre que cai e fica](ai/receitas/21-torre-caindo.md). Testes: `TerrainCheck` no `checkBudget` (volumes, determinismo pela seed, vedação da casca, orientação da trincheira, ordem estável dos lotes, paleta, preenchimento, estrutura girada, registro) e os casos 63 a 74 de TESTE-REAL.md.

