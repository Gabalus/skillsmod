package net.puffish.skillsmod.arpg;

import net.puffish.skillsmod.arpg.rule.ArpgResourceSemantics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArpgResourceSemanticsTest {
	@Test
	void lowResourceRequiresARealFractionAtOrBelowHalf() {
		assertTrue(ArpgResourceSemantics.isLowFraction(0.0));
		assertTrue(ArpgResourceSemantics.isLowFraction(0.5));
		assertFalse(ArpgResourceSemantics.isLowFraction(0.5001));
		assertFalse(ArpgResourceSemantics.isLowFraction(-0.1));
		assertFalse(ArpgResourceSemantics.isLowFraction(Double.NaN));
	}

	@Test
	void restorationUsesMaximumResourceAndCannotOverfill() {
		assertEquals(30.0, ArpgResourceSemantics.restoreFromMaximum(20.0, 100.0, 0.1), 0.00001);
		assertEquals(100.0, ArpgResourceSemantics.restoreFromMaximum(95.0, 100.0, 0.1), 0.00001);
		assertEquals(20.0, ArpgResourceSemantics.restoreFromMaximum(20.0, 100.0, -1.0), 0.00001);
		assertEquals(20.0, ArpgResourceSemantics.restoreFromMaximum(20.0, 0.0, 0.1), 0.00001);
	}

	@Test
	void bloodMagicUsesASeparateLifeScaleAndCannotKillCaster() {
		assertEquals(2.0, ArpgResourceSemantics.lifeCostFromMana(20.0), 0.00001);
		assertTrue(ArpgResourceSemantics.canPayLifeCost(3.0, 20.0));
		assertFalse(ArpgResourceSemantics.canPayLifeCost(2.99, 20.0));
		assertEquals(1.0, ArpgResourceSemantics.spendLifeForMana(3.0, 20.0), 0.00001);
		assertEquals(2.99, ArpgResourceSemantics.spendLifeForMana(2.99, 20.0), 0.00001);
		assertFalse(ArpgResourceSemantics.canPayLifeCost(20.0, Double.NaN));
	}

	@Test
	void cooldownTriggerValuesBecomeWholeTicks() {
		assertEquals(10, ArpgResourceSemantics.cooldownReductionTicks(10.0));
		assertEquals(6, ArpgResourceSemantics.cooldownReductionTicks(5.6));
		assertEquals(0, ArpgResourceSemantics.cooldownReductionTicks(0.0));
		assertEquals(0, ArpgResourceSemantics.cooldownReductionTicks(Double.NaN));
	}
}
