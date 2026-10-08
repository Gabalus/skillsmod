package net.puffish.skillsmod.arpg.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MartialCombatWindowTest {
	private static final MartialCombatSemantics.GuardInput HIT =
			new MartialCombatSemantics.GuardInput(10.0, 10.0, 0.75, 1.0);

	@Test
	void newlyRaisedGuardHasABoundedPerfectWindow() {
		var window = MartialCombatWindow.idle(100L).observeGuard(101L, true);
		assertEquals(MartialCombatSemantics.GuardTiming.PERFECT, window.timing(104L, true));
		assertEquals(MartialCombatSemantics.GuardTiming.HELD, window.timing(105L, true));
		assertEquals(MartialCombatSemantics.GuardTiming.MISSED, window.timing(103L, false));
	}

	@Test
	void loweringAndRaisingGuardStartsANewWindow() {
		var original = MartialCombatWindow.idle(20L).observeGuard(21L, true);
		var lowered = original.observeGuard(30L, false);
		var raised = lowered.observeGuard(40L, true);

		assertEquals(MartialCombatSemantics.GuardTiming.HELD, original.timing(30L, true));
		assertEquals(MartialCombatSemantics.GuardTiming.PERFECT, raised.timing(40L, true));
	}

	@Test
	void perfectParryCreatesOneConsumableCounter() {
		var guard = MartialCombatSemantics.resolveGuard(
				CombatState.fresh(CombatPillar.MARTIAL),
				MartialCombatSemantics.GuardTiming.PERFECT,
				HIT
		);
		var window = MartialCombatWindow.idle(200L).afterGuard(201L, guard);
		assertTrue(window.inCombat(301L));
		assertTrue(window.counterReady(213L));
		assertFalse(window.counterReady(214L));

		var used = window.consumeCounter(210L);
		assertTrue(used.applied());
		assertFalse(used.window().counterReady(210L));
		var rejected = used.window().consumeCounter(211L);
		assertFalse(rejected.applied());
		assertSame(used.window(), rejected.window());
	}

	@Test
	void anotherResolvedHitClearsAnUnspentCounter() {
		var perfect = MartialCombatSemantics.resolveGuard(
				CombatState.fresh(CombatPillar.MARTIAL),
				MartialCombatSemantics.GuardTiming.PERFECT,
				HIT
		);
		var missed = MartialCombatSemantics.resolveGuard(
				perfect.state(),
				MartialCombatSemantics.GuardTiming.MISSED,
				HIT
		);
		var window = MartialCombatWindow.idle(300L)
				.afterGuard(301L, perfect)
				.afterGuard(302L, missed);

		assertFalse(window.counterReady(302L));
	}

	@Test
	void recoveryIsThrottledAndReportsElapsedSeconds() {
		var window = MartialCombatWindow.idle(100L);
		assertFalse(window.recoveryDue(109L));
		assertTrue(window.recoveryDue(110L));
		assertEquals(0.5, window.recoverySeconds(110L), 0.000001);
		assertFalse(window.markRecovery(110L).recoveryDue(119L));
	}
}
