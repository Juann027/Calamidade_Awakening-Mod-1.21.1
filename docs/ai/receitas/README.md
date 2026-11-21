# Receitas de efeitos

Cada receita é um efeito real do exemplo `examples/vfx-sample`, registrado em `Receitas.java` e testável no jogo com `/vfxsample receita <nome>` (permissão de comandos); as receitas 15 a 17 (gatilhos de item, bloco e mob) são testadas com o item, os blocos e o mob do exemplo. O código dos arquivos `.md` é o mesmo dos arquivos do exemplo: `./gradlew checkRecipes` (parte do build) falha se divergirem, e `./gradlew syncRecipes` os copia de novo.

| # | Receita | Comando | Mostra |
|---|---|---|---|
| 1 | [Anel de impacto no chão](01-anel-de-impacto.md) | `anel` | profundidade da cena, `caDistanceWave`, marcador com cue, Impact |
| 2 | [Cubo de grade gigante](02-cubo-de-grade.md) | `cubo` | raio-caixa, grade em coordenadas do objeto, arestas, OVERSHOOT |
| 3 | [Bolha arco-íris](03-bolha-arco-iris.md) | `bolha` | raio-esfera, fresnel, `caRainbow`, centro animado no cliente |
| 4 | [Impact frame de duas cores](04-impact-frame.md) | `impacto` | passe de tela inteira acessível (`screenEffects`, `flashAllowed`) |
| 5 | [Feixe vertical](05-feixe-vertical.md) | `feixe` | `caSegmentGlow` com largura mínima por pixel e oclusão |
| 6 | [Efeito ambiente com sustain](06-ambiente-sustain.md) | `ambiente` + `release`/`stop` | loop sem emenda, release, malha de mundo |
| 7 | [Céu tingido](07-ceu-tingido.md) | `ceu` | Atmosphere API com trilhas, sem shader próprio |
| 8 | [Pilar de luz](08-pilar-de-luz.md) | `pilar` | Beam com revelação, `Light.along` e disco no chão, sem shader (0.9.0) |
| 9 | [Laser preso ao jogador](09-laser.md) | `laser` | `Point.anchorEye`/`anchorAim`, `Effects.follow`, luz de impacto cintilante |
| 10 | [Teia de feixes](10-teia.md) | `teia` | 8 feixes com pulsos e ondulação, trilha `PULSE_COUNT` |
| 11 | [Fogueira](11-fogueira.md) | `fogueira` + `release` | só luz (sem geometria), cintilação, sustain |
| 12 | [Onda de luz](12-onda-de-luz.md) | `onda_luz` | `Light.ring` cujo raio acompanha a onda de choque da receita 1 |
| 13 | [Protagonista](13-protagonista.md) | `protagonista` | prioridade HERO com feixes, luzes e atmosfera; teste sob `/calamidade bench 60` |
| 14 | [Leque orientado](14-leque.md) | `leque` | orientação por instância: `Placement` com o yaw do jogador e `Point.facing` (0.10.0) |
| 15 | [Cajado](15-cajado.md) | `/give @s vfxsample:cajado` | gatilho de item: uso, ataque, recarga, durabilidade, cue com `ctx.forward()` |
| 16 | [Pilares](16-pilares.md) | `/give @s vfxsample:pilar_redstone` | gatilho de block entity: REDSTONE e PERIODIC, facing, fim ao quebrar ou descarregar |
| 17 | [Zumbi com feixe](17-zumbi.md) | `/summon zombie` | gatilho de entidade num mob vanilla (`LivingDamageEvent.Post`), limite por mob |

Para começar um efeito novo do zero: `python3 scripts/novo_efeito.py <namespace> <nome>` (veja [AGENTS.md](../../../AGENTS.md)). Funções GLSL usadas aqui: `calamidade_fx.glsl`, documentadas em [glsl.md](../glsl.md). Assinaturas da API: [API-REFERENCIA.md](../API-REFERENCIA.md).
