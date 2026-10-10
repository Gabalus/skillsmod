package net.puffish.skillsmod.arpg.combat;

import java.util.Locale;
import java.util.Set;

/** Single-hit bonuses; never emits damage, rolls crits or manufactures an ailment application. */
public enum ElementalReaction {
	NONE(1), CONDUCTIVITY(1.2), SHATTER(1.25);

	private final double multiplier;

	ElementalReaction(double multiplier) {
		this.multiplier = multiplier;
	}

	public static ElementalReaction select(ElementalState state, DamageType type, boolean melee, boolean damageOverTime) {
		if (damageOverTime || type == null) {
			return NONE;
		}
		if (type == DamageType.LIGHTNING && state.wetTicks() > 0) {
			return CONDUCTIVITY;
		}
		return type == DamageType.PHYSICAL && melee && state.freezeTicks() > 0 ? SHATTER : NONE;
	}

	/** Mixed/unknown custom channels fail closed instead of treating the entire hit as one type. */
	public static DamageType singleType(Set<String> tags) {
		DamageType result = null;
		for (var type : DamageType.values()) {
			if (tags.contains(type.name().toLowerCase(Locale.ROOT))) {
				if (result != null) {
					return null;
				}
				result = type;
			}
		}
		return result;
	}

	public double scale(double amount) {
		if (!Double.isFinite(amount) || amount < 0) {
			throw new IllegalArgumentException("Reaction damage must be finite and nonnegative");
		}
		return Math.min(Float.MAX_VALUE, amount * multiplier);
	}

	public ElementalState settle(ElementalState state) {
		return this == SHATTER ? state.shatter() : state;
	}

	/** A completed damage call must still refer to the same frozen application. */
	public static boolean sameFreeze(ElementalState expected, ElementalState current) {
		return expected.freezeTicks() > 0 && expected.freezeTicks() == current.freezeTicks()
				&& expected.freezeRecoveryTicks() == current.freezeRecoveryTicks()
				&& java.util.Objects.equals(expected.coldOwner(), current.coldOwner())
				&& expected.coldSkill().equals(current.coldSkill());
	}

	/** One claim per victim/server tick, completed only by positive health damage. */
	public record Reservation(ElementalState frozen, int tick, boolean completed) {
		public Reservation {
			if (frozen == null || frozen.freezeTicks() <= 0) {
				throw new IllegalArgumentException("Shatter requires a frozen application");
			}
		}

		public static boolean canReserve(Reservation previous, int tick) {
			return previous == null || previous.tick() != tick;
		}

		public boolean canComplete(int currentTick, double healthDamage) {
			return !completed && tick == currentTick && Double.isFinite(healthDamage) && healthDamage > 0;
		}

		public ElementalState settle(ElementalState current, int currentTick, double healthDamage) {
			return canComplete(currentTick, healthDamage) && sameFreeze(frozen, current) ? current.shatter() : current;
		}

		public Reservation complete() {
			return new Reservation(frozen, tick, true);
		}
	}
}
