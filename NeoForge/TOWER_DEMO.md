# Opt-in tower demo

This development data pack supplies two enclosed tower rooms in dedicated void dimensions. The first-clear-gated lodestone passage connects the entry room to a blackstone room containing a placeholder Tower Sentinel. The prototype exercises construction, sector protection, Immersive Portals and the existing first-clear ledger together. It is not a generated overworld monolith or a finished dungeon.

## Install and run

1. Install the current NeoForge mod JAR and Immersive Portals 6.0.7 with its dependencies on server and clients.
2. Download the `tower-demo-datapack` CI artifact. Extract its outer artifact ZIP and put the inner `tower-demo-datapack.zip` in the test world's `datapacks` folder.
3. Restart the server so it registers the two new dimensions. This pack overrides the whole tower-link catalog with its demo route and sectors; other tower definitions are not merged automatically.
4. As an operator, run `/function puffish_skills:tower_demo/build`. It starts construction explicitly. Installing the pack alone does not build rooms or teleport players.
5. After the ready message, run `/function puffish_skills:tower_demo/visit` as a player. It enters the first room only when construction is complete.
6. Use survival mode and `/arpg choose primary warrior` for the initial melee test. If the first rift is not cleared, run `/rift enter arpg:first_rift`, defeat its Warden and return. The tower passage refuses activation before that first clear.
7. Walk to the centre lodestone at block `(100,64,100)` and right-click it. Approach the entry portal from positive Z towards negative Z. Cross into the second room and fight the Sentinel.
8. Inspect `/arpg completions` and `/arpg progression`. Killing the Sentinel uses the authored `arpg:first_world_boss` completion, granting its first-clear rewards only if that receipt has not already been earned. It does not give another award to a character who already cleared that completion elsewhere.
9. For the return, approach the second room's portal from negative Z towards positive Z. If your pair expired, click that room's lodestone to reopen it. `/tower close` ends your personal pair.

The room dimension IDs are `puffish_skills:tower_demo_entry` and `puffish_skills:tower_demo_depth`. The portal ID is `arpg:tower_demo_descent`. Both room shells occupy blocks `(93,64,93)` through `(107,80,107)`. They have clear interiors, solid landing strips and lighted floors. Room materials differ so the destination is visually identifiable through the portal.

The Sentinel is a vanilla Husk with a baseline 120 maximum health and 7 attack damage, not a bespoke tower boss. The normal L2/Apotheosis adapters can contribute encounter difficulty and XP. Public-world combat currently credits the killer; party contribution credit, personal boss instances and tower death recovery are later work. The void dimensions have no sandbox exit, bed or respawn-anchor workflow yet. Keep this in a development world and retain operator access for the test.

## Construction and lifecycle

Construction uses 35 scheduled stages, shared across both rooms, with at most 225 block writes per stage. The dispatcher checks stage numbers in descending order so incrementing a stage cannot cascade the rest of the build in one tick. It waits until all eight room chunks are loaded before writing. The nominal build takes about two seconds after loading, subject to server tick rate.

The builder writes only inside its two dedicated room bounds. It never edits the overworld, Nether, End or reserved solo-rift dimension. A persistent command-storage marker stops repeated `/build` calls from rebuilding completed rooms; the same call resumes scheduling for an interrupted build without resetting its stage. Ordinary world saves retain the schedule/storage, without promising atomic recovery from a hard process crash.

Eight chunks are explicitly force-loaded to keep both endpoints available. To stop that test load, run `/function puffish_skills:tower_demo/release_chunks`; it also stops construction scheduling. Run `/function puffish_skills:tower_demo/resume` to restore those tickets and resume any incomplete build. Release does not remove rooms, player receipts or the dimension definitions. Move players out and release tickets before disabling the test pack; dimension removal/backup management is separate.

After the Sentinel dies, an operator can use `/function puffish_skills:tower_demo/spawn_sentinel` to replay the combat test. It refuses a duplicate living Sentinel. Replays can produce encounter XP/ordinary loot, but the existing per-player first-clear receipt prevents duplicate tree-point payouts. The control functions are operator tools, not ordinary-player reward claims.

## Build and verification

```bash
python3 tools/test_tower_demo.py
python3 tools/build_tower_demo.py
```

The output is `build/tower-demo-datapack.zip`. CI uploads it separately from the mod JAR. The generator uses Minecraft 1.21 data-pack format 48 and singular `function` folders. Its deterministic ZIP puts `pack.mcmeta` at the root.

Automated tests reconstruct the finished rooms and verify enclosure, spawn/landing clearance, dimension isolation, per-stage write budgets, dispatcher behavior, chunk-ticket scope, function references, progression markers, provider-independent catalog definitions and reproducible packaging. These are structural/tooling tests, not a Minecraft command-parser or in-game certification. Minecraft function parsing, dimension registration, the sentinel, actual room creation, portal rendering, combat, death/reconnect and mod compatibility still need a client/dedicated-server smoke test. See `TOWER_PORTALS.md` for the event-protection limits.
