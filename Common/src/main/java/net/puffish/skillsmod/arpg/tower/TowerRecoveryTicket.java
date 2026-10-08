package net.puffish.skillsmod.arpg.tower;

import net.puffish.skillsmod.arpg.progression.CompletionReward;

/** Persisted lifecycle separates death capture, post-respawn return and inventory claims. */
public record TowerRecoveryTicket(String policy, TowerLink.Anchor checkpoint, Phase phase, boolean captured) {
	public enum Phase {
		WAITING_RESPAWN, RETURN_PENDING, CLAIMABLE
	}

	public TowerRecoveryTicket {
		CompletionReward.checkId(policy);
		if (checkpoint == null || phase == null) {
			throw new IllegalArgumentException("Invalid recovery ticket");
		}
	}

	public static TowerRecoveryTicket begin(TowerRecovery policy) {
		return new TowerRecoveryTicket(policy.id(), policy.checkpoint(), Phase.WAITING_RESPAWN, false);
	}

	public TowerRecoveryTicket capture() {
		if (phase != Phase.WAITING_RESPAWN || captured) {
			throw new IllegalStateException("Recovery death drops were already captured or respawned");
		}
		return new TowerRecoveryTicket(policy, checkpoint, phase, true);
	}

	public TowerRecoveryTicket respawn() {
		return phase == Phase.WAITING_RESPAWN ? new TowerRecoveryTicket(policy, checkpoint, Phase.RETURN_PENDING, captured) : this;
	}

	public TowerRecoveryTicket returned() {
		if (phase != Phase.RETURN_PENDING) {
			throw new IllegalStateException("Recovery return is not pending");
		}
		return new TowerRecoveryTicket(policy, checkpoint, Phase.CLAIMABLE, captured);
	}
}
