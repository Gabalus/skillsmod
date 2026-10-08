# First-clear currencies and recipe discoveries

Character XP now controls level and level-based class/specialization eligibility. It does not grant passive or confluence tree points. New points come from authored rift/world-boss completion receipts; ascendancy points still come from validated trials. Craftwork mastery remains a separate sandbox track.

## Bundled first clears

| Completion | Passive points | Confluence points | Discovery / trial |
| --- | --- | --- | --- |
| `arpg:first_rift` | 1 | 0 | Metalworking |
| `arpg:rune_rift` | 1 | 0 | Inscription; requires first rift |
| `arpg:first_world_boss` | 2 | 1 | First eligible Apotheosis invader, or authored boss |
| `arpg:trial_1` through `arpg:trial_4` | 0 | 0 | Trials at levels 30, 50, 70, 90; sequential |

Each completion ID pays once per player. A receipt stores the actual awarded points, trial number and recipe discoveries. Changing reward amounts, removing/reintroducing a data-pack definition or replaying another instance with the same ID cannot pay again. Additional first clears require new stable IDs. Passive currency caps at 1,500 and confluence currency at 12; receipts record reduced awards at those caps. Earned confluence points remain stored before a secondary discipline is chosen.

## Recipe discovery and study

Completion receipts retain discoveries independently of whether the player can learn them yet. Available discoveries are studied automatically on completion. `/craftwork study`, `/craftwork study arpg:inscription`, or the crafting screen's **Study recipes** button attempts study later. Study still enforces normal knowledge prerequisites, mastery requirements and provider/version availability. Players cannot study recipes they have not earned. Operator `/craftwork learn` remains an explicit development/admin bypass.

For example, clearing the rune rift discovers inscription. If smithing mastery is below 10, the discovery remains pending rather than preventing the encounter reward. After forging enough to meet the requirement, studying unlocks inscription. This is a persistent recipe entitlement, not a physical scroll item; inventory scroll drops/trading remain future work.

## Authored encounter API

Trusted controllers call `CompletionRuntime.complete(player, completionId)`. The method checks primary discipline, character level, prior first clears and trial readiness before minting a receipt. There is no ordinary-player command to claim a completion.

The NeoForge adapter also reads one entity command tag of the form `arpg:completion=arpg:first_rift`. That tag is an explicit authored encounter marker and permits encounter XP even outside configured dungeon dimensions. Completion settlement runs on the post-death drop event; cancellation of item drops does not cancel completion credit, while a canceled death never reaches this event. Conflicting completion markers reject the completion reward. Fake/creative/spectator players and player victims are excluded. A per-entity settlement flag plus the per-player receipt prevents replay.

Eligible Apotheosis invaders default to `arpg:first_world_boss`. Disable automatic invader completion rewards by setting `apotheosisWorldBoss` to an empty string in the completion catalog. Encounter XP eligibility still follows its separate policy. A generic `arpg:world_boss` tag grants XP eligibility, but requires a completion marker to identify its authored first-clear reward.

Operator test commands:

```text
/arpg choose primary warrior
/arpg completion grant @s arpg:first_rift
/arpg completion grant @s arpg:rune_rift
/arpg completions
/craftwork open
```

Repeat the grant: it must report already settled and leave points unchanged. To test the entity path, in survival mode:

```text
/summon minecraft:zombie ~ ~ ~3
/tag @e[type=minecraft:zombie,sort=nearest,limit=1,distance=..8] add arpg:completion=arpg:first_rift
```

Kill the zombie. Use a fresh character or a new authored completion ID to test a first award. The grant command can target any online player and is operator-only; normal players use `/arpg completions` to inspect progress.

## Data packs and persistence

Override `data/puffish_skills/arpg/completions.json`, then `/reload`. It defines schema 1, the automatic Apotheosis completion ID and a list of reward definitions. The catalog rejects duplicate IDs, missing/cyclic prerequisites, excessive graph depth, invalid reward amounts/trial levels, unknown knowledge IDs and invalid automatic boss mappings. Failed reloads preserve the previous catalog. Old receipts survive missing or edited definitions. This is a whole-file override; preserve existing IDs when adding content.

Character saves use schema 2. Schema 1 migrates once by snapshotting the previous level/campaign-derived passive budget and any existing secondary's confluence budget. Existing XP, class choices, trials, maps and specializations survive. The migrated balance stays fixed when later levels or milestones change. New characters have no migration grant. Future or malformed save schemas fail explicitly rather than erasing progress.

These budgets feed the existing `arpg_progression` point source; existing category allocations are retained. Completion receipt history is bounded at 4,096 entries. Additional long-term seasons/migrations need an explicit retention policy before exceeding that bound. Ordinary Minecraft player/world saving is not a hard-crash transaction; recovery testing is still required.

## Verification and limits

```bash
python3 tools/test_completion_rewards.py
python3 tools/test_encounter_progression.py
python3 tools/test_sandbox_engine.py
```

47 completion checks cover replay and edited rewards, independent XP/point currencies, caps, recipe retention, prerequisite/trial gates and graph validation. Common JUnit adds receipt round trips, one-time legacy migration, bundled JSON checks, malformed receipt rejection and future-schema rejection. GitHub CI runs these with Java 21 and builds NeoForge.

A bounded solo arena prototype now calls this reward API through `/rift enter arpg:first_rift` and `/rift enter arpg:rune_rift`; see [RIFT_PROTOTYPE.md](RIFT_PROTOTYPE.md). Authored dungeon rooms, the monolith, party contribution credit, physical recipe scrolls, completion UI beyond commands and combined-provider in-game testing remain future work.
