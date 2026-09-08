package net.puffish.skillsmod.arpg.item;

import net.puffish.skillsmod.arpg.data.ArpgContent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class LootGenerator {
	private LootGenerator() {
	}

	public static ArpgItemData generate(ArpgContent content, String baseId, int level, String rarity, Random random) {
		var base = content.bases().get(baseId);
		if (base == null || level < base.level() || level > 100 || rarity.equals("unique")) {
			throw new IllegalArgumentException("Base is unavailable at this item level");
		}
		var rolls = new ArrayList<ArpgItemData.Roll>();
		var groups = new HashSet<String>();
		int prefixes = rarity.equals("rare") ? 2 + random.nextInt(2) : rarity.equals("magic") ? 1 : 0;
		int suffixes = rarity.equals("rare") ? 2 + random.nextInt(2) : rarity.equals("magic") ? 1 : 0;
		roll(content, base, level, true, prefixes, groups, rolls, random);
		roll(content, base, level, false, suffixes, groups, rolls, random);
		return new ArpgItemData(1, baseId, level, rarity, rolls, List.of(), 15 + random.nextInt(16), false, "");
	}

	private static void roll(ArpgContent content, ArpgContent.Base base, int level, boolean prefix, int count,
			Set<String> groups, List<ArpgItemData.Roll> rolls, Random random) {
		for (int i = 0; i < count; i++) {
			var eligible = content.affixes().values().stream().filter(a -> a.prefix() == prefix && !groups.contains(a.group())
					&& a.tags().stream().anyMatch(base.tags()::contains) && a.tiers().stream().anyMatch(t -> t.level() <= level))
					.sorted(java.util.Comparator.comparing(ArpgContent.Affix::id)).toList();
			if (eligible.isEmpty()) {
				break;
			}
			var affix = eligible.get(random.nextInt(eligible.size()));
			var tiers = affix.tiers().stream().filter(t -> t.level() <= level).toList();
			int weight = random.nextInt(tiers.stream().mapToInt(ArpgContent.Tier::weight).sum());
			var selected = tiers.get(0);
			for (var tier : tiers) {
				weight -= tier.weight();
				if (weight < 0) {
					selected = tier;
					break;
				}
			}
			rolls.add(new ArpgItemData.Roll(affix.id(), selected.tier(), selected.min() + random.nextDouble() * (selected.max() - selected.min())));
			groups.add(affix.group());
		}
	}

	public static ArpgItemData unique(ArpgContent content, String id, int level) {
		var unique = content.uniques().get(id);
		if (unique == null || level < content.bases().get(unique.base()).level()) {
			throw new IllegalArgumentException("Unique is unavailable at this item level");
		}
		return new ArpgItemData(1, unique.base(), level, "unique", List.of(), List.of(), 0, false, id);
	}

	public static ArpgItemData reforge(ArpgContent content, ArpgItemData item, Random random) {
		if (item.corrupted() || item.forgingPotential() < 3 || item.rarity().equals("unique")) {
			throw new IllegalStateException("This item cannot be reforged");
		}
		var rolled = generate(content, item.base(), item.itemLevel(), "rare", random);
		return new ArpgItemData(1, item.base(), item.itemLevel(), "rare", rolled.affixes(), item.runes(), item.forgingPotential() - 3, false, "");
	}

	public static ArpgItemData upgrade(ArpgContent content, ArpgItemData item, int index, Random random) {
		if (item.corrupted() || item.forgingPotential() < 2 || index < 0 || index >= item.affixes().size()) {
			throw new IllegalStateException("This affix cannot be upgraded");
		}
		var current = item.affixes().get(index);
		var affix = content.affixes().get(current.affix());
		var tier = affix.tiers().stream().filter(t -> t.tier() == current.tier() - 1 && t.level() <= item.itemLevel()).findFirst()
				.orElseThrow(() -> new IllegalStateException("The next tier requires a higher item level"));
		var rolls = new ArrayList<>(item.affixes());
		rolls.set(index, new ArpgItemData.Roll(affix.id(), tier.tier(), tier.min() + random.nextDouble() * (tier.max() - tier.min())));
		return new ArpgItemData(1, item.base(), item.itemLevel(), item.rarity(), rolls, item.runes(), item.forgingPotential() - 2, false, item.unique());
	}
}
