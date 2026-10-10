package net.puffish.skillsmod.arpg.tower;

import net.puffish.skillsmod.arpg.progression.CompletionReward;

import java.util.Set;

/** Authored translation-only vertical portal seam; points are aperture centres. */
public record TowerLink(String id, Anchor from, Anchor to, double width, double height,
		int minimumLevel, Set<String> prerequisites) {
	public record Anchor(String dimension, double x, double y, double z) {
		public Anchor {
			CompletionReward.checkId(dimension);
			if (dimension.equals("puffish_skills:rifts") || !Double.isFinite(x) || !Double.isFinite(y)
					|| !Double.isFinite(z) || Math.abs(x) > 29_999_900 || Math.abs(z) > 29_999_900 || Math.abs(y) > 2000) {
				throw new IllegalArgumentException("Invalid tower anchor or reserved solo-rift dimension");
			}
		}

		public double distanceSquared(String world, double px, double py, double pz) {
			if (!dimension.equals(world) || !Double.isFinite(px) || !Double.isFinite(py) || !Double.isFinite(pz)) {
				return Double.POSITIVE_INFINITY;
			}
			return (px - x) * (px - x) + (py - y) * (py - y) + (pz - z) * (pz - z);
		}
	}

	public TowerLink {
		CompletionReward.checkId(id);
		if (from == null || to == null || from.dimension().equals(to.dimension())
				|| !Double.isFinite(width) || !Double.isFinite(height) || width < 2 || width > 6
				|| height < 3 || height > 6 || minimumLevel < 1 || minimumLevel > 100) {
			throw new IllegalArgumentException("Invalid cross-dimension tower link");
		}
		prerequisites = Set.copyOf(prerequisites);
		if (prerequisites.size() > 64) {
			throw new IllegalArgumentException("Too many tower gates");
		}
		prerequisites.forEach(CompletionReward::checkId);
	}

	public boolean eligible(int level, Set<String> clears, boolean hasRift, boolean alive, boolean survivalOrAdventure) {
		return alive && survivalOrAdventure && !hasRift && level >= minimumLevel && clears.containsAll(prerequisites);
	}

	public boolean nearby(String dimension, double x, double y, double z, double radius) {
		if (!Double.isFinite(radius) || radius < 0 || radius > 48) {
			throw new IllegalArgumentException("Invalid portal activation radius");
		}
		return Math.min(from.distanceSquared(dimension, x, y, z), to.distanceSquared(dimension, x, y, z)) <= radius * radius;
	}
}
