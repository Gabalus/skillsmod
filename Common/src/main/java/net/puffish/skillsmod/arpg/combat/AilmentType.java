package net.puffish.skillsmod.arpg.combat;

import java.util.Locale;
import java.util.Set;

/** Typed status identity, independent of attack/spell delivery and provider classes. */
public enum AilmentType {
	BLEED(DamageType.PHYSICAL, 1), IGNITE(DamageType.FIRE, 1), POISON(DamageType.NATURE, 8);

	private final DamageType damage;
	private final int stackLimit;

	AilmentType(DamageType damage, int stackLimit) {
		this.damage = damage;
		this.stackLimit = stackLimit;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public DamageType damage() {
		return damage;
	}

	public int stackLimit() {
		return stackLimit;
	}

	public Set<String> tags() {
		return Set.of("ailment", "damage_over_time", id(), damage.name().toLowerCase(Locale.ROOT));
	}
}
