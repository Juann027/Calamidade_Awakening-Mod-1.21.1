# Mudanças

**Estado: 0.7.0 compilada (motor e vfx-sample 1.4.0); testes em jogo da 0.7.0 pendentes.** Veja docs/VALIDACAO.md e o roteiro em docs/TESTE-REAL.md.

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
