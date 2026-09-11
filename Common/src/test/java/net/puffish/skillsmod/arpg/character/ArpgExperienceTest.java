package net.puffish.skillsmod.arpg.character;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArpgExperienceTest {
	@Test
	void levelThirtyStartsAtExpectedTotalExperience() {
		assertEquals(652_500L, ArpgCharacter.totalExperienceForLevel(30));

		var character = new ArpgCharacter();
		character.setLevel(30);
		assertEquals(30, character.level());
		assertEquals(652_500L, character.experience());
		assertEquals(29, character.passivePoints());
	}

	@Test
	void settingLevelWorksInBothDirectionsAndValidatesRange() {
		var character = new ArpgCharacter();
		character.setLevel(70);
		assertEquals(70, character.level());

		character.setLevel(20);
		assertEquals(20, character.level());
		assertEquals(ArpgCharacter.totalExperienceForLevel(20), character.experience());

		assertThrows(IllegalArgumentException.class, () -> character.setLevel(0));
		assertThrows(IllegalArgumentException.class, () -> character.setLevel(ArpgCharacter.MAX_LEVEL + 1));
	}

	@Test
	void killExperienceScalesVanillaDropsAndCapsBossRewards() {
		assertEquals(0L, ArpgCharacter.killExperience(0));
		assertEquals(500L, ArpgCharacter.killExperience(5));
		assertEquals(5_000L, ArpgCharacter.killExperience(50));
		assertEquals(50_000L, ArpgCharacter.killExperience(12_000));
	}
}
