package net.puffish.skillsmod.arpg.tower;

import net.puffish.skillsmod.arpg.progression.CompletionReward;

/** Inclusive authored block bounds. Protection changes permissions, never terrain or player modes. */
public record TowerSector(String id, String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
	public TowerSector {
		CompletionReward.checkId(id);
		CompletionReward.checkId(dimension);
		if (dimension.equals("puffish_skills:rifts") || minX > maxX || minY > maxY || minZ > maxZ
				|| minX < -29_999_900 || maxX > 29_999_900 || minZ < -29_999_900 || maxZ > 29_999_900
				|| minY < -2048 || maxY > 2047 || (long) maxX - minX > 4095 || (long) maxZ - minZ > 4095) {
			throw new IllegalArgumentException("Invalid tower sector bounds or reserved dimension");
		}
	}

	public boolean contains(String world, double x, double y, double z) {
		return dimension.equals(world) && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
				&& x >= minX && x < (double) maxX + 1 && y >= minY && y < (double) maxY + 1
				&& z >= minZ && z < (double) maxZ + 1;
	}
}
