package net.puffish.skillsmod.arpg.sandbox;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Register a server evaluator to add a distinct minigame without changing player persistence. */
public final class CraftMechanics {
	public record Result(int credit, Map<String, Integer> process) {
		public Result {
			if (credit < 0 || credit > 25) {
				throw new IllegalArgumentException("Invalid craft credit");
			}

			process = Map.copyOf(process);
			if (process.size() > 32) {
				throw new IllegalArgumentException("Process state exceeds limit");
			}
		}
	}

	@FunctionalInterface
	public interface Evaluator {
		Result evaluate(CraftSession session, String action, long tick, boolean correct);
	}

	private static final Map<String, Evaluator> EVALUATORS = new ConcurrentHashMap<>();
	static {
		register("arpg:sequence", (session, action, tick, correct) -> new Result(correct ? 25 : 0, session.process()));
		register("arpg:thermal_forge", CraftMechanics::forge);
		register("arpg:rune_route", CraftMechanics::rune);
	}

	private CraftMechanics() {
	}

	public static void register(String id, Evaluator evaluator) {
		SandboxCatalog.checkId(id);
		if (EVALUATORS.putIfAbsent(id, Objects.requireNonNull(evaluator)) != null) {
			throw new IllegalArgumentException("Duplicate craft mechanic " + id);
		}
	}

	public static boolean available(String id) {
		return EVALUATORS.containsKey(id);
	}

	public static Result evaluate(String id, CraftSession session, String action, long tick, boolean correct) {
		var evaluator = EVALUATORS.get(id);
		if (evaluator == null) {
			throw new IllegalStateException("Craft mechanic unavailable " + id);
		}

		return evaluator.evaluate(session, action, tick, correct);
	}

	private static Result forge(CraftSession session, String action, long tick, boolean correct) {
		int temperature = (int) Math.max(0L, session.process().getOrDefault("temperature", 0) - Math.min(1000L, tick - session.lastTick()));
		int credit = 0;
		if (correct) {
			switch (action) {
				case "heat" -> {
					temperature = 1000;
					credit = 25;
				}

				case "draw" -> {
					credit = Math.max(0, 25 - Math.abs(875 - temperature) / 5);
					temperature = Math.max(0, temperature - 80);
				}

				case "heavy" -> {
					credit = Math.max(0, 25 - Math.abs(775 - temperature) / 5);
					temperature = Math.max(0, temperature - 120);
				}

				case "quench" -> {
					credit = Math.max(0, 25 - Math.abs(600 - temperature) / 5);
					temperature = 0;
				}

				default -> throw new IllegalArgumentException("Unsupported forging action " + action);
			}
		}

		return new Result(credit, Map.of("temperature", temperature));
	}

	private static Result rune(CraftSession session, String action, long tick, boolean correct) {
		int x = session.process().getOrDefault("x", 0);
		int y = session.process().getOrDefault("y", 0);
		switch (action) {
			case "north" -> y++;
			case "south" -> y--;
			case "east" -> x++;
			case "west" -> x--;
			case "seal" -> {
				return new Result(correct && x == 0 && y == 0 ? 25 : 0, Map.of("x", x, "y", y, "closed", x == 0 && y == 0 ? 1 : 0));
			}

			default -> {
				return new Result(0, session.process());
			}
		}

		return new Result(correct ? 25 : 0, Map.of("x", x, "y", y));
	}
}
