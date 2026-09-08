package net.puffish.skillsmod.arpg.combat;

public record DamageConversion(DamageType from, DamageType to, double fraction) {
	public DamageConversion {
		if (from == null || to == null || from.ordinal() >= to.ordinal()) {
			throw new IllegalArgumentException("Conversion must follow the damage-type order (no cycles)");
		}
		if (!Double.isFinite(fraction) || fraction <= 0 || fraction > 1) {
			throw new IllegalArgumentException("Conversion fraction must be in (0, 1]");
		}
	}
}
