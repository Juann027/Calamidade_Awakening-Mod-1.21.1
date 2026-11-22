# Receitas de efeitos

Cada receita é um efeito real do exemplo `examples/vfx-sample`, registrado em `Receitas.java` e testável no jogo com `/vfxsample receita <nome>` (permissão de comandos); as receitas 15 a 17 (gatilhos de item, bloco e mob) são testadas com o item, os blocos e o mob do exemplo; as 18 a 21 (Terrain API) mexem no terreno e se desfazem com `/calamidade terrain undo`; as 22 a 26 (0.12.0) usam a Sky API, os eventos globais e o céu por dimensão. O código dos arquivos `.md` é o mesmo dos arquivos do exemplo: `./gradlew checkRecipes` (parte do build) falha se divergirem, e `./gradlew syncRecipes` os copia de novo.

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
| 18 | [Raio que abre uma trincheira](18-raio-trincheira.md) | `raio_trincheira` | Terrain API: `trench` virada pelo yaw, commit sob o impact frame, borda queimada, faixa incandescente (0.11.0) |
| 19 | [Meteoro que vira bloco real](19-meteoro.md) | `meteoro` | dublê → real: esfera em chamas que cai, cratera, esfera de blocos reais, revelação, calor |
| 20 | [Cratera simples](20-cratera-simples.md) | `cratera_simples` | escavação e borda queimada, para estudo (a mínima, sem `burn`, está no capítulo); desfazer e listar |
| 21 | [Torre que cai e fica](21-torre-caindo.md) | `torre_caindo` | estrutura `.nbt` girada em 90°, malha do dublê em cache, cratera sob ela, poeira |
| 22 | [Buraco negro no céu](22-buraco-negro.md) | `buraco_negro` | Sky API: `BLACK_HOLE` com lente, trilhas de tamanho, disco e lente, colapso com Impact, HERO (0.12.0) |
| 23 | [Portal para outra galáxia](23-portal-galaxia.md) | `portal_galaxia` | `PORTAL`: semente que pisca, abertura por trilha, galáxia girando |
| 24 | [Lua gigante](24-lua-gigante.md) | `lua_gigante` | `CELESTIAL_BODY` com anéis, trilhas `ELEVATION`/`AZIMUTH` e fase |
| 25 | [Eclipse global](25-eclipse-global.md) | `/vfxsample evento eclipse start\|release\|stop` | `WorldEvents` no escopo SERVER, persistente; `ECLIPSE` com escurecimento, raios ao longe, atmosfera |
| 26 | [Dimensão de teste](26-dimensao-teste.md) | `/vfxsample dimensao`, `/vfxsample ceu <estado>` | `SkyProfile` com três estados, `ProfileSkyEffects`, dimensão por datapack |

As receitas 18 a 21 são **casos de uso** da Terrain API e todas usam `burn` por intenção do efeito; não são a configuração padrão de uma destruição. Antes de copiar uma delas, veja [Escolher a operação](../../TERRENO-0.11.md#escolher-a-operação) (tabela de decisão, exemplos comparáveis e custos).

Para começar um efeito novo do zero: `python3 scripts/novo_efeito.py <namespace> <nome>` (veja [AGENTS.md](../../../AGENTS.md)). Funções GLSL usadas aqui: `calamidade_fx.glsl`, documentadas em [glsl.md](../glsl.md). Assinaturas da API: [API-REFERENCIA.md](../API-REFERENCIA.md).
