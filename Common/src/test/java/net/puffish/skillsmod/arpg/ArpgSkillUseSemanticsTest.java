package net.puffish.skillsmod.arpg;

import net.puffish.skillsmod.arpg.combat.DamageType;
import net.puffish.skillsmod.arpg.data.ArpgContent;
import net.puffish.skillsmod.arpg.skill.ArpgSkillUseSemantics;
import net.puffish.skillsmod.arpg.skill.SkillTag;
import net.puffish.skillsmod.arpg.stat.ArpgModifierOperation;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatCompiler;
import net.puffish.skillsmod.arpg.stat.ArpgStatModifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArpgSkillUseSemanticsTest {
	private static final ArpgContent.Skill CLEAVE = new ArpgContent.Skill(
			"cleave", "Cleave", "weapon", Set.of(SkillTag.ATTACK, SkillTag.MELEE, SkillTag.AREA),
			DamageType.PHYSICAL, 1.2, 8.0, 40, 5.0, 1, "", "melee", "hit"
	);

	@Test
	void reducedResourceCostUsesCompiledStatArithmetic() {
		var snapshot = ArpgStatCompiler.compile(List.of(
				new ArpgStatModifier(ArpgStat.RESOURCE_COST, ArpgModifierOperation.REDUCED, 0.25)
		));
		assertEquals(6.0, ArpgSkillUseSemantics.resourceCost(CLEAVE, snapshot), 0.00001);
	}

	@Test
	void increasedCooldownRecoveryDividesBaseCooldown() {
		var snapshot = ArpgStatCompiler.compile(List.of(
				new ArpgStatModifier(ArpgStat.COOLDOWN_RECOVERY, ArpgModifierOperation.INCREASED, 0.25)
		));
		assertEquals(32, ArpgSkillUseSemantics.cooldownTicks(CLEAVE, snapshot));
	}

	@Test
	void coneRejectsTargetsBehindOrOutsideRange() {
		assertTrue(ArpgSkillUseSemantics.inCone(0.0, 1.0, 0.0, 4.0, 5.0, 60.0));
		assertTrue(ArpgSkillUseSemantics.inCone(0.0, 1.0, 2.0, 3.0, 5.0, 60.0));
		assertFalse(ArpgSkillUseSemantics.inCone(0.0, 1.0, 0.0, -4.0, 5.0, 60.0));
		assertFalse(ArpgSkillUseSemantics.inCone(0.0, 1.0, 0.0, 6.0, 5.0, 60.0));
	}
}
