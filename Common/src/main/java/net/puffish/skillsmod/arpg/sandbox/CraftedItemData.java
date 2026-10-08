package net.puffish.skillsmod.arpg.sandbox;

import java.util.HashMap;
import java.util.Map;

/** Finishing stages coexist: inscription never erases the weapon's forging quality. */
public record CraftedItemData(Map<String, SandboxState.Quality> stages) {
	public CraftedItemData {
		stages = Map.copyOf(stages);
		stages.forEach((id, quality) -> {
			if (!id.equals(quality.operation())) {
				throw new IllegalArgumentException("Quality stage mismatch");
			}
		});
	}

	public static CraftedItemData empty() {
		return new CraftedItemData(Map.of());
	}

	public CraftedItemData add(SandboxState.Quality quality) {
		var next = new HashMap<>(stages);
		if (next.putIfAbsent(quality.operation(), quality) != null) {
			throw new IllegalStateException("Item already finished with this operation");
		}

		return new CraftedItemData(next);
	}
}
