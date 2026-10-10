package net.puffish.skillsmod.arpg.character;

import java.util.HashSet;
import java.util.List;

/** Bounded server-authored primary-class choices; choosing does not grant equipment or experience. */
public record ClassSelectionView(String primary, List<Option> options) {
	public ClassSelectionView {
		if (primary == null || primary.length() > 64 || options == null || options.size() > 32) {
			throw new IllegalArgumentException("Invalid class selection view");
		}
		var ids = new HashSet<String>();
		for (var option : options) {
			if (option == null || !ids.add(option.id())) {
				throw new IllegalArgumentException("Duplicate or missing class selection option");
			}
		}
		options = List.copyOf(options);
	}

	public record Option(String id, String title, int strength, int dexterity, int intelligence) {
		public Option {
			if (id == null || !id.matches("[a-z0-9_:/.-]{1,64}") || title == null || title.isBlank()
					|| title.length() > 80 || strength < 0 || dexterity < 0 || intelligence < 0) {
				throw new IllegalArgumentException("Invalid class selection option");
			}
		}
	}
}
