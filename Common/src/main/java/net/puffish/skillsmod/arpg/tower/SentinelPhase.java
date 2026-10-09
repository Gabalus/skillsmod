package net.puffish.skillsmod.arpg.tower;

/** Health-derived presentation matches Epic Fight's strict less_ratio predicate. */
public enum SentinelPhase {
	GUARDING, ENRAGED;

	public static final float ENRAGE_RATIO = .5f;

	public static float healthFraction(float health, float maximum) {
		if (!Float.isFinite(health) || !Float.isFinite(maximum) || maximum <= 0) {
			return 0;
		}
		return Math.max(0, Math.min(1, health / maximum));
	}

	public static SentinelPhase at(float health, float maximum) {
		return healthFraction(health, maximum) < ENRAGE_RATIO ? ENRAGED : GUARDING;
	}
}
