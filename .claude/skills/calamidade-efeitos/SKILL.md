---
name: calamidade-efeitos
description: Creates or changes visual effects of Calamidade_Awakening (a VFX engine for Minecraft 1.21.1, NeoForge, RimuLib) and its addons — definition, HERO priority, Timeline, Atmosphere (sky, fog, exposure), Beam, Light (light on the world, cookies, spotlights, projectors), Terrain API (craters, trenches, burnt edge, a meteor that becomes a block, a falling structure), Sky API, bosses, post-processing passes with GLSL shaders, world meshes, camera, server gameplay cues and tests with F7 and bench. Use it when asked for an effect, ability, attack, shader, beam, ring, bubble, impact frame, sky, fog, crater or terrain destruction for Calamidade, or for an addon that uses the dev.calamidade_awakening.api.v1 API.
---

# Creating effects in Calamidade_Awakening

First of all, read `AGENTS.md` (non-negotiable rules: public API, network, licenses, javap, git status, language). This skill is the workflow; the exact signatures are in `docs/ai/API-REFERENCIA.md` (generated from the JAR) and the complete examples in `docs/ai/receitas/`. Commands and Gradle tasks are English (`docs/COMMANDS.md`); the Portuguese names still work as deprecated aliases but are not used in new docs or scripts.

## When to use

- A new effect in an addon (or in `examples/vfx-sample`).
- Changing the script, visuals, camera or gameplay of an existing effect.
- Giving a fight or an area a mood (sky, fog, darkness) with the Atmosphere API.
- Triggering an effect from an item, a block (block entity) or a mob's attack, or turning it toward a direction (0.10.0).
- Using Calamidade from another mod (RespectCraft, for example): dependency without Maven and integration with animated rigs.

Do not use it to change the engine itself (internal rendering, network, registry): for that, follow `docs/ARQUITETURA.md` and `docs/HABILIDADES.md`.

## Workflow

Follow it in order. Each step points to the chapter and the recipe that show the code.

### 1. Definition

Start from the skeleton: `python3 scripts/novo_efeito.py <namespace> <name> --project <addon> [--card]` (compiles without edits, registers itself in `FMLConstructModEvent`, command `/<namespace> effect <name>`). Or copy the closest recipe.

- `Effects.Definition(ticks, radius, argb, Shape, Material, particle, sound[, Impact])`, `.withRange(blocks)`.
- Protagonist (chapter `docs/PRIORIDADE-0.9.md`, recipe 13): `.withPriority(Effects.Priority.HERO)` only on the main effect of the scene (boss attack, final blow). It goes before NORMAL effects, is never cut by the drawn-effects limit (at most 2 HERO per frame, the nearest) and has its own pass budget (1/2/3). Do not mark everything as HERO: the third, farthest HERO is simply not drawn.
- Radius: the frustum test uses 2.5 × radius around the center. Cover everything the shader draws (tall beam, large cube), otherwise the effect disappears when the center leaves the screen.
- Chapters: `docs/API-0.3.md`, `docs/ADDONS-0.4.md` (card). Recipes: all.

### 2. Timeline

- `Timeline.builder(duration).phase(name, start, end, rise, fall).track(name, keyframe(...)...).marker(name, tick).build()`; `Effects.timeline(id, timeline)` right after `register`.
- Phases for "when" (smooth weights 0..1), tracks for "how much" (values with easing), markers for gameplay and relative time (`t.since("impact")`).
- Ambient: `sustain(start, end)` + `Handle.release()`. Tracks equal at the loop edges and at the start of the exit; motion with a period equal to the loop.
- Chapter: `docs/TIMELINE-0.7.md`. Recipes: 1 (marker), 2 and 3 (tracks with OVERSHOOT), 6 (sustain).

### 3. Atmosphere (optional)

- `Atmosphere.builder().sky(top, horizon, blend).fog(color, density, start, end).exposure(x).ambient(color, strength).stars(x)`; `.track(Param.FOG_DENSITY, "track")`, `.weightPhase("phase")`; `Effects.atmosphere(id, atmosphere)` after the timeline.
- No shader or client layer needed. Weight = envelope × phase × WEIGHT track × fade at the end of the range; the engine blends the two strongest.
- Chapter: `docs/ATMOSFERA-0.8.md`. Recipe: 7. Example: Tidal Signal (sky on impact) and Beacon (fog in the sustain).

### 3b. Beams (optional, 0.9.0)

- `Beam.builder(Point.center(0, 0, 0), Point.center(0, 60, 0)).width(core, halo).color(core, halo).pulses(n, speed, size).wave(amplitude, wavelength, speed).noise(x).reveal(0..1)`; `.track(Beam.Param.REVEAL, "track")`, `.weightPhase("phase")`; `Effects.beams(id, beams...)` after the timeline (up to 16).
- Ends (`Point`): `center(dx,dy,dz)` (instance center), `world(x,y,z)`, `anchor`/`anchorEye`/`anchorAim(range)` (the `Effects.follow` entity), `caster`/`casterEye`/`casterAim(range)` (whoever cast it). Without the entity loaded on the client, the beam is not drawn.
- No shader or client layer: the engine draws every beam in one `beams` pass, with a minimum width of one pixel and terrain occlusion. The definition radius covers the beam (frustum test).
- Chapter: `docs/FEIXES-0.9.md`. Recipes: 8 (pillar), 9 (laser), 10 (web), 13 (hero).

### 3c. Light on the world (optional, 0.9.0)

- `Light.point(Point.center(0, .6, 0)).color(argb).intensity(x).radius(blocks).falloff(1..2).flicker(amount, speed)`; `Light.segment(a, b)`; `Light.along(beam)` (follows a beam of the same effect: ends, reveal, weight and halo color); `Light.disc(center, radius)` and `Light.ring(center, radius, width).height(blocks)` for the ground (shockwaves). `.track(Light.Param.RADIUS, "wave")`, `.weightPhase("phase")`. `Effects.lights(id, lights...)` after `Effects.beams` (up to 32 since 0.15 phase 3; 8 before).
- **Visual** light: it brightens what the camera sees, with normals from depth (only faces turned toward the source), no sky, fading with the game's fog. It does not change the light level, mob spawning or plants; it casts no shadows.
- Night calibration: intensity 2 to 3, range 8 to 12, falloff 1.2 to 2. Flicker disappears with screen effect intensity 0 and drops to 1/4 with "hide flashes".
- Chapter: `docs/LUZ-0.9.md`. Recipes: 8 (pillar), 11 (campfire), 12 (light wave).
- **Light Engine (0.15.0, experimental):** `Light.spot(p).direction(yaw, pitch)|toward(point).angle(degrees)` (spotlight; pitch 90 = down, the default), `Light.projector(p, width, height)` (parallel rays, `radius` = depth), `.cookie(Light.Cookie.of("ns:textures/lights/x.png"))` on a spotlight, projector or disc. Cookie: straight alpha, premultiplied; `MASK` (default) = luminance of `rgb·a` × light color; `.colored()` = `rgb·a` per channel (white light for exact colors); outside the image `CLAMP` = no light, `.repeat()` tiles; `.scale/.rotation/.offset`; `.sheet(c, r).fps(f).blendFrames(true)` or the `FRAME` track. Top of the image = front of the instance when the light points down. New tracks: `YAW`, `PITCH`, `ROLL`, `ANGLE`, `SOFTNESS`, `SIZE_X/Y`, `COOKIE_SCALE/ROTATION/U/V`, `FRAME`, `COLOR` (with `.colorRamp(...)`). `.distanceGradient(...)`, `.attenuation(SMOOTH|INVERSE_SQUARE|CONSTANT)`, `.blend(SOFT|SUBTRACT)`. Same single pass; a ~21 MB atlas only while a cookie is in use. Use Light for light **on surfaces**; something **in the air** is a particle, mesh or shader. **Phase 2:** `.blend(EMISSIVE)` (light that does not depend on the surface color: neon, energy, visible in daylight; `blendStrength` 0 = ADD → 1) and `.blend(OVERRIDE)` (replaces the color inside the mask; `blendStrength` 0..1; use `Attenuation.CONSTANT` for full control), `.surfaceDetail(0..1)`, `.contrast(0.1..8)` (sharp mask: 3–6), all with tracks (`BLEND_STRENGTH`, `DETAIL`, `CONTRAST`); cookie `.sharpness(0..1)`/`.filter(SMOOTH|SHARP|PIXELATED)`/`.lodBias(-1..4)` and `.resolution(HIGH|ULTRA)` (1024/2048 in an on-demand atlas; LOW 512, BALANCED 1024, HIGH 2048; fallback to 512). A readable reticle = OVERRIDE (shape) + EMISSIVE (glow) in the same effect. **Phase 3 (adaptive budget) and phase 5 (final budgets):** up to 32/80/176/192 lights and 24/60/132/192 cookies per frame (LOW/BALANCED/HIGH/ULTRA, configured separately; cookies never above the lights; safety maximum 192); `.priority(Light.Priority.DECORATIVE|NORMAL|IMPORTANT|CRITICAL)` (opt-in; NORMAL of a HERO effect counts as IMPORTANT): over the cap the most important stay, then those with the largest on-screen contribution, with hysteresis; cookies go to CRITICAL first. Use CRITICAL only for combat information (reticle, danger area) and **never as the only warning**: pair it with sound, particles or a mesh. One PNG per cookie, always: the engine reduces the effective resolution by itself (512 → 256 → 128) under atlas pressure, without fallback and without blinking. A projector disappears below the ground? With POWER the attenuation is `z / depth`: give it a depth larger than the height to the lowest point, or use `CONSTANT`. Test with `/vfxsample light load <n> <same|varied|mixed> [spread|overlapping]` and F7 (lines `limits`, `resolution`, `per pixel`). Chapter: `docs/LUZ-0.15.md`. Recipe: 28 (`/vfxsample light list`; technical examples, not styles).

### 3d. Orientation, sources and triggers (optional, 0.10.0)

- **Turning the effect:** `Effects.start(id, level, position, yaw, pitch)` or `Effects.start(id, level, Placement...)`. `Placement.at(pos).withLookOf(player)`, `.facing(target)`, `.arrivingFrom(attacker.getEyePosition())` (direction of the blow), `.shifted(right, up, forward)`, `Placement.on(mob).followingBody()` (yaw follows the body). Write points with `Point.facing(right, up, forward)`: they turn with the instance. Without orientation, everything stays as in 0.9 (forward = south).
- **Frame:** Minecraft's local axes (x left, y up, z forward); yaw 0 = south, positive pitch looks down. What turns: center points, track offsets, the anchor offset, `ctx.facing`/`ctx.forward()` in cues, `post.facing`/`world.facing` in layers. What does not: `Point.world`, offsets of entity points, light discs and rings (only the center), built-in `Shape`/`Material`.
- **Triggers (`EffectTriggers`, `static final`):** `item(id).offset(.3, -.25, .4).cooldown(30).durability(1)` with `use(...)` in `Item#use` and `attack(...)` in `hurtEnemy` (points EYES, CASTER, AIM, TARGET); `blockEntity(id).redstone().period(20)` / `.periodic(60)` / `.proximity(6)` with one `Component` per block entity and `tick()` in the server ticker (the instance ends when the block breaks or the chunk unloads); `entity(id).offset(0, 1.3, .4).cooldown(20)` with `fire(mob)` or `fire(mob, target)`.
- **Sources and limits:** every instance has an `EffectSource` (PLAYER, ENTITY, BLOCK, SCRIPT). Server default: 16 per player, 4 per mob, 2 per block, 16 of the 64 slots only for players. Over the limit, `Optional.empty()` without an error: handle the empty case. Cooldown per source and effect: `Placement.withCooldown`, `Effects.cooldownReady/cooldownLeft/startCooldown`.
- **GeckoLib/rigs:** the animation runs on the client, the effect starts on the server on the same tick as `triggerAnim`; align Timeline markers to the keyframes (seconds × 20). Calamidade does not depend on GeckoLib.
- Chapter: `docs/INTEGRACAO-0.10.md`. Recipes: 14 (oriented fan), 15 (staff), 16 (pillars), 17 (zombie). Dependency without Maven: `docs/ai/usar-como-dependencia.md`.

### 3e. Terrain (optional, 0.11.0; compatibility in 0.11.1)

- **Choose the operation before writing (rule 11 of AGENTS.md).** Destruction does not mean excavate + transform + fill. Start with `carve` alone and only add `burn`, `fill` or `structure` if the request says that result must **stay** in the world: ground that stays burnt, vitrified or frozen → `burn`; a real object (meteor core, crystal, rock) → `fill`; a construction that stays → `structure`. "Looks burnt right after" is already the client heat, which also appears without `burn`. A scene without a mark in the world → no `Effects.terrain` (impact frame, beams, light, atmosphere, sky). Do not copy the meteor or recipes 18 to 21 as a template: they all use `burn` because of the effect's intent. Repeated attacks (dragons, bosses): every blow visual, only some with a small crater, with a per-source cooldown; never replace blocks on every pulse. Decision table, comparable examples and costs: `docs/TERRENO-0.11.md`, sections "Escolher a operação" (choosing the operation) and "Custo de uma destruição" (cost of a destruction).
- `Effects.terrain(id, TerrainEdit.carve(shape).commitAt("marker").build())` after `register` and `timeline` (up to 4 per effect). Shapes (`TerrainShape`, instance frame): `sphere(r)`, `crater(r, depth).profile(2).clearance(h)`, `trench(length, r)` (along the forward axis: turns with the yaw), `cylinder(r, height)`, `union`, `minus`, `offset(right, up, forward)` and, on the final shape, `rough(amplitude, cell)`.
- Optional, independent pieces, each replacing real blocks: transformed shell `.burn(TerrainPalette.burnt(), 1..4)` (or a `TerrainPalette.builder()` with FLOOR, WALL, RIM bands and weights; start with thickness 1); real blocks `.fill(TerrainEdit.Fill.solid("minecraft:raw_gold_block", r).at(0, -3, 0))` or `Fill.shell` (hollow), written over everything, air included; structure `.structure("ns:path")` (`data/ns/structure/path.nbt`, turned by 90° steps from the yaw) and `.structureOffset(...)`.
- Cost: `terrainMaxVolume` counts planned positions (air included); `terrainBlocksPerTick` is a budget for the whole server, shared in arrival order; `.blocksPerTick(n)` spreads the edit over time but does not reduce the total. Large edits: a fixed position (instances attached to an entity plan on the server thread) and time between the start and the marker.
- Falling stand-in: `.standIn(TerrainEdit.StandIn.sphere(r).from(right, up, forward).fall(tick).reveal(ticks).build())` or `StandIn.structure()`; the engine draws the sphere, trail, mesh, dust and light. Heat: `.cooling(ticks)` or `.heatTrack("track")`, `.glow`, `.smoke`, `.cracks`, `.noHeat()`.
- **Under the impact frame:** put the marker on the tick of the definition's `Effects.Impact`, with 4 to 8 ticks. The definition radius covers the fall and the crater (frustum 2.5 × radius).
- Server: rules of the `terrain` config (volume 150 000, 16 384 blocks per tick, dimensions, `mobGriefing`, block entities only with `.replacingBlockEntities()` and permission), tag `calamidade_awakening:terrain_protected`, cancellable `TerrainEditEvent`, `/calamidade terrain undo|list`. Do not replace blocks in bulk in a cue with `setBlock`: use the API.
- Protection (0.11.1): after the `TerrainEditEvent`, the world border, spawn protection, the game mode of a player source (adventure and spectator do not edit) and NeoForge's explosion events (a cancelled `ExplosionEvent.Start` refuses; positions removed from `Detonate` stay intact), with a synthetic explosion that never runs. Claim mods that protect against explosions work without code. For the addon: pass the right source (`Placement.withCaster`/`withSource`), because that is what protection and the game mode check. Config `terrainExplosionEvent` and `terrainSpawnProtection`.
- World (0.11.1): light, heightmaps, POI, counters and saving as with `setBlock`; the outer shell gets shape and neighbour updates and a fluid tick at the end (no cascade); block entity data reaches the client. FULL chunks only; nothing is loaded.
- Chapter: `docs/TERRENO-0.11.md`. Recipes: 18 (lightning and trench), 19 (meteor), 20 (simple crater), 21 (falling tower).

### 3f. Sky, global events and per-dimension sky (optional, 0.12.0)

- **Sky layers:** `Effects.sky(id, Sky.blackHole()|portal()|eclipse()|rift()|body()|aurora()|bolts()...build())` after `register` and `timeline` (up to 6). Anchor: `direction(azimuth, elevation)` (0 = south, 90 = west), `toward(x, y, z)`, `centerOffset(x, y, z)` (instance frame: with `Placement` and the player's yaw, it appears in front of them) or `onSun()`/`onMoon()`. Tracks: `SIZE`, `PROGRESS` (opening, coverage, phase, activity), `LENS`, `DARKEN`, `AZIMUTH`/`ELEVATION` (degrees added), `ROTATION`, `SPIN`, `TILT`, `INTENSITY`, `WEIGHT`.
- The engine paints everything in the `sky` pass (first of the frame, outside the budget, 2/4/6/8 layers per profile) and hides the game's sun and moon under layers attached to them. `darken` darkens the world with the atmosphere rules (intensity, "hide flashes"). No shader of your own.
- **Global event:** `WorldEvents.start(id, Scope.SERVER|DIMENSION|AREA, level, Options.defaults().persistent(true).priority(80))` → `Handle` (`release`, `stop`); `WorldEvents.get(server, id)`. The effect uses a Timeline with sustain, Sky and Atmosphere; cues run at the origin; late joiners land in the right phase; up to `worldEventsMax` (4) events, one per effect. `/calamidade world list|stop|release`.
- **Dimension sky:** `SkyProfiles.define(dim, SkyProfile.builder().state("calm", State...).initial("calm").build())` in the constructor; `SkyProfiles.setState(server, dim, "chaos", 100)` on the server. Profile layers and atmosphere are constant (no tracks). To replace the whole sky of your dimension: `ProfileSkyEffects` in `RegisterDimensionSpecialEffectsEvent` and `vanillaSky(false)` in the state. State names are persistent identifiers (saved in the world): choose them once.
- Real lightning with damage stays in the cues; `Sky.bolts()` is visual only.
- **Sky background (Backdrop, 0.14):** `Effects.backdrop(id, Backdrop.cubemap("ns:textures/sky/x.png")|panorama(...)|gradient(top, horizon, nadir)...build())` after `register` and `timeline` (one per effect), or `SkyProfile.StateBuilder.backdrop(...)` for the dimension sky. Cubemap: a 3 × 2 PNG (row 0: north, east, south; row 1: west, up, down). Panorama: equirectangular with south in the middle. Alpha is coverage. `.opacity`, `.rotation`, `.spin` (stops with "motion effects" off), `.tint`, `.vanillaSunMoon(true)` (the game's sun and moon on top), `.matchFog(false)` (turns off the fog matched to the horizon). Drawn in `AFTER_SKY` without a post pass; Atmosphere, eclipse and lens compose on top. It does not show with blindness, darkness, lava, powder snow or biome fog, or with `customSkies` off. Use the seven procedural phenomena (`Sky`) for objects; the background is the sky behind them.
- **Stars, constellations and nebulae (0.14 phase 3):** `Effects.stars(id, StarField.random(seed, n)...build() | StarField.catalog().star(...).constellation(...)...build())` and `Effects.nebulae(id, Nebula.cloud(az, el, radius)|band(...)...build())`; profiles with `StateBuilder.stars/nebulae`. Per-object composition: `SkyBlend.NORMAL` (real alpha), `LUMINOUS` (adds light only), `COVER` (silhouette); reactions `atmosphere`, `daylight`, `darkening(SkyDarkening, strength)`, `horizon`; `order` (nebulae 100, stars 200).
- **Images, flipbooks and impostors (0.14 phase 4):** `Effects.images(id, SkyImage.of("ns:textures/sky/x.png").direction(az, el).size(degrees)...build())` (up to 8) for any PNG (ship, dragon, symbol, structure); `SkyImage.sheet(png, columns, rows).frames(n).fps(f).play(LOOP|ONCE|PING_PONG|TRACK).clock(INSTANCE|WORLD)` for a flipbook (no margin between frames; a sheet that flashes: `.flashing(true)`, the addon's responsibility); `Effects.impostors(id, SkyImpostor.of("ns:name").orientation(yaw, pitch, roll).heading(FIXED|PATH)...build())` (up to 4) with the descriptor `assets/ns/calamidade/impostors/name.json` built by `node scripts/gerar_impostor.cjs montar <views-folder> --id ns:name --saida <resources>` from already pre-rendered images. Same modes and reactions as the stars, default `order` 300, `layer(FRONT)` to compose in front of the procedural layers (visual composition, not depth). Tracks: `AZIMUTH`, `ELEVATION` (added), `SIZE`, `ROLL`/`YAW`/`PITCH`, `OPACITY`, `BRIGHTNESS`, `FRAME`. Objects at infinity: no parallax; real Blockbench models are future work — do not invent an API for them.
- Chapter: `docs/CEU-0.12.md`. Recipes: 22 (black hole), 23 (portal), 24 (giant moon), 25 (global eclipse), 26 (test dimension). Preview without the game: `node scripts/previa_passes.cjs ceu`.

### 3g. Bosses (optional, 0.13.0)

- **A controller, not an entity:** `Bosses.define(Boss.builder(id).bar(Boss.Bar.of(name).segments(true)).phase(...).transition(...).build())` in the common constructor; `Bosses.attach(mob, id)` on the server when the addon decides. The engine never creates bosses, bars or events on its own.
- **Phases:** `enter`, `exit`, `sustain` (released on exit), `every(n, ctx -> ctx.start(EFFECT))`, `worldEvent(effect, Scope.AREA)`, `invulnerable()` (opt-in), `label`, `color`, `onEnter`/`onExit`/`onHurt`. **Transitions:** `afterTicks`, `healthBelow`, `healthAbove`, `signal` (`handle.signal("x")`), `hurt(n)`, `death()`, `when(n, predicate)`; `transition(from, to, trigger, SEQUENCE)` locks until the sequence's Timeline ends. One transition per tick; `minDwell` 20 by default.
- **Anchors:** `RigAnchors.define(type, "mouth", Spec.head(...))`; beams and lights with `Point.socket("mouth", ...)` and `Point.socketAim("mouth", range)`; the whole instance with `Effects.anchorSocket(id, "mouth")`. Real bone pose on the client with `AnchorCapture.capture(entity, "mouth", poseStack.last().pose())` in the addon's renderer (GeckoLib optional, in the addon). **Server damage only with `RigAnchors.approximate`.**
- **Limits:** phase effects use `EffectSource.boss` and `maxPerBoss` (8), in the same count as the entity; `bossMaxActive` 16. Attacks do not need to touch terrain; if they do, a small and rare `carve`.
- **Owner (0.15):** every **sustained** instance with an `EffectSource.boss` source belongs to the controller and is released (with its exit) when the controller ends, even without a handle; ephemeral ones end by themselves. To outlive the boss on purpose, use another source (`withSource(EffectSource.script())`) or `WorldEvents` SERVER/DIMENSION. Diagnostics: `/calamidade fx list|orphans|release <n>|stop <n>`; `Scope.AREA` shows up in `/calamidade world list`. Chapter: `docs/LUZ-0.15.md`, section "Ciclo de vida" (life cycle).
- Chapter: `docs/BOSSES-0.13.md`. Recipe: 27 (Eclipse Sentinel, `/vfxsample boss`). Test: `xvfb-run ./gradlew testBoss`.

### 4. Post-processing and shaders

- Client (`FMLClientSetupEvent`): `AddonLayers.post(id, post -> { var t = post.timeline(); post.pass("ns:program", u -> {...}); post.bloom(...); })`.
- Shader in `assets/<ns>/shaders/fx/<program>.fsh`. RimuLib prepends `#version 150` and `common.glsl`. Read `DiffuseSampler`, add the effect, write `fragColor`. Center and positions are relative to the camera (`post.center()`, `scenePosAt`).
- Library: `#include "calamidade_fx.glsl"` (copy the file into your namespace; version 3 since 0.12.0): `caRayBox`, `caRaySphere`, `caSegmentGlow`, `caWorldGrid`, `caRainbow`, `caEnvelope`, `caDistanceWave`, `caStars`, and the sky ones `caSkyDir`, `caSkyBasis`, `caSkyLocal`, `caLensSource`, `caNoise2`, `caFbm2`, `caVanillaSky`. Documentation: `docs/ai/glsl.md`.
- Tracks as uniforms: `post.uploadTracks(u, "Track_")`.
- Chapter: `docs/AVANCADO-0.6.md`. Recipes: 1 (depth), 2 (ray-box), 3 (ray-sphere), 4 (full screen), 5 (segment).
- A world mesh instead of a shader: `AddonLayers.world` (`triangle`, `ribbon`; check `remainingVertices()`). Recipe: 6.

### 5. Camera

- `AddonLayers.film(id, film -> List.of(FilmApi.cut(...), FilmApi.sweep(...)))`, with cuts at `film.timeline().start("phase")`. It only plays for the caster (`Effects.start(id, player, position)` or a card).
- Chapter: `docs/AVANCADO-0.6.md`. Example: `SampleClient` (Tidal Signal).

### 6. Cues (server gameplay)

- `Effects.cue(id, "marker", ctx -> ...)`: damage (`ctx.living(radius)`), knockback, sound, cost. It runs on the same tick as the visual, once per instance (every lap inside a loop).
- Validate permissions and PvP in the cue. The engine is cosmetic.
- Chapter: `docs/GAMEPLAY-0.5.md`. Recipes: 1, 3, 4, 6.

### 7. Test

1. `./gradlew build` (includes `checkShaders`, `checkApiDocs`, `checkRecipes`), `./gradlew dist`, the addon build. Beams and lights: `node scripts/previa_passes.cjs` shows the real passes on a synthetic scene before the game.
2. In game: the effect's command; **F7** (post passes, vertices, sources, HERO protagonists, beams, lights, terrain hot zones · stand-ins · pass, stand-in meshes, atmosphere: N active · pass yes/no, since 0.12.0 sky: N layers · pass yes/no, global events: N and dimension sky: state); **F8** on LOW, BALANCED, HIGH and ULTRA (since 0.11.2 the default is AUTO; force a profile to compare; LOW shows the "Reduced Visual Quality" notice; the caps of each profile are in PRIORIDADE-0.9.md, LUZ-0.15.md and the Atlas Quality tab, key N); screen effect intensity at 0; "hide flashes" on; "motion effects" off. Oriented effects: test facing several directions; triggers: `/calamidade status` (live by source and refusals).
3. Load: `/calamidade bench <n> <effect>` and `/calamidade_client record 30` (CSV in `calamidade_bench/`). Light coverage: `/vfxsample coverage arena|combat|mixed|cinematic|stress <n> <grid|ring|clustered>` and F7 page 2.
4. Touched the engine's Terrain API (`terrain/`)? Run the in-game tests: `./gradlew testGame` (dedicated server without a screen, flat world, with restart) and `xvfb-run -a ./gradlew testClient` (client, light and image). New scenario: `src/testeJogo/java/.../teste/Roteiro.java` (world access only inside `no(...)`, on the server thread) and `RoteiroCliente.java`. `-PtesteMods=<folder>` installs other mods (Sodium, ScalableLux, C2ME...).
5. Manual procedure: `docs/TESTE-REAL.md`. Record what you measured in `docs/VALIDACAO.md`.

## Budget and limits

Since 0.12.0: sky layers 2/4/6/8 per frame (LOW/BALANCED/HIGH/ULTRA) in their own pass outside the budget; global events up to 4 (SERVER config). The table below has the 0.11.1 numbers (the measured 0.11.2 profiles are in `docs/PRIORIDADE-0.9.md`; drawn effects are 3/8/16/32 since then).

| Resource | LOW | BALANCED | HIGH |
|---|---|---|---|
| Effects drawn per frame | 3 | 6 | 12 |
| Addon passes (cards + `post.pass`) | 1 | 2 | 4 |
| HERO effect passes (own budget) | 1 | 2 | 3 |
| HERO effects drawn (the nearest) | 2 | 2 | 2 |
| Mesh vertices | 12 000 | 48 000 | 96 000 |
| Engine raymarch steps | 8 | 14 | 22 |

- `post.pass` returns `false` without a slot; the callback runs anyway, so bloom, shake and flash still apply. A card shader already uses a slot.
- Atmosphere pass: 1 per frame, the engine's, outside the budget.
- Beams pass: 1 per frame for all beams (16/48/128), charged to the HERO budget if a HERO effect has beams, otherwise to the common one.
- Lights pass: 1 per frame for all lights (profiles 32/80/176/192 lights and 24/60/132/192 cookies since 0.15 phase 5; 24/64/128/192 and 16/48/96/128 in phases 3 and 4), before the effects and the atmosphere. Since phase 4 it is the engine's pass (`Pool.ENGINE`), outside the addon pass budget; the beams pass is paid before the addon passes.
- **Coverage first (0.15 phase 4):** quality decides fidelity, not existence. Instances outside the drawn-effects cap keep delivering **their lights** (only those); over the light cap the engine spreads the selection over world cells and readmits lights where the tile has room. For the addon: mark combat information with `Light.Priority.CRITICAL` (never traded for decoration, cookie first), ambience with `DECORATIVE`; `Cookie.Fallback.NONE` when the light makes no sense without its image (it gets cookie priority); do not multiply instances to "have more light" (32 per effect). Diagnostics: F7 page 2 (resident/eligible/drawn/light-only instances, declared lights, the reason for every loss, CUSTOM caps). Restoring caps: `/calamidade_client quality defaults <profile|all>`. Chapter `docs/LUZ-0.15.md`, "Fase 4" and "Phase 5"; lab `/vfxsample coverage arena|combat|mixed|cinematic|stress`. Scenes packed in the middle of the screen keep fewer extra lights than spread ones (the density admission never raises the busiest tile): spread decorative lights over the scene instead of piling them.
- `terrain` pass (0.11.0): 1 per frame for the heat and the stand-ins (2/4/8 zones, 4/8/8 stand-ins), paid after the beams and before the lights; structure meshes 1/2/4 per frame.
- Duration up to 12000 ticks; radius up to 512; range 16..1024; 64 instances per server (0.10: 16 per player, 4 per mob, 2 per block and 16 slots reserved for players, configurable on the server).
- Mandatory accessibility: own full-screen passes × `post.screenEffects()`; no flashing with `!post.flashAllowed()`; no zoom or blur with `!post.motionEffects()`.

## Common mistakes

- Registering outside mod construction (cards are closed in the items `RegisterEvent`). Use the `@Mod` constructor or `FMLConstructModEvent`.
- Wrong order: `cue` before `timeline`, or an atmosphere that reads a track before the timeline.
- A phase or track name with an uppercase letter or a hyphen (`[a-z0-9_]`).
- A grid or pattern that "swims" with the camera: use coordinates relative to a fixed point (the effect center), not `scenePosAt` directly.
- `#include` of a file that is not in the shader's namespace (RimuLib does not look in the engine's namespace).
- Using RimuLib or Minecraft types without checking them with `javap` (see `AGENTS.md`).
- Writing "forward" with `Point.center(0, 0, d)` expecting the look direction without passing an orientation: without `Placement`/yaw, forward is south.
- Ignoring the empty `Optional` of a `start` (per-source limit, reserve, cooldown): it is not an error, it is a refusal.
- Starting effects or damage from animation keyframes on the client: the effect starts on the server.
- Terrain without a marker on the impact frame (the block swap becomes visible) or a definition radius smaller than the stand-in's fall (the stand-in disappears when the center leaves the screen).
- Expecting the Terrain API to load chunks: chunks with nobody nearby are skipped (`/forceload` in tests).
- Treating "destruction" as excavate + `burn` + `fill` (+ structure) by default, copying the meteor; using a structure or fill for debris, fire or dust that only needs to appear; replacing blocks on every pulse of a repeated attack; raising `terrainBlocksPerTick`/`terrainMaxVolume` as the first answer to a stall.
- New user-facing text hardcoded in Java instead of an `en_us.json` key, or Portuguese command names in new docs (use the English canonical names of `docs/COMMANDS.md`).
