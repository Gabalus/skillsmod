package net.puffish.skillsmod.arpg.mechanics;

import java.util.Locale;

public record ConversionEdge(String from, String to) implements Comparable<ConversionEdge> {
	public ConversionEdge {
		from = normalize(from, "from");
		to = normalize(to, "to");
		if (from.equals(to)) {
			throw new IllegalArgumentException("conversion edge cannot target its own source");
		}
	}

	@Override
	public int compareTo(ConversionEdge other) {
		int fromComparison = from.compareTo(other.from);
		return fromComparison != 0 ? fromComparison : to.compareTo(other.to);
	}

	private static String normalize(String value, String name) {
		if (value == null) {
			throw new IllegalArgumentException(name + " cannot be null");
		}
		var normalized = value.trim().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException(name + " cannot be blank");
		}
		return normalized;
	}
}
