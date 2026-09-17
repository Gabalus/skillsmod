package net.puffish.skillsmod.arpg.metric;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ModifierResolver {
	public static final double SOURCE_MORE_PRODUCT_MINIMUM = 0.25;
	public static final double SOURCE_MORE_PRODUCT_MAXIMUM = 4.00;

	private static final List<StackGroup> SOURCE_MORE_GROUPS = List.of(
			StackGroup.CORE,
			StackGroup.IDENTITY,
			StackGroup.CONDITIONAL,
			StackGroup.PARTY
	);

	private static final Comparator<Modifier> DETERMINISTIC_ORDER = Comparator
			.comparingInt(Modifier::priority).reversed()
			.thenComparing(Modifier::sourceKey)
			.thenComparing(modifier -> modifier.operation().name())
			.thenComparing(modifier -> modifier.stackGroup().name())
			.thenComparingDouble(Modifier::value)
			.thenComparing(modifier -> modifier.qualifierSet().toString())
			.thenComparing(modifier -> modifier.conditionExpression().describe());

	private ModifierResolver() {
	}

	public static ModifierResolution resolve(
			MetricDefinition definition,
			double authoredBase,
			ResolvedKey key,
			Collection<Modifier> modifiers,
			ResolveContext context
	) {
		validateRequest(definition, authoredBase, key, modifiers, context);

		var matching = modifiers.stream()
				.filter(modifier -> modifier.matches(key, context))
				.sorted(DETERMINISTIC_ORDER)
				.toList();
		var winningSet = matching.stream()
				.filter(modifier -> modifier.operation() == ModifierOperation.SET)
				.findFirst()
				.orElse(null);

		double resolvedBase = winningSet == null ? authoredBase : winningSet.value();
		double flat = 0.0;
		double increased = 0.0;
		double reduced = 0.0;
		Map<StackGroup, Double> more = new EnumMap<>(StackGroup.class);
		Map<StackGroup, Double> less = new EnumMap<>(StackGroup.class);
		var entries = new ArrayList<ModifierResolution.TraceEntry>();

		for (var modifier : matching) {
			if (modifier.operation() == ModifierOperation.SET) {
				boolean applied = modifier == winningSet;
				entries.add(entry(modifier, applied, applied ? "winning SET override" : "lower-priority SET override"));
				continue;
			}

			switch (modifier.operation()) {
				case FLAT -> {
					flat += modifier.value();
					entries.add(entry(modifier, true, "source FLAT"));
				}
				case INCREASED -> {
					increased += modifier.value();
					entries.add(entry(modifier, true, "source additive INCREASED"));
				}
				case REDUCED -> {
					reduced += modifier.value();
					entries.add(entry(modifier, true, "source additive REDUCED"));
				}
				case MORE, LESS -> {
					if (!modifier.stackGroup().isSourceMoreGroup()) {
						entries.add(entry(modifier, false, "resolved in a later non-source phase"));
						continue;
					}
					var accumulator = modifier.operation() == ModifierOperation.MORE ? more : less;
					accumulator.merge(modifier.stackGroup(), modifier.value(), Double::sum);
					entries.add(entry(modifier, true, "source grouped " + modifier.operation()));
				}
				case SET -> throw new IllegalStateException("SET handled before arithmetic switch");
				default -> throw new IllegalStateException("Unhandled operation: " + modifier.operation());
			}
		}

		double additiveFactor = Math.max(definition.additiveFactorFloor(), 1.0 + increased - reduced);
		Map<StackGroup, Double> sourceMoreFactors = new EnumMap<>(StackGroup.class);
		double sourceMoreProductBeforeCap = 1.0;
		for (var group : SOURCE_MORE_GROUPS) {
			double factor = group.clampSourceFactor(1.0 + more.getOrDefault(group, 0.0) - less.getOrDefault(group, 0.0));
			sourceMoreFactors.put(group, factor);
			sourceMoreProductBeforeCap *= factor;
		}
		double sourceMoreProduct = clamp(sourceMoreProductBeforeCap,
				SOURCE_MORE_PRODUCT_MINIMUM, SOURCE_MORE_PRODUCT_MAXIMUM);
		double rawValue = (resolvedBase + flat) * additiveFactor * sourceMoreProduct;
		double finalValue = definition.clampFinal(rawValue);

		var trace = new ModifierResolution.Trace(
				key,
				authoredBase,
				resolvedBase,
				winningSet == null ? null : winningSet.sourceKey(),
				flat,
				increased,
				reduced,
				additiveFactor,
				sourceMoreFactors,
				sourceMoreProductBeforeCap,
				sourceMoreProduct,
				rawValue,
				finalValue,
				entries
		);
		return new ModifierResolution(finalValue, trace);
	}

	private static void validateRequest(
			MetricDefinition definition,
			double authoredBase,
			ResolvedKey key,
			Collection<Modifier> modifiers,
			ResolveContext context
	) {
		if (definition == null) {
			throw new IllegalArgumentException("definition cannot be null");
		}
		if (!Double.isFinite(authoredBase)) {
			throw new IllegalArgumentException("authoredBase must be finite");
		}
		if (key == null) {
			throw new IllegalArgumentException("key cannot be null");
		}
		if (modifiers == null) {
			throw new IllegalArgumentException("modifiers cannot be null");
		}
		if (context == null) {
			throw new IllegalArgumentException("context cannot be null");
		}
		if (!definition.metricKey().equals(key.metricKey())) {
			throw new IllegalArgumentException("metric definition does not match resolved key");
		}
		if (!context.scope().equals(key.scope())) {
			throw new IllegalArgumentException("resolve context scope does not match resolved key scope");
		}
	}

	private static ModifierResolution.TraceEntry entry(Modifier modifier, boolean applied, String reason) {
		return new ModifierResolution.TraceEntry(
				modifier.sourceKey(),
				modifier.operation(),
				modifier.value(),
				modifier.stackGroup(),
				modifier.priority(),
				applied,
				reason
		);
	}

	private static double clamp(double value, double minimum, double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
