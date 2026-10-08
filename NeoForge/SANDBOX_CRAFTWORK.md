# Sandbox craftwork implementation — first code slice

Branch: `feat/sandbox-craftwork`, based on `667cd53` in Gabalus/skillsmod.

## Implemented

- Immutable integration catalog with namespaced IDs, module schemas, exact provider-version requirements, knowledge prerequisites, mastery requirements and cycle/duplicate/binding validation.
- Data-pack definitions at `data/<namespace>/arpg/sandbox/*.json`; validated reload publishes a replacement catalog or retains the previous one.
- Independent persistent player knowledge, bounded sandbox mastery, active craft session, and idempotent completion receipts. No combat experience or passive points are awarded by this subsystem.
- A minigame evaluator registry: `CraftMechanics.register(id, evaluator)` accepts bounded server-side results and process state. Existing sequence, thermal-forging and rune-routing evaluators are supplied.
- Thermal forging: heat to 1000 process units, then draw, heavy hammer and quench. Cooling follows server ticks; ideal action temperatures are 875, 775 and 600. Actions performed at the wrong temperature reduce quality. Units are game process values, not a metallurgical simulation.
- Rune routing: north/east/south/west/seal. Wrong directional strokes change the route; an unclosed route loses its sealing credit.
- Craft quality/provenance persisted in an item data component. Forging and inscription occupy separate stages, preserving previous work.
- Recipe authorization and baseline automated quality APIs. Automation requires the registered owner's state and cannot earn manual mastery. This API does not yet hook external machines.
- A NeoForge command prototype that binds a session to one held sword, requires an anvil nearby on each action, consumes an iron ingot or lapis on start, and applies the finishing result to that same item. Materials are spent on the attempt and not refunded on cancellation.

## Prototype walkthrough

Build with Java 21 after Gradle/Minecraft dependencies are available:

```bash
./gradlew :Common:test :NeoForge:build
```

In a development world, stand within two blocks horizontally and one vertically of an anvil. Hold one sword and keep an iron ingot in inventory. Operators can grant initial knowledge:

```text
/craftwork learn arpg:metalworking
/craftwork start arpg:forge
/craftwork act 0 heat
```

Wait about 125 server ticks (6.25 seconds at 20 TPS), then:

```text
/craftwork act 1 draw
```

Wait about 20 ticks (1 second), then:

```text
/craftwork act 2 heavy
```

Wait about 55 ticks (2.75 seconds), then:

```text
/craftwork act 3 quench
/craftwork finish
```

A perfect attempt awards 10 smithing mastery and quality 100. Inscription knowledge requires metalworking knowledge and at least 10 smithing mastery. Operators grant it for this prototype; eventual rifts will call the same knowledge API from authorized rewards:

```text
/craftwork learn arpg:inscription
/craftwork start arpg:inscribe
/craftwork act 0 north
/craftwork act 1 east
/craftwork act 2 south
/craftwork act 3 west
/craftwork act 4 seal
/craftwork finish
```

Keep lapis in inventory for inscription. Leave at least four server ticks between actions. `/craftwork` reports expected step, process values, mistakes, mastery and held-item quality. `/craftwork cancel` abandons an attempt. Each operation can finish an item once, preventing repeated mastery farming from the same finishing stage.

This is command-controlled equipment finishing. It is not yet the new blank-to-weapon production chain or the visual station UI.

## Extension points

A data pack adds modules, nodes and operations using the shape of bundled `core.json`. `requiredVersions` maps mod IDs to exact supported versions; `masteryRequirements` maps mastery IDs to required values. A new operation selects a registered mechanic and a namespaced knowledge/mastery track. Integrations register their evaluator before catalog loading. Data packs cannot execute Java or inject arbitrary scoring code.

External station/machine adapters must resolve ownership and inputs, call `SandboxState.requireRecipe` at server output creation, and preserve relevant `CraftedItemData` stages. A definition alone does not disable a mod's original recipes or gate its factories. New executable station bindings need compiled adapters; the prototype intentionally supports only forging and inscription on swords.

Existing sessions snapshot their sequence, mechanic ID and process state. Definitions reload atomically within the sandbox catalog. Existing player knowledge retains unknown IDs instead of being deleted when optional content disappears. Provider-version matching currently uses exact strings, not version ranges.

## Validation completed here

```bash
python3 tools/test_sandbox_engine.py
```

47 engine checks pass using the local Java compiler, with no Minecraft dependencies. They cover locked recipes, module/version validation, prerequisite cycles, mastery gates, replay/rate rejection, failed sessions, repeated settlement, automation quality limits, distinct finishing stages, thermal quality, route closure and custom mechanic registration. All 17 changed Java sources were also checked by the Java compiler's parser; this checks syntax, not Minecraft API compatibility.

JUnit persistence round-trip tests are included, but were not run here. The attempted Gradle compile/test command failed before project compilation because `services.gradle.org` was unreachable. This environment has Java 17; a complete NeoForge build requires Java 21. No Minecraft JAR, client smoke test or dedicated-server smoke test is claimed.

## Remaining integration work and limits

- Compile against actual Minecraft/NeoForge dependencies, run NBT tests and perform client/dedicated-server testing.
- Dedicated station interaction, recipe scroll loot/reward wiring and material blanks. The crafting screen is available through `/craftwork open`.
- Create, MIAPI, magic and firearm adapters, including their original recipe/output gates and cross-mod quality effects.
- Expand quality dimensions and connect wear/repair effects. Initial bounded melee/spell bonuses are now wired into ARPG action snapshots.
- Define production-scale receipt compaction and operation migration policies. Current receipt history grows with completed work.
- Inventory components and world persistent data use Minecraft's ordinary separate saves. Logical settlement is idempotent and recovery can reapply a saved receipt to a pending item, but this is not a crash-atomic transaction across player inventory and world data. Hard-crash rollback/lost-quality scenarios need explicit recovery testing before release.
- Minecraft administrators can still bypass knowledge with commands/creative access. Unknown future sandbox save schemas fail explicitly rather than being silently reset.
- Commands and provider discovery are currently wired for NeoForge; the shared pure engine and persistence are loader-neutral, but the Fabric command adapter is not implemented.

The existing full provider profile still includes Better Combat. This change does not select or install Epic Fight, Create, MIAPI or the gun mods. Use a separately reviewed modpack profile when assembling the intended Epic Fight stack.

## Crafting screen and data-driven finishing

Use `/craftwork open` to open the non-pausing screen. The screen shows discovered recipes, ingredient costs, smithing/runecraft mastery, process temperature or rune position, action buttons, mistakes, and held-item quality. Recipe selection cycles through the registered station bindings. A small server snapshot refreshes while the screen is open; button actions run through the same authenticated commands and server checks as the command prototype. The displayed heat is an estimate interpolated between snapshots; the server decides action timing and quality. Up to 64 station recipes and eight action buttons are shown; longer action sets remain command-accessible.

`bindings` entries in `core.json` define operation, eligible item tag, nearby station block tag, ingredient item and quantity. This removes hardcoded swords/anvils/materials from the runtime. Bindings validate operation references and ingredient IDs. Materials can be drawn from multiple inventory stacks; the target item and other pending craftwork items cannot pay the ingredient cost. Knowledge, mastery, provider and station gates are still enforced on the server.

Quality now has bounded effects through the existing ARPG action-stat snapshots. The held item's forging quality adds up to 15% increased melee damage; inscription adds up to 8% increased spell damage. Unworked items add nothing. Offhand quality is not stacked, and switching the held item changes the next action snapshot. These are tuning defaults, not a finalized balance model. Physical wear/repair and external machines remain separate work.

The new Sandbox craftwork CI builds and tests the feature branch with Java 21 and uploads its NeoForge JAR if the build succeeds. The branch also carries the prior local combat-pillar, Martial and Gunner work that had not reached the remote ARPG core branch.
