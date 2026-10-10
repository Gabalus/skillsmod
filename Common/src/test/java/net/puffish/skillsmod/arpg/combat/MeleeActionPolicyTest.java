package net.puffish.skillsmod.arpg.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MeleeActionPolicyTest {
	private static MeleeActionPolicy.Input ready(String skill) {
		return new MeleeActionPolicy.Input(true, true, true, true, false, true, false, true, skill);
	}

	@Test
	void swordCleaveAndLongswordStanceHaveDistinctAccess() {
		assertEquals("", MeleeActionPolicy.rejection(ready("epicfight:sweeping_edge"), MeleeActionPolicy.Action.INNATE));
		assertFalse(MeleeActionPolicy.rejection(ready("epicfight:sweeping_edge"), MeleeActionPolicy.Action.STANCE).isEmpty());
		assertEquals("", MeleeActionPolicy.rejection(ready("epicfight:liechtenauer"), MeleeActionPolicy.Action.STANCE));
		assertFalse(MeleeActionPolicy.rejection(ready("epicfight:guillotine"), MeleeActionPolicy.Action.INNATE).isEmpty());
	}

	@Test
	void forgedOrStaleWeaponAndBusyStateCannotActivate() {
		var stale = new MeleeActionPolicy.Input(true, true, true, true, false, true, false, false, "epicfight:liechtenauer");
		var recovery = new MeleeActionPolicy.Input(true, true, true, true, false, false, false, true, "epicfight:liechtenauer");
		var holding = new MeleeActionPolicy.Input(true, true, true, true, true, true, false, true, "epicfight:liechtenauer");
		var disabled = new MeleeActionPolicy.Input(true, true, true, true, false, true, true, true, "epicfight:liechtenauer");
		for (var input : java.util.List.of(stale, recovery, holding, disabled)) {
			assertFalse(MeleeActionPolicy.rejection(input, MeleeActionPolicy.Action.STANCE).isEmpty());
		}
	}

	@Test
	void survivalClassPillarAndProviderModeAreRequired() {
		for (int missing = 0; missing < 4; missing++) {
			var input = new MeleeActionPolicy.Input(missing != 0, missing != 1, missing != 2, missing != 3,
					false, true, false, true, "epicfight:liechtenauer");
			assertFalse(MeleeActionPolicy.rejection(input, MeleeActionPolicy.Action.INNATE).isEmpty());
		}
	}
}
