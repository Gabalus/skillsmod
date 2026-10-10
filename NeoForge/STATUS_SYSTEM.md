# Typed status foundation

NeoForge owns a saved ARPG ailment backend for players and living enemies. Player rule actions and enemy ability executors share the same eligibility, application, stacking and ticking rules. `NeoForgeAilments.applyStatus(owner, target, type, damage, duration, skill)` accepts a living owner and an already calculated per-pulse damage amount. It requires a living, same-world, non-allied target and excludes self, fake players, creative and spectator players. Ignite also excludes fire-immune or water-submerged targets.

| Ailment | Damage type | Stacking | Current interaction |
| --- | --- | --- | --- |
| Bleed | Physical | One strongest application | Double pulse damage when horizontal displacement since the previous sampled tick exceeds 0.05 blocks |
| Ignite | Fire | One strongest application | Removed when touching water |
| Poison | Nature | Up to eight independent applications | Stronger incoming applications replace the weakest at the cap |

Damage pulses every 20 loaded victim ticks. Durations are bounded to 1–1200 ticks and each base pulse to 10,000 damage. Bleed and ignite reject weaker applications. Equal-strength applications may refresh a longer duration; stronger ones use their own duration and owner. Neither refresh nor upgrade postpones their existing pulse interval. At the poison cap, equal or weaker applications are rejected; stronger replacements start a fresh interval. A final pulse on the expiration tick is permitted. Durations shorter than the next pulse interval can expire without damage.

The schema-versioned save records type, owner UUID, originating skill, damage, remaining duration and partial pulse interval in entity persistent NBT. Corrupt, over-limit and unknown-version saves are discarded. Victim unloading and player logout pause the duration without catch-up damage. Reconnect, build refresh and datapack sync do not cleanse these statuses. Death clears them, including player death clones; a cancelled death event does not clear them prematurely. Ordinary dimension-transfer behavior remains loader-owned and needs an in-game check.

Player ailment rules snapshot the ailment damage stat, its associated typed damage stat and damage-over-time stat at application. On each pulse, fire/nature resistance is evaluated for an ARPG player victim without penetration; physical bleed ignores armor. Ward and native damage hooks still apply. Dedicated damage types bypass armor, shields and hit cooldown so concurrent statuses and poison stacks do not discard one another's pulses. Ignite is tagged as fire for native fire protection/immunity. These statuses can kill, including ARPG poison; vanilla poison remains a separate mechanic.

Loaded living owners resolve from the victim's current server world. An online player owner's source and synchronous damage context retain kill attribution and the originating skill. If the owner dies, unloads, logs out or leaves that world, saved pulses continue with an unattributed source; no offline reward is queued. Becoming allied, creative or spectator suppresses eligible pulses while duration continues. Custom DoT sources skip ARPG hit, crit, block and damage-taken trigger dispatch, preventing recursive hit-trigger ailments. Kill triggers remain available. Provider-specific damage hooks can still affect the native damage call and need joint testing.

The Tower Sentinel has a 10% chance to apply bleed after an actual damaging direct melee hit, for 60 ticks at one damage per pulse (two when moving). Its native Iron's Icicle and Epic Fight combo remain provider-owned. Native Iron's burning, poison, freezing and other effects are neither converted into ARPG ailments nor reapplied by this backend. Explicit ARPG and native effects can coexist; authors should avoid deliberately assigning both versions to one ability unless that extra effect is intended.

`/arpg ailments` inspects the player's active ARPG stacks and longest remaining duration. `/arpg ailments target` inspects the nearest living entity under the crosshair within 16 blocks, clipped by blocks. Both commands are read-only and do not require an operator. A synchronized HUD/status panel is not implemented yet.

## Extension boundary

`AilmentType` defines typed identity, limits and rule tags; `AilmentState` owns immutable bounded applications and pulse scheduling. The NeoForge backend performs environment checks before stepping the state and applies the resulting typed pulses. New status/cross-effect rules should settle or consume state before external callbacks, retain attribution, and keep pulse damage out of hit-trigger dispatch. Resistance, immunity and reaction balance must be explicit rather than inferred from an Iron's school name.

Only water extinguishing and movement-sensitive bleed are implemented here. Chill, shock, freeze, wet, burning reactions, poison detonation, reaction cooldowns, boss-specific ailment resistance and native-provider status synchronization are future work. The combat type catalog already includes physical, fire, cold, lightning, blood, holy, ender and nature; it does not automatically grant an ailment to every channel. Fabric retains the existing unsaved fallback and does not have this NeoForge backend.

## Validation

Common JUnit covers pulse/expiration boundaries, movement, strongest ownership, refresh timing, bounded poison replacement, removal, save round-trip, malformed/future saves and non-hit tags. The pure state classes also compile locally. CI compiles the actual mapped Common/NeoForge sources and executes Common tests plus provider/data verification.

In Minecraft, test player rule applications and Sentinel melee bleeds; inspect with both commands. Check standing/moving bleed, entering water while ignited, all eight poison pulses alongside bleed/ignite, ward, resistance, native fire protection, owner-attributed deaths and rewards, allied/creative/spectator exclusions, victim and owner logout/unload separately, reconnect, restart, cancelled death, respawn, dimension transfer and datapack reload. Repeat with each provider absent and with all providers installed. Actual Minecraft behavior and combat balance have not yet been verified.
