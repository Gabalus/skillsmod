# Tower dimension network prototype

For a generated, installable two-room development route, see `TOWER_DEMO.md`. Its separate opt-in data pack supplies the rooms and overrides the empty default catalog.

The monolith is a network of authored spaces. A passage can carry players from an overworld tower into a Nether sector, through a second tower into another installed dimension, and back through an unlocked shortcut. Branches and cycles are valid; first-clear receipts gate each link. Crossing a portal never grants XP, points or completion rewards. Encounter rewards remain server-owned.

This slice adds an optional Immersive Portals entity adapter, data-pack link definitions, authored protection sectors and physical lodestone activation. It does not generate towers, rooms, destination dimensions or landing platforms. The bundled network is empty so installing this code does not change an existing world. The command prototype is deliberately separate from the reserved solo-rift dimension and its recovery lifecycle.

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
  ],
  "sectors": [
    {"id":"arpg:entry_room", "dimension":"minecraft:overworld", "minX":90, "minY":64, "minZ":90, "maxX":110, "maxY":80, "maxZ":110},
    {"id":"arpg:nether_room", "dimension":"minecraft:the_nether", "minX":90, "minY":64, "minZ":90, "maxX":110, "maxY":80, "maxZ":110}
  ]
}
```

Prepare and load both rooms before opening. Anchor coordinates describe portal centres, not player feet. In this example the aperture bottom is Y=65 and landing floors are at Y=64. Both sides require air across the full aperture and the adjacent landing strips, solid full-cube floors, empty fluids, no magma floor and world-border clearance. Validation refuses unloaded chunks and never writes blocks. Existing unrelated portals are not altered.

Reload the data pack, run `/tower status`, stand within eight blocks of either anchor and run `/tower open arpg:monolith_descent`. For physical activation, place a lodestone at the anchor floor centre before enabling protection: `(100,64,100)` in each example room. Right-click it with the main hand to open the same gated passage. The forward face is approached from positive Z towards negative Z; the return face is approached from negative Z towards positive Z. This prototype uses aligned vertical X/Y apertures and translation only: rotated passages, horizontal shafts and scaling are future extensions. Build rooms so these approach directions are accessible. `/tower close` closes your pair.

Links validate IDs, finite bounded coordinates, aperture size, target-dimension separation, duplicate definitions and known completion prerequisites. No solo-rift arena can be a tower endpoint. Failed reload retains the previous network; successful reload expires existing pairs.

Each player gets at most one two-entity pair, with 16 pairs active globally. The provider's specific-player restriction blocks other players, mobs and items from crossing. Cross-portal block interaction is disabled. Pairs expire after five minutes and close on disconnect, death, creative/spectator mode, a rift session, lost eligibility, unsafe geometry, provider removal or moving more than 48 blocks from both anchors. Reopen a nearby eligible link to return later. Closing a link leaves the player in the destination world; these are public authored spaces, not sessions that automatically recover or respawn players. Destination respawn policy, protection and alternate exits must be designed with those rooms.

Saved portal entities are rejected when loaded so a restart cannot preserve an obsolete gate. Ordinary ungated portals created by operators or other mods are outside this system; world protection and server permissions must prevent them bypassing authored progression. Parties, automatic proximity activation and encounter-aware tower recovery are not implemented yet.

## Validation

`python3 tools/test_tower_engine.py` covers level and first-clear gates, active-rift exclusion, death/mode exclusion, anchor proximity in both worlds, invalid geometry, reserved-dimension rejection, duplicate links, unknown gates and cyclic networks. CI also parses the default network and compiles the Common/NeoForge integration.

Required in-game checks: install the actual provider on a client and dedicated server; load both rooms; test both directions, a player without the first-clear, another player at the same seam, thrown items, …3006 tokens truncated…til.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.puffish.skillsmod.arpg.rift.RiftRuntime;

import java.util.Set;

public final class TowerExitRuntime {
	private static final String ATTEMPT = "puffish_skills.tower_exit_attempt";

	private TowerExitRuntime() {
	}

	public static void leave(ServerPlayerEntity player, TowerExit exit) {
		if (exit == null || player instanceof FakePlayer || !player.isAlive() || player.isCreative() || player.isSpectator()
				|| RiftRuntime.inRifts(player.getWorld()) || RiftRuntime.book(player.server).get(player.getUuid()) != null
				|| !exit.nearby(player.getWorld().getRegistryKey().getValue().toString(), player.getX(), player.getY(), player.getZ())) {
			throw new IllegalStateException("Stand within three blocks of an authored tower exit in survival, outside a rift session");
		}
		if (!TowerRecoveryRuntime.mayTravel(player)) {
			throw new IllegalStateException("Claim pending items with /tower recover before leaving the tower");
		}
		var marker = exit.marker();
		var pos = new BlockPos(marker.x(), marker.y(), marker.z());
		if (!player.getServerWorld().isChunkLoaded(pos) || !Registries.BLOCK.getId(player.getWorld().getBlockState(pos).getBlock()).toString().equals(marker.block())) {
			throw new IllegalStateException("The authored exit marker is missing; ask an operator to restore it");
		}
		long now = player.server.getOverworld().getTime();
		var persistent = player.getPersistentData();
		if (persistent.contains(ATTEMPT) && now >= persistent.getLong(ATTEMPT) && now - persistent.getLong(ATTEMPT) < 40) {
			throw new IllegalStateException("Wait two seconds before retrying the tower exit");
		}
		persistent.putLong(ATTEMPT, now);
		var world = player.server.getOverworld();
		var spawn = world.getSpawnPos();
		var target = TowerLandingSearch.find(spawn.getX(), spawn.getY(), spawn.getZ(), point -> {
			var feet = new BlockPos(point.x(), point.y(), point.z());
			return !TowerData.protection().protects(world.getRegistryKey().getValue().toString(), point.x(), point.y(), point.z())
					&& TowerSafeLanding.safe(world, feet);
		}, (x, z) -> world.isChunkLoaded(new BlockPos(x, spawn.getY(), z))
				? world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) : Integer.MIN_VALUE);
		if (target == null) {
			throw new IllegalStateException("No clear loaded sandbox landing near overworld spawn. Ask an operator to restore/load it; you remain in the tower");
		}
		if (!player.teleport(world, target.x() + .5, target.y(), target.z() + .5, Set.of(), player.getYaw(), player.getPitch())) {
			throw new IllegalStateException("Tower departure failed; try again");
		}
		ImmersiveTowerPortals.close(player.getUuid());
		player.fallDistance = 0;
		player.sendMessage(Text.literal("Returned to the sandbox near overworld spawn."), false);
	}
}
