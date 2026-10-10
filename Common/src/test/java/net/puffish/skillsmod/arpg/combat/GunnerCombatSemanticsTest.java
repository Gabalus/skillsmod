package net.puffish.skillsmod.arpg.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GunnerCombatSemanticsTest {
	@Test
	void firingAtomicallySpendsAmmunitionAndBuildsHeat() {
		var result = GunnerCombatSemantics.tryFire(
				CombatState.fresh(CombatPillar.GUNNER),
				new GunnerCombatSemantics.ShotInput(1.0, 18.0)
		);

		assertEquals(GunnerCombatSemantics.FireOutcome.FIRED, result.outcome());
		assertEquals(11.0, result.state().require(CombatResource.AMMUNITION).current(), 0.000001);
		assertEquals(18.0, result.state().require(CombatResource.HEAT).current(), 0.000001);
		assertFalse(result.overheated());
	}

	@Test
	void emptyAndOverheatedWeaponsRejectWithoutMutation() {
		var empty = CombatState.fresh(CombatPillar.GUNNER)
				.withPool(CombatResource.AMMUNITION, ResourcePool.empty(12.0));
		var emptyResult = GunnerCombatSemantics.tryFire(
				empty, new GunnerCombatSemantics.ShotInput(1.0, 10.0));
		assertEquals(GunnerCombatSemantics.FireOutcome.EMPTY, emptyResult.outcome());
		assertSame(empty, emptyResult.state());

		var hot = CombatState.fresh(CombatPillar.GUNNER)
				.withPool(CombatResource.HEAT, ResourcePool.full(100.0));
		var hotResult = GunnerCombatSemantics.tryFire(
				hot, new GunnerCombatSemantics.ShotInput(1.0, 10.0));
		assertEquals(GunnerCombatSemantics.FireOutcome.OVERHEATED, hotResult.outcome());
		assertSame(hot, hotResult.state());
	}

	@Test
	void perfectReloadRewardsTimingAndVentsMoreHeat() {
		var spent = CombatState.fresh(CombatPillar.GUNNER)
				.reduce(CombatResource.AMMUNITION, 8.0)
				.gain(CombatResource.HEAT, 70.0);
		var standard = GunnerCombatSemantics.reload(
				spent, GunnerCombatSemantics.ReloadQuality.STANDARD);
		var perfect = GunnerCombatSemantics.reload(
				spent, GunnerCombatSemantics.ReloadQuality.PERFECT);

		assertEquals(12.0, perfect.require(CombatResource.AMMUNITION).current(), 0.000001);
		assertEquals(50.0, standard.require(CombatResource.HEAT).current(), 0.000001);
		assertEquals(30.0, perfect.require(CombatResource.HEAT).current(), 0.000001);
		assertEquals(20.0, perfect.require(CombatResource.MOMENTUM).current(), 0.000001);
	}

	@Test
	void aggressionRewardsCloseHitsAndKills() {
		var state = CombatState.fresh(CombatPillar.GUNNER).reduce(CombatResource.AMMUNITION, 4.0);
		var distant = GunnerCombatSemantics.registerHit(state, 10.0, false);
		var close = GunnerCombatSemantics.registerHit(state, 10.0, true);
		var kill = GunnerCombatSemantics.registerKill(close);

		assertEquals(6.0, distant.require(CombatResource.MOMENTUM).current(), 0.000001);
		assertEquals(9.0, close.require(CombatResource.MOMENTUM).current(), 0.000001);
		assertEquals(29.0, kill.require(CombatResource.MOMENTUM).current(), 0.000001);
		assertEquals(10.0, kill.require(CombatResource.AMMUNITION).current(), 0.000001);
		assertTrue(GunnerCombatSemantics.damageMultiplier(kill) > 1.0);
	}

	@Test
	void gunnerRulesRejectAnotherPillar() {
		assertThrows(IllegalArgumentException.class, () -> GunnerCombatSemantics.tryFire(
				CombatState.fresh(CombatPillar.HUNTER),
				new GunnerCombatSemantics.ShotInput(1.0, 10.0)
		));
	}
}
