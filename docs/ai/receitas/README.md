# Receitas de efeitos

Cada receita é um efeito real do exemplo `examples/vfx-sample`, registrado em `Receitas.java` e testável no jogo com `/vfxsample receita <nome>` (permissão de comandos). O código dos arquivos `.md` é o mesmo dos arquivos do exemplo: `./gradlew checkRecipes` (parte do build) falha se divergirem, e `./gradlew syncRecipes` os copia de novo.

| # | Receita | Comando | Mostra |
|---|---|---|---|
| 1 | [Anel de impacto no chão](01-anel-de-impacto.md) | `anel` | profundidade da cena, `caDistanceWave`, marcador com cue, Impact |
| 2 | [Cubo de grade gigante](02-cubo-de-grade.md) | `cubo` | raio-caixa, grade em coordenadas do objeto, arestas, OVERSHOOT |
| 3 | [Bolha arco-íris](03-bolha-arco-iris.md) | `bolha` | raio-esfera, fresnel, `caRainbow`, centro animado no cliente |
| 4 | [Impact frame de duas cores](04-impact-frame.md) | `impacto` | passe de tela inteira acessível (`screenEffects`, `flashAllowed`) |
| 5 | [Feixe vertical](05-feixe-vertical.md) | `feixe` | `caSegmentGlow` com largura mínima por pixel e oclusão |
| 6 | [Efeito ambiente com sustain](06-ambiente-sustain.md) | `ambiente` + `release`/`stop` | loop sem emenda, release, malha de mundo |
| 7 | [Céu tingido](07-ceu-tingido.md) | `ceu` | Atmosphere API com trilhas, sem shader próprio |

Para começar um efeito novo do zero: `python3 scripts/novo_efeito.py <namespace> <nome>` (veja [AGENTS.md](../../../AGENTS.md)). Funções GLSL usadas aqui: `calamidade_fx.glsl`, documentadas em [glsl.md](../glsl.md). Assinaturas da API: [API-REFERENCIA.md](../API-REFERENCIA.md).
