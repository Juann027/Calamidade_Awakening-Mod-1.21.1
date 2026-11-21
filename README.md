# Calamidade_Awakening

A modular visual effects engine for Minecraft 1.21.1, NeoForge 21.1.x and Java 21. Release 0.11.0 uses RimuLib 1.3.0. The five built-in abilities are cosmetic; addons add gameplay through server cues and, since 0.11.0, terrain edits (craters, trenches, falling structures) through the Terrain API.

[Manual geral do mod e da API (Português)](docs/MANUAL-GERAL.md)

## Installation

Build the JAR with `./gradlew dist` (see Development) and install `dist/calamidade_awakening-0.11.0.jar` together with RimuLib 1.3.0 for NeoForge in the mods folder. Download RimuLib from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/rimulib/files/9676532) (`rimulib-1.3.0-neoforge.jar`); it is not stored in this repository nor attached to the GitHub releases, which carry only the Calamidade and sample JARs. Use the same versions on the server and all clients (0.10.0 changed the start packet to `event_v2`; 0.11.0 adds the `terrain_v1` channel). Replace the previous JAR; the namespace, item and key bindings are unchanged since 0.1.2. To obtain a focus, use `/give @s calamidade_awakening:focus`. Reassign custom shortcuts in Controls.

English is the fallback for interface text. Brazilian Portuguese is available through Minecraft's language setting.

| Default key | Action |
|---|---|
| N | Open or close the Star Atlas |
| K | Prismatic Pulse |
| U | Crystal Bloom |
| I | Horizon Stained Glass |
| V | Aurora Convergence |
| Y | Horizon Core |
| O | Cancel cinematic |
| F7 | Diagnostics panel |
| F8 | Cycle visual quality |

If Aurora Convergence still uses L after updating, assign V in Controls. L opens the vanilla Advancements screen.

Open **Star Atlas** from the pause menu or press **N** while playing. Five cards appear four blocks ahead of the camera. Aim the crosshair at a card and press Attack to cast it through the server; Use Item cycles addon pages. The atlas closes after selection, on dimension change, or beyond 24 blocks.

**Horizon Core** takes ten seconds: four curved blades gather, narrow into a slit and release a violet-white wave across visible surfaces, with four positional sound cues and a held camera (press O to leave it). **Prismatic Pulse** is the fast alternative, with a 38-tick presentation and a 48-tick cooldown. Cooldowns are tracked per player and per ability.

Holding the Awakening Focus also gives access to the RimuLib skill menu (normally H).

## Using the mod as a library

The public entry point is `dev.calamidade_awakening.api.v1.Effects`. Register an effect in your own namespace, then start it at a world position or attach it to a server entity; the returned handle can end it early. Effects can schedule a tinted flash or monochrome impact accent. Since 0.5.0, `Effects.cue` and `Effects.onImpact` run server callbacks on the instance clock, so damage and knockback land on the same tick as the visual contact. Since 0.6.0, client hooks give addons budgeted post-processing passes with their own uniforms and screen effects, a custom camera built with FilmApi, and a start event on every client; see the [advanced hooks guide](docs/AVANCADO-0.6.md). Since 0.7.0, a `Timeline` scripts an effect once (named phases with smooth weights, keyframed value tracks, markers for server cues) and the server, shaders and camera all read it; ambient effects can loop in a sustain section until `Handle.release()`; see the [Timeline guide](docs/TIMELINE-0.7.md) (Portuguese). Since 0.8.0, an `Atmosphere` gives an effect its own sky gradient, distance fog (through the game's own fog), exposure, ambient tint and stars, with parameters driven by timeline tracks; the engine blends the two strongest atmospheres in one pass per frame, outside the addon budget; see the [Atmosphere guide](docs/ATMOSFERA-0.8.md) (Portuguese). Since 0.9.0, a `Beam` draws light beams between points (instance center, fixed world position, anchor entity or caster, at the feet, the eyes or the aimed block) with core and halo, running pulses, lateral waves, noise and a progressive reveal, and a `Light` adds visual light to the blocks and entities on screen (point, segment, along a beam, disc or ring on the ground, with flicker); the engine draws all beams of a frame in one pass and all lights in another, with timeline tracks for every parameter. The lights are visual only: the game's light level, mob spawning and plant growth do not change. `Definition.withPriority(Priority.HERO)` marks the protagonist of a scene: chosen first, never cut by the drawn-effect limit (at most 2 per frame, the nearest) and with its own pass budget. See the [Beam guide](docs/FEIXES-0.9.md), the [Light guide](docs/LUZ-0.9.md) and the [priority guide](docs/PRIORIDADE-0.9.md) (Portuguese). Since 0.10.0, an instance can start turned by a yaw and a pitch (or from a `Placement`: position or anchor, orientation, optionally following the anchor's body yaw), and center points, beams and lights turn with it; every instance has a source (player, entity, block or script) with server-configurable per-source limits, slots reserved for players and per-source cooldowns; and `EffectTriggers` starts effects from items, block entities (periodic, redstone, proximity) and mob attacks with common code only (no GeckoLib dependency). Other mods can depend on the engine without Maven: see the [integration guide](docs/INTEGRACAO-0.10.md) and the [dependency template](docs/ai/usar-como-dependencia.md) (Portuguese). Since 0.11.0, the Terrain API (`TerrainShape`, `TerrainPalette`, `TerrainEdit`, `Effects.terrain`) carves spheres, bowl craters, trenches and cylinders (combined, with a seeded rough edge, turned by the instance orientation), lines them with a weighted burnt palette, places real blocks or a `.nbt` structure in the middle and commits it all at a timeline marker, under the impact frame: blocks are written straight into the chunk sections on the server thread with a per-tick block budget, no drops, neighbour updates or physics, and each changed section is resent once. The plan is pure and computed on a worker thread; the client draws the cooling heat from the same analytic shape and seed, plus falling stand-ins (a burning sphere or a cached block mesh of the structure) that turn into the real blocks. A server config section, a protected block tag, a cancellable `TerrainEditEvent`, `mobGriefing` for mob sources and `/calamidade terrain undo` keep it safe; see the [Terrain guide](docs/TERRENO-0.11.md) (Portuguese). See the [API guide](docs/API-0.3.md), the [gameplay cue guide](docs/GAMEPLAY-0.5.md) (Portuguese) and the [standalone consumer](examples/vfx-sample/src/main/java/example/vfxsample/Sample.java).

## For AIs and agents

Start with [AGENTS.md](AGENTS.md): project map, non-negotiable rules (public API, network, licenses, javap, git), build and test commands and "how to create a new effect in 6 steps". [docs/ai/](docs/ai/README.md) holds the API reference generated from the compiled JAR, the GLSL guide with the `calamidade_fx.glsl` library seventeen complete recipes that are real effects of the sample (`/vfxsample receita <name>`; recipes 15 to 17 are an item, blocks and a vanilla mob) and the template for depending on the engine without Maven. `python3 scripts/novo_efeito.py <namespace> <name>` scaffolds an effect that compiles without edits. Claude Code also picks up the project skill in `.claude/skills/calamidade-efeitos/`.

## Publishing an addon

Register an effect, then publish its card through `Addons.publish`. The Star Atlas shows a page per addon, and the Awakening Focus includes the same skills with artwork and optional power controls. The sample Tidal Signal adds a camera aligned to its timeline phases, a shader fed by timeline tracks, world-space blades, a HUD, a Focus panel and a marker cue that damages and pushes nearby creatures. The sample Beacon (`/vfxsample farol start`, then `release`) shows a looping ambient effect with amber fog while it is lit, and the Tidal Signal briefly tints the sky gold on impact.

[Addon tutorial (Portuguese)](docs/ADDONS-0.4.md) · [Example implementation](examples/vfx-sample/src/main/java/example/vfxsample/Sample.java)

## Testing on real hardware

F7 shows the active GPU, frame time median and p95, and per-frame engine cost. `/calamidade_client record <seconds>` writes a CSV to `calamidade_bench/`. `/calamidade bench <count> [effect]` (permission level 2) spawns a reproducible load. The [test protocol](docs/TESTE-REAL.md) covers physical GPUs and two clients on a dedicated server.

## Development

The build downloads RimuLib from CurseForge into `libs/` and checks its SHA-256 (task `rimulib`, see `libs/README.md`); the JAR is never committed. With JDK 21:

- `./gradlew build` compiles and runs the budget, priority, cooldown, cue, timeline, atmosphere, beam, light and frustum checks, compiles every fx shader with glslangValidator when it is installed (`checkShaders`), and checks the generated API reference (`checkApiDocs`) and the recipe code (`checkRecipes`).
- `node scripts/testar_glsl.cjs` runs the `calamidade_fx.glsl` tests on a GPU (WebGL2 in headless Chromium), and `node scripts/previa_passes.cjs` renders the engine beams and lights passes on a synthetic scene into `build/previa/`.
- `./gradlew dist` copies the JAR to `dist/`.
- `./gradlew -p examples/vfx-sample build` builds the sample addon against that JAR.
- `./gradlew runClient` / `./gradlew runServer` start the development client and dedicated server.

On Windows use `gradlew.bat`. The configured NeoForge version is 21.1.267. The manual is generated from the chapters in `docs/` with `python3 scripts/gerar_manual.py`.
