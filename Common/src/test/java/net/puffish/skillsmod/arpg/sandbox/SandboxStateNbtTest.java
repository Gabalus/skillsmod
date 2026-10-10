package net.puffish.skillsmod.arpg.sandbox;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SandboxStateNbtTest {
	@Test
	void legacyPlayerStartsWithoutSandboxProgress() {
		assertEquals(SandboxState.empty(), SandboxStateNbt.read(new NbtCompound()));
	}

	@Test
	void activeSessionAndKnowledgeRoundTrip() {
		var catalog = SandboxCatalog.core();
		var state = SandboxState.empty().learn(catalog, "arpg:metalworking", Map.of())
		.start(catalog, "arpg:forge", UUID.randomUUID(), 100, Map.of()).act(0, "heat", 104);
		assertEquals(state, SandboxStateNbt.read(SandboxStateNbt.write(state)));
	}

	@Test
	void receiptsAndMasteryRoundTrip() {
		var catalog = SandboxCatalog.core();
		var id = UUID.randomUUID();
		var state = SandboxState.empty().learn(catalog, "arpg:metalworking", Map.of())
		.start(catalog, "arpg:forge", id, 100, Map.of()).act(0, "heat", 104).act(1, "draw", 108)
		.act(2, "heavy", 112).act(3, "quench", 116).finish(id);
		assertEquals(state, SandboxStateNbt.read(SandboxStateNbt.write(state)));
	}

	@Test
	void refusesFutureSchemaInsteadOfErasingKnowledge() {
		var tag = new NbtCompound();
		tag.putInt("schema", 2);
		assertThrows(IllegalArgumentException.class, () -> SandboxStateNbt.read(tag));
	}
}
