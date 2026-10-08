# Tower dimension network prototype

The monolith is a network of authored spaces. A passage can carry players from an overworld tower into a Nether sector, through a second tower into another installed dimension, and back through an unlocked shortcut. Branches and cycles are valid; first-clear receipts gate each link. Crossing a portal never grants XP, points or completion rewards. Encounter rewards remain server-owned.

This slice adds an optional Immersive Portals entity adapter and data-pack link definitions. It does not generate towers, rooms, destination dimensions or landing platforms. The bundled network is empty so installing this code does not change an existing world. The command prototype is deliberately separate from the reserved solo-rift dimension and its recovery lifecycle.

## Provider

The CurseForge Forge project lists `immersive_portals-6.0.7-all.jar` for NeoForge 1.21.1 (2025-06-18). Install it and its dependencies on server and clients. The earlier starter assessment that no matching release existed was incorrect. Availability does not establish compatibility with Epic Fight, Create, rendering mods or the intended magic mods.

Source reference: https://github.com/iPortalTeam/ImmersivePortalsModForNeo/tree/1.21

The adapter resolves `immersive_portals:portal` through the entity registry and supplies the provider's serialized geometry keys. It does not invoke commands, reflect into provider classes or bundle that mod. The provider source confirms `axisWX/Y/Z`, `axisHX/Y/Z`, `dimensionTo`, `destinationX/Y/Z`, `specificPlayerMost/Least`, `interactable`, `scale` and teleport flags. These keys are version-specific; another fork requires a combined-mod smoke test. No teleport fallback is used when the provider is absent.

## Authoring a seam

Override `data/puffish_skills/arpg/tower_links.json` in a data pack. This complete example uses existing vanilla dimensions:

```json
{
  "schema": 1,
  "links": [
    {
      "id": "arpg:monolith_descent",
      "from": {"dimension": "minecraft:overworld", "x": 100.5, "y": 66.5, "z": 100.5},
      "to": {"dimension": "minecraft:the_nether", "x": 100.5, "y": 66.5, "z": 100.5},
      "width": 3,
      "height": 3,
      "minimumLevel": 1,
      "prerequisites": ["arpg:first_rift"]
    }
  ]
}
```

Prepare and load both rooms before opening. Anchor coordinates describe portal centres, not player feet. In this example the aperture bottom is Y=65 and landing floors are at Y=64. Both sides require air across the full aperture and the adjacent landing strips, solid full-cube floors, empty fluids, no magma floor and world-border clearance. Validation refuses unloaded chunks and never writes blocks. Existing unrelated portals are not altered.

Reload the data pack, run `/tower status`, stand within eight blocks of either anchor and run `/tower open arpg:monolith_descent`. The forward face is approached from positive Z towards negative Z; the return face is approached from negative Z towards positive Z. This prototype uses aligned vertical X/Y apertures and translation only: rotated passages, horizontal shafts and scaling are future extensions. Build rooms so these approach directions are accessible. `/tower close` closes your pair.

Links validate IDs, finite bounded coordinates, aperture size, target-dimension separation, duplicate definitions and known completion prerequisites. No solo-rift arena can be a tower endpoint. Failed reload retains the previous network; successful reload expires existing pairs.

Each player gets at most one two-entity pair, with 16 pairs active globally. The provider's specific-player restriction blocks other players, mobs and items from crossing. Cross-portal block interaction is disabled. Pairs expire after five minutes and close on disconnect, death, creative/spectator mode, a rift session, lost eligibility, unsafe geometry, provider removal or moving more than 48 blocks from both anchors. Reopen a nearby eligible link to return later. Closing a link leaves the player in the destination world; these are public authored spaces, not sessions that automatically recover or respawn players. Destination respawn policy, protection and alternate exits must be designed with those rooms.

Saved portal entities are rejected when loaded so a restart cannot preserve an obsolete gate. Ordinary ungated portals created by operators or other mods are outside this system; world protection and server permissions must prevent them bypassing authored progression. Parties, automatic physical activation, encounter-aware tower recovery and room protection are not implemented yet.

## Validation

`python3 tools/test_tower_engine.py` covers level and first-clear gates, active-rift exclusion, death/mode exclusion, anchor proximity in both worlds, invalid geometry, reserved-dimension rejection, duplicate links, unknown gates and cyclic networks. CI also parses the default network and compiles the Common/NeoForge integration.

Required in-game checks: install the actual provider on a client and dedicated server; load both rooms; test both directions, a player without the first-clear, another player at the same seam, thrown items, death, logout/restart, reload, unsafe floors and unloaded anchors. Verify rendering and collisions alongside the selected rendering and combat mods. Compilation alone does not verify seamless rendering or provider NBT behavior.
