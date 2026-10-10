# Opt-in tower demo

This development data pack supplies three enclosed tower rooms in dedicated void dimensions. The first-clear-gated lodestone passage connects the entry room to a blackstone room containing a placeholder Tower Sentinel; its dedicated kill receipt unlocks a quartz sanctum. The prototype exercises construction, sector protection, Immersive Portals and the existing first-clear ledger together. It is not a generated overworld monolith or a finished dungeon.

## Install and run

1. Install the current NeoForge mod JAR and Immersive Portals 6.0.7 with its dependencies on server and clients.
2. Download the `tower-demo-datapack` CI artifact. Extract its outer artifact ZIP and put the inner `tower-demo-datapack.zip` in the test world's `datapacks` folder.
3. Restart the server so it registers the three new dimensions. This pack overrides the whole tower-link catalog with its demo route and sectors; other tower definitions are not merged automatically.
4. As an operator, run `/function puffish_skills:tower_demo/build`. It starts construction explicitly. Installing the pack alone does not build rooms or teleport players.
5. After the ready message, run `/function puffish_skills:tower_demo/visit` as a player. It enters the first room only when construction is complete.
6. Use survival mode and `/arpg choose primary warrior` for the initial melee test. If the first rift is not cleared, run `/rift enter arpg:first_rift`, defeat its Warden and return. The tower passage refuses activation before that first clear.
7. Solve the entry relay sequence by right-clicking the floor blocks at `(96,64,104)` (copper), `(100,64,104)` (amethyst), then `(104,64,104)` (gold). Wait at least four ticks between clicks and finish within ten seconds between inputs. A wrong relay resets the attempt. Each player needs their own solve; it gives no XP or tree points. Walk to the centre lodestone at block `(100,64,100)` and right-click it. Approach the entry portal from positive Z towards negative Z. Cross into the second room and fight the Sentinel.
8. Inspect `/arpg completions`. Killing the Sentinel creates `arpg:tower_sentinel`, a dedicated zero-point receipt requiring the first rift. The depth-room lodestone at `(100,64,104)` opens `arpg:tower_demo_sanctum` only after that kill. Approach it from positive Z towards negative Z and cross into the quartz sanctum. An unrelated world-boss receipt cannot unlock it. Characters who killed the old demo Sentinel need another kill for this new receipt; previously awarded points remain intact.
9. Return from the sanctum through its centre portal, approaching from negative Z towards positive Z; its centre lodestone reopens the sanctum pair if needed. In the depth room, click the original centre lodestone `(100,64,100)` to reopen the entry route. For that return, approach the second room's portal from negative Z towards positive Z. If your pair expired, click that room's lodestone to reopen it. `/tower close` ends your personal pair.

The room dimension IDs are `puffish_skills:tower_demo_entry`, `puffish_skills:tower_demo_depth` and `puffish_skills:tower_demo_sanctum`. The portal IDs are `arpg:tower_demo_descent` and `arpg:tower_demo_sanctum`. All room shells occupy blocks `(93,64,93)` through `(107,80,107)`. They have clear interiors, solid landing strips and lighted floors. Room materials differ so the destination is visually identifiable through the portal.

The Sentinel is a dedicated Husk-derived entity with a baseline 120 maximum health and 7 attack damage, not a bespoke tower boss. With pinned L2 Hostility installed, fresh dedicated Sentinels use the authored level-20 cap and Tank-I profile, with reduced native scaling weights; `/arpg threat` shows the actual initialized values. See [ENCOUNTER_PROGRESSION.md](ENCOUNTER_PROGRESSION.md) for provider limits, persistence and tuning. L2/Apotheosis metadata still contributes to the existing bounded encounter XP calculation. Public-world combat currently credits the killer; party contribution credit and personal boss instances are later work. Death inside any authored room now returns the player to the safe entry checkpoint after respawn. Death drops enter a private saved queue and are inserted into inventory there; overflow stays queued for `/tower recover`. Solves and first-clear receipts remain intact. Vanilla XP loss and death rules remain in effect. Checkpoint recovery returns to the entry room. To leave, right-click the crying-obsidian floor marker at `(100,64,106)` or stand within three blocks and use `/tower leave`. Claim pending recovery stacks first. Departure checks a clear loaded landing near overworld spawn and preserves game mode. A blocked/unloaded spawn leaves you in the tower with an explanatory message. Bed and respawn-anchor workflows are still absent. Keep this in a development world and retain operator access for the test.

Solves persist in the server player save across reconnects/death. Changing the ordered puzzle definition invalidates its receipt, including while a personal portal is open. Old demo worlds with completed construction can add the exit marker using `/function puffish_skills:tower_demo/restore_exit`. They can add the three relay blocks using the operator function `/function puffish_skills:tower_demo/restore_relays`; after a server restart, `/build` detects a finished legacy layout and adds the third room without rebuilding the original two. Move players out during the upgrade. It restores authored floor fixtures, migrates a living legacy Sentinel marker, and records layout version 2 only when finished. Subsequent builds of layout 2 are no-ops.

## Construction and lifecycle

Construction uses 52 scheduled stages, shared across all three rooms, with at most 225 block writes per stage. The dispatcher checks stage numbers in descending order so incrementing a stage cannot cascade the rest of the build in one tick. It waits until all twelve room chunks are loaded before writing. The nominal build takes about three seconds after loading, subject to server tick rate.

The builder writes only inside its three dedicated room bounds. It never edits the overworld, Nether, End or reserved solo-rift dimension. A persistent command-storage marker stops repeated `/build` calls from rebuilding completed rooms; the same call resumes scheduling for an interrupted build without resetting its stage. Ordinary world saves retain the schedule/storage, without promising atomic recovery from a hard process crash.

Twelve chunks are explicitly force-loaded to keep all endpoints available. To stop that test load, run `/function puffish_skills:tower_demo/release_chunks`; it also stops construction scheduling. Run `/function puffish_skills:tower_demo/resume` to restore those tickets and resume any incomplete build. Release does not remove rooms, player receipts or the dimension definitions. Move players out and release tickets before disabling the test pack; dimension removal/backup management is separate.

After the Sentinel dies, an operator can use `/function puffish_skills:tower_demo/spawn_sentinel` to replay the combat test. It refuses a duplicate living Sentinel. Replays can produce encounter XP/ordinary loot, but the dedicated per-player receipt prevents duplicate completion settlement and never grants tree points. The control functions are operator tools, not ordinary-player reward claims.

## Build and verification

```bash
python3 tools/test_tower_demo.py
python3 tools/build_tower_demo.py
```

The output is `build/tower-demo-datapack.zip`. CI uploads it separately from the mod JAR. The generator uses Minecraft 1.21 data-pack format 48 and singular `function` folders. Its deterministic ZIP puts `pack.mcmeta` at the root.

Automated tests reconstruct the finished rooms and verify enclosure, spawn/landing clearance, dimension isolation, per-stage write budgets, dispatcher behavior, chunk-ticket scope, function references, progression markers, provider-independent catalog definitions and reproducible packaging. These are structural/tooling tests, not a Minecraft command-parser or in-game certification. Minecraft function parsing, dimension registration, the sentinel, actual room creation, portal rendering, combat, checkpoint return, keepInventory/grave-mod interactions, death/reconnect and mod compatibility still need a client/dedicated-server smoke test. See `TOWER_PORTALS.md` for the event-protection limits.

## Epic Fight Sentinel

The updated demo spawns `puffish_skills:tower_sentinel`, a separate husk-backed entity type registered by the mod JAR. Install the updated JAR and restart before using this pack version. Enable the Epic Fight profile described in `EPIC_FIGHT_INTEGRATION.md` for its baseline attack sequences and low-health additions. The patch affects only this entity type. Without Epic Fight it retains vanilla husk behavior for development. Custom sweep/slam/charge animations remain pending. The dedicated Sentinel now has a boss health bar and an Enraged attack pool below half health; healing to half health restores the Guarding label.

A living legacy tagged husk blocks new spawning and keeps its existing behavior. After it dies, `/function puffish_skills:tower_demo/spawn_sentinel` creates the dedicated type. No living entity is deleted or converted during an upgrade. Retagging preserves the dedicated completion receipt; replay cannot pay it again. The new entity has an explicit rotten-flesh loot table.

With Iron’s installed, the Sentinel also uses a telegraphed native Icicle against ranged player targets. Its blue boss bar and snowflakes warn before release; hit it, move close, break line of sight or sidestep the locked aim. See [ENEMY_ABILITIES.md](ENEMY_ABILITIES.md) for provider ownership, interruption, cooldown persistence and smoke tests.
