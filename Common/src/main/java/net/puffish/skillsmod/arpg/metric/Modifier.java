package net.puffish.skillsmod.arpg.metric;

import java.util.Locale;

public record Modifier(
		MetricKey metricKey,
		QualifierSet qualifierSet,
		ConditionExpression conditionExpression,
		ModifierOperation operation,
		double value,
		StackGroup stackGroup,
		String sourceKey,
		int priority
) {
	public Modifier {
		if (metricKey == null) {
			throw new IllegalArgumentException("metricKey cannot be null");
		}
		if (qualifierSet == null) {
			throw new IllegalArgumentException("qualifierSet cannot be null");
		}
		if (conditionExpression == null) {
			throw new IllegalArgumentException("conditionExpression cannot be null");
		}
		if (operation == null) {
			throw new IllegalArgumentException("operation cannot be null");
		}
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException("value must be finite");
		}
		if (stackGroup == null) {
			throw new IllegalArgumentException("stackGroup cannot be null");
		}
		if (sourceKey == null) {
			throw new IllegalArgumentException("sourceKey cannot be null");
		}
		sourceKey = sourceKey.trim().toLowerCase(Locale.ROOT);
		if (sourceKey.isEmpty()) {
			throw new IllegalArgumentException("sourceKey cannot be blank");
		}
		if (operation != ModifierOperation.SET && operation != ModifierOperation.FLAT && value < 0.0) {
			throw new IllegalArgumentException("percentage modifier values cannot be negative");
		}
		validateStackGroup(operation, stackGroup);
	}

	public boolean matches(ResolvedKey key, ResolveContext context) {
		return metricKey.equals(key.metricKey())
				&& qualifierSet.matches(key.qualifiers())
				&& conditionExpression.test(context);
	}

	private static void validateStackGroup(ModifierOperation operation, StackGroup stackGroup) {
		switch (operation) {
			case SET -> require(stackGroup == StackGroup.BASE_SET, "SET requires BASE_SET");
			case FLAT, INCREASED, REDUCED -> require(stackGroup == StackGroup.SOURCE_ADDITIVE,
					operation + " requires SOURCE_ADDITIVE");
			case MORE, LESS -> require(stackGroup.isSourceMoreGroup()
						|| stackGroup == StackGroup.TARGET_STATE
						|| stackGroup == StackGroup.ENCOUNTER_EXPLOIT,
					operation + " requires an explicit multiplier StackGroup");
			default -> throw new IllegalStateException("Unhandled operation: " + operation);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}
}
