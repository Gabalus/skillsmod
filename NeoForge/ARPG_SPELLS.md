# Iron's spell and skill content

Iron's Spells 'n Spellbooks 1.21.1-3.16.3 is the spell execution substrate. The existing nine built-in catalog spells remain available. This module now registers two original spells in the Iron's spell registry when that mod is installed. The base module still loads without Iron's; these spells are then unavailable. Installing the updated JAR requires a restart for registration.

| Spell | ARPG gate | Iron's defaults | Effect |
| --- | --- | --- | --- |
| `puffish_skills:storm_pulse` | Arcanist, level 3 | 20–40 mana, levels 1–5, 8-second cooldown | 6–14 base lightning damage to visible hostile mobs within four blocks; successful hits push back |
| `puffish_skills:rime_pulse` | Shaman, level 5 | 25–45 mana, levels 1–5, 10-second cooldown | 4–12 base ice damage to visible hostile mobs within four blocks; accepted hits apply ARPG chill for 2.5–4.5 seconds, with wet-enhanced buildup and bounded freeze |

Either primary or secondary discipline can satisfy the discipline gate. ARPG character level and Iron's spell level are distinct. Both spells use a 15-tick long cast (before provider cast-speed changes), Uncommon minimum rarity and the provider's school/power/config system. Iron's charges mana and cooldowns; the catalog adds no second resource charge. Existing Blood Magic handling applies through the same cast bridge.

These first spells are PvE: targets must inherit vanilla `HostileEntity`, be alive, outside the caster's team, inside the spherical range and visible to the caster. Players, pets/passive creatures, and modded enemies outside that base class are excluded. Damage is emitted once through Iron's `DamageSources.applyDamage` with the spell's damage source. Secondary chill/knockback only follows an accepted damage application. No terrain is changed, no lightning entity is spawned and no teleport is performed. Vanilla/other-mod knockback resistance still applies; chill/freeze never guarantees an Epic Fight stun or boss interrupt. Rime no longer adds vanilla Slowness alongside its ARPG chill. See [STATUS_SYSTEM.md](STATUS_SYSTEM.md) for explicit cold buildup, immunity, recovery and fire reactions; Iron's other native ice effects remain provider-owned.

The character hub's **Spells** button and `/arpg spells` display up to 32 Iron's catalog entries with progression requirements and clickable specialization actions. "Ready" means the ARPG gate is satisfied; scroll ownership, equipped slots, mana, cooldown, provider settings and ongoing casts still govern execution. Obtain/equip scrolls through Iron's normal systems, then use its spellbook, selection wheel and casting controls. The menu does not cast spells or award scrolls.

For operator smoke tests, the pinned provider exposes:

```text
/createScroll puffish_skills:storm_pulse 1
/createScroll puffish_skills:rime_pulse 1
```

Use a character with the relevant discipline/level, equip the scroll using Iron's inscription system, and cast normally. Check that low-level/wrong-discipline characters are rejected before casting. The provider's normal spell acquisition/configuration infrastructure sees the registered spells, but no encounter-specific scroll reward or custom recipe is authored in this slice. Existing loot tables, inscription behavior and configured acquisition need an in-game check.

Each spell has a 24-node specialization tree, three eight-node branches and a 20-point spending cap. The branches grant 4% increased spell, school or area damage per node, scoped to the exact spell ID. Specialize from the menu or with `/arpg specialize puffish_skills:storm_pulse` / `/arpg specialize puffish_skills:rime_pulse`, then use **Skills** in the hub. The existing cast bridge grants specialization XP for successful casts; this can progress without hitting a target. It is separate from dungeon-only character XP and first-clear points. Anti-farming specialization policy remains future work.

Global spell/school attribute projection is already included in Iron's spell power. The custom executor applies only context-dependent ARPG rule modifiers afterward, avoiding a second application of global passive modifiers. It preserves the spell ID/tags during synchronous damage so hit/kill triggers can identify the spell. No critical roll, range/duration specialization or separate cooldown/cost adapter is introduced. Tooltips show provider-scaled base power, before per-target conditional rules and damage mitigation.

Regenerate this content without changing unrelated trees:

```bash
python3 tools/generate_arpg_spells.py
python3 tools/test_arpg_spells.py
./gradlew :Common:test :NeoForge:build
./gradlew :NeoForge:verifyArpgIronsRuntime -Parpg_irons_runtime=true
./gradlew :NeoForge:runClient -Parpg_irons_runtime=true -Parpg_epic_runtime=true
```

The main catalog generator also calls this augmentation. Tests cover linked definitions/rules, graph integrity, icon existence, deterministic regeneration, preserved unrelated content, authored gates, secondary-discipline access and exact spell scoping. Compilation and dependency resolution do not prove runtime registration/config sync, scroll acquisition, casting interruption, targeting, particles/sounds, damage attribution or mixed Epic Fight behavior. Test those on a client and dedicated server before releasing a modpack.
