package net.puffish.skillsmod.arpg;

import net.puffish.skillsmod.arpg.combat.ArpgAttackScaling;
import net.puffish.skillsmod.arpg.stat.ArpgModifierOperation;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatCompiler;
import net.puffish.skillsmod.arpg.stat.ArpgStatModifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArpgAttackScalingTest {
	@Test
	void meleeAndProjectileUseSeparateIncreasedAndMoreBuckets() {
		var snapshot = ArpgStatCompiler.compile(List.of(
				new ArpgStatModifier(ArpgStat.PHYSICAL_DAMAGE, ArpgModifierOperation.INCREASED, 0.25),
				new ArpgStatModifier(ArpgStat.ATTACK_DAMAGE, ArpgModifierOperation.INCREASED, 0.50),
				new ArpgStatModifier(ArpgStat.MELEE_DAMAGE, ArpgModifierOperation.INCREASED, 0.25),
				new ArpgStatModifier(ArpgStat.MELEE_DAMAGE, ArpgModifierOperation.MORE, 0.20),
				new ArpgStatModifier(ArpgStat.PROJECTILE_DAMAGE, ArpgModifierOperation.MORE, 0.50),
				new ArpgStatModifier(ArpgStat.PHYSICAL_DAMAGE, ArpgModifierOperation.FLAT, 999.0)
		));

		assertEquals(240.0, ArpgAttackScaling.scaleExistingPhysicalAttack(
				100.0, snapshot, ArpgAttackScaling.Delivery.MELEE), 0.00001);
		assertEquals(262.5, ArpgAttackScaling.scaleExistingPhysicalAttack(
				100.0, snapshot, ArpgAttackScaling.Delivery.PROJECTILE), 0.00001);
	}

	@Test
	void bridgeRejectsInvalidInputAndClampsExtremeDamage() {
		var snapshot = ArpgStatCompiler.compile(List.of(
				new ArpgStatModifier(ArpgStat.ATTACK_DAMAGE, ArpgModifierOperation.MORE, 1_000_000.0),
				new ArpgStatModifier(ArpgStat.PHYSICAL_DAMAGE, ArpgModifierOperation.MORE, 1_000_000.0)
		));

		assertEquals(1_000_000.0, ArpgAttackScaling.scaleExistingPhysicalAttack(
				100.0, snapshot, ArpgAttackScaling.Delivery.MELEE));
		assertThrows(IllegalArgumentException.class, () -> ArpgAttackScaling.scaleExistingPhysicalAttack(
				Double.NaN, snapshot, ArpgAttackScaling.Delivery.MELEE));
	}
}
