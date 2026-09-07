package net.puffish.skillsmod.arpg.stat;

public record ArpgStatValue(double flat, double increased, double multiplier) {
	public static final ArpgStatValue ZERO = new ArpgStatValue(0.0, 0.0, 1.0);

	public double applyTo(double baseValue) {
		return (baseValue + flat) * (1.0 + increased) * multiplier;
	}
}
