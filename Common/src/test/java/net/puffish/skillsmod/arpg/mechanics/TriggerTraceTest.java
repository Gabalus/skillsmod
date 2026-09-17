package net.puffish.skillsmod.arpg.mechanics;

import net.puffish.skillsmod.arpg.metric.QualifierSet;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriggerTraceTest {
	@Test
	void damagingGenerationStopsAfterGenerationTwo() {
		var trace = TriggerTrace.root("fireball", "player");
		var first = trace.advanceGeneration(true, false);
		assertTrue(first.allowed());
		assertEquals(1, first.trace().generation());
		var second = first.trace().advanceGeneration(true, false);
		assertTrue(second.allowed());
		assertEquals(2, second.trace().generation());
		var third = second.trace().advanceGeneration(true, false);
		assertFalse(third.allowed());
		assertEquals(TriggerTrace.Rejection.DAMAGE_GENERATION_LIMIT, third.rejection());
	}

	@Test
	void utilityGenerationThreeRequiresExplicitWhitelist() {
		var trace = TriggerTrace.root("utility", "player")
				.advanceGeneration(false, true).trace()
				.advanceGeneration(false, true).trace();
		assertFalse(trace.advanceGeneration(false, false).allowed());
		var third = trace.advanceGeneration(false, true);
		assertTrue(third.allowed());
		assertEquals(3, third.trace().generation());
	}

	@Test
	void nonReentrantRuleCannotBeVisitedTwice() {
		var first = TriggerTrace.root("root", "player").visitRule("rule:a", false);
		assertTrue(first.allowed());
		var duplicate = first.trace().visitRule("rule:a", false);
		assertFalse(duplicate.allowed());
		assertEquals(TriggerTrace.Rejection.RULE_REENTRY, duplicate.rejection());
		assertTrue(first.trace().visitRule("rule:a", true).allowed());
	}

	@Test
	void conversionGraphRejectsCycles() {
		var trace = TriggerTrace.root("root", "player")
				.visitConversion(new ConversionEdge("fire", "cold")).trace()
				.visitConversion(new ConversionEdge("cold", "lightning")).trace();
		var cycle = trace.visitConversion(new ConversionEdge("lightning", "fire"));
		assertFalse(cycle.allowed());
		assertEquals(TriggerTrace.Rejection.CONVERSION_CYCLE, cycle.rejection());
	}

	@Test
	void refundKeyCanResolveOnlyOncePerTrace() {
		var first = TriggerTrace.root("root", "player").claimRefund("mana_refund");
		assertTrue(first.allowed());
		var second = first.trace().claimRefund("mana_refund");
		assertFalse(second.allowed());
		assertEquals(TriggerTrace.Rejection.REFUND_REUSE, second.rejection());
	}

	@Test
	void reflectedPacketCannotReflectAgain() {
		var first = TriggerTrace.root("root", "player").markReflected();
		assertTrue(first.allowed());
		assertTrue(first.trace().provenance().contains(ProvenanceFlag.REFLECT));
		var second = first.trace().markReflected();
		assertFalse(second.allowed());
		assertEquals(TriggerTrace.Rejection.REFLECT_RECURSION, second.rejection());
	}

	@Test
	void rootBudgetsCapTriggeredExecutionsAndAuthoritativeProjectiles() {
		var trace = TriggerTrace.root("root", "player");
		for (int i = 0; i < TriggerTrace.MAX_TRIGGERED_EXECUTIONS; i++) {
			var result = trace.claimTriggeredExecution();
			assertTrue(result.allowed());
			trace = result.trace();
		}
		assertFalse(trace.claimTriggeredExecution().allowed());

		var projectiles = TriggerTrace.root("root", "player")
				.claimAuthoritativeProjectiles(TriggerTrace.MAX_AUTHORITATIVE_PROJECTILES).trace();
		assertFalse(projectiles.claimAuthoritativeProjectiles(1).allowed());
	}

	@Test
	void noStandardLootProvenanceBlocksRewards() {
		var trace = TriggerTrace.root("summon_attack", "player")
				.addProvenance(ProvenanceFlag.SUMMONED)
				.addProvenance(ProvenanceFlag.NO_STANDARD_LOOT);
		assertFalse(trace.allowsStandardRewards());
	}

	@Test
	void actionContextSnapshotsTagsResourcesAndRootTrace() {
		var resources = new java.util.HashMap<String, Double>();
		resources.put("mana", 75.0);
		var context = ActionContext.root(
				"fireball",
				"player",
				"player_entity",
				"staff",
				QualifierSet.of("fire", "spell", "projectile"),
				resources
		).withTarget("boss");
		resources.put("mana", 0.0);

		assertEquals(75.0, context.resourceState().get("mana"), 0.000001);
		assertEquals(0, context.triggerTrace().generation());
		assertEquals("boss", context.targetEntityId());
		assertEquals(Set.of("fire", "projectile", "spell"), context.tags().values());
	}
}
