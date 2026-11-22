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
- **[V] A RimuLib executa os passes no fim do `renderLevel`** (`GameRendererMixin`: injeções `renderLevel` em `HEAD` para captura, antes de `PostChain.process` para guardar a profundidade e em `RETURN` para desenhar). Logo, tudo o que for desenhado no mundo, inclusive em `AFTER_SKY`, já está no quadro quando o passe `sky` lê a cena.
- **[V] `Uniforms.glTexture` liga `GL_TEXTURE_2D`** (constante 3553 no bytecode, com `glBindSampler`): `samplerCube` não passa pelos passes da RimuLib.
- **[V] `RenderTarget` cria cor `GL_RGBA8`** (32856): um alvo próprio tem o mesmo limite de 8 bits da 0.12.
- **[V] `TextureManager`** guarda as texturas `Tickable` registradas; `TextureAtlas` é `Tickable` e `TextureAtlasHolder(TextureManager, atlas, info)` existe: atlas próprio com animação `.mcmeta`.
- **[V] Modelos e animação no jogo:** `AnimationLoader` do NeoForge lê `assets/<ns>/neoforge/animations/entity/*.json` para o `AnimationDefinition` vanilla; `KeyframeAnimations.animate` pede um `HierarchicalModel`; `ObjLoader` registrado como `neoforge:obj`; `ModelEvent.RegisterAdditional` com `ModelResourceLocation.standalone`. (Uso na 0.15; os formatos reais de exportação do Blockbench ainda serão validados antes de qualquer leitor.)
- **[V] `ProfileSkyEffects`** já pode sobrescrever `renderClouds` (hoje repassa ao efeito base).

## Backdrop: o que o experimento comprovou

(Preenchido com o relatório de `testeBackdrop`: ver a seção seguinte.)
