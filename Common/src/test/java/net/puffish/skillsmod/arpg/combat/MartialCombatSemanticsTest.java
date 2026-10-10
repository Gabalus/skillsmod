package net.puffish.skillsmod.arpg.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MartialCombatSemanticsTest {
	private static final MartialCombatSemantics.GuardInput STANDARD_HIT =
			new MartialCombatSemantics.GuardInput(20.0, 40.0, 0.8, 1.0);

	@Test
	void perfectParryPreventsDamageAndCreatesCounterMomentum() {
		var result = MartialCombatSemantics.resolveGuard(
				CombatState.fresh(CombatPillar.MARTIAL),
				MartialCombatSemantics.GuardTiming.PERFECT,
				STANDARD_HIT
		);

		assertEquals(MartialCombatSemantics.Outcome.PERFECT_PARRY, result.outcome());
		assertEquals(0.0, result.healthDamage(), 0.000001);
		assertEquals(6.0, result.staminaSpent(), 0.000001);
		assertEquals(4.0, result.postureAdded(), 0.000001);
		assertEquals(10.0, result.state().require(CombatResource.MOMENTUM).current(), 0.000001);
		assertEquals(MartialCombatSemantics.PERFECT_COUNTER_WINDOW_TICKS, result.counterWindowTicks());
	}

	@Test
	void heldGuardTradesStaminaAndPostureForMitigation() {
		var result = MartialCombatSemantics.resolveGuard(
				CombatState.fresh(CombatPillar.MARTIAL),
				MartialCombatSemantics.GuardTiming.HELD,
				STANDARD_HIT
		);

		assertEquals(MartialCombatSemantics.Outcome.BLOCKED, result.outcome());
		assertEquals(4.0, result.healthDamage(), 0.000001);
		assertEquals(40.0, result.staminaSpent(), 0.000001);
		assertEquals(26.0, result.state().require(CombatResource.POSTURE).current(), 0.000001);
	}

	@Test
	void unaffordableGuardBreaksInsteadOfPartiallyPayingCost() {
		var exhausted = CombatState.fresh(CombatPillar.MARTIAL)
				.withPool(CombatResource.STAMINA, new ResourcePool(10.0, 100.0));
		var result = MartialCombatSemantics.resolveGuard(
				exhausted,
				MartialCombatSemantics.GuardTiming.HELD,
				STANDARD_HIT
		);

		assertEquals(MartialCombatSemantics.Outcome.GUARD_BROKEN, result.outcome());
		assertEquals(10.0, result.staminaSpent(), 0.000001);
		assertEquals(0.0, result.state().require(CombatResource.STAMINA).current(), 0.000001);
		assertTrue(result.state().require(CombatResource.POSTURE).isFull());
		assertEquals(10.0, result.healthDamage(), 0.000001);
	}

	@Test
	void postureThresholdBreaksEvenWithEnoughStamina() {
		var pressured = CombatState.fresh(CombatPillar.MARTIAL)
				.withPool(CombatResource.POSTURE, new ResourcePool(80.0, 100.0));
		var result = MartialCombatSemantics.resolveGuard(
				pressured,
				MartialCombatSemantics.GuardTiming.HELD,
				STANDARD_HIT
		);

		assertEquals(MartialCombatSemantics.Outcome.GUARD_BROKEN, result.outcome());
		assertEquals(10.0, result.healthDamage(), 0.000001);
	}

	@Test
	void recoveryHasDifferentCombatAndRestingRates() {
		var depleted = CombatState.fresh(CombatPillar.MARTIAL)
				.withPool(CombatResource.STAMINA, new ResourcePool(20.0, 100.0))
				.withPool(CombatResource.POSTURE, new ResourcePool(60.0, 100.0))
				.withPool(CombatResource.MOMENTUM, new ResourcePool(40.0, 100.0));
		var combat = MartialCombatSemantics.recover(depleted, 2.0, true);
		var resting = MartialCombatSemantics.recover(depleted, 2.0, false);

		assertEquals(36.0, combat.require(CombatResource.STAMINA).current(), 0.000001);
		assertEquals(48.0, combat.require(CombatResource.POSTURE).current(), 0.000001);
		assertEquals(34.0, combat.require(CombatResource.MOMENTUM).current(), 0.000001);
		assertEquals(56.0, resting.require(CombatResource.STAMINA).current(), 0.000001);
		assertEquals(16.0, resting.require(CombatResource.POSTURE).current(), 0.000001);
		assertEquals(20.0, resting.require(CombatResource.MOMENTUM).current(), 0.000001);
	}

	@Test
	void martialRulesRejectAnotherPillarState() {
		assertThrows(IllegalArgumentException.class, () -> MartialCombatSemantics.resolveGuard(
				CombatState.fresh(CombatPillar.GUNNER),
				MartialCombatSemantics.GuardTiming.HELD,
				STANDARD_HIT
		));
	}
}
