package net.puffish.skillsmod.arpg.tower;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TowerCatalogTest {
	@Test
	void gateAndAnchorRegressionChecks() {
		TowerEngineChecks.main(new String[0]);
	}

	@Test
	void bundledNetworkIsOptIn() throws Exception {
		try (var reader = new InputStreamReader(getClass().getResourceAsStream(
				"/data/puffish_skills/arpg/tower_links.json"), StandardCharsets.UTF_8)) {
			var definitions = new Gson().fromJson(reader, TowerData.Definitions.class);
			assertTrue(new TowerCatalog(definitions.links(), Set.of()).links().isEmpty());
			assertEquals(0, new TowerProtection(definitions.sectors(), new TowerCatalog(definitions.links(), Set.of())).size());
		}
	}

	@Test
	void legacySchemaWithoutSectorsStillLoads() {
		var definitions = new Gson().fromJson("{\"schema\":1,\"links\":[]}", TowerData.Definitions.class);
		assertEquals(null, definitions.sectors());
		assertEquals(null, definitions.puzzles());
		assertEquals(null, definitions.recovery());
		assertTrue(new TowerCatalog(definitions.links(), Set.of()).links().isEmpty());
	}

	@Test
	void authoredPuzzleRecordDeserializesWithOrderedRelays() {
		var json = """
				{"id":"arpg:relays","link":"arpg:descent","sequence":[
				{"dimension":"minecraft:overworld","x":1,"y":64,"z":4,"block":"minecraft:copper_block"},
				{"dimension":"minecraft:overworld","x":4,"y":64,"z":4,"block":"minecraft:gold_block"}]}
				""";
		var puzzle = new Gson().fromJson(json, TowerPuzzle.class);
		assertEquals(2, puzzle.sequence().size());
		assertEquals("minecraft:copper_block", puzzle.sequence().get(0).block());
		assertEquals(64, puzzle.sequence().get(1).y());
		assertEquals(64, puzzle.fingerprint().length());
	}

	@Test
	void providerGeometryKeepsPairInverseAndOwnerRestricted() {
		var from = new TowerLink.Anchor("minecraft:overworld", 10.5, 66.5, 20.5);
		var to = new TowerLink.Anchor("minecraft:the_nether", 100.5, 66.5, 200.5);
		var link = new TowerLink("arpg:descent", from, to, 3, 3, 1, Set.of());
		var owner = UUID.randomUUID();
		var forward = TowerPortalNbt.create(link, owner, false);
		var reverse = TowerPortalNbt.create(link, owner, true);
		assertEquals(to.dimension(), forward.getString("dimensionTo"));
		assertEquals(from.dimension(), reverse.getString("dimensionTo"));
		assertEquals(to.x(), forward.getDouble("destinationX"));
		assertEquals(from.x(), reverse.getDouble("destinationX"));
		assertEquals(from.x(), forward.getList("Pos", 6).getDouble(0));
		assertEquals(to.x(), reverse.getList("Pos", 6).getDouble(0));
		assertEquals(1, forward.getDouble("axisWX"));
		assertEquals(-1, reverse.getDouble("axisWX"));
		assertEquals(1, forward.getDouble("axisHY"));
		assertEquals(1, reverse.getDouble("axisHY"));
		assertEquals(owner, new UUID(forward.getLong("specificPlayerMost"), forward.getLong("specificPlayerLeast")));
		assertTrue(forward.getBoolean("teleportable"));
		assertFalse(forward.getBoolean("interactable"));
		assertFalse(forward.getBoolean("teleportChangesScale"));
		assertFalse(forward.getBoolean("teleportChangesGravity"));
		assertEquals(1, forward.getDouble("scale"));
		assertFalse(forward.contains("commandsOnTeleported"));
		assertThrows(IllegalArgumentException.class, () -> TowerPortalNbt.create(link, new UUID(0, 0), false));
	}
}
