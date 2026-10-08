package net.puffish.skillsmod.arpg.combat;

import java.util.Locale;
import java.util.Optional;

/** Typed meters used by the combat kernel instead of provider-specific mutable values. */
public enum CombatResource {
	STAMINA("stamina", Behavior.RESERVE),
	POSTURE("posture", Behavior.PRESSURE),
	MOMENTUM("momentum", Behavior.MOMENTUM),
	AMMUNITION("ammunition", Behavior.RESERVE),
	HEAT("heat", Behavior.PRESSURE),
	FOCUS("focus", Behavior.MOMENTUM),
	CONCEALMENT("concealment", Behavior.RESERVE),
	MANA("mana", Behavior.RESERVE),
	INSTABILITY("instability", Behavior.PRESSURE),
	COMMAND("command", Behavior.MOMENTUM),
	COHESION("cohesion", Behavior.RESERVE),
	POWER("power", Behavior.RESERVE),
	SCRAP("scrap", Behavior.RESERVE),
	TOXICITY("toxicity", Behavior.PRESSURE),
	CATALYST("catalyst", Behavior.RESERVE),
	BIOMASS("biomass", Behavior.RESERVE),
	TISSUE_CONDITION("tissue_condition", Behavior.RESERVE);

	private final String id;
	private final Behavior behavior;

	CombatResource(String id, Behavior behavior) {
		this.id = id;
		this.behavior = behavior;
	}

	public String id() {
		return id;
	}

	public Behavior behavior() {
		return behavior;
	}

	public static Optional<CombatResource> byId(String id) {
		if (id == null) {
			return Optional.empty();
		}
		var normalized = id.trim().toLowerCase(Locale.ROOT);
		for (var resource : values()) {
			if (resource.id.equals(normalized)) {
				return Optional.of(resource);
			}
		}
		return Optional.empty();
	}

	public enum Behavior {
		/** Starts available and is spent to perform or sustain actions. */
		RESERVE,
		/** Builds toward a harmful threshold such as posture break, heat or instability. */
		PRESSURE,
		/** Builds through skilled play and is spent on a payoff. */
		MOMENTUM
	}
}
