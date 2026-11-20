# Timeline: fases, trilhas, marcadores e sustain (0.7)

Até a 0.6, cada addon escrevia o roteiro do efeito com contas soltas sobre a idade: `exp(-|idade - 40| / 4)` no post, `cut(0, 36, ...)` na câmera, `Effects.cue(id, 40, ...)` no servidor. Os três números precisavam bater, e mudar o momento do impacto exigia caçar todos. A 0.7 junta esse roteiro num objeto só, a **Timeline**: fases com nome, trilhas de valores, marcadores e, para efeitos ambientes, um trecho em loop que só termina quando o servidor manda.

A Timeline é Java puro, sem tipos do Minecraft. O servidor, o cliente e os testes usam a mesma matemática. Efeitos registrados sem timeline continuam exatamente como na 0.6.

## Conceitos

- **Idade local.** Toda consulta recebe a idade *na timeline*, em ticks (com fração, para interpolar entre ticks). Sem sustain, ela é igual à idade real da instância. Com sustain, o motor converte a idade real com `localAge` antes de entregar ao addon; o addon não precisa saber do loop.
- **Fase.** Um intervalo com nome, `[início, fim)`, com subida e queda suaves. `weight` vale 0 fora da fase, sobe em `subida` ticks, fica em 1 e desce nos últimos `queda` ticks. Subida ou queda zero é um degrau. `progress` vai de 0 a 1 linearmente dentro da fase. Fases podem se sobrepor.
- **Trilha.** Uma lista de keyframes `(tick, valor, easing)`. `value` interpola entre eles; antes do primeiro e depois do último keyframe, mantém o valor da ponta. O easing de um keyframe define a curva do trecho que **chega** nele.
- **Marcador.** Um tick com nome. Serve para cues de servidor (`Effects.cue(id, "nome", cue)`) e para o cliente medir tempo relativo (`since("nome")`).
- **Sustain e release.** `sustain(início, fim)`: ao chegar em `fim`, a idade local volta para `início` e repete até a instância receber `release()`. `release(início)` define onde começa a saída (padrão: o fim do sustain). Depois do release, a idade local salta para esse ponto e segue até a duração do efeito, sem repetir.

Nomes seguem `[a-z0-9_]` (até 64 caracteres) e são únicos dentro de cada tipo: uma fase e um marcador podem ter o mesmo nome, como `impacto` no exemplo abaixo.

## Exemplo completo

O Sinal de Maré 1.4.0 (`examples/vfx-sample`) escreve o roteiro uma vez e o usa no servidor, no post e na câmera.

```java
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

static final Timeline SINAL = Timeline.builder(120)
    .phase("formacao", 0, 40, 8, 6)
    .phase("impacto", 38, 54, 2, 12)
    .phase("eco", 46, 120, 10, 34)
    .track("intensidade",
        keyframe(0, 0f),
        keyframe(36, .45f, Easing.EASE_IN),
        keyframe(40, 1f, Easing.OVERSHOOT),
        keyframe(60, .5f, Easing.SMOOTH),
        keyframe(120, 0f, Easing.EASE_OUT))
    .marker("impacto", 40)
    .build();

// Construtor comum do mod. Ordem obrigatória: register, timeline, cues.
Effects.register(SIGNAL, definicao);          // 120 ticks
Effects.timeline(SIGNAL, SINAL);
Effects.cue(SIGNAL, "impacto", Sample::impact); // dano no tick 40
```

No cliente (FMLClientSetupEvent):

```java
AddonLayers.post(SIGNAL, post -> {
    var t = post.timeline();                       // amostrador na idade local
    float impacto = t.weight("impacto");
    post.pass("vfxsample:signal_ripple", u -> {
        u.v3("RippleCenter", post.center());
        u.f4("RippleState", (float) t.since("impacto"), impacto, post.time().opacity(), (float) post.time().radius());
        post.uploadTracks(u, "Track_");             // uniform float Track_intensidade;
    });
    post.bloom(t.value("intensidade") * .25f);
    post.zoomBlur(impacto * .35f);                  // some com "Efeitos de movimento" desligado
    post.aberration(impacto * .5f);
});

AddonLayers.film(SIGNAL, film -> {
    var t = film.timeline();
    int impacto = t.start("impacto"), eco = t.end("impacto");
    return List.of(
        FilmApi.cut(0, impacto, (s, p, k) -> FilmApi.pose(s.aroundTarget(p, 3 - k, 1.5 + k, 5 - 2 * k), s.target(), 60f)),
        FilmApi.cut(impacto, eco, (s, p, k) -> FilmApi.pose(s.aroundTarget(p, 2, 2.5, 3.5 + k), s.target(), 52f + 14f * (float) k)),
        FilmApi.sweep(eco, t.end("eco") - 20, 10, (s, p, k) -> FilmApi.pose(s.aroundTarget(p, -4 + 8 * k, 3, 7), s.target(), 70f)));
});
```

Para mudar o impacto para o tick 50, basta mexer na timeline: o cue, o shader e o corte de câmera acompanham.

### Efeito ambiente com sustain: o Farol

```java
static final Timeline FAROL = Timeline.builder(140)
    .phase("acender", 0, 40, 30, 0)
    .phase("apagar", 80, 140, 0, 50)
    .track("feixe", keyframe(0, 0f), keyframe(40, 1f, Easing.EASE_OUT), keyframe(80, 1f), keyframe(140, 0f, Easing.SMOOTH))
    .track("pulso", keyframe(40, 0f), keyframe(60, 1f, Easing.SMOOTH), keyframe(80, 0f, Easing.SMOOTH))
    .marker("pico", 60)      // dentro do loop: o cue toca a cada volta
    .marker("apagar", 80)    // início da saída: o cue toca uma vez, no release
    .sustain(40, 80)
    .build();

Effects.register(FAROL_ID, new Effects.Definition(140, 3, 0xffffd27a,
    Effects.Shape.NONE, Effects.Material.NONE, null, null).withRange(256));
Effects.timeline(FAROL_ID, FAROL);

var handle = Effects.start(FAROL_ID, level, posicao).orElseThrow();
// ... minutos depois:
handle.release();   // apaga em 60 ticks; handle.stop() encerraria na hora
```

No exemplo, `/vfxsample farol start` acende o farol, `/vfxsample farol release` libera e `/vfxsample farol stop` encerra na hora.

## API

### Montagem (`Timeline.builder(duração)`)

| Chamada | Regras |
|---|---|
| `Timeline.builder(duração)` | 1 a 12000 ticks. A duração não pode passar da `Definition`. |
| `phase(nome, início, fim, subida, queda)` | `0 <= início < fim <= duração`; subida e queda `>= 0`; `subida + queda <= fim - início`. |
| `track(nome, keyframes...)` | Pelo menos um keyframe; ticks estritamente crescentes, de 0 à duração; valores finitos. |
| `keyframe(tick, valor[, easing])` | Easing padrão: `LINEAR`. Opções: `LINEAR`, `SMOOTH`, `EASE_IN`, `EASE_OUT`, `EASE_IN_OUT`, `OVERSHOOT` (passa cerca de 10% do alvo e volta). |
| `marker(nome, tick)` | `0 <= tick < duração`. |
| `sustain(início, fim)` | `0 <= início < fim < duração` (a saída precisa de pelo menos um tick). Uma vez só. |
| `release(início)` | Exige sustain; `fim do sustain <= início < duração`. |
| `build()` | Devolve a Timeline imutável. Erros de validação lançam `IllegalArgumentException`. |

### Consultas (`Timeline`)

| Método | Resultado |
|---|---|
| `weight(fase, idade)` | Peso de 0 a 1, com entrada e saída suaves (smoothstep). |
| `progress(fase, idade)` | 0 a 1, linear, dentro da fase; 0 antes e 1 depois. |
| `start(fase)`, `end(fase)` | Limites da fase, em ticks. |
| `value(trilha, idade)` | Valor interpolado; fora dos keyframes, o valor da ponta. |
| `tick(marcador)` | Tick do marcador. |
| `localAge(idadeReal, idadeDeLiberação)` | Converte tempo real em tempo da timeline. Use `-1` quando não houve release. Sem sustain, devolve a idade real. |
| `at(idadeLocal)` | Amostrador ligado a uma idade (veja abaixo). |
| `phases()`, `tracks()`, `markers()`, `has...(nome)`, `keyframes(trilha)` | Listagem, na ordem de declaração. |
| `sustained()`, `sustainStart()`, `sustainEnd()`, `releaseStart()` | Dados do loop; `-1` sem sustain. |

Nome inexistente lança `IllegalArgumentException`. Num callback de cliente, isso desativa o callback e registra o erro uma vez no log, o que ajuda a achar erros de digitação.

### Registro e servidor (`Effects`)

| Chamada | Resultado |
|---|---|
| `Effects.timeline(id, timeline)` | Liga a timeline ao efeito. Depois de `register` e antes de qualquer cue do efeito. Uma por efeito; só efeitos do seu namespace. |
| `Effects.cue(id, "marcador", cue)` | Cue no tick do marcador, resolvido no registro. Exige a timeline registrada antes. |
| `Effects.cue(id, tick, cue)` | Continua valendo. Com timeline, o tick é local. |
| `Handle.release()` | Libera uma instância com sustain. Devolve true só na primeira liberação de uma instância viva com sustain; nos outros casos, false e nada muda. |
| `Handle.stop()` | Continua encerrando na hora, com ou sem sustain. |
| `ctx.timeline()`, `ctx.localAge()`, `ctx.age()` | No cue: a timeline (null sem timeline), a idade local (igual ao tick do cue) e a idade real, que cresce a cada volta do loop. |

### Cliente (`AddonLayers`)

| Onde | Acesso |
|---|---|
| `world.timeline()`, `hud.timeline()`, `post.timeline()`, `time.timeline()` | Amostrador na idade local da instância: `weight(fase)`, `progress(fase)`, `value(trilha)`, `tick(marcador)`, `since(marcador)`, `localAge()`, `timeline()`. |
| `post.age()` | Idade local com a fração do quadro (igual à real sem sustain, como na 0.6). |
| `post.realAge()` | Idade real, que não volta no loop. Use para rotação contínua ou ruído que não deve saltar. |
| `post.uploadTracks(u, prefixo)` | Dentro do consumer de `pass`: envia cada trilha como `uniform float prefixo + nome`. Uniforms não declarados no shader são ignorados. |
| `film.timeline()` | A Timeline do efeito (vazia se não houver), para alinhar cortes: `film.timeline().start("ruptura")`. |
| `time.progress()` | Idade local dividida pela duração. Opacidade automática e acento de impacto também seguem a idade local. |

Efeitos sem timeline recebem um amostrador de uma timeline vazia: `localAge()` funciona e é a idade real; consultar fases, trilhas ou marcadores lança erro.

## Regras de sustain e release

- **Loop.** Antes do release, a idade local cresce normalmente até `fim do sustain` e então repete `[início, fim)`. O efeito não expira sozinho enquanto não for liberado; ele só termina por `release()` seguido do fim da saída, por `stop()`, por perda da âncora ou por descarregamento do chunk. O limite de 64 instâncias por servidor continua valendo.
- **Release.** A idade local salta para `releaseStart` e segue até a duração da `Definition`. A instância expira na idade real `liberação + duração - releaseStart`. O farol do exemplo, liberado a qualquer momento, apaga em 60 ticks.
- **Release antes do loop.** Se o release chega durante a introdução, a instância pula direto para a saída.
- **Sincronia.** O salto acontece no primeiro tick real cujos cues ainda não rodaram. Assim, um release pedido dentro de um cue vale a partir do tick seguinte, e servidor e clientes trocam para a saída no mesmo tick. O servidor envia a idade de liberação no pacote `release` a todos que já recebem a instância e, logo depois do pacote de início, a quem entra no alcance mais tarde.
- **Cues.** Cues dentro do loop rodam a cada volta. O cue no `releaseStart` roda uma vez, no tick do salto. Cues em ticks que o salto pula (entre a posição do loop e `releaseStart`, ou entre o fim do sustain e um `release(início)` posterior) não rodam. Se o servidor atrasar, ele repassa cada tick real (até 1200 por tick de servidor), então nenhuma volta é perdida.
- **Continuidade.** O motor não suaviza saltos. Para um loop sem emenda, dê às trilhas o mesmo valor em `início` e `fim` do sustain. Para uma saída sem salto, use no `releaseStart` o mesmo valor do trecho em loop (no farol, `feixe` vale 1 em 40, 80 e no início da saída, e `pulso` vale 0 nas duas bordas). O envelope automático de opacidade usa a idade local: deixe o loop dentro do platô (entre 14% e 72% da duração, para efeitos de até 1200 ticks).
- **Câmera.** As tomadas do `film` rodam em tempo real, a partir do início. Alinhe-as às fases anteriores ao loop.
- **Versões.** O pacote `release` é novo na 0.7.0. **Servidor e todos os clientes precisam usar a mesma versão do motor.** Um cliente 0.6 num servidor 0.7 não recebe o release e encerraria efeitos em loop pela duração; um addon com timeline exige o motor 0.7 dos dois lados (declare `[0.7.0,0.8.0)` no manifesto).

## Custo

- **Consultas.** `weight`, `progress`, `start`, `end` e `tick` são uma busca em mapa e algumas multiplicações. `value` percorre os keyframes da trilha até achar o trecho; mantenha cada trilha com poucas dezenas de keyframes.
- **Por quadro.** Cada instância visível cria um amostrador pequeno para cada camada que a desenha (world, hud, post). Não há cache a invalidar nem estado mutável.
- **Uniforms.** `uploadTracks` faz uma chamada `glUniform1f` por trilha e por passe. A RimuLib guarda a localização de cada nome; um uniform que o shader não declara custa só a busca nesse cache.
- **Servidor.** Sem sustain, os cues rodam como na 0.6. Com sustain, cada tick percorre a lista de cues do efeito uma vez.
- **Rede.** Um pacote pequeno (um `long` com a ordem da instância e a idade de liberação em varlong) por release e por entrada tardia numa instância já liberada. Nada é enviado por tick.
