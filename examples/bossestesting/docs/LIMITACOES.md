# Limitações reais encontradas (insumo para a futura Combat VFX Engine)

Tudo abaixo apareceu ao construir o BossesTesting 0.1.0 só com a API pública do Calamidade 0.14.0, sem mudar o
motor. Cada item diz o contorno usado no addon e o que faria falta.

## Terreno (Terrain API)

1. **Primitivas não giram individualmente.** `TerrainShape.trench` só segue a frente da instância; fissuras radiais
   (Ground Slam V2, Leap Smash) viraram um anel de crateras (`FxP1.craterRing`). Falta: rotação por primitiva ou
   trincheira por polilinha.
2. **Edições fixas por id de efeito.** A intensidade (LOW/NORMAL/EPIC) e o "sem terreno" exigem registrar cada
   efeito destrutivo 4 vezes (`fx/Variants`, 9 efeitos → 36 ids). Falta: escala/intensidade passada no `start`.
3. **Sem ordem espacial do commit.** `blocksPerTick` espalha a edição por seção de chunk, não ao longo do rasgo; a
   trincheira do feixe não "corre" do ponto de impacto para frente. Falta: commit ordenado por distância/eixo.
4. **Fonte SCRIPT não passa pela regra `mobGriefing` do motor.** Impactos de míssil usam fonte SCRIPT (por causa da
   cota por chefe) e o addon confere `mobGriefing` e a config por conta própria (`Variants.Destruction`).

## Parâmetros por instância

5. **Feixes, luzes e decalques não recebem parâmetros em tempo de execução.** O comprimento do feixe do reator é
   fixo (48/52 blocos) e depende da oclusão pelo terreno; o ponto de acerto real vira uma segunda instância (a
   cicatriz). Raio do aviso e comprimento da faixa são inferidos no cliente pelo id do efeito. Falta: floats por
   instância sincronizados no `start` (como `Placement`, mas com valores livres).
6. **Pose de osso só no cliente.** `AnchorCapture` é visual; o servidor usa `RigAnchors.approximate` (pose estática),
   então a saída dos mísseis e o ponto do reator no servidor são aproximados. Falta: amostragem de rig no servidor
   ou "sockets" declarativos com animação.

## Cotas e câmeras

7. **Câmera só para o conjurador.** `AddonLayers.film` toca no cliente do caster; para todos os participantes verem
   pouso, transformação e morte, o addon inicia uma instância de câmera por jogador (gasta vagas de 16 por jogador).
8. **`maxPerBoss` 8 e 4 por entidade.** Auras de fase, ataque, câmera e 18 impactos de míssil não cabem; impactos e
   efeitos puramente visuais usam fonte SCRIPT. Falta: cota separada para efeitos "efêmeros de projétil".

## Renderização e pós

9. **Sem decalque no chão nem impact frame estilizado no motor.** Avisos (círculos, faixas), impact frames em
   tinta e onda de choque volumétrica são passes próprios (`shaders/fx/bt_*.fsh`) e disputam o orçamento HERO
   (1/2/3 passes). Em LOW o addon recai em malhas de mundo (anéis no chão) e luzes.
10. **Sem sistema de partículas no motor.** Detritos, brasas, fumaça e arcos são partículas próprias enviadas por
    pacote vanilla (`sendParticles`), sem orçamento conjunto com os efeitos.
11. **Sem som no motor.** Música (canal próprio `bossestesting:music_v1`), fade, laço e supressão da música vanilla
    ficam no addon.

## Animação

12. **Sem relógio de rig sincronizado.** O GeckoLib roda no cliente; o addon sincroniza por um id de clipe + contador
    e um `LEAD` fixo (transição de 2 ticks + 1 tick de rede). Quem entra no meio de um ataque vê o clipe desde o
    começo. Falta: tempo de início do clipe sincronizado e "seek" na animação.
13. **Eventos de animação fora do GeckoLib.** `walk`, `run` e `spawn_landing` não têm instruções internas; o addon
    os sincroniza explicitamente (servidor por tick; passos no cliente pela fase do laço).

## Céu

14. **Um evento global por fase.** A transição entre céus é a soltura de um e o início do outro (cruzamento pelo
    fade da timeline), sem interpolar parâmetros entre perfis.
