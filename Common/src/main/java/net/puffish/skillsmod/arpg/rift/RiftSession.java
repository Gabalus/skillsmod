package net.puffish.skillsmod.arpg.rift;

import net.puffish.skillsmod.arpg.progression.CompletionReward;

import java.util.UUID;

/** Bounded solo encounter state; coordinates refer only to the dedicated rift dimension. */
public record RiftSession(UUID id, UUID player, int slot, String completion, Phase phase,
		ReturnPoint origin, UUID boss, int buildIndex, long deadline) {
	public static final int MAX_SLOTS = 8;
	public static final int RADIUS = 12;
	public static final int FLOOR = 64;
	public static final int HEIGHT = 11;
	public static final int WIDTH = RADIUS * 2 + 1;
	public static final int BUILD_VOLUME = WIDTH * WIDTH * HEIGHT;

	public enum Phase {
		BUILDING, RUNNING, CLEARED, EXIT_PENDING
	}

	public record ReturnPoint(String dimension, double x, double y, double z, float yaw, float pitch, String mode) {
		public ReturnPoint {
			CompletionReward.checkId(dimension);
			if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)
					|| Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 4096
					|| Math.abs(pitch) > 90 || (!"survival".equals(mode) && !"adventure".equals(mode))) {
				throw new IllegalArgumentException("Invalid rift return anchor");
			}
		}
	}

	public RiftSession {
		if (id == null || player == null || phase == null || origin == null || slot < 0 || slot >= MAX_SLOTS
				|| (phase == Phase.BUILDING && boss != null)
				|| buildIndex < 0 || buildIndex > BUILD_VOLUME || deadline < 0
				|| ((phase == Phase.RUNNING || phase == Phase.CLEARED) && (boss == null || buildIndex != BUILD_VOLUME))) {
			throw new IllegalArgumentException("Invalid rift session");
		}
		CompletionReward.checkId(completion);
	}

	public int centerX() {
		return (slot % 4) * 64;
	}

	public int centerZ() {
		return (slot / 4) * 64;
	}

	public boolean contains(double x, double y, double z) {
		return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
				&& x > centerX() - RADIUS && x < centerX() + RADIUS + 1
				&& z > centerZ() - RADIUS && z < centerZ() + RADIUS + 1 && y >= FLOOR + 1 && y < FLOOR + HEIGHT - 1;
	}

	public RiftSession build(int count) {
		if (phase != Phase.BUILDING || count < 1 || count > 256) {
			throw new IllegalStateException("Invalid rift build step");
		}
		return new RiftSession(id, player, slot, completion, phase, origin, boss, Math.min(BUILD_VOLUME, buildIndex + count), deadline);
	}

	public RiftSession start(UUID bossId) {
		if (phase != Phase.BUILDING || buildIndex != BUILD_VOLUME || bossId == null) {
			throw new IllegalStateException("Rift is not ready to start");
		}
		return new RiftSession(id, player, slot, completion, Phase.RUNNING, origin, bossId, buildIndex, deadline);
	}

	public RiftSession clear(UUID killer, UUID defeatedBoss, long now) {
		if (now < 0 || now > Long.MAX_VALUE - 100 || phase != Phase.RUNNING || !player.equals(killer) || !boss.equals(defeatedBoss) || now >= deadline) {
			throw new IllegalStateException("Rift clear does not match its live encounter");
		}
		return new RiftSession(id, player, slot, completion, Phase.CLEARED, origin, boss, buildIndex, now + 100);
	}

	public RiftSession exit() {
		return new RiftSession(id, player, slot, completion, Phase.EXIT_PENDING, origin, boss, buildIndex, deadline);
	}
}
