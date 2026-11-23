# Custom Sky Engine: arquitetura (0.14 e 0.15)

> **Estado:** a 0.14 (fases 0 a 4: Backdrop; estrelas, constelações e nebulosas; imagens, flipbooks e impostors) foi **publicada** no release v0.14.0 (tag no commit `4c9fd40`), integrada sobre a 0.13.0. Testada só por software (servidor dedicado e cliente Mesa llvmpipe); GPU física, shaderpacks e dois jogadores reais continuam pendentes. A 0.15 (Sky Objects & Cinematics) não começou. As seções abaixo são o documento de trabalho do branch `claude/ceu-personalizado`, mantidas como histórico de cada fase (inclusive os planos aprovados antes da implementação). Ainda não é capítulo do `MANUAL-GERAL.md` (`gerar_manual.py`); leia-o aqui.

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

## Fase 4: imagens, flipbooks e impostors (implementada)

### O que foi implementado e o que mudou em relação ao plano

Implementado como planejado abaixo, com quatro ajustes que a implementação mostrou necessários:

1. **Sem margem entre quadros.** O plano pedia 2 px de margem em volta de cada quadro. A prévia mostrou que 2 px só protegem os dois primeiros níveis de mipmap: um flipbook desenhado pequeno mistura o quadro vizinho. A solução adotada: os mipmaps são feitos **na CPU, por quadro** (caixa 2 × 2 só até o nível em que todo quadro continua inteiro, `SkySpriteMath.gridLevels`) e o shader prende a amostragem meio texel **do nível lido** para dentro do quadro. Resultado: nenhum vazamento em tamanho nenhum e sem margem (caso I6 da prévia, com o controle negativo "mipmaps do driver" vazando 49/255), e o mesmo resultado em qualquer driver, porque os mipmaps não vêm do `glGenerateMipmap`. Custo: quadros de lado pouco divisível por 2 têm menos níveis (36 px: 2; 64 px: 6).
2. **Orientação "de pé".** `SkyImage.Up` ficou com `HORIZON` e `PATH`; o giro com o céu do jogo é `celestial(true)` (posição e "de pé" giram juntos), o mesmo nome de `StarField` e `Nebula`, em vez de `Up.CELESTIAL`.
3. **Até quatro retângulos por sprite.** Quadros vizinhos do flipbook e vistas do impostor entram como até quatro retângulos ponderados de uma mesma textura (página). Um impostor animado com `blendFrames` usa no máximo `PAIR` nas vistas.
4. **Amostradores depois de `setDefaultUniforms`.** Conferido com `javap -c`: `ShaderInstance.setDefaultUniforms` grava `Sampler0..11` com `RenderSystem.getShaderTexture(i)`. Os amostradores do sprite são definidos depois dela; antes, a imagem saía com a textura que o fundo tinha deixado na unidade 0. O `testeImagens` pegou isso na primeira rodada.

Código: API `SkyImage`, `SkyImpostor` (`api.v1`); puro `SkySpriteMath`; cliente `SkyTextures` (substitui o `BackdropTextures`), `SkySprites` e `SkyObjectsFrame` (seleção, desenho intercalado, camada de frente); shader de núcleo `sky_sprite` (`sky_objects.glsl` agora recebe os parâmetros do objeto, com o mesmo resultado para estrelas e nebulosas); `sky.fsh` compõe a camada de frente; ferramenta `scripts/gerar_impostor.cjs`.

### Guia rápido para addons

```java
// Imagem estática (qualquer PNG com transparência): uma nave parada no céu, atrás das camadas procedurais.
Effects.images(ID, SkyImage.of("meumod:textures/sky/nave.png").direction(200, 35).size(14)
    .blend(SkyBlend.COVER).build());

// Flipbook: dragão batendo asas (folha 4 × 2), cruzando o céu pela Timeline e virado para onde voa.
Effects.timeline(ID, Timeline.builder(400).track("voo", Timeline.keyframe(0, -60), Timeline.keyframe(400, 60)).build());
Effects.images(ID, SkyImage.sheet("meumod:textures/sky/dragao.png", 4, 2).fps(10)
    .direction(180, 40).size(20).track(SkyImage.Param.AZIMUTH, "voo").up(SkyImage.Up.PATH).build());

// Impostor: nave pré-renderizada em 16 vistas, na frente de um portal do motor.
Effects.impostors(ID, SkyImpostor.of("meumod:nave").direction(90, 25).size(12)
    .heading(SkyImpostor.Heading.PATH).track(SkyImpostor.Param.AZIMUTH, "voo").layer(SkyImage.Layer.FRONT).build());
```

**Preparar um impostor (Blockbench ou outro programa 3D):** câmera ortográfica (ou de campo de visão estreito) apontada para o centro do objeto, fundo transparente, o mesmo enquadramento em todas as vistas. O objeto olha para +Z na yaw 0. Gire o **modelo** em passos fixos (por exemplo 16 vistas = 22,5°), com a câmera do lado −X do objeto na yaw 90. Para cada pitch, incline a câmera. O "para cima" da imagem é sempre o +Y do objeto. Exporte `y<yaw>_p<pitch>.png`, ou `y<yaw>_p<pitch>_f<quadro>.png` se for animado. Depois:

```
node scripts/gerar_impostor.cjs montar vistas/ --id meumod:nave --saida src/main/resources --lod 128:96,64:40
node scripts/gerar_impostor.cjs validar src/main/resources meumod:nave
```

A ferramenta confere contagem, passo de yaw, quadros, tamanhos e formato. Ela monta as páginas, com quadros inteiros por página e até 4 096 px, gera o descritor e os níveis de LOD por redução exata. Os erros são claros: vista faltando, yaw fora do passo, LOD de proporção errada, `below` acima da largura do nível, PNG corrompido. `exemplo <pasta>` grava vistas sintéticas para experimentar. Ela não abre modelos; isso, e modelos 3D reais no jogo, é a 0.15.

**Flashes:** marque com `flashing(true)` toda folha que pisca ou alterna brilho depressa; ela congela em `safeFrame` com "ocultar clarões" ou com os efeitos de tela em 0. A rede de segurança do motor, que conta alternâncias de brilho médio por quadro, não vê área pequena, cor saturada nem quadros vindos de trilha. A responsabilidade continua do addon.

## Fase 4: plano técnico (aprovado em 23/11/2026)

Imagens PNG e billboards arbitrários (naves, dragões, criaturas, estruturas, símbolos, portais ilustrados, objetos cósmicos irregulares, não só planetas), spritesheets e flipbooks e objetos 3D pré-renderizados (impostors). **Não entram** e continuam na 0.15: modelos Blockbench reais, geometria e animação 3D reais, Sky/World/Entity Space, objetos gigantescos próximos, transição do objeto distante para o real, portais que transportam objetos, colisão, profundidade física e integração com entidades e chefes. A 0.13 continua reservada aos Bosses. Marcadores como no resto do documento: **[H]** depende de API do jogo ainda não conferida e é conferido com `javap` na etapa 4.0, antes do primeiro uso.

### Decisões do autor (aprovação de 23/11/2026)

1. **Uma textura por PNG, sob demanda**, num cache compartilhado `SkyTextures` que generaliza o `BackdropTextures` da fase 2. Nada depende do atlas do jogo nem do `.mcmeta`: FPS, Timeline, reprodução única, laço, sincronia e animações complexas ficam com o addon.
2. **`layer(FRONT)` nesta fase**, como camada de composição visual do céu (não profundidade física), compatível com `NORMAL`, `LUMINOUS` e `COVER`, transparência e ordem, sem segundo pipeline.
3. **`gerar_impostor.cjs` nesta fase**: pequeno, documentado, trabalha com imagens já pré-renderizadas (organiza vistas, valida contagens, referências e dimensões, monta a grade e gera o descritor). Nenhum renderizador de arquivos Blockbench (seria antecipar a 0.15).
4. **Flipbooks e acessibilidade**: flipbooks normais continuam animando; só os marcados `flashing(true)` congelam (num quadro seguro) com "ocultar clarões" ou intensidade dos efeitos de tela 0. A responsabilidade de marcar é do addon; o motor acrescenta uma proteção complementar simples (abaixo), sem prometer detecção perfeita. `LOOP`, `ONCE`, `PING_PONG` e `TRACK` são determinísticos.
5. **Três melhorias aprovadas**: LOD sem saltos (histerese e troca equivalente), recursos ausentes ou inválidos sem derrubar o jogo (com casos negativos), limites de envio por perfil (quantidade e bytes, conservadores).

### Arquitetura

1. **[D] Mais um tipo de objeto do céu no caminho da fase 3.** Imagens, flipbooks e impostors são todos "um quadrilátero tangente à esfera do céu com até quatro retângulos de textura ponderados": um renderizador (`SkySprites`, chamado pelo `SkyObjectsFrame` no mesmo ouvinte de `AFTER_SKY`, na mesma lista ordenada por `order` que intercala estrelas e nebulosas), um shader de núcleo (`sky_sprite`, que importa `sky_objects.glsl`), os mesmos modos `SkyBlend`, as mesmas reações (`atmosphere`, `daylight`, `SkyDarkening`, `horizon`) e a **mesma camada de cobertura**. Atmosphere, escurecimento do eclipse, apagamento do sol e da lua do jogo e lente gravitacional valem sem código novo nos passes. Nenhum passe de tela inteira novo; nenhum cache, composição ou renderizador paralelo.
2. **[D] Objetos no infinito.** Direção, não posição: sem profundidade, terreno e nuvens na frente, sem paralaxe. Paralaxe e distância real são a 0.15.
3. **[D] Atrás das camadas procedurais por padrão; `FRONT` opcional.** O que é desenhado em `AFTER_SKY` fica atrás das sete camadas da 0.12 (um dragão passa atrás do buraco negro e é entortado pela lente, o certo para objetos distantes). Com `layer(FRONT)`, se o passe `sky` roda no quadro, os objetos `FRONT` vão para uma **camada de frente** de tela (RGBA8, limpa em zero, alocada só quando existe objeto `FRONT` **e** o passe roda) desenhada pelo mesmo `SkySprites` com o mesmo shader; o passe `sky` a compõe no fim, depois das camadas, só nos pixels de céu (`final = céu × (1 − a) + rgb`), sem lente; o passe `atmosphere` lê a cobertura dela junto com a da camada de cobertura para não velar de novo. Sem passe `sky` no quadro, `FRONT` é desenhado como os outros (igual a atrás) e nada é alocado. `FRONT` fica na frente de **todos** os objetos e camadas atrás, independentemente de `order`; entre objetos `FRONT` vale `order`. É composição visual: sem colisão, sem profundidade física, sem interação com o portal.
4. **Separação comum × cliente.** A definição (comum) guarda só textos de recurso e parâmetros e é registrada nos dois lados; o cliente carrega texturas e descritores. **Nenhum pacote novo, nenhum canal novo**: tudo deriva do relógio da instância (efeitos e eventos globais) ou do estado do perfil (`sky_profile_v1`).
5. **Dados por objeto numa textura de dados** (`DataTexture`, RGBA32F, como feixes e luzes): cada vértice leva só a posição no mundo, o canto e o índice do objeto; o shader lê modo, opacidade, brilho, reações, tinta, pesos e retângulos com `texelFetch`. Assim objetos com parâmetros diferentes compartilham a mesma chamada de desenho quando usam a mesma textura.

### APIs públicas (só aditivas; `checkApiCompat` em toda etapa)

Classes novas em `api.v1`: `SkyImage`, `SkyImpostor`. Nenhuma assinatura existente muda; `Sky.Type`, `SkyBlend` e `SkyDarkening` não ganham constantes. Métodos novos: `Effects.images`, `Effects.impostors`, `SkyProfile.StateBuilder.images/impostors`, `SkyProfile.State.images()/impostors()`.

```java
// Imagem estática: planeta ilustrado, símbolo, estrutura gigante, criatura parada
SkyImage dragao = SkyImage.of("meumod:textures/sky/dragao.png")
    .direction(120, 35)            // azimute, elevação (graus), mesmo referencial do Sky e das estrelas
    .size(18)                      // largura angular em graus (0, 160]; altura pela proporção do quadro (ou size(l, a))
    .roll(-10).pivot(.5f, .5f)     // giro no plano do céu (graus) em torno do pivô (0..1 da imagem, 0,0 = canto superior esquerdo)
    .up(SkyImage.Up.HORIZON)       // HORIZON (de pé em relação ao zênite) ou PATH (o +X da imagem segue o movimento)
    .celestial(false)              // true: posição e "de pé" giram com o céu do jogo, como as estrelas
    .blend(SkyBlend.COVER).tint(0xFFFFE0C0).brightness(1f).opacity(1f)
    .atmosphere(.6f).daylight(0f).darkening(SkyDarkening.DIM, 1f).horizon(4f)
    .order(300).layer(SkyImage.Layer.BEHIND)    // BEHIND (padrão) ou FRONT (na frente das camadas procedurais)
    .filter(SkyImage.Filter.SMOOTH)             // SMOOTH (linear + mipmaps) ou PIXEL (sem filtro, arte em pixel)
    .track(SkyImage.Param.AZIMUTH, "voo_az")     // AZIMUTH, ELEVATION (graus somados), SIZE, ROLL, OPACITY, BRIGHTNESS, FRAME
    .weightPhase("aparicao")
    .build();

// Spritesheet / flipbook: a mesma classe, com a grade de quadros
SkyImage asas = SkyImage.sheet("meumod:textures/sky/dragao_voo.png", 4, 2)   // colunas, linhas (ordem por linha)
    .frames(8).fps(12)
    .play(SkyImage.Play.LOOP)      // LOOP, ONCE (para no último), PING_PONG, TRACK (quadro vindo da trilha FRAME)
    .clock(SkyImage.Clock.INSTANCE) // INSTANCE (idade da instância, igual em todos os clientes) ou WORLD (tempo do jogo)
    .startFrame(0).blendFrames(true)            // mistura entre quadros vizinhos (2 retângulos; desligada em LOW)
    .flashing(false).safeFrame(0)  // flashing(true): congela em safeFrame com "ocultar clarões" ou efeitos de tela 0
    .direction(200, 40).size(25).build();

// Impostor: objeto 3D pré-renderizado de vários ângulos (o descritor vem dos recursos)
SkyImpostor nave = SkyImpostor.of("meumod:nave")   // assets/meumod/calamidade/impostors/nave.json
    .direction(90, 20).size(12)
    .orientation(45, 0, 0)         // yaw, pitch, roll do objeto, em graus
    .heading(SkyImpostor.Heading.PATH)   // FIXED ou PATH (a frente do objeto aponta para onde a trilha o leva)
    .viewBlend(SkyImpostor.ViewBlend.AUTO)  // NEAREST, PAIR, QUAD; AUTO segue o perfil de qualidade
    .track(SkyImpostor.Param.YAW, "giro").build();

Effects.images(id, dragao, asas);          // até 8 por efeito; eventos globais herdam do efeito
Effects.impostors(id, nave);               // até 4 por efeito
SkyProfile.state("guerra").images(...).impostors(...);   // constantes por estado; transição por crossfade
```

Opções comuns copiam as de `StarField` e `Nebula` (mesmos nomes, faixas e validação contra a Timeline em `validate`). Padrões: `SkyBlend.NORMAL`, véu 1, dia 0 (imagens não somem de dia, ao contrário das estrelas), `SkyDarkening.IGNORE`, horizonte 0, ordem 300 (na frente das estrelas, 200, e nebulosas, 100), `BEHIND`, `SMOOTH`, não celeste. Limites: tamanho angular (0, 160]°, grade até 64 × 64, até 4 096 quadros por folha, FPS (0, 120].

### Tipos de imagem e formatos para addons

| Recurso | Onde | Regras |
|---|---|---|
| Imagem estática | `assets/<ns>/textures/...*.png` (qualquer caminho de textura) | PNG, alfa direto (não pré-multiplicado; o motor pré-multiplica na carga). Lado até 4 096 px; acima do `GL_MAX_TEXTURE_SIZE` da placa, reduzida pela metade até caber, com aviso único. Potência de 2 não é exigida (recomendada com `SMOOTH`). |
| Spritesheet | um PNG em grade | Quadros do mesmo tamanho, ordem por linha. **Sem margem obrigatória**: com `SMOOTH`, os mipmaps são feitos por quadro (só até o nível em que todo quadro continua inteiro) e o shader prende a amostragem meio texel do nível lido para dentro do quadro (provado na prévia, caso I6, com controle negativo). Quadros de lado potência de 2 (32, 64, 128) ganham mais níveis e cintilam menos quando pequenos. `.mcmeta` não é lido. |
| Impostor | descritor `assets/<ns>/calamidade/impostors/<nome>.json` + PNGs | Lido na primeira vez que aparece e de novo depois da recarga de recursos (F3+T). |

```json
{
  "format": 1,
  "textures": ["meumod:textures/sky/nave_views.png"],
  "frame": [256, 256], "grid": [16, 1], "gutter": 0,
  "views": { "yaw": 16, "pitch": [0], "mirror": false },
  "frames": 1, "fps": 8, "play": "loop",
  "pivot": [0.5, 0.5],
  "lod": [ { "textures": ["meumod:textures/sky/nave_views_128.png"], "frame": [128, 128], "below": 96 } ]
}
```

- **Vistas.** Vista (`yaw` θ, `pitch` φ) = câmera na direção local `direction(θ, φ)` do objeto (mesma convenção de azimute e elevação do Sky: θ 0 = +Z local, a frente do objeto; θ 90 = −X). `yaw` é o número de vistas numa volta (θ = k × 360 / n); `pitch` é a lista de elevações em ordem crescente; `mirror: true` usa simetria bilateral (guarda só θ em [0, 180] e espelha a imagem do outro lado).
- **Índice na grade:** `((quadro × nPitch) + pitch) × nYaw + yaw`, por linha. **Uma página contém sempre quadros inteiros** (todas as vistas de um quadro na mesma textura), para a mistura entre vistas nunca precisar de duas texturas; as páginas seguintes continuam a contagem.
- **LOD:** níveis menores com o mesmo número de páginas, grade e proporção; `below` em pixels de tela e no máximo a largura do quadro daquele nível (ver LOD abaixo).
- Erros do descritor (campo faltando, contagem que não fecha, página inexistente, proporção diferente no LOD) são mensagens claras no log, uma vez; o impostor não é desenhado e o resto do céu continua.

**Ferramenta `scripts/gerar_impostor.cjs` (Node, sem dependência):** recebe uma pasta de PNGs já pré-renderizados (Blockbench, Blender ou qualquer outra ferramenta), com nomes `y<graus>_p<graus>[_f<quadro>].png` ou um manifesto, valida contagem de vistas e quadros, tamanhos iguais e proporção, monta as páginas da grade (margem opcional), gera o descritor JSON e, opcionalmente, os níveis de LOD reduzidos; valida também um descritor existente contra os PNGs. Leitura e escrita de PNG próprias (zlib do Node), saída byte a byte reproduzível. A documentação ensina a preparar as vistas no Blockbench (câmera ortográfica, fundo transparente, giro do modelo em passos fixos); a ferramenta não abre modelos.

### Estratégia de renderização

- **Lote por textura.** Todo quadro a CPU monta os quadriláteros dos sprites visíveis (4 vértices cada, dezenas no máximo) num `VertexBuffer` dinâmico reaproveitado (buffers de bytes reaproveitados, sem alocação por objeto) e desenha **uma chamada por sequência contígua de sprites com a mesma textura** na ordem de desenho (deslocamento no buffer de índices). Recorte por cone de visão na CPU; fora da tela não custa nada.
- **Posição e orientação na CPU.** Direção (com trilhas e giro celeste), base do plano (`HORIZON`, `PATH`, `roll`), pivô e tamanho angular viram quatro cantos por projeção gnomônica (o quadrilátero é plano e a textura sai com perspectiva correta). O vértice leva posição no mundo × 50, canto (UV0) e índice do objeto.
- **Shader `sky_sprite`.** Lê da textura de dados até 4 retângulos com pesos (quadros vizinhos do flipbook e vistas do impostor), amostra cada um preso dentro do seu retângulo, soma ponderada (alfa pré-multiplicado), multiplica pela tinta e devolve o pixel pela composição de `sky_objects.glsl` (refatorada para receber os parâmetros, com resultado idêntico para estrelas e nebulosas: `checkCeuReferencia` com diferença zero prova). `COVER` usa o limiar alfa ≥ 0,5 da fase 3 (silhueta, nunca retângulo).
- **Alfa pré-multiplicado na carga** (fora da thread de render): sem franjas escuras com filtro linear e mipmaps.
- **Filtro por textura**: `SMOOTH` = linear + mipmaps feitos na CPU, na thread de trabalho (caixa 2 × 2 sobre dados pré-multiplicados, por quadro: `SkySpriteMath.gridLevels`; o mesmo resultado em qualquer driver); `PIXEL` = sem filtro, sem mipmaps. A chave do cache inclui o filtro e, para `SMOOTH`, a grade.
- **Flipbook.** Quadro calculado na CPU (`SkySpriteMath.frame`, Java puro e determinístico): `INSTANCE` usa a idade da instância (igual em todos os clientes, sem pacote), `WORLD` o tempo do jogo, `TRACK` a trilha `FRAME`; `LOOP`, `ONCE` (para no último), `PING_PONG` (ida e volta sem repetir as pontas). Só muda o retângulo: mil quadros custam o mesmo que um, nenhum envio por quadro.
- **Impostor.** Para um objeto no infinito a direção da câmera até ele é a própria direção dele no céu, então a vista depende só da orientação e da posição no céu: vetor de vista local = inverso da rotação do objeto × (−direção). `NEAREST` (uma vista), `PAIR` (as duas vizinhas em yaw), `QUAD` (yaw × pitch, bilinear); pesos na CPU, troca de vista por mistura, sem salto. `AUTO`: LOW `NEAREST`, BALANCED `PAIR`, HIGH e ULTRA `QUAD`. Com `blendFrames` num impostor animado, a mistura de vistas cai para `PAIR` (no máximo 4 retângulos).

### Cache e gerenciamento de memória (`SkyTextures`)

- **[D] Um cache para todo o céu**: o `BackdropTextures` vira `SkyTextures`, usado pelo Backdrop e pelos sprites. Uma textura por PNG e filtro, compartilhada; textura própria do motor (não pelo `TextureManager`), liberada pelo próprio cache.
- **Fundo sem mudança de comportamento**: o Backdrop continua carregando de forma síncrona no primeiro uso, sem filtro, sem pré-multiplicar, com a cópia grossa na CPU (névoa do horizonte, sol e lua) e o mesmo modo de repetição; os gradientes gerados também passam pelo cache. Prova: `testeBackdrop` e `checkCeuReferencia`.
- **Sprites assíncronos**: leitura do recurso, decodificação, redução e pré-multiplicação numa thread de trabalho; na thread de render só o envio (`glTexImage2D` e mipmaps). O objeto aparece com um fade curto (0,25 s) quando a textura fica pronta. Efeitos e eventos globais pedem as texturas assim que a instância está ativa no cliente.
- **Limites de envio por perfil** (quantidade e bytes por quadro): LOW 1 envio / 4 MiB, BALANCED 1 / 8 MiB, HIGH 2 / 16 MiB, ULTRA 2 / 32 MiB. Um envio maior que o limite de bytes só sai sozinho no quadro (senão nunca carregaria). Pontos de partida conservadores: o custo do envio de 4, 16 e 64 MiB é medido no `testeImagens` (llvmpipe, só tendência) e registrado; não sobem sem medição.
- **Orçamento de VRAM por perfil** (pontos de partida, a medir): LOW 64 MiB, BALANCED 128, HIGH 256, ULTRA 512, contando RGBA8 com mipmaps (+⅓) de tudo o que o cache tem, fundos incluídos. Fundos nunca são recusados (comportamento da fase 2); um sprite novo que estouraria o orçamento primeiro libera texturas sem uso há 60 quadros, depois usa o nível de LOD menor (impostor) e, sem saída, é recusado com aviso único no log e no F7, sem travar.
- **Liberação** depois de 1 200 quadros sem uso (como as malhas da fase 3), na recarga de recursos e ao sair do mundo. Cargas em andamento de uma geração antiga (recarga no meio) são descartadas e fechadas quando chegam.
- **Recursos ausentes ou inválidos** (PNG que não existe, PNG corrompido, grade que não divide a imagem, descritor inválido, página faltando): o recurso não é desenhado, uma mensagem informativa por recurso no log (sem repetir), contagem e o último erro no F7; os outros objetos continuam. Depois de F3+T tenta de novo.
- **F7:** imagens e impostors desenhados, camada de frente, texturas residentes, VRAM estimada e orçamento, envios no quadro, cargas pendentes, recusas e recursos inválidos.

### LOD sem saltos (impostors)

- Escolha pelo tamanho na tela (pixels da largura do objeto) com **histerese**: desce para o nível menor abaixo de 0,85 × `below`, volta acima de 1,15 × `below`, e a condição precisa durar 10 quadros seguidos; nada de alternar perto do limite.
- **Troca equivalente**: `below` não pode passar da largura do quadro do nível menor (o descritor é recusado se passar). Assim, no momento da troca, as duas texturas estão sendo **reduzidas** na tela e, com mipmaps, a imagem é a mesma (o nível grande já estava amostrando o mipmap do tamanho do pequeno): não há crossfade a fazer nem piscada.
- Troca só quando o nível novo está pronto (até lá continua o atual); proporção, pivô, orientação e alfa são os mesmos em todos os níveis (validado).

### Proteção de flashes

- `flashing(true)`: com "ocultar clarões" ligado ou intensidade dos efeitos de tela 0, o flipbook congela em `safeFrame` (padrão `startFrame`).
- **Complemento simples, não detecção perfeita**: na carga (thread de trabalho) o motor mede a luminância média de cada quadro da folha (alfa incluído) e conta as viradas opostas de 10 % ou mais na sequência; se a folha, no FPS pedido, passa de 3 viradas por segundo, ela é tratada como `flashing(true)` com "ocultar clarões" ligado, com um aviso único no log. Não vê flashes de área pequena, cor saturada ou trilha `FRAME`; por isso a marcação do addon continua obrigatória.
- `spin` não existe nos sprites; movimento vem das trilhas, que são conteúdo. As outras opções de acessibilidade continuam como estão.

### Composição e cobertura

Sem regra nova: os três modos e a camada de cobertura da fase 3 valem para qualquer imagem. `NORMAL` para PNG com transparência real (cores exatas, alfa contínuo), `LUMINOUS` para o que brilha (o fundo só clareia), `COVER` para silhuetas sólidas (o dragão esconde a nebulosa e o sol só dentro da forma). Ordem entre objetos por `order`, em empate o mais importante por cima; o Backdrop sempre atrás de todos; as camadas procedurais na frente, salvo `FRONT`.

### Integração com Timeline, SkyProfiles e WorldEvents

- Trilhas por nome: `AZIMUTH`, `ELEVATION` (graus somados à direção), `SIZE`, `ROLL`, `OPACITY`, `BRIGHTNESS`, `FRAME` (impostor também `YAW`, `PITCH`); `weightPhase`. Uma nave que cruza o céu = duas trilhas e `Heading.PATH` (a direção do movimento sai da trilha um tick à frente).
- Perfis de dimensão: imagens e impostors constantes por estado (sem trilhas), crossfade entre estados, flipbooks com `Clock.WORLD`.
- Eventos globais: herdam do efeito, como camadas, fundos e estrelas.
- Seleção como a da fase 3 (eventos globais, perfil da dimensão, HERO, opacidade); tetos por quadro: imagens 8 / 24 / 48 / 96 e impostors 2 / 4 / 8 / 16 em LOW / BALANCED / HIGH / ULTRA (AUTO segue o perfil escolhido).
- Mesmas regras do céu do jogo (cegueira, escuridão, lava, neve em pó, névoa de bioma ou de chefe) e a opção `customSkies`.

### Estratégia de testes (pela regra de [AGENTS.md](../AGENTS.md#quando-rodar-cada-teste-regra-permanente-desde-a-fase-3-da-014))

- **Durante o desenvolvimento:** `SkySpriteCheck` no `checkBudget` (construtores e casos inválidos; quadro em `LOOP`, `ONCE`, `PING_PONG`, `TRACK`, `INSTANCE`, `WORLD`, congelamento; vistas e pesos do impostor, `PATH`, `mirror`; LOD com histerese; descritor válido e inválidos; orçamento de VRAM e de envios; heurística de flashes; recorte; chaves de lote); `checkShaders`; `checkApiCompat`; `checkObjetosComposicao` ampliado com casos de imagem (cor exata, silhueta `COVER`, `LUMINOUS`, Atmosphere, eclipse, lente, `FRONT` sobre uma camada, quadros sem vazamento em qualquer tamanho, troca de vista), cada um com controle negativo. Minecraft não é aberto nas etapas 4.0, 4.2, 4.3 e 4.5.
- **Em jogo, `testeImagens`** (um só teste, todos os cenários numa inicialização do cliente): nada sem addon; cor exata; silhueta; flipbook com cor sentinela por quadro em tempos conhecidos; `ONCE` parado; quadro pela trilha; `flashing` congelado; impostor com vistas coloridas; LOD; `FRONT` na frente de uma camada; PNG ausente, corrompido e descritor inválido sem quebrar o resto; recarga; VRAM e envios contabilizados; chamadas de desenho ≤ sequências de textura; `customSkies`; cegueira; regressão do céu do Overworld; custo. Roda uma vez quando as imagens estáticas ficam prontas (só a parte estática) e uma vez no fim da fase.
- **Rodada consolidada no fim da fase, pelo risco:** `checkCeuReferencia` (as 38 cenas com diferença zero + cenas novas); `testeEstrelas` e `testeBackdrop` **uma vez** cada (o `SkyObjectsFrame` e o cache de texturas do fundo mudam); `testeCeu` **uma vez** porque `FRONT` mexe no passe `sky`; build do vfx-sample. `testeJogo` **não**: a fase não mexe em servidor, rede, terreno nem estado de servidor (só registros comuns sem estado); se isso mudar, ele entra.
- O registro em VALIDACAO.md separa executado, reaproveitado (com commit) e pendente.

### Riscos e limitações

- **Impostors:** sem paralaxe e sem mudar com o movimento do jogador; iluminação assada nas imagens (tinta e `daylight` atenuam, não corrigem); "fantasma" na mistura de vistas em objetos finos (mais vistas resolvem, com mais VRAM). Ver de perto ou de qualquer ângulo: modelos reais, 0.15.
- **VRAM e engasgos:** folhas grandes consomem memória rápido; o envio de uma textura de 4 096² na thread de render custa alguns ms (por isso um envio por quadro e decodificação fora dela). Placas antigas podem ter `GL_MAX_TEXTURE_SIZE` menor.
- **Vazamento entre quadros**: resolvido sem margem (mipmaps por quadro e amostragem presa ao nível lido); o custo é ter menos níveis de mipmap quando o lado do quadro não é par muitas vezes (36 px: 2 níveis).
- **8 bits:** luz somada sobre fundo claro estoura em 255 antes dos passes.
- **`COVER` com arte de borda suave** fica serrilhado no limiar 0,5: use `NORMAL`.
- **`FRONT`** depende do passe `sky` rodando; não sofre lente; nuvens seguem a regra das camadas da 0.12; não é profundidade física.
- **Heurística de flashes** é um complemento e falha em casos reais (área pequena, cor saturada, trilha).
- **Não testado e não afirmado:** GPU física, Iris/Oculus e shaderpacks, Sodium/Embeddium, outros mods em `AFTER_SKY`, dois jogadores. Direitos das imagens dos addons são de quem as publica.

### Etapas (um commit por entrega real)

| Etapa | Conteúdo | Verificação |
|---|---|---|
| 4.0 | `javap` de tudo marcado [H]; `SkySpriteMath`; API `SkyImage`, `SkyImpostor` e registros; `SkySpriteCheck` | build, `checkApiCompat` |
| 4.1 | `SkyTextures` (Backdrop migrado sem mudança visível), `SkySprites`, shader `sky_sprite`, lote, camada de cobertura, `Effects.images`, perfis, eventos, F7 | build, `checkShaders`, `checkObjetosComposicao` ampliado, `checkCeuReferencia`; `testeImagens` (parte estática) uma vez |
| 4.2 | Flipbooks: grade, relógios, trilha `FRAME`, `blendFrames`, mipmaps por quadro, `flashing` e heurística | build, puros, prévia |
| 4.3 | Impostors: descritor e recarga, vistas e mistura, `PATH`, LOD com histerese, orçamento de VRAM | build, puros, prévia |
| 4.4 | `layer(FRONT)` | `checkCeuReferencia` (diferença zero nas 38), prévia |
| 4.5 | `gerar_impostor.cjs`, texturas de teste por script (byte a byte iguais), exemplos no Javadoc e nesta página | a própria ferramenta sobre os PNGs de teste |
| 4.6 | Rodada consolidada, VALIDACAO.md, capturas | lista da seção de testes |

## Visão geral consolidada (0.14 e 0.15)

| Modalidade | Caminho de desenho | Versão |
|---|---|---|
| Procedural (sete tipos da 0.12) | Passe `sky`, sem mudança | já existe |
| Backdrop (cubemap, panorama, gradiente) | Geometria em `AFTER_SKY` (acima) | 0.14, fase 2 — **feito** |
| Estrelas procedurais, nebulosas procedurais | `VertexBuffer` estático por campo e malhas compartilhadas de nebulosa em `AFTER_SKY`, shaders de núcleo próprios | 0.14, fase 3 — **feito** |
| Catálogo de estrelas e constelações | Um `VertexBuffer` estático em `AFTER_SKY` (linhas e estrelas na mesma malha) | 0.14, fase 3 — **feito** |
| Imagens, flipbooks e impostors atrás das camadas | Quads em lote em `AFTER_SKY`, textura por PNG sob demanda (`SkyTextures`), mesma camada de cobertura; sem atlas nem `.mcmeta` (decisão da fase 4) | 0.14, fase 4 |
| Imagens e impostors na frente das camadas (`FRONT`) | Camada de frente sob demanda, composta no fim do passe `sky` | 0.14, fase 4 |
| Modelos na frente de camadas ou mascarados por portal | A mesma camada de frente, estendida | 0.15 |
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

## O que a fase 4 deixa pronto para a 0.15 (sem implementar nada dela)

- **Camada de frente** (`CaElFront`, composta no fim do passe `sky`, só nos pixels de céu, sem lente): o mesmo ponto em que modelos 3D distantes poderão ficar na frente de um portal ou de um buraco negro.
- **Orientação e vistas** (`SkySpriteMath.orientation`, `viewAngles`, `Heading.PATH`): a convenção de frente (+Z), yaw e pitch que um modelo Blockbench real vai usar, e a transição natural "impostor ao longe, modelo de perto".
- **Cache único de texturas com orçamento por perfil** (`SkyTextures`): pronto para as texturas de modelos e para os níveis de LOD.
- **Formato de impostor e ferramenta**: um modelo da 0.15 pode ter um impostor gerado das próprias vistas para a distância.
- Continuam **não** começados: modelos Blockbench reais, geometria e animação 3D, Sky/World/Entity Space, objetos gigantescos próximos, portais que transportam objetos, colisão e integração com entidades e chefes.

## Pendências que continuam abertas

- Da fase 4: medir em GPU física o custo do envio de texturas grandes (2 048² e 4 096²) e de folhas de impostor reais, e só então ajustar os limites de envio e de memória por perfil; uma receita do vfx-sample com imagem, flipbook e impostor (nesta fase os exemplos estão no Javadoc, nesta página e no mod de teste).

- Da 0.12.0 (VALIDACAO.md): multiplayer manual com dois jogadores; a pulsação observada no Overworld (causa não identificada, não é bug confirmado). Quando a fase 2 tocar em Atmosphere ou no estado do céu entre dimensões, entra um teste de regressão que captura o céu do Overworld antes e depois de mudar os estados da dimensão de teste, com a hora congelada.
- Da 0.15: validar os formatos reais de exportação do Blockbench (Bedrock `.geo.json`, Java, OBJ) e o subconjunto de animação antes de qualquer leitor.
- GPU física, Iris/Oculus e Sodium/Embeddium com os renderizadores novos (o Backdrop foi medido só por software; nenhuma compatibilidade com shaderpacks ou outros mods é afirmada).
- Uma receita do vfx-sample com um fundo de céu, estrelas e nebulosas, quando a 0.14 tiver capítulo próprio (nas fases 2 e 3, os exemplos estão no Javadoc, nesta página e no mod de teste).
- Integrada sobre a 0.13.0 (Boss Engine) no release 0.14.0 (branch `claude/integracao-0.13-0.14-u8nraa`): o JAR tem o número 0.14.0 e o aviso de build de desenvolvimento saiu (`CalamidadeAwakening.BUILD = "release"`); **publicada** pelo workflow de release com a tag `v0.14.0` (commit `4c9fd40`, SHA-256 do motor `02851af9…cb0cb7`).
- Da 0.12.0, continuam: a falha intermitente do limite de 50 ms da cratera grande do `testeJogo` (causa não isolada; nesta rodada passou com 33,6 ms, o que não a resolve) e a pulsação no Overworld (o teste de regressão de céu entre dimensões da fase 2 deu diferença zero com a hora parada, mas a pulsação relatada não foi reproduzida nem explicada).
