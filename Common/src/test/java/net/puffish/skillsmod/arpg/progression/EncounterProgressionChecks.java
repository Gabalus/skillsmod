package net.puffish.skillsmod.arpg.progression;

import java.util.Set;

/** Dependency-free regression checks, also executed by the JUnit wrapper. */
public final class EncounterProgressionChecks {
	private static int checks;

	private EncounterProgressionChecks() {
	}

	public static void main(String[] args) {
		checks = 0;
		var policy = EncounterProgressionPolicy.defaults();
		var none = EncounterThreat.NONE;
		var elite = new EncounterThreat(20, 5, 2, true, false, false);
		var boss = new EncounterThreat(20, 5, 2, true, true, false);
		var summon = new EncounterThreat(20, 5, 2, true, true, true);
		check(!policy.eligible("minecraft:overworld", false, false, false, none), "Sandbox kills cannot level characters");
		check(!policy.eligible("minecraft:overworld", false, false, false, elite), "Random elites are not progression encounters");
		check(policy.eligible("minecraft:overworld", false, false, false, boss), "Natural invaders are world-boss encounters");
		check(!policy.eligible("minecraft:overworld", false, false, true, boss), "Farmed bosses are excluded");
		check(!policy.eligible("minecraft:overworld", false, false, false, summon), "Provider summons are excluded");
		check(policy.eligible("minecraft:overworld", true, false, true, summon), "Authored encounter markers explicitly opt in");
		check(policy.eligible("minecraft:overworld", false, true, false, none), "World-boss markers opt in independently");
		var dungeonPolicy = configured(Set.of("arpg:rift"), false, 50_000);
		check(dungeonPolicy.eligible("arpg:rift", false, false, false, none), "Whitelisted expedition allows ordinary enemies");
		check(!dungeonPolicy.eligible("arpg:rift", false, false, true, none), "Dungeon farms do not bypass exclusions");
		check(!dungeonPolicy.eligible("arpg:rift", false, false, false, summon), "Dungeon summons do not bypass exclusions");
		check(!dungeonPolicy.eligible("minecraft:overworld", false, false, false, boss), "Automatic world-boss rewards are configurable");
		check(dungeonPolicy.eligible("minecraft:overworld", false, true, false, boss), "Explicit boss marker works with auto mode disabled");
		check(policy.reward(5, none) == 500, "Missing providers use baseline XP");
		check(policy.reward(5, elite) == 1000, "L2 and Apotheosis bonuses add rather than multiply");
		check(policy.reward(5, boss) == 1375, "Invaders use one rank bonus, not elite plus invader");
		check(policy.reward(0, boss) == 0, "Zero drops cannot generate XP");
		check(policy.reward(-1, boss) == 0, "Negative drops cannot generate XP");
		check(policy.reward(Integer.MAX_VALUE, boss) == 50_000, "Provider XP boosts cannot exceed reward cap");
		var extreme = new EncounterThreat(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, true, true, false);
		check(policy.reward(5, extreme) == 2200, "Extreme provider metrics are individually clamped");
		var negative = new EncounterThreat(-100, -200, -3, false, false, false);
		check(policy.reward(5, negative) == 500, "Negative threat cannot reduce or underflow rewards");
		check(configured(Set.of(), true, 1000).reward(5, boss) == 1000, "Lower pack reward caps are respected");
		expectInvalid(() -> configured(Set.of("invalid dimension"), true, 1000));
		expectInvalid(() -> configured(Set.of(), true, 0));
		expectInvalid(() -> configured(Set.of(), true, 50_001));
		expectInvalid(() -> new EncounterProgressionPolicy(2, Set.of(), true, 100, 20, 4, 1, 3, 20, 25, 100, 50_000));
		expectInvalid(() -> new EncounterProgressionPolicy(1, Set.of(), true, 1001, 20, 4, 1, 3, 20, 25, 100, 50_000));
		expectInvalid(() -> new EncounterProgressionPolicy(1, Set.of(), true, 100, 20, 5, 1, 3, 20, 25, 100, 50_000));
		expectInvalid(() -> new EncounterProgressionPolicy(1, Set.of(), true, 100, 20, 4, -1, 3, 20, 25, 100, 50_000));
		expectInvalid(() -> new EncounterProgressionPolicy(1, Set.of(), true, 100, 20, 4, 101, 3, 20, 25, 100, 50_000));
		expectInvalid(() -> new EncounterProgressionPolicy(1, null, true, 100, 20, 4, 1, 3, 20, 25, 100, 50_000));
		System.out.println("Encounter progression: " + checks + " checks passed");
	}

	private static EncounterProgressionPolicy configured(Set<String> dimensions, boolean automaticBosses, long cap) {
		return new EncounterProgressionPolicy(1, dimensions, automaticBosses, 100, 20, 4, 1, 3, 20, 25, 100, cap);
	}

	private static void expectInvalid(Runnable action) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			checks++;
			return;
		}
		throw new AssertionError("Invalid progression policy was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
		checks++;
	}
}
