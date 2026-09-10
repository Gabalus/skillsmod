package net.puffish.skillsmod.arpg.skill;

import net.puffish.skillsmod.arpg.data.ArpgContent;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatSnapshot;

/** Pure arithmetic for active-skill costs, cooldowns, and directional targeting. */
public final class ArpgSkillUseSemantics {
	private static final double MINIMUM_RECOVERY_RATE = 0.05;

	private ArpgSkillUseSemantics() {
	}

	public static double resourceCost(ArpgContent.Skill skill, ArpgStatSnapshot snapshot) {
		double cost = snapshot.apply(ArpgStat.RESOURCE_COST, skill.cost());
		return Double.isFinite(cost) ? Math.max(0.0, cost) : Double.POSITIVE_INFINITY;
	}

	/** Cooldown recovery is a rate: 20% increased recovery turns 100 ticks into ceil(100 / 1.2). */
	public static int cooldownTicks(ArpgContent.Skill skill, ArpgStatSnapshot snapshot) {
		double recoveryRate = snapshot.apply(ArpgStat.COOLDOWN_RECOVERY, 1.0);
		if (!Double.isFinite(recoveryRate)) {
			return skill.cooldown();
		}
		recoveryRate = Math.max(MINIMUM_RECOVERY_RATE, recoveryRate);
		return Math.max(1, (int) Math.ceil(skill.cooldown() / recoveryRate));
	}

	/** Horizontal cone test using a cosine threshold, avoiding angle normalization in world code. */
	public static boolean inCone(
			double forwardX,
			double forwardZ,
			double deltaX,
			double deltaZ,
			double range,
			double halfAngleDegrees
	) {
		if (!Double.isFinite(range) || range <= 0.0 || !Double.isFinite(halfAngleDegrees)
				|| halfAngleDegrees <= 0.0 || halfAngleDegrees > 180.0) {
			return false;
		}
		double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
		if (distanceSquared <= 1.0e-9 || distanceSquared > range * range) {
			return false;
		}
		double forwardLength = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
		if (forwardLength <= 1.0e-9) {
			return false;
		}
		double dot = (forwardX * deltaX + forwardZ * deltaZ)
				/ (forwardLength * Math.sqrt(distanceSquared));
		double threshold = Math.cos(Math.toRadians(halfAngleDegrees));
		return dot >= threshold;
	}
}
