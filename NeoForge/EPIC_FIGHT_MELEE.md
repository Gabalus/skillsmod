# First provider-backed player melee actions

This slice connects the ARPG controls to two existing Epic Fight 21.17.3.1 weapon skills. It does not register replacement skills or invent three generic stances.

| Equipped provider skill | ARPG action | Result owned by Epic Fight |
| --- | --- | --- |
| Sword: `epicfight:sweeping_edge` | Innate | Cleaving attack through the provider animation, collision and resource checks |
| Longsword: `epicfight:liechtenauer` | Innate or Stance | Toggle the provider's timed defensive stance, with different combat motions and defensive behavior |

The pinned provider's default Liechtenauer duration is 240 ticks (12 seconds), with a 60-tick extension on kills according to its own skill rules. It prevents sprinting during the stance. Resource consumption, charge generation, duration, animation and cancellation remain provider-controlled, including server data-pack changes. ARPG does not refill charges, assign skills, set stamina, extend duration or apply an extra health hit. The generic balanced/defensive/aggressive design is not yet implemented across weapon families.

## Controls

- Open the Character hub (`P`) and select **Melee**, or run `/arpg melee`.
- The read-only menu shows the live weapon category, style, innate skill, charge count/resource type, stance state and remaining duration. Click Use innate, Enter/Leave defensive stance, or Refresh.
- `/arpg melee innate` requests the current supported innate skill.
- `/arpg melee stance` requests Liechtenauer only; swords without it are rejected.
- In Controls, bind **ARPG Melee Innate** and **ARPG Weapon Stance** for combat use. They default to unbound to coexist with the modpack's Epic Fight controls. They send the same server commands and do nothing while a GUI is open. Existing Epic Fight inputs still work.

The menu does not continuously refresh. Action results appear in the action bar. A displayed charge count is information, not an authoritative guarantee of readiness: Epic Fight evaluates live resource/charge predicates when the request executes.

## Server validation

Require a real, alive survival player with a chosen primary class and the Martial pillar in Epic Fight combat mode. Read the provider's primary hand (including supported mirrored-hand selection), current item capability and current WEAPON_INNATE container on every request. The weapon's live innate skill must be the identical registered skill held by that container. Disabled containers, held skills and animation states that forbid skill use are rejected. Only Liechtenauer and Sweeping Edge are admitted; held/charged or argument-dependent skills continue to use their provider controls.

After these checks, call `SkillContainer.requestCasting(ServerPlayerPatch, CompoundTag)` with an empty server-created argument tag. The provider then runs its native skill eligibility, cast events and resource rules, performs the action, and synchronizes its normal feedback. If it denies the action, no ARPG fallback attack is emitted. Skill cancellation via a second Liechtenauer request uses its ordinary provider toggle path, not a forced container reset.

These actions do not pass through the ARPG catalog's legacy weapon executor. They currently use class/pillar gating rather than per-skill ARPG level or specialization gates, and award no separate ARPG skill XP. Provider hits retain the existing ordinary attack-scaling path; exact-ID custom melee specialization integration is future work.

Whenever Epic Fight is installed, `/arpg skill use` rejects legacy direct-damage weapon skills in both provider combat and vanilla mode. Without Epic Fight that development executor retains its old behavior. This closes the independent attack/resource route; it does not convert the old catalog skills into provider movesets. Provider discovery now lists Epic Fight under `/arpg providers`.

The bridge is reflection-only and loads its provider classes after checking ModList. Missing/changed APIs log once and deny actions. No custom stance state is saved or restored on login, respawn or weapon switch; Epic Fight owns that lifecycle. Live cancellation on weapon changes still needs validation with the actual provider.

## Validation and smoke test

CI compiles the normal optional-provider build, runs three Common validation tests and verifies 19 additional melee API signatures against the pinned JAR. Runtime verification checks 25 model/animation assets: 14 Sentinel assets and 11 longsword/cleave assets. The Common tests cover sword versus stance access, unsupported skills, stale weapon identity, busy/disabled/held states, survival/class/pillar and combat-mode requirements. They do not execute Minecraft or the provider skill itself.

1. Run `./gradlew :NeoForge:runClient -Parpg_epic_runtime=true`. Choose Warrior through the class screen, enter survival and Epic Fight combat mode, equip a supported sword, and inspect `/arpg melee`.
2. Generate the provider's required innate charge through normal combat. Request Sweeping Edge using the command and a bound key. Verify one provider animation/hit sequence, ordinary charge consumption, damage attribution and no direct ARPG cone attack. Insufficient charge and active recovery must deny further requests.
3. Equip an Epic Fight longsword (operator development setup can use `/give @s epicfight:iron_longsword`), build native charge and enter Liechtenauer. Verify changed combat motions, defense, sprint restriction, timed expiration and ordinary provider cancellation on a second valid request. Check that toggling does not restore charge or reset an active attack/recovery.
4. Change weapons while the chat menu is open, then click an old action. The current item/container must be rechecked; a sword cannot enter Liechtenauer. Test mounted, airborne, held-skill, stunned, death, respawn, reconnect and mirrored-hand cases.
5. Verify `/arpg skill use` is denied with Epic Fight installed, including vanilla player mode. Remove Epic Fight and verify the base build starts, the menu reports its absence and the old development executor remains available.
6. Repeat with the full provider stack on a dedicated server and verify other players see the provider's stance/attack state. No in-game or combined-mod compatibility claim follows from the compile/API tests.

The next combat work is a custom weapon kit with heavy and active melee attacks, followed by provider guard/parry outcome translation and broader stance support. Mage live weaving remains a later distinct mechanic alongside prepared spells.


## Additive sword kit: Measured Strike and Driving Slash

Two original registered skills now occupy dedicated `ARPG_HEAVY` and `ARPG_DRIVING` slots. They do not replace the weapon's native WEAPON_INNATE, guard, mover or passive containers. The extra slots register through Epic Fight's extensible-enum setup on both client and server. Their shared category uses native synchronization and does not save loadout entries; first use installs only the requested skill in its empty dedicated slot and sends normal local/remote provider sync packets. A conflicting occupied slot is rejected rather than overwritten.

| Action | Command | Minimum ARPG level | Default provider stamina | Cooldown |
| --- | --- | --- | --- | --- |
| Measured Strike | `/arpg melee heavy` | 3 | 8 | 80 ticks / 4 seconds |
| Driving Slash | `/arpg melee driving` | 5 | 6 | 60 ticks / 3 seconds |

Both require a chosen class, Martial pillar, real alive survival player, Epic Fight combat mode, a main-hand sword/longsword and empty offhand. Held skills, item use, riding, airborne state and provider animation recovery deny execution. Mirror/offhand and dual-weapon support are deliberately outside this first kit; the earlier native stance bridge retains its own scope. Commands, optional **ARPG Heavy Strike** / **ARPG Driving Slash** keybinds and Melee menu actions use the same server validation. The menu reports levels, default costs and live cooldowns; being off cooldown alone does not imply sufficient stamina or eligibility.

Measured Strike uses the sword/longsword's existing third combo animation as a deliberate finisher input. An active Liechtenauer longsword uses its corresponding defensive third animation. Driving Slash uses the appropriate existing sword/longsword dash attack animation. Neither adds bespoke motion, guaranteed travel distance, armor bypass, a damage multiplier or a separate damage cone. They reuse provider-authored damage/collision/timing and the existing ordinary ARPG attack-scaling path. Exact-ID specialization and separate skill XP are not implemented for these two actions, and they are not exposed as catalog specialization trees yet.

The registered skills declare `Resource.STAMINA`; `requestCasting` invokes native cast validation and resource consumption once before execution. No second stamina deduction or legacy ARPG resource pool is used. Provider attribute/event modifications remain effective. Parameter JSONs live under `data/puffish_skills/skill_parameters`; base stamina overrides are bounded to 1–30, while duration/stacks remain fixed for these instant actions. Cooldowns and progression levels are code-defined for this prototype.

Cooldowns are versioned in the existing UUID-owned ARPG player save, using overworld game time. They survive ordinary saves, death/player replacement, reconnect and weapon/container changes. An eight-tick shared commitment window prevents a same-tick second kit action. Provider animation eligibility can impose longer recovery. Interrupted or missed accepted attacks still consume their cost and cooldown. Cooldowns are not reset by generic combat-meter reset. Legacy saves default to zero; malformed/future schemas reject explicitly. If the saved clock moves backward, normalization caps remaining cooldown to each action's duration, preventing an unbounded wait. No cooldown or stamina refund is awarded.

Epic Fight is now a compile-only dependency for the addon classes and remains optional at runtime. The guarded entry point only loads/registers them when the provider is present. Startup registration failure stops that provider-enabled installation with a clear error rather than claiming a working kit. Base builds retain their existing development behavior without the provider.

Validation now includes five additional Common tests for kit gates, exact cooldown boundaries, shared commitment, PlayerData save round-trip/legacy defaults and corrupt/rollback bounds. Runtime verification checks 30 melee signatures and 28 model/animation assets. Actual addon registry startup, slot allocation/synchronization, resource consumption, hit phases, interruption and save/reconnect behavior still require Minecraft client and dedicated-server tests.

Extend the smoke test above: use `/arpg level set 5` only as an operator development setup, equip one supported sword/longsword, bind the two new inputs and compare live Epic Fight stamina before/after accepted and rejected requests. Test heavy followed immediately by driving, missed/interrupted attacks, defensive longsword selection, weapon changes, reconnect and restart mid-cooldown. Confirm no extra hit or native-slot replacement. Removing the provider must still permit the base installation to start.
