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

## Medições pendentes

As ferramentas da 0.5.0 existem para fechar estes itens. O roteiro está em TESTE-REAL.md.

- Tempo de quadro com 1, 3, 6 e 12 eventos em GPU física, em 1080p e 1440p, nos três perfis de qualidade.
- Sincronização entre dois clientes em servidor dedicado: observador, entrada tardia, saída e retorno ao alcance, troca de dimensão.
- Pipeline em Fast, Fancy, Fabulous e com mods de renderização (Sodium/Embeddium, Iris/Oculus).

## Próximas versões

### 0.9 — Beam API e Light API sobre superfícies

Próxima versão planejada.

- **Beam API:** feixes e raios como objetos do motor (pontos, largura, núcleo e halo, ramificações), desenhados em lote num passe, com oclusão pelo terreno e largura mínima por pixel, em vez de cada addon reescrever `caSegmentGlow`.
- **Light API sobre superfícies:** luz colorida de efeitos projetada no terreno e nas entidades próximas (pela profundidade e normais reconstruídas), com orçamento de luzes por quadro e mistura com a atmosfera.

### Depois da 0.9

- **Trail API:** rastros de entidades e projéteis (fitas com histórico de posições), com orçamento de vértices.
- **Títulos:** cartões de título e legendas de habilidade sincronizados à Timeline (nome do golpe, fase do chefe), com acessibilidade de tamanho e duração.

### Evolução da API, sem versão definida

- Parâmetros de habilidade via arquivos de dados.
- Editor de sequências e previsão de custo.
- Trilha de áudio sincronizada e poses ligadas às fases da Timeline.
- Dados por instância enviados pelo servidor ao cliente (por exemplo, lista de alvos) para os ganchos de cliente.
- Transição suave (crossfade) opcional no release, para loops cujas bordas não coincidem.
- Cache de malhas estáticas e LOD adaptativo ao frametime.
