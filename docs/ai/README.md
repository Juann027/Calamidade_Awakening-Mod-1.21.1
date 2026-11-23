# Material para IAs e agentes

Comece por [AGENTS.md](../../AGENTS.md) na raiz (regras, comandos e "como criar um efeito novo em 6 passos"). Para o fluxo completo de criação de efeitos, a skill de projeto está em [.claude/skills/calamidade-efeitos/SKILL.md](../../.claude/skills/calamidade-efeitos/SKILL.md).

| Arquivo | Conteúdo |
|---|---|
| [API-REFERENCIA.md](API-REFERENCIA.md) | Toda a API pública (`api.v1`), gerada do JAR compilado com `javap` e do Javadoc; uma linha por membro. Não edite à mão: `./gradlew apiDocs`. |
| [glsl.md](glsl.md) | Como a RimuLib monta os shaders, o que o `common.glsl` oferece e a biblioteca `calamidade_fx.glsl`. |
| [receitas/](receitas/README.md) | 26 efeitos completos e compiláveis, cada um com objetivo, Java, shader (quando há), timeline e o que conferir no jogo (`/vfxsample receita <nome>`): shaders, malha, atmosfera, feixes, luzes, prioridade HERO, desde a 0.10.0 orientação e gatilhos de item, bloco e mob, desde a 0.11.0 a Terrain API (trincheira, meteoro, cratera e torre que cai) e desde a 0.12.0 a Sky API, os eventos globais e o céu por dimensão (buraco negro, portal, lua gigante, eclipse global, dimensão de teste). |
| [usar-como-dependencia.md](usar-como-dependencia.md) | Modelo de `build.gradle` para outro mod depender do Calamidade sem Maven (JAR do release do GitHub com SHA-256, `compileOnly` + `runtimeOnly`), igual ao projeto real `examples/consumidor`. |

Ferramentas: `python3 scripts/novo_efeito.py` (esqueleto de efeito), `node scripts/testar_glsl.cjs` (testes da biblioteca GLSL numa GPU), `node scripts/previa_passes.cjs` (passes `beams` e `lights` numa cena sintética; `ceu` para os passes `sky` e `atmosphere`), `./gradlew build` (inclui `checkShaders`, `checkApiDocs` e `checkRecipes`).

Integração com outros mods (orientação, fontes, limites, gatilhos, GeckoLib e o Finisher Engine do RespectCraft): [docs/INTEGRACAO-0.10.md](../INTEGRACAO-0.10.md).

Céu fantástico, eventos globais e céu por dimensão (0.12.0): [docs/CEU-0.12.md](../CEU-0.12.md).

Destruição de terreno: antes de criar crateras, trincheiras ou ataques destrutivos, leia [Escolher a operação](../TERRENO-0.11.md#escolher-a-operação) e [Custo de uma destruição](../TERRENO-0.11.md#custo-de-uma-destruição) (escavação, casca, preenchimento, estrutura ou só visual; dragões e chefes). Resumo obrigatório: regra 11 do [AGENTS.md](../../AGENTS.md).
