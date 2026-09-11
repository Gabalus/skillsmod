package net.puffish.skillsmod.arpg.stat;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

public final class ArpgStatCompiler {
	private ArpgStatCompiler() {
	}

	public static ArpgStatSnapshot compile(Collection<ArpgStatModifier> modifiers) {
		Map<ArpgStat, Accumulator> accumulators = new EnumMap<>(ArpgStat.class);

		for (var modifier : modifiers) {
			var accumulator = accumulators.computeIfAbsent(modifier.stat(), ignored -> new Accumulator());
			switch (modifier.operation()) {
				case FLAT -> accumulator.flat += modifier.value();
				case INCREASED -> accumulator.increased += modifier.value();
				case REDUCED -> accumulator.increased -= modifier.value();
				case MORE -> accumulator.multiplier *= 1.0 + modifier.value();
				case LESS -> accumulator.multiplier *= 1.0 - modifier.value();
				default -> throw new IllegalStateException("Unsupported ARPG modifier operation: " + modifier.operation());
			}
		}

		Map<ArpgStat, ArpgStatValue> compiled = new EnumMap<>(ArpgStat.class);
		for (var entry : accumulators.entrySet()) {
			var accumulator = entry.getValue();
			compiled.put(entry.getKey(), new ArpgStatValue(
					accumulator.flat,
					accumulator.increased,
					accumulator.multiplier
			));
		}
		return new ArpgStatSnapshot(compiled);
	}

	private static final class Accumulator {
		private double flat;
		private double increased;
		private double multiplier = 1.0;
	}
}
