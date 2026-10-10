package net.puffish.skillsmod.arpg.rift;

import java.util.Map;
import java.util.UUID;

public final class RiftEngineChecks {
	private static int checks;
	private RiftEngineChecks() {
	}
	private static void check(boolean value) {
		checks++;
		if (!value) {
			throw new AssertionError("Rift check " + checks);
		}
	}
	private static void rejects(Runnable action) {
		checks++;
		try {
			action.run();
		} catch (IllegalArgumentException | IllegalStateException expected) {
			return;
		}
		throw new AssertionError("Expected rejection " + checks);
	}
	public static void main(String[] args) {
		var origin = new RiftSession.ReturnPoint("minecraft:overworld", 10, 70, -20, 90, 0, "survival");
		var player = UUID.randomUUID();
		var book = new RiftBook(Map.of());
		var preparing = book.reserve(player, UUID.randomUUID(), "arpg:first_rift", origin, 100, 4);
		check(preparing.apotheosisTier() == 4);
		rejects(() -> book.update(new RiftSession(preparing.id(), player, preparing.slot(), preparing.completion(), preparing.phase(),
				origin, null, 0, preparing.deadline(), 0)));
		check(preparing.slot() == 0 && preparing.deadline() == 12100);
		rejects(() -> book.reserve(player, UUID.randomUUID(), "arpg:first_rift", origin, 100));
		rejects(() -> preparing.start(UUID.randomUUID()));
		rejects(() -> preparing.build(257));
		var built = preparing;
		while (built.buildIndex() < RiftSession.BUILD_VOLUME) {
			built = built.build(256);
			book.update(built);
		}
		rejects(() -> book.update(preparing));
		var boss = UUID.randomUUID();
		var live = built.start(boss);
		book.update(live);
		check(live.contains(.5, 65, .5));
		check(!live.contains(100, 65, .5) && !live.contains(.5, 64, .5));
		rejects(() -> live.clear(UUID.randomUUID(), boss, 200));
		rejects(() -> live.clear(player, UUID.randomUUID(), 200));
		rejects(() -> live.clear(player, boss, live.deadline()));
		rejects(() -> live.clear(player, boss, -1));
		var cleared = live.clear(player, boss, 200);
		book.update(cleared);
		check(cleared.deadline() == 300 && cleared.apotheosisTier() == 4);
		rejects(() -> cleared.clear(player, boss, 201));
		rejects(() -> book.update(live));
		rejects(() -> book.returned(player));
		book.update(cleared.exit());
		var second = book.reserve(UUID.randomUUID(), UUID.randomUUID(), "arpg:first_rift", origin, 300);
		check(second.slot() == live.slot());
		check(book.get(player).origin().equals(origin) && book.get(player).apotheosisTier() == 4);
		book.returned(player);
		check(book.get(player) == null);
		book.recoverAfterRestart();
		check(book.get(second.player()).phase() == RiftSession.Phase.EXIT_PENDING);
		check(book.get(second.player()).origin().equals(origin));
		var restarted = new RiftBook(book.sessions());
		check(restarted.reserve(player, UUID.randomUUID(), "arpg:first_rift", origin, 400).slot() == 0);
		var full = new RiftBook(Map.of());
		for (int i = 0; i < RiftSession.MAX_SLOTS; i++) {
			check(full.reserve(UUID.randomUUID(), UUID.randomUUID(), "arpg:first_rift", origin, 0).slot() == i);
		}
		rejects(() -> full.reserve(UUID.randomUUID(), UUID.randomUUID(), "arpg:first_rift", origin, 0));
		rejects(() -> new RiftBook(Map.of()).reserve(player, UUID.randomUUID(), "arpg:first_rift", origin, Long.MAX_VALUE));
		rejects(() -> new RiftSession.ReturnPoint("minecraft:overworld", Double.NaN, 70, 0, 0, 0, "survival"));
		rejects(() -> new RiftSession.ReturnPoint("minecraft:overworld", 0, 70, 0, 0, 0, null));
		rejects(() -> new RiftBook(Map.of(UUID.randomUUID(), live)));
		rejects(() -> new RiftBook(Map.of()).reserve(UUID.randomUUID(), UUID.randomUUID(), "arpg:first_rift", origin, 0, -1));
		rejects(() -> new RiftBook(Map.of()).reserve(UUID.randomUUID(), UUID.randomUUID(), "arpg:first_rift", origin, 0, 5));
		System.out.println("Rift session checks passed: " + checks);
	}
}
