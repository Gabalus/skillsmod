package net.puffish.skillsmod.arpg.combat;

import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatSnapshot;

import java.util.EnumSet;

/** Percentage bridge for damage amounts already constructed by vanilla or another combat mod. */
public final class ArpgAttackScaling {
	private static final double MAX_BRIDGED_DAMAGE = 1_000_000.0;

	private ArpgAttackScaling() {
	}

	public enum Delivery {
		MELEE,
		PROJECTILE
	}

	/**
	 * Scales an existing physical attack without injecting flat added damage.
	 *
	 * <p>The incoming NeoForge damage amount already contains vanilla weapon damage, enchantments,
	 * attack cooldown and critical multiplication. Flat ARPG additions therefore remain owned by
	 * {@link DamagePipeline}; applying them here would make critical-hit ordering incorrect.</p>
	 */
	public static double scaleExistingPhysicalAttack(
			double amount,
			ArpgStatSnapshot snapshot,
			Delivery delivery
	) {
		if (!Double.isFinite(amount) || amount < 0.0) {
			throw new IllegalArgumentException("Attack damage must be finite and nonnegative");
		}
		if (snapshot == null || delivery == null) {
			throw new IllegalArgumentException("Snapshot and delivery are required");
		}

		var applicable = EnumSet.of(ArpgStat.PHYSICAL_DAMAGE, ArpgStat.ATTACK_DAMAGE);
		applicable.add(delivery == Delivery.MELEE ? ArpgStat.MELEE_DAMAGE : ArpgStat.PROJECTILE_DAMAGE);

		double increased = 0.0;
		double multiplier = 1.0;
		for (var stat : applicable) {
			var value = snapshot.get(stat);
			increased += value.increased();
			multiplier *= value.multiplier();
		}

		double scaled = amount * Math.max(0.0, 1.0 + increased) * multiplier;
		if (!Double.isFinite(scaled)) {
			return MAX_BRIDGED_DAMAGE;
		}
		return Math.max(0.0, Math.min(MAX_BRIDGED_DAMAGE, scaled));
	}
}
