# Referência da API pública (api.v1) — Calamidade_Awakening 0.12.0

> Gerado por `scripts/ApiReferencia.java` a partir do `javap -public` do JAR compilado e do Javadoc dos fontes. Não edite à mão: rode `./gradlew apiDocs`. O build (`checkApiDocs`) falha se este arquivo estiver desatualizado ou se um membro público ficar sem Javadoc.

Unidades: tempo em ticks (20 por segundo), distâncias em blocos, cores em ARGB (`0xAARRGGBB`), frações em 0..1. Posições de cliente (`post.center()`, uniforms de shader) são relativas à câmera. Entrada inválida lança `IllegalArgumentException`. Guia por tarefa: [AGENTS.md](../../AGENTS.md) e [receitas](receitas/).

## Índice

- [`Addons`](#addons)
- [`Addons.Card`](#addonscard)
- [`Addons.Cast`](#addonscast)
- [`Addons.Entry`](#addonsentry)
- [`Addons.Range`](#addonsrange)
- [`Addons.Shot`](#addonsshot)
- [`Addons.TunedCast`](#addonstunedcast)
- [`Atmosphere`](#atmosphere)
- [`Atmosphere.Builder`](#atmospherebuilder)
- [`Atmosphere.Param`](#atmosphereparam)
- [`Atmosphere.Rgb`](#atmospherergb)
- [`Atmosphere.State`](#atmospherestate)
- [`Beam`](#beam)
- [`Beam.Builder`](#beambuilder)
- [`Beam.Param`](#beamparam)
- [`Beam.State`](#beamstate)
- [`EffectSource`](#effectsource)
- [`EffectSource.Kind`](#effectsourcekind)
- [`EffectTriggers`](#effecttriggers)
- [`EffectTriggers.BlockTrigger`](#effecttriggersblocktrigger)
- [`EffectTriggers.BlockTrigger.Component`](#effecttriggersblocktriggercomponent)
- [`EffectTriggers.BlockTrigger.Mode`](#effecttriggersblocktriggermode)
- [`EffectTriggers.EntityTrigger`](#effecttriggersentitytrigger)
- [`EffectTriggers.ItemTrigger`](#effecttriggersitemtrigger)
- [`EffectTriggers.ItemTrigger.Spot`](#effecttriggersitemtriggerspot)
- [`Effects`](#effects)
- [`Effects.Context`](#effectscontext)
- [`Effects.Cue`](#effectscue)
- [`Effects.Definition`](#effectsdefinition)
- [`Effects.Handle`](#effectshandle)
- [`Effects.Impact`](#effectsimpact)
- [`Effects.ImpactStyle`](#effectsimpactstyle)
- [`Effects.Material`](#effectsmaterial)
- [`Effects.Priority`](#effectspriority)
- [`Effects.Shape`](#effectsshape)
- [`Light`](#light)
- [`Light.Builder`](#lightbuilder)
- [`Light.Param`](#lightparam)
- [`Light.Shape`](#lightshape)
- [`Light.State`](#lightstate)
- [`Placement`](#placement)
- [`Point`](#point)
- [`Point.Source`](#pointsource)
- [`Sky`](#sky)
- [`Sky.Anchor`](#skyanchor)
- [`Sky.Builder`](#skybuilder)
- [`Sky.Direction`](#skydirection)
- [`Sky.Param`](#skyparam)
- [`Sky.State`](#skystate)
- [`Sky.Type`](#skytype)
- [`TerrainEdit`](#terrainedit)
- [`TerrainEdit.Builder`](#terraineditbuilder)
- [`TerrainEdit.Fill`](#terraineditfill)
- [`TerrainEdit.FillKind`](#terraineditfillkind)
- [`TerrainEdit.StandIn`](#terraineditstandin)
- [`TerrainEdit.StandIn.Builder`](#terraineditstandinbuilder)
- [`TerrainEdit.StandIn.Kind`](#terraineditstandinkind)
- [`TerrainEditEvent`](#terraineditevent)
- [`TerrainPalette`](#terrainpalette)
- [`TerrainPalette.Band`](#terrainpaletteband)
- [`TerrainPalette.Builder`](#terrainpalettebuilder)
- [`TerrainPalette.Entry`](#terrainpaletteentry)
- [`TerrainShape`](#terrainshape)
- [`TerrainShape.Kind`](#terrainshapekind)
- [`TerrainShape.Primitive`](#terrainshapeprimitive)
- [`Timeline`](#timeline)
- [`Timeline.Builder`](#timelinebuilder)
- [`Timeline.Easing`](#timelineeasing)
- [`Timeline.Keyframe`](#timelinekeyframe)
- [`Timeline.Sampler`](#timelinesampler)
- [`WorldEvents`](#worldevents)
- [`WorldEvents.Handle`](#worldeventshandle)
- [`WorldEvents.Options`](#worldeventsoptions)
- [`WorldEvents.Scope`](#worldeventsscope)
- [`AddonLayers`](#addonlayers)
- [`AddonLayers.Film`](#addonlayersfilm)
- [`AddonLayers.Hud`](#addonlayershud)
- [`AddonLayers.Panel`](#addonlayerspanel)
- [`AddonLayers.Post`](#addonlayerspost)
- [`AddonLayers.Start`](#addonlayersstart)
- [`AddonLayers.Time`](#addonlayerstime)
- [`AddonLayers.World`](#addonlayersworld)

## Addons

`dev.calamidade_awakening.api.v1.Addons` · class

Publishes a registered effect as a player-selectable skill: a card in the Star Atlas and a row in the Awakening Focus menu.

| Membro | Descrição |
|---|---|
| `static void resources(String namespace, Class<?> owner)` | Bind the resource owner's JAR so RimuLib can resolve shader files in its Java module. |
| `static void publish(ResourceLocation id, Addons.Card card)` | Publishes a card whose cast starts the effect at the aimed point for the caster. |
| `static void publish(ResourceLocation id, Addons.Card card, Addons.Cast action)` | Publishes a card with your own cast callback (validate costs and permissions there). |
| `static void publish(ResourceLocation id, Addons.Card card, Addons.Range power, Addons.TunedCast action)` | Publishes a card with a power slider. |
| `static List<Addons.Entry> entries()` | Every published card, ordered by effect ID. |
| `static void finishRegistration()` | Engine use: seals publishing at the end of mod loading. |
| `static Addons.Entry find(ResourceLocation id)` | Published card of an effect, or null. |

## Addons.Card

`dev.calamidade_awakening.api.v1.Addons.Card` · record

Card shown in the Atlas and the Focus.

| Membro | Descrição |
|---|---|
| `new Card(String titleKey, String fallback, String tier, int cooldown, ResourceLocation shader, List<Addons.Shot> camera)` | Canonical constructor: titleKey, fallback, tier, cooldown, shader, camera. |
| `String titleKey()` | Translation key of the title. |
| `String fallback()` | Title used when the key is missing. |
| `String tier()` | Short label shown on the card (for example `"RESONANCE"`). |
| `int cooldown()` | Per-player cooldown in ticks, 1..72000. |
| `ResourceLocation shader()` | Optional card shader (assets/&lt;ns&gt;/shaders/fx/&lt;path&gt;.fsh) with the CaCenter/CaEffect uniforms, charged to the addon pass budget; null for none. |
| `List<Addons.Shot> camera()` | Linear camera shots, possibly empty; replaced by `AddonLayers.film` when registered. |

## Addons.Cast

`dev.calamidade_awakening.api.v1.Addons.Cast` · interface

Server callback of a card without power control.

| Membro | Descrição |
|---|---|
| `boolean cast(ServerPlayer player, Vec3 target)` | Server thread. |

## Addons.Entry

`dev.calamidade_awakening.api.v1.Addons.Entry` · record

A published card.

| Membro | Descrição |
|---|---|
| `new Entry(ResourceLocation effect, Addons.Card card, Addons.Range power, Addons.TunedCast action)` | Canonical constructor: effect, card, power, action. |
| `String skillKey()` | Skill key used by the Focus menu: `addon/<namespace>/<path>`. |
| `ResourceLocation art()` | Card art: `assets/calamidade_awakening/textures/gui/sprites/skill/focus/addon/<ns>/<path>.png`, shipped in your JAR. |
| `ResourceLocation effect()` | Registered effect ID. |
| `Addons.Card card()` | Card metadata. |
| `Addons.Range power()` | Power range, or null without power control. |
| `Addons.TunedCast action()` | Server callback. |

## Addons.Range

`dev.calamidade_awakening.api.v1.Addons.Range` · record

Power slider shown in the Focus for a card; the server clamps the value the client sends.

| Membro | Descrição |
|---|---|
| `new Range(float min, float initial, float max)` | Canonical constructor: min, initial, max. |
| `float clamp(float value)` | `value` clamped to min..max; `initial` when not finite. |
| `float min()` | Smallest power, &gt; 0. |
| `float initial()` | Starting power, min..max. |
| `float max()` | Largest power, &gt; min. |

## Addons.Shot

`dev.calamidade_awakening.api.v1.Addons.Shot` · record

One linear camera shot of a card, played on the caster's client.

| Membro | Descrição |
|---|---|
| `new Shot(int endTick, Vec3 camera, float fov)` | Canonical constructor: endTick, camera, fov. |
| `int endTick()` | Last tick of this shot (ticks since the start), increasing along the list. |
| `Vec3 camera()` | Camera offset from the instance center, in blocks (up to 128). |
| `float fov()` | Field of view in degrees, 25..110. |

## Addons.TunedCast

`dev.calamidade_awakening.api.v1.Addons.TunedCast` · interface

Server callback of a card with power control.

| Membro | Descrição |
|---|---|
| `boolean cast(ServerPlayer player, Vec3 target, float power)` | Server thread. |

## Atmosphere

`dev.calamidade_awakening.api.v1.Atmosphere` · class

Immutable atmosphere of one effect (0.8.0): how the sky, the fog and the whole scene look while the effect is active.

| Membro | Descrição |
|---|---|
| `static float MAX_DISTANCE` | Largest fog distance, in blocks. |
| `static float MAX_EXPOSURE` | Largest exposure multiplier. |
| `static float MIN_FOG_SPAN` | Smallest gap between fog start and end, in blocks, after track values are clamped. |
| `static float ACTIVE_WEIGHT` | Weight below which an atmosphere is ignored (the engine draws no pass). |
| `static Atmosphere.Builder builder()` | New builder. |
| `int skyTop()` | Sky color at the zenith (ARGB, alpha ignored). |
| `int skyHorizon()` | Sky color at the horizon (ARGB, alpha ignored). |
| `float skyBlend()` | Sky gradient mix, 0..1. |
| `int fogColor()` | Fog color (ARGB, alpha ignored). |
| `float fogDensity()` | Fog density, 0..1. |
| `float fogStart()` | Fog start, blocks. |
| `float fogEnd()` | Fog end, blocks. |
| `float exposure()` | Exposure multiplier, 0..`MAX_EXPOSURE`. |
| `int ambientTint()` | Ambient tint color (ARGB, alpha ignored). |
| `float ambientStrength()` | Ambient tint strength, 0..1. |
| `float stars()` | Star field, 0..1. |
| `String track(Atmosphere.Param param)` | Track bound to a parameter, or null when the parameter is constant. |
| `Map<Atmosphere.Param, String> tracks()` | Every track binding, in `Param` order. |
| `String weightPhase()` | Phase whose weight multiplies the global weight, or null. |
| `boolean animated()` | True when some parameter, the weight or the phase depends on the timeline. |
| `void validate(Timeline timeline)` | Checks that every track and the phase this atmosphere reads exist in `timeline`. |
| `Atmosphere.State sample(Timeline.Sampler timeline, float envelope)` | Resolves this atmosphere at one moment. |
| `static Atmosphere.State mix(List<Atmosphere.State> states)` | Mixes the active atmospheres of one frame. |

## Atmosphere.Builder

`dev.calamidade_awakening.api.v1.Atmosphere.Builder` · class

Builder of an `Atmosphere`.

| Membro | Descrição |
|---|---|
| `Atmosphere.Builder sky(int top, int horizon, float blend)` | Sky gradient from `horizon` to `top` (ARGB, alpha ignored), mixed by `blend` (0..1). |
| `Atmosphere.Builder fog(int color, float density, float start, float end)` | Distance fog: `color` (ARGB, alpha ignored), `density` 0..1, from `start` to `end` blocks (0 &lt;= start &lt; end &lt;= `MAX_DISTANCE`). |
| `Atmosphere.Builder exposure(float multiplier)` | Exposure multiplier, 0..`MAX_EXPOSURE` (1 = unchanged). |
| `Atmosphere.Builder ambient(int tint, float strength)` | Ambient tint (ARGB, alpha ignored) applied to the whole scene by `strength` (0..1). |
| `Atmosphere.Builder stars(float amount)` | Procedural star field on the sky, 0..1. |
| `Atmosphere.Builder track(Atmosphere.Param param, String track)` | Takes `param` from a track of the effect's timeline instead of the constant (clamped to the parameter range). |
| `Atmosphere.Builder weightPhase(String phase)` | Multiplies the global weight by the weight of a timeline phase (0 outside it). |
| `Atmosphere build()` | Immutable atmosphere. |

## Atmosphere.Param

`dev.calamidade_awakening.api.v1.Atmosphere.Param` · enum

Numeric parameters that can be driven by a timeline track.

| Membro | Descrição |
|---|---|
| `SKY_BLEND` | Sky gradient mix, 0..1. |
| `FOG_DENSITY` | Fog density, 0..1. |
| `FOG_START` | Fog start, blocks. |
| `FOG_END` | Fog end, blocks (kept at least `MIN_FOG_SPAN` beyond the start). |
| `EXPOSURE` | Exposure multiplier, 0..`MAX_EXPOSURE`. |
| `AMBIENT_STRENGTH` | Ambient tint strength, 0..1. |
| `STARS` | Star field, 0..1. |
| `WEIGHT` | Extra multiplier of the global weight, 0..1 (the envelope and the phase still apply). |
| `float min` | Smallest and largest value of the parameter; track values are clamped to this range. |
| `float max` | Smallest and largest value of the parameter; track values are clamped to this range. |
| `float clamp(float value)` | Clamps a value into this parameter's range; NaN becomes `min`. |

## Atmosphere.Rgb

`dev.calamidade_awakening.api.v1.Atmosphere.Rgb` · record

RGB color with components in 0..1, built from ARGB ints (alpha ignored).

| Membro | Descrição |
|---|---|
| `static Atmosphere.Rgb WHITE` | (1, 1, 1). |
| `static Atmosphere.Rgb BLACK` | (0, 0, 0). |
| `new Rgb(float r, float g, float b)` | Canonical constructor: r, g, b. |
| `static Atmosphere.Rgb of(int argb)` | Color of an ARGB int; alpha is ignored. |
| `int argb()` | Opaque ARGB int, rounded to the nearest 8-bit value. |
| `Atmosphere.Rgb lerp(Atmosphere.Rgb other, float t)` | Linear interpolation: `this` at t = 0, `other` at t = 1 (t clamped to 0..1). |
| `float luminance()` | Relative luminance (Rec. 709 weights). |
| `float r()` | Red, 0..1. |
| `float g()` | Green, 0..1. |
| `float b()` | Blue, 0..1. |

## Atmosphere.State

`dev.calamidade_awakening.api.v1.Atmosphere.State` · record

Resolved atmosphere at one moment: every parameter is a plain number already taken from tracks and clamped, plus the global `weight` (0..1).

| Membro | Descrição |
|---|---|
| `static Atmosphere.State NEUTRAL` | Weight 0, no sky blend, no fog, exposure 1, no tint, no stars. |
| `new State(float weight, Atmosphere.Rgb skyTop, Atmosphere.Rgb skyHorizon, float skyBlend, Atmosphere.Rgb fogColor, float fogDensity, float fogStart, float fogEnd, float exposure, Atmosphere.Rgb ambientTint, float ambientStrength, float stars)` | Canonical constructor: weight, skyTop, skyHorizon, skyBlend, fogColor, fogDensity, fogStart, fogEnd, exposure, ambientTint, ambientStrength, stars. |
| `boolean active()` | True when the weight is above `ACTIVE_WEIGHT`: the engine draws the atmosphere pass. |
| `float fogNear(float vanillaNear)` | Game fog start (blocks) after this atmosphere: moves `vanillaNear` toward `fogStart` by `fogDensity`, never farther than the game's own value. |
| `float fogFar(float vanillaNear, float vanillaFar)` | Game fog end (blocks) after this atmosphere: moves `vanillaFar` toward `fogEnd` by `fogDensity`, never farther than the game's own value and at least `MIN_FOG_SPAN` beyond fogNear(vanillaNear). |
| `Atmosphere.Rgb fogColorOver(Atmosphere.Rgb vanilla)` | Game fog color after this atmosphere: `vanilla` moved toward `fogColor` by `fogDensity`. |
| `float screenFog(double distance)` | Fraction of fog (0..1) at `distance` blocks when the engine draws the fog itself on the screen (client option `vanillaFog` off): `fogDensity * smoothstep(fogStart, fogEnd, distance)`. |
| `float weight()` | Global weight, 0..1. |
| `Atmosphere.Rgb skyTop()` | Zenith color. |
| `Atmosphere.Rgb skyHorizon()` | Horizon color. |
| `float skyBlend()` | Sky gradient mix, 0..1. |
| `Atmosphere.Rgb fogColor()` | Fog color. |
| `float fogDensity()` | Fog density, 0..1. |
| `float fogStart()` | Fog start, blocks. |
| `float fogEnd()` | Fog end, blocks (greater than the start). |
| `float exposure()` | Exposure multiplier, 0..`MAX_EXPOSURE`. |
| `Atmosphere.Rgb ambientTint()` | Ambient tint color. |
| `float ambientStrength()` | Ambient tint strength, 0..1. |
| `float stars()` | Star field, 0..1. |

## Beam

`dev.calamidade_awakening.api.v1.Beam` · class

Immutable beam of light between two points (0.9.0): a white-hot core inside a saturated halo, with optional pulses that run along it, a lateral wave and noise, and a progressive reveal (the beam "grows" from its start to its end).

| Membro | Descrição |
|---|---|
| `static int MAX_PER_EFFECT` | Most beams per effect (`Effects.beams`). |
| `static float MAX_CORE_WIDTH` | Largest core width, blocks. |
| `static float MAX_HALO_WIDTH` | Largest halo width, blocks. |
| `static float MAX_INTENSITY` | Largest intensity multiplier. |
| `static int MAX_PULSES` | Most pulses on one beam. |
| `static float MAX_TRACK_OFFSET` | Largest track offset of an end, blocks per axis. |
| `static float ACTIVE_WEIGHT` | Weight below which a beam is not drawn. |
| `static Beam.Builder builder(Point from, Point to)` | New builder for a beam from `from` to `to` (different points). |
| `Point from()` | Start of the beam. |
| `Point to()` | End of the beam. |
| `float coreWidth()` | Core width, blocks. |
| `float haloWidth()` | Halo width, blocks. |
| `int coreColor()` | Core color, ARGB (alpha ignored). |
| `int haloColor()` | Halo color, ARGB (alpha ignored). |
| `float intensity()` | Brightness multiplier, 0..`MAX_INTENSITY`. |
| `int pulses()` | Number of pulses, 0..`MAX_PULSES`. |
| `float pulseSpeed()` | Pulse speed, blocks per tick. |
| `float pulseSize()` | Pulse length, blocks. |
| `float waveAmplitude()` | Lateral wave amplitude, blocks. |
| `float waveLength()` | Wavelength, blocks. |
| `float waveSpeed()` | Wave speed, blocks per tick. |
| `float noise()` | Lateral noise amplitude, blocks. |
| `float reveal()` | Drawn fraction from the start, 0..1. |
| `String track(Beam.Param param)` | Track bound to a parameter, or null when the parameter is constant. |
| `Map<Beam.Param, String> tracks()` | Every track binding, in `Param` order. |
| `String weightPhase()` | Phase whose weight multiplies the beam weight, or null. |
| `boolean animated()` | True when some parameter, the weight or the phase depends on the timeline. |
| `void validate(Timeline timeline)` | Checks that every track and the phase this beam reads exist in `timeline`, which may be null only when nothing is animated. |
| `Beam.State sample(Timeline.Sampler timeline, float envelope)` | Resolves this beam at one moment. |

## Beam.Builder

`dev.calamidade_awakening.api.v1.Beam.Builder` · class

Builder of a `Beam`.

| Membro | Descrição |
|---|---|
| `Beam.Builder width(float core, float halo)` | Core and halo widths in blocks (full widths): core 0..`MAX_CORE_WIDTH`, halo 0..`MAX_HALO_WIDTH`. |
| `Beam.Builder color(int core, int halo)` | Core and halo colors (ARGB, alpha ignored). |
| `Beam.Builder color(int halo)` | Halo color (ARGB, alpha ignored), keeping the white core. |
| `Beam.Builder intensity(float value)` | Brightness multiplier, 0..`MAX_INTENSITY` (1 by default). |
| `Beam.Builder pulses(int count, float speed, float size)` | Pulses running along the beam: `count` 0..`MAX_PULSES`, evenly spaced over the full length; `speed` in blocks per tick, -8..8 (negative runs from the end to the start); `size`, the length of each pulse in blocks, 0..32. |
| `Beam.Builder wave(float amplitude, float wavelength, float speed)` | Lateral wave: `amplitude` 0..8 blocks, `wavelength` 0.25..256 blocks, `speed` -8..8 blocks per tick along the beam. |
| `Beam.Builder noise(float amplitude)` | Lateral noise (a crackling, unstable beam), amplitude 0..8 blocks. |
| `Beam.Builder reveal(float fraction)` | Drawn fraction from the start, 0..1 (1 by default). |
| `Beam.Builder track(Beam.Param param, String track)` | Takes `param` from a track of the effect's timeline instead of the constant (clamped to the parameter range). |
| `Beam.Builder weightPhase(String phase)` | Multiplies the beam weight by the weight of a timeline phase (0 outside it). |
| `Beam build()` | Immutable beam. |

## Beam.Param

`dev.calamidade_awakening.api.v1.Beam.Param` · enum

Numeric parameters that can be driven by a timeline track, each with the range used to clamp it.

| Membro | Descrição |
|---|---|
| `CORE_WIDTH` | Core width, blocks. |
| `HALO_WIDTH` | Halo width, blocks. |
| `INTENSITY` | Brightness multiplier. |
| `PULSE_COUNT` | Number of pulses, rounded to the nearest integer. |
| `PULSE_SPEED` | Pulse speed, blocks per tick (negative: from the end toward the start). |
| `PULSE_SIZE` | Length of each pulse, blocks. |
| `WAVE_AMPLITUDE` | Lateral wave amplitude, blocks. |
| `WAVE_LENGTH` | Wavelength, blocks. |
| `WAVE_SPEED` | Wave speed along the beam, blocks per tick. |
| `NOISE` | Lateral noise amplitude, blocks. |
| `REVEAL` | Drawn fraction from the start, 0..1. |
| `WEIGHT` | Extra multiplier of the beam weight, 0..1 (the envelope and the phase still apply). |
| `FROM_X` | Offset added to the start, x, blocks. |
| `FROM_Y` | Offset added to the start, y, blocks. |
| `FROM_Z` | Offset added to the start, z, blocks. |
| `TO_X` | Offset added to the end, x, blocks. |
| `TO_Y` | Offset added to the end, y, blocks. |
| `TO_Z` | Offset added to the end, z, blocks. |
| `float min` | Smallest and largest value of the parameter; track values are clamped to this range. |
| `float max` | Smallest and largest value of the parameter; track values are clamped to this range. |
| `float clamp(float value)` | Clamps a value into this parameter's range; NaN becomes the value closest to 0 in the range. |

## Beam.State

`dev.calamidade_awakening.api.v1.Beam.State` · record

Resolved beam at one moment: parameters already taken from tracks and clamped, the weight (0..1) and the track offsets of both ends.

| Membro | Descrição |
|---|---|
| `new State(float weight, float fromX, float fromY, float fromZ, float toX, float toY, float toZ, float coreWidth, float haloWidth, int coreColor, int haloColor, float intensity, int pulses, float pulseSpeed, float pulseSize, float waveAmplitude, float waveLength, float waveSpeed, float noise, float reveal)` | Canonical constructor: weight, fromX, fromY, fromZ, toX, toY, toZ, coreWidth, haloWidth, coreColor, haloColor, intensity, pulses, pulseSpeed, pulseSize, waveAmplitude, waveLength, waveSpeed, noise, reveal. |
| `boolean visible()` | True when the engine draws this beam: weight above `ACTIVE_WEIGHT`, something revealed, lit and wide. |
| `float weight()` | Beam weight, 0..1. |
| `float fromX()` | Track offset of the start, x, blocks. |
| `float fromY()` | Track offset of the start, y, blocks. |
| `float fromZ()` | Track offset of the start, z, blocks. |
| `float toX()` | Track offset of the end, x, blocks. |
| `float toY()` | Track offset of the end, y, blocks. |
| `float toZ()` | Track offset of the end, z, blocks. |
| `float coreWidth()` | Core width, blocks. |
| `float haloWidth()` | Halo width, blocks. |
| `int coreColor()` | Core color, ARGB (alpha ignored). |
| `int haloColor()` | Halo color, ARGB (alpha ignored). |
| `float intensity()` | Brightness multiplier, 0..`MAX_INTENSITY`. |
| `int pulses()` | Number of pulses, 0..`MAX_PULSES`. |
| `float pulseSpeed()` | Pulse speed, blocks per tick. |
| `float pulseSize()` | Pulse length, blocks. |
| `float waveAmplitude()` | Lateral wave amplitude, blocks. |
| `float waveLength()` | Wavelength, blocks. |
| `float waveSpeed()` | Wave speed, blocks per tick. |
| `float noise()` | Lateral noise amplitude, blocks. |
| `float reveal()` | Drawn fraction from the start, 0..1. |

## EffectSource

`dev.calamidade_awakening.api.v1.EffectSource` · class

Who started an instance (0.10.0): a player, an entity (a mob), a block or a script.

| Membro | Descrição |
|---|---|
| `static EffectSource player(Player player)` | A player. |
| `static EffectSource player(UUID uuid)` | A player by UUID (for cooldown lookups of offline or remembered players). |
| `static EffectSource entity(Entity entity)` | An entity: `Kind.PLAYER` for players, `Kind.ENTITY` for any other entity. |
| `static EffectSource entity(UUID uuid)` | A non-player entity by UUID (`Kind.ENTITY`). |
| `static EffectSource block(Level level, BlockPos pos)` | A block at a position of a level. |
| `static EffectSource block(ResourceLocation dimension, BlockPos pos)` | A block at a position of a dimension (for example `minecraft:overworld`). |
| `static EffectSource block(BlockEntity blockEntity)` | The block of a block entity placed in a level. |
| `static EffectSource script()` | Code with no individual source. |
| `EffectSource.Kind kind()` | Kind of source. |
| `UUID uuid()` | UUID of the player or entity; null for blocks and scripts. |
| `ResourceLocation dimension()` | Dimension of a block source; null for the others. |
| `BlockPos pos()` | Position of a block source (immutable); null for the others. |
| `boolean equals(Object o)` | Same kind and identity. |
| `int hashCode()` | Hash of kind and identity. |
| `String toString()` | Readable form, such as `ENTITY(uuid)` or `BLOCK(minecraft:overworld 10 64 -3)`. |

## EffectSource.Kind

`dev.calamidade_awakening.api.v1.EffectSource.Kind` · enum

Kind of source, each with its own per-source limit.

| Membro | Descrição |
|---|---|
| `PLAYER` | A player (cards, items, commands run by a player). |
| `ENTITY` | A non-player entity: a mob attack, a projectile, a vehicle. |
| `BLOCK` | A block or block entity, identified by dimension and position. |
| `SCRIPT` | Code with no individual source (commands, datapacks, mod logic). |

## EffectTriggers

`dev.calamidade_awakening.api.v1.EffectTriggers` · class

Ready-made triggers that start effects from items, block entities and entities (0.10.0).

| Membro | Descrição |
|---|---|
| `static EffectTriggers.ItemTrigger item(ResourceLocation effect)` | Item trigger for `effect` (registered with `Effects.register` before it fires): spot EYES, no cooldown, no durability cost. |
| `static EffectTriggers.BlockTrigger blockEntity(ResourceLocation effect)` | Block entity trigger for `effect`: PERIODIC every 40 ticks, 1 block above the block, turned by its facing. |
| `static EffectTriggers.EntityTrigger entity(ResourceLocation effect)` | Entity trigger for `effect`: anchored at the feet, yaw following the body, no cooldown. |

## EffectTriggers.BlockTrigger

`dev.calamidade_awakening.api.v1.EffectTriggers.BlockTrigger` · class

Starts effects from a block entity (0.10.0).

| Membro | Descrição |
|---|---|
| `EffectTriggers.BlockTrigger mode(EffectTriggers.BlockTrigger.Mode mode)` | Copy in this mode. |
| `EffectTriggers.BlockTrigger periodic(int ticks)` | Copy in PERIODIC mode every `ticks` ticks, 1..72000. |
| `EffectTriggers.BlockTrigger redstone()` | Copy in REDSTONE mode (period 0: once per rising edge, unless `period` says otherwise). |
| `EffectTriggers.BlockTrigger proximity(double radius)` | Copy in PROXIMITY mode with this radius, 0.5..128 blocks (period 0: once per arrival). |
| `EffectTriggers.BlockTrigger period(int ticks)` | Copy with this period in ticks: PERIODIC 1..72000; REDSTONE and PROXIMITY 0..72000, where 0 starts once per rising edge and more repeats while the trigger stays on. |
| `EffectTriggers.BlockTrigger radius(double blocks)` | Copy with this PROXIMITY radius, 0.5..128 blocks. |
| `EffectTriggers.BlockTrigger offset(double right, double up, double forward)` | Copy with the center moved `right`, `up`, `forward` blocks from the block's bottom center (default 0, 1, 0). |
| `EffectTriggers.BlockTrigger yaw(float degrees)` | Copy turned by a fixed yaw in degrees instead of the block's facing. |
| `EffectTriggers.BlockTrigger pitch(float degrees)` | Copy with this pitch in degrees, [-90, 90] (positive looks down). |
| `EffectTriggers.BlockTrigger cooldown(int ticks)` | Copy with a cooldown per block and effect, 0..72000 ticks. |
| `ResourceLocation effect()` | Effect this trigger starts. |
| `EffectTriggers.BlockTrigger.Mode mode()` | Mode. |
| `int period()` | Period in ticks. |
| `double radius()` | PROXIMITY radius in blocks. |
| `int cooldown()` | Cooldown in ticks, 0 for none. |
| `EffectTriggers.BlockTrigger.Component attach(BlockEntity blockEntity)` | New component for one block entity; keep it in a field of that block entity. |

## EffectTriggers.BlockTrigger.Component

`dev.calamidade_awakening.api.v1.EffectTriggers.BlockTrigger.Component` · class

Effect state of one block entity.

| Membro | Descrição |
|---|---|
| `void tick()` | Server tick: reads the input and starts, holds or releases the effect. |
| `boolean stop()` | Stops the running instance now. |
| `Optional<Effects.Handle> handle()` | Handle of the instance this component started last, while it runs. |
| `boolean active()` | Input seen on the last tick: powered (REDSTONE), a player near (PROXIMITY), always true for PERIODIC. |
| `BlockEntity blockEntity()` | Block entity this component serves. |

## EffectTriggers.BlockTrigger.Mode

`dev.calamidade_awakening.api.v1.EffectTriggers.BlockTrigger.Mode` · enum

When the block starts its effect.

| Membro | Descrição |
|---|---|
| `PERIODIC` | Every `period` ticks while the block is loaded (a sustained effect is held instead). |
| `REDSTONE` | On the rising edge of a redstone signal into the block; again every `period` while powered (0: once). |
| `PROXIMITY` | When a player (not a spectator) comes within `radius` blocks; again every `period` while one stays. |

## EffectTriggers.EntityTrigger

`dev.calamidade_awakening.api.v1.EffectTriggers.EntityTrigger` · class

Starts effects anchored to an entity (0.10.0), for mob attacks.

| Membro | Descrição |
|---|---|
| `EffectTriggers.EntityTrigger offset(double right, double up, double forward)` | Copy whose center is `right`, `up`, `forward` blocks from the entity's feet, in its body frame. |
| `EffectTriggers.EntityTrigger followBody(boolean follow)` | Copy whose yaw follows the body (`true`, default) or stays as it was at the start (`false`). |
| `EffectTriggers.EntityTrigger pitch(float degrees)` | Copy with this pitch for `fire(Entity)`, degrees in [-90, 90] (positive looks down). |
| `EffectTriggers.EntityTrigger cooldown(int ticks)` | Copy with a cooldown per entity and effect, 0..72000 ticks. |
| `ResourceLocation effect()` | Effect this trigger starts. |
| `int cooldown()` | Cooldown in ticks, 0 for none. |
| `Optional<Effects.Handle> fire(Entity entity)` | Starts the effect on `entity`, turned with its body (or with its yaw at the start). |
| `Optional<Effects.Handle> fire(Entity entity, Entity target)` | Starts the effect on `entity`, turned toward `target` (yaw and pitch from the center to the target's middle) at this moment; the instance keeps following the entity. |

## EffectTriggers.ItemTrigger

`dev.calamidade_awakening.api.v1.EffectTriggers.ItemTrigger` · class

Starts effects from an item (0.10.0): call `use` from `Item.use` and `attack` from `Item.hurtEnemy`, or `fire` from anywhere on the server.

| Membro | Descrição |
|---|---|
| `EffectTriggers.ItemTrigger at(EffectTriggers.ItemTrigger.Spot spot)` | Copy that starts at this spot. |
| `EffectTriggers.ItemTrigger reach(double blocks)` | Copy with this aim reach for `Spot.AIM`, 0.5..256 blocks (32 by default). |
| `EffectTriggers.ItemTrigger offset(double right, double up, double forward)` | Copy that moves the start by `right`, `up` and `forward` blocks in the instance frame. |
| `EffectTriggers.ItemTrigger cooldown(int ticks)` | Copy with a cooldown per user and effect, 0..72000 ticks (also put on the item for players). |
| `EffectTriggers.ItemTrigger durability(int points)` | Copy that costs this many durability points per accepted start, 0..1024 (damageable items, not in creative). |
| `ResourceLocation effect()` | Effect this trigger starts. |
| `EffectTriggers.ItemTrigger.Spot spot()` | Where it starts. |
| `int cooldown()` | Cooldown in ticks, 0 for none. |
| `int durability()` | Durability cost per start. |
| `InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)` | For `Item.use`: on the server starts the effect and returns `consume` (or `fail` when refused); on the client returns `success`, so the hand swings while the server decides. |
| `boolean attack(ItemStack stack, LivingEntity target, LivingEntity attacker)` | For `Item.hurtEnemy`: starts the effect with `target` (spot TARGET puts it on the target), using the main hand. |
| `Optional<Effects.Handle> fire(LivingEntity user, InteractionHand hand, Entity target)` | Starts the effect for `user` holding the item in `hand`, with an optional `target` (null for none). |

## EffectTriggers.ItemTrigger.Spot

`dev.calamidade_awakening.api.v1.EffectTriggers.ItemTrigger.Spot` · enum

Where the instance starts and how it is turned.

| Membro | Descrição |
|---|---|
| `CASTER` | Anchored to the user's feet; the yaw follows the user's body. |
| `EYES` | Fixed at the user's eyes, turned by the user's look (yaw and pitch). |
| `AIM` | Fixed at the first block the user looks at, up to the reach (the full reach when nothing is hit), turned by the look's yaw. |
| `TARGET` | Fixed at the middle of the hit entity, turned from the user's eyes toward it. |

## Effects

`dev.calamidade_awakening.api.v1.Effects` · class

Public common-side API: register effects, schedule server cues and start or stop instances.

| Membro | Descrição |
|---|---|
| `static double DEFAULT_RANGE` | Default distribution range, in blocks: players farther than this from the center do not receive an instance. |
| `static int MAX_TICKS` | Longest effect, in ticks (12000 = 10 minutes). |
| `static void register(ResourceLocation id, Effects.Definition definition)` | Registers an external effect in your own namespace (the `calamidade_awakening` namespace is reserved). |
| `static void timeline(ResourceLocation id, Timeline timeline)` | Attaches a `Timeline` to a registered external effect (0.7.0). |
| `static void atmosphere(ResourceLocation id, Atmosphere atmosphere)` | Attaches an `Atmosphere` to a registered external effect (0.8.0): sky tint, fog, exposure, ambient tint and stars while its instances are active. |
| `static void beams(ResourceLocation id, Beam... beams)` | Attaches 1..`Beam.MAX_PER_EFFECT` beams to a registered external effect (0.9.0). |
| `static void lights(ResourceLocation id, Light... lights)` | Attaches 1..`Light.MAX_PER_EFFECT` visual lights to a registered external effect (0.9.0). |
| `static void terrain(ResourceLocation id, TerrainEdit... edits)` | Attaches 1..`TerrainEdit.MAX_PER_EFFECT` terrain edits to a registered external effect (0.11.0): excavation, burnt shell, real blocks or a structure placed at a marker of the effect's timeline, with the heat and the falling stand-in drawn by the client. |
| `static void sky(ResourceLocation id, Sky... layers)` | Attaches 1..`Sky.MAX_PER_EFFECT` sky layers to a registered external effect (0.12.0): black holes, portals, eclipses, rifts, giant moons and planets, auroras, placed by direction on the sky. |
| `static void cue(ResourceLocation id, int tick, Effects.Cue cue)` | Schedules a server callback at `tick` (0 .. duration-1) of every instance of a registered external effect. |
| `static void cue(ResourceLocation id, String marker, Effects.Cue cue)` | Schedules a cue on a marker of the effect's timeline (0.7.0). |
| `static void onImpact(ResourceLocation id, Effects.Cue cue)` | Schedules a cue at the definition's `Impact` tick, keeping visual contact and gameplay in sync. |
| `static boolean cooldownReady(EffectSource source, ResourceLocation id, ServerLevel level)` | True when `source` may start `id` now: no cooldown of that source for that effect is running (0.10.0). |
| `static long cooldownLeft(EffectSource source, ResourceLocation id, ServerLevel level)` | Ticks left of the cooldown of `source` for `id`, 0 when ready (0.10.0). |
| `static void startCooldown(EffectSource source, ResourceLocation id, ServerLevel level, int ticks)` | Starts (or restarts) the cooldown of `source` for `id`: `ticks` 1..72000, 0 clears it (0.10.0). |
| `static Optional<Effects.Handle> start(ResourceLocation id, ServerLevel level, Vec3 position)` | Starts an instance at a fixed world position (blocks). |
| `static Optional<Effects.Handle> start(ResourceLocation id, ServerLevel level, Vec3 position, float yaw, float pitch)` | Starts an instance at a fixed world position (blocks) turned by `yaw` and `pitch` (degrees, Minecraft convention: yaw 0 faces south, pitch positive looks down, within [-90, 90]) (0.10.0). |
| `static Optional<Effects.Handle> start(ResourceLocation id, ServerPlayer caster, Vec3 position)` | Starts an instance at a position for a caster: the caster's client plays the effect's camera (card shots or `AddonLayers.film`) and cues receive the player as `caster`. |
| `static Optional<Effects.Handle> start(ResourceLocation id, ServerPlayer caster, Vec3 position, float yaw, float pitch)` | Starts an instance for a caster at a position turned by `yaw` and `pitch` in degrees (0.10.0); see ServerLevel, Vec3, float, float). |
| `static Optional<Effects.Handle> start(ResourceLocation id, ServerLevel level, Placement placement)` | Starts an instance from a `Placement` (0.10.0): fixed position or anchor entity, yaw and pitch, the yaw optionally following the anchor's body, the caster, the source, a cooldown and a bound block entity. |
| `static Optional<Effects.Handle> follow(ResourceLocation id, Entity entity, Vec3 offset)` | Starts an instance anchored to a server entity: the center follows the entity's feet plus `offset` (world axes, blocks). |
| `static Optional<Effects.Handle> follow(ResourceLocation id, Entity entity, Vec3 offset, boolean followBody)` | Starts an instance anchored to a server entity whose yaw follows the entity's body when `followBody` is true (0.10.0): the body yaw of a living entity, interpolated on clients, or the yaw of other entities. |

## Effects.Context

`dev.calamidade_awakening.api.v1.Effects.Context` · record

Server-side moment of a live instance, delivered to a `Cue` on the server thread.

| Membro | Descrição |
|---|---|
| `new Context(ServerLevel level, Vec3 center, ServerPlayer caster, Entity anchor, long seed, int tick, Effects.Handle handle)` | 0.6 form, kept for compatibility: no timeline, local and real age equal to the tick. |
| `new Context(ServerLevel level, Vec3 center, ServerPlayer caster, Entity anchor, long seed, int tick, Effects.Handle handle, Timeline timeline, double localAge, long age)` | 0.7 to 0.9 form, kept for compatibility: no orientation (yaw 0, pitch 0, the world axes); the source is the caster, else the anchor, else a script. |
| `new Context(ServerLevel level, Vec3 center, ServerPlayer caster, Entity anchor, long seed, int tick, Effects.Handle handle, Timeline timeline, double localAge, long age, float yaw, float pitch, EffectSource source)` | Canonical constructor: level, center, caster, anchor, seed, tick, handle, timeline, localAge, age, yaw, pitch, source. |
| `List<LivingEntity> living(double radius)` | Living entities whose bounding box center lies within the radius, excluding the caster and the anchor. |
| `Vec3 facing(double right, double up, double forward)` | World offset (blocks) of `right`, `up` and `forward` in the instance frame (0.10.0); add it to `center()` for a point in front of the effect. |
| `Vec3 forward()` | Unit vector the instance faces (0.10.0); (0, 0, 1) without orientation. |
| `ServerLevel level()` | Server level of the instance. |
| `Vec3 center()` | Current center (blocks); includes the entity offset for anchored instances. |
| `ServerPlayer caster()` | Player who cast it, or null (started without a player, or the player left). |
| `Entity anchor()` | Entity of `Effects.follow`, or null for fixed positions. |
| `long seed()` | Instance seed, the same the client uses for its visuals. |
| `int tick()` | Scheduled tick on the timeline clock (local tick). |
| `Effects.Handle handle()` | Handle of this instance (`stop()`, `release()`). |
| `Timeline timeline()` | Registered timeline, or null (0.7.0). |
| `double localAge()` | Local age when the cue runs, equal to `tick` (0.7.0). |
| `long age()` | Real age in ticks, which keeps growing across sustain loops (0.7.0). |
| `float yaw()` | Instance yaw in degrees when the cue runs, [-180, 180) (0.10.0): the `Placement` yaw, plus the anchor's body yaw when the instance follows it; 0 without orientation. |
| `float pitch()` | Instance pitch in degrees, [-90, 90], positive looks down (0.10.0); 0 without orientation. |
| `EffectSource source()` | Who started the instance (0.10.0): player, entity, block or script. |

## Effects.Cue

`dev.calamidade_awakening.api.v1.Effects.Cue` · interface

Gameplay hook.

| Membro | Descrição |
|---|---|
| `void run(Effects.Context context)` | Runs on the server thread; exceptions are logged and do not stop the effect or the other cues. |

## Effects.Definition

`dev.calamidade_awakening.api.v1.Effects.Definition` · record

What an effect is: duration, size, color, built-in look, optional particle and sound, impact accent and distribution range.

| Membro | Descrição |
|---|---|
| `new Definition(int ticks, double radius, int argb, Effects.Shape shape, Effects.Material material, ResourceLocation particle, ResourceLocation sound)` | Definition without impact accent and with the default range. |
| `new Definition(int ticks, double radius, int argb, Effects.Shape shape, Effects.Material material, ResourceLocation particle, ResourceLocation sound, Effects.Impact impact)` | Definition with an impact accent and the default range. |
| `new Definition(int ticks, double radius, int argb, Effects.Shape shape, Effects.Material material, ResourceLocation particle, ResourceLocation sound, Effects.Impact impact, double range)` | 0.8 form, kept for compatibility: `Priority.NORMAL` priority. |
| `new Definition(int ticks, double radius, int argb, Effects.Shape shape, Effects.Material material, ResourceLocation particle, ResourceLocation sound, Effects.Impact impact, double range, Effects.Priority priority)` | Canonical constructor: ticks, radius, argb, shape, material, particle, sound, impact, range, priority. |
| `Effects.Definition withRange(double blocks)` | Copy with another distribution range, 16..1024 blocks (keeps the priority). |
| `Effects.Definition withPriority(Effects.Priority value)` | Copy with another drawing priority (0.9.0). |
| `int ticks()` | Duration, 1..`MAX_TICKS` ticks. |
| `double radius()` | Size in blocks, (0, 512]; also the frustum test extent and the radius handed to layers. |
| `int argb()` | Main color (ARGB), used by built-in geometry, flashes and cards. |
| `Effects.Shape shape()` | Built-in geometry. |
| `Effects.Material material()` | Built-in volume. |
| `ResourceLocation particle()` | Optional particle ID (a SimpleParticleType), emitted every 4 ticks; null for none. |
| `ResourceLocation sound()` | Optional positional opening sound ID; null for none. |
| `Effects.Impact impact()` | Optional impact accent; null for none. |
| `double range()` | Distribution range, 16..1024 blocks (`DEFAULT_RANGE` by default). |
| `Effects.Priority priority()` | Drawing priority (0.9.0), `Priority.NORMAL` by default; see `withPriority`. |

## Effects.Handle

`dev.calamidade_awakening.api.v1.Effects.Handle` · interface

Server-side control of one live instance, returned by `start` and `follow`.

| Membro | Descrição |
|---|---|
| `boolean stop()` | Ends the instance right away. |
| `default boolean release()` | Releases a sustained instance (0.7.0): the loop ends and the timeline jumps to its release start, then plays the exit and expires. |
| `default boolean alive()` | True while the instance lives on the server (0.10.0): not stopped, not expired and its anchor or bound block still there. |

## Effects.Impact

`dev.calamidade_awakening.api.v1.Effects.Impact` · record

Impact accent of a definition: a screen accent at `tick` whose strength decays linearly to zero over `duration` ticks.

| Membro | Descrição |
|---|---|
| `new Impact(int tick, int duration, float strength, Effects.ImpactStyle style)` | Canonical constructor: tick, duration, strength, style. |
| `float weight(double age)` | Accent strength at `age` ticks: `strength` at the impact tick, falling to 0 after `duration`. |
| `int tick()` | Start tick, 0..`MAX_TICKS`-1 (and tick + duration within the definition). |
| `int duration()` | Ticks, 1..20. |
| `float strength()` | Peak strength, (0, 1]. |
| `Effects.ImpactStyle style()` | Accent style. |

## Effects.ImpactStyle

`dev.calamidade_awakening.api.v1.Effects.ImpactStyle` · enum

Look of the impact accent.

| Membro | Descrição |
|---|---|
| `TINTED_FLASH` | Flash tinted with the definition color (hidden when the player hides lightning flashes). |
| `MONOCHROME` | Desaturation plus contrast (a monochrome impact frame). |

## Effects.Material

`dev.calamidade_awakening.api.v1.Effects.Material` · enum

Built-in volumetric material raymarched by the engine's shared pass (costs one of its marks).

| Membro | Descrição |
|---|---|
| `NONE` | No volume. |
| `CRYSTAL` | Faceted crystal shell with an inner grid. |
| `VEIL` | Translucent petals and strands. |
| `LENS` | Lens band with streams. |
| `NEBULA` | Noisy nebula halo. |

## Effects.Priority

`dev.calamidade_awakening.api.v1.Effects.Priority` · enum

Drawing priority of an effect (0.9.0), set with `Definition.withPriority`.

| Membro | Descrição |
|---|---|
| `NORMAL` | Default: competes with the other instances by intensity over distance. |
| `HERO` | Protagonist: chosen first, never cut by the effect limit (nearest 2 per frame), own pass budget. |

## Effects.Shape

`dev.calamidade_awakening.api.v1.Effects.Shape` · enum

Built-in world geometry drawn by the engine around the instance center.

| Membro | Descrição |
|---|---|
| `NONE` | No built-in geometry (use AddonLayers.world or post passes). |
| `PRISM` | Spinning prism shards. |
| `RINGS` | Concentric rings. |
| `ORBIT` | Orbiting ribbons. |
| `NEXUS` | Curved blades that converge (Horizon Core style). |

## Light

`dev.calamidade_awakening.api.v1.Light` · class

Immutable visual light of one effect (0.9.0): it brightens the blocks, entities and terrain on screen around a `Point`, along a segment (optionally the segment of one of the effect's beams) or over a disc or a ring on the ground (shock waves).

| Membro | Descrição |
|---|---|
| `static int MAX_PER_EFFECT` | Most lights per effect (`Effects.lights`). |
| `static float MAX_RADIUS` | Largest reach (point and segment) or disc/ring radius, blocks. |
| `static float MAX_INTENSITY` | Largest intensity multiplier. |
| `static float MAX_TRACK_OFFSET` | Largest track offset, blocks per axis. |
| `static float ACTIVE_WEIGHT` | Weight below which a light is not drawn. |
| `static Light.Builder point(Point at)` | Point light at `at`. |
| `static Light.Builder segment(Point from, Point to)` | Light along the segment from `from` to `to` (different points). |
| `static Light.Builder along(Beam beam)` | Light along a beam of the same effect (register the beams before the lights): its ends, reveal and weight, and its halo color unless `Builder.color` is called. |
| `static Light.Builder disc(Point center, float radius)` | Disc of light on the ground: `radius` blocks (0..`MAX_RADIUS`) around `center`; soft edge 25% of it. |
| `static Light.Builder ring(Point center, float radius, float width)` | Ring of light on the ground: a band `width` blocks wide (0..64) at `radius` blocks from `center`. |
| `Light.Shape shape()` | Shape of the source. |
| `Point from()` | Position (point), start (segment) or center (disc, ring). |
| `Point to()` | End of a segment, or null for the other shapes. |
| `Beam beam()` | Beam this light follows (`along(Beam)`), or null. |
| `int color()` | Light color, ARGB (alpha ignored); for `along(Beam)` without an explicit color, the beam's halo color. |
| `float intensity()` | Brightness multiplier, 0..`MAX_INTENSITY`. |
| `float radius()` | Reach (point, segment) or disc/ring radius, blocks. |
| `float falloff()` | Attenuation exponent (point, segment). |
| `float width()` | Ring band width or disc edge softness, blocks. |
| `float height()` | Vertical reach of a disc or ring, blocks. |
| `float flicker()` | Flicker amount, 0..1. |
| `float flickerSpeed()` | Flicker speed, changes per second. |
| `String track(Light.Param param)` | Track bound to a parameter, or null when the parameter is constant. |
| `Map<Light.Param, String> tracks()` | Every track binding, in `Param` order. |
| `String weightPhase()` | Phase whose weight multiplies the light weight, or null. |
| `boolean animated()` | True when some parameter, the weight or the phase depends on the timeline. |
| `void validate(Timeline timeline)` | Checks that every track and the phase this light reads exist in `timeline`, which may be null only when nothing is animated. |
| `Light.State sample(Timeline.Sampler timeline, float envelope, double seconds, float seed, float flickerScale)` | Resolves this light at one moment. |
| `static float flickerFactor(float amount, float speed, double seconds, float seed)` | Flicker multiplier of the intensity, pure: `1 - amount * n`, with `n` a smooth value noise in 0..1 that changes about `speed` times per second, offset by `seed`. 1 when amount or speed is 0; always within `[1 - amount, 1]`. |

## Light.Builder

`dev.calamidade_awakening.api.v1.Light.Builder` · class

Builder of a `Light`.

| Membro | Descrição |
|---|---|
| `Light.Builder color(int argb)` | Light color (ARGB, alpha ignored); white by default, the halo color for `Light.along(Beam)`. |
| `Light.Builder intensity(float value)` | Brightness multiplier, 0..`MAX_INTENSITY` (1 by default). |
| `Light.Builder radius(float blocks)` | Reach of a point or segment light, or the radius of a disc or ring, in blocks (0..`MAX_RADIUS`). |
| `Light.Builder falloff(float exponent)` | Attenuation exponent of a point or segment light, 0.25..4 (2 by default). |
| `Light.Builder width(float blocks)` | Ring band width or disc edge softness, blocks (0..64). |
| `Light.Builder height(float blocks)` | Vertical reach of a disc or ring above and below its center, blocks (0.25..32, 3 by default). |
| `Light.Builder flicker(float amount, float speed)` | Flicker: `amount` 0..1 of the intensity, about `speed` changes per second (0..20). |
| `Light.Builder track(Light.Param param, String track)` | Takes `param` from a track of the effect's timeline instead of the constant (clamped to the parameter range). |
| `Light.Builder weightPhase(String phase)` | Multiplies the light weight by the weight of a timeline phase (0 outside it). |
| `Light build()` | Immutable light. |

## Light.Param

`dev.calamidade_awakening.api.v1.Light.Param` · enum

Numeric parameters that can be driven by a timeline track, each with the range used to clamp it.

| Membro | Descrição |
|---|---|
| `INTENSITY` | Brightness multiplier. |
| `RADIUS` | Reach (point, segment) or disc/ring radius, blocks. |
| `FALLOFF` | Exponent of the distance attenuation (point, segment); 1 linear, 2 soft (default), 4 tight. |
| `WIDTH` | Ring band width or disc edge softness, blocks. |
| `HEIGHT` | Vertical reach of a disc or ring above and below its center, blocks. |
| `FLICKER` | Flicker amount, 0..1 (0 steady, 1 may go fully dark). |
| `FLICKER_SPEED` | Flicker speed, changes per second. |
| `X` | Offset added to the position (all ends and the center), x, blocks. |
| `Y` | Offset added to the position, y (up), blocks. |
| `Z` | Offset added to the position, z (forward in the instance frame), blocks. |
| `WEIGHT` | Extra multiplier of the light weight, 0..1 (the envelope and the phase still apply). |
| `float min` | Smallest and largest value of the parameter; track values are clamped to this range. |
| `float max` | Smallest and largest value of the parameter; track values are clamped to this range. |
| `float clamp(float value)` | Clamps a value into this parameter's range; NaN becomes the value closest to 0 in the range. |

## Light.Shape

`dev.calamidade_awakening.api.v1.Light.Shape` · enum

Shape of the light source.

| Membro | Descrição |
|---|---|
| `POINT` | A point: lights in every direction within `radius`. |
| `SEGMENT` | A segment (or a beam): lights from its nearest point within `radius`. |
| `DISC` | A disc on the ground: `radius` wide with a soft edge `width` blocks wide. |
| `RING` | A ring on the ground: a band `width` blocks wide at `radius` from the center. |

## Light.State

`dev.calamidade_awakening.api.v1.Light.State` · record

Resolved light at one moment: parameters taken from tracks and clamped, flicker applied to the intensity, and the weight (0..1).

| Membro | Descrição |
|---|---|
| `new State(float weight, float x, float y, float z, int color, float intensity, float radius, float falloff, float width, float height)` | Canonical constructor: weight, x, y, z, color, intensity, radius, falloff, width, height. |
| `boolean visible()` | True when the engine draws this light: weight above `ACTIVE_WEIGHT`, lit and with some size. |
| `float weight()` | Light weight, 0..1 (for `along(Beam)`, times the beam weight on the client). |
| `float x()` | Track offset, x, blocks. |
| `float y()` | Track offset, y, blocks. |
| `float z()` | Track offset, z, blocks. |
| `int color()` | Light color, ARGB (alpha ignored). |
| `float intensity()` | Brightness multiplier after flicker, 0..`MAX_INTENSITY`. |
| `float radius()` | Reach (point, segment) or disc/ring radius, blocks. |
| `float falloff()` | Attenuation exponent. |
| `float width()` | Ring band width or disc edge, blocks. |
| `float height()` | Vertical reach of a disc or ring, blocks. |

## Placement

`dev.calamidade_awakening.api.v1.Placement` · class

Where and how an instance starts (0.10.0): a fixed position or an anchor entity, an orientation (yaw and pitch), the player who cast it, its source, an optional cooldown per source and an optional block entity that bounds its life.

| Membro | Descrição |
|---|---|
| `static Placement at(Vec3 position)` | Fixed world position in blocks, no orientation (world axes). |
| `static Placement at(Vec3 position, float yaw, float pitch)` | Fixed world position in blocks, turned by `yaw` and `pitch` (degrees). |
| `static Placement on(Entity anchor)` | Anchored to an entity: the center follows the entity's feet. |
| `static Placement on(Entity anchor, Vec3 offset)` | Anchored to an entity with an offset from its feet, in blocks, in the instance frame (x left, y up, z forward; the world axes while the placement has no orientation, as in `Effects.follow`). |
| `Placement withOrientation(float yaw, float pitch)` | Copy with this orientation: yaw in degrees (any finite value, wrapped to [-180, 180)) and pitch in degrees ([-90, 90], positive looks down). |
| `Placement withYaw(float yaw)` | Copy with this yaw (degrees), keeping the pitch. |
| `Placement withLookOf(Entity entity)` | Copy with the yaw and pitch an entity is looking with (`getYRot()`, `getXRot()`). |
| `Placement facing(Vec3 target)` | Copy oriented to look at `target` (blocks): from the position, or from the anchor's eyes for anchored placements. |
| `Placement arrivingFrom(Vec3 origin)` | Copy oriented along the path of something that arrives here from `origin` (blocks): forward points from `origin` to the position (or to the anchor's eyes for anchored placements). |
| `Placement shifted(double right, double up, double forward)` | Copy moved by `right`, `up` and `forward` blocks in the placement's frame. |
| `Placement followingBody()` | Copy whose yaw follows the anchor's body every tick and every frame (0.10.0): the body yaw of a living entity (`yBodyRot`, interpolated on clients), or the entity's yaw for other entities, plus this placement's yaw as an offset. |
| `Placement withCaster(ServerPlayer caster)` | Copy cast by this player: their client plays the effect's camera (card shots or `AddonLayers.film`), cues receive them as `caster` and caster points resolve to them. |
| `Placement withSource(EffectSource source)` | Copy started by this source (0.10.0), for the per-source limits, the slots reserved for players and `withCooldown`. |
| `Placement withCooldown(int ticks)` | Copy with a cooldown per source and effect (0.10.0), in ticks, 0 (none) to 72000: the start is refused (empty Optional) while the cooldown of this `source()` for this effect runs, and an accepted start begins it. |
| `Placement boundTo(BlockEntity blockEntity)` | Copy whose life is bound to a block entity (0.10.0): the instance ends when the block entity is removed (the block is broken or replaced, or its chunk unloads). |
| `Vec3 position()` | World position in blocks (fixed placements), or the offset from the anchor's feet in the instance frame. |
| `Entity anchor()` | Anchor entity, or null for a fixed position. |
| `boolean anchored()` | True when anchored to an entity. |
| `float yaw()` | Yaw in degrees, [-180, 180); an offset added to the body yaw when `followBody()` is true. |
| `float pitch()` | Pitch in degrees, [-90, 90] (positive looks down). |
| `boolean followBody()` | True when the yaw follows the anchor's body (`followingBody`). |
| `ServerPlayer caster()` | Player who cast the instance, or null. |
| `EffectSource source()` | Source of the instance (0.10.0): the one given to `withSource`, else the bound block, else the caster, else the anchor (`EffectSource.entity`), else `EffectSource.script()`. |
| `int cooldown()` | Cooldown per source and effect in ticks, 0 for none (0.10.0). |
| `BlockEntity block()` | Block entity that bounds the instance's life, or null (0.10.0). |
| `String toString()` | Readable form with the position or anchor, orientation and options. |

## Point

`dev.calamidade_awakening.api.v1.Point` · class

Immutable place in the world used by `Beam` ends and `Light` positions (0.9.0).

| Membro | Descrição |
|---|---|
| `static double MAX_OFFSET` | Largest offset on each axis, in blocks. |
| `static double MAX_REACH` | Largest aim distance, in blocks. |
| `static double MAX_WORLD` | Largest horizontal world coordinate, in blocks (the world border). |
| `static Point center()` | The instance center. |
| `static Point center(double dx, double dy, double dz)` | Offset in blocks from the instance center, in the instance frame: `dx` to the left, `dy` up, `dz` forward (0.10.0). |
| `static Point facing(double right, double up, double forward)` | Offset from the instance center written as `right`, `up` and `forward` blocks in the instance frame (0.10.0). |
| `static Point world(double x, double y, double z)` | Fixed world position in blocks, the same for every instance (\|x\| and \|z\| up to `MAX_WORLD`, \|y\| up to `MAX_OFFSET`). |
| `static Point anchor(double dx, double dy, double dz)` | Feet of the anchor entity (`Effects.follow`) plus an offset in blocks. |
| `static Point anchorEye(double dx, double dy, double dz)` | Eyes of the anchor entity plus an offset in blocks. |
| `static Point anchorAim(double reach)` | First block hit along the anchor entity's look, up to `reach` blocks (0.5..`MAX_REACH`); the full reach when nothing is hit. |
| `static Point caster(double dx, double dy, double dz)` | Feet of the caster plus an offset in blocks. |
| `static Point casterEye(double dx, double dy, double dz)` | Eyes of the caster plus an offset in blocks. |
| `static Point casterAim(double reach)` | First block hit along the caster's look, up to `reach` blocks (0.5..`MAX_REACH`); the full reach when nothing is hit. |
| `Point.Source source()` | What the point is attached to. |
| `double x()` | Offset (or world coordinate for `Source.WORLD`) on x, blocks; 0 for aim points. |
| `double y()` | Offset (or world coordinate for `Source.WORLD`) on y, blocks; 0 for aim points. |
| `double z()` | Offset (or world coordinate for `Source.WORLD`) on z, blocks; 0 for aim points. |
| `double reach()` | Aim distance in blocks for `*_AIM` points; 0 for the others. |
| `boolean equals(Object o)` | Same source, offsets and reach. |
| `int hashCode()` | Hash of source, offsets and reach. |
| `String toString()` | Readable form, such as `CENTER(0.0, 40.0, 0.0)` or `CASTER_AIM(48.0)`. |

## Point.Source

`dev.calamidade_awakening.api.v1.Point.Source` · enum

What a point is attached to.

| Membro | Descrição |
|---|---|
| `CENTER` | Instance center plus offset in the instance frame (world axes without orientation). |
| `WORLD` | Fixed world position. |
| `ANCHOR` | Feet of the anchor entity (`Effects.follow`) plus offset. |
| `ANCHOR_EYE` | Eyes of the anchor entity plus offset. |
| `ANCHOR_AIM` | First block hit along the anchor entity's look, up to the reach. |
| `CASTER` | Feet of the caster plus offset. |
| `CASTER_EYE` | Eyes of the caster plus offset. |
| `CASTER_AIM` | First block hit along the caster's look, up to the reach. |
| `boolean entity()` | True when the point needs an entity (anchor or caster) loaded on the client. |
| `boolean caster()` | True for the caster's points. |
| `boolean aim()` | True for the look-ray points (`*_AIM`). |
| `boolean eye()` | True for the eye points (`*_EYE`). |

## Sky

`dev.calamidade_awakening.api.v1.Sky` · class

Immutable sky layer of one effect (0.12.0): a fantastic object painted on the sky by direction, such as a black hole, a portal opening onto another galaxy, an eclipse, a rift, a giant moon or planet, or an aurora.

| Membro | Descrição |
|---|---|
| `static int MAX_PER_EFFECT` | Most sky layers per effect (`Effects.sky`). |
| `static float MAX_SIZE` | Largest angular diameter, degrees. |
| `static float MAX_INTENSITY` | Largest intensity multiplier. |
| `static float MAX_SPIN` | Largest spin, degrees per tick (36 = one turn every half second). |
| `static float ACTIVE_WEIGHT` | Weight below which a layer is not painted. |
| `static double MAX_DISTANCE` | Largest distance of a center offset or world point coordinate from the origin of its frame, blocks. |
| `static float ECLIPSE_SUN_RATIO` | Ratio between the radius of the sun an `Type.ECLIPSE` draws and the radius of its covering disk. |
| `static float ECLIPSE_START_DISTANCE` | Distance between the centers of the eclipse disk and its sun at progress 0, in covering-disk radii. |
| `static float eclipseCoverage(float progress)` | Fraction of the sun hidden by an `Type.ECLIPSE` at `progress` (0..1, clamped): the overlap area of the covering disk (radius 1) and the sun (radius `ECLIPSE_SUN_RATIO`) whose centers are `(1 - progress) × ``ECLIPSE_START_DISTANCE` radii apart, over the sun's area. 0 at progress 0, 1 at totality (from progress ≈ 0.986 on). |
| `static Sky.Builder builder(Sky.Type type)` | New builder of a layer of `type`, with the defaults of that type (size, colors, progress, tilt, lens, darken and anchor: an `Type.ECLIPSE` starts on the sun, an `Type.AURORA` at azimuth 180 and elevation 12, the others at azimuth 0 and elevation 35). |
| `static Sky.Builder blackHole()` | `builder(Type.BLACK_HOLE)`: 40 degrees, tilt 80 (a wide disk seen almost edge-on), lens 0.6, spin 1.2 degrees per tick. |
| `static Sky.Builder portal()` | `builder(Type.PORTAL)`: 30 degrees, open, galaxy spin 0.5 degrees per tick. |
| `static Sky.Builder eclipse()` | `builder(Type.ECLIPSE)`: on the sun, 13 degrees, total, darken 0.75. |
| `static Sky.Builder rift()` | `builder(Type.RIFT)`: 50 degrees long, rotation 20, open. |
| `static Sky.Builder body()` | `builder(Type.CELESTIAL_BODY)`: 28 degrees, full. |
| `static Sky.Builder aurora()` | `builder(Type.AURORA)`: 140 degrees of azimuth, toward the north, curtains from 12 degrees up. |
| `static Sky.Builder bolts()` | `builder(Type.DISTANT_BOLTS)` (0.12.0): bolts all around (size 180), activity 0.6, white core and violet glow. |
| `Sky.Type type()` | What the layer paints. |
| `Sky.Anchor anchor()` | Where the layer is placed. |
| `double anchorX()` | Azimuth (degrees) for `Anchor.DIRECTION`, x (blocks) for `Anchor.WORLD` and `Anchor.CENTER`; 0 otherwise. |
| `double anchorY()` | Elevation (degrees) for `Anchor.DIRECTION`, y (blocks) for `Anchor.WORLD` and `Anchor.CENTER`; 0 otherwise. |
| `double anchorZ()` | z (blocks) for `Anchor.WORLD` and `Anchor.CENTER`; 0 otherwise. |
| `float size()` | Angular diameter, degrees. |
| `float rotation()` | Roll, degrees. |
| `float spin()` | Spin, degrees per tick. |
| `float tilt()` | Inclination, degrees. |
| `float intensity()` | Brightness multiplier, 0..`MAX_INTENSITY`. |
| `float progress()` | Per-type progress, 0..1. |
| `float lens()` | Lens strength, 0..1. |
| `float darken()` | World darkening, 0..1. |
| `int primary()` | Primary color, ARGB (alpha ignored). |
| `int secondary()` | Secondary color, ARGB (alpha ignored). |
| `boolean hidesVanilla()` | True when the layer hides the vanilla sun or moon it is anchored on while it is active (by default for every type anchored on the sun or the moon except `Type.RIFT`, `Type.AURORA` and `Type.DISTANT_BOLTS`). |
| `String track(Sky.Param param)` | Track bound to a parameter, or null when the parameter is constant. |
| `Map<Sky.Param, String> tracks()` | Every track binding, in `Param` order. |
| `String weightPhase()` | Phase whose weight multiplies the layer weight, or null. |
| `boolean animated()` | True when some parameter, the weight or the phase depends on the timeline. |
| `void validate(Timeline timeline)` | Checks that every track and the phase this layer reads exist in `timeline`, which may be null only when nothing is animated. |
| `Sky.State sample(Timeline.Sampler timeline, float envelope)` | Resolves this layer at one moment. |

## Sky.Anchor

`dev.calamidade_awakening.api.v1.Sky.Anchor` · enum

Where a layer is placed on the sky.

| Membro | Descrição |
|---|---|
| `DIRECTION` | Fixed azimuth and elevation (world axes: does not turn with the instance). |
| `WORLD` | Toward an absolute world point (blocks); far points barely move as the player walks. |
| `CENTER` | Toward a point relative to the instance center (blocks, instance frame: x left, y up, z forward). |
| `SUN` | On the vanilla sun, following the time of day. |
| `MOON` | On the vanilla moon (opposite the sun). |

## Sky.Builder

`dev.calamidade_awakening.api.v1.Sky.Builder` · class

Builder of a `Sky` layer.

| Membro | Descrição |
|---|---|
| `Sky.Builder direction(float azimuth, float elevation)` | Fixed direction: `azimuth` in degrees (0 = south, 90 = west, 180 = north, -90 = east; any value, wrapped) and `elevation` in degrees above the horizon, -90..90. |
| `Sky.Builder toward(double x, double y, double z)` | Toward an absolute world point (blocks, each coordinate within ±`MAX_DISTANCE`). |
| `Sky.Builder centerOffset(double x, double y, double z)` | Toward a point relative to the instance center (blocks, instance frame: x left, y up, z forward; each within ±`MAX_DISTANCE`). |
| `Sky.Builder onSun()` | On the vanilla sun (it follows the time of day; the sun itself is hidden unless `hidesVanilla(boolean)` says no). |
| `Sky.Builder onMoon()` | On the vanilla moon (opposite the sun; the moon itself is hidden unless `hidesVanilla(boolean)` says no). |
| `Sky.Builder hidesVanilla(boolean hide)` | Whether the vanilla sun or moon this layer is anchored on is hidden while the layer is active (scaled by its weight). |
| `Sky.Builder size(float degrees)` | Angular diameter, degrees, 0..`MAX_SIZE`. |
| `Sky.Builder rotation(float degrees)` | Roll, degrees, -3600..3600. |
| `Sky.Builder spin(float degreesPerTick)` | Spin, degrees per tick over the real age, -`MAX_SPIN`..`MAX_SPIN`; stops with motion effects off. |
| `Sky.Builder tilt(float degrees)` | Inclination, degrees, -90..90 (disk of a black hole, ring of a portal, rings of a planet). |
| `Sky.Builder intensity(float value)` | Brightness multiplier, 0..`MAX_INTENSITY`. |
| `Sky.Builder progress(float value)` | Per-type progress, 0..1 (see `Type`); usually a track: `track(Param.PROGRESS, "abrir")`. |
| `Sky.Builder lens(float strength)` | Gravitational lens strength of a `Type.BLACK_HOLE`, 0..1 (0 = no bending). |
| `Sky.Builder darken(float amount)` | World darkening while the layer is active, 0..1 (an eclipse also scales it by the covered fraction). |
| `Sky.Builder color(int primary, int secondary)` | Primary and secondary colors (ARGB, alpha ignored); see `Type` for what each one paints. |
| `Sky.Builder track(Sky.Param param, String track)` | Takes `param` from a track of the effect's timeline instead of the constant (clamped to the parameter range). |
| `Sky.Builder weightPhase(String phase)` | Multiplies the layer weight by the weight of a timeline phase (0 outside it). |
| `Sky build()` | Immutable layer. |

## Sky.Direction

`dev.calamidade_awakening.api.v1.Sky.Direction` · record

Unit direction on the sky in world axes (x east, y up, z south: Minecraft's axes), with conversions to and from azimuth and elevation in degrees (Minecraft convention: azimuth 0 = south, 90 = west, 180 = north, -90 = east, the same as an entity yaw; elevation 90 = straight up, the opposite sign of a pitch).

| Membro | Descrição |
|---|---|
| `static Sky.Direction UP` | Straight up. |
| `new Direction(double x, double y, double z)` | Canonical constructor: x, y, z. |
| `static Sky.Direction of(double azimuth, double elevation)` | Direction of an azimuth and an elevation, degrees: `x = -sin(az) cos(el)`, `y = sin(el)`, `z = cos(az) cos(el)` (azimuth 90 points west, like an entity yaw of 90). |
| `static Sky.Direction toward(double fromX, double fromY, double fromZ, double toX, double toY, double toZ)` | Direction from a point toward another one (blocks); throws when they coincide. |
| `double azimuth()` | Azimuth in degrees, [-180, 180); 0 when the direction is vertical. |
| `double elevation()` | Elevation in degrees, -90..90. |
| `double angleTo(Sky.Direction other)` | Angle to another direction, degrees 0..180 (stable for tiny and for nearly opposite angles). |
| `Sky.Direction offset(double azimuth, double elevation)` | This direction moved by `azimuth` and `elevation` degrees (the elevation is kept within -90..90). |
| `double x()` | East component. |
| `double y()` | Up component. |
| `double z()` | South component. |

## Sky.Param

`dev.calamidade_awakening.api.v1.Sky.Param` · enum

Numeric parameters that can be driven by a timeline track, each with the range used to clamp it.

| Membro | Descrição |
|---|---|
| `AZIMUTH` | Degrees added to the anchor azimuth. |
| `ELEVATION` | Degrees added to the anchor elevation (the result is kept within -90..90). |
| `SIZE` | Angular diameter, degrees. |
| `ROTATION` | Roll, degrees. |
| `SPIN` | Spin, degrees per tick over the instance's real age. |
| `TILT` | Inclination, degrees. |
| `INTENSITY` | Brightness multiplier. |
| `PROGRESS` | Per-type progress (formation, opening, coverage, lit fraction, activity), 0..1. |
| `LENS` | Gravitational lens strength of a black hole, 0..1 (ignored by the other types). |
| `DARKEN` | World darkening while the layer is active, 0..1. |
| `WEIGHT` | Extra multiplier of the layer weight, 0..1 (the envelope and the phase still apply). |
| `float min` | Smallest and largest value of the parameter; track values are clamped to this range. |
| `float max` | Smallest and largest value of the parameter; track values are clamped to this range. |
| `float clamp(float value)` | Clamps a value into this parameter's range; NaN becomes the value closest to 0 in the range. |

## Sky.State

`dev.calamidade_awakening.api.v1.Sky.State` · record

Resolved layer at one moment: parameters already taken from tracks and clamped, plus the weight (0..1).

| Membro | Descrição |
|---|---|
| `new State(Sky.Type type, float weight, float azimuth, float elevation, float size, float rotation, float spin, float tilt, float intensity, float progress, float lens, float darken, int primary, int secondary)` | Canonical constructor: type, weight, azimuth, elevation, size, rotation, spin, tilt, intensity, progress, lens, darken, primary, secondary. |
| `boolean visible()` | True when the engine paints this layer: weight above `ACTIVE_WEIGHT`, a size and some brightness. |
| `Sky.Direction direction(Sky.Direction anchor)` | Anchor direction moved by this state's azimuth and elevation offsets. |
| `float radius()` | Angular radius, degrees (`size / 2`). |
| `float darkening()` | World darkening this layer asks for, 0..1, before accessibility: `darken × weight`, and for an `Type.ECLIPSE` also times the fourth power of the covered fraction of the sun (`Sky.eclipseCoverage`), so the world only goes dark close to totality. |
| `double rotationAt(double realAge)` | Rotation in degrees after `realAge` ticks of spin, wrapped to [0, 360). |
| `Sky.Type type()` | What the layer paints. |
| `float weight()` | Layer weight, 0..1. |
| `float azimuth()` | Degrees added to the anchor azimuth. |
| `float elevation()` | Degrees added to the anchor elevation. |
| `float size()` | Angular diameter, degrees. |
| `float rotation()` | Roll, degrees (spin not included). |
| `float spin()` | Spin, degrees per tick. |
| `float tilt()` | Inclination, degrees. |
| `float intensity()` | Brightness multiplier. |
| `float progress()` | Per-type progress, 0..1. |
| `float lens()` | Lens strength, 0..1. |
| `float darken()` | World darkening, 0..1 (before `darkening()`). |
| `int primary()` | Primary color, ARGB (alpha ignored). |
| `int secondary()` | Secondary color, ARGB (alpha ignored). |

## Sky.Type

`dev.calamidade_awakening.api.v1.Sky.Type` · enum

What the layer paints.

| Membro | Descrição |
|---|---|
| `BLACK_HOLE` | Black disk (the shadow, a fifth of the size), a thin and very bright photon ring, a wide tilted accretion disk crossing the shadow from side to side with its far half bent over the top of the shadow by the lens, the sky around slightly darkened, and a gravitational lens that bends the sky and the terrain around it. |
| `PORTAL` | Energy ring that opens and reveals another sky inside: nebula, dense stars and a spinning spiral galaxy. |
| `ECLIPSE` | Disk that covers a sun (the vanilla one with `Builder.onSun()`, which is hidden and redrawn) with a corona and streamers at totality, or a reddened moon with `Builder.onMoon()`. |
| `RIFT` | Jagged crack across the sky with an inner glow and a darker void, small branches and flicker. |
| `CELESTIAL_BODY` | Lit sphere: a giant moon or planet with phases, a lit limb and, with a non-zero tilt, a ring system. |
| `AURORA` | Waving curtains of light along the horizon. |
| `DISTANT_BOLTS` | Distant lightning (0.12.0, "raios ao longe"): bolts and beams that fall far away, at random spots around each player (70 to 300 blocks), with a flash on the sky. |

## TerrainEdit

`dev.calamidade_awakening.api.v1.TerrainEdit` · class

Immutable terrain edit of one effect (0.11.0): an excavation (`TerrainShape`) with an optional burnt shell (`TerrainPalette`), real blocks placed in the middle (a `Fill` or a structure template), the moment of the commit (a marker of the effect's `Timeline`, normally the impact frame) and the client visuals that go with it: the cooling heat over the edited area and an optional falling stand-in (`StandIn`).

| Membro | Descrição |
|---|---|
| `static int MAX_PER_EFFECT` | Most terrain edits per effect (`Effects.terrain`). |
| `static int MAX_THICKNESS` | Thickest burnt shell, blocks. |
| `static int MAX_BLOCKS_PER_TICK` | Largest blocks-per-tick value an edit may ask for (the server config caps it too). |
| `static int MAX_COOLING` | Longest cooling, ticks. |
| `static float MAX_FILL_RADIUS` | Largest fill radius, blocks. |
| `static float MAX_OFFSET` | Largest offset of a fill, a structure or a stand-in start, blocks per axis. |
| `static TerrainEdit.Builder carve(TerrainShape shape)` | New edit that carves `shape`; call `Builder.commitAt` before `Builder.build`. |
| `TerrainShape shape()` | Excavation shape. |
| `String marker()` | Timeline marker of the commit. |
| `TerrainPalette palette()` | Burnt shell palette, or null for no shell. |
| `int thickness()` | Shell thickness, blocks (0 without a shell). |
| `TerrainEdit.Fill fill()` | Real blocks placed in the middle, or null. |
| `String structure()` | Structure template ID (`data/<namespace>/structure/<path>.nbt`), or null. |
| `float structureX()` | Structure offset to the left of the origin, blocks (instance frame snapped to 90°). |
| `float structureY()` | Structure offset up from the origin, blocks. |
| `float structureZ()` | Structure offset forward from the origin, blocks (instance frame snapped to 90°). |
| `int blocksPerTick()` | Blocks written per tick at the commit, or 0 for the server default (capped by the server config). |
| `boolean replacesBlockEntities()` | True when blocks with a block entity may be replaced (the server config must allow it too). |
| `boolean heat()` | True when the client draws the heat after the commit. |
| `int cooling()` | Ticks the heat takes to cool down without a heat track. |
| `int glow()` | Ember color of the heat, ARGB (alpha ignored). |
| `String heatTrack()` | Track giving the heat 0..1 after the commit, or null for the default cooling. |
| `float smoke()` | Smoke amount over the hot area, 0..1. |
| `float cracks()` | Strength of the glowing cracks, 0..1. |
| `TerrainEdit.StandIn standIn()` | Falling stand-in, or null. |
| `float heatAt(Timeline timeline, double localAge, double sinceCommit)` | Heat 0..1 at a local age, `sinceCommit` ticks after the commit (negative before it): 0 before the commit; then the `heatTrack()` value, or `(1 - sinceCommit / cooling)^1.6` down to 0. |
| `void validate(Timeline timeline, int duration)` | Checks the edit against the effect's timeline and duration: the commit marker and the tracks exist, the stand-in starts falling before the marker, and the structure stand-in has a structure. |
| `String toString()` | Readable form with the shape, the marker and the structure. |

## TerrainEdit.Builder

`dev.calamidade_awakening.api.v1.TerrainEdit.Builder` · class

Builder of a `TerrainEdit`.

| Membro | Descrição |
|---|---|
| `TerrainEdit.Builder commitAt(String marker)` | Timeline marker of the commit (required), normally the impact frame tick. |
| `TerrainEdit.Builder burn(TerrainPalette palette, int thickness)` | Burnt shell of `thickness` blocks (1..`MAX_THICKNESS`) around the excavation, from `palette`. |
| `TerrainEdit.Builder fill(TerrainEdit.Fill fill)` | Real blocks placed in the middle at the commit (see `Fill`). |
| `TerrainEdit.Builder structure(String id)` | Structure template placed at the commit, after the excavation: `id` is `data/<namespace>/structure/<path>.nbt` as `namespace:path`. |
| `TerrainEdit.Builder structureOffset(float right, float up, float forward)` | Moves the structure `right`, `up` and `forward` blocks (instance yaw snapped to 90°). |
| `TerrainEdit.Builder blocksPerTick(int blocks)` | Blocks written per tick at the commit, 1..`MAX_BLOCKS_PER_TICK` (0, the default, uses the server's; the server caps it). |
| `TerrainEdit.Builder replacingBlockEntities()` | Explicitly allows replacing blocks with a block entity (chests, furnaces, blocks of other mods): their contents are lost (saved in the undo snapshot). |
| `TerrainEdit.Builder cooling(int ticks)` | Cooling time of the heat without a track, 1..`MAX_COOLING` ticks (default 200). |
| `TerrainEdit.Builder heatTrack(String track)` | Heat after the commit from a timeline track (values 0..1) instead of the default cooling. |
| `TerrainEdit.Builder glow(int argb)` | Ember color of the heat, ARGB (alpha ignored; default 0xffff5a14). |
| `TerrainEdit.Builder smoke(float amount)` | Smoke amount over the hot area, 0..1 (default 0.5). |
| `TerrainEdit.Builder cracks(float amount)` | Strength of the glowing cracks, 0..1 (default 0.8). |
| `TerrainEdit.Builder noHeat()` | No heat visuals after the commit (the edit is invisible apart from the blocks). |
| `TerrainEdit.Builder standIn(TerrainEdit.StandIn standIn)` | Falling stand-in drawn by the client before the commit (see `StandIn`). |
| `TerrainEdit build()` | The edit; throws when the commit marker is missing. |

## TerrainEdit.Fill

`dev.calamidade_awakening.api.v1.TerrainEdit.Fill` · record

Real blocks placed at the commit, after the excavation: the stand-in becoming real (a meteor turning into a ball of ore).

| Membro | Descrição |
|---|---|
| `new Fill(TerrainEdit.FillKind kind, float radius, String block, float x, float y, float z)` | Canonical constructor: kind, radius, block, x, y, z. |
| `static TerrainEdit.Fill solid(String block, float radius)` | Solid ball of `block` with `radius` blocks at the origin. |
| `static TerrainEdit.Fill shell(String block, float radius)` | Hollow ball (one block thick) of `block` with `radius` blocks at the origin. |
| `TerrainEdit.Fill at(float right, float up, float forward)` | Copy centered `right`, `up` and `forward` blocks from the origin (instance frame). |
| `boolean contains(double px, double py, double pz)` | True when the local point (blocks, instance frame) is inside the fill. |
| `TerrainEdit.FillKind kind()` | Solid or hollow ball. |
| `float radius()` | Radius, blocks, (0, `MAX_FILL_RADIUS`]. |
| `String block()` | Registry ID of the block (`namespace:path`); a block with a block entity is placed without its data. |
| `float x()` | Center offset to the left, blocks. |
| `float y()` | Center offset up, blocks. |
| `float z()` | Center offset forward, blocks. |

## TerrainEdit.FillKind

`dev.calamidade_awakening.api.v1.TerrainEdit.FillKind` · enum

What a `Fill` places.

| Membro | Descrição |
|---|---|
| `SOLID` | A solid ball of the block. |
| `SHELL` | A hollow ball one block thick. |

## TerrainEdit.StandIn

`dev.calamidade_awakening.api.v1.TerrainEdit.StandIn` · class

Visual stand-in of a falling object (0.11.0): drawn by the client while it falls from `Builder.from` to the impact point, between `Builder.fall` and the commit marker (or along a 0..1 track), with a fire trail.

| Membro | Descrição |
|---|---|
| `static TerrainEdit.StandIn.Builder sphere(float radius)` | Burning ball of `radius` blocks, (0, `MAX_FILL_RADIUS`]; it lands on the fill center, or on the origin. |
| `static TerrainEdit.StandIn.Builder structure()` | The edit's structure template as a falling block mesh; it lands where the structure is placed. |
| `TerrainEdit.StandIn.Kind kind()` | Look of the stand-in. |
| `float radius()` | Ball radius, blocks (0 for a structure). |
| `float x()` | Start of the fall to the left of the impact point, blocks (instance frame). |
| `float y()` | Start of the fall above the impact point, blocks. |
| `float z()` | Start of the fall forward of the impact point, blocks (instance frame). |
| `int fall()` | Local tick when the fall starts (before the commit marker). |
| `int reveal()` | Ticks the stand-in takes to fade over the real blocks after the commit. |
| `int color()` | Fire color, ARGB (alpha ignored). |
| `float trail()` | Length of the fire trail, blocks (0 for none). |
| `String track()` | Track giving the fall progress 0..1 (eased by the track itself), or null for the default ease-in. |
| `float progress(Timeline timeline, double localAge, int commitTick)` | Fall progress 0..1 at a local age: the `track()` value when set, otherwise `t²` (accelerating) with `t` going from 0 at `fall()` to 1 at `commitTick`. |
| `float opacity(double localAge, int commitTick)` | Opacity 0..1 at a local age: 1 until the commit, then fading to 0 over `reveal()` ticks. |
| `String toString()` | Readable form, such as `StandIn[SPHERE]`. |

## TerrainEdit.StandIn.Builder

`dev.calamidade_awakening.api.v1.TerrainEdit.StandIn.Builder` · class

Builder of a `StandIn`.

| Membro | Descrição |
|---|---|
| `TerrainEdit.StandIn.Builder from(float right, float up, float forward)` | Start of the fall relative to the impact point: `right`, `up`, `forward` blocks in the instance frame (default straight above, 80 blocks), each within ±`MAX_OFFSET`. |
| `TerrainEdit.StandIn.Builder fall(int startTick)` | Local tick when the fall starts, 0..commit tick − 1 (default 0). |
| `TerrainEdit.StandIn.Builder track(String name)` | Fall progress from a timeline track (values 0..1) instead of the default ease-in. |
| `TerrainEdit.StandIn.Builder reveal(int ticks)` | Ticks of the fade over the real blocks after the commit, 0..400 (default 30). |
| `TerrainEdit.StandIn.Builder color(int argb)` | Fire color, ARGB (alpha ignored; default orange 0xffff8a2a). |
| `TerrainEdit.StandIn.Builder trail(float blocks)` | Length of the fire trail, 0..64 blocks (default 16). |
| `TerrainEdit.StandIn build()` | The stand-in. |

## TerrainEdit.StandIn.Kind

`dev.calamidade_awakening.api.v1.TerrainEdit.StandIn.Kind` · enum

Look of the stand-in.

| Membro | Descrição |
|---|---|
| `SPHERE` | A burning ball drawn by the engine's shader. |
| `STRUCTURE` | The edit's structure template as a moving block mesh. |

## TerrainEditEvent

`dev.calamidade_awakening.api.v1.TerrainEditEvent` · class

Posted once per `TerrainEdit` of an instance (0.11.0), on `NeoForge.EVENT_BUS` and the server thread, before any block changes: terrain protection mods (claims, spawn protection, regions) cancel it to refuse the edit.

| Membro | Descrição |
|---|---|
| `new TerrainEditEvent(ServerLevel level, ResourceLocation effect, int index, TerrainEdit edit, Vec3 origin, BoundingBox area, int blocks, EffectSource source, Entity entity)` | Event of one edit. |
| `ServerLevel level()` | Server level of the edit. |
| `ResourceLocation effect()` | Effect ID. |
| `int index()` | Index of the edit among the effect's edits (0-based). |
| `TerrainEdit edit()` | The edit (shape, shell, fill, structure). |
| `Vec3 origin()` | Edit origin, blocks (the instance center when planned). |
| `BoundingBox area()` | Box of every planned position, inclusive block coordinates. |
| `int blocks()` | Planned volume, blocks (carved, shell, fill and structure; air positions included). |
| `EffectSource source()` | Who started the instance (player, entity, block or script). |
| `Entity entity()` | The player or entity behind the source (caster, anchor or source entity), or null for blocks and scripts. |

## TerrainPalette

`dev.calamidade_awakening.api.v1.TerrainPalette` · class

Immutable "burnt" palette of a `TerrainEdit` (0.11.0): weighted block lists per band of the shell around the excavation.

| Membro | Descrição |
|---|---|
| `static int MAX_WEIGHT` | Largest weight of one entry. |
| `static int MAX_ENTRIES` | Most entries per band. |
| `static TerrainPalette.Builder builder()` | New empty builder; `Band.WALL` needs at least one entry. |
| `static TerrainPalette burnt()` | The default burnt palette, vanilla blocks only: floor of blackstone, coarse dirt, basalt, magma block and cobbled deepslate; walls of blackstone, cobblestone, tuff, andesite, cobbled deepslate and a little magma block; a rim of coarse dirt, cobblestone, andesite, tuff and blackstone. |
| `List<TerrainPalette.Entry> entries(TerrainPalette.Band band)` | Entries of a band (the `Band.WALL` entries when the band was left empty), immutable. |
| `int pick(TerrainPalette.Band band, int hash)` | Index into `entries(Band)` picked by weight from a hash (any int, for example of a block position). |
| `int pickAt(TerrainPalette.Band band, int x, int y, int z, long seed)` | Index picked for the block at world position (`x`, `y`, `z`) of an instance with this seed: `pick` of a `TerrainNoise` hash of the position and the band. |
| `boolean equals(Object o)` | Same entries in every band. |
| `int hashCode()` | Hash of the entries. |
| `String toString()` | Readable form with the entries of each band. |

## TerrainPalette.Band

`dev.calamidade_awakening.api.v1.TerrainPalette.Band` · enum

Band of the shell around the excavation.

| Membro | Descrição |
|---|---|
| `FLOOR` | Floor: carved space right above. |
| `WALL` | Walls and ceilings touching the carved space. |
| `RIM` | Outer ring at or above the origin level. |

## TerrainPalette.Builder

`dev.calamidade_awakening.api.v1.TerrainPalette.Builder` · class

Builder of a `TerrainPalette`.

| Membro | Descrição |
|---|---|
| `TerrainPalette.Builder add(TerrainPalette.Band band, String block, int weight)` | Adds `block` (registry ID) to a band with a relative weight, 1..`MAX_WEIGHT`. |
| `TerrainPalette build()` | The palette; throws when `Band.WALL` has no entry. |

## TerrainPalette.Entry

`dev.calamidade_awakening.api.v1.TerrainPalette.Entry` · record

One weighted block of a band.

| Membro | Descrição |
|---|---|
| `new Entry(String block, int weight)` | Canonical constructor: block, weight. |
| `String block()` | Registry ID of the block, `namespace:path`. |
| `int weight()` | Relative weight, 1..`MAX_WEIGHT`. |

## TerrainShape

`dev.calamidade_awakening.api.v1.TerrainShape` · class

Immutable excavation shape of a `TerrainEdit` (0.11.0): a sphere, a crater (a bowl with a configurable depth profile), a trench (a capsule along a segment, the cut of a beam), a cylinder, or a combination of them, with an optional rough edge from deterministic noise.

| Membro | Descrição |
|---|---|
| `static float MAX_SIZE` | Largest radius, depth or clearance of one primitive, blocks. |
| `static float MAX_LENGTH` | Largest trench length or cylinder height, blocks. |
| `static float MAX_OFFSET` | Largest offset of a primitive from the origin, blocks per axis. |
| `static int MAX_PRIMITIVES` | Most primitives in one combined shape (added and subtracted). |
| `static float MAX_EXTENT` | Largest bounding radius of a whole shape around the origin, blocks (offsets and rough edge included). |
| `static float MAX_ROUGHNESS` | Largest rough edge amplitude, blocks. |
| `static TerrainShape sphere(float radius)` | Ball of `radius` blocks, (0, `MAX_SIZE`], around the origin. |
| `static TerrainShape crater(float radius, float depth)` | Bowl of `radius` blocks whose floor is `depth` blocks below the origin at the center (both (0, `MAX_SIZE`]), parabolic profile (2) and clearance equal to the depth. |
| `static TerrainShape trench(float length, float radius)` | Capsule of `radius` blocks ((0, `MAX_SIZE`]) around a segment `length` blocks long ((0, `MAX_LENGTH`]) along local forward, centered on the origin: the cut a beam leaves in the ground. |
| `static TerrainShape cylinder(float radius, float height)` | Vertical cylinder of `radius` ((0, `MAX_SIZE`]) and `height` ((0, `MAX_LENGTH`]) blocks, centered. |
| `static TerrainShape union(TerrainShape first, TerrainShape... more)` | Union of shapes (at least one); each part must be without a rough edge (call `rough` on the result). |
| `TerrainShape union(TerrainShape other)` | This shape plus `other` (see TerrainShape...)). |
| `TerrainShape minus(TerrainShape cut)` | This shape with `cut` carved out of it: blocks inside `cut` are left alone (for example a pillar spared in the middle of a crater). |
| `TerrainShape offset(float right, float up, float forward)` | Copy moved by `right`, `up` and `forward` blocks in the instance frame (each offset of the result within ±`MAX_OFFSET`). |
| `TerrainShape profile(float exponent)` | Copy of a single crater with another depth profile exponent, 1..16: 1 a cone, 2 a parabola (default), 8 or more a flat bottom with steep walls. |
| `TerrainShape clearance(float blocks)` | Copy of a single crater that also carves up to `blocks` above the origin, 0..`MAX_SIZE`. |
| `TerrainShape rough(float amplitude, float scale)` | Copy with a rough edge: the surface moves in and out by up to `amplitude` blocks (0..`MAX_ROUGHNESS`; 0 removes it) following two octaves of value noise whose cells are `scale` blocks wide (0.5..64). |
| `List<TerrainShape.Primitive> primitives()` | Primitives in order: the added ones and the subtracted ones (immutable). |
| `float roughness()` | Rough edge amplitude, blocks (0 for a smooth edge). |
| `float roughScale()` | Rough edge noise cell size, blocks (1 when smooth). |
| `double extent()` | Radius around the origin that contains the whole shape, rough edge included, blocks. |
| `double baseDistance(double x, double y, double z)` | Signed distance of a local point (blocks, instance frame) to the shape without the rough edge: the union of the added primitives minus the subtracted ones. |
| `double distance(double x, double y, double z, long seed)` | Signed distance of a local point (blocks, instance frame) to the shape, rough edge included: negative inside. |
| `double edge(double x, double y, double z, long seed)` | Rough edge term in [-1, 1] at a local point: `2 fbm(p / scale) - 1`. |
| `boolean equals(Object o)` | Same primitives and rough edge. |
| `int hashCode()` | Hash of the primitives and the rough edge. |
| `String toString()` | Readable form with the primitives and the rough edge. |

## TerrainShape.Kind

`dev.calamidade_awakening.api.v1.TerrainShape.Kind` · enum

Kind of a primitive.

| Membro | Descrição |
|---|---|
| `SPHERE` | Ball: `a` = radius. |
| `CRATER` | Bowl: `a` = radius, `b` = depth, `c` = profile exponent, `d` = clearance. |
| `TRENCH` | Capsule along local forward: `a` = radius, `b` = length. |
| `CYLINDER` | Vertical cylinder: `a` = radius, `b` = height. |

## TerrainShape.Primitive

`dev.calamidade_awakening.api.v1.TerrainShape.Primitive` · record

One primitive of a shape, in the instance frame.

| Membro | Descrição |
|---|---|
| `new Primitive(TerrainShape.Kind kind, boolean subtract, float x, float y, float z, float a, float b, float c, float d)` | Canonical constructor: kind, subtract, x, y, z, a, b, c, d. |
| `double distance(double px, double py, double pz)` | Signed distance of a local point (blocks, instance frame) to this primitive, without the rough edge. |
| `double bound()` | Radius of a ball around the primitive center that contains it, blocks. |
| `TerrainShape.Kind kind()` | Primitive kind. |
| `boolean subtract()` | True when the primitive is cut out of the added ones (`minus`). |
| `float x()` | Center offset to the left, blocks. |
| `float y()` | Center offset up, blocks. |
| `float z()` | Center offset forward, blocks. |
| `float a()` | First size (radius), blocks. |
| `float b()` | Second size (crater depth, trench length, cylinder height), blocks; 0 for a sphere. |
| `float c()` | Crater profile exponent (1..16); 0 for the other kinds. |
| `float d()` | Crater clearance above the origin, blocks; 0 for the other kinds. |

## Timeline

`dev.calamidade_awakening.api.v1.Timeline` · class

Immutable script of one effect (0.7.0): named phases with smooth weights, value tracks with keyframes, markers and, for ambient effects, a sustain loop that only ends with a release.

| Membro | Descrição |
|---|---|
| `static Timeline.Keyframe keyframe(int tick, float value, Timeline.Easing easing)` | Keyframe at `tick` (ticks) with `value`, reached through `easing`. |
| `static Timeline.Keyframe keyframe(int tick, float value)` | Keyframe reached linearly. |
| `static Timeline.Builder builder(int duration)` | Builder for a timeline of `duration` ticks (1..`Effects.MAX_TICKS`). |
| `static Timeline empty(int duration)` | Timeline with no phases, tracks or markers; what client layers see for effects registered without one. |
| `int duration()` | Duration in ticks (1..`Effects.MAX_TICKS`); must not exceed the effect definition. |
| `float weight(String phase, double age)` | Weight of a phase, 0..1: zero before `start` and from `end` on, a smooth rise over `rise` ticks, 1 on the plateau and a smooth fall over the last `fall` ticks. |
| `float progress(String phase, double age)` | Linear progress inside the phase: 0 at `start` (and before), 1 at `end` (and after). |
| `int start(String phase)` | First tick of a phase. |
| `int end(String phase)` | Tick where a phase ends (exclusive). |
| `boolean hasPhase(String name)` | True when a phase with this name exists. |
| `Set<String> phases()` | Phase names in declaration order. |
| `float value(String track, double age)` | Interpolated value of a track. |
| `boolean hasTrack(String name)` | True when a track with this name exists. |
| `Set<String> tracks()` | Track names in declaration order. |
| `List<Timeline.Keyframe> keyframes(String track)` | Copy of the keyframes of a track. |
| `int tick(String marker)` | Tick of a marker. |
| `boolean hasMarker(String name)` | True when a marker with this name exists. |
| `Set<String> markers()` | Marker names in declaration order. |
| `boolean sustained()` | True when the timeline has a sustain loop (the instance loops until released). |
| `int sustainStart()` | First tick of the loop, or -1 without sustain. |
| `int sustainEnd()` | Tick where the loop jumps back to `sustainStart()`, or -1 without sustain. |
| `int releaseStart()` | Local tick where the exit begins after a release (`sustainEnd()` by default), or -1 without sustain. |
| `double localAge(double realAge, double releaseAge)` | Converts the real age of an instance into the local age of this timeline. |
| `long endAge(long releaseAge, int duration)` | Real age at which a released instance reaches local age `duration`, that is, when it expires; `Long.MAX_VALUE` while not released. |
| `Timeline.Sampler at(double localAge)` | Sampler bound to a local age, for layers that read several phases and tracks at once. |

## Timeline.Builder

`dev.calamidade_awakening.api.v1.Timeline.Builder` · class

Builder of a `Timeline`; every call validates its arguments right away.

| Membro | Descrição |
|---|---|
| `Timeline.Builder phase(String name, int start, int end, int rise, int fall)` | Named phase over [start, end), with a smooth rise of `rise` ticks and fall of `fall` ticks. |
| `Timeline.Builder track(String name, Timeline.Keyframe... keyframes)` | Value track. |
| `Timeline.Builder marker(String name, int tick)` | Named tick in [0, duration); cues can be scheduled on it with String, Effects.Cue). |
| `Timeline.Builder sustain(int start, int end)` | Loop for ambient effects: on reaching `end` the local age returns to `start` until the instance is released. |
| `Timeline.Builder release(int start)` | Local tick where the exit starts after a release; defaults to the sustain end. |
| `Timeline build()` | Immutable timeline. |

## Timeline.Easing

`dev.calamidade_awakening.api.v1.Timeline.Easing` · enum

Curve of one keyframe segment.

| Membro | Descrição |
|---|---|
| `LINEAR` | Straight line. |
| `SMOOTH` | Smoothstep: slow at both ends. |
| `EASE_IN` | Cubic, slow start. |
| `EASE_OUT` | Cubic, slow end. |
| `EASE_IN_OUT` | Cubic, slow start and end. |
| `OVERSHOOT` | Back-out: passes about 10% beyond the target and settles on it. |
| `double apply(double t)` | Maps t in [0,1] (clamped) to the eased fraction; 0 maps to 0 and 1 to 1. |

## Timeline.Keyframe

`dev.calamidade_awakening.api.v1.Timeline.Keyframe` · record

One value on a track.

| Membro | Descrição |
|---|---|
| `new Keyframe(int tick, float value, Timeline.Easing easing)` | Canonical constructor: tick, value, easing. |
| `int tick()` | Local tick, 0..duration, strictly increasing along the track. |
| `float value()` | Finite value at that tick. |
| `Timeline.Easing easing()` | Curve of the segment that arrives at this keyframe. |

## Timeline.Sampler

`dev.calamidade_awakening.api.v1.Timeline.Sampler` · class

Read-only view of a timeline at one local age (client layers receive one per instance and frame).

| Membro | Descrição |
|---|---|
| `new Sampler(Timeline timeline, double localAge)` | Sampler of `timeline` at `localAge` ticks. |
| `Timeline timeline()` | The sampled timeline. |
| `double localAge()` | Local age in ticks, partial tick included. |
| `float weight(String phase)` | Phase weight at this age, 0..1 (`Timeline.weight`). |
| `float progress(String phase)` | Phase progress at this age, 0..1 (`Timeline.progress`). |
| `float value(String track)` | Track value at this age (`Timeline.value`). |
| `int tick(String marker)` | Tick of a marker. |
| `double since(String marker)` | Ticks since a marker (negative before it). |

## WorldEvents

`dev.calamidade_awakening.api.v1.WorldEvents` · class

Global events (0.12.0): an effect seen beyond its range, by every player of a dimension or of the whole server, in any dimension, including players who log in, change dimension or respawn while it runs (they get it in the right phase).

| Membro | Descrição |
|---|---|
| `static int MAX_PRIORITY` | Highest priority. |
| `static Optional<WorldEvents.Handle> start(ResourceLocation id, WorldEvents.Scope scope, ServerLevel origin, WorldEvents.Options options)` | Starts a global event of a registered effect. |
| `static Optional<WorldEvents.Handle> get(MinecraftServer server, ResourceLocation id)` | The active global event of an effect (DIMENSION or SERVER scope), if any; also after a restart. |
| `static List<WorldEvents.Handle> active(MinecraftServer server)` | Every active global event, oldest first. |

## WorldEvents.Handle

`dev.calamidade_awakening.api.v1.WorldEvents.Handle` · interface

Server-side control of a global event (or of the AREA instance).

| Membro | Descrição |
|---|---|
| `ResourceLocation id()` | Effect of the event. |
| `WorldEvents.Scope scope()` | Scope it was started with. |
| `boolean persistent()` | True when it survives a server restart. |
| `int priority()` | Priority, 0..`MAX_PRIORITY`. |

## WorldEvents.Options

`dev.calamidade_awakening.api.v1.WorldEvents.Options` · record

Options of a start: origin position (blocks; the origin level's spawn point when null), persistence and priority (0..`MAX_PRIORITY`, 50 by default).

| Membro | Descrição |
|---|---|
| `new Options(Vec3 position, boolean persistent, int priority)` | Canonical constructor: position, persistent, priority. |
| `static WorldEvents.Options defaults()` | Spawn point, not persistent, priority 50. |
| `WorldEvents.Options at(Vec3 position)` | Copy with another origin position (blocks). |
| `WorldEvents.Options persistent(boolean value)` | Copy that survives (true) or not (false) a server restart. |
| `WorldEvents.Options priority(int value)` | Copy with another priority, 0..`MAX_PRIORITY`. |
| `Vec3 position()` | Origin position in the origin level, blocks, or null for its spawn point. |
| `boolean persistent()` | Survives a server restart (DIMENSION and SERVER scopes). |
| `int priority()` | 0..`MAX_PRIORITY`: who stays when the limit is full; also drawn first. |

## WorldEvents.Scope

`dev.calamidade_awakening.api.v1.WorldEvents.Scope` · enum

Who sees the event.

| Membro | Descrição |
|---|---|
| `AREA` | An ordinary instance at a position: players within the effect's range (what `Effects.start` does). |
| `DIMENSION` | Every player in the origin dimension. |
| `SERVER` | Every player on the server, in any dimension, on their own sky. |

## AddonLayers

`dev.calamidade_awakening.api.v1.client.AddonLayers` · class

Client-only callbacks of an effect: world mesh, HUD, Focus panel, post-processing passes, camera and start event.

| Membro | Descrição |
|---|---|
| `static void world(ResourceLocation effect, Consumer<AddonLayers.World> layer)` | Registers the world mesh of an effect (one per effect). |
| `static void hud(ResourceLocation effect, Consumer<AddonLayers.Hud> layer)` | Registers the HUD layer of an effect (one per effect). |
| `static void panel(ResourceLocation effect, Consumer<AddonLayers.Panel> layer)` | Registers the Focus panel of a published effect (one per effect). |
| `static void post(ResourceLocation effect, Consumer<AddonLayers.Post> layer)` | Registers the post-processing callback of an effect (one per effect); called for every visible instance. |
| `static void film(ResourceLocation effect, Function<AddonLayers.Film, List<Shot>> shots)` | Registers the custom camera of an effect (one per effect); replaces the card's shots. |
| `static void start(ResourceLocation effect, Consumer<AddonLayers.Start> listener)` | Registers the start listener of an effect (one per effect). |
| `static Consumer<AddonLayers.World> world(String id)` | Engine lookup: world layer registered for an effect ID string, or null. |
| `static Consumer<AddonLayers.Hud> hud(String id)` | Engine lookup: HUD layer for an effect ID string, or null. |
| `static Consumer<AddonLayers.Panel> panel(String id)` | Engine lookup: Focus panel for an effect ID string, or null. |
| `static Consumer<AddonLayers.Post> post(String id)` | Engine lookup: post callback for an effect ID string, or null. |
| `static Function<AddonLayers.Film, List<Shot>> film(String id)` | Engine lookup: camera callback for an effect ID string, or null. |
| `static Consumer<AddonLayers.Start> start(String id)` | Engine lookup: start listener for an effect ID string, or null. |

## AddonLayers.Film

`dev.calamidade_awakening.api.v1.client.AddonLayers.Film` · record

Custom camera (0.6.0), built on the caster's client when the instance starts.

| Membro | Descrição |
|---|---|
| `new Film(Subject subject, Vec3 center, int duration, long instance)` | 0.6 form, kept for compatibility: empty timeline of the given duration. |
| `new Film(Subject subject, Vec3 center, int duration, long instance, Timeline timeline)` | Canonical constructor: subject, center, duration, instance, timeline. |
| `Subject subject()` | RimuLib camera subject created by the engine for the caster. |
| `Vec3 center()` | World center of the instance, blocks. |
| `int duration()` | Effect duration, ticks. |
| `long instance()` | Instance identifier. |
| `Timeline timeline()` | Effect timeline (empty when none was registered). |

## AddonLayers.Hud

`dev.calamidade_awakening.api.v1.client.AddonLayers.Hud` · record

HUD drawing for one effect per frame (the strongest visible instance), in GUI coordinates.

| Membro | Descrição |
|---|---|
| `new Hud(GuiGraphics graphics, AddonLayers.Time time)` | Canonical constructor: graphics, time. |
| `Timeline.Sampler timeline()` | Timeline sampler of the instance (0.7.0). |
| `GuiGraphics graphics()` | GUI graphics of the frame. |
| `AddonLayers.Time time()` | Clock of the instance. |

## AddonLayers.Panel

`dev.calamidade_awakening.api.v1.client.AddonLayers.Panel` · record

Drawing over the Focus menu while the effect's card is selected (explain parameters there).

| Membro | Descrição |
|---|---|
| `new Panel(GuiGraphics graphics, float partial)` | Canonical constructor: graphics, partial. |
| `GuiGraphics graphics()` | GUI graphics. |
| `float partial()` | Partial tick, 0..1. |

## AddonLayers.Post

`dev.calamidade_awakening.api.v1.client.AddonLayers.Post` · class

Post-processing access for one visible instance (0.6.1).

| Membro | Descrição |
|---|---|
| `new Post(FxApi.Frame frame, AddonLayers.Time time, Vec3 center, double distance, double age, long instance, Quality quality, boolean flashAllowed, int passBudget)` | Engine use (0.6 form, kept for compatibility: local and real age are equal). |
| `new Post(FxApi.Frame frame, AddonLayers.Time time, Vec3 center, double distance, double age, double realAge, long instance, Quality quality, boolean flashAllowed, int passBudget)` | Engine use (0.7 to 0.9 form, kept for compatibility: no orientation). |
| `new Post(FxApi.Frame frame, AddonLayers.Time time, Vec3 center, double distance, double age, double realAge, long instance, Quality quality, boolean flashAllowed, int passBudget, float yaw, float pitch)` | Engine use (0.10.0): with the instance yaw and pitch of this frame, in degrees. |
| `float yaw()` | Instance yaw in degrees this frame, [-180, 180) (0.10.0); 0 without orientation. |
| `float pitch()` | Instance pitch in degrees this frame, [-90, 90], positive looks down (0.10.0). |
| `Vec3 facing(double right, double up, double forward)` | Offset in world axes (blocks) of `right`, `up` and `forward` in the instance frame (0.10.0). |
| `boolean pass(String program, Consumer<Uniforms> uniforms)` | Queues a shader pass from assets/&lt;namespace&gt;/shaders/fx. |
| `void uploadTracks(Uniforms uniforms, String prefix)` | Sends every track of the timeline, at the current local age, as a float uniform named `prefix + trackName` (0.7.0). |
| `int passesLeft()` | Passes that still fit in this frame's budget for this effect: the shared addon budget (1/2/4 for LOW/BALANCED/HIGH) or, for a HERO effect, the HERO budget (1/2/3). |
| `int used()` | Passes actually queued by this callback (diagnostics). |
| `void bloom(float amount)` | Bloom, 0..1 typical; combined by maximum and scaled by the screen-effects setting. |
| `void shake(float amount)` | Camera shake, 0..1 typical; combined by maximum and ignored when the player turns camera shake off. |
| `void desaturate(float amount)` | Desaturation, 0..1; combined by maximum and scaled by the screen-effects setting. |
| `void contrast(float amount)` | Extra contrast, 0..1 typical; combined by maximum and scaled by the screen-effects setting. |
| `void flash(int argb, float amount)` | Colored flash (ARGB), 0..1; ignored when the player hides lightning flashes, scaled by the screen-effects setting. |
| `void aberration(float amount)` | Chromatic aberration, 0..1 typical; combined by maximum and scaled by the screen-effects setting. |
| `void zoomBlur(float amount)` | Radial zoom blur, 0..1 typical; ignored when the player turns motion effects off. |
| `void vignette(int argb, float amount)` | Colored vignette (ARGB), 0..1; the strongest request keeps its color (ScreenFx.addVignette). |
| `void afterimage(float amount)` | Afterimage trail, 0..1 typical; ignored when the player turns motion effects off. |
| `void grain(float amount)` | Film grain, 0..1 typical; combined by maximum and scaled by the screen-effects setting. |
| `float partial()` | Partial tick of the frame, 0..1. |
| `AddonLayers.Time time()` | Clock of the instance (progress, opacity, seed, radius, timeline). |
| `Timeline.Sampler timeline()` | Timeline sampler of the instance (0.7.0). |
| `Vec3 center()` | Instance center relative to the camera, in blocks (the space of scenePosAt in shaders). |
| `double distance()` | Distance from the camera to the center, in blocks. |
| `double age()` | Local age in ticks including the partial tick (equal to the real age without sustain). |
| `double realAge()` | Real age in ticks including partial; differs from `age()` only for sustained timelines. |
| `long instance()` | Instance identifier (stable for its lifetime). |
| `Quality quality()` | Quality profile: LOW, BALANCED or HIGH; lower the raymarch steps on the smaller ones. |
| `boolean flashAllowed()` | False when the player hides lightning flashes: avoid strobes and sudden brightness changes. |
| `float screenEffects()` | Screen-effects intensity chosen by the player, 0..1 (0.8.0). |
| `boolean motionEffects()` | False when the player turned motion effects off (0.8.0): skip custom zoom, smear or shake passes. |

## AddonLayers.Start

`dev.calamidade_awakening.api.v1.client.AddonLayers.Start` · record

Fired once on every client that accepts an instance (0.6.0), including late arrivals (`age` &gt; 0).

| Membro | Descrição |
|---|---|
| `new Start(ClientLevel level, Vec3 center, long age, float seed, boolean caster, long instance)` | Canonical constructor: level, center, age, seed, caster, instance. |
| `ClientLevel level()` | Client level. |
| `Vec3 center()` | World center at the start, blocks. |
| `long age()` | Ticks since the server started it (0 for an on-time start, more for late arrivals). |
| `float seed()` | Instance seed, 0..1. |
| `boolean caster()` | True on the caster's client. |
| `long instance()` | Instance identifier. |

## AddonLayers.Time

`dev.calamidade_awakening.api.v1.client.AddonLayers.Time` · record

Clock of one instance.

| Membro | Descrição |
|---|---|
| `new Time(float progress, float opacity, float seed, double radius)` | 0.6 form, kept for compatibility: empty timeline sampler. |
| `new Time(float progress, float opacity, float seed, double radius, Timeline.Sampler timeline)` | Canonical constructor: progress, opacity, seed, radius, timeline. |
| `float progress()` | Local age / duration, 0..1. |
| `float opacity()` | Engine envelope (fade in and out), 0..1, raised by the impact accent. |
| `float seed()` | Instance seed, 0..1. |
| `double radius()` | Definition radius, blocks. |
| `Timeline.Sampler timeline()` | Sampler of the effect's `Timeline` at the local age (0.7.0); an empty timeline whose local age is the real age when none was registered. |

## AddonLayers.World

`dev.calamidade_awakening.api.v1.client.AddonLayers.World` · class

Additive world mesh of one instance, drawn after particles in one batch with the engine geometry (no depth write, not fogged, so it reads as emissive light).

| Membro | Descrição |
|---|---|
| `new World(Mesh mesh, Vec3 origin, AddonLayers.Time time)` | Engine use (0.6 form, kept for compatibility: no orientation). |
| `new World(Mesh mesh, Vec3 origin, AddonLayers.Time time, float yaw, float pitch)` | Engine use (0.10.0): with the instance yaw and pitch of this frame, in degrees. |
| `float yaw()` | Instance yaw in degrees this frame, [-180, 180) (0.10.0); 0 without orientation. |
| `float pitch()` | Instance pitch in degrees this frame, [-90, 90], positive looks down (0.10.0). |
| `Vec3 facing(double right, double up, double forward)` | Offset in world axes (blocks) of `right`, `up` and `forward` in the instance frame (0.10.0), to pass to `triangle` and `ribbon`. |
| `AddonLayers.Time time()` | Clock of the instance. |
| `Timeline.Sampler timeline()` | Timeline sampler of the instance (0.7.0). |
| `int remainingVertices()` | Vertices left in this instance's share of the frame budget. |
| `void triangle(Vec3 a, Vec3 b, Vec3 c, int argb)` | One triangle (3 vertices); points are offsets in blocks from the center, color ARGB. |
| `void ribbon(Vec3 a, Vec3 b, Vec3 side, double width, int argb)` | Flat ribbon from `a` to `b` (offsets in blocks), `width` blocks wide along `side`; 6 vertices. |

