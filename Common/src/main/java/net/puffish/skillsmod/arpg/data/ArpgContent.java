package net.puffish.skillsmod.arpg.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.puffish.skillsmod.arpg.combat.DamageConversion;
import net.puffish.skillsmod.arpg.combat.DamageType;
import net.puffish.skillsmod.arpg.skill.SkillTag;
import net.puffish.skillsmod.arpg.stat.ArpgModifierOperation;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatModifier;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable, validated catalog. A failed data-pack reload never replaces the active catalog. */
public record ArpgContent(Map<String, Discipline> disciplines, Map<String, Skill> skills,
		Map<String, Rule> rules, Map<String, Base> bases, Map<String, Affix> affixes, Map<String, Unique> uniques) {
	public ArpgContent {
		disciplines = Map.copyOf(disciplines);
		skills = Map.copyOf(skills);
		rules = Map.copyOf(rules);
		bases = Map.copyOf(bases);
		affixes = Map.copyOf(affixes);
		uniques = Map.copyOf(uniques);
	}

	public static ArpgContent read(Reader reader) {
		var root = JsonParser.parseReader(reader).getAsJsonObject();
		if (integer(root, "schema", 0) != 1) {
			throw new IllegalArgumentException("Unsupported ARPG catalog schema");
		}
		var disciplines = new LinkedHashMap<String, Discipline>();
		for (var element : root.getAsJsonArray("disciplines")) {
			var o = element.getAsJsonObject();
			put(disciplines, string(o, "id"), new Discipline(string(o, "id"), string(o, "title"),
					strings(o, "ascendancies"), modifiers(o), integer(o, "strength", 10), integer(o, "dexterity", 10), integer(o, "intelligence", 10)));
		}
		var skills = new LinkedHashMap<String, Skill>();
		for (var element : root.getAsJsonArray("skills")) {
			var o = element.getAsJsonObject();
			put(skills, string(o, "id"), new Skill(string(o, "id"), string(o, "title"), string(o, "provider"),
					strings(o, "tags").stream().map(t -> SkillTag.valueOf(t.toUpperCase(Locale.ROOT))).collect(Collectors.toUnmodifiableSet()),
					DamageType.valueOf(string(o, "damage_type").toUpperCase(Locale.ROOT)), number(o, "coefficient", 1),
					number(o, "cost", 0), integer(o, "cooldown", 20), number(o, "range", 4), integer(o, "level", 1),
					optionalString(o, "discipline", ""), optionalString(o, "weapon", "any"), optionalString(o, "effect", "hit")));
		}
		var rules = new LinkedHashMap<String, Rule>();
		for (var element : root.getAsJsonArray("rules")) {
			var o = element.getAsJsonObject();
			DamageConversion conversion = null;
			if (o.has("conversion")) {
				var c = o.getAsJsonObject("conversion");
				conversion = new DamageConversion(DamageType.valueOf(string(c, "from").toUpperCase(Locale.ROOT)),
						DamageType.valueOf(string(c, "to").toUpperCase(Locale.ROOT)), number(c, "fraction", 0));
			}
			var rule = new Rule(string(o, "id"), string(o, "title"), string(o, "description"), string(o, "kind"),
					optionalString(o, "event", "none"), optionalString(o, "condition", "always"), optionalString(o, "action", "none"),
					optionalString(o, "skill", ""), strings(o, "tags"), number(o, "value", 0), integer(o, "cooldown", 20),
					integer(o, "duration", 60), modifiers(o), conversion);
			if (!Set.of("iron_will", "blood_magic", "iron_fortress", "elemental_avatar", "conversion", "conditional", "trigger", "jewel", "spell_life_cost").contains(rule.kind())) {
				throw new IllegalArgumentException("Unknown rule kind: " + rule.kind());
			}
			if (!Set.of("always", "low_life", "full_life", "low_mana", "close", "distant", "ignited", "bleeding", "poisoned", "shield", "dual_wield", "moving").contains(rule.condition())) {
				throw new IllegalArgumentException("Unknown rule condition: " + rule.condition());
			}
			if (!Set.of("none", "hit", "crit", "kill", "block", "dodge", "cast", "attack", "damage_taken", "ailment").contains(rule.event())) {
				throw new IllegalArgumentException("Unknown rule event: " + rule.event());
			}
			if (!Set.of("none", "heal", "mana", "ward", "cooldown", "explode", "ignite", "bleed", "poison", "buff", "counterspell").contains(rule.action())) {
				throw new IllegalArgumentException("Unknown rule action: " + rule.action());
			}
			put(rules, rule.id(), rule);
		}
		var bases = new LinkedHashMap<String, Base>();
		for (var element : root.getAsJsonArray("bases")) {
			var o = element.getAsJsonObject();
			put(bases, string(o, "id"), new Base(string(o, "id"), string(o, "title"), string(o, "item"),
					string(o, "slot"), strings(o, "tags"), integer(o, "level", 1), modifiers(o)));
		}
		var affixes = new LinkedHashMap<String, Affix>();
		for (var element : root.getAsJsonArray("affixes")) {
			var o = element.getAsJsonObject();
			var tiers = new ArrayList<Tier>();
			for (var tierElement : o.getAsJsonArray("tiers")) {
				var t = tierElement.getAsJsonObject();
				tiers.add(new Tier(integer(t, "tier", 1), integer(t, "level", 1), number(t, "min", 0), number(t, "max", 0), integer(t, "weight", 100)));
			}
			put(affixes, string(o, "id"), new Affix(string(o, "id"), string(o, "title"), string(o, "group"),
					o.get("prefix").getAsBoolean(), strings(o, "tags"), ArpgStat.byId(string(o, "stat")).orElseThrow(),
					ArpgModifierOperation.byId(string(o, "operation")).orElseThrow(), List.copyOf(tiers)));
		}
		var uniques = new LinkedHashMap<String, Unique>();
		for (var element : root.getAsJsonArray("uniques")) {
			var o = element.getAsJsonObject();
			var unique = new Unique(string(o, "id"), string(o, "title"), string(o, "base"), modifiers(o), strings(o, "rules"));
			if (!bases.containsKey(unique.base()) || !rules.keySet().containsAll(unique.rules())) {
				throw new IllegalArgumentException("Unknown base or rule on unique " + unique.id());
			}
			put(uniques, unique.id(), unique);
		}
		for (var skill : skills.values()) {
			if (!skill.discipline().isEmpty() && !disciplines.containsKey(skill.discipline())) {
				throw new IllegalArgumentException("Unknown skill discipline " + skill.discipline());
			}
		}
		return new ArpgContent(disciplines, skills, rules, bases, affixes, uniques);
	}

	private static <T> void put(Map<String, T> map, String id, T value) {
		if (!id.matches("[a-z0-9_:/.-]+") || map.putIfAbsent(id, value) != null) {
			throw new IllegalArgumentException("Invalid or duplicate ARPG ID: " + id);
		}
	}

	private static String string(JsonObject o, String key) {
		return o.get(key).getAsString();
	}

	private static String optionalString(JsonObject o, String key, String fallback) {
		return o.has(key) ? string(o, key) : fallback;
	}

	private static int integer(JsonObject o, String key, int fallback) {
		double value = number(o, key, fallback);
		if (value != Math.rint(value) || value < 0 || value > 1_000_000) {
			throw new IllegalArgumentException("Invalid integer " + key);
		}
		return (int) value;
	}

	private static double number(JsonObject o, String key, double fallback) {
		double value = o.has(key) ? o.get(key).getAsDouble() : fallback;
		if (!Double.isFinite(value) || Math.abs(value) > 1_000_000) {
			throw new IllegalArgumentException("Invalid number " + key);
		}
		return value;
	}

	private static Set<String> strings(JsonObject o, String key) {
		if (!o.has(key)) {
			return Set.of();
		}
		return o.getAsJsonArray(key).asList().stream().map(JsonElement::getAsString).collect(Collectors.toUnmodifiableSet());
	}

	private static List<ArpgStatModifier> modifiers(JsonObject o) {
		var modifiers = new ArrayList<ArpgStatModifier>();
		for (var element : o.has("modifiers") ? o.getAsJsonArray("modifiers") : new JsonArray()) {
			var m = element.getAsJsonObject();
			modifiers.add(new ArpgStatModifier(ArpgStat.byId(string(m, "stat")).orElseThrow(),
					ArpgModifierOperation.byId(string(m, "operation")).orElseThrow(), number(m, "value", 0)));
		}
		return List.copyOf(modifiers);
	}

	public record Discipline(String id, String title, Set<String> ascendancies, List<ArpgStatModifier> modifiers,
			int strength, int dexterity, int intelligence) {
	}

	public record Skill(String id, String title, String provider, Set<SkillTag> tags, DamageType damageType,
			double coefficient, double cost, int cooldown, double range, int level, String discipline, String weapon, String effect) {
		public Skill {
			if (!Set.of("weapon", "irons").contains(provider) || coefficient < 0 || cost < 0 || cooldown < 1 || range <= 0 || range > 64 || level < 1 || level > 100) {
				throw new IllegalArgumentException("Invalid skill " + id);
			}
		}
	}

	public record Rule(String id, String title, String description, String kind, String event, String condition, String action,
			String skill, Set<String> tags, double value, int cooldown, int duration, List<ArpgStatModifier> modifiers, DamageConversion conversion) {
	}

	public record Base(String id, String title, String item, String slot, Set<String> tags, int level, List<ArpgStatModifier> modifiers) {
	}

	public record Tier(int tier, int level, double min, double max, int weight) {
		public Tier {
			if (tier < 1 || level < 1 || level > 100 || max < min || weight < 1) {
				throw new IllegalArgumentException("Invalid affix tier");
			}
		}
	}

	public record Affix(String id, String title, String group, boolean prefix, Set<String> tags,
			ArpgStat stat, ArpgModifierOperation operation, List<Tier> tiers) {
	}

	public record Unique(String id, String title, String base, List<ArpgStatModifier> modifiers, Set<String> rules) {
	}
}
