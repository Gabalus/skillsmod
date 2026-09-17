package net.puffish.skillsmod.arpg.metric;

import java.util.Locale;

public record ResolvedKey(MetricKey metricKey, QualifierSet qualifiers, String scope) {
	public ResolvedKey {
		if (metricKey == null) {
			throw new IllegalArgumentException("metricKey cannot be null");
		}
		if (qualifiers == null) {
			throw new IllegalArgumentException("qualifiers cannot be null");
		}
		if (scope == null) {
			throw new IllegalArgumentException("scope cannot be null");
		}
		scope = scope.trim().toLowerCase(Locale.ROOT);
		if (scope.isEmpty()) {
			throw new IllegalArgumentException("scope cannot be blank");
		}
	}

	public static ResolvedKey global(MetricKey metricKey, QualifierSet qualifiers) {
		return new ResolvedKey(metricKey, qualifiers, "global");
	}
}
