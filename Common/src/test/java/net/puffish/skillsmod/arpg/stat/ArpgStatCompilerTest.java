package net.puffish.skillsmod.arpg.stat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ArpgStatCompilerTest {
	@Test
	public void compilesFlatIncreasedMoreAndLessInCorrectOrder() {
		var snapshot = ArpgStatCompiler.compile(List.of(
				new ArpgStatModifier(ArpgStat.FIRE_DAMAGE, ArpgModifierOperation.FLAT, 20.0),
				new ArpgStatModifier(ArpgStat.FIRE_DAMAGE, ArpgModifierOperation.INCREASED, 0.50),
				new ArpgStatModifier(ArpgStat.FIRE_DAMAGE, ArpgModifierOperation.MORE, 0.25),
				new ArpgStatModifier(ArpgStat.FIRE_DAMAGE, ArpgModifierOperation.LESS, 0.10)
		));

		assertEquals(202.5, snapshot.apply(ArpgStat.FIRE_DAMAGE, 100.0), 0.000001);
	}

	@Test
	public void increasedAndReducedShareAnAdditiveBucket() {
		var snapshot = ArpgStatCompiler.compile(List.of(
				new ArpgStatModifier(ArpgStat.ARMOR, ArpgModifierOperation.INCREASED, 0.80),
				new ArpgStatModifier(ArpgStat.ARMOR, ArpgModifierOperation.REDUCED, 0.30)
		));

		assertEquals(150.0, snapshot.apply(ArpgStat.ARMOR, 100.0), 0.000001);
	}

	@Test
	public void independentMoreModifiersMultiply() {
		var snapshot = ArpgStatCompiler.compile(List.of(
				new ArpgStatModifier(ArpgStat.SPELL_DAMAGE, ArpgModifierOperation.MORE, 0.20),
				new ArpgStatModifier(ArpgStat.SPELL_DAMAGE, ArpgModifierOperation.MORE, 0.30)
		));

		assertEquals(156.0, snapshot.apply(ArpgStat.SPELL_DAMAGE, 100.0), 0.000001);
	}

	@Test
	public void unmodifiedStatsKeepTheirBaseValue() {
		var snapshot = ArpgStatCompiler.compile(List.of());

		assertEquals(100.0, snapshot.apply(ArpgStat.MAXIMUM_LIFE, 100.0), 0.000001);
	}

	@Test
	public void rejectsInvalidLessModifier() {
		assertThrows(IllegalArgumentException.class, () -> new ArpgStatModifier(
				ArpgStat.ARMOR,
				ArpgModifierOperation.LESS,
				1.0
		));
	}
}
