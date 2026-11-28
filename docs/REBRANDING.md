# Rebranding preparation (0.15.0 phase 5)

The project will get a new name. **No name has been chosen and nothing has been renamed yet.** This document records
where the current identity lives, what can change safely and what must stay stable, so the future rename is a
deliberate, checked operation rather than a search-and-replace.

The identity splits in two:

- **Presentation identity**: what people read. It can change without breaking worlds, addons or configs.
- **Technical identity**: what code, worlds, configs, networks and other mods depend on. Changing it is a breaking
  migration.

## 1. Current branding locations

### Presentation identity

| What | Where | Current value |
|---|---|---|
| Display name in the mod list | `src/main/resources/META-INF/neoforge.mods.toml`, `displayName` | `Calamidade_Awakening` |
| Mod description | same file, `description` | "Modular procedural visual effects…" |
| Display name in Java messages | `CalamidadeAwakening.NAME` (phase 5: the one Java constant; used by chat prefixes, F7 title fallback, log warning) | `Calamidade_Awakening` |
| Display name in translations | `assets/calamidade_awakening/lang/en_us.json` (and `pt_br.json`): `key.categories.calamidade_awakening`, `gui.calamidade_awakening.void.title`, `perf.calamidade_awakening.title`, `calamidade_awakening.configuration.title`, `perf.calamidade_awakening.profile`, `perf.calamidade_awakening.mode.auto`, `perf.calamidade_awakening.defaults` | `Calamidade_Awakening` in the **values** |
| Config comments | `config/ClientConfig.java`, `config/ServerConfig.java` (comment text only) | mentions "Calamidade_Awakening" |
| Star Atlas panel | `atlas.calamidade_awakening.title` and related values | "Star Atlas" (feature name, not the mod name) |
| Logo and branding art | none: the manifest has no `logoFile`; no logo asset in `assets/` | — |
| README, AGENTS.md, docs titles | `README.md`, `AGENTS.md`, `CLAUDE.md`, `docs/**` | "Calamidade_Awakening", "Calamidade" |
| Skill description | `.claude/skills/calamidade-efeitos/SKILL.md` (front matter `description`) | mentions Calamidade_Awakening |
| GitHub repository name and description | `Juann027/Calamidade_Awakening-Mod-1.21.1` (remote, not in the tree) | — |
| Gradle root project name | `settings.gradle`, `rootProject.name` | `calamidade-awakening` (affects IDE and build output folder names, not the JAR) |
| Release workflow texts | `.github/workflows/release.yml`, `limpar-release.yml` (titles and notes) | — |

### Technical identity

| What | Where | Current value | Who depends on it |
|---|---|---|---|
| Mod ID | `CalamidadeAwakening.ID`, `@Mod`, `neoforge.mods.toml` `modId` and `[[dependencies.calamidade_awakening]]` | `calamidade_awakening` | every addon's `neoforge.mods.toml` dependency, NeoForge config file names, resource namespace, registries |
| Java packages | `dev.calamidade_awakening.*`; public API in `dev.calamidade_awakening.api.v1` and `api.v1.client` | — | every addon compiled against the API (binary compatibility, checked by `checkApiCompat`) |
| Maven group / archive name | `build.gradle`: `group = 'dev.calamidade_awakening'`, `base.archivesName = 'calamidade_awakening'` | JAR `calamidade_awakening-0.15.0.jar` | `examples/consumidor/build.gradle` and `examples/vfx-sample/build.gradle` (JAR file name and download URL), release workflows (`calamidade_awakening-*.jar` globs), `dist/` |
| Release download URL | `examples/consumidor/build.gradle` | `github.com/Juann027/Calamidade_Awakening-Mod-1.21.1/releases/download/v…/calamidade_awakening-….jar` | addons using the consumer template |
| Resource namespace | `src/main/resources/assets/calamidade_awakening/` (shaders `fx/`, textures, sounds, models, lang) | `calamidade_awakening:` | addon shaders that `#include` engine GLSL, resource packs, `Boss.SEGMENTED` and other `ResourceLocation` constants in the API |
| Registry IDs | attachment type `calamidade_awakening:boss` (`BossServer.ATTACHMENTS`), sounds in `sounds.json` | — | saved entities (boss state is serialized on the entity) |
| Network payload IDs | `calamidade_awakening:event_v2`, `request_v2`, `release`, `end_effect`, `live_v1`, `terrain_v1`, `world_event_v1`, `world_end_v1`, `sky_profile_v1`, `boss_v1`, `nexus` | — | client and server must match (same engine version on both) |
| SavedData keys | `calamidade_awakening_world_events` (`WorldEventData`), `calamidade_awakening_sky_profile` (`SkyProfileServer`) | files in `<world>/data/` | existing worlds: persistent global events and sky profile states |
| Config files | NeoForge derives them from the mod ID: `config/calamidade_awakening-client.toml`, `serverconfig/calamidade_awakening-server.toml` | — | players' saved settings, server operators |
| Translation keys | prefixes `*.calamidade_awakening.*` (e.g. `perf.calamidade_awakening.title`, `calamidade_awakening.configuration.*`) | — | resource packs and other translations |
| Commands | `/calamidade`, `/calamidade_client` | — | scripts, command blocks, datapacks, tests |
| Keybinding IDs | `key.calamidade_awakening.*`, category `key.categories.calamidade_awakening` | — | players' `options.txt` key bindings |
| Addon-facing references | addons use `dev.calamidade_awakening.api.v1` imports, `calamidade_awakening` dependency entries, engine `ResourceLocation`s and `calamidade_awakening:` shader includes; the Focus menu art path `calamidade_awakening:textures/gui/sprites/skill/focus/…` | — | third-party addons, `examples/vfx-sample`, `examples/consumidor` |
| Example addon IDs | `vfxsample` mod ID and `vfxsample:*` effect IDs (`vfxsample:luz_runas`, `vfxsample:receita_anel`, …) | — | tests, docs, bench commands (`CostProbe`, `BenchCommand` reference `vfxsample:receita_*`) |
| API reference baselines | `scripts/api-base/api-v1-0.12.0.txt`, `api-v1-0.13.0.txt` (class names with the package) | — | `checkApiCompat` |

## 2. Safe presentation-only rename

Changes only what people read; worlds, configs, addons and networks keep working.

1. Pick the new display name (a project decision, not part of this document).
2. Change `displayName` and `description` in `neoforge.mods.toml`.
3. Change `CalamidadeAwakening.NAME`.
4. Change the **values** (never the keys) of the translation entries listed above in `en_us.json` and any optional
   translation file.
5. Update comment text in `ClientConfig` and `ServerConfig` if desired (comments only; the entry names stay).
6. Update README, AGENTS.md, CLAUDE.md, docs titles and prose, the skill description and the release workflow texts.
   Keep commands, IDs, package names and file paths in code blocks exactly as they are.
7. Optionally change the GitHub repository description and add a logo (`logoFile` in the manifest plus an image in
   the JAR root).
8. Run `./gradlew build` (includes `checkApiCompat`, `checkApiDocs`, `checkRecipes`, `checkModVersion`), the focused
   in-game tests that cover menus and messages (`testAtlas`), and check F7, F8, the Atlas and the mod list by hand.

Renaming the GitHub repository is also presentation, **but** it changes the release download URL used by
`examples/consumidor`; GitHub redirects old URLs, but update the template in the same change.

## 3. Technical identifiers that must stay stable

Until a full technical rename is explicitly decided, do not change:

- the mod ID `calamidade_awakening`;
- the Java packages `dev.calamidade_awakening.*`, above all `api.v1` and `api.v1.client`;
- the resource namespace `calamidade_awakening` and every `ResourceLocation` in the API;
- registry, attachment, payload and SavedData identifiers;
- config entry names and the derived config file names;
- translation keys and keybinding IDs;
- the commands `/calamidade` and `/calamidade_client`;
- the Maven group and the archive name;
- `vfxsample` IDs used by tests and benchmarks.

## 4. Consequences of changing the mod ID or the Java packages

**Mod ID.**

- Every addon's `[[dependencies.calamidade_awakening]]` stops matching: NeoForge refuses to load them or loads them
  without the engine.
- The resource namespace moves: engine shaders, textures and sounds referenced as `calamidade_awakening:…` by addons or
  resource packs are no longer found; addon GLSL `#include`s of engine files break.
- Payload IDs change: old clients cannot join new servers (already true across engine versions, but now also within a
  version line).
- SavedData under the old keys is ignored: persistent global events and sky profile states of existing worlds are lost
  unless migrated.
- The boss attachment ID changes: bosses saved in existing worlds load without their controller.
- Config files get new names: every player's and server's settings reset to defaults.
- Translation keys and keybindings change: resource packs break and players lose custom key bindings.

**Java packages.**

- Binary and source incompatibility for every addon (`NoClassDefFoundError`, compile errors). `checkApiCompat` would
  fail against every baseline by design.
- Javadoc links, `docs/ai/API-REFERENCIA.md`, recipes and examples must be regenerated.
- Reflection in the in-game tests (`src/testeJogo`) and in scripts must be updated.

## 5. Migration requirements for a complete technical rename

If a full rename is requested later, it needs, at minimum:

1. A new major API line (for example `api.v2` under the new package) while the old package stays as deprecated
   delegating shims for at least one release, or an explicit decision to break all addons, announced in the release
   notes.
2. A compatibility layer for worlds: read the old SavedData keys (`calamidade_awakening_world_events`,
   `calamidade_awakening_sky_profile`) and write the new ones; register the old boss attachment ID as an alias or
   migrate it on entity load.
3. Config migration: on first start, copy values from `calamidade_awakening-client.toml` and
   `calamidade_awakening-server.toml` if the new files do not exist.
4. Resource aliases: keep the old namespace for shader includes and textures for at least one release, or document the
   break for addon authors.
5. Command aliases: keep `/calamidade` and `/calamidade_client` as aliases of the new commands (same pattern as the
   vfx-sample `Cmd` helper: shared nodes, one implementation).
6. New Maven coordinates and JAR names, with the consumer template, release workflows and `dist/` updated together;
   the old artifact stays downloadable.
7. A protocol bump: new payload IDs, documented as requiring the same version on server and client.
8. A migration guide for addon authors (dependency entry, imports, resource locations, shader includes).
9. Tests: `checkApiCompat` against new baselines, plus in-game tests that load a world saved by the old version
   (global events, sky profile, boss) and verify it survives.

## 6. Rebranding checklist

Presentation (safe):

- [ ] New name approved.
- [ ] `neoforge.mods.toml` `displayName` and `description`.
- [ ] `CalamidadeAwakening.NAME`.
- [ ] Translation **values** in `en_us.json` (and optional translations).
- [ ] Logo (optional) and `logoFile`.
- [ ] README, AGENTS.md, CLAUDE.md, docs titles, skill description.
- [ ] Release workflow texts, GitHub description.
- [ ] Repository rename (if wanted) plus the consumer download URL.
- [ ] `./gradlew build`, `testAtlas`, manual check of F7, F8, the Atlas, the mod list and the chat messages.

Technical (only after an explicit decision):

- [ ] Decision recorded: what changes (mod ID, packages, Maven, namespace) and in which version.
- [ ] Shims or announced break for `api.v1`.
- [ ] SavedData, attachment and config migrations, with tests on a world saved by the old version.
- [ ] Payload ID bump.
- [ ] Resource and command aliases.
- [ ] Maven coordinates, JAR names, consumer and vfx-sample builds, release workflows.
- [ ] Addon migration guide.
- [ ] Full test run (`build`, `testGame`, `testClient`, `testSky`, `testBoss`, `testLight`, `checkSkyReference`).
