package net.puffish.skillsmod.arpg.rift;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/** One session per player, eight active cells, retained return anchors for offline players. */
public final class RiftBook {
	public static final int MAX_RECORDS = 4096;
	private final Map<UUID, RiftSession> sessions = new HashMap<>();

	public RiftBook(Map<UUID, RiftSession> saved) {
		if (saved.size() > MAX_RECORDS) {
			throw new IllegalArgumentException("Too many retained rift anchors");
		}
		var slots = new HashSet<Integer>();
		var ids = new HashSet<UUID>();
		for (var entry : saved.entrySet()) {
			var session = entry.getValue();
			if (!entry.getKey().equals(session.player()) || !ids.add(session.id())
					|| (session.phase() != RiftSession.Phase.EXIT_PENDING && !slots.add(session.slot()))) {
				throw new IllegalArgumentException("Conflicting rift session ownership");
			}
		}
		sessions.putAll(saved);
	}

	public RiftSession reserve(UUID player, UUID id, String completion, RiftSession.ReturnPoint origin, long now) {
		if (sessions.containsKey(player) || sessions.size() >= MAX_RECORDS || now < 0
				|| now > Long.MAX_VALUE - 12_000
				|| sessions.values().stream().anyMatch(session -> session.id().equals(id))) {
			throw new IllegalStateException("Player already has a rift or return anchor");
		}
		for (int slot = 0; slot < RiftSession.MAX_SLOTS; slot++) {
			int candidate = slot;
			if (sessions.values().stream().noneMatch(session -> session.slot() == candidate && session.phase() != RiftSession.Phase.EXIT_PENDING)) {
				var session = new RiftSession(id, player, slot, completion, RiftSession.Phase.BUILDING, origin, null, 0, now + 12_000);
				sessions.put(player, session);
				return session;
			}
		}
		throw new IllegalStateException("All eight rift arenas are occupied");
	}

	public RiftSession get(UUID player) {
		return sessions.get(player);
	}

	public Map<UUID, RiftSession> sessions() {
		return Map.copyOf(sessions);
	}

	public void update(RiftSession next) {
		var current = sessions.get(next.player());
		if (current == null || !current.id().equals(next.id()) || current.slot() != next.slot()
				|| !current.completion().equals(next.completion()) || !current.origin().equals(next.origin())) {
			throw new IllegalStateException("Rift update changed reserved ownership");
		}
		boolean allowed = switch (current.phase()) {
			case BUILDING -> next.phase() == RiftSession.Phase.BUILDING || next.phase() == RiftSession.Phase.RUNNING || next.phase() == RiftSession.Phase.EXIT_PENDING;
			case RUNNING -> next.phase() == RiftSession.Phase.CLEARED || next.phase() == RiftSession.Phase.EXIT_PENDING;
			case CLEARED, EXIT_PENDING -> next.phase() == RiftSession.Phase.EXIT_PENDING;
			default -> false;
		};
		if (!allowed || next.buildIndex() < current.buildIndex()
				|| (current.boss() != null && !current.boss().equals(next.boss()))
				|| (next.phase() != RiftSession.Phase.CLEARED && next.deadline() != current.deadline())) {
			throw new IllegalStateException("Invalid rift phase transition");
		}
		sessions.put(next.player(), next);
	}

	public void returned(UUID player) {
		var session = sessions.get(player);
		if (session == null || session.phase() != RiftSession.Phase.EXIT_PENDING) {
			throw new IllegalStateException("Rift return has not been scheduled");
		}
		sessions.remove(player);
	}

	public void recoverAfterRestart() {
		sessions.replaceAll((player, session) -> session.exit());
	}
}
