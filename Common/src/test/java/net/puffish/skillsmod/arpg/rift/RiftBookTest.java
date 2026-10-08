package net.puffish.skillsmod.arpg.rift;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RiftBookTest {
	@Test
	void sessionRegressionChecks() {
		RiftEngineChecks.main(new String[0]);
	}
	@Test
	void returnAnchorAndLiveBossSurviveNbtRoundTrip() {
		var book = new RiftBook(Map.of());
		var point = new RiftSession.ReturnPoint("minecraft:the_nether", 10, 70, -20, 90, 15, "adventure");
		var session = book.reserve(UUID.randomUUID(), UUID.randomUUID(), "arpg:first_rift", point, 100);
		while (session.buildIndex() < RiftSession.BUILD_VOLUME) {
			session = session.build(256);
			book.update(session);
		}
		book.update(session.start(UUID.randomUUID()));
		var saved = RiftBookNbt.write(book);
		var restored = RiftBookNbt.read(saved);
		assertEquals(book.sessions(), restored.sessions());
		restored.recoverAfterRestart();
		assertEquals(point, restored.get(session.player()).origin());
		assertEquals(RiftSession.Phase.EXIT_PENDING, restored.get(session.player()).phase());
		assertEquals(restored.sessions(), RiftBookNbt.read(RiftBookNbt.write(restored)).sessions());
	}
	@Test
	void refusesFutureSaveSchema() {
		var tag = new NbtCompound();
		tag.putInt("schema", 2);
		assertThrows(IllegalArgumentException.class, () -> RiftBookNbt.read(tag));
		assertEquals(Map.of(), RiftBookNbt.read(new NbtCompound()).sessions());
	}
}
