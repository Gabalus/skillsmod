package net.puffish.skillsmod.arpg.tower;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.stream.Collectors;

/** Dimension-indexed protection and unambiguous physical-anchor lookup. */
public final class TowerProtection {
	private final Map<String, List<TowerSector>> sectors;
	private final TowerCatalog catalog;

	public TowerProtection(List<TowerSector> definitions, TowerCatalog catalog) {
		if (definitions == null || definitions.size() > 128 || catalog == null) {
			throw new IllegalArgumentException("Invalid tower sector catalog");
		}
		var ids = new HashSet<String>();
		for (var sector : definitions) {
			if (sector == null || !ids.add(sector.id())) {
				throw new IllegalArgumentException("Null or duplicate tower sector");
			}
		}
		sectors = definitions.stream().collect(Collectors.groupingBy(TowerSector::dimension,
				Collectors.collectingAndThen(Collectors.toList(), List::copyOf)));
		this.catalog = catalog;
	}

	public boolean protects(String dimension, double x, double y, double z) {
		return sectors.getOrDefault(dimension, List.of()).stream().anyMatch(sector -> sector.contains(dimension, x, y, z));
	}

	public int size() {
		return sectors.values().stream().mapToInt(List::size).sum();
	}

	/** Conservative guard covering vanilla pistons' twelve-block assemblies and destination step. */
	public boolean pistonMayTouch(String dimension, int x, int y, int z) {
		return sectors.getOrDefault(dimension, List.of()).stream().anyMatch(sector ->
				sector.minX() <= (long) x + 13 && sector.maxX() >= (long) x - 13
						&& sector.minY() <= (long) y + 13 && sector.maxY() >= (long) y - 13
						&& sector.minZ() <= (long) z + 13 && sector.maxZ() >= (long) z - 13);
	}

	/** An anchor's floor-centre lodestone activates its link; conflicting anchors fail closed. */
	public String activation(String dimension, int x, int y, int z) {
		if (!protects(dimension, x, y, z)) {
			return "";
		}
		var matches = catalog.links().values().stream().filter(link -> floorMatches(link.from(), link, dimension, x, y, z)
				|| floorMatches(link.to(), link, dimension, x, y, z)).map(TowerLink::id).toList();
		if (matches.size() > 1) {
			throw new IllegalStateException("Overlapping tower activators: give each passage a distinct floor anchor");
		}
		return matches.isEmpty() ? "" : matches.get(0);
	}

	private static boolean floorMatches(TowerLink.Anchor anchor, TowerLink link, String dimension, int x, int y, int z) {
		return anchor.dimension().equals(dimension) && x == (int) Math.floor(anchor.x())
				&& y == (int) Math.floor(anchor.y() - link.height() / 2) - 1 && z == (int) Math.floor(anchor.z());
	}
}
