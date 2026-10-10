package net.puffish.skillsmod.arpg.sandbox;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable sandbox track, deliberately independent of combat experience and passive-tree points. */
public record SandboxState(Set<String> knowledge, Map<String, Integer> mastery,
Map<UUID, Quality> receipts, CraftSession session) {
	public record Quality(UUID receipt, String operation, int score, boolean automated) {
		public Quality {
			Objects.requireNonNull(receipt);
			SandboxCatalog.checkId(operation);
			if (score < 0 || score > (automated ? 60 : 100)) {
				throw new IllegalArgumentException("Invalid quality");
			}
		}
	}

	public SandboxState {
		knowledge = Set.copyOf(knowledge);
		mastery = Map.copyOf(mastery);
		receipts = Map.copyOf(receipts);
		knowledge.forEach(SandboxCatalog::checkId);
		mastery.forEach((id, value) -> {
			SandboxCatalog.checkId(id); if (value < 0 || value > 10000) {
				throw new IllegalArgumentException("Invalid mastery");
			}
		});
		receipts.forEach((id, quality) -> {
			if (!id.equals(quality.receipt())) {
				throw new IllegalArgumentException("Receipt key mismatch");
			}
		});
		if (session != null && receipts.containsKey(session.id())) {
			throw new IllegalArgumentException("Settled session still active");
		}
	}

	public static SandboxState empty() {
		return new SandboxState(Set.of(), Map.of(), Map.of(), null);
	}

	public SandboxState learn(SandboxCatalog catalog, String id, Map<String, String> installed) {
		var node = catalog.node(id);
		if (!catalog.available(node.module(), installed)) {
			throw new IllegalStateException("Required integration unavailable");
		}

		if (!knowledge.containsAll(node.prerequisites())) {
			throw new IllegalStateException("Missing knowledge prerequisites");
		}

		for (var requirement : node.masteryRequirements().entrySet()) {
			if (mastery.getOrDefault(requirement.getKey(), 0) < requirement.getValue()) {
				throw new IllegalStateException("Insufficient sandbox mastery: " + requirement.getKey());
			}
		}
		var next = new HashSet<>(knowledge);
		next.add(id);
		return new SandboxState(next, mastery, receipts, session);
	}

	public void requireRecipe(SandboxCatalog catalog, String operation, Map<String, String> installed) {
		var definition = catalog.operation(operation);
		if (!CraftMechanics.available(definition.mechanic())) {
			throw new IllegalStateException("Craft mechanic unavailable: " + definition.mechanic());
		}
		if (!catalog.available(definition.module(), installed) || !knowledge.contains(definition.knowledge())) {
			throw new IllegalStateException("Recipe locked or integration unavailable");
		}
	}

	public SandboxState start(SandboxCatalog catalog, String operation, UUID id, long tick, Map<String, String> installed) {
		requireRecipe(catalog, operation, installed);
		if (session != null || receipts.containsKey(id)) {
			throw new IllegalStateException("Session already active or settled");
		}

		return new SandboxState(knowledge, mastery, receipts, CraftSession.start(catalog.operation(operation), id, tick));
	}

	public SandboxState act(long sequence, String action, long tick) {
		if (session == null) {
			throw new IllegalStateException("No active session");
		}

		return new SandboxState(knowledge, mastery, receipts, session.act(sequence, action, tick));
	}

	public SandboxState finish(UUID id) {
		if (receipts.containsKey(id)) {
			return this;
		}

		if (session == null || !session.id().equals(id) || !session.complete()) {
			throw new IllegalStateException("Session cannot settle");
		}

		var nextReceipts = new HashMap<>(receipts);
		nextReceipts.put(id, new Quality(id, session.operation(), session.quality(), false));
		var nextMastery = new HashMap<>(mastery);
		nextMastery.put(session.mastery(), Math.min(10000, nextMastery.getOrDefault(session.mastery(), 0) + Math.max(1, session.quality() / 10)));
		return new SandboxState(knowledge, nextMastery, nextReceipts, null);
	}

	/** Requires the machine's registered owner state. It does not award manual mastery. */
	public Quality automated(SandboxCatalog catalog, String operation, UUID receipt, Map<String, String> installed) {
		requireRecipe(catalog, operation, installed);
		return new Quality(receipt, operation, catalog.operation(operation).automatedCeiling(), true);
	}

	public SandboxState cancel() {
		return new SandboxState(knowledge, mastery, receipts, null);
	}
}
