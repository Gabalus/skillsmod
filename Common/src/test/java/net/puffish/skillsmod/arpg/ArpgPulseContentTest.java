package net.puffish.skillsmod.arpg;

import net.puffish.skillsmod.arpg.character.ArpgCharacter;
import net.puffish.skillsmod.arpg.data.ArpgContent;
import net.puffish.skillsmod.arpg.rule.ArpgRuleEngine;
import net.puffish.skillsmod.arpg.skill.ArpgSkillAccess;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatCompiler;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArpgPulseContentTest {
	private ArpgContent content() throws Exception {
		try (var reader = new InputStreamReader(getClass().getResourceAsStream(
				"/data/puffish_skills/arpg/catalog.json"), StandardCharsets.UTF_8)) {
			return ArpgContent.read(reader);
		}
	}

	@Test
	void newSpellsRequireTheAuthoredLevelAndDiscipline() throws Exception {
		var catalog = content();
		for (String name : Set.of("storm_pulse", "rime_pulse")) {
			var skill = catalog.skills().get("puffish_skills:" + name);
			var character = new ArpgCharacter();
			assertEquals(ArpgSkillAccess.Denial.NO_PRIMARY,
					ArpgSkillAccess.check(character, skill, "irons").denial());
			character.choosePrimary(skill.discipline());
			assertEquals(ArpgSkillAccess.Denial.LEVEL,
					ArpgSkillAccess.check(character, skill, "irons").denial());
			while (character.level() < skill.level()) {
				character.gainExperience(1);
			}
			assertTrue(ArpgSkillAccess.check(character, skill, "irons").allowed());
			assertEquals(0.0, skill.cost());
		}
	}

	@Test
	void secondaryDisciplineCanUseTheSpellWithoutChangingItsProvider() throws Exception {
		var skill = content().skills().get("puffish_skills:rime_pulse");
		var character = new ArpgCharacter();
		character.choosePrimary("warrior");
		character.gainExperience(100_000_000L);
		assertEquals(ArpgSkillAccess.Denial.DISCIPLINE,
				ArpgSkillAccess.check(character, skill, "irons").denial());
		character.chooseSecondary("shaman");
		assertTrue(ArpgSkillAccess.check(character, skill, "irons").allowed());
		assertEquals(ArpgSkillAccess.Denial.WRONG_PROVIDER,
				ArpgSkillAccess.check(character, skill, "weapon").denial());
	}

	@Test
	void specializationRulesAffectOnlyTheirSpellAndAllThreeDamageBranches() throws Exception {
		var catalog = content();
		var active = Set.of("specialization/puffish_skills_storm_pulse_0",
				"specialization/puffish_skills_storm_pulse_8", "specialization/puffish_skills_storm_pulse_16");
		var storm = ArpgRuleEngine.evaluate(catalog, active, ArpgRuleEngine.Context.of(
				ArpgRuleEngine.Event.HIT, "puffish_skills:storm_pulse", Set.of("spell", "area", "lightning", "hit")));
		var snapshot = ArpgStatCompiler.compile(storm.modifiers());
		assertEquals(8.0 * 1.04 * 1.04 * 1.04, snapshot.apply(ArpgStat.AREA_DAMAGE,
				snapshot.apply(ArpgStat.LIGHTNING_DAMAGE, snapshot.apply(ArpgStat.SPELL_DAMAGE, 8.0))), 0.000001);
		var frost = ArpgRuleEngine.evaluate(catalog, active, ArpgRuleEngine.Context.of(
				ArpgRuleEngine.Event.HIT, "puffish_skills:rime_pulse", Set.of("spell", "area", "cold", "hit")));
		assertTrue(frost.modifiers().isEmpty());
	}
}
