# Custom Sky Engine: arquitetura (0.14 e 0.15)

Documento de trabalho do branch `claude/ceu-personalizado`. Fases 0 e 1 concluídas; a fase 2 (primeiros renderizadores) espera aprovação. Ainda não é capítulo do manual: entra em `gerar_manual.py` quando a 0.14 tiver API.

Marcadores: **[V]** verificado (código, `javap`/fontes do NeoForge 21.1.267, bytecode da RimuLib 1.3.0 ou experimento em jogo, com a fonte indicada); **[H]** hipótese; **[D]** decisão de arquitetura.

## Princípios

1. **Só aditivo.** Os sete fenômenos procedurais da 0.12.0 (`BLACK_HOLE`, `PORTAL`, `ECLIPSE`, `RIFT`, `CELESTIAL_BODY`, `AURORA`, `DISTANT_BOLTS`), seus shaders, parâmetros, prioridades, lentes e integrações ficam como estão. Os tipos novos são classes novas; `Sky.Type` não ganha constantes (o `sky.fsh` usa o ordinal e addons podem ter `switch` sobre ele).
2. **Tudo por escolha do addon.** Sem addon pedindo, o quadro é o da 0.12.0: nenhum passe, alvo, textura ou malha nova.
3. **Quem não usa não paga.** Cada modalidade tem o próprio caminho de desenho; um céu estático não liga o passe procedural.
4. **Limites medidos.** Os números das propostas são pontos de partida; só viram limite depois de benchmark.

## Guardas de regressão (fase 1)

| Guarda | Como roda | O que garante |
|---|---|---|
| Compatibilidade binária (`checkApiCompat`, no `check`) | `scripts/ApiCompat.java` contra `scripts/api-base/api-v1-0.12.0.txt` (89 classes, 1038 membros, do JAR byte a byte igual ao release v0.12.0) | Nenhuma classe ou membro público/protegido some ou muda de descritor; cabeçalhos iguais; tipos extensíveis sem método abstrato novo; enums sem constante removida ou reordenada. Autoteste com as cinco quebras simuladas. |
| Referência visual (`./gradlew checkCeuReferencia`) | `scripts/referencia_ceu.cjs`: passes reais `sky` e `atmosphere` (SwiftShader, 8 bits entre os passes) em 15 cenas contra `src/test/resources/referencia-ceu/` | Aparência dos sete fenômenos, de quatro camadas juntas e da atmosfera sozinha igual à 0.12.0 (tolerância só para ruído de ponto flutuante; escurecer a fenda em 20% é acusado). |
| Testes em jogo existentes | `testeJogo`, `testeCeu` | Eventos globais, perfis, eclipse em três dimensões, respawn, relogin. |
| Experimento do Backdrop (`xvfb-run ./gradlew testeBackdrop`) | Protótipo só no mod de teste (`BackdropExperimento`, `RoteiroBackdrop`) | As afirmações da seção "Backdrop: o que o experimento comprovou". |

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

`xvfb-run -a ./gradlew testeBackdrop` (22/11/2026, Mesa llvmpipe, 1280 × 720, mundo plano do `testeJogo`, vfx-sample 1.9.0): **13 de 13 afirmações**. O protótipo é um cubo de raio 50 em volta da câmera, só com a rotação da vista, textura 3 × 2 com uma cor por face, desenhado em `AFTER_SKY` com `depthMask(false)`, sem teste de profundidade e sem descarte de faces, pelo shader `position_tex_color` do jogo. Capturas em `run/teste-backdrop/screenshots/` (fora do repositório).

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
| **Eclipse** sobre fundo opaco | Disco, coroa e raios corretos por cima; o fundo escurece com o mundo (desejado), mas as cores também se deslocam pelo mesmo motivo da Atmosphere (o eclipse do exemplo traz uma atmosfera). A marca do apagamento do sol sobre o fundo não ficou isolável nesta medição (razão dentro/fora do quadrado do sol 1,43 com a coroa por cima) | INFO: precisa de regra |
| Cegueira | O jogo não desenha céu (centro preto); o protótipo desenha assim mesmo | INFO: precisa de regra |
| Horizonte | Uma linha clara no fim do terreno: a névoa do terreno distante usa a cor de névoa do jogo, não a do fundo | INFO: precisa de opção |
| Custo (software, só tendência) | Mediana 21,9 → 26,4 ms e p95 29,1 → 32,2 ms na segunda rodada; 22,0 → 23,8 ms na primeira. Ruidoso; GPU física pendente | INFO |

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

## Visão geral consolidada (0.14 e 0.15)

| Modalidade | Caminho de desenho | Versão |
|---|---|---|
| Procedural (sete tipos da 0.12) | Passe `sky`, sem mudança | já existe |
| Backdrop (cubemap, panorama, gradiente) | Geometria em `AFTER_SKY` (acima) | 0.14, fase 2 |
| Estrelas procedurais, nebulosas procedurais | Domo em `AFTER_SKY` com shader próprio | 0.14, fase 3 |
| Catálogo de estrelas e constelações | Um `VertexBuffer` estático em `AFTER_SKY` | 0.14, fase 3 |
| Imagens e impostors atrás das camadas | Quads em `AFTER_SKY`, atlas próprio com `.mcmeta` | 0.14, fase 4 |
| Imagens, impostors e modelos na frente de camadas ou mascarados por portal | Alvo próprio sob demanda, composto dentro do passe `sky` | 0.14 (imagens) e 0.15 (modelos) |
| Modelos em World e Entity Space | `RenderType` de entidade do jogo; cache opcional | 0.15 |
| Portais no mundo, transições, ponte para entidades | Timeline existente; recorte CPU × GPU escolhido por medição | 0.15 |

Regras que valem para todas: nada novo sem um addon pedir; separação entre definição (comum, só `ResourceLocation`s e parâmetros), configuração visual e instância ativa (cliente); nenhum pacote por quadro; limites só depois de benchmark; `checkApiCompat` e `checkCeuReferencia` em toda etapa.

## Pendências que continuam abertas

- Da 0.12.0 (VALIDACAO.md): multiplayer manual com dois jogadores; a pulsação observada no Overworld (causa não identificada, não é bug confirmado). Quando a fase 2 tocar em Atmosphere ou no estado do céu entre dimensões, entra um teste de regressão que captura o céu do Overworld antes e depois de mudar os estados da dimensão de teste, com a hora congelada.
- Da 0.15: validar os formatos reais de exportação do Blockbench (Bedrock `.geo.json`, Java, OBJ) e o subconjunto de animação antes de qualquer leitor.
- GPU física, Iris/Oculus e Sodium/Embeddium com os renderizadores novos.
