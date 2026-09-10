package net.puffish.skillsmod.arpg;

import net.puffish.skillsmod.arpg.combat.ArpgDefenseSemantics;
import net.puffish.skillsmod.arpg.stat.ArpgModifierOperation;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatCompiler;
import net.puffish.skillsmod.arpg.stat.ArpgStatModifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArpgDefenseSemanticsTest {
	@Test
	void chanceStatsAreFractionsAndClampSafely() {
		assertEquals(0.0, ArpgDefenseSemantics.chance(-0.5), 0.00001);
		assertEquals(0.35, ArpgDefenseSemantics.chance(0.35), 0.00001);
		assertEquals(1.0, ArpgDefenseSemantics.chance(2.0), 0.00001);
		assertEquals(0.0, ArpgDefenseSemantics.chance(Double.NaN), 0.00001);
		assertTrue(ArpgDefenseSemantics.succeeds(0.35, 0.349));
		assertFalse(ArpgDefenseSemantics.succeeds(0.35, 0.35));
	}

	@Test
	void attacksResolveEvasionBeforeBlock() {
		var snapshot = ArpgStatCompiler.compile(List.of(
				new ArpgStatModifier(ArpgStat.EVASION, ArpgModifierOperation.FLAT, 0.25),
				new ArpgStatModifier(ArpgStat.BLOCK_CHANCE, ArpgModifierOperation.FLAT, 0.40)
		));

		assertEquals(ArpgDefenseSemantics.Outcome.DODGE,
				ArpgDefenseSemantics.resolveAttack(snapshot, 0.10, 0.10));
		assertEquals(ArpgDefenseSemantics.Outcome.BLOCK,
				ArpgDefenseSemantics.resolveAttack(snapshot, 0.30, 0.10));
		assertEquals(ArpgDefenseSemantics.Outcome.HIT,
				ArpgDefenseSemantics.resolveAttack(snapshot, 0.30, 0.50));
	}

	@Test
	void spellBlockDoesNotUseEvasionOrGenericBlock() {
		var withoutSpellBlock = ArpgStatCompiler.compile(List.of(
				new ArpgStatModifier(ArpgStat.EVASION, ArpgModifierOperation.FLAT, 1.0),
				new ArpgStatModifier(ArpgStat.BLOCK_CHANCE, ArpgModifierOperation.FLAT, 1.0)
		));
		assertEquals(ArpgDefenseSemantics.Outcome.HIT,
				ArpgDefenseSemantics.resolveSpell(withoutSpellBlock, 0.0));

		var withSpellBlock = ArpgStatCompiler.compile(List.of(
				new ArpgStatModifier(ArpgStat.SPELL_BLOCK, ArpgModifierOperation.FLAT, 0.3)
		));
		assertEquals(ArpgDefenseSemantics.Outcome.BLOCK,
				ArpgDefenseSemantics.resolveSpell(withSpellBlock, 0.29));
	}

	@Test
	void ironFortressMovesEvasionModifiersToArmor() {
		var modifiers = List.of(
				new ArpgStatModifier(ArpgStat.ARMOR, ArpgModifierOperation.FLAT, 5.0),
				new ArpgStatModifier(ArpgStat.EVASION, ArpgModifierOperation.FLAT, 7.0),
				new ArpgStatModifier(ArpgStat.EVASION, ArpgModifierOperation.INCREASED, 0.25)
		);
		var transformed = ArpgDefenseSemantics.applyIronFortress(modifiers, true);
		var snapshot = ArpgStatCompiler.compile(transformed);

		assertEquals(0.0, snapshot.apply(ArpgStat.EVASION, 0.0), 0.00001);
		assertEquals(15.0, snapshot.apply(ArpgStat.ARMOR, 0.0), 0.00001);
	}
}
