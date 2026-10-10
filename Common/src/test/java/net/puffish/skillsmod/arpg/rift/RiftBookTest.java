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
		var session = book.reserve(UUID.randomUUID(), UUID.randomUUID(), "arpg:first_rift", point, 100, 3);
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
	void rejectsMalformedSessionContainer() {
		var tag = new NbtCompound();
		tag.putInt("schema", 1);
		tag.putString("sessions", "corrupt");
		assertThrows(IllegalArgumentException.class, () -> RiftBookNbt.read(tag));
	}
	@Test
	void refusesFutureSaveSchema() {
		var tag = new NbtCompound();
		tag.putInt("schema", 3);
		assertThrows(IllegalArgumentException.class, () -> RiftBookNbt.read(tag));
		assertEquals(Map.of(), RiftBookNbt.read(new NbtCompound()).sessions());
	}
	@Test
	void legacySavesUseBaselineAndModernSavesRequireValidTier() {
		var book = new RiftBook(Map.of());
		var point = new RiftSession.ReturnPoint("minecraft:overworld", 0, 70, 0, 0, 0, "survival");
		var player = UUID.randomUUID();
		book.reserve(player, UUID.randomUUID(), "arpg:first_rift", point, 0, 4);
		var saved = RiftBookNbt.write(book);
		var entry = saved.getCompound("sessions").getCompound(player.toString());
		assertEquals(4, RiftBookNbt.read(saved).get(player).apotheosisTier());
		entry.remove("tier");
		assertThrows(IllegalArgumentException.class, () -> RiftBookNbt.read(saved));
		entry.putString("tier", "4");
		assertThrows(IllegalArgumentException.class, () -> RiftBookNbt.read(saved));
		entry.putInt("tier", 5);
		assertThrows(IllegalArgumentException.class, () -> RiftBookNbt.read(saved));
		entry.putInt("tier", -1);
		assertThrows(IllegalArgumentException.class, () -> RiftBookNbt.read(saved));
		entry.remove("tier");
		saved.putInt("schema", 1);
		assertEquals(0, RiftBookNbt.read(saved).get(player).apotheosisTier());
	}

}
