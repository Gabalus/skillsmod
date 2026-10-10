package net.puffish.skillsmod.arpg.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ProviderStaminaSnapshotTest {
	@Test
	void providerScaleAndAttributeChangesPreserveTheDisplayedFraction() {
		var state = CombatState.fresh(CombatPillar.MARTIAL)
				.gain(CombatResource.POSTURE, 25.0).gain(CombatResource.MOMENTUM, 12.0);
		var mirrored = ProviderStaminaSnapshot.apply(state, 3.0, 15.0);
		assertEquals(20.0, mirrored.require(CombatResource.STAMINA).current());
		assertEquals(100.0, mirrored.require(CombatResource.STAMINA).maximum());
		assertEquals(state.require(CombatResource.POSTURE), mirrored.require(CombatResource.POSTURE));
		assertEquals(state.require(CombatResource.MOMENTUM), mirrored.require(CombatResource.MOMENTUM));
		assertSame(mirrored, ProviderStaminaSnapshot.apply(mirrored, 6.0, 30.0));
	}

	@Test
	void exhaustionAndOverflowStayBounded() {
		var state = CombatState.fresh(CombatPillar.MARTIAL);
		assertEquals(0.0, ProviderStaminaSnapshot.apply(state, -1.0, 15.0)
				.require(CombatResource.STAMINA).current());
		assertSame(state, ProviderStaminaSnapshot.apply(state, 18.0, 15.0));
		assertEquals(0.0, ProviderStaminaSnapshot.apply(state, 0.0, 15.0)
				.require(CombatResource.STAMINA).current());
	}

	@Test
	void invalidSamplesAndOtherPillarsNeverEraseExistingMeters() {
		var state = CombatState.fresh(CombatPillar.MARTIAL).reduce(CombatResource.STAMINA, 40.0);
		for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
			assertSame(state, ProviderStaminaSnapshot.apply(state, invalid, 15.0));
			assertSame(state, ProviderStaminaSnapshot.apply(state, 3.0, invalid));
		}
		assertSame(state, ProviderStaminaSnapshot.apply(state, 3.0, 0.0));
		assertSame(state, ProviderStaminaSnapshot.apply(state, 3.0, -15.0));
		var arcane = CombatState.fresh(CombatPillar.ARCANE);
		assertSame(arcane, ProviderStaminaSnapshot.apply(arcane, 3.0, 15.0));
	}
}
