package net.puffish.skillsmod.arpg.tower;

import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;

/** Bounded nearest-column search: 81 columns, nine local heights and one surface candidate each. */
public final class TowerLandingSearch {
	public record Point(int x, int y, int z) {
	}

	private TowerLandingSearch() {
	}

	public static Point find(int x, int y, int z, Predicate<Point> safe, IntBinaryOperator surface) {
		for (int radius = 0; radius <= 4; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}
					for (int step = 0; step <= 8; step++) {
						int dy = (step + 1) / 2 * (step % 2 == 0 ? -1 : 1);
						var point = new Point(x + dx, y + dy, z + dz);
						if (safe.test(point)) {
							return point;
						}
					}
					int height = surface.applyAsInt(x + dx, z + dz);
					if (height != Integer.MIN_VALUE) {
						var point = new Point(x + dx, height, z + dz);
						if (safe.test(point)) {
							return point;
						}
					}
				}
			}
		}
		return null;
	}
}
