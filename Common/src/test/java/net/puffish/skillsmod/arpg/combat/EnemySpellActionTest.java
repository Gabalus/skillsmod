package net.puffish.skillsmod.arpg.combat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemySpellActionTest {
	private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Test
	void warmupReleasesOnceAndKeepsTheCooldown() {
		var action = new EnemySpellAction();
		action.restore(0);
		assertEquals(EnemySpellAction.Event.START, action.tick(TARGET, true));
		assertTrue(action.casting());
		for (int tick = 1; tick < EnemySpellAction.WINDUP; tick++) {
			assertEquals(EnemySpellAction.Event.WINDUP, action.tick(TARGET, true));
		}
		assertEquals(EnemySpellAction.Event.RELEASE, action.tick(TARGET, true));
		assertFalse(action.casting());
		assertEquals(170, action.cooldown());
		for (int tick = 1; tick < 170; tick++) {
			assertEquals(EnemySpellAction.Event.NONE, action.tick(TARGET, true));
		}
		assertEquals(EnemySpellAction.Event.START, action.tick(TARGET, true));
	}

	@Test
	void interruptionOrRetargetAtReleaseCannotProduceASpellOrRefundRecovery() {
		for (boolean retarget : new boolean[]{false, true}) {
			var action = new EnemySpellAction();
			action.restore(0);
			action.tick(TARGET, true);
			for (int i = 1; i < EnemySpellAction.WINDUP; i++) {
				action.tick(TARGET, true);
			}
			assertEquals(EnemySpellAction.Event.CANCEL,
					action.tick(retarget ? UUID.randomUUID() : TARGET, retarget));
			assertEquals(170, action.cooldown());
			assertFalse(action.casting());
			assertEquals(EnemySpellAction.Event.NONE, action.tick(TARGET, true));
		}
	}

	@Test
	void restoreDropsPartialCastsButRetainsAndBoundsTheirSavedCooldown() {
		var action = new EnemySpellAction();
		action.restore(0);
		action.tick(TARGET, true);
		var restored = new EnemySpellAction();
		restored.restore(action.cooldown());
		assertFalse(restored.casting());
		assertEquals(EnemySpellAction.Event.NONE, restored.tick(TARGET, true));
		assertEquals(199, restored.cooldown());
		restored.restore(Integer.MAX_VALUE);
		assertEquals(EnemySpellAction.COOLDOWN, restored.cooldown());
		restored.restore(-1);
		assertEquals(0, restored.cooldown());
		assertEquals(EnemySpellAction.Event.NONE, restored.tick(null, true));
		assertEquals(EnemySpellAction.Event.NONE, restored.tick(TARGET, false));
	}
}
