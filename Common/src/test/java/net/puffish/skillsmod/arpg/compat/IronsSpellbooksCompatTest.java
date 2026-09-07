package net.puffish.skillsmod.arpg.compat;

import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.puffish.skillsmod.arpg.stat.ArpgModifierOperation;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatModifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IronsSpellbooksCompatTest {
	@Test
	public void increasedDamageProjectsAsBaseMultiplier() {
		var modifier = new ArpgStatModifier(
				ArpgStat.SPELL_DAMAGE,
				ArpgModifierOperation.INCREASED,
				0.25
		);

		assertTrue(IronsSpellbooksCompat.canProject(modifier));
		assertEquals(
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE,
				IronsSpellbooksCompat.projectedOperation(modifier.operation())
		);
		assertEquals(0.25, IronsSpellbooksCompat.projectedValue(modifier), 0.000001);
	}

	@Test
	public void moreDamageProjectsAsIndependentTotalMultiplier() {
		var modifier = new ArpgStatModifier(
				ArpgStat.FIRE_DAMAGE,
				ArpgModifierOperation.MORE,
				0.30
		);

		assertEquals(
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
				IronsSpellbooksCompat.projectedOperation(modifier.operation())
		);
		assertEquals(0.30, IronsSpellbooksCompat.projectedValue(modifier), 0.000001);
	}

	@Test
	public void reducedAndLessBecomeNegativeAttributeModifiers() {
		var reduced = new ArpgStatModifier(
				ArpgStat.SPELL_DAMAGE,
				ArpgModifierOperation.REDUCED,
				0.15
		);
		var less = new ArpgStatModifier(
				ArpgStat.FIRE_DAMAGE,
				ArpgModifierOperation.LESS,
				0.20
		);

		assertEquals(-0.15, IronsSpellbooksCompat.projectedValue(reduced), 0.000001);
		assertEquals(-0.20, IronsSpellbooksCompat.projectedValue(less), 0.000001);
	}

	@Test
	public void flatSpellDamageStaysInArpgCoreInsteadOfBecomingSpellPower() {
		var spellDamage = new ArpgStatModifier(
				ArpgStat.SPELL_DAMAGE,
				ArpgModifierOperation.FLAT,
				10.0
		);
		var maximumMana = new ArpgStatModifier(
				ArpgStat.MAXIMUM_MANA,
				ArpgModifierOperation.FLAT,
				20.0
		);

		assertFalse(IronsSpellbooksCompat.canProject(spellDamage));
		assertTrue(IronsSpellbooksCompat.canProject(maximumMana));
	}
}
