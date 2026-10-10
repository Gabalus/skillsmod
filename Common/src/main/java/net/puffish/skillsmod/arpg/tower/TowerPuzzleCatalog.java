package net.puffish.skillsmod.arpg.tower;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Explicit protected interaction points; unrelated blocks retain normal tower protection. */
public final class TowerPuzzleCatalog {
	public record Click(TowerPuzzle puzzle, int index) {
	}

	private final Map<String, TowerPuzzle> links;
	private final Map<String, Click> relays;

	public TowerPuzzleCatalog(List<TowerPuzzle> definitions, TowerCatalog catalog, TowerProtection protection) {
		if (definitions == null || definitions.size() > 64) {
			throw new IllegalArgumentException("Invalid tower puzzle catalog size");
		}
		var links = new HashMap<String, TowerPuzzle>();
		var ids = new HashMap<String, TowerPuzzle>();
		var relays = new HashMap<String, Click>();
		for (var puzzle : definitions) {
			var link = catalog.link(puzzle.link());
			if (links.putIfAbsent(puzzle.link(), puzzle) != null || ids.putIfAbsent(puzzle.id(), puzzle) != null) {
				throw new IllegalArgumentException("Duplicate puzzle ID or multiple puzzles for one passage");
			}
			for (int i = 0; i < puzzle.sequence().size(); i++) {
				var relay = puzzle.sequence().get(i);
				if (!relay.dimension().equals(link.from().dimension())
						|| !protection.protects(relay.dimension(), relay.x(), relay.y(), relay.z())
						|| !protection.activation(relay.dimension(), relay.x(), relay.y(), relay.z()).isEmpty()
						|| relays.putIfAbsent(relay.position(), new Click(puzzle, i)) != null) {
					throw new IllegalArgumentException("Relay must be a unique protected source-room position distinct from portal anchors");
				}
			}
		}
		this.links = Map.copyOf(links);
		this.relays = Map.copyOf(relays);
	}

	public Map<String, TowerPuzzle> links() {
		return links;
	}

	public Click click(String dimension, int x, int y, int z) {
		return relays.get(dimension + ":" + x + ":" + y + ":" + z);
	}
}
