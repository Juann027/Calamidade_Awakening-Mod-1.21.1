# Ganchos avançados: pós-processamento, câmera e início (0.6)

A API de cartões cobre o caso comum: um shader com `CaCenter` e `CaEffect`, uma lista linear de tomadas e o acento de impacto. Efeitos de chefe e sequências longas precisam de mais. A 0.6 abre três ganchos de cliente que entregam ao addon as ferramentas da própria RimuLib, sem perder o que o motor já oferece: sincronização, seleção por prioridade, orçamento por qualidade, Atlas, Focus e cues.

Registre todos no setup **exclusivo do cliente** (FMLClientSetupEvent), um por ID de efeito.

## AddonLayers.post: passes próprios e pós-processamento

```java
AddonLayers.post(id, post -> {
    float pulse = (float) Math.exp(-Math.abs(post.age() - 40) / 4.0);
    post.pass("meuaddon:fase_ruptura", u -> {
        u.v3("Centro", post.center());
        u.f4("Estado", (float) post.age(), pulse, post.time().opacity(), (float) post.time().radius());
    });
    // Passe extra só quando há orçamento e a fase pede.
    if (post.age() > 60 && post.passesLeft() > 0)
        post.pass("meuaddon:fase_eco", u -> u.v3("Centro", post.center()));
    post.bloom(pulse * .4f);
    post.flash(0xFFFFE0B0, pulse * .6f);
});
```

| Membro | Conteúdo |
|---|---|
| pass(programa, uniforms) | Enfileira um shader de `assets/<namespace>/shaders/fx` com uniforms próprios. Retorna false, sem desenhar, quando o orçamento do quadro acabou. |
| passesLeft() | Passes que ainda cabem neste quadro. Use para escolher uma variante mais barata. |
| bloom, shake, desaturate, contrast | Combinam pelo máximo com os outros efeitos e com o motor; nunca sobrescrevem. Passam pela configuração de acessibilidade do jogador: a intensidade multiplica bloom, contraste e dessaturação, e o tremor pode estar desligado. |
| flash(cor, intensidade) | Clarão colorido, multiplicado pela intensidade configurada. Ignorado quando o jogador oculta clarões. |
| aberration, zoomBlur, vignette, afterimage, grain | 0.7.0. Mesmas regras de combinação e intensidade; zoom blur e afterimage obedecem a "Efeitos de movimento". |
| center() | Centro relativo à câmera, no espaço de `scenePosAt`. Já acompanha a entidade em `Effects.follow`. |
| age(), partial() | Idade em ticks com o tempo parcial, e o tempo parcial do quadro. Desde a 0.7.0, `age()` é a idade local da timeline (igual à real sem sustain) e `realAge()` a idade real. Para fases, prefira `timeline().weight("fase")` (TIMELINE-0.7.md). |
| timeline(), uploadTracks(u, prefixo) | 0.7.0: amostrador da timeline na idade local; envio das trilhas como uniforms float dentro de `pass`. |
| time() | Progresso (0 a 1), opacidade do envelope do motor, seed e raio. |
| distance(), instance() | Distância até a câmera e identificador da instância. |
| quality() | LOW, BALANCED ou HIGH. Reduza passos de raymarch nos perfis menores. |
| flashAllowed() | Falso quando o jogador oculta clarões. Evite também estrobos e mudanças fortes de exposição no shader. |
| screenEffects(), motionEffects() | 0.8.0: intensidade dos efeitos de tela (0 a 1) e a opção "Efeitos de movimento". Multiplique por `screenEffects()` qualquer mudança de tela inteira feita no seu próprio shader (impact frames, gradação de cor) e pule zoom ou borrão com `motionEffects()` falso. Exemplo: receita [impact frame](ai/receitas/04-impact-frame.md). |

Regras:

- **Orçamento por passe.** O quadro tem 1, 2 ou 4 passes de addon (LOW, BALANCED, HIGH). Shaders de cartão e cada `pass` dos posts consomem esse total na ordem de prioridade dos efeitos. Um efeito com shader de cartão e post já gasta uma vaga no cartão. Desde a 0.6.2, o callback é chamado mesmo sem vaga: `pass` retorna false, mas bloom, tremor, contraste e clarão continuam valendo. O painel F7 mostra os passes realmente enfileirados.
- **Outros efeitos de tela (0.7.0).** `aberration(f)`, `zoomBlur(f)`, `vignette(argb, f)`, `afterimage(f)` e `grain(f)` escrevem nos campos do ScreenFx da RimuLib 1.3.0. Combinam pelo máximo e passam pela intensidade configurada; a vinheta usa `addVignette`, que mantém a cor do pedido mais forte. Zoom blur e afterimage são ignorados quando o jogador desliga **Efeitos de movimento** (`motionEffects`).
- **Arrays de uniforms.** Veja a seção abaixo.

### Arrays de uniforms (conferido no JAR da RimuLib 1.3.0)

O `Uniforms` que o `pass` entrega é a classe interna `PostFxRenderer$Binder`. Pelo `javap -c`:

- `f1`, `f2`, `f3` e `f4` passam o nome recebido, sem alteração, para `ShaderProgram.location(String)`. Esse método guarda o resultado num `HashMap` por nome (`computeIfAbsent`) e, na primeira vez, chama `GL20.glGetUniformLocation(id, nome)` com a mesma string. Não há corte em `[`, troca de caracteres nem prefixo.
- Se a localização vier `-1` (uniform inexistente ou eliminado pelo compilador do driver), a chamada `glUniform*` é pulada em silêncio.
- `vec4s(nome, float[])` procura primeiro `nome + "[0]"` e, se não achar, `nome`; depois envia o array inteiro com uma única chamada `glUniform4fv`.

**Conclusão:** nomes indexados como `u.f4("Pontos[3]", ...)` chegam ao `glGetUniformLocation` exatamente como escritos. O OpenGL aceita `nome[índice]` para elementos de arrays de tipos básicos, então a forma funciona, desde que o shader declare o array e use o elemento (elementos não usados podem ser eliminados e receber `-1`). Para enviar muitos `vec4` de uma vez, prefira `u.vec4s("Pontos", dados)`, com `dados.length` múltiplo de 4: uma chamada só, em vez de uma por elemento. Arrays de `float`, `vec2` ou `vec3` não têm envio em bloco; use nomes indexados ou empacote em `vec4`.

Isso foi conferido no bytecode; o comportamento do driver continua a ser confirmado em jogo (TESTE-REAL.md).
- **Falhas.** Uma exceção no callback desativa aquele post até reiniciar o jogo e é registrada uma vez no log. Os passes enfileirados antes da falha continuam contados.

## AddonLayers.film: câmera própria

```java
AddonLayers.film(id, film -> List.of(
    FilmApi.cut(0, 40, (s, partial, k) ->
        FilmApi.pose(s.aroundTarget(partial, 3 - k, 2, 6), s.target(), 60f)),
    FilmApi.sweep(40, 120, 12, (s, partial, k) ->
        FilmApi.pose(s.aroundTarget(partial, -6 + 12 * k, 4, 9), s.target(), 72f))));
```

O callback roda no cliente do conjurador quando a instância começa e recebe o `Subject` já criado pelo motor (`film.subject()`), o centro, a duração e o identificador. Devolva tomadas da RimuLib (`FilmApi.cut`, `sweep`, `pose`, os helpers do Subject). O motor toca a sequência, cuida do pular, da tecla O e do encerramento junto com a instância. O gancho substitui a lista `camera` do cartão e também funciona para efeitos sem cartão iniciados com `Effects.start(id, jogador, posição)`. Devolva uma lista vazia para não ter câmera.

## AddonLayers.start: início em todos os clientes

```java
AddonLayers.start(id, start -> {
    if (start.age() > 2) return; // quem chegou atrasado não ouve o início
    start.level().playLocalSound(start.center().x, start.center().y, start.center().z,
        SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1f, 1f, false);
});
```

Roda uma vez em cada cliente que aceita a instância, inclusive quem entra atrasado ou volta ao alcance da definição (`age()` maior que zero). Serve para estado local, partículas e sons pontuais. `caster()` é verdadeiro no cliente de quem conjurou.

## Quando ir direto à RimuLib

| Necessidade | Caminho |
|---|---|
| Quebrar ou esculpir terreno | `CarveApi` da RimuLib dentro de um cue de servidor (`Effects.cue`/`onImpact`). Defina área máxima, blocos protegidos e permissões. |
| Cutscene independente de efeito | `FilmApi` direto no addon, com pacote próprio. Para câmera ligada a uma instância, prefira `AddonLayers.film`. |
| Menu ou tela própria | `MenuApi`/`UiApi` ou um Screen do addon. O painel do Focus continua em `AddonLayers.panel`. |
| Passe de tela sem instância do Calamidade | Um `FxApi.stage` do próprio addon. Para qualquer coisa ligada a uma instância, prefira `AddonLayers.post`, que já traz tempo, centro e orçamento. |

## Exemplo completo

O Sinal de Maré (1.4.0 em diante) usa os três ganchos, guiados pela Timeline; na 1.5.0 ele também tem uma atmosfera curta no impacto (ATMOSFERA-0.8.md). Em `SampleClient.java`: um passe `signal_ripple` com uniforms próprios e a trilha `intensidade` enviada por `uploadTracks` (anéis dourados que saem do impacto, pedidos a partir de dois ticks antes da fase `impacto` e omitidos em LOW porque o cartão já usa a única vaga), bloom, tremor, zoom blur e aberração no impacto; uma câmera com cortes alinhados às fases; e um som para quem assiste de fora. O shader está em `assets/vfxsample/shaders/fx/signal_ripple.fsh`.
