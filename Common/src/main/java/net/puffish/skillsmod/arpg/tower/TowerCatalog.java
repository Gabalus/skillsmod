package net.puffish.skillsmod.arpg.tower;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A network may contain branches and cycles; first-clear prerequisites gate individual seams. */
public final class TowerCatalog {
	private final Map<String, TowerLink> links;

	public TowerCatalog(List<TowerLink> definitions, Set<String> completionIds) {
		if (definitions == null || definitions.size() > 128) {
			throw new IllegalArgumentException("Invalid tower network size");
		}
		var result = new LinkedHashMap<String, TowerLink>();
		for (var link : definitions) {
			if (link == null || result.putIfAbsent(link.id(), link) != null || !completionIds.containsAll(link.prerequisites())) {
				throw new IllegalArgumentException("Duplicate tower link or unknown completion gate");
			}
		}
		links = Map.copyOf(result);
	}

	public Map<String, TowerLink> links() {
		return links;
	}

	public TowerLink link(String id) {
		var link = links.get(id);
		if (link == null) {
			throw new IllegalArgumentException("Unknown tower link: " + id);
		}
		return link;
	}
}
