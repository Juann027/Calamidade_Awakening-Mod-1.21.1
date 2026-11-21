# Material para IAs e agentes

Comece por [AGENTS.md](../../AGENTS.md) na raiz (regras, comandos e "como criar um efeito novo em 6 passos"). Para o fluxo completo de criação de efeitos, a skill de projeto está em [.claude/skills/calamidade-efeitos/SKILL.md](../../.claude/skills/calamidade-efeitos/SKILL.md).

| Arquivo | Conteúdo |
|---|---|
| [API-REFERENCIA.md](API-REFERENCIA.md) | Toda a API pública (`api.v1`), gerada do JAR compilado com `javap` e do Javadoc; uma linha por membro. Não edite à mão: `./gradlew apiDocs`. |
| [glsl.md](glsl.md) | Como a RimuLib monta os shaders, o que o `common.glsl` oferece e a biblioteca `calamidade_fx.glsl`. |
| [receitas/](receitas/README.md) | 13 efeitos completos e compiláveis, cada um com objetivo, Java, shader (quando há), timeline e o que conferir no jogo (`/vfxsample receita <nome>`): shaders, malha, atmosfera, feixes, luzes e prioridade HERO. |

Ferramentas: `python3 scripts/novo_efeito.py` (esqueleto de efeito), `node scripts/testar_glsl.cjs` (testes da biblioteca GLSL numa GPU), `node scripts/previa_passes.cjs` (passes `beams` e `lights` numa cena sintética), `./gradlew build` (inclui `checkShaders`, `checkApiDocs` e `checkRecipes`).
