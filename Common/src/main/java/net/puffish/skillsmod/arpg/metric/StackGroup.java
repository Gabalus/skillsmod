package net.puffish.skillsmod.arpg.metric;

public enum StackGroup {
	BASE_SET(false, Double.NaN, Double.NaN),
	SOURCE_ADDITIVE(false, Double.NaN, Double.NaN),
	CORE(true, 0.25, 2.00),
	IDENTITY(true, 0.25, 2.00),
	CONDITIONAL(true, 0.25, 1.75),
	PARTY(true, 0.50, 1.50),
	TARGET_STATE(false, 0.50, 1.75),
	ENCOUNTER_EXPLOIT(false, Double.NaN, 1.60),
	RATING_CURVE(false, Double.NaN, Double.NaN),
	UNIQUE_STATE(false, Double.NaN, Double.NaN);

	private final boolean sourceMoreGroup;
	private final double minimumFactor;
	private final double maximumFactor;

	StackGroup(boolean sourceMoreGroup, double minimumFactor, double maximumFactor) {
		this.sourceMoreGroup = sourceMoreGroup;
		this.minimumFactor = minimumFactor;
		this.maximumFactor = maximumFactor;
	}

	public boolean isSourceMoreGroup() {
		return sourceMoreGroup;
	}

	public double clampSourceFactor(double value) {
		if (!sourceMoreGroup) {
			throw new IllegalStateException(name() + " is not a source MORE/LESS group");
		}
		return Math.max(minimumFactor, Math.min(maximumFactor, value));
	}
}
