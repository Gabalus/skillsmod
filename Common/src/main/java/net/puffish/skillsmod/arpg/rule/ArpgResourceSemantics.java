package net.puffish.skillsmod.arpg.rule;

/** Pure resource arithmetic shared by optional provider bridges. */
public final class ArpgResourceSemantics {
	private static final double LOW_RESOURCE_THRESHOLD = 0.5;
	private static final double LIFE_PER_MANA = 0.1;
	private static final double MINIMUM_SURVIVING_HEALTH = 1.0;

	private ArpgResourceSemantics() {
	}

	public static boolean isLowFraction(double fraction) {
		return Double.isFinite(fraction) && fraction >= 0.0 && fraction <= LOW_RESOURCE_THRESHOLD;
	}

	public static double restoreFromMaximum(double current, double maximum, double fraction) {
		if (!Double.isFinite(current) || !Double.isFinite(maximum) || !Double.isFinite(fraction) || maximum <= 0.0) {
			return current;
		}
		double clampedCurrent = Math.max(0.0, Math.min(maximum, current));
		double amount = maximum * Math.max(0.0, fraction);
		return Math.min(maximum, clampedCurrent + amount);
	}

	/** Converts provider mana points into Minecraft health points. Ten mana currently costs one health point. */
	public static double lifeCostFromMana(double manaCost) {
		if (!Double.isFinite(manaCost) || manaCost < 0.0) {
			return Double.NaN;
		}
		return manaCost * LIFE_PER_MANA;
	}

	/** Blood-magic costs are valid only when the player survives with at least one health point. */
	public static boolean canPayLifeCost(double currentHealth, double manaCost) {
		double lifeCost = lifeCostFromMana(manaCost);
		return Double.isFinite(currentHealth) && Double.isFinite(lifeCost)
				&& currentHealth - lifeCost >= MINIMUM_SURVIVING_HEALTH;
	}

	/** Returns unchanged health when the cost cannot legally be paid. */
	public static double spendLifeForMana(double currentHealth, double manaCost) {
		if (!canPayLifeCost(currentHealth, manaCost)) {
			return currentHealth;
		}
		return Math.max(MINIMUM_SURVIVING_HEALTH, currentHealth - lifeCostFromMana(manaCost));
	}
}
