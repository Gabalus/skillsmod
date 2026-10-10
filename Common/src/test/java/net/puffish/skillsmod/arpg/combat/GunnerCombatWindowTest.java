package net.puffish.skillsmod.arpg.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GunnerCombatWindowTest {
	@Test
	void activeReloadHasDeterministicSweetSpot() {
		var started = GunnerCombatWindow.idle(100L).startReload(100L, 40);
		assertTrue(started.applied());
		assertEquals(122L, started.window().activeReloadStartTick());
		assertEquals(128L, started.window().activeReloadEndTick());
		assertEquals(
				GunnerCombatWindow.ActiveReloadOutcome.PERFECT,
				started.window().attemptActiveReload(125L).outcome()
		);
	}

	@Test
	void mistimedActiveReloadJamsAndLocksWeapon() {
		var window = GunnerCombatWindow.idle(200L).startReload(200L, 40).window();
		var attempt = window.attemptActiveReload(210L);

		assertEquals(GunnerCombatWindow.ActiveReloadOutcome.JAMMED, attempt.outcome());
		assertFalse(attempt.window().reloading());
		assertTrue(attempt.window().locked(225L));
		assertFalse(attempt.window().locked(226L));
		assertFalse(attempt.window().startReload(220L, 40).applied());
	}

	@Test
	void untouchedReloadCompletesAutomatically() {
		var window = GunnerCombatWindow.idle(300L).startReload(300L, 40).window();
		assertFalse(window.automaticReloadReady(339L));
		assertTrue(window.automaticReloadReady(340L));
		assertFalse(window.finishReload().reloading());
	}

	@Test
	void overheatRequiresCoolingBelowReleaseThreshold() {
		var window = GunnerCombatWindow.idle(400L)
				.observeHeat(ResourcePool.full(100.0));
		assertTrue(window.overheated());
		assertTrue(window.startReload(400L, 40).applied());
		assertTrue(window.observeHeat(new ResourcePool(61.0, 100.0)).overheated());
		assertFalse(window.observeHeat(new ResourcePool(60.0, 100.0)).overheated());
	}

	@Test
	void invalidOperationsLeaveWindowIdentityStable() {
		var idle = GunnerCombatWindow.idle(500L);
		assertSame(idle, idle.attemptActiveReload(500L).window());
		var active = idle.startReload(500L, 40).window();
		assertFalse(active.startReload(501L, 40).applied());
		assertSame(active, active.startReload(501L, 40).window());
	}
}
