package net.puffish.skillsmod.arpg.rift;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Resource contract checks; registry-codec loading requires the transformed loader runtime. */
class RiftDimensionTest {
	@Test
	void voidDimensionContainsArenaWithoutGeneratingSandboxTerrain() throws Exception {
		try (var typeReader = new InputStreamReader(getClass().getResourceAsStream(
				"/data/puffish_skills/dimension_type/rifts.json"), StandardCharsets.UTF_8);
				var worldReader = new InputStreamReader(getClass().getResourceAsStream(
						"/data/puffish_skills/dimension/rifts.json"), StandardCharsets.UTF_8)) {
			var type = JsonParser.parseReader(typeReader).getAsJsonObject();
			int minY = type.get("min_y").getAsInt();
			int height = type.get("height").getAsInt();
			assertEquals(0, minY % 16);
			assertEquals(0, height % 16);
			assertTrue(RiftSession.FLOOR >= minY && RiftSession.FLOOR + RiftSession.HEIGHT <= minY + height);
			assertFalse(type.get("bed_works").getAsBoolean());
			assertFalse(type.get("respawn_anchor_works").getAsBoolean());
			var world = JsonParser.parseReader(worldReader).getAsJsonObject();
			assertEquals("puffish_skills:rifts", world.get("type").getAsString());
			var generator = world.getAsJsonObject("generator");
			assertEquals("minecraft:flat", generator.get("type").getAsString());
			var settings = generator.getAsJsonObject("settings");
			assertEquals("minecraft:the_void", settings.get("biome").getAsString());
			assertFalse(settings.get("features").getAsBoolean());
			assertFalse(settings.get("lakes").getAsBoolean());
			assertEquals(0, settings.getAsJsonArray("structure_overrides").size());
			for (var layer : settings.getAsJsonArray("layers")) {
				assertEquals("minecraft:air", layer.getAsJsonObject().get("block").getAsString());
			}
		}
	}
}
