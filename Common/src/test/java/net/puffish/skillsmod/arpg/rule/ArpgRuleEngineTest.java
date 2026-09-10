package net.puffish.skillsmod.arpg.rule;

import net.puffish.skillsmod.arpg.combat.DamageType;
import net.puffish.skillsmod.arpg.data.ArpgContent;
import net.puffish.skillsmod.arpg.stat.ArpgModifierOperation;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArpgRuleEngineTest {
	@Test
	void conversionAndElementalAvatarCompileIntoDamagePipelineInputs() throws Exception {
		var catalog = catalog();
		var context = ArpgRuleEngine.Context.of(ArpgRuleEngine.Event.HIT, "cleave", Set.of("attack", "melee", "hit"));
		var infernal = ArpgRuleEngine.evaluate(catalog, List.of("infernal_edge"), context);
		assertEquals(1, infernal.conversions().size());
		assertEquals(DamageType.PHYSICAL, infernal.conversions().getFirst().from());
		assertEquals(DamageType.FIRE, infernal.conversions().getFirst().to());
		assertEquals(.5, infernal.conversions().getFirst().fraction(), .00001);
		assertFalse(infernal.elementalOnly());

		var avatar = ArpgRuleEngine.evaluate(catalog, List.of("elemental_avatar"), context);
		assertEquals(3, avatar.conversions().size());
		assertTrue(avatar.elementalOnly());
	}

	@Test
	void conditionalRulesRequireBothConditionAndTags() throws Exception {
		var catalog = catalog();
		var inactive = ArpgRuleEngine.evaluate(catalog, List.of("last_stand"),
				ArpgRuleEngine.Context.of(ArpgRuleEngine.Event.HIT, "cleave", Set.of("melee")));
		assertTrue(inactive.modifiers().isEmpty());

		var wrongTags = ArpgRuleEngine.evaluate(catalog, List.of("last_stand"),
				ArpgRuleEngine.Context.of(ArpgRuleEngine.Event.HIT, "irons_spellbooks:fireball", Set.of("spell"))
						.with(ArpgRuleEngine.Condition.LOW_LIFE));
		assertTrue(wrongTags.modifiers().isEmpty());

		var active = ArpgRuleEngine.evaluate(catalog, List.of("last_stand"),
				ArpgRuleEngine.Context.of(ArpgRuleEngine.Event.HIT, "cleave", Set.of("melee"))
						.with(ArpgRuleEngine.Condition.LOW_LIFE));
		assertTrue(active.modifiers().stream().anyMatch(modifier -> modifier.stat() == ArpgStat.MELEE_DAMAGE
				&& modifier.operation() == ArpgModifierOperation.MORE && Math.abs(modifier.value() - .4) < .00001));
	}

	@Test
	void triggersAreTypedAndOnlyFireForTheirDeclaredEventAndCondition() throws Exception {
		var catalog = catalog();
		var wrongCondition = ArpgRuleEngine.evaluate(catalog, List.of("sanguine_recovery"),
				ArpgRuleEngine.Context.of(ArpgRuleEngine.Event.KILL, "cleave", Set.of("melee")));
		assertTrue(wrongCondition.triggers().isEmpty());

		var wrongEvent = ArpgRuleEngine.evaluate(catalog, List.of("sanguine_recovery"),
				ArpgRuleEngine.Context.of(ArpgRuleEngine.Event.HIT, "cleave", Set.of("melee"))
						.with(ArpgRuleEngine.Condition.BLEEDING));
		assertTrue(wrongEvent.triggers().isEmpty());

		var active = ArpgRuleEngine.evaluate(catalog, List.of("sanguine_recovery"),
				ArpgRuleEngine.Context.of(ArpgRuleEngine.Event.KILL, "cleave", Set.of("melee"))
						.with(ArpgRuleEngine.Condition.BLEEDING));
		assertEquals(1, active.triggers().size());
		assertEquals(ArpgRuleEngine.Action.HEAL, active.triggers().getFirst().action());
		assertEquals(.04, active.triggers().getFirst().value(), .00001);
		assertTrue(active.triggers().getFirst().cooldown() > 0);
	}

	@Test
	void buffTriggersCarryAuthoredModifiersAndDuration() throws Exception {
		var evaluation = ArpgRuleEngine.evaluate(catalog(), List.of("arcane_bulwark"),
				ArpgRuleEngine.Context.of(ArpgRuleEngine.Event.BLOCK, "", Set.of()));
		assertEquals(1, evaluation.triggers().size());
		var trigger = evaluation.triggers().getFirst();
		assertEquals(ArpgRuleEngine.Action.BUFF, trigger.action());
		assertTrue(trigger.duration() > 0);
		assertTrue(trigger.modifiers().stream().anyMatch(modifier -> modifier.stat() == ArpgStat.CAST_SPEED
				&& modifier.operation() == ArpgModifierOperation.INCREASED
				&& Math.abs(modifier.value() - .3) < .00001));
	}

	@Test
	void missingRuleIdsAreIgnoredSoReloadCanSafelyRetireDefinitions() throws Exception {
		var evaluation = ArpgRuleEngine.evaluate(catalog(), List.of("removed_by_datapack"),
				ArpgRuleEngine.Context.of(ArpgRuleEngine.Event.NONE, "", Set.of()));
		assertTrue(evaluation.modifiers().isEmpty());
		assertTrue(evaluation.conversions().isEmpty());
		assertTrue(evaluation.triggers().isEmpty());
	}

	private ArpgContent catalog() throws Exception {
		try (var reader = new InputStreamReader(
				getClass().getResourceAsStream("/data/puffish_skills/arpg/catalog.json"),
				StandardCharsets.UTF_8)) {
			return ArpgContent.read(reader);
		}
	}
}
