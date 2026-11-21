# Referência da API pública (api.v1) — Calamidade_Awakening 0.10.0

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
- [`Timeline`](#timeline)
- [`Timeline.Builder`](#timelinebuilder)
- [`Timeline.Easing`](#timelineeasing)
- [`Timeline.Keyframe`](#timelinekeyframe)
- [`Timeline.Sampler`](#timelinesampler)
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
| `static void cue(ResourceLocation id, int tick, Effects.Cue cue)` | Schedules a server callback at `tick` (0 .. duration-1) of every instance of a registered external effect. |
| `static void cue(ResourceLocation id, String marker, Effects.Cue cue)` | Schedules a cue on a marker of the effect's timeline (0.7.0). |
| `static void onImpact(ResourceLocation id, Effects.Cue cue)` | Schedules a cue at the definition's `Impact` tick, keeping visual contact and gameplay in sync. |
| `static Optional<Effects.Handle> start(ResourceLocation id, ServerLevel level, Vec3 position)` | Starts an instance at a fixed world position (blocks). |
| `static Optional<Effects.Handle> start(ResourceLocation id, ServerLevel level, Vec3 position, float yaw, float pitch)` | Starts an instance at a fixed world position (blocks) turned by `yaw` and `pitch` (degrees, Minecraft convention: yaw 0 faces south, pitch positive looks down, within [-90, 90]) (0.10.0). |
| `static Optional<Effects.Handle> start(ResourceLocation id, ServerPlayer caster, Vec3 position)` | Starts an instance at a position for a caster: the caster's client plays the effect's camera (card shots or `AddonLayers.film`) and cues receive the player as `caster`. |
| `static Optional<Effects.Handle> start(ResourceLocation id, ServerPlayer caster, Vec3 position, float yaw, float pitch)` | Starts an instance for a caster at a position turned by `yaw` and `pitch` in degrees (0.10.0); see ServerLevel, Vec3, float, float). |
| `static Optional<Effects.Handle> start(ResourceLocation id, ServerLevel level, Placement placement)` | Starts an instance from a `Placement` (0.10.0): fixed position or anchor entity, yaw and pitch, the yaw optionally following the anchor's body, and the caster. |
| `static Optional<Effects.Handle> follow(ResourceLocation id, Entity entity, Vec3 offset)` | Starts an instance anchored to a server entity: the center follows the entity's feet plus `offset` (world axes, blocks). |
| `static Optional<Effects.Handle> follow(ResourceLocation id, Entity entity, Vec3 offset, boolean followBody)` | Starts an instance anchored to a server entity whose yaw follows the entity's body when `followBody` is true (0.10.0): the body yaw of a living entity, interpolated on clients, or the yaw of other entities. |

## Effects.Context

`dev.calamidade_awakening.api.v1.Effects.Context` · record

Server-side moment of a live instance, delivered to a `Cue` on the server thread.

| Membro | Descrição |
|---|---|
| `new Context(ServerLevel level, Vec3 center, ServerPlayer caster, Entity anchor, long seed, int tick, Effects.Handle handle)` | 0.6 form, kept for compatibility: no timeline, local and real age equal to the tick. |
| `new Context(ServerLevel level, Vec3 center, ServerPlayer caster, Entity anchor, long seed, int tick, Effects.Handle handle, Timeline timeline, double localAge, long age)` | 0.7 to 0.9 form, kept for compatibility: no orientation (yaw 0, pitch 0, the world axes). |
| `new Context(ServerLevel level, Vec3 center, ServerPlayer caster, Entity anchor, long seed, int tick, Effects.Handle handle, Timeline timeline, double localAge, long age, float yaw, float pitch)` | Canonical constructor: level, center, caster, anchor, seed, tick, handle, timeline, localAge, age, yaw, pitch. |
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

Where and how an instance starts (0.10.0): a fixed position or an anchor entity, an orientation (yaw and pitch) and the player who cast it.

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
| `Placement shifted(double right, double up, double forward)` | Copy moved by `right`, `up` and `forward` blocks in the placement's frame. |
| `Placement followingBody()` | Copy whose yaw follows the anchor's body every tick and every frame (0.10.0): the body yaw of a living entity (`yBodyRot`, interpolated on clients), or the entity's yaw for other entities, plus this placement's yaw as an offset. |
| `Placement withCaster(ServerPlayer caster)` | Copy cast by this player: their client plays the effect's camera (card shots or `AddonLayers.film`), cues receive them as `caster` and caster points resolve to them. |
| `Vec3 position()` | World position in blocks (fixed placements), or the offset from the anchor's feet in the instance frame. |
| `Entity anchor()` | Anchor entity, or null for a fixed position. |
| `boolean anchored()` | True when anchored to an entity. |
| `float yaw()` | Yaw in degrees, [-180, 180); an offset added to the body yaw when `followBody()` is true. |
| `float pitch()` | Pitch in degrees, [-90, 90] (positive looks down). |
| `boolean followBody()` | True when the yaw follows the anchor's body (`followingBody`). |
| `ServerPlayer caster()` | Player who cast the instance, or null. |
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

