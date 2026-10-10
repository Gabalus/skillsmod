package net.puffish.skillsmod.arpg.combat;

import net.puffish.skillsmod.arpg.mechanics.ActionContext;
import net.puffish.skillsmod.arpg.metric.QualifierSet;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatStateTest {
	@Test
	void everyPillarCreatesAValidDistinctResourceProfile() {
		for (var pillar : CombatPillar.values()) {
			var state = CombatState.fresh(pillar);
			assertEquals(pillar, state.pillar());
			assertFalse(state.resources().isEmpty());
			assertEquals(0L, state.revision());
		}

		var martial = CombatState.fresh(CombatPillar.MARTIAL);
		assertEquals(100.0, martial.require(CombatResource.STAMINA).current(), 0.000001);
		assertEquals(0.0, martial.require(CombatResource.POSTURE).current(), 0.000001);
		assertThrows(IllegalArgumentException.class, () -> martial.require(CombatResource.MANA));
	}

	@Test
	void builtInDisciplinesSelectTheirCoreCombatGrammar() {
		assertEquals(CombatPillar.MARTIAL, CombatPillar.forDiscipline("warrior"));
		assertEquals(CombatPillar.MARTIAL, CombatPillar.forDiscipline("rogue"));
		assertEquals(CombatPillar.MARTIAL, CombatPillar.forDiscipline("templar"));
		assertEquals(CombatPillar.HUNTER, CombatPillar.forDiscipline("ranger"));
		assertEquals(CombatPillar.ARCANE, CombatPillar.forDiscipline("arcanist"));
		assertEquals(CombatPillar.ARCANE, CombatPillar.forDiscipline("shaman"));
	}

	@Test
	void costsAreAtomicAndRevisionOnlyChangesOnMutation() {
		var state = CombatState.fresh(CombatPillar.MARTIAL);
		var paid = state.spend(CombatResource.STAMINA, 35.0);
		assertTrue(paid.applied());
		assertEquals(65.0, paid.state().require(CombatResource.STAMINA).current(), 0.000001);
		assertEquals(1L, paid.state().revision());

		var rejected = paid.state().spend(CombatResource.STAMINA, 70.0);
		assertFalse(rejected.applied());
		assertSame(paid.state(), rejected.state());
		assertEquals(1L, rejected.state().revision());

		var unchanged = rejected.state().gain(CombatResource.STAMINA, 0.0);
		assertSame(rejected.state(), unchanged);
	}

	@Test
	void immutableSnapshotsFeedActionContextWithoutProviderMutation() {
		var state = CombatState.fresh(CombatPillar.ARCANE)
				.reduce(CombatResource.MANA, 25.0)
				.gain(CombatResource.INSTABILITY, 18.0);
		var context = ActionContext.root(
				"fire_orb",
				"player",
				"player_entity",
				"wand",
				QualifierSet.of("spell", "fire", "orb"),
				state
		);

		assertEquals(Map.of("focus", 0.0, "instability", 18.0, "mana", 75.0), context.resourceState());
		var changed = state.reduce(CombatResource.MANA, 25.0);
		assertNotSame(state, changed);
		assertEquals(75.0, context.resourceState().get("mana"), 0.000001);
		assertThrows(UnsupportedOperationException.class, () -> context.resourceState().put("mana", 0.0));
	}

	@Test
	void resourcePoolsRejectInvalidValuesAndClampLegalChanges() {
		assertThrows(IllegalArgumentException.class, () -> new ResourcePool(Double.NaN, 100.0));
		assertThrows(IllegalArgumentException.class, () -> ResourcePool.full(0.0));
		assertEquals(100.0, ResourcePool.empty(100.0).gain(120.0).current(), 0.000001);
		assertEquals(0.0, ResourcePool.full(100.0).reduce(120.0).current(), 0.000001);
		assertEquals(40.0, new ResourcePool(20.0, 50.0).withMaximum(100.0, true).current(), 0.000001);
	}
}
