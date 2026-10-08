package net.puffish.skillsmod.arpg.sandbox;

/** Bounded quality bonuses, applied once through the existing ARPG stat pipeline. */
public final class CraftQualityEffects {
	public record Bonuses(double meleeDamage, double spellDamage) {
	}

	private CraftQualityEffects() {
	}

	public static Bonuses from(CraftedItemData item) {
		return new Bonuses(score(item, "arpg:forge") * 0.0015, score(item, "arpg:inscribe") * 0.0008);
	}

	private static int score(CraftedItemData item, String operation) {
		var quality = item.stages().get(operation);
		return quality == null ? 0 : quality.score();
	}
}
