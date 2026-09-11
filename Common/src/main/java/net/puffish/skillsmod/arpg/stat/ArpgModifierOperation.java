package net.puffish.skillsmod.arpg.stat;

import java.util.Locale;
import java.util.Optional;

public enum ArpgModifierOperation {
	FLAT,
	INCREASED,
	REDUCED,
	MORE,
	LESS;

	public String getId() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Optional<ArpgModifierOperation> byId(String id) {
		for (var operation : values()) {
			if (operation.getId().equals(id)) {
				return Optional.of(operation);
			}
		}
		return Optional.empty();
	}
}
