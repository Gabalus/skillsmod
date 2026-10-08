package net.puffish.skillsmod.arpg.rift;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiftDimensionTest {
	@Test
	void bundledDimensionLoadsThroughMinecraftCodecs() throws Exception {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		try (var typeReader = new InputStreamReader(getClass().getResourceAsStream(
				"/data/puffish_skills/dimension_type/rifts.json"), StandardCharsets.UTF_8);
				var worldReader = new InputStreamReader(getClass().getResourceAsStream(
						"/data/puffish_skills/dimension/rifts.json"), StandardCharsets.UTF_8)) {
			var type = DimensionType.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(typeReader)).getOrThrow();
			assertEquals(0, type.minY());
			assertEquals(256, type.height());
			var world = JsonParser.parseReader(worldReader).getAsJsonObject();
			assertEquals("puffish_skills:rifts", world.get("type").getAsString());
			var ops = RegistryOps.of(JsonOps.INSTANCE, BuiltinRegistries.createWrapperLookup());
			var generator = ChunkGenerator.CODEC.parse(ops, world.get("generator")).getOrThrow();
			assertTrue(generator instanceof FlatChunkGenerator);
		}
	}
}
