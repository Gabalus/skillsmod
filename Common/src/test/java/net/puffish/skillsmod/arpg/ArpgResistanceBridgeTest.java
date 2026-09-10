package net.puffish.skillsmod.arpg;

import net.puffish.skillsmod.arpg.combat.DamagePipeline;
import net.puffish.skillsmod.arpg.combat.DamageType;
import net.puffish.skillsmod.arpg.stat.ArpgModifierOperation;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatCompiler;
import net.puffish.skillsmod.arpg.stat.ArpgStatModifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArpgResistanceBridgeTest {
	@Test
	void resistanceCapsAtSeventyFivePercentAndPenetrationCanOvercomeIt() {
		var attack = ArpgStatCompiler.compile(List.of(
				new ArpgStatModifier(ArpgStat.FIRE_PENETRATION, ArpgModifierOperation.FLAT, 0.20)
		));
		var defense = ArpgStatCompiler.compile(List.of(
				new ArpgStatModifier(ArpgStat.FIRE_RESISTANCE, ArpgModifierOperation.FLAT, 0.60)
		));
		assertEquals(60.0, DamagePipeline.mitigateResistance(
				100.0, DamageType.FIRE, attack, defense, false), 0.00001);

		var cappedDefense = ArpgStatCompiler.compile(List.of(
				new ArpgStatModifier(ArpgStat.FIRE_RESISTANCE, ArpgModifierOperation.FLAT, 2.0)
		));
		var empty = ArpgStatCompiler.compile(List.of());
		assertEquals(25.0, DamagePipeline.mitigateResistance(
				100.0, DamageType.FIRE, empty, cappedDefense, false), 0.00001);

		var overPenetration = ArpgStatCompiler.compile(List.of(
				new ArpgStatModifier(ArpgStat.FIRE_PENETRATION, ArpgModifierOperation.FLAT, 2.0)
		));
		assertEquals(200.0, DamagePipeline.mitigateResistance(
				100.0, DamageType.FIRE, overPenetration, empty, false), 0.00001);
	}

	@Test
	void damageOverTimeIgnoresPenetrationAndPhysicalCannotUseResistanceBridge() {
		var attack = ArpgStatCompiler.compile(List.of(
				new ArpgStatModifier(ArpgStat.COLD_PENETRATION, ArpgModifierOperation.FLAT, 0.50)
		));
		var defense = ArpgStatCompiler.compile(List.of(
				new ArpgStatModifier(ArpgStat.COLD_RESISTANCE, ArpgModifierOperation.FLAT, 0.40)
		));
		assertEquals(60.0, DamagePipeline.mitigateResistance(
				100.0, DamageType.COLD, attack, defense, true), 0.00001);
		assertThrows(IllegalArgumentException.class, () -> DamagePipeline.mitigateResistance(
				100.0, DamageType.PHYSICAL, attack, defense, false));
	}
}
