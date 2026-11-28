# Effect recipes

Each recipe is a real effect of the example `examples/vfx-sample`, registered in `Receitas.java` and testable in game with `/vfxsample recipe <name>` (command permission); recipes 15 to 17 (item, block and mob triggers) are tested with the example's item, blocks and mob; 18 to 21 (Terrain API) change the terrain and are undone with `/calamidade terrain undo`; 22 to 26 (0.12.0) use the Sky API, global events and the per-dimension sky; 27 (0.13.0) is the Boss API demo boss; 28 (0.15.0) is the light lab. The code in the `.md` files is the same as the example's files: `./gradlew checkRecipes` (part of the build) fails if they diverge, and `./gradlew syncRecipes` copies them again. The individual recipe pages are still Portuguese prose around the (shared) code; the command names below are the English canonical ones of VFX Sample 1.14.1 (the Portuguese names in the pages still work as deprecated aliases).

| # | Recipe | Command | Shows |
|---|---|---|---|
| 1 | [Ground impact ring](01-anel-de-impacto.md) | `ring` | scene depth, `caDistanceWave`, marker with a cue, Impact |
| 2 | [Giant grid cube](02-cubo-de-grade.md) | `cube` | ray-box, grid in object coordinates, edges, OVERSHOOT |
| 3 | [Rainbow bubble](03-bolha-arco-iris.md) | `bubble` | ray-sphere, fresnel, `caRainbow`, center animated on the client |
| 4 | [Two-color impact frame](04-impact-frame.md) | `impact` | accessible full-screen pass (`screenEffects`, `flashAllowed`) |
| 5 | [Vertical beam](05-feixe-vertical.md) | `beam` | `caSegmentGlow` with a minimum per-pixel width and occlusion |
| 6 | [Ambient effect with sustain](06-ambiente-sustain.md) | `ambient` + `release`/`stop` | seamless loop, release, world mesh |
| 7 | [Tinted sky](07-ceu-tingido.md) | `sky` | Atmosphere API with tracks, no shader of its own |
| 8 | [Pillar of light](08-pilar-de-luz.md) | `pillar` | Beam with reveal, `Light.along` and a ground disc, no shader (0.9.0) |
| 9 | [Laser attached to the player](09-laser.md) | `laser` | `Point.anchorEye`/`anchorAim`, `Effects.follow`, flickering impact light |
| 10 | [Web of beams](10-teia.md) | `web` | 8 beams with pulses and waves, `PULSE_COUNT` track |
| 11 | [Campfire](11-fogueira.md) | `campfire` + `release` | light only (no geometry), flicker, sustain |
| 12 | [Light wave](12-onda-de-luz.md) | `light_wave` | `Light.ring` whose radius follows the shockwave of recipe 1 |
| 13 | [Hero](13-protagonista.md) | `hero` | HERO priority with beams, lights and atmosphere; test under `/calamidade bench 60` |
| 14 | [Oriented fan](14-leque.md) | `fan` | per-instance orientation: `Placement` with the player's yaw and `Point.facing` (0.10.0) |
| 15 | [Staff](15-cajado.md) | `/give @s vfxsample:cajado` | item trigger: use, attack, cooldown, durability, cue with `ctx.forward()` |
| 16 | [Pillars](16-pilares.md) | `/give @s vfxsample:pilar_redstone` | block entity trigger: REDSTONE and PERIODIC, facing, end on break or unload |
| 17 | [Zombie with a beam](17-zumbi.md) | `/summon zombie` (beams: `/vfxsample zombie on\|off`) | entity trigger on a vanilla mob (`LivingDamageEvent.Post`), per-mob limit |
| 18 | [Lightning that opens a trench](18-raio-trincheira.md) | `lightning_trench` | Terrain API: `trench` turned by the yaw, commit under the impact frame, burnt edge, glowing strip (0.11.0) |
| 19 | [Meteor that becomes a real block](19-meteoro.md) | `meteor` | stand-in → real: a burning sphere that falls, crater, sphere of real blocks, reveal, heat |
| 20 | [Simple crater](20-cratera-simples.md) | `simple_crater` | excavation and burnt edge, for study (the minimal one, without `burn`, is in the chapter); undo and list |
| 21 | [Tower that falls and stays](21-torre-caindo.md) | `falling_tower` | `.nbt` structure turned by 90°, cached stand-in mesh, crater under it, dust |
| 22 | [Black hole in the sky](22-buraco-negro.md) | `black_hole` | Sky API: `BLACK_HOLE` with lensing, size, disc and lens tracks, collapse with Impact, HERO (0.12.0) |
| 23 | [Portal to another galaxy](23-portal-galaxia.md) | `galaxy_portal` | `PORTAL`: a blinking seed, opening by track, rotating galaxy |
| 24 | [Giant moon](24-lua-gigante.md) | `giant_moon` | `CELESTIAL_BODY` with rings, `ELEVATION`/`AZIMUTH` tracks and phase |
| 25 | [Global eclipse](25-eclipse-global.md) | `/vfxsample event eclipse start\|release\|stop` | `WorldEvents` in the SERVER scope, persistent; `ECLIPSE` with darkening, distant lightning, atmosphere |
| 26 | [Test dimension](26-dimensao-teste.md) | `/vfxsample dimension`, `/vfxsample sky <state>` | `SkyProfile` with three states, `ProfileSkyEffects`, dimension by datapack |
| 27 | [Eclipse Sentinel boss](27-chefe-sentinela.md) | `/vfxsample boss`, `/vfxsample boss crater on\|off` | Boss API (0.13.0): phases, segmented bar, `Point.socket`, `Effects.anchorSocket`, `AnchorCapture` in a `RenderLayer`, area eclipse |
| 28 | [Light lab](28-laboratorio-luz.md) | `/vfxsample light <demo>`, `compare`, `stress`, `load`, `stop`, `list`; `/vfxsample coverage …` | Light Engine (0.15.0, experimental): Light Cookies (MASK, COLOR, tiling, animated sheets), spotlight, projector, ramps, gradients, `SOFT` and `SUBTRACT`, budgets and coverage; technical examples, not styles |

Recipes 18 to 21 are **use cases** of the Terrain API and all of them use `burn` because of the effect's intent; they are not the default configuration of a destruction. Before copying one of them, see [Choosing the operation](../../TERRENO-0.11.md#escolher-a-operação) (decision table, comparable examples and costs).

To start a new effect from scratch: `python3 scripts/novo_efeito.py <namespace> <name>` (see [AGENTS.md](../../../AGENTS.md)). GLSL functions used here: `calamidade_fx.glsl`, documented in [glsl.md](../glsl.md). API signatures: [API-REFERENCIA.md](../API-REFERENCIA.md).
