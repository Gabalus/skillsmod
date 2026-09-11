package net.puffish.skillsmod.arpg.character;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArpgTrialTest {
	@Test
	void trialsRequirePrimaryLevelAndOrderAndGrantTwoPointsEach() {
		var character = new ArpgCharacter();

		levelTo(character, 30);
		assertThrows(IllegalStateException.class, () -> character.completeTrial(1));

		character.choosePrimary("warrior");
		assertEquals(1, character.nextTrial());
		assertTrue(character.completeTrial(1));
		assertFalse(character.completeTrial(1));
		assertEquals(2, character.ascendancyPoints());
		character.chooseAscendancy("juggernaut");

		assertThrows(IllegalStateException.class, () -> character.completeTrial(2));
		levelTo(character, 50);
		assertTrue(character.completeTrial(2));
		assertEquals(4, character.ascendancyPoints());

		levelTo(character, 70);
		assertTrue(character.completeTrial(3));
		assertEquals(6, character.ascendancyPoints());

		levelTo(character, 90);
		assertTrue(character.completeTrial(4));
		assertEquals(8, character.ascendancyPoints());
		assertEquals(0, character.nextTrial());

		assertTrue(character.completeMilestone("trial_debug"));
		assertEquals(8, character.ascendancyPoints());
	}

	@Test
	void laterTrialsCannotBeCompletedOutOfOrder() {
		var character = new ArpgCharacter();
		character.choosePrimary("warrior");
		levelTo(character, 100);

		assertThrows(IllegalStateException.class, () -> character.completeTrial(2));
		assertThrows(IllegalArgumentException.class, () -> character.completeTrial(0));
		assertThrows(IllegalArgumentException.class, () -> character.completeTrial(5));

		assertTrue(character.completeTrial(1));
		assertTrue(character.completeTrial(2));
		assertTrue(character.completeTrial(3));
		assertTrue(character.completeTrial(4));
	}

	private static void levelTo(ArpgCharacter character, int targetLevel) {
		while (character.level() < targetLevel) {
			character.gainExperience(ArpgCharacter.experienceForLevel(character.level()));
		}
		assertEquals(targetLevel, character.level());
	}
}
