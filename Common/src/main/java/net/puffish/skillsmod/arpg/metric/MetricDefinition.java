package net.puffish.skillsmod.arpg.metric;

public record MetricDefinition(
		MetricKey metricKey,
		double additiveFactorFloor,
		double minimumValue,
		double maximumValue
) {
	public static final double DEFAULT_ADDITIVE_FACTOR_FLOOR = 0.20;

	public MetricDefinition {
		if (metricKey == null) {
			throw new IllegalArgumentException("metricKey cannot be null");
		}
		if (!Double.isFinite(additiveFactorFloor) || additiveFactorFloor < DEFAULT_ADDITIVE_FACTOR_FLOOR) {
			throw new IllegalArgumentException("additiveFactorFloor must be finite and >= 0.20");
		}
		if (Double.isNaN(minimumValue) || Double.isNaN(maximumValue)) {
			throw new IllegalArgumentException("metric bounds cannot be NaN");
		}
		if (minimumValue > maximumValue) {
			throw new IllegalArgumentException("minimumValue cannot exceed maximumValue");
		}
	}

	public static MetricDefinition unbounded(MetricKey metricKey) {
		return new MetricDefinition(metricKey, DEFAULT_ADDITIVE_FACTOR_FLOOR,
				Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
	}

	public static MetricDefinition bounded(MetricKey metricKey, double minimumValue, double maximumValue) {
		return new MetricDefinition(metricKey, DEFAULT_ADDITIVE_FACTOR_FLOOR, minimumValue, maximumValue);
	}

	public double clampFinal(double value) {
		return Math.max(minimumValue, Math.min(maximumValue, value));
	}
}
