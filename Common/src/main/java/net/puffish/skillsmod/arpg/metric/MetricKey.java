package net.puffish.skillsmod.arpg.metric;

import java.util.Locale;

public record MetricKey(String id) {
	public MetricKey {
		if (id == null) {
			throw new IllegalArgumentException("id cannot be null");
		}
		id = id.trim().toLowerCase(Locale.ROOT);
		if (!id.matches("[a-z0-9][a-z0-9_.:-]*")) {
			throw new IllegalArgumentException("invalid metric id: " + id);
		}
	}

	public static MetricKey of(String id) {
		return new MetricKey(id);
	}
}
