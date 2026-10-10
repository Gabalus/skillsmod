package net.puffish.skillsmod.arpg.tower;

import net.puffish.skillsmod.arpg.progression.CompletionReward;

/** Explicit checkpoint departure to overworld spawn; marker coordinates are floor block positions. */
public record TowerExit(String id, TowerPuzzle.Relay marker) {
	public TowerExit {
		CompletionReward.checkId(id);
		if (marker == null || !marker.block().equals("minecraft:crying_obsidian")) {
			throw new IllegalArgumentException("Tower exit needs a crying-obsidian floor marker");
		}
	}

	public boolean nearby(String dimension, double x, double y, double z) {
		if (!dimension.equals(marker.dimension()) || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
			return false;
		}
		double dx = x - marker.x() - .5;
		double dy = y - marker.y() - 1;
		double dz = z - marker.z() - .5;
		return dx * dx + dy * dy + dz * dz <= 9;
	}
}
