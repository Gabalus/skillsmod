package net.puffish.skillsmod.arpg.progression;

import java.util.Set;

/** Authored first-clear rewards; changing amounts never reopens a settled ID. */
public record CompletionReward(String id, String type, int minimumLevel, int passivePoints,
		int confluencePoints, int trial, Set<String> prerequisites, Set<String> knowledge) {
	public CompletionReward {
		checkId(id);
		if (!Set.of("rift", "world_boss", "trial").contains(type) || minimumLevel < 1 || minimumLevel > 100
				|| passivePoints < 0 || passivePoints > 20 || confluencePoints < 0 || confluencePoints > 12
				|| trial < 0 || trial > 4 || ((trial > 0) != type.equals("trial"))
				|| (trial > 0 && minimumLevel < 30 + (trial - 1) * 20)) {
			throw new IllegalArgumentException("Invalid completion reward " + id);
		}
		prerequisites = Set.copyOf(prerequisites);
		knowledge = Set.copyOf(knowledge);
		if (prerequisites.size() > 64 || knowledge.size() > 32 || prerequisites.contains(id)) {
			throw new IllegalArgumentException("Invalid completion prerequisites or discoveries");
		}
		prerequisites.forEach(CompletionReward::checkId);
		knowledge.forEach(CompletionReward::checkId);
	}

	public static void checkId(String id) {
		if (id == null || id.length() > 256 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
			throw new IllegalArgumentException("Invalid completion/knowledge ID: " + id);
		}
	}
}
