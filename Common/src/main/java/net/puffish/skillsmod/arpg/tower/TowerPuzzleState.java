package net.puffish.skillsmod.arpg.tower;

import net.puffish.skillsmod.arpg.progression.CompletionReward;

import java.util.HashMap;
import java.util.Map;

/** Immutable per-player progress. Four-tick input spacing; ten-second gap resets an unfinished attempt. */
public record TowerPuzzleState(Map<String, Progress> entries) {
	public record Progress(String fingerprint, int step, long lastTick) {
		public Progress {
			if (fingerprint == null || !fingerprint.matches("[a-f0-9]{64}") || step < 0 || step > 8 || lastTick < 0) {
				throw new IllegalArgumentException("Invalid tower puzzle progress");
			}
		}
	}

	public TowerPuzzleState {
		entries = Map.copyOf(entries);
		if (entries.size() > 256) {
			throw new IllegalArgumentException("Tower puzzle receipt capacity exceeded");
		}
		entries.keySet().forEach(CompletionReward::checkId);
	}

	public static TowerPuzzleState empty() {
		return new TowerPuzzleState(Map.of());
	}

	public boolean solved(TowerPuzzle puzzle) {
		var progress = entries.get(puzzle.id());
		return progress != null && progress.fingerprint().equals(puzzle.fingerprint()) && progress.step() == puzzle.sequence().size();
	}

	public TowerPuzzleState press(TowerPuzzle puzzle, int index, long now) {
		if (index < 0 || index >= puzzle.sequence().size() || now < 0) {
			throw new IllegalArgumentException("Invalid puzzle input");
		}
		if (solved(puzzle)) {
			return this;
		}
		var previous = entries.get(puzzle.id());
		String fingerprint = puzzle.fingerprint();
		int step = 0;
		if (previous != null && previous.fingerprint().equals(fingerprint) && now >= previous.lastTick()) {
			if (now - previous.lastTick() < 4) {
				return this;
			}
			if (now - previous.lastTick() <= 200 && previous.step() < puzzle.sequence().size()) {
				step = previous.step();
			}
		}
		var updated = new HashMap<>(entries);
		updated.put(puzzle.id(), new Progress(fingerprint, index == step ? step + 1 : 0, now));
		return new TowerPuzzleState(updated);
	}
}
