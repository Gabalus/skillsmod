package net.puffish.skillsmod.arpg.tower;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TowerExitCatalog {
	private final Map<String, TowerExit> exits;
	private final Map<String, TowerExit> markers;

	public TowerExitCatalog(List<TowerExit> definitions, TowerProtection protection, TowerPuzzleCatalog puzzles) {
		if (definitions == null || definitions.size() > 64) {
			throw new IllegalArgumentException("Invalid tower exit catalog size");
		}
		var exits = new HashMap<String, TowerExit>();
		var markers = new HashMap<String, TowerExit>();
		for (var exit : definitions) {
			var marker = exit.marker();
			if (exits.putIfAbsent(exit.id(), exit) != null || markers.putIfAbsent(marker.position(), exit) != null
					|| !protection.protects(marker.dimension(), marker.x(), marker.y(), marker.z())
					|| !protection.protects(marker.dimension(), marker.x(), marker.y() + 2, marker.z())
					|| !protection.activation(marker.dimension(), marker.x(), marker.y(), marker.z()).isEmpty()
					|| puzzles.click(marker.dimension(), marker.x(), marker.y(), marker.z()) != null) {
				throw new IllegalArgumentException("Exit must have unique ID/position, fit inside protection and avoid relays/portal activators");
			}
		}
		this.exits = Map.copyOf(exits);
		this.markers = Map.copyOf(markers);
	}

	public Map<String, TowerExit> exits() {
		return exits;
	}

	public TowerExit at(String dimension, int x, int y, int z) {
		return markers.get(dimension + ":" + x + ":" + y + ":" + z);
	}

	public TowerExit nearby(String dimension, double x, double y, double z) {
		var matches = exits.values().stream().filter(exit -> exit.nearby(dimension, x, y, z)).toList();
		if (matches.size() > 1) {
			throw new IllegalStateException("Multiple tower exits nearby; click the intended floor marker");
		}
		return matches.isEmpty() ? null : matches.get(0);
	}
}
