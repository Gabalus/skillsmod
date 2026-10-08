package net.puffish.skillsmod.arpg.tower;

import net.puffish.skillsmod.arpg.progression.CompletionReward;

import java.util.Set;

/** Opt-in death policy. Checkpoint coordinates describe the player's feet, not a portal centre. */
public record TowerRecovery(String id, Set<String> sectors, TowerLink.Anchor checkpoint) {
	public TowerRecovery {
		CompletionReward.checkId(id);
		sectors = Set.copyOf(sectors);
		if (sectors.isEmpty() || sectors.size() > 128 || checkpoint == null) {
			throw new IllegalArgumentException("Invalid tower recovery policy");
		}
		sectors.forEach(CompletionReward::checkId);
	}
}
