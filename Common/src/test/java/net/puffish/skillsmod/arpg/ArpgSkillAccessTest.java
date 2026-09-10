package net.puffish.skillsmod.arpg;

import net.puffish.skillsmod.arpg.character.ArpgCharacter;
import net.puffish.skillsmod.arpg.combat.DamageType;
import net.puffish.skillsmod.arpg.data.ArpgContent;
import net.puffish.skillsmod.arpg.skill.ArpgSkillAccess;
import net.puffish.skillsmod.arpg.skill.SkillTag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArpgSkillAccessTest {
	private ArpgContent.Skill skill(String id, int level, String discipline) {
		return new ArpgContent.Skill(
				id,
				"Test Skill",
				"irons",
				Set.of(SkillTag.SPELL, SkillTag.FIRE, SkillTag.HIT),
				DamageType.FIRE,
				1.0,
				0.0,
				20,
				16.0,
				level,
				discipline,
				"any",
				"hit"
		);
	}

	@Test
	void providerLevelAndDisciplineAreServerAuthoritative() {
		var character = new ArpgCharacter();
		var generic = skill("irons_spellbooks:test", 5, "");
		assertEquals(ArpgSkillAccess.Denial.NO_PRIMARY,
				ArpgSkillAccess.check(character, generic, "irons").denial());

		character.choosePrimary("warrior");
		assertEquals(ArpgSkillAccess.Denial.LEVEL,
				ArpgSkillAccess.check(character, generic, "irons").denial());
		assertEquals(ArpgSkillAccess.Denial.WRONG_PROVIDER,
				ArpgSkillAccess.check(character, generic, "weapon").denial());

		character.gainExperience(100_000_000L);
		assertTrue(ArpgSkillAccess.check(character, generic, "irons").allowed());

		var arcanist = skill("irons_spellbooks:arcane_test", 1, "arcanist");
		assertEquals(ArpgSkillAccess.Denial.DISCIPLINE,
				ArpgSkillAccess.check(character, arcanist, "irons").denial());
		character.chooseSecondary("arcanist");
		assertTrue(ArpgSkillAccess.check(character, arcanist, "irons").allowed());
	}
}
