package net.puffish.skillsmod.arpg.metric;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public record QualifierSet(Set<String> values) {
	private static final QualifierSet EMPTY = new QualifierSet(Set.of());

	public QualifierSet {
		if (values == null) {
			throw new IllegalArgumentException("values cannot be null");
		}
		var normalized = new TreeSet<String>();
		for (var value : values) {
			if (value == null) {
				throw new IllegalArgumentException("qualifier cannot be null");
			}
			var qualifier = value.trim().toLowerCase(Locale.ROOT);
			if (qualifier.isEmpty()) {
				throw new IllegalArgumentException("qualifier cannot be blank");
			}
			normalized.add(qualifier);
		}
		values = Collections.unmodifiableSet(normalized);
	}

	public static QualifierSet empty() {
		return EMPTY;
	}

	public static QualifierSet of(String... values) {
		return values.length == 0 ? empty() : new QualifierSet(Set.copyOf(Arrays.asList(values)));
	}

	public boolean matches(QualifierSet actual) {
		return actual.values.containsAll(values);
	}

	public boolean contains(String qualifier) {
		return values.contains(qualifier.trim().toLowerCase(Locale.ROOT));
	}
}
