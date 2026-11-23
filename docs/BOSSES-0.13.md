# Bosses (0.13) — Boss API, BossBar API e Rig Anchors

> **Estado:** em desenvolvimento no branch `claude/bosses-0.13`, criado em `8cfd93a` (o `origin/main` do release
> v0.12.0 mais dois commits só de `VALIDACAO.md`). A 0.13 **não** contém nada da 0.14 (Custom Sky Engine,
> branch `claude/ceu-personalizado`, intocado). Este capítulo começou como o plano aprovado (Etapa 0) e é atualizado
> a cada etapa com o que foi realmente feito.

A 0.13 transforma o Calamidade numa biblioteca para **chefes cinematográficos de addons**. O motor não cria
criaturas, barras ou eventos sozinho: tudo começa porque um addon pediu.

## 1. Arquitetura aprovada

Um **boss é um controlador** preso a uma `LivingEntity` do addon. A entidade continua dona da IA, dos atributos, do
movimento, do modelo, do spawn, do loot e dos comportamentos próprios. O Calamidade dá fases e transições, gatilhos,
Timeline, BossBar, Rig Anchors, integração com os efeitos existentes, sincronização e ferramentas.

| Camada | Onde (arquivos novos) | Papel |
|---|---|---|
| Núcleo puro | `api/BossMachine`, `api/AnchorMath`, `api/BossBarMath`; `api/SourceLimits` (regra `perBoss`) | Máquina de fases determinística, matemática dos anchors e da barra; sem Minecraft, no `checkBudget` |
| API comum | `api/v1/Boss`, `api/v1/Bosses`, `api/v1/RigAnchors`; adições em `Point`, `EffectSource`, `Effects` | Definição e controle do chefe, anchors estáticos (servidor e cliente) |
| API de cliente | `api/v1/client/AnchorCapture`, `api/v1/client/BossBars` | Captura de pose e providers; estilos de barra |
| Servidor | `boss/BossServer`, `boss/BossData`, `boss/BossCommand` | Estado autoritativo, gatilhos, barra vanilla, persistência, comandos |
| Cliente | `client/boss/AnchorCache`, `client/boss/BossBarClient` | Anchors por quadro, desenho da barra |
| Rede | `net/BossSync`, canal **`boss_v1`** | Um pacote por mudança de estado, e a quem chega depois |

Para facilitar a integração futura com a 0.14, o código novo fica em arquivos novos; nos arquivos que a 0.14 também
altera (`Effects`, `VisualKind`, `StageScene`, `ClientHooks`, `ClientConfig`, `CalamidadeAwakening`, `build.gradle`,
`neoforge.mods.toml`) as mudanças são curtas e localizadas.

## 2. APIs do jogo conferidas com javap (Etapa 0)

NeoForge 21.1.267 (`build/moddev/artifacts/neoforge-21.1.267-merged.jar`) e RimuLib 1.3.0:

| Uso | Tipo e membros conferidos | Conclusão |
|---|---|---|
| Barra vanilla | `ServerBossEvent(Component, BossBarColor, BossBarOverlay)`, `setProgress`, `setName`, `setColor`, `setOverlay`, `setDarkenScreen`, `setPlayBossMusic`, `setCreateWorldFog`, `addPlayer`, `removePlayer`, `removeAllPlayers`, `setVisible`, `getPlayers`; `BossEvent.getId()` (UUID) | Base da BossBar: o jogo envia pacotes só quando um valor muda |
| Cores e divisões | `BossBarColor` PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE; `BossBarOverlay` PROGRESS, NOTCHED_6/10/12/20 | Espelhados em enums próprios da API (sem expor tipos vanilla na assinatura) |
| Barra no cliente | `CustomizeGuiOverlayEvent.BossEventProgress` (cancelável; `getBossEvent()` → `LerpingBossEvent`, `getX`, `getY`, `getIncrement`, `setIncrement`); `BossHealthOverlay.render` chama `ClientHooks.onCustomizeBossEventProgress` e, se cancelado, **pula barra e nome** mas soma o incremento | Estilo próprio = cancelar e desenhar; sem estilo (ou "forçar vanilla"), nada muda |
| Dano | `LivingIncomingDamageEvent` (cancelável; `getSource`, `getAmount`); `LivingDamageEvent.Post` (`getNewDamage`, `getSource`); `LivingDeathEvent` (cancelável, `getSource`) | Invulnerabilidade opt-in por fase; contagem de golpes e `onHurt` no `Post`; morte no `LivingDeathEvent` |
| Persistência | `AttachmentType.builder(Supplier).serialize(Codec)`, `IAttachmentHolder.setData/getExistingData/removeData`; `NeoForgeRegistries.Keys.ATTACHMENT_TYPES` | Estado do chefe salvo na própria entidade |
| Ciclo de vida | `EntityJoinLevelEvent(loadedFromDisk)`, `EntityLeaveLevelEvent`, `Entity.getRemovalReason()` (KILLED, DISCARDED, UNLOADED_TO_CHUNK, UNLOADED_WITH_PLAYER, CHANGED_DIMENSION), `PlayerEvent.PlayerLoggedOutEvent` | Retomar ao carregar, limpar ao sair, morte só com KILLED |
| Corpo e cabeça | `LivingEntity.yBodyRot/yBodyRotO/yHeadRot/yHeadRotO`, `getHealth`, `getMaxHealth`; `Entity.getEyeHeight`, `getViewXRot` | Anchors estáticos e aproximação no servidor |
| Captura | `PoseStack.Pose.pose()` (`Matrix4f`), `ModelPart.translateAndRotate(PoseStack)`, `getChild`; `RenderLivingEvent.Post` (`getEntity`, `getPoseStack`, `getPartialTick`) | Captura de pose no render do addon; helper para `ModelPart` |
| Comandos | `RegisterCommandsEvent.getDispatcher()` | `/calamidade boss` registrado à parte (o Brigadier junta literais iguais) |
| **Ordem do quadro** | RimuLib `LevelRendererMixin.rimulib$render`: `@Inject(method = "renderLevel", at = @At("RETURN"))` chama `PostFx.render(Camera, float)`; os passes do Calamidade (e a coleta de feixes e luzes, que resolve os `Point`) rodam nessa camada `FxApi` | **Os efeitos são montados depois de todas as entidades do quadro**: a pose capturada no render da entidade vale **no mesmo quadro**, sem atraso |

## 3. Contrato público planejado

### 3.1 Boss API (servidor autoritativo)

```java
static final Boss SENTINELA = Boss.builder(id("sentinela"))
    .bar(Boss.Bar.of(Component.translatable("boss.vfxsample.sentinela")).color(Boss.BarColor.PURPLE)
        .segments(true).style(id("calamidade_awakening:segmented")).range(64))
    .phase("despertar", p -> p.enter(DESPERTAR).invulnerable().label("boss.vfxsample.despertar"))
    .phase("combate",   p -> p.sustain(AURA).every(120, ctx -> ctx.start(OLHAR)))
    .phase("furia",     p -> p.enter(FURIA).worldEvent(ECLIPSE, WorldEvents.Scope.AREA, 96))
    .phase("derrota",   p -> p.enter(QUEDA))
    .transition("despertar", "combate", Boss.Trigger.afterTicks(80))
    .transition("combate", "furia", Boss.Trigger.healthBelow(.4f))
    .transition(Boss.ANY, "derrota", Boss.Trigger.death())
    .build();

Bosses.define(SENTINELA);                                  // construtor comum do mod
Optional<Bosses.Handle> h = Bosses.attach(mob, SENTINELA.id());   // servidor, quando o addon quiser
h.get().signal("carga_completa");  h.get().phase();  h.get().detach();
```

- **Gatilhos (`Boss.Trigger`)**: `afterTicks(n)`, `healthBelow(f)`, `healthAbove(f)`, `signal(nome)`, `hurt(n)`,
  `death()`, `when(intervalo, predicado)`. Avaliados só no servidor, uma vez por tick, na ordem declarada; **no
  máximo uma transição por tick**; permanência mínima por fase (`minDwell`, padrão 20 ticks) contra oscilação; após a
  morte, só regras de morte.
- **Fases (`Boss.Phase`)**: efeitos de entrada e de saída, um `sustain` (solto com `release()` quando a fase acaba),
  ações periódicas `every(n, ...)`, evento global opcional (iniciado na entrada, solto na saída e na morte),
  `onEnter`/`onExit`/`onHurt`, rótulo da barra (chave de tradução), cor própria e `invulnerable()` (opt-in, nunca
  padrão).
- **Sequências**: `transition(de, para, gatilho).through(efeito)` toca a Timeline do efeito e trava a máquina até ele
  acabar (ou até o marcador `fim`, se existir); só a morte quebra a trava.
- **Tempo e persistência**: o estado salvo na entidade é fase, **tempo decorrido na fase** (ticks em que o chefe foi de
  fato processado), golpes e alvo de sequência pendente. Tempo de mundo com o chunk descarregado ou o servidor
  desligado não conta; uma sequência pendente termina ao recarregar. Chefe salvo com id desconhecido (addon removido)
  é ignorado e limpo, com aviso no log.
- **Efeitos do chefe** começam com `EffectSource.boss(entidade)` e seguem a entidade (`Effects.follow`).

### 3.2 `maxPerBoss` e a origem dos efeitos

- `EffectSource.boss(entity)` é uma fonte `ENTITY` com a mesma identidade (UUID) da entidade e uma **marca de
  controlador**. `equals`/`hashCode` ignoram a marca: recarga e contagem por fonte são as da entidade, então alternar
  entre "boss" e "entidade" não dá vagas novas nem pula recargas.
- Na admissão, o servidor conta **todas** as instâncias vivas com aquele UUID (marcadas ou não) e escolhe o limite:
  - início marcado **e** `Bosses` confirma um controlador válido anexado àquela entidade → `maxPerBoss` (padrão 8,
    configurável 1..32);
  - qualquer outro caso (mob comum, marca sem controlador, controlador já removido) → `maxPerEntity` (padrão 4).
- Consequência: efeitos comuns do mob continuam no teto de 4 mesmo num chefe; os do controlador podem chegar a 8 no
  total da entidade. O teto global de 64, a reserva de jogadores (só `TOTAL − playerReserve` para não jogadores), as
  recargas, a prioridade de desenho, os orçamentos por quadro e os limites de eventos globais e de terreno valem como
  antes. Acima do limite: `Optional.empty()`, contado em `/calamidade status` como `SOURCE_LIMIT`, sem erro nem
  trava.

### 3.3 BossBar API

- Base: `ServerBossEvent` vanilla (nome, progresso, 7 cores, divisões, escurecer, música, névoa). O Calamidade decide
  quem vê (mesma dimensão, dentro de `range`, 16..256, padrão 64), revisando a lista a cada 10 ticks (entrada tardia,
  troca de dimensão, respawn) e removendo no logout, na morte, no descarregamento e no `detach`.
- Progresso enviado só com variação ≥ 0,5% ou a cada 5 ticks se mudou (`BossBarMath.shouldSend`).
- Extensão pelo `boss_v1` (uma vez por mudança): estilo, rótulo da fase, cor de destaque, marcas dos segmentos (os
  limiares de `healthBelow`), vulnerabilidade e o tick de início da fase.
- Cliente: estilos `calamidade_awakening:segmented` (embutido) e os registrados por `BossBars.style(id, renderer)`.
  Barra "fantasma" de dano, clarão na troca de fase (respeita `flashAllowed`) e troca de rótulo, calculados pelo
  carimbo de tempo, sem pacotes extras.
- Acessibilidade (config de cliente, seção `bossBar`): escala 0,5..2, alto contraste, rótulo da fase em texto, **forçar
  barras vanilla**. Sem estilo conhecido o vanilla aparece igual (fallback).

### 3.4 Rig Anchors

Uma `AnchorPose` tem posição, frente e cima (ortonormais). Resolução por quadro, com cache por (entidade, nome):

1. **Captura no render** — `AnchorCapture.capture(entity, "boca", poseStack.last().pose())` no renderizador do addon
   (vanilla, `RenderLayer`, GeckoLib ou outro). Uma cópia de matriz, só para anchors que algum efeito ativo pediu
   (`AnchorCapture.wanted`).
2. **Provider** — `AnchorCapture.provider(type, provider)` para quem calcula a pose sem passar pelo render.
3. **Estático** — `RigAnchors.define(type, "boca", RigAnchors.Spec.head(0, .2, .9))`, preso ao corpo ou à cabeça.
4. Plano B: olhos da entidade.

Fora da tela, a última captura acompanha o deslocamento e o giro do corpo por até 10 ticks; depois vale o estático.

- `Point.socket(nome, direita, cima, frente)` e `Point.socketAim(nome, alcance)` (raio na frente do anchor) — feixes e
  luzes passam a sair da boca ou dos olhos sem mudar o pacote `event_v2`.
- `Effects.anchorSocket(id, nome)` (na definição, registrada nos dois lados): o centro inteiro da instância segue o
  anchor (partículas nos olhos, explosão no corpo).

**Anchors visuais × autoritativos.** A pose capturada no cliente só orienta o visual (feixes, luzes, partículas).
Ela **nunca** volta ao servidor. Para dano, colisão ou qualquer consequência, o servidor usa `RigAnchors.serverPose`
(definição estática + posição, corpo e cabeça da entidade no servidor), uma aproximação documentada; o centro de
servidor de uma instância com `anchorSocket` é essa aproximação.

**GeckoLib** não é dependência. A receita mostra como capturar a matriz de um osso num `GeoRenderLayer` do addon (com
os eixos do modelo informados), com nomes a conferir com javap na versão do GeckoLib do addon. Para modelos vanilla,
`AnchorCapture.capturePart` aplica uma cadeia de `ModelPart`.

## 4. Riscos técnicos

| Risco | Tratamento |
|---|---|
| Entidade fora da tela (sem render no quadro) | Extrapolação da última captura por 10 ticks, depois o estático; documentado |
| Shaderpacks (Iris/Oculus), Sodium/Embeddium alterando o render das entidades | A captura acontece no render da entidade, como os próprios modelos; não afirmamos compatibilidade sem teste (pendência) |
| Eixos de modelo diferentes (vanilla invertido, GeckoLib não) | `capture` com eixos explícitos; padrão vanilla (frente −Z, cima −Y no espaço do modelo) |
| Callback de addon com erro | Capturado e registrado com id do chefe e fase; 3 falhas seguidas pausam o controlador (barra some, efeitos soltos) com `ERROR` no log, nunca em silêncio |
| Cancelar dano conflitar com outros mods | Só com `invulnerable()` na fase; prioridade normal do evento |
| Barra vanilla tem 7 cores | Visual rico só no cliente com o mod (obrigatório por causa da rede); o vanilla é o fallback |
| Persistência com addon atualizado (fase renomeada) | Fase desconhecida volta à primeira; alvo pendente desconhecido descartado |
| Merge futuro com a 0.14 | Código em arquivos novos; mudanças curtas nos arquivos comuns (lista na seção 1) |
| Multiplayer real | Testes automatizados com um jogador; dois jogadores reais continuam pendência manual |

## 5. Estratégia de branches

```
v0.12.0 ── origin/main (8cfd93a) ──┬── claude/ceu-personalizado (ab2a97d)   0.14, intocado
                                   └── claude/bosses-0.13                   0.13 (este trabalho)
```

- Nada de merge, rebase ou cherry-pick com a 0.14 durante a 0.13; `main`, tags e releases não são alterados.
- Integração futura (cada passo com autorização): PR da 0.13 para `main` e tag `v0.13.0`; branch
  `claude/integracao-0.13-0.14` a partir de `ceu-personalizado` com `git merge origin/main` (merge comum, sem
  reescrever histórico); resolver conflitos, regenerar `API-REFERENCIA.md` e `MANUAL-GERAL.md`, levar a base de
  compatibilidade binária da 0.14 ao JAR 0.13.0, versão `0.14.0` e faixa do vfx-sample `[0.14.0,0.15.0)`, juntar os
  canais de rede; uma rodada consolidada; só então avançar `ceu-personalizado`.

## 6. Testes de aceitação

Política proporcional ao risco (AGENTS.md):

- **Durante:** `./gradlew build` com verificações puras novas no `checkBudget`: `BossCheck` (máquina: ordem, uma
  transição por tick, permanência, sequência, morte, restauração), anchors (eixos, ortonormalização, extrapolação,
  estático no corpo e na cabeça), barra (envio, segmentos, fantasma, clarão), `SourceCheck` (`maxPerBoss`: marca sem
  controlador, contagem compartilhada, reserva) e o codec do `boss_v1`.
- **Ao fechar o servidor:** cenários de chefe no `testeJogo` numa rodada (attach, fase por tempo, por dano, sinal,
  invulnerável, limite de efeitos, persistência com reinício, limpeza na morte e no `detach`, barra com jogador
  simulado ausente).
- **Ao fechar o cliente:** `xvfb-run ./gradlew testeChefe`, numa só inicialização: anchor capturado × estático,
  feixe saindo do anchor, barra segmentada, forçar vanilla, escala, capturas.
- **Não rodar** sem mudança no que validam: `testeCeu`, referências de céu, `testeCliente` (escritor de terreno
  intocado), `testeMedicao`, `testeAtlas`.

Critérios: chefe com 3 fases, barra e anchors sem GeckoLib; nada aparece sem o addon pedir; compatibilidade binária
com a 0.12.0; `event_v2` igual; só o canal novo `boss_v1`; nenhum pacote por tick; servidor dedicado sem classes de
cliente; reinício na fase certa; entrada tardia com barra e fase certas; acessibilidade da barra; build, vfx-sample e
consumidor verdes; resultados em `VALIDACAO.md`; nada da 0.14 no branch.
