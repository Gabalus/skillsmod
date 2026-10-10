# Epic Fight enemy and boss integration

## Required direction

Epic Fight is the core for authored enemy and boss movesets on NeoForge 1.21.1. It is also the intended Martial action provider. The base ARPG module may still load without external providers for development, but an encounter advertised as an Epic Fight encounter requires the validated Epic Fight runtime. GeckoLib is not the moveset foundation for these enemies; its presence in the Iron's Spells dependency family does not change that decision.

The existing full runtime profile still installs Better Combat. Treat it as a legacy convenience profile when `arpg_epic_runtime` is disabled. Enabling that flag selects Epic Fight instead of Better Combat. Do not combine both providers for the same melee action pipeline by default. The profile and first data patch below are now implemented. The first Martial ownership/stamina display bridge is implemented; guard/parry-to-posture translation remains pending.

## Ownership

| System | Responsibility |
| --- | --- |
| Epic Fight entity patch | Attack animation, attack-phase collision, combat behavior selection and supported hit reactions |
| Enemy/boss addon | Custom attacks, phase transitions, telegraphs, encounter targeting, cooldowns and arena mechanics through Epic Fight patches |
| ARPG core | Character progression, completion receipts, loot/recipe rewards, tower gates and encounter lifecycle |
| Martial bridge | Epic Fight owns stamina, guard and recovery; the character meter displays its stamina fraction. Outcome-to-posture translation remains pending. |

The bridge must reconcile Epic Fight stamina/stun semantics with ARPG stamina/posture. Do not run two independent player stamina budgets or apply one attack twice through Epic Fight and a second custom damage loop. The existing vanilla shield adapter is not proof of Epic Fight guard compatibility. Determine supported events and cancellation order against a pinned 1.21.1 release before writing that bridge.

## Martial ownership and stamina display

When Epic Fight is installed, it owns Martial combat in both Epic Fight and vanilla player modes. The vanilla ARPG shield guard adjustment, perfect-parry counter multiplier and Martial recovery loop are bypassed. Old vanilla guard windows are cleared on the next Martial tick. Toggling player mode cannot switch between two independent stamina budgets. Vanilla shields retain their ordinary provider/vanilla behavior without the ARPG guard modifier.

Every five server ticks, Martial players mirror `PlayerPatch.getStamina() / getMaxStamina()` onto the existing character screen stamina meter (0–100 by default). The display uses the provider fraction, including attribute changes, instead of presenting Epic Fight stamina points as ARPG points. Unchanged samples do not increment combat revision or send duplicate snapshots. Other pillar meters, posture and momentum remain unchanged. This bridge never writes provider stamina, applies health damage, cancels a provider event or grants a counter.

Access is optional and cached through reflection; a base build does not load Epic Fight classes. The exact pinned JAR's public entity-patch and stamina methods are checked during runtime verification. A missing player patch waits for a later sample. An API/invocation error is logged once and retains the last display snapshot; ownership stays with Epic Fight rather than re-enabling a second budget. Invalid non-finite samples or a non-positive maximum also retain the previous snapshot. The character display can lag the provider by up to five ticks. Saved display values are refreshed from the live provider after reconnect or respawn; they do not restore provider resources.

This is an ownership/display bridge, not a completed guard/parry integration. ARPG posture and momentum currently receive no Epic Fight guard outcomes or recovery. Rule/skill operations on the displayed ARPG stamina do not spend Epic Fight stamina and must not be used as authoritative Martial costs until a provider-backed spending adapter exists. Guard/parry event ordering, posture semantics, skill spending and live client/server behavior still require implementation and validation. If Epic Fight is removed between restarts, the existing normalized stamina meter resumes the vanilla adapter.

## Content workflow

Start ordinary enemies with compatible Epic Fight presets or explicit mob-patch data using existing animations. The documented location is `data/<entity namespace>/epicfight_mobpatch/<entity path>.json`; these patches select by entity type, not by our completion tag. A generic husk override would affect unrelated husks, so the authored Sentinel needs a dedicated entity type or a verified per-entity Java patch before it receives its unique moveset.

Use Java entity patches for boss behavior that exceeds the supported data predicates, including phase changes and custom arena attacks. Custom animation assets must use the Epic Fight model/armature pipeline and the pinned release's animation API. Animation playback alone must not award damage or progression.

Design each attack around a visible wind-up, an active hit phase and a recovery period. Specify range, facing/turn limits, movement, allowed interruption, cooldown, damage/impact and recovery. Resolve target selection and phase changes on the server; clients display synchronized state. Define how attacks cancel on death, target loss, disconnect and encounter reset.

## First conversion: tower Sentinel

New demo Sentinels use `puffish_skills:tower_sentinel`, a dedicated registry type backed by the husk entity class, with 120 health and 7 base attack damage. Its own Epic Fight mob patch selects a single strike (weight 60, cooldown 40 ticks), a two-hit sequence (weight 30, cooldown 60 ticks) or a three-hit sequence (weight 10, cooldown 100 ticks). All use existing Epic Fight zombie animations, eye-height/range conditions up to 2.2 blocks, non-looping series and one target per strike. Cooldowns apply per series, not as a guaranteed global rest window. Custom sweep/slam/charge assets are not implemented. The health-gated second phase and boss bar below are now implemented.

The vanilla client renderer and explicit rotten-flesh loot table allow development without Epic Fight. In that case the entity retains vanilla husk behavior; it is not an Epic Fight encounter. The existing first-clear receipt remains unchanged. Living legacy husks are retained and block duplicate spawning; after their death, `/spawn_sentinel` creates the new type. Restart with the updated JAR before installing the updated demo pack. Replacing the pack alone cannot register the entity.

1. Implemented: pinned Epic Fight 21.17.3.1 and NeoForge 21.1.219 profile with Better Combat excluded. Dependency/asset resolution is checked in CI.
2. Implemented: dedicated Sentinel entity type, attributes, fallback renderer, loot table and matching Epic Fight data patch. Confirm idle, chase, hit and death behavior in game before custom attacks.
3. Pending: implement a sweep, slam and charge with readable telegraphs and punishable recovery. These are planned attacks, not existing animation registry entries.
4. Implemented as a prototype: below half health, Epic Fight unlocks two additional sequences. The boss bar labels this health-derived phase; actual gameplay validation remains pending.
5. Implemented first slice: hand Martial guard/counter/recovery ownership to Epic Fight and mirror stamina. Pending: provider guard/parry outcome translation to ARPG posture/momentum.
6. Validate solo and party fights, attack collision/timing, death/interrupt/reset behavior, reconnects, first-clear rewards and tower unlocks with the actual modpack.

Other combat pillars retain their distinct gun, bow and spell loops. Their attacks need compatibility with Epic Fight-patched enemies, including damage attribution and hit reactions; they are not automatically converted into melee combos.

## Health phase and boss UI

The dedicated type now instantiates `TowerSentinelEntity`, a husk subclass. Its standard server boss bar appears for players tracking the entity, reports health, and labels the phase **Guarding** (yellow) or **Enraged** (red). Labels and the default entity name have translation keys; an authored custom name is preserved. Tracking loss removes that viewer, death hides the bar, and entity removal clears all viewers. Phase is derived from current health, so reloads need no extra phase receipt and healing back to half health returns the UI to Guarding. Water conversion is disabled for this custom entity; ordinary husks keep their behavior.

Epic Fight's `health` predicate with `less_ratio` and threshold `0.5` enables two additional sequences: attack3 → attack2 (weight 90, cooldown 50), and attack2 → attack1 → attack3 (weight 60, cooldown 80). Each hit retains the eye-height, range and health conditions. Baseline sequences remain available at all health values, including exactly half health. When all series are selectable below half health, the new sequences account for 60% of the total selection weight; cooldowns and ongoing sequence state affect actual choices. Automatic AI switching between series is disabled so a selected combo retains its authored order. This does not disable Epic Fight hit reactions or grant stun immunity. This is a change in the eligible attack pool, not an invulnerable transition or a forced mid-animation interrupt. Healing can stop an unfinished health-gated continuation when Epic Fight next checks its predicates.

The threshold uses current maximum health, not a hard-coded 60 HP. The UI threshold matches the strict provider comparison. Cooldowns remain per series, and the attacks still use existing Epic Fight animations. No additional custom damage loop, phase reward or tower-unlock receipt is introduced. Existing dedicated Sentinels acquire the subclass on save reload; living legacy `minecraft:husk` Sentinels remain unchanged.

Local checks cover exact/adjacent half-health boundaries, changed maximum health, bounded bar values, fallback inputs, baseline attack availability and per-hit phase predicates. Client/server smoke testing must check the real phase transition, heal reversal, late joining, tracking loss, death, removal, restart and underwater identity retention. Provider patch parsing, attack timing/collision and actual rendering remain unverified by those structural checks.

## Runtime commands and checks

Use the isolated combat profile first:

```bash
./gradlew :NeoForge:verifyArpgEpicRuntime -Parpg_epic_runtime=true
./gradlew :NeoForge:runClient -Parpg_epic_runtime=true
./gradlew :NeoForge:runServer -Parpg_epic_runtime=true
```

Add `-Parpg_full_runtime=true` to include the remaining full provider family while substituting Epic Fight for Better Combat. That combined modpack still requires smoke testing. The isolated profile does not install Iron's runtime stack. Both profiles keep the normal build's external providers optional.

The pinned artifact is `maven.modrinth:vu3NZ5Ma:8HHhJt6i`. Runtime verification resolves it, excludes Better Combat, checks the three read-only bridge API methods plus fourteen model/animation resources used by the patch and writes `NeoForge/build/reports/epic-fight-runtime.txt` with the actual JAR metadata. Asset presence and dependency resolution do not prove animation registration, data-pack parsing, collision behavior or startup compatibility. CI compiles the loader code through the normal NeoForge build; an in-game client/server test remains required.

## Primary references

- Entity patch data and predicates: https://github.com/Antikythera-Studios/epic-fight.github.io/blob/main/docs/Guides/Entities/page1.en.md
- Addon entity patches: https://github.com/Antikythera-Studios/epic-fight.github.io/blob/main/docs/API/Starting.en.md
- Version-specific source: https://github.com/Antikythera-Studios/epicfight/tree/1.21.1

These references establish the available integration mechanisms. They do not establish compatibility of the current project, its mappings, Iron's Spells, gun mods or other installed providers. No Epic Fight encounter has been runtime-tested in this project yet.


## First player stance and innate controls

The optional ARPG melee bridge now requests the equipped longsword's Liechtenauer stance or sword's Sweeping Edge through Epic Fight's authoritative skill container. The Melee hub entry, command menu and optional keybinds inspect live provider state. Legacy direct-damage weapon skills are blocked while Epic Fight is installed. See `EPIC_FIGHT_MELEE.md` for exact scope and smoke tests. Custom melee kits, broad stance selection and exact-ID specialization remain pending. Runtime verification now checks 19 additional melee API signatures and 11 weapon animations in addition to the earlier three stamina accessors and 14 Sentinel assets.
