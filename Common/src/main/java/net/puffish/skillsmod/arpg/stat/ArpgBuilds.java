package net.puffish.skillsmod.arpg.stat;

import net.minecraft.server.network.ServerPlayerEntity;
import net.puffish.skillsmod.arpg.character.ArpgProgression;
import net.puffish.skillsmod.arpg.combat.DamageConversion;
import net.puffish.skillsmod.arpg.combat.DamageType;
import net.puffish.skillsmod.arpg.data.ArpgContent;
import net.puffish.skillsmod.arpg.data.ArpgData;
import net.puffish.skillsmod.arpg.skill.SkillTag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public final class ArpgBuilds {
	private static final Map<ServerPlayerEntity, Cached> CACHE = new WeakHashMap<>();

	private ArpgBuilds() {
	}

	public static Build get(ServerPlayerEntity player) {
		var state = ArpgProgression.character(player);
		long revision = ArpgPlayerStats.revision(player);
		var cached = CACHE.get(player);
		if (cached == null || cached.revision != revision || cached.dataRevision != ArpgData.revision()
				|| cached.level != state.level() || !cached.primary.equals(state.primary())) {
			var modifiers = new ArrayList<>(ArpgPlayerStats.getModifiers(player));
			var discipline = ArpgData.content().disciplines().get(state.primary());
			if (discipline != null) {
				modifiers.addAll(discipline.modifiers());
				modifiers.add(flat(ArpgStat.STRENGTH, discipline.strength()));
				modifiers.add(flat(ArpgStat.DEXTERITY, discipline.dexterity()));
				modifiers.add(flat(ArpgStat.INTELLIGENCE, discipline.intelligence()));
				modifiers.add(flat(ArpgStat.MAXIMUM_LIFE, (state.level() - 1) * 2));
			}
			var ruleIds = ArpgPlayerStats.getRules(player);
			var rules = ruleIds.stream().sorted().map(ArpgData.content().rules()::get).filter(java.util.Objects::nonNull).toList();
			for (var rule : rules) {
				if (!rule.kind().equals("conditional") && !rule.kind().equals("trigger")) {
					modifiers.addAll(rule.modifiers());
				}
			}
			var raw = ArpgStatCompiler.compile(modifiers);
			double strength = raw.apply(ArpgStat.STRENGTH, 0);
			double dexterity = raw.apply(ArpgStat.DEXTERITY, 0);
			double intelligence = raw.apply(ArpgStat.INTELLIGENCE, 0);
			modifiers.add(flat(ArpgStat.MAXIMUM_LIFE, strength * .2));
			modifiers.add(flat(ArpgStat.MAXIMUM_MANA, intelligence * 2));
			modifiers.add(flat(ArpgStat.EVASION, dexterity));
			modifiers.add(flat(ArpgStat.CRITICAL_CHANCE, dexterity * .0002));
			modifiers.add(new ArpgStatModifier(ArpgStat.MELEE_DAMAGE, ArpgModifierOperation.INCREASED, strength * .002));
			modifiers.add(new ArpgStatModifier(ArpgStat.SPELL_DAMAGE, ArpgModifierOperation.INCREASED,
					intelligence * .002 + (ruleIds.contains("iron_will") ? strength * .002 : 0)));
			if (ruleIds.contains("iron_fortress")) {
				modifiers.replaceAll(m -> m.stat() == ArpgStat.EVASION ? new ArpgStatModifier(ArpgStat.ARMOR, m.operation(), m.value()) : m);
			}
			var conversions = new ArrayList<DamageConversion>();
			for (var rule : rules) {
				if (rule.conversion() != null) {
					conversions.add(rule.conversion());
				}
			}
			if (ruleIds.contains("elemental_avatar")) {
				conversions.clear();
				conversions.add(new DamageConversion(DamageType.PHYSICAL, DamageType.FIRE, 1.0 / 3));
				conversions.add(new DamageConversion(DamageType.PHYSICAL, DamageType.COLD, 1.0 / 3));
				conversions.add(new DamageConversion(DamageType.PHYSICAL, DamageType.LIGHTNING, 1.0 / 3));
			}
			cached = new Cached(revision, ArpgData.revision(), state.level(), state.primary(),
					new Build(List.copyOf(modifiers), ArpgStatCompiler.compile(modifiers), rules, List.copyOf(conversions), ruleIds));
			CACHE.put(player, cached);
		}
		return cached.build;
	}

	private static ArpgStatModifier flat(ArpgStat stat, double value) {
		return new ArpgStatModifier(stat, ArpgModifierOperation.FLAT, value);
	}

	public static void clear(ServerPlayerEntity player) {
		CACHE.remove(player);
	}

	private record Cached(long revision, long dataRevision, int level, String primary, Build build) {
	}

	public static final class Build {
		private final List<ArpgStatModifier> modifiers;
		private final ArpgStatSnapshot stats;
		private final List<ArpgContent.Rule> conditionals;
		private final Map<String, List<ArpgContent.Rule>> triggers = new HashMap<>();
		private final List<DamageConversion> conversions;
		private final Set<String> rules;
		private final Map<String, ArpgStatSnapshot> skillCache = new HashMap<>();

		private Build(List<ArpgStatModifier> modifiers, ArpgStatSnapshot stats, List<ArpgContent.Rule> definitions,
				List<DamageConversion> conversions, Set<String> rules) {
			this.modifiers = modifiers;
			this.stats = stats;
			this.conversions = conversions;
			this.rules = rules;
			conditionals = definitions.stream().filter(r -> r.kind().equals("conditional")).toList();
			for (var rule : definitions) {
				if (rule.kind().equals("trigger")) {
					triggers.computeIfAbsent(rule.event(), ignored -> new ArrayList<>()).add(rule);
				}
			}
		}

		public ArpgStatSnapshot stats() {
			return stats;
		}

		public boolean has(String id) {
			return rules.contains(id);
		}

		public List<DamageConversion> conversions() {
			return conversions;
		}

		public List<ArpgContent.Rule> triggers(String event) {
			return triggers.getOrDefault(event, List.of());
		}

		public ArpgStatSnapshot forSkill(String id, Set<SkillTag> tags, Predicate<String> condition) {
			var active = conditionals.stream().filter(r -> (r.skill().isEmpty() || r.skill().equals(id)) && matchesTags(r, tags)
					&& condition.test(r.condition())).toList();
			if (active.isEmpty()) {
				return stats;
			}
			String key = String.join("|", active.stream().map(ArpgContent.Rule::id).toList());
			if (skillCache.size() >= 128) {
				skillCache.clear();
			}
			return skillCache.computeIfAbsent(key, ignored -> {
				var combined = new ArrayList<>(modifiers);
				active.forEach(rule -> combined.addAll(rule.modifiers()));
				return ArpgStatCompiler.compile(combined);
			});
		}
	}

	public static boolean matchesTags(ArpgContent.Rule rule, Set<SkillTag> tags) {
		return rule.tags().stream().allMatch(tag -> tags.stream().anyMatch(t -> t.name().equalsIgnoreCase(tag)));
	}
}
