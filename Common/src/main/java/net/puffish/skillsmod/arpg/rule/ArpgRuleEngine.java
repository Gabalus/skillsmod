package net.puffish.skillsmod.arpg.rule;

import net.puffish.skillsmod.arpg.combat.DamageConversion;
import net.puffish.skillsmod.arpg.combat.DamageType;
import net.puffish.skillsmod.arpg.data.ArpgContent;
import net.puffish.skillsmod.arpg.stat.ArpgStatModifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Compiles catalog rule definitions into typed runtime semantics.
 *
 * <p>The catalog keeps stable string IDs for data-pack compatibility; runtime code never has to
 * switch on those raw strings. Evaluation is pure and deterministic, so loader event adapters can
 * supply combat state without duplicating rule semantics.</p>
 */
public final class ArpgRuleEngine {
	private ArpgRuleEngine() {
	}

	public enum Kind {
		IRON_WILL,
		BLOOD_MAGIC,
		IRON_FORTRESS,
		ELEMENTAL_AVATAR,
		CONVERSION,
		CONDITIONAL,
		TRIGGER,
		JEWEL,
		SPELL_LIFE_COST
	}

	public enum Event {
		NONE,
		HIT,
		CRIT,
		KILL,
		BLOCK,
		DODGE,
		CAST,
		ATTACK,
		DAMAGE_TAKEN,
		AILMENT
	}

	public enum Condition {
		ALWAYS,
		LOW_LIFE,
		FULL_LIFE,
		LOW_MANA,
		CLOSE,
		DISTANT,
		IGNITED,
		BLEEDING,
		POISONED,
		SHIELD,
		DUAL_WIELD,
		MOVING
	}

	public enum Action {
		NONE,
		HEAL,
		MANA,
		WARD,
		COOLDOWN,
		EXPLODE,
		IGNITE,
		BLEED,
		POISON,
		BUFF,
		COUNTERSPELL
	}

	public record CompiledRule(
			ArpgContent.Rule definition,
			Kind kind,
			Event event,
			Condition condition,
			Action action
	) {
	}

	public record Context(
			Event event,
			String skill,
			Set<String> tags,
			boolean lowLife,
			boolean fullLife,
			boolean lowMana,
			boolean close,
			boolean distant,
			boolean ignited,
			boolean bleeding,
			boolean poisoned,
			boolean shield,
			boolean dualWield,
			boolean moving
	) {
		public Context {
			skill = skill == null ? "" : skill;
			tags = tags == null ? Set.of() : Set.copyOf(tags);
		}

		public static Context of(Event event, String skill, Set<String> tags) {
			return new Context(event, skill, tags, false, false, false, false, false,
					false, false, false, false, false, false);
		}

		public Context with(Condition condition) {
			return switch (condition) {
				case ALWAYS -> this;
				case LOW_LIFE -> copy(true, fullLife, lowMana, close, distant, ignited, bleeding, poisoned, shield, dualWield, moving);
				case FULL_LIFE -> copy(lowLife, true, lowMana, close, distant, ignited, bleeding, poisoned, shield, dualWield, moving);
				case LOW_MANA -> copy(lowLife, fullLife, true, close, distant, ignited, bleeding, poisoned, shield, dualWield, moving);
				case CLOSE -> copy(lowLife, fullLife, lowMana, true, distant, ignited, bleeding, poisoned, shield, dualWield, moving);
				case DISTANT -> copy(lowLife, fullLife, lowMana, close, true, ignited, bleeding, poisoned, shield, dualWield, moving);
				case IGNITED -> copy(lowLife, fullLife, lowMana, close, distant, true, bleeding, poisoned, shield, dualWield, moving);
				case BLEEDING -> copy(lowLife, fullLife, lowMana, close, distant, ignited, true, poisoned, shield, dualWield, moving);
				case POISONED -> copy(lowLife, fullLife, lowMana, close, distant, ignited, bleeding, true, shield, dualWield, moving);
				case SHIELD -> copy(lowLife, fullLife, lowMana, close, distant, ignited, bleeding, poisoned, true, dualWield, moving);
				case DUAL_WIELD -> copy(lowLife, fullLife, lowMana, close, distant, ignited, bleeding, poisoned, shield, true, moving);
				case MOVING -> copy(lowLife, fullLife, lowMana, close, distant, ignited, bleeding, poisoned, shield, dualWield, true);
			};
		}

		private Context copy(
				boolean newLowLife,
				boolean newFullLife,
				boolean newLowMana,
				boolean newClose,
				boolean newDistant,
				boolean newIgnited,
				boolean newBleeding,
				boolean newPoisoned,
				boolean newShield,
				boolean newDualWield,
				boolean newMoving
		) {
			return new Context(event, skill, tags, newLowLife, newFullLife, newLowMana, newClose,
					newDistant, newIgnited, newBleeding, newPoisoned, newShield, newDualWield, newMoving);
		}
	}

	public record Trigger(
			String id,
			Action action,
			double value,
			int cooldown,
			int duration,
			List<ArpgStatModifier> modifiers
	) {
		public Trigger {
			modifiers = List.copyOf(modifiers);
		}
	}

	public record Evaluation(
			List<ArpgStatModifier> modifiers,
			List<DamageConversion> conversions,
			List<Trigger> triggers,
			boolean ironWill,
			boolean bloodMagic,
			boolean ironFortress,
			boolean elementalOnly,
			boolean spellLifeCost
	) {
		public Evaluation {
			modifiers = List.copyOf(modifiers);
			conversions = List.copyOf(conversions);
			triggers = List.copyOf(triggers);
		}
	}

	public static List<CompiledRule> resolve(ArpgContent content, Collection<String> activeRuleIds) {
		return activeRuleIds.stream()
				.distinct()
				.sorted(Comparator.naturalOrder())
				.map(content.rules()::get)
				.filter(java.util.Objects::nonNull)
				.map(ArpgRuleEngine::compile)
				.toList();
	}

	public static Evaluation evaluate(ArpgContent content, Collection<String> activeRuleIds, Context context) {
		var modifiers = new ArrayList<ArpgStatModifier>();
		var conversions = new ArrayList<DamageConversion>();
		var triggers = new ArrayList<Trigger>();
		boolean ironWill = false;
		boolean bloodMagic = false;
		boolean ironFortress = false;
		boolean elementalOnly = false;
		boolean spellLifeCost = false;

		for (var rule : resolve(content, activeRuleIds)) {
			if (!matchesContext(rule, context)) {
				continue;
			}

			if (rule.kind() == Kind.TRIGGER) {
				if (rule.event() == context.event()) {
					var definition = rule.definition();
					triggers.add(new Trigger(
							definition.id(),
							rule.action(),
							definition.value(),
							definition.cooldown(),
							definition.duration(),
							definition.modifiers()
					));
				}
				continue;
			}

			modifiers.addAll(rule.definition().modifiers());
			switch (rule.kind()) {
				case IRON_WILL -> ironWill = true;
				case BLOOD_MAGIC -> bloodMagic = true;
				case IRON_FORTRESS -> ironFortress = true;
				case ELEMENTAL_AVATAR -> {
					elementalOnly = true;
					conversions.add(new DamageConversion(DamageType.PHYSICAL, DamageType.FIRE, 1));
					conversions.add(new DamageConversion(DamageType.PHYSICAL, DamageType.COLD, 1));
					conversions.add(new DamageConversion(DamageType.PHYSICAL, DamageType.LIGHTNING, 1));
				}
				case CONVERSION -> {
					if (rule.definition().conversion() != null) {
						conversions.add(rule.definition().conversion());
					}
				}
				case SPELL_LIFE_COST -> spellLifeCost = true;
				case CONDITIONAL, JEWEL -> {
					// Their modifiers were collected above after condition/tag matching.
				}
				case TRIGGER -> throw new IllegalStateException("Trigger handled before rule switch");
				default -> throw new IllegalStateException("Unsupported ARPG rule kind: " + rule.kind());
			}
		}

		return new Evaluation(modifiers, conversions, triggers, ironWill, bloodMagic, ironFortress,
				elementalOnly, spellLifeCost);
	}

	private static CompiledRule compile(ArpgContent.Rule definition) {
		return new CompiledRule(
				definition,
				parse(Kind.class, definition.kind()),
				parse(Event.class, definition.event()),
				parse(Condition.class, definition.condition()),
				parse(Action.class, definition.action())
		);
	}

	private static boolean matchesContext(CompiledRule rule, Context context) {
		var definition = rule.definition();
		if (!definition.skill().isEmpty() && !definition.skill().equals(context.skill())) {
			return false;
		}
		if (!definition.tags().isEmpty() && !context.tags().containsAll(definition.tags())) {
			return false;
		}
		return switch (rule.condition()) {
			case ALWAYS -> true;
			case LOW_LIFE -> context.lowLife();
			case FULL_LIFE -> context.fullLife();
			case LOW_MANA -> context.lowMana();
			case CLOSE -> context.close();
			case DISTANT -> context.distant();
			case IGNITED -> context.ignited();
			case BLEEDING -> context.bleeding();
			case POISONED -> context.poisoned();
			case SHIELD -> context.shield();
			case DUAL_WIELD -> context.dualWield();
			case MOVING -> context.moving();
		};
	}

	private static <E extends Enum<E>> E parse(Class<E> type, String id) {
		try {
			return Enum.valueOf(type, id.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Unsupported ARPG " + type.getSimpleName().toLowerCase(Locale.ROOT)
					+ " `" + id + "`", exception);
		}
	}
}
