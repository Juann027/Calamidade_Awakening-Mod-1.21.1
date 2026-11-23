# Integração com outros mods: orientação, fontes, limites e gatilhos (0.10.0)

A 0.10.0 prepara o Calamidade para ser usado por outros mods: o **RespectCraft** (Finisher Engine: finalizações entre dois participantes, com rigs GeckoLib, câmera própria e eventos por tick como `EVENT_HIT` de força 1 a 3 e `EVENT_FALL`), itens, blocos e ataques de mobs. Quatro peças novas, todas na API pública `dev.calamidade_awakening.api.v1`:

1. **Orientação por instância**: um efeito pode começar com yaw e pitch, e seus pontos, feixes e luzes viram junto.
2. **Fontes, limites e reserva**: toda instância sabe quem a começou (jogador, mob, bloco ou script); o servidor limita cada fonte e guarda vagas para jogadores.
3. **Gatilhos prontos** (`EffectTriggers`): item, block entity e entidade, sem depender de GeckoLib.
4. **Consumo como biblioteca**: um modelo de `build.gradle` que baixa o JAR do release do GitHub com SHA-256.

Addons da 0.9 continuam funcionando sem mudança no código: sem orientação, o referencial é o dos eixos do mundo, e as fontes e os limites só recusam o que antes passaria de 64 instâncias, de 16 por jogador ou da reserva. O protocolo de rede mudou (pacote `event_v2`): **servidor e clientes precisam da mesma versão do motor**.

## 1. Orientação por instância

### O referencial

Uma instância orientada tem um referencial próprio, com os eixos locais do Minecraft (os mesmos de `^x ^y ^z` nos comandos):

| Eixo local | `Point.center(x, y, z)` | `Point.facing(direita, cima, frente)` | Com yaw 0 e pitch 0 |
|---|---|---|---|
| esquerda | `+x` | `-direita` | leste (+X) |
| cima | `+y` | `cima` | cima (+Y) |
| frente | `+z` | `frente` | sul (+Z) |

Yaw e pitch em graus, na convenção do Minecraft: **yaw 0 olha para o sul**, 90 para o oeste, -90 para o leste, 180 para o norte; **pitch positivo olha para baixo**, de -90 a 90. Sem orientação (yaw 0, pitch 0) o referencial é exatamente o dos eixos do mundo, por isso efeitos escritos para a 0.9 ficam idênticos quando ninguém passa orientação, e viram quando alguém passa.

O que gira com a instância:

| Gira | Não gira |
|---|---|
| Pontos `Point.center(...)` e `Point.facing(...)` de feixes e luzes | Pontos `Point.world(...)` (posição fixa) |
| Deslocamentos por trilha (`Beam.Param.FROM_X`…`TO_Z`, `Light.Param.X/Y/Z`) | Deslocamentos de pontos de entidade (`anchor`, `anchorEye`, `caster`…: eixos do mundo) |
| O deslocamento da âncora (`Placement.on(entidade, offset)`, `follow(..., true)`) | Discos e anéis de luz no chão (só o centro gira; continuam horizontais) |
| `ctx.facing(...)`/`ctx.forward()` nos cues; `post.facing(...)` e `world.facing(...)` nas camadas | A geometria e os volumes embutidos (`Shape`, `Material`) e shaders de addon que não usam `post.facing` |

### Como começar orientado

```java
// Virado para onde o jogador olha (só na horizontal):
Effects.start(ID, level, posicao, jogador.getYRot(), 0);
Effects.start(ID, jogador, posicao, jogador.getYRot(), jogador.getXRot());   // com conjurador

// Placement: posição ou âncora, orientação, conjurador, fonte, recarga e bloco vinculado.
Effects.start(ID, level, Placement.at(olhos).withLookOf(jogador).shifted(.3, -.25, .4).withCaster(jogador));
Effects.start(ID, level, Placement.at(vitima.getBoundingBox().getCenter()).arrivingFrom(atacante.getEyePosition()));
Effects.start(ID, level, Placement.at(posicao).facing(alvo));                 // olhando para um ponto

// Preso a um mob, com o yaw seguindo o corpo dele a cada tick e quadro:
Effects.start(ID, level, Placement.on(mob).shifted(0, 1.2, .6).followingBody());
Effects.follow(ID, mob, new Vec3(0, 1.2, .6), true);                         // o mesmo, forma curta
```

| `Placement` | O que faz |
|---|---|
| `at(posição)`, `at(posição, yaw, pitch)` | posição fixa, com ou sem orientação |
| `on(entidade)`, `on(entidade, offset)` | presa à entidade (pés + offset no referencial da instância) |
| `withOrientation(yaw, pitch)`, `withYaw(yaw)` | orientação explícita (yaw qualquer, normalizado; pitch em [-90, 90]) |
| `withLookOf(entidade)` | o olhar da entidade (`getYRot`, `getXRot`) |
| `facing(alvo)` | olhando da posição (ou dos olhos da âncora) para `alvo` |
| `arrivingFrom(origem)` | na direção de quem chega de `origem`: a direção de um golpe, do atacante para a vítima |
| `shifted(direita, cima, frente)` | desloca no referencial; numa posição fixa usa a orientação já definida (defina antes), numa âncora soma ao offset |
| `followingBody()` | o yaw segue o corpo da âncora (`yBodyRot`, interpolado no cliente como o renderizador faz); o yaw da Placement vira um desvio; o pitch fica fixo |
| `withCaster(jogador)` | câmera do efeito para esse jogador, `ctx.caster()` e pontos `caster*` |
| `withSource(fonte)`, `withCooldown(ticks)`, `boundTo(blockEntity)` | seção 2 |

No servidor, o `Effects.Context` dos cues traz `yaw()`, `pitch()`, `facing(direita, cima, frente)` e `forward()` do momento do cue (já com o yaw do corpo, se a instância o segue). No cliente, `AddonLayers.Post` e `AddonLayers.World` trazem `yaw()`, `pitch()` e `facing(...)` do quadro.

## 2. Fontes, limites e reserva

### Quem começou cada instância

`EffectSource` tem quatro tipos:

| Tipo | Quem | Limite padrão por fonte | Usa a reserva |
|---|---|---|---|
| `PLAYER` | um jogador (cartões, itens, comandos com jogador conjurador, efeitos presos a jogadores) | 16 | sim |
| `ENTITY` | uma entidade que não é jogador (ataques de mobs) | 4 | não |
| `BLOCK` | um bloco (dimensão e posição) | 2 | não |
| `SCRIPT` | código sem fonte individual (comandos, datapacks, lógica de mod) | sem limite por fonte | não |

Quando o código não diz, o motor escolhe: o bloco vinculado (`boundTo`), senão o conjurador, senão a âncora (`EffectSource.entity`: PLAYER para jogadores), senão SCRIPT. `Effects.start(id, level, posição)` é SCRIPT; `Effects.start(id, jogador, posição)` é PLAYER; `Effects.follow(id, entidade, offset)` é a entidade.

### Limites do servidor

`serverconfig/calamidade_awakening-server.toml` de cada mundo (também editável na lista de mods em um jogador):

| Opção | Padrão | Faixa | Significado |
|---|---|---|---|
| `maxPerPlayer` | 16 | 1..64 | instâncias ativas por jogador |
| `maxPerEntity` | 4 | 1..64 | instâncias ativas por mob |
| `maxPerBlock` | 2 | 1..64 | instâncias ativas por bloco |
| `playerReserve` | 16 | 0..64 | das 64 vagas do servidor, quantas só jogadores usam: mobs, blocos e scripts dividem as outras 48 |

Um pedido acima de qualquer limite é **recusado sem erro**: `Effects.start` devolve `Optional.empty()` (os gatilhos devolvem vazio ou `false`). O `/calamidade bench`, que usa fonte SCRIPT, chega a 48 com a reserva padrão (`playerReserve = 0` libera as 64).

### Recarga por fonte

A recarga é por fonte **e** por efeito, no servidor:

```java
var mob = EffectSource.entity(zumbi);
Effects.cooldownReady(mob, ID, level);        // pode começar agora?
Effects.cooldownLeft(mob, ID, level);         // ticks restantes
Effects.startCooldown(mob, ID, level, 60);    // começa (0 limpa)
Effects.start(ID, level, Placement.on(zumbi).withCooldown(60));   // confere e começa numa chamada
```

Blocos usam `EffectSource.block(level, pos)` (ou `block(blockEntity)`); jogadores, `EffectSource.player(jogador)`. A recarga dos cartões do Focus é a do jogador.

### Diagnóstico

- **F7**: linha "Fontes: N jogadores · N mobs · N blocos · N scripts" (instâncias residentes no cliente). O CSV de `/calamidade_client record` ganha as colunas `players`, `entities`, `blocks` e `scripts`.
- **`/calamidade status`**: ativas por tipo, os limites em vigor e quantos pedidos cada limite recusou desde que o servidor abriu.

## 3. Gatilhos prontos (`EffectTriggers`)

Cada gatilho é uma configuração imutável (cada método devolve uma cópia): guarde em um campo `static final`. O efeito só precisa estar registrado quando o gatilho dispara.

### Item

```java
public class Cajado extends Item {
    static final EffectTriggers.ItemTrigger RAIO = EffectTriggers.item(RAIO_ID).offset(.3, -.25, .4).cooldown(30).durability(1);
    static final EffectTriggers.ItemTrigger GOLPE = EffectTriggers.item(GOLPE_ID).at(EffectTriggers.ItemTrigger.Spot.TARGET);
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return RAIO.use(level, player, hand);
    }
    @Override public boolean hurtEnemy(ItemStack stack, LivingEntity alvo, LivingEntity atacante) {
        GOLPE.attack(stack, alvo, atacante);
        return super.hurtEnemy(stack, alvo, atacante);
    }
}
```

| Ponto (`at`) | Onde e como vira |
|---|---|
| `EYES` (padrão) | nos olhos, virado pelo olhar (yaw e pitch): `Point.facing(0, 0, 24)` é sempre "24 blocos à frente" |
| `CASTER` | preso aos pés do usuário, yaw seguindo o corpo (auras, efeitos carregados) |
| `AIM` | no primeiro bloco mirado, até `reach` (32 por padrão), virado pelo yaw do olhar |
| `TARGET` | no meio do alvo do ataque, virado dos olhos do atacante para ele (sem alvo, como `AIM`) |

O usuário é a fonte (PLAYER para jogadores) e, sendo jogador, o conjurador. Depois de uma partida aceita, o item perde `durability` pontos (não em criativo) e, para jogadores, mostra a recarga na barra. `fire(usuário, mão, alvo)` dispara de qualquer lugar no servidor (por exemplo, `releaseUsing`). Receita completa: [15-cajado.md](ai/receitas/15-cajado.md).

### Block entity

```java
public class PilarEntidade extends BlockEntity {
    static final EffectTriggers.BlockTrigger PULSO = EffectTriggers.blockEntity(PULSO_ID).redstone().period(20);
    private final EffectTriggers.BlockTrigger.Component efeito = PULSO.attach(this);
    public PilarEntidade(BlockPos pos, BlockState state) { super(TIPO.get(), pos, state); }
    static void tick(Level level, BlockPos pos, BlockState state, PilarEntidade self) { self.efeito.tick(); }
}
// No bloco: getTicker devolve createTickerHelper(type, TIPO.get(), PilarEntidade::tick) no servidor.
```

| Modo | Quando começa | Com sustain | `period` |
|---|---|---|---|
| `PERIODIC` (padrão, 40 ticks) | a cada `period` ticks | começa uma vez e fica preso | 1..72000 |
| `REDSTONE` | na borda de subida do sinal | preso enquanto ligado; `release()` ao desligar | 0: uma vez por borda; mais: repete enquanto ligado |
| `PROXIMITY` (`proximity(raio)`) | quando um jogador entra no raio | preso enquanto houver jogador | como no REDSTONE |

O centro é a base do bloco mais `offset(direita, cima, frente)` (padrão 0, 1, 0), virado pela propriedade `facing`/`horizontal_facing` (ou `yaw(...)` fixo). A instância nasce vinculada ao bloco (`Placement.boundTo`): **quebrar o bloco, trocá-lo ou descarregar o chunk encerra o efeito**, mesmo se o mod não avisar (o motor confere `BlockEntity.isRemoved()`, que o jogo marca nos três casos). Partidas recusadas são tentadas de novo no máximo a cada `max(period, 20)` ticks. Receita: [16-pilares.md](ai/receitas/16-pilares.md).

### Entidade (ataques de mobs)

```java
static final EffectTriggers.EntityTrigger FEIXE = EffectTriggers.entity(ID).offset(0, 1.25, .35).cooldown(20);
// NeoForge.EVENT_BUS, servidor:
void dano(LivingDamageEvent.Post e) {
    if (e.getSource().getEntity() instanceof Zombie zumbi) FEIXE.fire(zumbi, e.getEntity());
}
```

`fire(mob)` vira a instância com o corpo do mob, seguido a cada tick e quadro; `fire(mob, alvo)` a vira para o alvo no instante do golpe (o centro continua preso ao mob, e o offset fica nivelado, sem inclinar com a mira). O mob é a fonte e a âncora: os cues recebem `ctx.anchor()`, os pontos `anchor*` resolvem para ele e a instância acaba quando ele some. Receita: [17-zumbi.md](ai/receitas/17-zumbi.md).

## 4. Mobs animados com GeckoLib (ou outro rig)

O Calamidade **não depende do GeckoLib**; o código abaixo fica no mod do usuário. A regra: **a animação roda nos clientes, o efeito começa no servidor**. Os handlers de keyframe do GeckoLib (som, partícula, instrução personalizada) rodam em cada cliente, então não servem para começar efeitos nem para causar dano.

**Padrão recomendado (servidor manda nos dois):** no servidor, no mesmo tick, dispare a animação e o efeito; escreva a Timeline do efeito com os tempos da animação (segundos do Blockbench × 20 = ticks): um marcador no keyframe do golpe para o cue de dano, fases para preparação e recuperação.

```java
// No mod com GeckoLib (nomes do GeckoLib 4.x para 1.21.1; confira com javap na versão que o seu mod usa).
public class Golem extends Monster implements GeoEntity {
    private static final RawAnimation ATAQUE = RawAnimation.begin().thenPlay("animation.golem.ataque");
    // Animação de 1,25 s (25 ticks) com o golpe no keyframe de 0,6 s (tick 12).
    static final Timeline ROTEIRO = Timeline.builder(30)
        .phase("preparo", 0, 12, 4, 0)
        .phase("golpe", 12, 20, 0, 8)
        .track("feixe", keyframe(10, 0f), keyframe(12, 1f, Easing.EASE_OUT), keyframe(20, 0f, Easing.SMOOTH))
        .marker("golpe", 12)
        .build();
    static final EffectTriggers.EntityTrigger GOLPE = EffectTriggers.entity(GOLPE_ID).offset(0, 1.6, .8);

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "ataque", 0, state -> PlayState.STOP)
            .triggerableAnim("golpe", ATAQUE));
    }

    /** Chamado pelo goal de ataque, no servidor, quando o ataque começa. */
    void comecarAtaque(LivingEntity alvo) {
        triggerAnim("ataque", "golpe");   // o GeckoLib sincroniza a animação com os clientes
        GOLPE.fire(this, alvo);           // o efeito começa no mesmo tick; o marcador "golpe" cai no keyframe do golpe
    }
}
// Construtor do mod: Effects.register(GOLPE_ID, ...); Effects.timeline(GOLPE_ID, Golem.ROTEIRO);
// Effects.beams(GOLPE_ID, ...track(Beam.Param.REVEAL, "feixe")...);
// Effects.cue(GOLPE_ID, "golpe", ctx -> { /* dano em ctx.living(3) na frente: ctx.forward() */ });
```

Detalhes:

- **Ossos (a ponta do cajado, a mão):** as posições dos ossos só existem no cliente. Meça no Blockbench onde o osso está no keyframe do golpe e use esse deslocamento no `offset` do gatilho ou em `Point.facing`; com `followBody`, ele gira com o mob.
- **Keyframes de instrução** servem para detalhes só de cliente (partículas, som local). Não mande pacote do cliente para o servidor começar um efeito: atrasa e confia no cliente.
- **Golpes repetidos:** o limite por mob (4) e a recarga do gatilho seguram uma animação em loop.

## 5. O Finisher Engine do RespectCraft

Um esboço do lado do RespectCraft (nomes da API do Finisher são ilustrativos):

```java
// Servidor, ouvindo os eventos por tick do Finisher Engine.
void aoEvento(FinisherEvent e) {
    ServerLevel level = (ServerLevel) e.vitima().level();
    switch (e.tipo()) {
        case EVENT_HIT -> {
            ResourceLocation id = switch (e.forca()) { case 1 -> HIT_LEVE; case 2 -> HIT_MEDIO; default -> HIT_FORTE; };
            Effects.start(id, level, Placement.at(e.vitima().getBoundingBox().getCenter())
                .arrivingFrom(e.atacante().getEyePosition())      // "frente" = direção do golpe
                .withSource(EffectSource.entity(e.atacante())));
        }
        case EVENT_FALL -> Effects.start(QUEDA, level, Placement.at(e.vitima().position())
            .withYaw(e.atacante().getYRot()).withSource(EffectSource.entity(e.atacante())));
    }
}
```

- **Força 1 a 3:** três efeitos (ou um efeito com trilhas diferentes) escolhidos pela força; o golpe final pode ser `withPriority(Effects.Priority.HERO)` na definição, para nunca ser cortado pelo limite de desenhados.
- **Direção:** `arrivingFrom(atacante)` põe a "frente" do efeito na direção do golpe; feixes e luzes escritos com `Point.facing` saem para o lado certo, seja qual for o lado da câmera.
- **Câmera:** o Calamidade só toca câmera para efeitos com tomadas de cartão ou `AddonLayers.film`, e só para o conjurador. Efeitos do finisher sem `film` não mexem na câmera do RespectCraft.
- **Fontes e limites:** golpes rápidos de um mesmo mob podem passar de 4 instâncias se cada efeito durar muito. Use efeitos curtos (10 a 20 ticks), a fonte do jogador quando é ele quem finaliza (16 por padrão) ou `EffectSource.script()` para a cena inteira (sem limite por fonte, ainda dentro das 48 vagas não reservadas). O dono do servidor também pode subir `maxPerEntity`.
- **Oclusão:** os rigs são entidades desenhadas pelo jogo e escrevem profundidade; feixes atrás deles ficam escondidos como atrás de blocos.

## 6. Consumo como dependência

Sem Maven: uma task do Gradle baixa `calamidade_awakening-0.10.0.jar` do release do GitHub e a RimuLib do CurseForge, confere os dois por SHA-256 e os põe em `libs/`; o projeto compila com `compileOnly` e roda com `runtimeOnly`. O modelo completo, igual ao projeto real `examples/consumidor` (conferido pelo build), está em [docs/ai/usar-como-dependencia.md](ai/usar-como-dependencia.md), com integração opcional e problemas comuns.

**Versão atual (release v0.14.0):** o modelo e o `examples/consumidor` baixam `calamidade_awakening-0.14.0.jar` (SHA-256 `02851af9f70ae404783bc8eaec985b065221828f0ecad8f35bd22d287bcb0cb7`, também em `SHA256SUMS.txt` do release) e declaram `calamidade_awakening` em `[0.14.0,0.15.0)`. Código escrito para a 0.10 até a 0.13 compila sem mudança (a `api.v1` só cresce; `checkApiCompat` confere as bases 0.12.0, 0.13.0 e 0.14.0, geradas dos JARs publicados). Para subir de versão, troque `calamidadeVersao`, `calamidadeSha256` e a faixa do manifesto juntos; servidor e clientes usam a mesma versão do motor.

O Calamidade 0.10.0 declara `neoforge [21.1,21.2)`: os releases saem da 21.1.267 e a 0.10.0 foi conferida com a 21.1.265 (compilação, testes e o roteiro em jogo; veja [VALIDACAO.md](VALIDACAO.md)). `./gradlew build -Pneoforge.version=21.1.265` repete a conferência.

## 7. Limites e custo

| Item | Limite |
|---|---|
| Instâncias no servidor | 64; por padrão 16 só para jogadores |
| Por fonte (padrão) | 16 por jogador, 4 por mob, 2 por bloco; script sem limite por fonte |
| Recarga por fonte e efeito | 0..72000 ticks |
| Yaw e pitch | yaw qualquer valor finito (normalizado para [-180, 180)); pitch em [-90, 90] |
| `Placement.on(..., offset)` e `shifted` | até 1024 blocos por eixo |
| Gatilho de item | `reach` 0,5..256 blocos; durabilidade 0..1024 por partida |
| Gatilho de bloco | `period` até 72000 ticks; raio de aproximação 0,5..128 blocos |

Orientação não custa nada a mais por quadro: é uma rotação por ponto no cliente. Os gatilhos rodam no servidor e só leem o sinal de redstone ou a lista de jogadores do mundo por tick.

## 8. Testes

- `./gradlew build`: `OrientationCheck` (eixos, igual a `Vec3.directionFromRotation`, ortonormal, ida e volta, `Placement`, `Context`), `StageEventCheck` (o pacote `event_v2` leva orientação e fonte), `SourceCheck` (fontes, admissão, simulação aleatória, recarga), `TriggerCheck` (modos de bloco e validação dos gatilhos).
- No jogo: receitas 14 a 17, F7 ("Fontes"), `/calamidade status`. Roteiro completo em [TESTE-REAL.md](TESTE-REAL.md), parte 5.
