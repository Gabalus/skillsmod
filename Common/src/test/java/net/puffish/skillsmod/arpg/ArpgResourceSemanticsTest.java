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
}
