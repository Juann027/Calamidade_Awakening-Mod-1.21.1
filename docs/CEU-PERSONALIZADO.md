# Custom Sky Engine: arquitetura (0.14 e 0.15)

Documento de trabalho do branch `claude/ceu-personalizado`. Fases 0, 1 e 2 (Backdrop) concluídas; a fase 3 espera aprovação. Ainda não é capítulo do manual: entra em `gerar_manual.py` quando a 0.14 for lançada (a versão do build continua 0.12.0 até lá).

Marcadores: **[V]** verificado (código, `javap`/fontes do NeoForge 21.1.267, bytecode da RimuLib 1.3.0 ou experimento em jogo, com a fonte indicada); **[H]** hipótese; **[D]** decisão de arquitetura.

## Princípios

1. **Só aditivo.** Os sete fenômenos procedurais da 0.12.0 (`BLACK_HOLE`, `PORTAL`, `ECLIPSE`, `RIFT`, `CELESTIAL_BODY`, `AURORA`, `DISTANT_BOLTS`), seus shaders, parâmetros, prioridades, lentes e integrações ficam como estão. Os tipos novos são classes novas; `Sky.Type` não ganha constantes (o `sky.fsh` usa o ordinal e addons podem ter `switch` sobre ele).
2. **Tudo por escolha do addon.** Sem addon pedindo, o quadro é o da 0.12.0: nenhum passe, alvo, textura ou malha nova.
3. **Quem não usa não paga.** Cada modalidade tem o próprio caminho de desenho; um céu estático não liga o passe procedural.
4. **Limites medidos.** Os números das propostas são pontos de partida; só viram limite depois de benchmark.

## Guardas de regressão (fase 1; atualizadas na fase 2)

| Guarda | Como roda | O que garante |
|---|---|---|
| Compatibilidade binária (`checkApiCompat`, no `check`) | `scripts/ApiCompat.java` contra `scripts/api-base/api-v1-0.12.0.txt` (89 classes, 1038 membros, do JAR byte a byte igual ao release v0.12.0) | Nenhuma classe ou membro público/protegido some ou muda de descritor; cabeçalhos iguais; tipos extensíveis sem método abstrato novo; enums sem constante removida ou reordenada. Autoteste com as cinco quebras simuladas. |
| Referência visual (`./gradlew checkCeuReferencia`) | `scripts/referencia_ceu.cjs`: passes reais `sky` e `atmosphere` (SwiftShader, 8 bits entre os passes) em 26 cenas contra `src/test/resources/referencia-ceu/` | As 15 cenas da 0.12.0 (sete fenômenos, quatro camadas juntas, atmosfera sozinha) iguais à 0.12.0 — na fase 2, com diferença zero — e 11 cenas com fundos estáveis. |
| Composição dos fundos (`./gradlew checkFundoComposicao`, roda depois do anterior) | `scripts/fundo_composicao.cjs`: sondas de pixel na prévia | Atmosphere sobre fundo opaco, parcial e com buraco de alfa; sol do jogo apagado sob fundo opaco e parcial; eclipse escurecendo o fundo; dois controles negativos (o comportamento do protótipo) que precisam falhar. |
| Testes em jogo existentes | `testeJogo`, `testeCeu` | Eventos globais, perfis, eclipse em três dimensões, respawn, relogin. |
| Backdrop em jogo (`xvfb-run ./gradlew testeBackdrop`) | Fase 1: protótipo só no mod de teste. Fase 2: os fundos reais do motor (`RoteiroBackdrop`, `SondasDoQuadro`, texturas de `scripts/gerar_fundos_teste.py`) | 34 afirmações com sondas de pixel (lista na seção da fase 2 e em VALIDACAO.md). |

## Fatos verificados que a arquitetura usa

- **[V] Ordem do `LevelRenderer.renderLevel`** (`javap -c`): `RenderSystem.clear` (cor e profundidade, offset 338) → `renderSky` (450) → `AFTER_SKY` (453) → seções sólidas, entidades (`AFTER_ENTITIES`), block entities, translúcidos, partículas (`AFTER_PARTICLES`), nuvens (`renderClouds`, 2908), chuva (`AFTER_WEATHER`) → `AFTER_LEVEL`.
- **[V] O céu do jogo não grava profundidade:** `renderSky` chama `depthMask(false)` antes de desenhar (offset 193) e `depthMask(true)` só no fim; o céu do End (`renderEndSky`) também. Com névoa (`isFoggy`), câmera em neve em pó ou lava, ou cegueira/escuridão (`doesMobEffectBlockSky`), `renderSky` não desenha nada; com `skyType` diferente de NORMAL e END, também não.
- **[V] A RimuLib executa os passes no fim do `LevelRenderer.renderLevel`** (`LevelRendererMixin`: injeções em `HEAD` para captura, antes de `PostChain.process` para guardar a profundidade e em `RETURN` para desenhar), e o NeoForge dispara **`AFTER_LEVEL` depois**, em `GameRenderer.renderLevel` (offset 598, logo após a chamada do `renderLevel` do nível, 580). Ordem real no quadro: `AFTER_SKY` → … → `AFTER_WEATHER` → **passes da RimuLib** → `AFTER_LEVEL` → mão. Tudo o que for desenhado até `AFTER_WEATHER` entra nos passes (lente, escurecimento, atmosfera); o que for desenhado em `AFTER_LEVEL` (o Atlas, por exemplo) fica por cima deles. Comprovado também em jogo (sequência dos eventos no `testeBackdrop`).
- **[V] `Uniforms.glTexture` liga `GL_TEXTURE_2D`** (constante 3553 no bytecode, com `glBindSampler`): `samplerCube` não passa pelos passes da RimuLib.
- **[V] `RenderTarget` cria cor `GL_RGBA8`** (32856): um alvo próprio tem o mesmo limite de 8 bits da 0.12.
- **[V] `TextureManager`** guarda as texturas `Tickable` registradas; `TextureAtlas` é `Tickable` e `TextureAtlasHolder(TextureManager, atlas, info)` existe: atlas próprio com animação `.mcmeta`.
- **[V] Modelos e animação no jogo:** `AnimationLoader` do NeoForge lê `assets/<ns>/neoforge/animations/entity/*.json` para o `AnimationDefinition` vanilla; `KeyframeAnimations.animate` pede um `HierarchicalModel`; `ObjLoader` registrado como `neoforge:obj`; `ModelEvent.RegisterAdditional` com `ModelResourceLocation.standalone`. (Uso na 0.15; os formatos reais de exportação do Blockbench ainda serão validados antes de qualquer leitor.)
- **[V] `ProfileSkyEffects`** já pode sobrescrever `renderClouds` (hoje repassa ao efeito base).

## Backdrop: o que o experimento comprovou

`xvfb-run -a ./gradlew testeBackdrop` (22/11/2026, Mesa llvmpipe, 1280 × 720, mundo plano do `testeJogo`, vfx-sample 1.9.0): **13 de 13 afirmações** (três rodadas; a primeira tinha 12 de 13, veja a linha da ordem). O protótipo é um cubo de raio 50 em volta da câmera, só com a rotação da vista, textura 3 × 2 com uma cor por face, desenhado em `AFTER_SKY` com `depthMask(false)`, sem teste de profundidade e sem descarte de faces, pelo shader `position_tex_color` do jogo. Capturas em `run/teste-backdrop/screenshots/` (fora do repositório).

| Pergunta | Resultado | Tipo |
|---|---|---|
| Ordem em relação à RimuLib | `AFTER_SKY` < camada da RimuLib < `AFTER_LEVEL` em todo quadro (a primeira rodada afirmava outra ordem, a minha hipótese errada: falhou e foi corrigida com o `javap -c` acima) | PASS |
| Framebuffer | `AFTER_SKY` desenha no framebuffer principal | PASS |
| Profundidade | 1,0 no centro em `AFTER_SKY` antes e depois do fundo; 1,0 num pixel de céu em `AFTER_LEVEL` (o passe `sky` continua vendo céu); 0,971 num pixel de chão | PASS |
| Cobertura completa | Fundo opaco cobre o sol ao meio-dia, a lua e as estrelas à meia-noite (0 pixels brilhantes) e todas as amostras da metade de cima | PASS |
| Transparência (parcial) | Alfa 0,5: (93,126,242) medido contra (94,126,243) esperado pela mistura com o céu do jogo | PASS |
| Nuvens | Desenhadas depois de `AFTER_SKY`: ficam na frente do fundo (25,8% do céu amostrado eram nuvem) | PASS |
| Terreno | Cobre o fundo; o céu acima do horizonte é fundo | PASS |
| Lente | O buraco negro do exemplo entorta a grade do fundo (≈ 93% dos pixels de grade deslocados); o fundo entra na lente sem código novo | PASS |
| Nether | `AFTER_SKY` acontece mesmo sem céu do jogo; acima do teto o fundo aparece | PASS |
| **Atmosphere** sobre fundo opaco | Pixel azul (60,80,230) vira (0,0,55): o passe soma a diferença entre o gradiente e o céu **do jogo**, que não está mais lá | INFO: precisa de mudança |
| **Eclipse** sobre fundo opaco | Disco, coroa e raios corretos por cima; o fundo escurece com o mundo (desejado), mas as cores também se deslocam pelo mesmo motivo da Atmosphere (o eclipse do exemplo traz uma atmosfera). A marca do apagamento do sol sobre o fundo não ficou isolável nesta medição (razão de brilho dentro/fora do quadrado do sol entre 1,43 e 4,33 nas rodadas, com a coroa por cima) | INFO: precisa de regra |
| Cegueira | O jogo não desenha céu (centro preto); o protótipo desenha assim mesmo | INFO: precisa de regra |
| Horizonte | Uma linha clara no fim do terreno: a névoa do terreno distante usa a cor de névoa do jogo, não a do fundo | INFO: precisa de opção |
| Custo (software, só tendência) | Três rodadas, mediana sem → com fundo: 22,0 → 23,8; 21,9 → 26,4; 25,9 → 28,6 ms (p95 28,8 → 30,7; 29,1 → 32,2; 32,1 → 40,3). Cerca de 2 a 4,5 ms por quadro em llvmpipe a 1280 × 720, ruidoso; o fundo cobre a tela inteira e o rasterizador por software paga cada pixel. GPU física pendente | INFO |

## Arquitetura definitiva recomendada para o Backdrop (para aprovação da fase 2)

1. **[D] Geometria em `AFTER_SKY`, no framebuffer principal**, com `depthMask(false)` e sem teste de profundidade, ouvinte em prioridade alta para ficar debaixo do que outros mods desenham ali. Um `VertexBuffer` estático por forma, enviado uma vez: **cubo** com textura 3 × 2 (cubemap de 6 faces montado na carga) ou **esfera UV** para panorama equirretangular, ambos com o shader `position_tex_color` do jogo (sem shader próprio, sem passe de pós). Gradiente: cores por vértice na mesma esfera. Domos procedurais (estrelas e nebulosas) ficam para a fase 3, com shader próprio e medição separada.
2. **[D] Os passes do motor continuam por cima** sem mudança: lente, escurecimento, camadas procedurais e raios. Comprovado: a lente entorta o fundo e o eclipse o escurece.
3. **[D] Mesmas condições do céu do jogo:** não desenhar com cegueira ou escuridão, câmera em lava ou neve em pó, névoa de bioma ou de chefe (as condições que o `SkyFrame.vanillaDrawn` já confere), e respeitar a dimensão pedida pelo addon. A opção de cliente `customSkies` desliga tudo.
4. **[D] Atmosphere com fundo:** os passes `sky` e `atmosphere` recebem a cobertura do fundo no quadro. Com fundo opaco, o gradiente é pintado por mistura (`skyBlend`) sobre o fundo, como no modo `replaced` que já existe para `ProfileSkyEffects`; com fundo parcial, a diferença em relação ao céu do jogo é escalada por `1 − cobertura`. Sem fundo, o código e a aparência da 0.12.0 ficam iguais (guarda: `checkCeuReferencia`; a fase 2 acrescenta cenas de referência com fundo).
5. **[D] Sol e lua com fundo opaco:** o motor não subtrai texels do fundo. Se o addon pede o sol e a lua do jogo de volta, o Backdrop os redesenha (mesma textura e pose do `SkyMath.vanillaAxes`), e uma camada que esconde o sol (eclipse) simplesmente impede esse redesenho. Sem fundo, o apagamento da 0.12.0 continua como está.
6. **[D] Nuvens do jogo ficam na frente** (correto para nuvens abaixo do céu). Trocar as nuvens só numa dimensão própria, por `ProfileSkyEffects.renderClouds` (fase 3).
7. **[H→fase 2] Horizonte:** opção para a névoa do terreno distante usar a cor do horizonte do fundo (evento de cor de névoa do NeoForge, a conferir com `javap` antes de usar), para tirar a linha clara medida.
8. **[D] Prioridade:** um fundo por quadro (eventos globais, depois céu da dimensão, depois efeitos, como as camadas da 0.12), dois durante uma transição (crossfade). Texturas carregadas uma vez por recarga de recursos, liberadas na recarga e ao sair; nada enviado por quadro.
9. **Riscos que só teste resolve:** Iris/Oculus e shaderpacks (o pipeline pode não disparar `AFTER_SKY` do mesmo jeito), outros mods que desenham em `AFTER_SKY` e o custo numa GPU física. Nenhum é afirmado como compatível.

## Fase 2: Backdrop implementado

A arquitetura acima foi seguida; o que mudou em relação a ela está marcado.

**API (só aditiva; `checkApiCompat` verde contra a base 0.12.0).** `Backdrop` (Java puro): `cubemap("ns:textures/…png")` (um PNG 3 × 2; linha 0: norte, leste, sul; linha 1: oeste, cima, baixo; faces laterais vistas de dentro com o topo para cima; cima com o norte no topo; baixo com o sul no topo), `panorama(...)` (equirretangular, sul no meio, oeste à direita, zênite em cima) e `gradient(topo, horizonte, nadir)` (ARGB, alfa = cobertura). Opções: `opacity`, `rotation` (graus, positivo leva o sul para o oeste), `spin` (graus por segundo; para com "efeitos de movimento" desligado), `tint`, `vanillaSunMoon(true)` (sol e lua do jogo redesenhados sobre o fundo), `matchFog(false)` (desliga a névoa casada), trilhas `OPACITY` e `ROTATION` e `weightPhase`. Ligação: `Effects.backdrop(id, fundo)` (um por efeito; também vale para eventos globais, que são efeitos) e `SkyProfile.StateBuilder.backdrop(fundo)` (constante; estados fazem crossfade). `Sky.Type` não mudou e os sete fenômenos continuam os mesmos.

**Desenho (`BackdropFrame`, `BackdropTextures`).** **[V]** Em `AFTER_SKY`, ouvinte de prioridade alta, no framebuffer principal, sem gravar profundidade e sem teste de profundidade. Um cubo (cantos de `BackdropMath.cubeDirection`) para todo cubemap e uma esfera UV 64 × 32 (cantos de `BackdropMath.panoramaDirection`) para panoramas e gradientes, enviados uma vez (`VertexBuffer` estático) e reaproveitados por todos os fundos; o giro é só a matriz. Shader `position_tex_color` do jogo; amostragem sem filtro (como o jogo e como o passe confere). Cada PNG vira uma textura do `TextureManager` (compartilhada por todos os fundos que a usam); o gradiente vira uma textura gerada de 4 × 512 e segue o mesmo caminho dos panoramas. Uma cópia grosseira (128 × 64) das cores e da cobertura fica na CPU para a névoa e para o sol e a lua. Tudo é liberado na recarga de recursos e ao sair do mundo. Estado devolvido ao jogo depois de desenhar (máscara e teste de profundidade, culling, mistura, cor do shader) — **[V]** terreno idêntico com e sem fundo no teste em jogo.

**Seleção.** No máximo dois fundos por quadro (`BackdropMath.select`): o mais importante (eventos globais, depois o perfil da dimensão, depois HERO e opacidade) e, enquanto ele não é opaco, o de baixo, com opacidade base × min(1, peso dele + peso do de cima). Numa transição (pesos somando 1) o de baixo fica inteiro e o de cima aparece sobre ele: o céu do jogo não aparece no meio (**[V]** em jogo, no End).

**Os quatro problemas do protótipo, resolvidos.**

| Problema (fase 1) | Solução (fase 2) | Prova |
|---|---|---|
| Atmosphere sobre fundo opaco desloca as cores (azul virava quase preto) | Os passes `sky` e `atmosphere` recebem os fundos do quadro (texturas, opacidade, giro, tinta) e calculam a cor e a cobertura deles em cada direção (`calamidade_sky.glsl`, mesmas consultas do `BackdropMath`). O "céu de baixo" da atmosfera passa a ser o fundo composto sobre a estimativa do céu do jogo (`caAtmoSkyDeltaOver`). Resultado: a atmosfera tinge o céu que aparece, com fundo opaco, parcial ou com buraco de alfa. Sem fundo, o código da 0.12.0 roda inalterado. | Prévia: 4 cenas com erro máximo de 0,5 a 1,0/255 contra mix(tela, gradiente, blend); controle negativo (passes sem o fundo) erra 77,5/255. Em jogo: medido (37,32,92) = esperado (37,32,92). |
| Eclipse e apagamento do sol e da lua sobre o fundo | O passe só tira a parte do sol e da lua que os fundos não cobrem (1 − cobertura); sob fundo, a saturação do miolo do sol é prevista pelo céu do jogo estimado (a tela já não a mostra). Com `vanillaSunMoon(true)`, o Backdrop redesenha o sol e a lua com a geometria do jogo, só a parte que ele cobriu e menos o que uma camada esconde: um eclipse impede o redesenho. O escurecimento do eclipse vale para o fundo. | Prévia: fundo opaco sem marca (0,0/255; controle negativo 217/255); fundo parcial 8/255, metade do erro do próprio apagamento da 0.12.0 sem fundo (16/255); escurecimento 0,5/255. Em jogo: alfa do sol redesenhado 1,00 → 0,00 na totalidade; fundo escurecido longe do disco. |
| Fundo aparecendo onde o jogo não desenha céu | Mesmas condições do `LevelRenderer.renderSky` (javap): cegueira, escuridão, câmera em lava ou neve em pó, névoa de bioma ou de chefe (`isFoggyAt`, `shouldCreateWorldFog`); e a opção de cliente `customSkies`. | Em jogo: os quatro bloqueios, a ilha central do End com a barra do dragão (névoa de chefe) e `customSkies` desligado. |
| Linha clara no horizonte | `ComputeFogColor` (antes da Atmosphere): com `matchFog` (padrão), a névoa toma a cor dos fundos 1,5° acima do horizonte, na direção do olhar, composta pela cobertura. **[V]** `setupColor` (offset 326) vem antes de `renderSky` (450) e de `AFTER_SKY` (453) no `renderLevel`: névoa e fundo usam a mesma seleção no quadro. | Em jogo, mundo plano: faixa do horizonte 0/255 mais clara que o fundo com a névoa casada, 90/255 sem ela. |

**[D] Mudança em relação à arquitetura aprovada:** a cobertura que os passes usam não vem de um alvo próprio nem de uma estimativa: eles leem as mesmas texturas do fundo por direção. Custa duas consultas de textura por pixel de céu, só nos quadros em que o passe já roda por outro motivo (camada, atmosfera); um fundo estático sozinho não liga passe nenhum.

**Limitações conhecidas.** Névoa casada é uma cor por quadro (a do horizonte na direção do olhar), como a do jogo: com faces muito diferentes, o terreno distante nas bordas da tela pode diferir do fundo atrás dele. Amostragem sem filtro (pixel a pixel); filtro linear daria costuras nas arestas do cubo e fica para depois, se pedido. Na prévia e em jogo, o fundo usa a cobertura amostrada; o redesenho do sol usa a cobertura no centro do sol (pode diferir nas bordas de uma textura com alfa variável). Sob fundo parcial o apagamento do sol herda o erro de 8 bits do mecanismo da 0.12.0, pela metade. O fundo não muda com o dia e a noite (a hora não o escurece; um perfil ou uma trilha `OPACITY` fazem isso). Iris/Oculus, shaderpacks, Sodium/Embeddium e GPU física **não foram testados** com o fundo.

## Visão geral consolidada (0.14 e 0.15)

| Modalidade | Caminho de desenho | Versão |
|---|---|---|
| Procedural (sete tipos da 0.12) | Passe `sky`, sem mudança | já existe |
| Backdrop (cubemap, panorama, gradiente) | Geometria em `AFTER_SKY` (acima) | 0.14, fase 2 — **feito** |
| Estrelas procedurais, nebulosas procedurais | Domo em `AFTER_SKY` com shader próprio | 0.14, fase 3 |
| Catálogo de estrelas e constelações | Um `VertexBuffer` estático em `AFTER_SKY` | 0.14, fase 3 |
| Imagens e impostors atrás das camadas | Quads em `AFTER_SKY`, atlas próprio com `.mcmeta` | 0.14, fase 4 |
| Imagens, impostors e modelos na frente de camadas ou mascarados por portal | Alvo próprio sob demanda, composto dentro do passe `sky` | 0.14 (imagens) e 0.15 (modelos) |
| Modelos em World e Entity Space | `RenderType` de entidade do jogo; cache opcional | 0.15 |
| Portais no mundo, transições, ponte para entidades | Timeline existente; recorte CPU × GPU escolhido por medição | 0.15 |

Regras que valem para todas: nada novo sem um addon pedir; separação entre definição (comum, só `ResourceLocation`s e parâmetros), configuração visual e instância ativa (cliente); nenhum pacote por quadro; limites só depois de benchmark; `checkApiCompat` e `checkCeuReferencia` em toda etapa.

## O que a fase 2 deixa pronto para a fase 3 (sem implementar nada dela)

- **Ponto de desenho em `AFTER_SKY` com estado devolvido**: estrelas e nebulosas procedurais (domo com shader próprio), o catálogo de estrelas e constelações (um `VertexBuffer` estático) e imagens e impostors atrás das camadas podem entrar no mesmo ouvinte, depois do fundo, com a mesma disciplina de estado.
- **Composição já resolvida para "algo pintado antes dos passes"**: os passes sabem compor a Atmosphere, o apagamento do sol e da lua e a lente sobre o que foi desenhado em `AFTER_SKY` desde que conheçam a cor e a cobertura por direção. Estrelas e imagens opacas precisam entrar nessa conta (ou ser declaradas aditivas) — decisão da fase 3, com prova na prévia como nesta fase.
- **Texturas**: `BackdropTextures` (carga pelo `TextureManager`, cópia na CPU, liberação na recarga e ao sair) serve de base para o atlas próprio com `.mcmeta` (spritesheets e imagens animadas) da fase 4.
- **Seleção e transição**: `BackdropMath.select` (dois por quadro, crossfade sem buraco) é o modelo para objetos do céu com transição.
- **Testes**: a prévia aceita texturas de teste e pré-passes que fazem o papel de `AFTER_SKY`; o `testeBackdrop` tem sondas, capturas e o padrão de registrar fundos no mod de teste.
- Continuam planejados e **não** começados: na 0.14, estrelas luminosas, constelações, nebulosas, PNGs e billboards, spritesheets e imagens animadas, objetos 3D pré-renderizados e impostors, personalização mais completa da atmosfera e do céu; na 0.15, modelos Blockbench reais, modelos personalizados e animados, Sky, World e Entity Space, objetos gigantescos no céu, transições 2D ↔ 3D, portais cinematográficos, objetos atravessando portais e integração opcional com entidades e chefes. A 0.13 continua reservada aos Bosses.

## Pendências que continuam abertas

- Da 0.12.0 (VALIDACAO.md): multiplayer manual com dois jogadores; a pulsação observada no Overworld (causa não identificada, não é bug confirmado). Quando a fase 2 tocar em Atmosphere ou no estado do céu entre dimensões, entra um teste de regressão que captura o céu do Overworld antes e depois de mudar os estados da dimensão de teste, com a hora congelada.
- Da 0.15: validar os formatos reais de exportação do Blockbench (Bedrock `.geo.json`, Java, OBJ) e o subconjunto de animação antes de qualquer leitor.
- GPU física, Iris/Oculus e Sodium/Embeddium com os renderizadores novos (o Backdrop foi medido só por software; nenhuma compatibilidade com shaderpacks ou outros mods é afirmada).
- Uma receita do vfx-sample com um fundo de céu, quando a 0.14 tiver capítulo próprio (nesta fase, os exemplos estão no Javadoc, nesta página e no mod de teste).
- Da 0.12.0, continuam: a falha intermitente do limite de 50 ms da cratera grande do `testeJogo` (causa não isolada; nesta rodada passou com 33,6 ms, o que não a resolve) e a pulsação no Overworld (o teste de regressão de céu entre dimensões da fase 2 deu diferença zero com a hora parada, mas a pulsação relatada não foi reproduzida nem explicada).
