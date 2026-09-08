package net.puffish.skillsmod.arpg;

import net.puffish.skillsmod.arpg.character.ArpgCharacter;
import net.puffish.skillsmod.arpg.combat.DamageConversion;
import net.puffish.skillsmod.arpg.combat.DamagePipeline;
import net.puffish.skillsmod.arpg.combat.DamageType;
import net.puffish.skillsmod.arpg.combat.HitContext;
import net.puffish.skillsmod.arpg.data.ArpgContent;
import net.puffish.skillsmod.arpg.item.LootGenerator;
import net.puffish.skillsmod.arpg.skill.SkillTag;
import net.puffish.skillsmod.arpg.stat.ArpgModifierOperation;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatCompiler;
import net.puffish.skillsmod.arpg.stat.ArpgStatModifier;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArpgSystemsTest {
	private ArpgStatModifier modifier(ArpgStat stat, ArpgModifierOperation operation, double value) {
		return new ArpgStatModifier(stat, operation, value);
	}

	@Test
	void fireballUsesOneIncreasedBucketBeforeMoreCritAndPenetration() {
		var attack = ArpgStatCompiler.compile(List.of(
				modifier(ArpgStat.FIRE_DAMAGE, ArpgModifierOperation.FLAT, 20),
				modifier(ArpgStat.SPELL_DAMAGE, ArpgModifierOperation.INCREASED, 1),
				modifier(ArpgStat.FIRE_DAMAGE, ArpgModifierOperation.INCREASED, .5),
				modifier(ArpgStat.PROJECTILE_DAMAGE, ArpgModifierOperation.MORE, .3),
				modifier(ArpgStat.MELEE_DAMAGE, ArpgModifierOperation.MORE, 10),
				modifier(ArpgStat.CRITICAL_MULTIPLIER, ArpgModifierOperation.FLAT, .25),
				modifier(ArpgStat.FIRE_PENETRATION, ArpgModifierOperation.FLAT, .15)));
		var defense = ArpgStatCompiler.compile(List.of(modifier(ArpgStat.FIRE_RESISTANCE, ArpgModifierOperation.FLAT, .4)));
		var hit = new HitContext(Map.of(DamageType.FIRE, 100.0), Set.of(SkillTag.SPELL, SkillTag.PROJECTILE, SkillTag.FIRE), true, false);
		assertEquals(511.875, DamagePipeline.resolve(hit, attack, defense, List.of(), 0, false, false).total(), .00001);
	}

	@Test
	void conversionConservesDamageAndRemembersSourceScalingWithoutDoubleCountingTags() {
		var attack = ArpgStatCompiler.compile(List.of(modifier(ArpgStat.PHYSICAL_DAMAGE, ArpgModifierOperation.INCREASED, .5),
				modifier(ArpgStat.FIRE_DAMAGE, ArpgModifierOperation.INCREASED, .5), modifier(ArpgStat.ATTACK_DAMAGE, ArpgModifierOperation.INCREASED, 1)));
		var empty = ArpgStatCompiler.compile(List.of());
		var hit = new HitContext(Map.of(DamageType.PHYSICAL, 100.0), Set.of(SkillTag.ATTACK), false, false);
		var result = DamagePipeline.resolve(hit, attack, empty, List.of(new DamageConversion(DamageType.PHYSICAL, DamageType.FIRE, .5)), 0, false, false);
		assertEquals(125, result.damage().get(DamageType.PHYSICAL), .00001);
		assertEquals(150, result.damage().get(DamageType.FIRE), .00001);
		assertEquals(0, DamagePipeline.resolve(hit, attack, empty, List.of(), 0, true, false).total());
		assertThrows(IllegalArgumentException.class, () -> new DamageConversion(DamageType.FIRE, DamageType.PHYSICAL, .5));
	}

	@Test
	void excessiveConversionIsNormalizedAndDamageOverTimeCannotCritOrReceiveAddedDamage() {
		var empty = ArpgStatCompiler.compile(List.of());
		var conversions = List.of(new DamageConversion(DamageType.PHYSICAL, DamageType.FIRE, .8), new DamageConversion(DamageType.PHYSICAL, DamageType.COLD, .8));
		assertEquals(100, DamagePipeline.resolve(new HitContext(Map.of(DamageType.PHYSICAL, 100.0), Set.of(), false, false), empty, empty, conversions, 0, false, false).total(), .001);
		var attack = ArpgStatCompiler.compile(List.of(modifier(ArpgStat.FIRE_DAMAGE, ArpgModifierOperation.FLAT, 500)));
		assertEquals(10, DamagePipeline.resolve(new HitContext(Map.of(DamageType.FIRE, 10.0), Set.of(), true, true), attack, empty, List.of(), 0, false, false).total());
	}

	@Test
	void multiclassTrialsAndMilestonesCannotGrantDuplicateProgress() {
		var character = new ArpgCharacter();
		character.choosePrimary("warrior");
		assertThrows(IllegalStateException.class, () -> character.chooseSecondary("arcanist"));
		assertThrows(IllegalStateException.class, () -> character.choosePrimary("rogue"));
		character.gainExperience(100_000_000L);
		assertEquals(100, character.level());
		assertThrows(IllegalStateException.class, () -> character.chooseSecondary("warrior"));
		character.chooseSecondary("arcanist");
		assertEquals("arcanist_warrior", character.confluence());
		assertThrows(IllegalStateException.class, () -> character.chooseAscendancy("juggernaut"));
		for (int i = 1; i <= 4; i++) {
			assertTrue(character.completeMilestone("trial_" + i));
			assertFalse(character.completeMilestone("trial_" + i));
		}
		character.chooseAscendancy("juggernaut");
		assertEquals(8, character.ascendancyPoints());
		assertEquals(12, character.confluencePoints());
		for (int i = 0; i < 6; i++) {
			character.completeMilestone("campaign_" + i);
		}
		assertEquals(117, character.passivePoints());
		character.specialize("cleave");
		character.gainSkillExperience("cleave", 100_000);
		assertEquals(20, character.specializationPoints("cleave"));
		assertTrue(character.completeMap("crypt", 1));
		assertFalse(character.completeMap("crypt", 1));
		assertEquals(2, character.unlockedMapTier());
	}

	@Test
	void catalogAndLootRespectTierTagsGroupsAndCraftingLimits() throws Exception {
		try (var reader = new InputStreamReader(getClass().getResourceAsStream("/data/puffish_skills/arpg/catalog.json"), StandardCharsets.UTF_8)) {
			var catalog = ArpgContent.read(reader);
			assertEquals(6, catalog.disciplines().size());
			assertEquals(24, catalog.disciplines().values().stream().mapToInt(d -> d.ascendancies().size()).sum());
			assertEquals(44, catalog.affixes().size());
			for (int seed = 0; seed < 500; seed++) {
				var item = LootGenerator.generate(catalog, "iron_sword", 20, "rare", new Random(seed));
				var groups = new HashSet<String>();
				int prefixes = 0;
				for (var roll : item.affixes()) {
					var affix = catalog.affixes().get(roll.affix());
					assertTrue(groups.add(affix.group()));
					assertTrue(affix.tags().stream().anyMatch(catalog.bases().get(item.base()).tags()::contains));
					var tier = affix.tiers().stream().filter(t -> t.tier() == roll.tier()).findFirst().orElseThrow();
					assertTrue(tier.level() <= item.itemLevel());
					assertTrue(roll.value() >= tier.min() && roll.value() <= tier.max());
					prefixes += affix.prefix() ? 1 : 0;
				}
				assertTrue(prefixes <= 3 && item.affixes().size() - prefixes <= 3);
			}
			var item = LootGenerator.generate(catalog, "iron_sword", 20, "rare", new Random(7));
			var reforged = LootGenerator.reforge(catalog, item, new Random(8));
			assertEquals(item.forgingPotential() - 3, reforged.forgingPotential());
			assertThrows(IllegalArgumentException.class, () -> LootGenerator.generate(catalog, "netherite_sword", 20, "rare", new Random(1)));
			assertThrows(IllegalStateException.class, () -> LootGenerator.reforge(catalog, LootGenerator.unique(catalog, "infernal_edge", 70), new Random(1)));
		}
	}
}
