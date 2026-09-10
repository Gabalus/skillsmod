package net.puffish.skillsmod.arpg.rule;

/** Pure resource arithmetic shared by optional provider bridges. */
public final class ArpgResourceSemantics {
	private static final double LOW_RESOURCE_THRESHOLD = 0.5;

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
}
