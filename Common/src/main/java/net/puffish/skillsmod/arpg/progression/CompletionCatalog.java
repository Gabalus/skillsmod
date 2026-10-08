package net.puffish.skillsmod.arpg.progression;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validated completion graph; stable IDs are the per-player replay boundary. */
public final class CompletionCatalog {
	private final Map<String, CompletionReward> rewards;
	private final String apotheosisWorldBoss;

	public CompletionCatalog(List<CompletionReward> definitions, String apotheosisWorldBoss) {
		if (definitions == null || definitions.isEmpty() || definitions.size() > 4096 || apotheosisWorldBoss == null) {
			throw new IllegalArgumentException("Invalid completion catalog");
		}
		var entries = new LinkedHashMap<String, CompletionReward>();
		for (var reward : definitions) {
			if (entries.putIfAbsent(reward.id(), reward) != null) {
				throw new IllegalArgumentException("Duplicate completion " + reward.id());
			}
		}
		rewards = Map.copyOf(entries);
		this.apotheosisWorldBoss = apotheosisWorldBoss;
		if (!apotheosisWorldBoss.isEmpty() && !reward(apotheosisWorldBoss).type().equals("world_boss")) {
			throw new IllegalArgumentException("Automatic Apotheosis reward must be a world boss");
		}
		var depths = new java.util.HashMap<String, Integer>();
		for (String id : rewards.keySet()) {
			visit(id, new HashSet<>(), depths);
		}
	}

	private int visit(String id, Set<String> path, Map<String, Integer> depths) {
		var known = depths.get(id);
		if (known != null) {
			if (path.size() + known > 128) {
				throw new IllegalArgumentException("Excessively deep completion graph");
			}
			return known;
		}
		if (path.size() >= 128 || !path.add(id)) {
			throw new IllegalArgumentException("Cyclic or excessively deep completion graph");
		}
		int depth = 1;
		for (String prerequisite : reward(id).prerequisites()) {
			depth = Math.max(depth, 1 + visit(prerequisite, path, depths));
		}
		path.remove(id);
		depths.put(id, depth);
		return depth;
	}

	public CompletionReward reward(String id) {
		var reward = rewards.get(id);
		if (reward == null) {
			throw new IllegalArgumentException("Unknown completion " + id);
		}
		return reward;
	}

	public Map<String, CompletionReward> rewards() {
		return rewards;
	}

	public String apotheosisWorldBoss() {
		return apotheosisWorldBoss;
	}

	public static CompletionCatalog defaults() {
		return new CompletionCatalog(List.of(
				new CompletionReward("arpg:first_rift", "rift", 1, 1, 0, 0, Set.of(), Set.of("arpg:metalworking")),
				new CompletionReward("arpg:rune_rift", "rift", 1, 1, 0, 0, Set.of("arpg:first_rift"), Set.of("arpg:inscription")),
				new CompletionReward("arpg:first_world_boss", "world_boss", 1, 2, 1, 0, Set.of(), Set.of()),
				new CompletionReward("arpg:trial_1", "trial", 30, 0, 0, 1, Set.of("arpg:first_rift"), Set.of()),
				new CompletionReward("arpg:trial_2", "trial", 50, 0, 0, 2, Set.of("arpg:trial_1"), Set.of()),
				new CompletionReward("arpg:trial_3", "trial", 70, 0, 0, 3, Set.of("arpg:trial_2"), Set.of()),
				new CompletionReward("arpg:trial_4", "trial", 90, 0, 0, 4, Set.of("arpg:trial_3"), Set.of())
		), "arpg:first_world_boss");
	}
}
