package net.puffish.skillsmod.arpg.metric;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModifierResolverTest {
	private static final MetricKey DAMAGE = MetricKey.of("damage");
	private static final ResolvedKey FIRE_SPELL = ResolvedKey.global(
			DAMAGE,
			QualifierSet.of("fire", "spell", "projectile")
	);
	private static final MetricDefinition DAMAGE_DEFINITION = MetricDefinition.unbounded(DAMAGE);
	private static final ResolveContext GLOBAL_CONTEXT = ResolveContext.global();

	@Test
	void qualifierMatchingUsesSubsetSemantics() {
		var result = ModifierResolver.resolve(DAMAGE_DEFINITION, 100.0, FIRE_SPELL, List.of(
				modifier(QualifierSet.of("fire", "spell"), ModifierOperation.INCREASED, 0.20,
						StackGroup.SOURCE_ADDITIVE, "fire_spell_bonus", 0),
				modifier(QualifierSet.of("fire", "melee"), ModifierOperation.INCREASED, 0.50,
						StackGroup.SOURCE_ADDITIVE, "melee_bonus", 0)
		), GLOBAL_CONTEXT);

		assertEquals(120.0, result.value(), 0.000001);
		assertEquals(1, result.trace().entries().size());
	}

	@Test
	void setUsesHighestPriorityThenSourceKeyAsDeterministicTieBreak() {
		var result = ModifierResolver.resolve(DAMAGE_DEFINITION, 100.0, FIRE_SPELL, List.of(
				modifier(QualifierSet.empty(), ModifierOperation.SET, 80.0, StackGroup.BASE_SET, "z_low", 5),
				modifier(QualifierSet.empty(), ModifierOperation.SET, 120.0, StackGroup.BASE_SET, "b_high", 10),
				modifier(QualifierSet.empty(), ModifierOperation.SET, 110.0, StackGroup.BASE_SET, "a_high", 10)
		), GLOBAL_CONTEXT);

		assertEquals(110.0, result.value(), 0.000001);
		assertEquals("a_high", result.trace().winningSetSource());
		assertEquals(1, result.trace().entries().stream().filter(ModifierResolution.TraceEntry::applied).count());
	}

	@Test
	void canonicalLaneOrderAppliesGroupAndGlobalMultiplierCaps() {
		var result = ModifierResolver.resolve(DAMAGE_DEFINITION, 100.0, FIRE_SPELL, List.of(
				modifier(QualifierSet.empty(), ModifierOperation.FLAT, 20.0, StackGroup.SOURCE_ADDITIVE, "flat", 0),
				modifier(QualifierSet.empty(), ModifierOperation.INCREASED, 0.50, StackGroup.SOURCE_ADDITIVE, "inc", 0),
				modifier(QualifierSet.empty(), ModifierOperation.REDUCED, 0.10, StackGroup.SOURCE_ADDITIVE, "red", 0),
				modifier(QualifierSet.empty(), ModifierOperation.MORE, 2.00, StackGroup.CORE, "core", 0),
				modifier(QualifierSet.empty(), ModifierOperation.LESS, 0.10, StackGroup.IDENTITY, "identity", 0),
				modifier(QualifierSet.empty(), ModifierOperation.MORE, 1.00, StackGroup.CONDITIONAL, "conditional", 0),
				modifier(QualifierSet.empty(), ModifierOperation.MORE, 1.00, StackGroup.PARTY, "party", 0)
		), GLOBAL_CONTEXT);

		assertEquals(1.40, result.trace().additiveFactor(), 0.000001);
		assertEquals(2.00, result.trace().sourceMoreFactors().get(StackGroup.CORE), 0.000001);
		assertEquals(0.90, result.trace().sourceMoreFactors().get(StackGroup.IDENTITY), 0.000001);
		assertEquals(1.75, result.trace().sourceMoreFactors().get(StackGroup.CONDITIONAL), 0.000001);
		assertEquals(1.50, result.trace().sourceMoreFactors().get(StackGroup.PARTY), 0.000001);
		assertEquals(4.725, result.trace().sourceMoreProductBeforeCap(), 0.000001);
		assertEquals(4.00, result.trace().sourceMoreProduct(), 0.000001);
		assertEquals(672.0, result.value(), 0.000001);
	}

	@Test
	void multipleMoreModifiersAddInsideTheirGroupBeforeClamping() {
		var result = ModifierResolver.resolve(DAMAGE_DEFINITION, 100.0, FIRE_SPELL, List.of(
				modifier(QualifierSet.empty(), ModifierOperation.MORE, 0.20, StackGroup.CORE, "core_a", 0),
				modifier(QualifierSet.empty(), ModifierOperation.MORE, 0.30, StackGroup.CORE, "core_b", 0)
		), GLOBAL_CONTEXT);

		assertEquals(1.50, result.trace().sourceMoreFactors().get(StackGroup.CORE), 0.000001);
		assertEquals(150.0, result.value(), 0.000001);
	}

	@Test
	void additiveBucketHasCanonicalTwentyPercentFloor() {
		var result = ModifierResolver.resolve(DAMAGE_DEFINITION, 100.0, FIRE_SPELL, List.of(
				modifier(QualifierSet.empty(), ModifierOperation.REDUCED, 1.50,
						StackGroup.SOURCE_ADDITIVE, "heavy_reduction", 0)
		), GLOBAL_CONTEXT);

		assertEquals(0.20, result.trace().additiveFactor(), 0.000001);
		assertEquals(20.0, result.value(), 0.000001);
	}

	@Test
	void metricDefinitionCanOwnATighterAdditiveFloorAndFinalCap() {
		var definition = new MetricDefinition(DAMAGE, 0.50, 0.0, 60.0);
		var result = ModifierResolver.resolve(definition, 100.0, FIRE_SPELL, List.of(
				modifier(QualifierSet.empty(), ModifierOperation.REDUCED, 1.00,
						StackGroup.SOURCE_ADDITIVE, "reduction", 0),
				modifier(QualifierSet.empty(), ModifierOperation.FLAT, 30.0,
						StackGroup.SOURCE_ADDITIVE, "flat", 0)
		), GLOBAL_CONTEXT);

		assertEquals(0.50, result.trace().additiveFactor(), 0.000001);
		assertEquals(65.0, result.trace().rawValue(), 0.000001);
		assertEquals(60.0, result.value(), 0.000001);
	}

	@Test
	void conditionExpressionsGateModifiersWithoutBespokeResolverCode() {
		var condition = ConditionExpression.all(
				ConditionExpression.flag("low_life"),
				ConditionExpression.not(ConditionExpression.flag("silenced"))
		);
		var conditional = new Modifier(
				DAMAGE,
				QualifierSet.of("spell"),
				condition,
				ModifierOperation.MORE,
				0.50,
				StackGroup.CONDITIONAL,
				"low_life_spell",
				0
		);

		assertEquals(100.0, ModifierResolver.resolve(
				DAMAGE_DEFINITION, 100.0, FIRE_SPELL, List.of(conditional), GLOBAL_CONTEXT
		).value(), 0.000001);
		assertEquals(150.0, ModifierResolver.resolve(
				DAMAGE_DEFINITION,
				100.0,
				FIRE_SPELL,
				List.of(conditional),
				ResolveContext.global(Set.of("low_life"))
		).value(), 0.000001);
		assertEquals(100.0, ModifierResolver.resolve(
				DAMAGE_DEFINITION,
				100.0,
				FIRE_SPELL,
				List.of(conditional),
				ResolveContext.global(Set.of("low_life", "silenced"))
		).value(), 0.000001);
	}

	@Test
	void targetSideMultiplierIsNotFoldedBackIntoSourceMoreProduct() {
		var targetState = modifier(
				QualifierSet.empty(), ModifierOperation.MORE, 0.50, StackGroup.TARGET_STATE, "broken", 0
		);
		var result = ModifierResolver.resolve(
				DAMAGE_DEFINITION, 100.0, FIRE_SPELL, List.of(targetState), GLOBAL_CONTEXT
		);

		assertEquals(100.0, result.value(), 0.000001);
		assertFalse(result.trace().entries().get(0).applied());
		assertTrue(result.trace().entries().get(0).reason().contains("later non-source phase"));
	}

	@Test
	void resolutionIsIndependentOfInputCollectionOrder() {
		var modifiers = new ArrayList<>(List.of(
				modifier(QualifierSet.empty(), ModifierOperation.FLAT, 13.0, StackGroup.SOURCE_ADDITIVE, "flat", 0),
				modifier(QualifierSet.of("spell"), ModifierOperation.INCREASED, 0.25,
						StackGroup.SOURCE_ADDITIVE, "spell", 0),
				modifier(QualifierSet.of("fire"), ModifierOperation.REDUCED, 0.05,
						StackGroup.SOURCE_ADDITIVE, "fire_reduced", 0),
				modifier(QualifierSet.empty(), ModifierOperation.MORE, 0.20, StackGroup.CORE, "core", 0),
				modifier(QualifierSet.empty(), ModifierOperation.MORE, 0.15, StackGroup.IDENTITY, "identity", 0),
				modifier(QualifierSet.empty(), ModifierOperation.LESS, 0.05, StackGroup.CONDITIONAL, "condition", 0)
		));
		double baseline = ModifierResolver.resolve(
				DAMAGE_DEFINITION, 100.0, FIRE_SPELL, modifiers, GLOBAL_CONTEXT
		).value();

		for (int seed = 0; seed < 50; seed++) {
			Collections.shuffle(modifiers, new Random(seed));
			assertEquals(baseline, ModifierResolver.resolve(
					DAMAGE_DEFINITION, 100.0, FIRE_SPELL, modifiers, GLOBAL_CONTEXT
			).value(), 0.0);
		}
	}

	@Test
	void invalidStackGroupIsRejectedAtConstructionTime() {
		assertThrows(IllegalArgumentException.class, () -> modifier(
				QualifierSet.empty(), ModifierOperation.MORE, 0.20, StackGroup.SOURCE_ADDITIVE, "invalid", 0
		));
	}

	private static Modifier modifier(
			QualifierSet qualifiers,
			ModifierOperation operation,
			double value,
			StackGroup stackGroup,
			String sourceKey,
			int priority
	) {
		return new Modifier(
				DAMAGE,
				qualifiers,
				ConditionExpression.always(),
				operation,
				value,
				stackGroup,
				sourceKey,
				priority
		);
	}
}
