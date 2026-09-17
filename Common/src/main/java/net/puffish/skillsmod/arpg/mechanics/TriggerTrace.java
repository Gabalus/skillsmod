package net.puffish.skillsmod.arpg.mechanics;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public record TriggerTrace(
		String rootActionId,
		String rootOwnerId,
		int generation,
		Set<String> visitedRuleIds,
		Set<ConversionEdge> visitedConversionEdges,
		Set<String> refundKeys,
		int triggeredExecutionCount,
		int authoritativeProjectileCount,
		Set<ProvenanceFlag> provenance
) {
	public static final int MAX_DAMAGE_GENERATION = 2;
	public static final int MAX_WHITELISTED_UTILITY_GENERATION = 3;
	public static final int MAX_TRIGGERED_EXECUTIONS = 12;
	public static final int MAX_AUTHORITATIVE_PROJECTILES = 32;

	public TriggerTrace {
		rootActionId = normalizeId(rootActionId, "rootActionId");
		rootOwnerId = normalizeId(rootOwnerId, "rootOwnerId");
		if (generation < 0) {
			throw new IllegalArgumentException("generation cannot be negative");
		}
		if (visitedRuleIds == null || visitedConversionEdges == null || refundKeys == null || provenance == null) {
			throw new IllegalArgumentException("trace collections cannot be null");
		}
		if (triggeredExecutionCount < 0 || triggeredExecutionCount > MAX_TRIGGERED_EXECUTIONS) {
			throw new IllegalArgumentException("triggeredExecutionCount is outside the global budget");
		}
		if (authoritativeProjectileCount < 0 || authoritativeProjectileCount > MAX_AUTHORITATIVE_PROJECTILES) {
			throw new IllegalArgumentException("authoritativeProjectileCount is outside the global budget");
		}
		visitedRuleIds = normalizedIds(visitedRuleIds, "ruleId");
		visitedConversionEdges = Collections.unmodifiableSet(new TreeSet<>(visitedConversionEdges));
		refundKeys = normalizedIds(refundKeys, "refundKey");
		var flags = provenance.isEmpty() ? EnumSet.noneOf(ProvenanceFlag.class) : EnumSet.copyOf(provenance);
		provenance = Collections.unmodifiableSet(flags);
	}

	public static TriggerTrace root(String rootActionId, String rootOwnerId) {
		return new TriggerTrace(
				rootActionId,
				rootOwnerId,
				0,
				Set.of(),
				Set.of(),
				Set.of(),
				0,
				0,
				Set.of()
		);
	}

	public GuardResult advanceGeneration(boolean damaging, boolean whitelistUtilityGenerationThree) {
		int nextGeneration = generation + 1;
		int maximum = damaging || !whitelistUtilityGenerationThree
				? MAX_DAMAGE_GENERATION
				: MAX_WHITELISTED_UTILITY_GENERATION;
		if (nextGeneration > maximum) {
			return GuardResult.rejected(this,
					damaging ? Rejection.DAMAGE_GENERATION_LIMIT : Rejection.UTILITY_GENERATION_LIMIT);
		}
		return GuardResult.allowed(copy(
				nextGeneration,
				visitedRuleIds,
				visitedConversionEdges,
				refundKeys,
				triggeredExecutionCount,
				authoritativeProjectileCount,
				withFlag(ProvenanceFlag.TRIGGERED)
		));
	}

	public GuardResult visitRule(String ruleId, boolean reentrant) {
		var normalized = normalizeId(ruleId, "ruleId");
		if (visitedRuleIds.contains(normalized) && !reentrant) {
			return GuardResult.rejected(this, Rejection.RULE_REENTRY);
		}
		var rules = new TreeSet<>(visitedRuleIds);
		rules.add(normalized);
		return GuardResult.allowed(copy(
				generation,
				rules,
				visitedConversionEdges,
				refundKeys,
				triggeredExecutionCount,
				authoritativeProjectileCount,
				provenance
		));
	}

	public GuardResult visitConversion(ConversionEdge edge) {
		if (edge == null) {
			throw new IllegalArgumentException("edge cannot be null");
		}
		if (visitedConversionEdges.contains(edge) || createsConversionCycle(edge)) {
			return GuardResult.rejected(this, Rejection.CONVERSION_CYCLE);
		}
		var edges = new TreeSet<>(visitedConversionEdges);
		edges.add(edge);
		return GuardResult.allowed(copy(
				generation,
				visitedRuleIds,
				edges,
				refundKeys,
				triggeredExecutionCount,
				authoritativeProjectileCount,
				provenance
		));
	}

	public GuardResult claimRefund(String refundKey) {
		var normalized = normalizeId(refundKey, "refundKey");
		if (refundKeys.contains(normalized)) {
			return GuardResult.rejected(this, Rejection.REFUND_REUSE);
		}
		var refunds = new TreeSet<>(refundKeys);
		refunds.add(normalized);
		return GuardResult.allowed(copy(
				generation,
				visitedRuleIds,
				visitedConversionEdges,
				refunds,
				triggeredExecutionCount,
				authoritativeProjectileCount,
				provenance
		));
	}

	public GuardResult markReflected() {
		if (provenance.contains(ProvenanceFlag.REFLECT)) {
			return GuardResult.rejected(this, Rejection.REFLECT_RECURSION);
		}
		return GuardResult.allowed(copy(
				generation,
				visitedRuleIds,
				visitedConversionEdges,
				refundKeys,
				triggeredExecutionCount,
				authoritativeProjectileCount,
				withFlag(ProvenanceFlag.REFLECT)
		));
	}

	public GuardResult claimTriggeredExecution() {
		if (triggeredExecutionCount >= MAX_TRIGGERED_EXECUTIONS) {
			return GuardResult.rejected(this, Rejection.TRIGGERED_EXECUTION_BUDGET);
		}
		return GuardResult.allowed(copy(
				generation,
				visitedRuleIds,
				visitedConversionEdges,
				refundKeys,
				triggeredExecutionCount + 1,
				authoritativeProjectileCount,
				provenance
		));
	}

	public GuardResult claimAuthoritativeProjectiles(int count) {
		if (count < 0) {
			throw new IllegalArgumentException("projectile count cannot be negative");
		}
		if (authoritativeProjectileCount + count > MAX_AUTHORITATIVE_PROJECTILES) {
			return GuardResult.rejected(this, Rejection.PROJECTILE_BUDGET);
		}
		return GuardResult.allowed(copy(
				generation,
				visitedRuleIds,
				visitedConversionEdges,
				refundKeys,
				triggeredExecutionCount,
				authoritativeProjectileCount + count,
				provenance
		));
	}

	public TriggerTrace addProvenance(ProvenanceFlag flag) {
		if (flag == null) {
			throw new IllegalArgumentException("flag cannot be null");
		}
		if (flag == ProvenanceFlag.REFLECT || flag == ProvenanceFlag.TRIGGERED) {
			throw new IllegalArgumentException("REFLECT and TRIGGERED provenance must use guarded trace transitions");
		}
		return copy(
				generation,
				visitedRuleIds,
				visitedConversionEdges,
				refundKeys,
				triggeredExecutionCount,
				authoritativeProjectileCount,
				withFlag(flag)
		);
	}

	public boolean allowsStandardRewards() {
		return !provenance.contains(ProvenanceFlag.NO_STANDARD_LOOT);
	}

	private boolean createsConversionCycle(ConversionEdge candidate) {
		Map<String, Set<String>> graph = new HashMap<>();
		for (var edge : visitedConversionEdges) {
			graph.computeIfAbsent(edge.from(), ignored -> new HashSet<>()).add(edge.to());
		}
		graph.computeIfAbsent(candidate.from(), ignored -> new HashSet<>()).add(candidate.to());
		return hasPath(graph, candidate.to(), candidate.from(), new HashSet<>());
	}

	private static boolean hasPath(Map<String, Set<String>> graph, String current, String target, Set<String> visited) {
		if (current.equals(target)) {
			return true;
		}
		if (!visited.add(current)) {
			return false;
		}
		for (var next : graph.getOrDefault(current, Set.of())) {
			if (hasPath(graph, next, target, visited)) {
				return true;
			}
		}
		return false;
	}

	private Set<ProvenanceFlag> withFlag(ProvenanceFlag flag) {
		var flags = provenance.isEmpty() ? EnumSet.noneOf(ProvenanceFlag.class) : EnumSet.copyOf(provenance);
		flags.add(flag);
		return flags;
	}

	private TriggerTrace copy(
			int newGeneration,
			Set<String> newVisitedRuleIds,
			Set<ConversionEdge> newVisitedConversionEdges,
			Set<String> newRefundKeys,
			int newTriggeredExecutionCount,
			int newAuthoritativeProjectileCount,
			Set<ProvenanceFlag> newProvenance
	) {
		return new TriggerTrace(
				rootActionId,
				rootOwnerId,
				newGeneration,
				newVisitedRuleIds,
				newVisitedConversionEdges,
				newRefundKeys,
				newTriggeredExecutionCount,
				newAuthoritativeProjectileCount,
				newProvenance
		);
	}

	private static Set<String> normalizedIds(Set<String> values, String name) {
		var normalized = new TreeSet<String>();
		for (var value : values) {
			normalized.add(normalizeId(value, name));
		}
		return Collections.unmodifiableSet(normalized);
	}

	private static String normalizeId(String value, String name) {
		if (value == null) {
			throw new IllegalArgumentException(name + " cannot be null");
		}
		var normalized = value.trim().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException(name + " cannot be blank");
		}
		return normalized;
	}

	public enum Rejection {
		DAMAGE_GENERATION_LIMIT,
		UTILITY_GENERATION_LIMIT,
		RULE_REENTRY,
		CONVERSION_CYCLE,
		REFUND_REUSE,
		REFLECT_RECURSION,
		TRIGGERED_EXECUTION_BUDGET,
		PROJECTILE_BUDGET
	}

	public record GuardResult(boolean allowed, TriggerTrace trace, Rejection rejection) {
		private static GuardResult allowed(TriggerTrace trace) {
			return new GuardResult(true, trace, null);
		}

		private static GuardResult rejected(TriggerTrace trace, Rejection rejection) {
			return new GuardResult(false, trace, rejection);
		}
	}
}
