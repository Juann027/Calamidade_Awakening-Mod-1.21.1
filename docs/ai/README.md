# Material for AIs and agents

Start with [AGENTS.md](../../AGENTS.md) at the root (rules, commands and "how to create a new effect in 7 steps"). For the complete effect creation workflow, the project skill is in [.claude/skills/calamidade-efeitos/SKILL.md](../../.claude/skills/calamidade-efeitos/SKILL.md). Commands are listed in [docs/COMMANDS.md](../COMMANDS.md) (English canonical names; the Portuguese names are deprecated aliases).

| File | Contents |
|---|---|
| [API-REFERENCIA.md](API-REFERENCIA.md) | The whole public API (`api.v1`), generated from the compiled JAR with `javap` and from the Javadoc; one line per member. Do not edit it by hand: `./gradlew apiDocs`. |
| [glsl.md](glsl.md) | How RimuLib assembles the shaders, what `common.glsl` offers and the `calamidade_fx.glsl` library (Portuguese chapter). |
| [receitas/](receitas/README.md) | 28 complete, compilable effects, each with its goal, Java, shader (when there is one), timeline and what to check in game (`/vfxsample recipe <name>`): shaders, mesh, atmosphere, beams, lights, HERO priority, since 0.10.0 orientation and item, block and mob triggers, since 0.11.0 the Terrain API (trench, meteor, crater and falling tower), since 0.12.0 the Sky API, global events and the per-dimension sky (black hole, portal, giant moon, global eclipse, test dimension), since 0.13.0 the demo boss and since 0.15.0 the light lab. |
| [usar-como-dependencia.md](usar-como-dependencia.md) | A `build.gradle` template for another mod to depend on Calamidade without Maven (JAR from the GitHub release with SHA-256, `compileOnly` + `runtimeOnly`), identical to the real project `examples/consumidor` (Portuguese chapter). |

Tools: `python3 scripts/novo_efeito.py` (effect skeleton), `node scripts/testar_glsl.cjs` (GLSL library tests on a GPU), `node scripts/previa_passes.cjs` (`beams` and `lights` passes on a synthetic scene; `ceu` for the `sky` and `atmosphere` passes), `./gradlew build` (includes `checkShaders`, `checkApiDocs` and `checkRecipes`).

Integration with other mods (orientation, sources, limits, triggers, GeckoLib and RespectCraft's Finisher Engine): [docs/INTEGRACAO-0.10.md](../INTEGRACAO-0.10.md).

Fantastic sky, global events and per-dimension sky (0.12.0): [docs/CEU-0.12.md](../CEU-0.12.md).

Terrain destruction: before creating craters, trenches or destructive attacks, read [Choosing the operation](../TERRENO-0.11.md#escolher-a-operação) and [Cost of a destruction](../TERRENO-0.11.md#custo-de-uma-destruição) (excavation, shell, fill, structure or visual only; dragons and bosses). Mandatory summary: rule 11 of [AGENTS.md](../../AGENTS.md).

Identity and the future rename: [docs/REBRANDING.md](../REBRANDING.md) lists which names are presentation (safe to change) and which are technical (must stay stable).
