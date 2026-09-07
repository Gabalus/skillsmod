package net.puffish.skillsmod.arpg.stat;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ArpgStatSnapshot {
	private final Map<ArpgStat, ArpgStatValue> values;

	ArpgStatSnapshot(Map<ArpgStat, ArpgStatValue> values) {
		this.values = Collections.unmodifiableMap(new EnumMap<>(values));
	}

	public ArpgStatValue get(ArpgStat stat) {
		return values.getOrDefault(stat, ArpgStatValue.ZERO);
	}

	public double apply(ArpgStat stat, double baseValue) {
		return get(stat).applyTo(baseValue);
	}

	public Map<ArpgStat, ArpgStatValue> asMap() {
		return values;
	}
}
