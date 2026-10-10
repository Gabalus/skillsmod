# Encounter progression: L2 Hostility and Apotheosis

The custom character owns XP, levels and tree allocation. L2 Hostility supplies observed monster level/trait ranks; Apotheosis supplies its mob elite/invader markers and a spawn-context world tier. These feed one bounded encounter XP calculation. Neither provider becomes a second character XP pool.

## Eligible encounters

The former global mob-kill XP hook is replaced. Kills qualify if the entity has an operator/server-authored `arpg:encounter` or `arpg:world_boss` command tag, belongs to a configured expedition dimension, or is an Apotheosis invader while automatic world-boss rewards are enabled. Ordinary overworld kills and random L2/Apotheosis elites do not qualify by default. Vanilla XP orbs are unaffected.

Automatic qualification rejects recognized spawner, trial-spawner, spawn-egg, dispenser, breeding, summon, conversion and reinforcement spawn origins. L2 `summoned`, `minion` and `noDrop` flags also reject automatic qualification. A `copied` flag is read only when present; pinned Hostility 3.0.16 does not expose it. Explicit encounter/boss tags override those exclusions so authored trial-spawner encounters remain possible. Markers are a trusted server authoring API, not a player request. Fake players, creative players, spectators and player victims cannot earn these rewards. An entity stores a settlement flag to reject repeated XP-drop callbacks.

The default dimension allowlist is empty: no existing sandbox dimension is silently reclassified as a dungeon. Populate it only with real expedition dimension IDs supplied by the eventual dungeon implementation. Without that configuration or explicit tags, only eligible natural Apotheosis invaders provide character XP.

## Reward calculation

Base XP remains the previous conversion: original mob XP times 100, capped at 50,000. Bonus percentages are additive, and the final result is capped again. Zero/negative original XP awards zero.

| Threat input | Default bonus | Input cap |
| --- | --- | --- |
| L2 monster level | 1% per level | 100 |
| Sum of L2 trait ranks | 3% per rank | 20 |
| Apotheosis spawn tier | 20% per tier index | 4 |
| Apotheosis elite | 25% | One rank bonus |
| Apotheosis invader | 100%, replaces elite bonus | One rank bonus |

A 5-XP mob with L2 level 20, five total trait ranks, Apotheosis tier index 2 and elite status awards 1,000 character XP, if encounter-eligible. An invader with the same metrics awards 1,375. These are initial tuning values, not a final balance curve. `maxKillExperience` can lower the final cap below 50,000.

Apotheosis tier is captured once from the nearest player within 64 blocks at spawn/join, then persisted with the mob. Killing-player tier changes cannot inflate it. Legacy mobs loaded from disk without a snapshot get tier zero; absent player context or an unavailable adapter also gives zero. This is a conservative approximation of the provider's spawn context, not an exact reference to its internal generation context. It does not force world-tier selection or modify provider mob stats.

## Configuration and testing

Override `data/puffish_skills/arpg/encounter_progression.json` in a data pack, then `/reload`. Unknown schemas, malformed dimensions, missing required values and out-of-range percentages/caps are rejected; the previous validated policy remains active. `/arpg progression` reports the active eligibility rules and percentages. `/arpg providers` reports detected providers.

For an operator smoke test in a development world, spawn one zombie, then mark it:

```text
/summon minecraft:zombie ~ ~ ~3
/tag @e[type=minecraft:zombie,sort=nearest,limit=1,distance=..8] add arpg:encounter
/arpg progression
```

Kill it in survival mode and compare `/arpg status` before/after. An otherwise identical unmarked sandbox zombie must award no character XP. Repeat with L2 traits, an Apotheosis elite/invader, high threat metrics, and a spawner mob. Change world tier after spawning and verify the reward still uses the stored tier. Restart the server with surviving mobs and verify snapshots remain stable.

```bash
python3 tools/test_encounter_progression.py
python3 tools/test_sandbox_engine.py
```

The first command runs 30 dependency-free regression checks. Common JUnit also checks bundled JSON equality and incomplete JSON rejection; GitHub CI compiles actual Minecraft/NeoForge APIs and runs these tests. Real combined-provider client/server testing remains required.

## Scope and remaining work

The existing opt-in `arpg_full_runtime` profile already includes pinned L2 Hostility and Apotheosis dependencies. This slice adds progression use of their metadata; it does not install either mod into the default profile. Optional reflection failures warn once and use baseline threat. Their own world-tier unlocks, L2 regional/player difficulty growth, loot tables and attribute scaling remain provider-controlled and still require coordinated modpack tuning. Provider metadata adapters are based on the upstream 1.21 source APIs; no combined-mod runtime compatibility claim is made.

Character levels no longer generate passive/confluence points. Authored first-clear receipts now supply those currencies and recipe discoveries; see [COMPLETION_REWARDS.md](COMPLETION_REWARDS.md) for reward definitions, migration and server authoring. Physical recipe scroll drops, party credit, monolith floors and prestige beyond level 100 remain future work. Trial-completion commands remain operator-only; the completion adapter can now authorize authored trial rewards.

The entity settlement flag and character progress use ordinary Minecraft saves, not a transaction across entity/player/world storage. Hard-crash rollback testing and restart recovery remain necessary before release. Mobs whose spawn origin was not captured before this integration cannot be fully classified for farm exclusion.

Upstream APIs inspected:

- https://github.com/Minecraft-LightLand/L2Hostility/blob/1.21/src/main/java/dev/xkmc/l2hostility/content/capability/mob/MobTraitCap.java
- https://github.com/Shadows-of-Fire/Apotheosis/blob/1.21/src/main/java/dev/shadowsoffire/apotheosis/tiers/WorldTier.java
- https://github.com/Shadows-of-Fire/Apotheosis/blob/1.21/src/main/java/dev/shadowsoffire/apotheosis/mobs/types/Invader.java
- https://github.com/Shadows-of-Fire/Apotheosis/blob/1.21/src/main/java/dev/shadowsoffire/apotheosis/mobs/types/Elite.java


## Read-only threat inspection

With Hostility installed, look at a living target within 16 blocks and run `/arpg threat`. The first intersected living entity before terrain is reported with L2 level, health, sorted trait IDs/ranks (up to 20), and any provider summon/minion/no-drop exclusion. This does not assign traits, modify health/damage or grant rewards. Trait IDs are diagnostic labels, not yet a polished tooltip overlay. The adapter now invokes `AttVal.type()` through its public interface and treats the removed `copied` field as optional, so its absence no longer disables the entire bridge.

Dungeon-authored level curves, trait pools/rank limits and coordination with L2 regional/player difficulty remain pending. See `MELEE_FIRST_COMBAT.md` for the melee-first direction. Do not duplicate L2's attribute scaling with another blanket multiplier.
