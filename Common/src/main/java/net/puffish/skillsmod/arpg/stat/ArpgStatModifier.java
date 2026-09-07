package net.puffish.skillsmod.arpg.stat;

public record ArpgStatModifier(ArpgStat stat, ArpgModifierOperation operation, double value) {
	public ArpgStatModifier {
		if (stat == null) {
			throw new IllegalArgumentException("stat cannot be null");
		}
		if (operation == null) {
			throw new IllegalArgumentException("operation cannot be null");
		}
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException("value must be finite");
		}
		if (operation != ArpgModifierOperation.FLAT && value < 0.0) {
			throw new IllegalArgumentException("percentage modifiers cannot be negative");
		}
		if (operation == ArpgModifierOperation.LESS && value >= 1.0) {
			throw new IllegalArgumentException("less modifiers must be lower than 1.0");
		}
	}
}
