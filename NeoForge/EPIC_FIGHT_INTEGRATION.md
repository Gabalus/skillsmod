# Epic Fight enemy and boss integration

## Required direction

Epic Fight is the core for authored enemy and boss movesets on NeoForge 1.21.1. It is also the intended Martial action provider. The base ARPG module may still load without external providers for development, but an encounter advertised as an Epic Fight encounter requires the validated Epic Fight runtime. GeckoLib is not the moveset foundation for these enemies; its presence in the Iron's Spells dependency family does not change that decision.

The existing full runtime profile still installs Better Combat. Treat it as a legacy convenience profile until a pinned Epic Fight profile replaces that selection. Do not combine both providers for the same melee action pipeline by default. No runtime dependency or combat adapter has been changed by this design update.

## Ownership

| System | Responsibility |
| --- | --- |
| Epic Fight entity patch | Attack animation, attack-phase collision, combat behavior selection and supported hit reactions |
| Enemy/boss addon | Custom attacks, phase transitions, telegraphs, encounter targeting, cooldowns and arena mechanics through Epic Fight patches |
| ARPG core | Character progression, completion receipts, loot/recipe rewards, tower gates and encounter lifecycle |
| Martial bridge | Map Epic Fight guard/parry outcomes and resource costs into the chosen authoritative ARPG resource model |

The bridge must reconcile Epic Fight stamina/stun semantics with ARPG stamina/posture. Do not run two independent player stamina budgets or apply one attack twice through Epic Fight and a second custom damage loop. The existing vanilla shield adapter is not proof of Epic Fight guard compatibility. Determine supported events and cancellation order against a pinned 1.21.1 release before writing that bridge.

## Content workflow

Start ordinary enemies with compatible Epic Fight presets or explicit mob-patch data using existing animations. The documented location is `data/<entity namespace>/epicfight_mobpatch/<entity path>.json`; these patches select by entity type, not by our completion tag. A generic husk override would affect unrelated husks, so the authored Sentinel needs a dedicated entity type or a verified per-entity Java patch before it receives its unique moveset.

Use Java entity patches for boss behavior that exceeds the supported data predicates, including phase changes and custom arena attacks. Custom animation assets must use the Epic Fight model/armature pipeline and the pinned release's animation API. Animation playback alone must not award damage or progression.

Design each attack around a visible wind-up, an active hit phase and a recovery period. Specify range, facing/turn limits, movement, allowed interruption, cooldown, damage/impact and recovery. Resolve target selection and phase changes on the server; clients display synchronized state. Define how attacks cancel on death, target loss, disconnect and encounter reset.

## First conversion: tower Sentinel

The current Sentinel is a tagged vanilla husk with adjusted attributes and a dedicated completion receipt. It has no authored custom moveset. Preserve the receipt's first-clear and replay behavior when replacing the entity.

1. Pin and resolve a NeoForge 1.21.1 Epic Fight release and its dependencies in a separate runtime profile, excluding Better Combat.
2. Register a dedicated Sentinel entity and matching Epic Fight patch; confirm idle, chase, hit and death behavior before custom attacks.
3. Implement a sweep, slam and charge with readable telegraphs and punishable recovery. These are planned attacks, not existing animation registry entries.
4. Add one deliberate phase transition only after the base attacks work reliably.
5. Connect Martial guard/resource semantics without duplicate stamina costs, damage or stun resolution.
6. Validate solo and party fights, attack collision/timing, death/interrupt/reset behavior, reconnects, first-clear rewards and tower unlocks with the actual modpack.

Other combat pillars retain their distinct gun, bow and spell loops. Their attacks need compatibility with Epic Fight-patched enemies, including damage attribution and hit reactions; they are not automatically converted into melee combos.

## Primary references

- Entity patch data and predicates: https://github.com/Antikythera-Studios/epic-fight.github.io/blob/main/docs/Guides/Entities/page1.en.md
- Addon entity patches: https://github.com/Antikythera-Studios/epic-fight.github.io/blob/main/docs/API/Starting.en.md
- Version-specific source: https://github.com/Antikythera-Studios/epicfight/tree/1.21.1

These references establish the available integration mechanisms. They do not establish compatibility of the current project, its mappings, Iron's Spells, gun mods or other installed providers. No Epic Fight encounter has been runtime-tested in this project yet.
