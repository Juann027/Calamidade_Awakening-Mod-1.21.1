# History and planned evolution

## Published versions

### 0.1 — engine base

- Shared ability catalog, menu and transport.
- Client timeline, deduplication and selection by priority.
- Geometric primitives, volume, lens, orbital and cinematic scene.
- Five visual abilities and quality tuning.
- Distributed JAR and isolated geometry tests. See VALIDACAO.md for the version's coverage.

### 0.2.0 — Atlas and Horizon Core

- Opening and focusing the Atlas with selection while moving.
- Horizon Core with camera, positional audio and a wave across surfaces.
- Prismatic Pulse with a fast start and a reduced cooldown.

### 0.3.0 — external integration

- Public API with registration by namespace, position and entity anchors, cancellation and synchronization.
- Impact accent configurable in ticks, with a colored flash or desaturation and contrast.
- Separate consumer project and the API-0.3.md guide with a registration example and test commands.

### 0.4.0 — ability addons

- Cards published by the addon in the paged Atlas and in the Focus set.
- Configurable power with limits defined and applied by the server.
- Shader in the addon's namespace, per-instance mesh, HUD, panel and camera shots.
- Complete Tidal Signal example. See ADDONS-0.4.md.

### 0.5.0 — gameplay and diagnostics

- Server cues (`Effects.cue`, `Effects.onImpact`) for damage, knockback and other consequences on the same tick as the visual. See GAMEPLAY-0.5.md.
- Cooldown per player and per ability, instead of a single cooldown per player.
- Diagnostics panel (F7), frame time recording to CSV and the `/calamidade bench` command for reproducible load. See TESTE-REAL.md.
- Atlas: titles fitted to the card and a more discreet decorative orbit.
- The Tidal Signal now demonstrates gameplay on impact.

### 0.6.0 — advanced hooks for addons

- `AddonLayers.post`: a RimuLib frame per instance, with its own uniforms, several passes and post-processing per phase, within the quality budget.
- `AddonLayers.film`: own camera with RimuLib shots, controlled by the engine (skip, cancel, end).
- `AddonLayers.start`: start event on every client, late joiners included.
- Tidal Signal 1.3.0 demonstrates the three hooks. See AVANCADO-0.6.md.

### 0.6.1 — review fixes

- `AddonLayers.Post` no longer exposes the raw frame: each pass requested with `post.pass(...)` consumes the frame budget (1/2/4) and passes beyond it are refused. Card shaders and posts share the budget in the effects' priority order.
- Screen effects through methods that combine by maximum (`bloom`, `shake`, `desaturate`, `contrast`, `flash`); `flash` respects the hide-flashes option.
- The F7 panel counts the passes actually queued.
- A cue that ends its own instance prevents the following cues on the same tick.

### 0.6.2 — review of priority, range, duration and accessibility

- The `AddonLayers.post` callback is called even without a budget slot; only the passes are refused, and the screen adjustments still apply.
- Distribution range per definition (`withRange`, 16 to 1024 blocks; default 128).
- Duration up to 12000 ticks and radius up to 512 blocks for addon effects; automatic ramps limited in ticks above 1200.
- Centralized client configuration: intensity of screen effects and shake, applied to the engine and the addons, editable in the mod list.

### 0.7.0 — Timeline API

- `Timeline`: phases with smooth weights, tracks with keyframes and six easings, markers and sustain with release. Pure Java, validated and covered by checkBudget. See TIMELINE-0.7.md.
- `Effects.timeline`, cues on markers, `Handle.release()` and the release packet, also sent to late joiners. Server and client use the local age.
- Timeline sampler in `Time`, `World`, `Hud` and `Post`; `Post.uploadTracks`; `Film` receives the timeline to align cuts to the phases.
- `Post.aberration`, `zoomBlur`, `vignette`, `afterimage` and `grain`; client option "Motion effects".
- Conclusion about uniform arrays recorded in AVANCADO-0.6.md; end of the obsolete `EventBusSubscriber.Bus` attribute.
- vfx-sample 1.4.0: Tidal Signal scripted by the timeline and the Beacon, a looping ambient effect.

### 0.8.0 — Atmosphere API and AI kit

- `Atmosphere`: sky (gradient and blend), distance fog, exposure, ambient tint and stars, with parameters from Timeline tracks and weight from the envelope. Blending of the two with the highest weight. See ATMOSFERA-0.8.md.
- One `atmosphere` pass per frame, outside the addon budget; fog through the game's fog (NeoForge events) with an on-screen fallback; accessibility of exposure and tint; new line in F7.
- GLSL library `calamidade_fx.glsl` by `#include`, tested on a GPU.
- AI kit: AGENTS.md, project skill, API reference generated from the JAR, GLSL guide, 7 runnable recipes and `scripts/novo_efeito.py`.
- Build checks: `checkShaders`, `checkApiDocs`, `checkRecipes`. RimuLib downloaded from CurseForge with SHA-256, outside the repository and the releases.
- vfx-sample 1.5.0: recipes, golden sky on the Tidal Signal impact and fog in the Beacon.

### 0.9.0 — Beam API, Light API and protagonist priority

- `Effects.Priority.HERO` (`withPriority`): the main effect of the scene goes before NORMAL ones, is never cut by the drawn-effects limit (at most 2 HERO per frame, the nearest) and has its own pass budget (1/2/3). See PRIORIDADE-0.9.md.
- `Beam` and `Point`: beams between points (center, world, anchor, caster, eyes, aim) with core and halo, pulses, waves, noise and reveal, through Timeline tracks; one `beams` pass for all beams of the frame (16/48/128), with a minimum per-pixel width and occlusion. See FEIXES-0.9.md.
- `Light`: visual point light, segment light (or following a beam), disc or ring light on the ground, with flicker; one `lights` pass before the effects and the atmosphere (8/24/64), with normals from depth and the game's fog. It does not change the game's light level. See LUZ-0.9.md.
- `calamidade_fx.glsl` version 2 (11 new functions, 106 cases in WebGL2) and `scripts/previa_passes.cjs` (preview of the passes without the game).
- F7 and CSV with HERO, beams and lights.
- vfx-sample 1.6.0: pillar, laser, web, campfire, light wave and hero recipes; a brief light on the Tidal Signal impact.

### 0.10.0 — integration with other mods

- Per-instance orientation: `Placement` (position or anchor, yaw, pitch, `facing`, `arrivingFrom`, `shifted`, `followingBody`) and `Effects.start`/`follow` with orientation; center points, `Point.facing`, track offsets and the anchor offset turn with the instance; the yaw can follow the anchor's body. Packet `event_v2`. See INTEGRACAO-0.10.md.
- Sources (`EffectSource`: player, mob, block, script), per-source limits and slots reserved for players in the server config; cooldown per source and effect; F7 and `/calamidade status` by source kind.
- `EffectTriggers`: item triggers (use, attack, cooldown, durability), block entity triggers (PERIODIC, REDSTONE, PROXIMITY, end on break or unload) and entity triggers (body or target), with common API only; guide to linking GeckoLib keyframes.
- Consumption as a library without Maven: a `build.gradle` template (GitHub release with SHA-256) and `examples/consumidor`, checked by the build; checked with NeoForge 21.1.265.
- vfx-sample 1.7.0: oriented fan, staff, redstone and periodic pillars, zombie with a beam.

### 0.11.0 — Terrain API

- Excavation shapes (`TerrainShape`: sphere, crater with a profile, trench, cylinder, union and subtraction) with a rough edge from deterministic noise seeded by the instance, turned by the `Placement`. See TERRENO-0.11.md.
- Burnt palette by bands (`TerrainPalette`: floor, wall, rim) in a shell that seals fluids and burns plants; real fill at the center (`Fill`) and `.nbt` structures turned by 90°.
- Pure plan on a worker thread (without reading the world) and commit at the Timeline marker, writing straight into the chunk sections with a blocks-per-tick budget, without drops, neighbours or physics; resend per section at the end of each batch. Step 0 decision: RimuLib's `CarveApi` is not used (column-only cuts, shared time budget, no undo).
- Safety in the server config (on and off, volume, blocks per tick, dimensions, mobGriefing, BlockEntity only with an explicit option, tag `terrain_protected`), cancellable `TerrainEditEvent` and `/calamidade terrain undo|list` with a compact snapshot.
- Client: `terrain` pass with the heat (embers, cracks, smoke, heat shimmer, glowing strip in trenches) computed from the same analytic shape and seed, cooling lights, spherical stand-ins and cached structure meshes; new channel `terrain_v1`.
- vfx-sample 1.8.0: lightning that opens a trench, meteor that becomes real blocks, simple crater and a tower that falls and stays.

### 0.11.1 — Terrain API compatibility

- Land protection through NeoForge's explosion events (`Start` and `Detonate` with a synthetic explosion), world border, spawn protection and the player's game mode; config `terrainExplosionEvent` and `terrainSpawnProtection`.
- Outer shell: shape and neighbour updates and fluid ticks on the blocks just outside the edit (commit and undo), without cascade; block entity data resent to the client.
- Compatible with ScalableLux (fix), C2ME, Sodium, Embeddium and Distant Horizons; Moonrise does not start with RimuLib 1.3.0.
- In-game tests in the repository: `./gradlew testGame` (`testeJogo`; dedicated server, 51 checks then, with restart) and `xvfb-run ./gradlew testClient` (`testeCliente`; 10 checks). See TERRENO-0.11.md, section "Compatibilidade (0.11.1)".

### 0.11.2 — measured profiles, automatic quality and the Atlas in the world

- Cost measurement per effect and per pass in the game itself (`/calamidade_client debug measure`, test profile UNLIMITED, `bench <n> mix`), in software and on the author's RTX 4060; new profiles (LOW unchanged, BALANCED 8, HIGH 16, ULTRA 32 drawn) with caps in the client config.
- Automatic quality AUTO (default): frame p95, hysteresis, never during a HERO effect; F8 forces a profile, F7 shows the reason.
- Star Atlas rebuilt as panels standing in the world (engineering adapted from RespectCraft's holographic menu), astrolabe look, tabs for abilities, quality, accessibility, terrain and diagnostics. See ATLAS-0.11.2.md.
- New in-game tests: `testMeasure` (`testeMedicao`) and `testAtlas` (`testeAtlas`).

### 0.12.0 — Sky & World Events

- **Sky API:** sky layers by direction (black hole with gravitational lensing that bends the sky and distant terrain, portal to another galaxy, eclipse, rift, giant celestial body with phases and rings, aurora and distant lightning), procedural, with parameters by track; one engine `sky` pass, first of the frame, outside the budget, 2/4/6/8 layers per profile. The game's sun and moon are hidden by subtracting the same texture. See CEU-0.12.md.
- **Global events (`WorldEvents`):** scopes AREA, DIMENSION and SERVER, late join in the right phase (login, dimension change, respawn), optional persistence, a limit with priorities, `/calamidade world`.
- **Per-dimension sky (`SkyProfiles`):** named states with smooth transitions, synchronized and saved in the world; `ProfileSkyEffects` so the engine owns the whole sky of its own dimension.
- Example 1.9.0: recipes 22 to 26 (black hole, portal, giant moon, global eclipse, test dimension).

## Pending measurements

The tools from 0.5.0 exist to close these items. The procedure is in TESTE-REAL.md.

- Absolute cost per effect and per pass on the RTX 4060 with VSync off (the 0.11.2 measurement was capped at 144 Hz) and on integrated graphics (LOW profile); AUTO going up on a physical GPU.
- Synchronization between two clients on a dedicated server: observer, late join, leaving and returning to range, dimension change.
- The pipeline in Fast, Fancy, Fabulous and with rendering mods (Sodium/Embeddium, Iris/Oculus).
- Look of the beams and the light on a physical GPU (day, night, underwater) and cost of the `beams` and `lights` passes at the HIGH cap.
- Triggers and per-source limits with two players on a dedicated server (TESTE-REAL.md, part 5) and RespectCraft running the Finisher Engine with Calamidade effects.
- Terrain API with real protection mods (FTB Chunks, Open Parties and Claims, GriefDefender), with real players in spawn protection and adventure mode, Distant Horizons LOD on a server without DH and the cost of the rebuild on a physical GPU (TESTE-REAL.md, parts 6 and 7).
- **0.15.0 Light Engine:** formal benchmarks of the phase 5 budgets (LOW 32/24, BALANCED 80/60, HIGH 176/132, ULTRA 192/192) on physical GPUs, including integrated graphics. Manual testing on an RTX 4060 was satisfactory; no formal numbers were submitted.

## Next versions

### 0.13 — Bosses

Implemented on branch `claude/bosses-0.13` (based on 0.12.0, without 0.14); **published** (release v0.13.0, commit `2c90f60`). Chapter: [BOSSES-0.13.md](BOSSES-0.13.md).

- **Boss API** (`Boss`, `Bosses`): a controller attached to an addon entity; phases, transitions by time, health, signal, damage, death and predicate, sequences through the Timeline, enter, exit and sustain effects, periodic actions, a global event per phase, optional invulnerability, persistence on the entity, `maxPerBoss` and `bossMaxActive`, `/calamidade boss`.
- **Points attached to bones** (`RigAnchors`, `Point.socket`, `Point.socketAim`, `Effects.anchorSocket`, `AnchorCapture`): pose captured in the addon's renderer (vanilla, GeckoLib or other), providers, static anchors and fallback, without Calamidade depending on GeckoLib; visual on the client, static approximation on the server.
- **BossBar:** vanilla bar plus client styles (`BossBars`), a segmented bar with the phase name, damage ghost and flash; accessibility of size, contrast, labels and "force vanilla"; channel `boss_v1`.
- Example boss: the Eclipse Sentinel (recipe 27), which triggers the 0.12 eclipse in an area in the final phase.

### 0.14 — Custom Sky (release candidate v0.14.0, integrated on top of 0.13.0; not published)

Phases 0 to 4 implemented on branch `claude/ceu-personalizado` and integrated with the 0.13 Boss Engine in `claude/integracao-0.13-0.14-u8nraa`. **Included:** Backdrop (cubemap, panorama, gradient), procedural stars, constellations and nebulae, images, flipbooks and impostors, `customSkies`. **Deferred** (no version set, or 0.15 where noted): procedural dome, nebulae and backgrounds from animated PNGs, sky profiles in datapack JSON, layers drawn at night over the sun, own clouds, a sky recipe in the vfx-sample. **Not tested:** physical GPU, two real players, shaderpacks and alternative renderers.

An **additive-only** expansion of the Sky API: the seven procedural phenomena of 0.12 stay as they are, and nothing new appears unless an addon asks for it. Architecture and checks: [CEU-PERSONALIZADO.md](CEU-PERSONALIZADO.md).

**Delivered in 0.14.0** (candidate):

- **Backdrop:** 3 × 2 cubemap, equirectangular panorama and gradient, partial or complete replacement, the game's sun and moon redrawn on request (phase 2).
- **Procedural stars, constellations and nebulae:** seeded field, manual catalog with constellations, `SkyBlend` composition NORMAL, LUMINOUS and COVER, `SkyDarkening` (phase 3).
- **Images in the sky, flipbooks and impostors:** `SkyImage`, `SkyImpostor`, `layer(FRONT)`, `SkyTextures` cache with a per-profile budget, tool `gerar_impostor.cjs` (phase 4).
- Client config `customSkies`; integration with the 0.13 Boss Engine without a network change.

**Deferred (not in 0.14.0; no version set, under "After 0.14"):** procedural Backdrop dome; nebulae, galaxies and backgrounds from animated PNGs (beyond the `SkyImage` flipbooks); sky profiles in datapack JSON; layers drawn at night over the sun; own clouds; a sky recipe in the vfx-sample; limits set by measurement on a physical GPU.

**Approved proposal (history, before implementation):**

- **Backdrop:** sky background from a 6-face cubemap, an equirectangular panorama, a gradient or a procedural dome; partial replacement (with transparency) or complete (covering the game's sky and redrawing only what the addon asks: gradient, sun, moon, stars); in own dimensions, `ProfileSkyEffects` stays the owner of the whole sky.
- **Stars:** a procedural seeded field (density, sizes, colors, twinkle, galactic band) and a manual catalog with constellations, in few draw calls. **Done in phase 3** (with procedural nebulae and the hybrid per-object composition: `SkyBlend` NORMAL, LUMINOUS and COVER).
- **Nebulae and galaxies:** procedural and from PNG; animated backgrounds.
- **Images in the sky and impostors:** PNG with transparency, angular size, rotation, color, brightness, by Timeline track; sprite sheets, flipbooks, several angles. **Done in phase 4** (`SkyImage`, `SkyImpostor`, `layer(FRONT)`, `SkyTextures` cache with a per-profile budget, tool `gerar_impostor.cjs`; real 3D models stay in 0.15).
- Items in this list that came from "After 0.13": **sky profiles in datapack JSON** (codec, `/reload`, sending the whole profile), **layers also drawn at night over the sun** and **own clouds** — all three deferred, not delivered in 0.14.0.
- Limits and costs set by measurement (no limit of the proposal is final without a benchmark).

### 0.15.0 experimental — Light Engine (branch `claude/great-pascal-pq0xtj`, not published)

A focus request before Sky Objects: Light Cookies (own images as light patterns), spotlight, planar projector, attenuation curves, `ADD`/`SOFT`/`SUBTRACT` composition, color ramps and distance gradients in the same single pass; a fix of the instance life cycle (owner, orphan watchdog, `live_v1` reconciliation, `fx` commands). Chapter: [LUZ-0.15.md](LUZ-0.15.md). **Deferred:** cookies on point lights (cubemap) and on segments, real occlusion/shadows, materials. The numbering of the proposal below moves to the next version when the Light Engine is published.

- Phase 2: EMISSIVE and OVERRIDE, sharpness and high-resolution cookies.
- Phase 3: adaptive light budgets, light priorities, tiled lighting, adaptive cookie resolution.
- Phase 4: coverage first (independent light lane, engine lights pass, spatial spread, density admission, temporal hysteresis, AUTO relief), the coverage lab in vfx-sample 1.14.0.
- **Phase 5:** final budgets LOW 32/24, BALANCED 80/60, HIGH 176/132, ULTRA 192/192 (lights/cookies; requested defaults after manual RTX 4060 testing, not validated performance targets); the "Reduced Visual Quality" notice for LOW; English as the canonical language (commands, Gradle tasks, interface strings, AI instructions, entry-point docs), with Portuguese aliases kept; rebranding preparation ([REBRANDING.md](REBRANDING.md)); vfx-sample 1.14.1.

### Rebranding (name not chosen)

The project will be renamed. Phase 5 mapped presentation and technical identity and documented a safe presentation-only procedure and the migration a full technical rename would need ([REBRANDING.md](REBRANDING.md)). No identifier has been changed.

### 0.15 — Sky Objects & Cinematics (approved proposal)

- **Real 3D Blockbench models:** main format Bedrock `.geo.json` with a documented subset of animation, validated against real exports; Java alternatives (`ModelPart`), block OBJ/JSON and external renderers (no GeckoLib dependency).
- **Sky Space, World Space and Entity Space** chosen by the addon; optional LOD (billboard → impostor → model), mesh cache.
- **Sky ↔ world transitions and cinematic portals** coordinated by the existing Timeline; CPU or GPU clipping chosen by measurement; continuity guaranteed only near the reference point.
- **Optional integration with entities** (the addon handles AI, health, damage and spawning), without duplicating the 0.13 Boss API.
- Items in this list that came from "After 0.13" and from "API evolution": **lenses that bend addon beams and effects** and **static mesh cache and adaptive LOD**.

### After 0.14

Deferred from 0.14 (planned for it, not delivered in 0.14.0; version to be set):

- Sky profiles in datapack JSON (codec, `/reload`, sending the whole profile).
- Layers also drawn at night over the sun; own clouds.
- Procedural Backdrop dome; nebulae, galaxies and backgrounds from animated PNGs.
- A custom sky recipe in the vfx-sample; memory and upload limits measured on a physical GPU.

### After 0.13

- Sky profiles in datapack JSON → planned for 0.14, deferred (see "After 0.14").
- Sky API: layers also drawn at night over the sun and own clouds → planned for 0.14, deferred; ~~lenses that bend addon beams and effects~~ → 0.15.
- Trail API (trails and marks): ribbons with a position history for entities, projectiles and beam tips; marks that stay on the ground for a while.
- Cinematic titles and HUD synchronized to the Timeline.
- Beam branching (lightning that splits) and curved beams.
- Simple shadows for Light (depth occlusion toward the source).
- Terrain API: chained edits by track (a tear that grows over several ticks along with the lightning) and palettes by biome.

### API evolution, no version set

- Ability parameters through data files.
- Sequence editor and cost prediction.
- Synchronized audio track and poses tied to the Timeline phases.
- Per-instance data sent by the server to the client (for example, a target list, the strength of a blow) for the client hooks.
- Orientation following the look (head yaw and pitch) besides the body.
- Optional smooth transition (crossfade) on release, for loops whose edges do not match.
- ~~Static mesh cache and adaptive LOD to frame time~~ → 0.15 (Sky Objects).
