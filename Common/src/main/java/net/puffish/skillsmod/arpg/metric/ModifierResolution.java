package net.puffish.skillsmod.arpg.metric;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record ModifierResolution(double value, Trace trace) {
	public record TraceEntry(
			String sourceKey,
			ModifierOperation operation,
			double value,
			StackGroup stackGroup,
			int priority,
			boolean applied,
			String reason
	) {
	}

	public record Trace(
			ResolvedKey resolvedKey,
			double authoredBase,
			double resolvedBase,
			String winningSetSource,
			double flat,
			double increased,
			double reduced,
			double additiveFactor,
			Map<StackGroup, Double> sourceMoreFactors,
			double sourceMoreProductBeforeCap,
			double sourceMoreProduct,
			double rawValue,
			double finalValue,
			List<TraceEntry> entries
	) {
		public Trace {
			if (resolvedKey == null) {
				throw new IllegalArgumentException("resolvedKey cannot be null");
			}
			var factors = new EnumMap<StackGroup, Double>(StackGroup.class);
			factors.putAll(sourceMoreFactors);
			sourceMoreFactors = Collections.unmodifiableMap(factors);
			entries = List.copyOf(entries);
		}
	}
}
