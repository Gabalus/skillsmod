package net.puffish.skillsmod.arpg.sandbox;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Server evaluates ordered actions. No quality score or final output is accepted from clients. */
public record CraftSession(UUID id, String operation, String mastery, List<String> sequence,
		int step, int score, int mistakes, long lastTick, String mechanic, Map<String, Integer> process) {
	public CraftSession {
		Objects.requireNonNull(id);
		SandboxCatalog.checkId(operation);
		SandboxCatalog.checkId(mastery);
		sequence = List.copyOf(sequence);
		SandboxCatalog.checkId(mechanic);
		process = Map.copyOf(process);
		if (process.size() > 32) {
			throw new IllegalArgumentException("Process state exceeds limit");
		}

		if (sequence.isEmpty() || sequence.size() > 64 || sequence.stream().anyMatch(String::isBlank)
		|| step < 0 || step > sequence.size() || score < 0 || score > step * 25
		|| mistakes < 0 || mistakes > 8 || lastTick < 0) {
			throw new IllegalArgumentException("Invalid craft session");
		}
	}

	public static CraftSession start(SandboxCatalog.Operation operation, UUID id, long tick) {
		return new CraftSession(id, operation.id(), operation.mastery(), operation.sequence(), 0, 0, 0, tick, operation.mechanic(), Map.of());
	}

	public boolean complete() {
		return step == sequence.size();
	}

	public boolean failed() {
		return mistakes == 8;
	}

	public String expected() {
		return complete() ? "finish" : sequence.get(step);
	}

	public CraftSession act(long sequenceNumber, String action, long tick) {
		if (complete() || failed()) {
			throw new IllegalStateException("Operation has ended");
		}

		// sequenceNumber is the server step: stale/out-of-order packets cannot advance the session.
		if (sequenceNumber != step) {
			throw new IllegalArgumentException("Stale action sequence");
		}

		if (tick < lastTick || tick - lastTick < 4) {
			throw new IllegalArgumentException("Action too soon");
		}

		boolean correct = expected().equals(action);
		var result = CraftMechanics.evaluate(mechanic, this, action, tick, correct);
		return new CraftSession(id, operation, mastery, sequence, step + (correct ? 1 : 0),
		score + (correct ? result.credit() : 0), mistakes + (correct ? 0 : 1), tick, mechanic, result.process());
	}

	public int quality() {
		if (!complete()) {
			throw new IllegalStateException("Incomplete craft");
		}

		return Math.max(0, Math.min(100, score * 100 / (sequence.size() * 25) - mistakes * 15));
	}
}
