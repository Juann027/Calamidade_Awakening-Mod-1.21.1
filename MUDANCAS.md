# Mudanças

**Estado: 0.5.0 a 0.6.2 compiladas na 0.6.2; testes em jogo pendentes.** Veja docs/VALIDACAO.md.

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
