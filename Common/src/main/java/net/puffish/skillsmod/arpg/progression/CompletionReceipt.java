package net.puffish.skillsmod.arpg.progression;

import java.util.Set;

/** Permanent reward snapshot, independent of later catalog edits or provider removal. */
public record CompletionReceipt(String id, int passivePoints, int confluencePoints, int trial, Set<String> knowledge) {
	public CompletionReceipt {
		CompletionReward.checkId(id);
		if (passivePoints < 0 || passivePoints > 20 || confluencePoints < 0 || confluencePoints > 12 || trial < 0 || trial > 4) {
			throw new IllegalArgumentException("Invalid completion receipt");
		}
		knowledge = Set.copyOf(knowledge);
		if (knowledge.size() > 32) {
			throw new IllegalArgumentException("Too many recipe discoveries");
		}
		knowledge.forEach(CompletionReward::checkId);
	}
}
