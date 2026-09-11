package net.puffish.skillsmod.arpg.item;

import java.util.List;

public record ArpgItemData(int schema, String base, int itemLevel, String rarity, List<Roll> affixes,
		List<String> runes, int forgingPotential, boolean corrupted, String unique) {
	public ArpgItemData {
		affixes = List.copyOf(affixes);
		runes = List.copyOf(runes);
		if (schema != 1 || itemLevel < 1 || itemLevel > 100 || forgingPotential < 0 || forgingPotential > 100
				|| affixes.size() > 6 || runes.size() > 3 || base == null || unique == null
				|| !List.of("normal", "magic", "rare", "unique").contains(rarity)) {
			throw new IllegalArgumentException("Invalid ARPG item data");
		}
	}

	public record Roll(String affix, int tier, double value) {
		public Roll {
			if (affix == null || tier < 1 || tier > 5 || !Double.isFinite(value)) {
				throw new IllegalArgumentException("Invalid rolled affix");
			}
		}
	}
}
