# Custom Sky Engine: arquitetura (0.14 e 0.15)

Documento de trabalho do branch `claude/ceu-personalizado`. Fases 0, 1 e 2 (Backdrop) concluídas e aprovadas; fase 3 (estrelas, constelações e nebulosas) concluída, com a validação registrada em VALIDACAO.md; a fase 4 (imagens, spritesheets e impostors) tem [plano](#fase-4-plano-técnico-aguardando-aprovação) à espera de aprovação e **não começou**. Ainda não é capítulo do manual: entra em `gerar_manual.py` quando a 0.14 for lançada (a versão do build continua 0.12.0 até lá).

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

## Fase 3: estrelas, constelações e nebulosas

### Decisão de composição (pedida na aprovação da fase 2): híbrida e por objeto

Não há uma regra global. **[D]** Todo objeto do céu (estrelas, linhas, nebulosas e, na fase 4, imagens, billboards, spritesheets e impostors) sai em **alfa pré-multiplicado**: o pixel traz uma luz `rgb` e uma cobertura `a`, e o céu de trás vira `trás × (1 − a) + rgb` (mistura `ONE, ONE_MINUS_SRC_ALPHA`). O modo de cada objeto (`SkyBlend`) só muda o que ele traz:

| Modo | Luz | Cobertura | Uso |
|---|---|---|---|
| `NORMAL` | cor × alfa × opacidade | alfa × opacidade | transparência real, cores exatas (nebulosa semitransparente, estrela colorida sobre fundo claro, PNG com alfa) |
| `LUMINOUS` | cor × alfa × opacidade × brilho | 0 | luz emissiva: o fundo nunca some, só clareia (padrão das estrelas) |
| `COVER` | cor × opacidade dentro da silhueta | 1 × opacidade onde alfa ≥ 0,5 | silhueta: esconde o fundo só dentro da forma, nunca como retângulo |

O Backdrop (fundo inteiro) continua com o mecanismo de cobertura da fase 2. Uma estrela `NORMAL` fraca cobre pouco (a cobertura segue o brilho até 1) e o brilho acima de 1 alarga o disco em vez de lavar a cor; em `LUMINOUS` o brilho acima de 1 estoura para branco, como luz. Cada objeto escolhe também a reação à Atmosphere (`atmosphere`, 0..1: quanto o véu o cobre), à luz do dia (`daylight`, 0..1: como as estrelas do jogo), ao escurecimento das camadas (`SkyDarkening`: `DIM` escurece com o mundo, `REVEAL` aparece com a escuridão, `IGNORE`; com força 0..1) e ao horizonte (degradê em graus). Padrões: estrelas `LUMINOUS`, véu 1, dia 1, `REVEAL`, ordem 200; nebulosas `NORMAL`, véu 1, dia 1, `REVEAL`, ordem 100 (atrás das estrelas). `order` (−1000..1000) define quem fica na frente; em empate, o mais importante fica por cima.

**Cobertura por pixel [D].** O objeto aplica a própria reação no shader (o véu com as cores da Atmosphere do quadro, a exposição do eclipse, a visibilidade de dia). Quando o passe `sky` ou o `atmosphere` vai rodar no quadro, os mesmos objetos são desenhados uma segunda vez numa **camada de cobertura** do tamanho da tela (RGBA8, limpa em zero, mesma mistura): `rgb` é a luz que os objetos trouxeram e `a` a cobertura combinada, sem o terreno na frente. A composição pré-multiplicada é associativa, então a camada é exatamente o que os objetos fizeram na tela (provado no `SkyObjectsCheck`). Os passes leem a camada (`CaElTex`, `calamidade_sky.glsl`) e:
- **Atmosphere:** a diferença do céu vale só para o que os objetos deixam ver, `× (1 − a)`. Com véu 1, o resultado é exatamente `mix(tela, gradiente, mistura)`, como se o objeto estivesse atrás da atmosfera; com véu 0, ele brilha através dela.
- **Escurecimento (eclipse):** `tela × exposição + rgb × (1 − exposição)`: os objetos ficam como se desenharam (cada um já aplicou `DIM`, `REVEAL` ou `IGNORE`).
- **Sol e lua do jogo:** a parte ainda visível é `× (1 − a)`: um objeto opaco na frente do sol não ganha marca.
- **Lente:** a cena desviada já traz os objetos; onde o desvio cai num morro, o preenchimento é o céu estimado com os objetos por cima (a camada não tem terreno).
- **Profundidade:** os objetos não gravam profundidade, como o céu do jogo e o fundo; terreno, nuvens e as camadas procedurais da 0.12 ficam na frente.

Sem objetos, ou sem esses passes no quadro, a camada não é desenhada nem alocada e os passes rodam o código da fase 2 (`CaEl.x = 0`; `checkCeuReferencia` com diferença zero). Custo da camada: o segundo desenho dos mesmos objetos, só nos quadros com passe de céu ou de atmosfera; nenhum passe de tela inteira novo. Isto prepara a fase 4: imagens e impostors entram como mais um objeto no mesmo ouvinte, com os mesmos modos, a mesma camada e as mesmas reações.

### API (só aditiva; `checkApiCompat` verde contra a base 0.12.0)

- `StarField.random(semente, n)` (até 65 536): magnitudes com a distribuição do céu real (cerca de quatro vezes mais estrelas por magnitude mais fraca), paleta de temperaturas, `band(polo, meia largura, fração)` (galáxia vista de lado), `region(centro, raio)`. `StarField.catalog().star(nome, azimute, elevação, magnitude, cor)` (até 4 096) e `.constellation(cor, "a", "b", "c")` (até 2 048 segmentos, `lineWidth`). Comum: `size`, `brightness`, `twinkle(quantidade, velocidade)`, `blend`, `atmosphere`, `darkening`, `daylight`, `horizon`, `opacity`, `rotation`, `spin`, `celestial` (gira com a hora como as estrelas do jogo; direções dadas como vistas à meia-noite), `order`, trilhas `OPACITY`, `BRIGHTNESS`, `ROTATION` e `weightPhase`.
- `Nebula.cloud(azimute, elevação, raio)` e `Nebula.band(polo, meia largura)`: ruído com semente, `colors(denso, borda)`, `density`, `detail`, `warp`, `roll` e as mesmas opções comuns (padrão `NORMAL`).
- `Effects.stars(id, campos...)` (até 4), `Effects.nebulae(id, nebulosas...)` (até 8); `SkyProfile.StateBuilder.stars(...)` e `.nebulae(...)` (constantes; estados fazem transição); eventos globais herdam do efeito. Enums novos `SkyBlend` e `SkyDarkening`. `Sky.Type` não mudou.
- Brilho: `10^(−0,2 (m − 1))` (magnitude 1 = 1, magnitude 6 ainda visível em 8 bits). Abaixo de ~1 pixel a estrela perde brilho pela raiz da razão de tamanho em vez de encolher (sem cintilação de aliasing); as linhas têm largura mínima na tela.
- Opção de cliente `customSkies` desliga fundos, estrelas e nebulosas. Cintilação parada com "efeitos de movimento" desligado, a 1/4 com "ocultar clarões", zero com intensidade dos efeitos de tela 0.

### Desenho e desempenho (`SkyObjectsFrame`)

- **[V]** Em `AFTER_SKY`, logo depois dos fundos, no mesmo ouvinte, com o estado devolvido ao jogo. Shaders de núcleo `sky_stars` e `sky_nebula` (registrados no `RegisterShadersEvent`, `#moj_import` de `sky_objects.glsl`; `checkShaders` agora compila também os shaders de núcleo).
- **Lote:** cada campo vira um `VertexBuffer` estático uma vez (linhas primeiro, depois as estrelas da mais brilhante para a mais fraca), desenhado com **uma chamada por quadro** (medido em jogo: 1,00 chamada por quadro com 40 000 estrelas). Instancing não existe no `VertexBuffer` do jogo (`javap`: só `draw()` com a contagem inteira); o prefixo é desenhado como o `drawWithShader` faz (`setDefaultUniforms`, `apply`, `RenderSystem.drawElements` com a contagem e o tipo de índice do buffer sequencial), conferido por `javap -c`. Nebulosas: duas malhas compartilhadas (grade 24 × 24 aberta em volta do centro, só os pixels da nuvem pagam; esfera 64 × 32 para faixas).
- **Perfis (AUTO incluído):** campos / estrelas / nebulosas / oitavas por quadro: LOW 2 / 4 000 / 2 / 3; BALANCED 3 / 16 000 / 3 / 4; HIGH 4 / 40 000 / 4 / 5; ULTRA 6 / 131 072 / 6 / 5. Os perfis menores desenham o prefixo (as mais brilhantes). Seleção como a das camadas: eventos globais, depois o perfil da dimensão, depois HERO, depois opacidade. Malhas sem uso por 1 200 quadros são liberadas; tudo é liberado na recarga de recursos e ao sair.
- Mesmas regras do céu do jogo: nada com cegueira, escuridão, lava, neve em pó, névoa de bioma ou de chefe.

### Provas

- `SkyObjectsCheck` (no `checkBudget`): regras dos construtores, amostragem, geração determinística, distribuição de magnitudes, faixa e região, catálogo e segmentos, tetos e partilha do orçamento de estrelas, seleção e ordem, referencial celeste (mesmo eixo do sol), os três modos, a associatividade da camada de cobertura, reações.
- `checkObjetosComposicao` (prévia com os shaders de núcleo **reais** em WebGL2 e os passes reais): **24/24**, cada caso com controle negativo que precisa falhar — estrelas `LUMINOUS` não apagam o fundo (pior escurecimento 0/255); `NORMAL` com a cor exata (255,32,16); nebulosa a 0,5 mistura por alfa coerente (1,3/255) sem substituir a skybox (0/255 fora dela) e sem chegar à cor dela (alfa máximo 0,40); `COVER` só na silhueta; Atmosphere sobre fundo, nebulosa e estrelas pelos dois passes 1,4/255 (sem a camada: 70,8/255); estrelas `REVEAL` no eclipse 0,5/255 da visibilidade prevista (sem a camada: 27,5/255), `DIM` 1,0/255, `IGNORE` diferente; sol sob nuvem `COVER` 0,0/255 (sem a camada: 192/255); ordem; lente (15/17 pixels de estrela deslocados perto, 16/16 iguais longe).
- `checkCeuReferencia`: as 26 cenas anteriores com diferença zero, mais 12 cenas de objetos na referência.
- Em jogo, `testeEstrelas` (resultados em VALIDACAO.md).

**O que a prévia achou e foi corrigido:** estrelas fracas demais (conservação de fluxo pelo quadrado e escala de magnitude dura), cor `NORMAL` lavada para branco nas estrelas brilhantes e linhas de constelação serrilhadas sem largura mínima.

**Limitações conhecidas.** A camada de cobertura não conhece as nuvens do jogo (desenhadas depois de `AFTER_SKY`): sobre uma nuvem na frente de uma nebulosa, a Atmosphere tinge um pouco menos e o escurecimento devolve um pouco de luz das estrelas atrás dela. A reação ao escurecimento usa a exposição do quadro anterior (um quadro de atraso numa transição). No primeiro quadro em que um passe começa, a camada pode não estar desenhada e os objetos levam o véu duas vezes por esse quadro. Exposição e tinta da Atmosphere continuam valendo para a tela inteira, objetos incluídos (como para o sol). Pixels que estouram em 255 (luz somada sobre fundo claro) perdem cor antes dos passes, como qualquer luz em 8 bits. Iris/Oculus, shaderpacks, Sodium/Embeddium, outros mods em `AFTER_SKY`, GPU física e multiplayer com dois jogadores **não foram testados**; nenhuma compatibilidade é afirmada. Shaderpacks substituem o pipeline de desenho e podem não disparar `AFTER_SKY` do mesmo jeito.

## Fase 4: plano técnico (aguardando aprovação)

**Nada desta seção está implementado.** É a proposta para PNGs e billboards arbitrários, spritesheets e flipbooks e objetos 3D pré-renderizados (impostors). Modelos Blockbench reais, geometria, Sky/World/Entity Space, objetos que atravessam portais e transição para o objeto real próximo continuam na 0.15 e não entram aqui. Marcadores como no resto do documento: o que depende de API do jogo ainda não conferida está como **[H]** e será conferido com `javap` na etapa 4.0, antes do primeiro uso.

### Arquitetura

1. **[D] Mais um tipo de objeto do céu, no caminho que já existe.** Imagens, flipbooks e impostors são todos "um quadrilátero tangente à esfera do céu, com um retângulo de textura": um único renderizador (`SkySprites`, dentro do ouvinte do `SkyObjectsFrame` em `AFTER_SKY`), um único shader de núcleo novo (`sky_sprite`, que importa `sky_objects.glsl`), os mesmos modos `SkyBlend`, as mesmas reações (`atmosphere`, `daylight`, `SkyDarkening`, `horizon`), a mesma `order` intercalada com estrelas e nebulosas e a **mesma camada de cobertura** da fase 3. Consequência: Atmosphere, escurecimento do eclipse, apagamento do sol e da lua do jogo e lente gravitacional funcionam sobre as imagens sem código novo nos passes (como já foi provado para estrelas e nebulosas). Nenhum passe de tela inteira novo; nenhum renderizador, cache ou composição paralela.
2. **[D] Objetos no infinito.** Como o fundo e as estrelas, um objeto desta fase tem direção, não posição: não grava profundidade, terreno e nuvens ficam na frente, não há paralaxe com o movimento do jogador. Paralaxe, distância real e objetos no mundo são a 0.15 (World Space).
3. **[D] Atrás das camadas procedurais, por padrão.** O que é desenhado em `AFTER_SKY` fica atrás das sete camadas da 0.12 (o passe `sky` pinta por cima): um dragão desenhado assim passa atrás de um buraco negro e é entortado pela lente, o que é o certo para objetos distantes. Para um objeto **na frente** de uma camada (uma nave passando diante do portal), a etapa 4.4 propõe `layer(FRONT)`: os objetos `FRONT` vão para uma segunda camada de tela (RGBA8, alocada só quando existe um objeto `FRONT` **e** o passe `sky` roda no quadro) e o passe `sky` a compõe depois das camadas, só nos pixels de céu (`final = céu × (1 − a) + rgb`), sem lente. Sem camada procedural ativa, `FRONT` é igual a atrás e nada é alocado. É a mesma peça de que os modelos da 0.15 vão precisar, então não é trabalho perdido. **Decisão do autor:** fazer na fase 4 (recomendado, custo baixo) ou deixar para a 0.15.
4. **Separação comum × cliente, como nas fases anteriores.** A definição (comum) só guarda `ResourceLocation`s e parâmetros e é registrada nos dois lados; o cliente carrega texturas e descritores. **Nenhum pacote novo, nenhum canal novo**: tudo deriva do relógio da instância (efeitos e eventos globais) ou do estado do perfil (`sky_profile_v1` já existente).

### APIs públicas propostas (só aditivas; `checkApiCompat` em toda etapa)

Classes novas em `api.v1`; nenhuma existente muda de assinatura e `Sky.Type` não ganha constantes.

```java
// Imagem estática: planeta ilustrado, símbolo, estrutura gigante, criatura parada
SkyImage dragao = SkyImage.of("meumod:textures/sky/dragao.png")
    .direction(120, 35)            // azimute, elevação (graus), mesmo referencial do Sky e das estrelas
    .size(18)                      // largura angular em graus; altura pela proporção do PNG (ou size(l, a))
    .roll(-10).pivot(.5f, .5f)     // giro no plano do céu, em graus, em torno do pivô (0..1 da imagem)
    .up(SkyImage.Up.HORIZON)       // HORIZON (de pé em relação ao zênite), CELESTIAL (gira com o céu), PATH (alinha ao movimento)
    .blend(SkyBlend.COVER).tint(0xFFFFE0C0).brightness(1f).opacity(1f)
    .atmosphere(.6f).daylight(0f).darkening(SkyDarkening.DIM, 1f).horizon(4f)
    .order(300).filter(SkyImage.Filter.SMOOTH)   // SMOOTH (linear + mipmaps) ou PIXEL (sem filtro, arte em pixel)
    .track(SkyImage.Param.AZIMUTH, "voo_az")     // trilhas da Timeline: AZIMUTH, ELEVATION, SIZE, ROLL, OPACITY, BRIGHTNESS, FRAME
    .weightPhase("aparicao")
    .build();

// Spritesheet / flipbook: a mesma classe, com a grade de quadros
SkyImage asas = SkyImage.sheet("meumod:textures/sky/dragao_voo.png", 4, 2)   // colunas, linhas (ordem por linha)
    .frames(8).fps(12)
    .play(SkyImage.Play.LOOP)      // LOOP, ONCE (para no último), PING_PONG, TRACK (quadro vindo da trilha FRAME)
    .clock(SkyImage.Clock.INSTANCE) // INSTANCE (relógio da instância, igual em todos os clientes) ou WORLD (tempo do jogo: perfis)
    .startFrame(0).blendFrames(true)            // mistura entre quadros vizinhos (2 consultas; desligada em LOW)
    .flashing(false)               // true para folhas que piscam: congela/desacelera com "ocultar clarões"
    .direction(200, 40).size(25).build();

// Impostor: objeto 3D pré-renderizado de vários ângulos (o descritor vem dos recursos)
SkyImpostor nave = SkyImpostor.of("meumod:nave")   // assets/meumod/calamidade/impostors/nave.json
    .direction(90, 20).size(12)
    .orientation(45, 0, 0)         // yaw, pitch, roll do objeto, em graus
    .heading(SkyImpostor.Heading.PATH)   // FIXED ou PATH (aponta para onde a trilha de azimute/elevação o leva)
    .viewBlend(SkyImpostor.ViewBlend.AUTO)  // NEAREST, PAIR, QUAD; AUTO segue o perfil de qualidade
    .track(SkyImpostor.Param.YAW, "giro").build();

Effects.images(id, dragao, asas);          // até 8 por efeito; eventos globais herdam do efeito
Effects.impostors(id, nave);               // até 4 por efeito
SkyProfile.state("guerra").images(...).impostors(...);   // constantes por estado; transição por crossfade
```

Opções comuns copiam as de `StarField` e `Nebula` (mesmos nomes, mesmas faixas, mesmos padrões de validação contra a Timeline em `validate`). Padrões: `SkyBlend.NORMAL`, véu 1, dia 0 (imagens não somem de dia, ao contrário das estrelas), `IGNORE`, horizonte 0, ordem 300 (na frente das estrelas). Limites do construtor: tamanho angular (0, 180]°, até 8 192 quadros por folha, grade até 64 × 64.

### Tipos de imagem e formatos para addons

| Recurso | Onde | Regras |
|---|---|---|
| Imagem estática | `assets/<ns>/textures/sky/*.png` (qualquer caminho de textura) | PNG RGBA, alfa direto (não pré-multiplicado). Lado até 4 096 px; acima do `GL_MAX_TEXTURE_SIZE` da placa, reduzida na carga com aviso. Potência de 2 não é exigida; é recomendada com `SMOOTH` (mipmaps limpos). |
| Spritesheet | um PNG em grade | Quadros do mesmo tamanho, ordem por linha, da esquerda para a direita. Com `SMOOTH`, **margem de 2 px** transparente em volta de cada quadro (os mipmaps não vazam de um quadro para o outro); o motor também prende a amostragem dentro do quadro. `.mcmeta` do jogo **não** é usado (veja Decisões). |
| Impostor | descritor `assets/<ns>/calamidade/impostors/<nome>.json` + PNGs | Lido na recarga de recursos (F3+T recarrega). Exemplo abaixo. |

```json
{
  "format": 1,
  "textures": ["meumod:textures/sky/nave_views.png"],
  "frame": [256, 256], "grid": [16, 12], "gutter": 2,
  "views": { "yaw": 16, "pitch": [-30, 0, 30, 60], "mirror": false },
  "frames": 3, "fps": 8, "play": "loop",
  "pivot": [0.5, 0.5],
  "lod": [ { "textures": ["meumod:textures/sky/nave_views_128.png"], "frame": [128, 128], "below": 96 } ]
}
```

Índice do quadro na grade: `((animação × nPitch) + pitch) × nYaw + yaw`, com várias páginas quando não couber numa textura. `yaw` 0 = o objeto visto de frente (eixo −Z local olhando para a câmera, como o `Placement`), crescendo no sentido horário visto de cima; `mirror: true` usa simetria bilateral e guarda só meia volta. `lod.below` em pixels na tela: abaixo disso, usa a versão menor (carregada à parte, só quando precisa).

**Ferramenta de assar impostors (etapa 4.5, opcional):** `scripts/gerar_impostor.cjs` (Node + Playwright + WebGL, que o projeto já usa) abre um `.gltf`/`.obj` exportado do Blockbench, renderiza as vistas e quadros com fundo transparente e grava a grade e o descritor. Fica fora do JAR e não carrega geometria no jogo: o jogo só vê PNGs. Os formatos reais de exportação do Blockbench continuam a validar (pendência da 0.15); a ferramenta só precisa ler um deles.

### Estratégia de renderização

- **Lote por textura.** Todo quadro, a CPU monta os quadriláteros visíveis (4 vértices por objeto, dezenas no máximo) num buffer dinâmico e desenha **uma chamada por textura distinta, por trecho de `order`** (estrelas e nebulosas se intercalam pela ordem). Recorte na CPU por cone de visão (raio angular do objeto contra o frustum); fora da tela não custa nada. **[H]** Reaproveitar o formato de vértice próprio da fase 3 (`STAR_FORMAT`: posição, cor, UV0, UV1, UV2, normal) para levar retângulo do quadro A, retângulo do quadro B e mistura, cor pré-multiplicada e parâmetros de reação; o envio por quadro (`BufferBuilder` → `VertexBuffer.upload` dinâmico) será conferido com `javap`.
- **Shader `sky_sprite`.** Amostra o quadro (ou dois, com `blendFrames`/`viewBlend`), multiplica pela tinta, aplica a reação do objeto (mesmo código de `sky_objects.glsl`) e devolve alfa pré-multiplicado conforme o `SkyBlend`. `COVER` usa o limiar alfa ≥ 0,5 da fase 3 (silhueta, nunca retângulo).
- **Alfa pré-multiplicado na carga.** O PNG é pré-multiplicado na CPU antes do envio: com filtro linear e mipmaps, alfa direto deixa franjas escuras nas bordas de um dragão; pré-multiplicado não.
- **Filtro.** `SMOOTH` = linear + mipmaps gerados na carga (objetos que aparecem pequenos não cintilam); `PIXEL` = sem filtro, como o Backdrop. **[H]** `AbstractTexture.setFilter(blur, mipmap)` e a geração de mipmaps a conferir.
- **Flipbook.** O quadro é calculado na CPU por objeto (Java puro, `SkySpriteMath.frame`): `INSTANCE` usa o relógio da instância (igual em todos os clientes, sem pacote); `WORLD` usa o tempo do jogo (perfis de dimensão, sincronizado pelo próprio jogo); `TRACK` lê a trilha `FRAME` da Timeline; `ONCE` para no último quadro. Nada de textura animada por tick: só muda o retângulo de UV, então mil quadros custam o mesmo que um.
- **Impostor.** A direção da câmera para um objeto no infinito é a própria direção dele, então a vista escolhida depende **só** da orientação do objeto e da posição dele no céu (com `Heading.PATH`, do movimento): vetor de vista local = inverso da rotação do objeto × direção. Escolha da vista mais próxima (`NEAREST`), das duas vizinhas em yaw (`PAIR`) ou das quatro em yaw e pitch (`QUAD`, mistura bilinear), com pesos calculados na CPU; a troca entre vistas é por mistura, sem salto.

### Cache e gerenciamento de memória

- **[D] Uma textura por PNG, carregada sob demanda, compartilhada** (o mesmo esquema do `BackdropTextures`, generalizado num carregador comum `SkyTextures` em vez de um segundo cache): o fundo e as imagens passam a usar a mesma classe. O PNG é decodificado numa thread de trabalho e enviado na thread de render no máximo **uma textura por quadro** (sem engasgo de várias de uma vez); o objeto aparece com fade quando a textura fica pronta. Efeitos e eventos globais pedem as texturas assim que a instância chega ao cliente (antes do envelope a deixar visível).
- **Orçamento de VRAM por perfil** (pontos de partida, a medir): LOW 64 MiB, BALANCED 128, HIGH 256, ULTRA 512, contando RGBA8 com mipmaps (+⅓). Exemplos: um PNG 2 048² = 21 MiB; um impostor 16 × 4 vistas × 3 quadros de 256² = 50 MiB. Acima do orçamento, usa o nível de `lod` menor; sem nível menor, recusa a carga com um aviso único no log e no F7, sem travar.
- **Liberação** após 1 200 quadros sem uso (como as malhas da fase 3), na recarga de recursos e ao sair do mundo. Nenhuma cópia na CPU fica guardada (a camada de cobertura já resolve sol e lua, ao contrário do fundo).
- **F7:** imagens e impostors desenhados, texturas residentes, VRAM estimada, envios no quadro, recusas por orçamento.

### Composição e cobertura

Sem regra nova: os três modos e a camada de cobertura da fase 3 valem para qualquer imagem. `NORMAL` para PNG com transparência real (cores exatas, alfa contínuo), `LUMINOUS` para coisas que brilham (o fundo só clareia), `COVER` para silhuetas sólidas (o dragão esconde a nebulosa e o sol só dentro da forma). A ordem entre objetos é `order`, em empate o mais importante; o Backdrop fica sempre atrás de todos; as camadas procedurais ficam na frente, salvo `layer(FRONT)` (item 3 da arquitetura).

### Integração com Timeline, SkyProfiles e WorldEvents

- Trilhas da Timeline por nome (como em `StarField`/`Nebula`): `AZIMUTH`, `ELEVATION`, `SIZE`, `ROLL`, `OPACITY`, `BRIGHTNESS`, `FRAME` (impostor também `YAW`, `PITCH`); `weightPhase` para aparecer e sumir com uma fase. Uma nave que cruza o céu = duas trilhas e `Heading.PATH`.
- Perfis de dimensão: imagens e impostors constantes por estado, crossfade entre estados, flipbooks com `Clock.WORLD`.
- Eventos globais: herdam do efeito, como as camadas, os fundos e as estrelas.
- Seleção como a da fase 3: eventos globais, depois o perfil da dimensão, depois HERO, depois opacidade; tetos por perfil (pontos de partida: imagens 8 / 24 / 48 / 96 e impostors 2 / 4 / 8 / 16 por quadro, em LOW / BALANCED / HIGH / ULTRA; AUTO segue o perfil escolhido).
- Mesmas regras do céu do jogo (cegueira, escuridão, lava, neve em pó, névoa de bioma ou de chefe) e a opção `customSkies`.
- Acessibilidade: `spin` para com "efeitos de movimento" desligado, como hoje; flipbooks continuam (são conteúdo), exceto os marcados `flashing(true)`, que congelam com "ocultar clarões" e com intensidade dos efeitos de tela 0.

### Estratégia de testes (pela regra de [AGENTS.md](../AGENTS.md#quando-rodar-cada-teste-regra-permanente-desde-a-fase-3-da-014))

- **Durante o desenvolvimento (segundos a ~4 min):** `SkySpriteCheck` novo no `checkBudget` (validação dos construtores; quadro do flipbook em LOOP, ONCE, PING_PONG, TRACK, INSTANCE e WORLD; escolha e pesos das vistas do impostor, `Heading.PATH`, `mirror`; LOD por pixels; contabilidade de VRAM e recusa; recorte por cone; ordem e chaves de lote; leitura do descritor com erros); `checkShaders` para `sky_sprite`; `checkApiCompat`; `checkObjetosComposicao` ampliado com casos de imagem (cor exata em `NORMAL`, silhueta `COVER` sobre o sol, `LUMINOUS`, Atmosphere, eclipse, lente, margem do flipbook sem vazamento, troca de vista do impostor), cada um com controle negativo; prévia WebGL para olhar. Minecraft não é aberto nas etapas 4.0, 4.2 e 4.3 (só na 4.1 e no fim da fase, abaixo).
- **Ao concluir as imagens (4.1) e ao fim da fase:** um único teste em jogo novo, `testeImagens`, com todos os cenários numa só inicialização do cliente: nada sem addon; cor exata; silhueta; flipbook com uma cor sentinela por quadro conferida em tempos conhecidos; `ONCE` parado no último; quadro pela trilha; impostor com vistas coloridas em orientações conhecidas; LOD; recarga libera e recarrega; VRAM contabilizada; chamadas de desenho ≤ texturas visíveis; `customSkies`; cegueira; regressão do céu do Overworld; custo.
- **Rodada consolidada no fim da fase, escolhida pelo risco:** `checkCeuReferencia` (as 38 cenas atuais com diferença zero + cenas novas); `testeEstrelas` e `testeBackdrop` **uma vez**, porque `SkyObjectsFrame` e o carregador de texturas do fundo mudam; `testeCeu` uma vez **só se** a etapa 4.4 (`FRONT`) mexer no passe `sky`; build do vfx-sample. `testeJogo` **não** está previsto: a fase não mexe em servidor, rede nem terreno, só acrescenta registros comuns sem estado de servidor; se isso mudar durante a implementação, ele entra.
- O registro final em VALIDACAO.md separa o que foi executado, o que foi reaproveitado (com commit) e o que fica pendente.

### Riscos e limitações

- **Perspectiva dos impostors:** sem paralaxe e sem mudar com o movimento do jogador (o objeto está no infinito); a vista só muda com orientação e posição no céu. A iluminação vem assada nas imagens e não acompanha o sol do jogo (a tinta e `daylight` atenuam, não corrigem). Entre vistas, a mistura pode mostrar "fantasma" em objetos finos; mais vistas resolvem, ao custo de VRAM. Para ver de perto ou de qualquer ângulo: modelos reais, 0.15.
- **VRAM e engasgos:** folhas grandes consomem memória rápido; envio de uma textura de 4 096² na thread de render custa alguns ms (por isso um envio por quadro e decodificação fora dela). Placas antigas podem ter `GL_MAX_TEXTURE_SIZE` menor.
- **Vazamento de quadros** com mipmaps em folhas sem margem: mitigado pela margem exigida e pela amostragem presa, provado na prévia.
- **8 bits:** como na fase 3, luz somada sobre fundo claro estoura em 255 antes dos passes.
- **`COVER` com arte de borda suave** fica serrilhado no limiar 0,5: arte suave deve usar `NORMAL`.
- **`FRONT`** depende do passe `sky` rodando; objetos `FRONT` não sofrem lente (certo para quem está na frente) e a regra com nuvens segue a das camadas da 0.12.
- **Não testado e não afirmado:** GPU física, Iris/Oculus e shaderpacks, Sodium/Embeddium, outros mods em `AFTER_SKY`, dois jogadores. Direitos das imagens dos addons são responsabilidade de quem as publica.

### Decisões que o autor precisa aprovar

1. **Textura por PNG sob demanda (recomendado)** em vez do atlas do jogo com `.mcmeta` que este documento previa: o atlas do jogo costura tudo na recarga (VRAM para imagens sem uso, limite de tamanho do atlas) e anima por tick, igual para todos, sem FPS próprio, sem `ONCE` e sem Timeline. Empacotar imagens pequenas numa página só fica como otimização futura, se o F7 mostrar chamadas demais.
2. **`layer(FRONT)` na fase 4 (recomendado)** ou na 0.15.
3. **Ferramenta de assar impostors** na fase 4 (versão mínima, recomendado) ou depois.
4. **Flipbooks e acessibilidade** como descrito (continuam tocando; só `flashing(true)` congela).

### Etapas sugeridas (um commit por etapa)

| Etapa | Conteúdo | Verificação |
|---|---|---|
| 4.0 | `javap` de tudo marcado [H]; `SkySpriteMath` (Java puro); esqueleto da API; `SkySpriteCheck` | build, `checkApiCompat` |
| 4.1 | `SkyImage` estática: `SkyTextures` (carregador comum, Backdrop migrado sem mudança visível), shader `sky_sprite`, lote, camada de cobertura, `Effects.images`, perfis | build, `checkShaders`, `checkObjetosComposicao` ampliado; `testeImagens` (parte estática) uma vez |
| 4.2 | Flipbooks: grade, relógios, trilha `FRAME`, `blendFrames`, margem, `flashing` | build, puros, prévia |
| 4.3 | Impostors: descritor e recarga, vistas e mistura, `Heading.PATH`, LOD, orçamento de VRAM, F7 | build, puros, prévia |
| 4.4 | `layer(FRONT)` (se aprovado) | `checkCeuReferencia` (diferença zero nas 38) |
| 4.5 | `gerar_impostor.cjs`, texturas de teste geradas por script (byte a byte iguais), exemplos no Javadoc e nesta página | prévia |
| 4.6 | Rodada consolidada da fase, VALIDACAO.md, capturas | lista da seção de testes |

## Visão geral consolidada (0.14 e 0.15)

| Modalidade | Caminho de desenho | Versão |
|---|---|---|
| Procedural (sete tipos da 0.12) | Passe `sky`, sem mudança | já existe |
| Backdrop (cubemap, panorama, gradiente) | Geometria em `AFTER_SKY` (acima) | 0.14, fase 2 — **feito** |
| Estrelas procedurais, nebulosas procedurais | `VertexBuffer` estático por campo e malhas compartilhadas de nebulosa em `AFTER_SKY`, shaders de núcleo próprios | 0.14, fase 3 — **feito** |
| Catálogo de estrelas e constelações | Um `VertexBuffer` estático em `AFTER_SKY` (linhas e estrelas na mesma malha) | 0.14, fase 3 — **feito** |
| Imagens, flipbooks e impostors atrás das camadas | Quads em lote em `AFTER_SKY`, textura por PNG sob demanda, mesma camada de cobertura (proposta da fase 4; o atlas com `.mcmeta` previsto antes está em revisão) | 0.14, fase 4 — planejada |
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
- Uma receita do vfx-sample com um fundo de céu, estrelas e nebulosas, quando a 0.14 tiver capítulo próprio (nas fases 2 e 3, os exemplos estão no Javadoc, nesta página e no mod de teste).
- O JAR deste branch mantém o número 0.12.0 até o release da 0.14, mas **não** é o release: o aviso de build de desenvolvimento aparece no log, na descrição da lista de mods e no F7 (`CalamidadeAwakening.BUILD`); o release tira o aviso.
- Da 0.12.0, continuam: a falha intermitente do limite de 50 ms da cratera grande do `testeJogo` (causa não isolada; nesta rodada passou com 33,6 ms, o que não a resolve) e a pulsação no Overworld (o teste de regressão de céu entre dimensões da fase 2 deu diferença zero com a hora parada, mas a pulsação relatada não foi reproduzida nem explicada).
