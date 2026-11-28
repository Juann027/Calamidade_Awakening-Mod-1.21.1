# Command reference (0.15.0, VFX Sample 1.14.1)

English is the canonical language of every command. All commands need permission level 2 (operator), except
`/calamidade_client`, which runs on the client.

Portuguese names from earlier versions still work as **deprecated aliases** (see [Legacy aliases](#legacy-aliases)), so
existing scripts and tests do not break. New documentation, examples and scripts should use only the English names.

## Engine: `/calamidade` (server)

| Command | What it does |
|---|---|
| `/calamidade status` | Live instances, sources and limits on the server. |
| `/calamidade bench <count> [<effect>]` | Starts `count` (1..64) instances of `effect`, or of the default effect, in front of the player (benchmark load). |
| `/calamidade bench <count> mix` | Same, with a mix of the vfx-sample recipes. |
| `/calamidade bench stop` | Stops the benchmark instances. |
| `/calamidade fx list` | Lists live effect instances (order, effect, scope, dimension, source, owner, anchor, state). |
| `/calamidade fx orphans` | Lists sustained instances whose owner controller is gone. |
| `/calamidade fx stop <n>` | Stops instance `n`. |
| `/calamidade fx release <n>` | Releases sustained instance `n` (it plays its exit). |
| `/calamidade fx stopall` | Stops every instance. |
| `/calamidade world list` | Lists global events (World Events). |
| `/calamidade world stop <id>` · `release <id>` | Stops or releases a global event. |
| `/calamidade boss list` | Lists boss controllers. |
| `/calamidade boss phase <entity> <phase>` | Forces a boss phase. |
| `/calamidade boss signal <entity> <signal>` | Sends a signal to a boss controller. |
| `/calamidade boss detach <entity>` | Removes the boss controller from an entity. |
| `/calamidade terrain list` | Lists terrain edits that can still be undone. |
| `/calamidade terrain undo [last \| <id>]` | Undoes the last terrain edit, or edit `id`. |

## Engine: `/calamidade_client` (client)

| Command | What it does |
|---|---|
| `/calamidade_client debug unlimited <effects> <passes>` · `debug off` | Internal test profile UNLIMITED (debug only). |
| `/calamidade_client debug measure effects \| passes \| all` | Cost measure sweep; writes a report. |
| `/calamidade_client debug lighttiles on \| off` | Tiled lighting on or off (comparison). |
| `/calamidade_client quality defaults low \| balanced \| high \| ultra \| all` | Restores the caps of a profile to the current defaults (other settings kept). |
| `/calamidade_client coverage on \| off` | Coverage first on or off (light lane, density admission). |
| `/calamidade_client record <seconds>` | Records frame times for a benchmark (1..300 s). |

Keys: **F7** diagnostics pages, **F8** cycles the quality mode (AUTO, LOW, BALANCED, HIGH, ULTRA).

## Example addon: `/vfxsample` (VFX Sample 1.14.1)

| Command | What it does |
|---|---|
| `/vfxsample at` · `follow` · `stop` | The Tidal Signal at a point, following the player, stop. |
| `/vfxsample beacon start \| release \| stop` | Looping beacon: start, release (fades out), stop. |
| `/vfxsample recipe <name>` | Starts a recipe effect (names below). |
| `/vfxsample recipe release` · `stop` | Releases or stops your last recipe. |
| `/vfxsample zombie on \| off` | Zombie beams (recipe 17). |
| `/vfxsample light <demo>` | Light lab demo (names below). |
| `/vfxsample light <demo> release \| stop` | Releases or stops that demo. |
| `/vfxsample light compare <a> <b>` | Two demos side by side, e.g. `light compare reference runes`. |
| `/vfxsample light stress [n]` | The stress demo, or `n` (1..16) instances of it. |
| `/vfxsample light load <n> same \| varied \| mixed [spread \| overlapping]` | Exactly `n` lights (16..192): one PNG, 16–32 PNGs or half with cookies; spread over a grid or all at one point. |
| `/vfxsample light adaptive` | 32 PNGs near and far: adaptive cookie resolution and atlas pressure. |
| `/vfxsample light list` · `stop` | Lists the demos; stops every demo. |
| `/vfxsample coverage arena` | 12 instances in a ring: 192 lights, 84 cookies. |
| `/vfxsample coverage combat` | 2 CRITICAL reticles among 64 decorative lights. |
| `/vfxsample coverage mixed` | Conventional, RGB (COLOR), EMISSIVE and OVERRIDE lights in one scene. |
| `/vfxsample coverage cinematic` | 8 instances with engine volume, beams and lights. |
| `/vfxsample coverage stress <n> grid \| ring \| clustered` | `n` lights (16..192) in instances of 16. |
| `/vfxsample coverage stop` | Stops the coverage scenes. |
| `/vfxsample event eclipse start \| release \| stop` | Server-wide persistent eclipse (World Events). |
| `/vfxsample sky <state>` | Changes the sky state of the test dimension (5 s transition). |
| `/vfxsample dimension` · `dimension back` | Goes to the test dimension; back to the Overworld. |
| `/vfxsample boss` | Spawns the Eclipse Sentinel (Boss API demo). |
| `/vfxsample boss crater on \| off` | The Sentinel's small crater at the start of Fury. |

Recipe names: `ring`, `cube`, `bubble`, `impact`, `beam`, `ambient`, `sky`, `pillar`, `laser`, `web`, `campfire`,
`light_wave`, `hero`, `fan`, `lightning_trench`, `meteor`, `simple_crater`, `falling_tower`, `black_hole`,
`galaxy_portal`, `giant_moon`.

Light demos: `reference`, `runes`, `technology`, `water`, `stained_glass`, `spotlight`, `seal`, `follow`, `combined`,
`stress`, `modes`, `strengths`, `filters`, `resolutions`, `neon`, `daylight`, `transition`, `surfaces`, `overlapping`,
`attack`, `runes_attenuation`, `runes_modes`, `runes_terrain`, `priority`.

Examples:

```
/vfxsample light runes
/vfxsample light reference
/vfxsample light resolutions
/vfxsample light compare reference runes
/vfxsample light load 128 same spread
/vfxsample coverage arena
/vfxsample coverage combat
/vfxsample coverage mixed
/vfxsample coverage cinematic
/vfxsample coverage stress 192 clustered
/vfxsample coverage stop
/vfxsample event eclipse start
/vfxsample boss
```

## Legacy aliases

Deprecated, kept for compatibility. Each alias is a Brigadier literal that shares the canonical node's command and
child nodes (`example.vfxsample.Cmd`), so there is one implementation, and the arguments parsed before an alias (such as
`load <n> mesmo`) stay in the same context. Aliases show up in tab completion because Brigadier cannot hide a usable
node.

| Legacy (Portuguese) | Canonical (English) |
|---|---|
| `/calamidade terrain undo último` · `ultimo` | `/calamidade terrain undo last` |
| `/vfxsample farol` | `/vfxsample beacon` |
| `/vfxsample receita <nome>` | `/vfxsample recipe <name>` |
| `/vfxsample zumbi` | `/vfxsample zombie` |
| `/vfxsample luz` | `/vfxsample light` |
| `luz <demo> soltar` · `parar` | `light <demo> release` · `stop` |
| `luz comparar` · `estresse` · `carga` · `adaptativa` · `lista` · `parar` | `light compare` · `stress` · `load` · `adaptive` · `list` · `stop` |
| `carga <n> mesmo` · `varios` · `mista` | `load <n> same` · `varied` · `mixed` |
| `... espalhado` · `sobreposto` | `... spread` · `overlapping` |
| `/vfxsample cobertura` | `/vfxsample coverage` |
| `cobertura combate` · `mista` · `cinema` · `estresse` · `parar` | `coverage combat` · `mixed` · `cinematic` · `stress` · `stop` |
| `estresse <n> grade` · `anel` · `aglomerado` | `stress <n> grid` · `ring` · `clustered` |
| `/vfxsample evento` | `/vfxsample event` |
| `/vfxsample ceu <estado>` | `/vfxsample sky <state>` |
| `/vfxsample dimensao [voltar]` | `/vfxsample dimension [back]` |
| `/vfxsample chefe [cratera on\|off]` | `/vfxsample boss [crater on\|off]` |

Recipe aliases: `anel`, `cubo`, `bolha`, `impacto`, `feixe`, `ambiente`, `ceu`, `pilar`, `laser`, `teia`, `fogueira`,
`onda_luz`, `protagonista`, `leque`, `raio_trincheira`, `meteoro`, `cratera_simples`, `torre_caindo`, `buraco_negro`,
`portal_galaxia`, `lua_gigante` (same order as the English names above).

Light demo aliases: `referencia`, `runas`, `tecnologia`, `agua`, `vitral`, `holofote`, `selo`, `seguir`, `combinada`,
`estresse`, `modos`, `forcas`, `filtros`, `resolucoes`, `neon`, `dia`, `transicao`, `superficies`, `sobrepostas`,
`ataque`, `runas_atenuacao`, `runas_modos`, `runas_relevo`, `prioridade`.

Effect IDs, resource paths, sky state names and timeline track names were **not** renamed: they are persistent or
technical identifiers (for example `vfxsample:luz_runas` and `vfxsample:receita_anel`), see
[REBRANDING.md](REBRANDING.md).

## Gradle tasks

English task names are canonical; the Portuguese names stay operational.

| English | Legacy | Notes |
|---|---|---|
| `testGame` | `testeJogo` | Dedicated server, Terrain API; two server runs. |
| `testClient` | `testeCliente` | Needs a screen (`xvfb-run -a`). |
| `testMeasure` | `testeMedicao` | Cost measure on the client. |
| `testAtlas` | `testeAtlas` | Atlas panel. |
| `testSky` | `testeCeu` | Sky API. |
| `testBackdrop` · `testStars` · `testImages` | `testeBackdrop` · `testeEstrelas` · `testeImagens` | Custom Sky. |
| `testBoss` | `testeChefe` | Boss API. |
| `testOrphans` | `testeOrfaos` | Orphan effects. |
| `testLight` | `testeLuz` | Light lab; run `testGame` first (it builds the world). |
| `checkSkyReference` | `checkCeuReferencia` | Sky visual reference (Node, Playwright). |
| `checkBackdropComposition` · `checkObjectComposition` | `checkFundoComposicao` · `checkObjetosComposicao` | Pixel probes. |

`checkBudget`, `checkFrustum`, `checkShaders`, `checkApiCompat`, `checkApiDocs`, `checkRecipes`, `checkModVersion`,
`apiDocs`, `syncRecipes` and `dist` already had English names.
