package net.puffish.skillsmod.arpg.combat;

import java.util.Locale;
import java.util.Optional;

/** Distinct moment-to-moment combat grammars. Classes and providers adapt into one of these pillars. */
public enum CombatPillar {
	MARTIAL("martial"),
	GUNNER("gunner"),
	HUNTER("hunter"),
	ARCANE("arcane"),
	COMMANDER("commander"),
	ENGINEER("engineer"),
	ALCHEMIST("alchemist"),
	LIVING("living");

	private final String id;

	CombatPillar(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static Optional<CombatPillar> byId(String id) {
		if (id == null) {
			return Optional.empty();
		}
		var normalized = id.trim().toLowerCase(Locale.ROOT);
		for (var pillar : values()) {
			if (pillar.id.equals(normalized)) {
				return Optional.of(pillar);
			}
		}
		return Optional.empty();
	}

	/** Default combat grammar selected by the six built-in progression disciplines. */
	public static CombatPillar forDiscipline(String discipline) {
		if (discipline == null) {
			return MARTIAL;
		}
		return switch (discipline.trim().toLowerCase(Locale.ROOT)) {
			case "ranger" -> HUNTER;
			case "arcanist", "shaman" -> ARCANE;
			default -> MARTIAL;
		};
	}
}
