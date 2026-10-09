# ARPG runtime profiles

The normal NeoForge build deliberately keeps external ARPG providers optional. The custom ARPG core remains authoritative and loads without Iron's Spells, Better Combat, Apotheosis, L2 Hostility, L2 Artifacts or Celestial Artifacts.

## Passive-tree authoring

The shipped ARPG graphs are deterministic resources. Regenerate the authored passive/ascendancy layer after changing tree design data with:

```bash
python3 tools/generate_arpg_skilltrees.py
```

On Windows, `python tools/generate_arpg_skilltrees.py` is equivalent when Python is on PATH. The generator keeps the 1,602-node universal graph, replaces placeholder passive names with searchable thematic names, and authors all 24 ascendancies as radial four-branch boards. It uses the existing ARPG catalog/rule engine; it does not import Path of Exile node data or artwork.

In game, **P** opens the ARPG Character hub. It provides direct Passive / Ascendancy / Confluence / Skills / Atlas navigation and full-text node search. Search results focus the selected node in the existing allocation screen. **K** still opens the classic Puffish Skills category view. Inside a tree, drag to pan and use the mouse wheel to zoom.

## Iron's integration profile

Enable the spell-integration runtime with:

```bash
./gradlew :NeoForge:verifyArpgIronsRuntime -Parpg_irons_runtime=true
```

The profile targets:

- Minecraft 1.21.1
- NeoForge 21.1.200
- Iron's Spells 'n Spellbooks 1.21.1-3.16.3
- GeckoLib 4.7.5.1
- playerAnimator 2.0.1+1.21.1-forge
- Curios 9.5.1+1.21.1
- Iron's Lib 1.21.1-2.1.0

Launch it with:

```bash
./gradlew :NeoForge:runClient -Parpg_irons_runtime=true
./gradlew :NeoForge:runServer -Parpg_irons_runtime=true
```

## Legacy full ARPG provider profile

This existing convenience profile includes Better Combat and is not the intended Epic Fight combat stack. The enemy/boss moveset core is now Epic Fight; see [EPIC_FIGHT_INTEGRATION.md](EPIC_FIGHT_INTEGRATION.md). Enable `-Parpg_epic_runtime=true` to substitute the pinned Epic Fight runtime and NeoForge 21.1.219. Dependency/asset resolution is checked separately; combined-mod gameplay validation remains pending.

Enable the legacy convenience-provider stack with:

```bash
./gradlew :NeoForge:verifyArpgFullRuntime -Parpg_full_runtime=true
```

This profile includes the Iron's stack plus:

- Better Combat 2.4.0
- Apotheosis 8.7.0 and its required 1.21.1 modules
- L2 Hostility 3.0.13
- L2 Artifacts 3.0.x and the shared L2 libraries
- Celestial Artifacts 2.0.4 for NeoForge 1.21.1

Launch the complete profile with:

```bash
./gradlew :NeoForge:runClient -Parpg_full_runtime=true
./gradlew :NeoForge:runServer -Parpg_full_runtime=true
```

Use `/arpg providers` in-game to see which provider adapters are actually loaded. Provider integrations normalize external mechanics into ARPG tags/attributes; they do not become authoritative progression systems.

## Combat-pillar runtime

The shared `Common` source set owns combat-pillar state. Providers may animate or report an action, but they must not maintain a second authoritative stamina, posture, heat, focus or instability value.

The kernel currently defines eight combat grammars:

- Martial
- Gunner
- Hunter
- Arcane
- Commander
- Engineer
- Alchemist
- Living

`CombatState.fresh(pillar)` creates the bounded resource profile for a pillar. Every mutation returns a new state and increments its revision. Costs are atomic: an unaffordable cost leaves the state unchanged. `CombatState.snapshot()` is the stable resource view attached to an `ActionContext`, so triggered effects evaluate the values captured when the root action began rather than later provider mutations.

The first implemented gameplay slice is Martial guard resolution:

- A missed guard receives full health damage and some posture pressure.
- A held guard converts the hit into stamina cost, reduced health damage and posture pressure.
- A perfect guard heavily discounts stamina/posture cost, prevents health damage, grants momentum and creates a 12-tick counter window.
- An unaffordable guard or a full posture meter produces a guard break.
- Recovery rates differ in and out of combat.

These rules are pure Java and provider-independent. The intended modpack uses Epic Fight as its action and enemy/boss moveset provider. An Epic Fight adapter must translate guard outcomes into the ARPG rules on the logical server without applying damage or resource costs twice. That bridge is not implemented yet; the current adapter below handles vanilla shields.

Combat state is stored in each player's persistent `PlayerData` under a versioned `combat` compound. Existing saves without that compound migrate from their primary discipline (or to Martial when no primary is selected). Choosing a built-in primary discipline selects its default grammar: Warrior/Rogue/Templar use Martial, Ranger uses Hunter, and Arcanist/Shaman use Arcane. Explicit pillar changes reset the target pillar to its canonical meters, so stamina or mana cannot leak between grammars.

The server sends an immutable combat snapshot on login and after every runtime mutation. The ARPG hub displays the active pillar and meter values from that snapshot. Useful diagnostics are:

- `/arpg combat status`
- `/arpg combat pillar set <pillar>` (operator)
- `/arpg combat resource set <resource> <value>` (operator)
- `/arpg combat reset` (operator)

All gameplay adapters should mutate state through `ArpgCombatRuntime.update`. That entry point rejects accidental pillar changes and synchronizes successful mutations to the client.

### Martial NeoForge adapter

The first loader-owned gameplay adapter now binds Martial state to NeoForge's server damage pipeline:

- `LivingShieldBlockEvent` preserves vanilla direction and shield-bypass decisions through `getOriginalBlock()`.
- Raising a valid guard opens a four-tick perfect-parry window; later valid blocks use the held-guard result.
- Hits from an attacker that fail the vanilla block check are treated as missed guards and add posture without changing the incoming damage.
- Held guards block only the amount returned by `MartialCombatSemantics`; perfect parries block the full hit and do not damage the shield.
- Guard breaks force the active item down, preventing a broken guard from remaining visually raised.
- A perfect parry creates one 12-tick counter opportunity. The next successful basic melee attack consumes it and deals 1.5x damage. Perfect parry, guard break and counter consumption are confirmed in the action bar.
- Stamina, posture and momentum recovery is evaluated every ten server ticks. Combat recovery remains active for five seconds after a resolved incoming attack; otherwise the faster resting rates apply.

Short timing windows live in `MartialCombatWindow` and are cleared on death, logout or pillar change. They are intentionally transient: persistent saves contain resource meters, not stale parry or counter opportunities.

### Gunner NeoForge adapter and provider contract

Gunner now has a second, deliberately different combat loop. It combines Remnant-like reload decisions with Doom-like pressure and close-range aggression:

- A provider-authorized shot atomically spends ammunition and builds heat. Empty, jammed, reloading and overheated weapons cannot fire.
- Projectile hits build momentum. Hits within eight blocks grant 50% more, encouraging aggressive movement instead of distant passive shooting.
- Momentum provides up to 25% additional basic-projectile damage. It decays slowly in combat and quickly after the five-second combat memory expires.
- Projectile kills grant momentum and refund two ammunition, making forward kill chains partially self-sustaining.
- Reloading takes 40 ticks by default. Completing it normally refills ammunition and vents 20 heat.
- Pressing reload again during the 55%-70% timing window performs a perfect reload: it refills ammunition, vents 40 heat and grants 20 momentum.
- Missing the active window cancels the reload and jams the weapon for 15 ticks.
- Reaching maximum heat locks firing until heat falls to 60%. Reloading remains available while overheated, so a perfect reload can act as an emergency vent.
- Heat cools faster out of combat. Reload, jam and overheat-lock timing is transient and is cleared on death, logout or pillar change.

Optional firearm integrations must call `GunnerCombatRuntime.fire(player, shotInput)` on the logical server before creating a projectile or resolving a hitscan shot. Only a `FIRED` result authorizes the provider action. Providers start and resolve the minigame through `startReload` and `attemptActiveReload`; they must not separately mutate ammunition, heat or momentum. The NeoForge adapter recognizes vanilla and `ProjectileEntity`-based projectiles automatically. Hitscan or custom non-projectile damage providers should report confirmed damage and kills through `registerProjectileHit` and `registerProjectileKill`.

Operator diagnostics can exercise the provider contract without installing a gun mod:

- `/arpg combat gunner fire`
- `/arpg combat gunner reload start`
- `/arpg combat gunner reload active`

## Sandbox craftwork

The initial module/knowledge/mastery and craftwork implementation is documented in [SANDBOX_CRAFTWORK.md](SANDBOX_CRAFTWORK.md). It includes thermal forging and rune-route evaluators, persistent quality, data-pack extension definitions and a NeoForge command prototype. External machine recipe adapters and a visual station UI remain separate implementation work.
