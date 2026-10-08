package net.puffish.skillsmod.arpg.sandbox;

import java.util.List;
import java.util.Map;

/** Small immutable screen snapshot; persistent receipt history is never sent to the client. */
public record CraftworkView(String operation, String mechanic, String expected, int step, int total,
		int score, int mistakes, int temperature, int x, int y, long snapshotTick,
		boolean complete, boolean failed, int smithing, int runecraft, int forgeQuality, int runeQuality,
		List<String> actions, Map<String, Boolean> recipes, Map<String, String> recipeCosts) {
	public CraftworkView {
		actions = List.copyOf(actions);
		recipes = Map.copyOf(recipes);
		recipeCosts = Map.copyOf(recipeCosts);
		if (!recipes.keySet().equals(recipeCosts.keySet())) {
			throw new IllegalArgumentException("Recipe cost snapshot mismatch");
		}
		if (actions.size() > 64 || recipes.size() > 64 || operation.length() > 256 || mechanic.length() > 256
				|| expected.length() > 256 || step < 0 || total < step || total > 64 || snapshotTick < 0) {
			throw new IllegalArgumentException("Invalid craftwork screen snapshot");
		}
	}
}
