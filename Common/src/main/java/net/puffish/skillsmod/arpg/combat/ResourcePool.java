package net.puffish.skillsmod.arpg.combat;

/** Immutable bounded meter. Costs are atomic; gains and pressure changes clamp to the legal range. */
public record ResourcePool(double current, double maximum) {
	private static final double EPSILON = 0.000001;

	public ResourcePool {
		if (!Double.isFinite(current) || !Double.isFinite(maximum) || maximum <= 0.0
				|| current < 0.0 || current > maximum) {
			throw new IllegalArgumentException("Resource pool must be finite and bounded by a positive maximum");
		}
	}

	public static ResourcePool empty(double maximum) {
		return new ResourcePool(0.0, maximum);
	}

	public static ResourcePool full(double maximum) {
		return new ResourcePool(maximum, maximum);
	}

	public double fraction() {
		return current / maximum;
	}

	public boolean isEmpty() {
		return current <= EPSILON;
	}

	public boolean isFull() {
		return maximum - current <= EPSILON;
	}

	public boolean canSpend(double amount) {
		validateAmount(amount);
		return current + EPSILON >= amount;
	}

	public Spend spend(double amount) {
		validateAmount(amount);
		if (!canSpend(amount)) {
			return new Spend(false, this, 0.0);
		}
		return new Spend(true, new ResourcePool(Math.max(0.0, current - amount), maximum), amount);
	}

	public ResourcePool gain(double amount) {
		validateAmount(amount);
		return new ResourcePool(Math.min(maximum, current + amount), maximum);
	}

	public ResourcePool reduce(double amount) {
		validateAmount(amount);
		return new ResourcePool(Math.max(0.0, current - amount), maximum);
	}

	public ResourcePool withCurrent(double value) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException("Resource value must be finite");
		}
		return new ResourcePool(Math.max(0.0, Math.min(maximum, value)), maximum);
	}

	public ResourcePool withMaximum(double value, boolean preserveFraction) {
		if (!Double.isFinite(value) || value <= 0.0) {
			throw new IllegalArgumentException("Resource maximum must be finite and positive");
		}
		double nextCurrent = preserveFraction ? fraction() * value : Math.min(current, value);
		return new ResourcePool(nextCurrent, value);
	}

	private static void validateAmount(double amount) {
		if (!Double.isFinite(amount) || amount < 0.0) {
			throw new IllegalArgumentException("Resource change must be finite and nonnegative");
		}
	}

	public record Spend(boolean applied, ResourcePool pool, double amount) {
	}
}
