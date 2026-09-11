package net.puffish.skillsmod.arpg.character;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Server-owned progress; never accepted from a client payload. */
public final class ArpgCharacter {
	public static final int MAX_LEVEL = 100;
	public static final int MAX_TRIALS = 4;
	public static final long MAX_EXPERIENCE = 100_000_000L;
	private static final int[] TRIAL_LEVELS = {30, 50, 70, 90};
	private static final long KILL_XP_MULTIPLIER = 100L;
	private static final long MAX_KILL_XP = 50_000L;

	private long experience;
	private String primary = "";
	private String secondary = "";
	private String ascendancy = "";
	private final Set<String> milestones = new HashSet<>();
	private final Set<String> atlas = new HashSet<>();
	private final Map<String, Integer> specializations = new HashMap<>();
	private int corruption;
	private int deepestDelve;

	public int level() {
		long remaining = experience;
		int level = 1;
		while (level < MAX_LEVEL && remaining >= experienceForLevel(level)) {
			remaining -= experienceForLevel(level++);
		}
		return level;
	}

	/** Experience required to advance from {@code level} to the next ARPG level. */
	public static long experienceForLevel(int level) {
		return 75L * level * level + 25L * level;
	}

	/** Total accumulated experience at the exact start of {@code targetLevel}. */
	public static long totalExperienceForLevel(int targetLevel) {
		if (targetLevel < 1 || targetLevel > MAX_LEVEL) {
			throw new IllegalArgumentException("ARPG level must be between 1 and " + MAX_LEVEL);
		}
		long total = 0L;
		for (int level = 1; level < targetLevel; level++) {
			total += experienceForLevel(level);
		}
		return total;
	}

	/** Converts a vanilla mob experience drop into server-authoritative ARPG kill experience. */
	public static long killExperience(int vanillaExperience) {
		if (vanillaExperience <= 0) {
			return 0L;
		}
		return Math.min(MAX_KILL_XP, vanillaExperience * KILL_XP_MULTIPLIER);
	}

	/** Development/admin operation that moves the character to the exact start of a level. */
	public void setLevel(int targetLevel) {
		experience = totalExperienceForLevel(targetLevel);
	}

	public int gainExperience(long amount) {
		if (amount < 0) {
			throw new IllegalArgumentException("Experience gain cannot be negative");
		}
		int previous = level();
		experience = Math.min(MAX_EXPERIENCE, experience + Math.min(amount, MAX_EXPERIENCE));
		return level() - previous;
	}

	public void choosePrimary(String id) {
		if (!primary.isEmpty() || id.isBlank()) {
			throw new IllegalStateException("Your primary discipline has already been chosen");
		}
		primary = id;
	}

	public void chooseSecondary(String id) {
		if (primary.isEmpty() || !secondary.isEmpty() || primary.equals(id) || id.isBlank() || level() < 20) {
			throw new IllegalStateException("Choose a different secondary discipline at level 20");
		}
		secondary = id;
	}

	public void chooseAscendancy(String id) {
		if (!ascendancy.isEmpty() || primary.isEmpty() || level() < 30 || !hasCompletedTrial(1)) {
			throw new IllegalStateException("Complete the first trial at level 30 before ascending");
		}
		ascendancy = id;
	}

	public boolean completeMilestone(String id) {
		return milestones.add(id);
	}

	public static int trialRequiredLevel(int trial) {
		if (trial < 1 || trial > MAX_TRIALS) {
			throw new IllegalArgumentException("Trial must be between 1 and " + MAX_TRIALS);
		}
		return TRIAL_LEVELS[trial - 1];
	}

	public boolean hasCompletedTrial(int trial) {
		if (trial < 1 || trial > MAX_TRIALS) {
			return false;
		}
		return milestones.contains("trial_" + trial);
	}

	public int completedTrials() {
		int completed = 0;
		for (int trial = 1; trial <= MAX_TRIALS; trial++) {
			if (hasCompletedTrial(trial)) {
				completed++;
			}
		}
		return completed;
	}

	/** Returns the next incomplete trial number, or 0 when all trials are complete. */
	public int nextTrial() {
		for (int trial = 1; trial <= MAX_TRIALS; trial++) {
			if (!hasCompletedTrial(trial)) {
				return trial;
			}
		}
		return 0;
	}

	/**
	 * Completes a validated Ascendancy Trial.
	 *
	 * @return true when the trial was newly completed, false when it was already complete
	 */
	public boolean completeTrial(int trial) {
		int requiredLevel = trialRequiredLevel(trial);
		if (hasCompletedTrial(trial)) {
			return false;
		}
		if (primary.isEmpty()) {
			throw new IllegalStateException("Choose a primary discipline before attempting Ascendancy Trials");
		}
		if (level() < requiredLevel) {
			throw new IllegalStateException(
					"Reach ARPG level " + requiredLevel + " before attempting Trial " + trial);
		}
		if (trial > 1 && !hasCompletedTrial(trial - 1)) {
			throw new IllegalStateException(
					"Complete Trial " + (trial - 1) + " before attempting Trial " + trial);
		}
		return milestones.add("trial_" + trial);
	}

	public int passivePoints() {
		return level() - 1 + (int) milestones.stream().filter(id -> id.startsWith("campaign_")).count() * 3;
	}

	public int ascendancyPoints() {
		return Math.min(8, completedTrials() * 2);
	}

	public int confluencePoints() {
		return secondary.isEmpty() ? 0 : Math.min(12, 1 + (level() - 20) / 5);
	}

	public String confluence() {
		return secondary.isEmpty() ? "" : primary.compareTo(secondary) < 0 ? primary + "_" + secondary : secondary + "_" + primary;
	}

	public void specialize(String id) {
		int slots = level() >= 70 ? 5 : level() >= 50 ? 4 : level() >= 30 ? 3 : level() >= 15 ? 2 : 1;
		if (specializations.containsKey(id) || specializations.size() >= slots) {
			throw new IllegalStateException("No free specialization slot; unspecialize a skill first");
		}
		specializations.put(id, 0);
	}

	public void unspecialize(String id) {
		specializations.remove(id);
	}

	public void gainSkillExperience(String id, int amount) {
		if (amount > 0) {
			specializations.computeIfPresent(id, (key, xp) -> Math.min(100_000, xp + Math.min(100_000, amount)));
		}
	}

	public int specializationPoints(String id) {
		return specializations.containsKey(id) ? Math.min(20, 1 + specializations.get(id) / 250) : 0;
	}

	public boolean completeMap(String id, int tier) {
		if (tier < 1 || tier > 16) {
			throw new IllegalArgumentException("Map tiers range from 1 to 16");
		}
		return atlas.add(id + ":" + tier);
	}

	public int unlockedMapTier() {
		int tier = 1;
		for (var completion : atlas) {
			int separator = completion.lastIndexOf(':');
			try {
				tier = Math.max(tier, Integer.parseInt(completion.substring(separator + 1)) + 1);
			} catch (NumberFormatException ignored) {
				// Old or removed map IDs do not unlock a tier.
			}
		}
		return Math.min(16, tier);
	}

	public void addCorruption(int amount) {
		corruption = Math.max(0, Math.min(1000, corruption + amount));
	}

	public void completeDelve(int depth) {
		deepestDelve = Math.max(deepestDelve, Math.min(1000, depth));
	}

	public long experience() {
		return experience;
	}

	public String primary() {
		return primary;
	}

	public String secondary() {
		return secondary;
	}

	public String ascendancy() {
		return ascendancy;
	}

	public Set<String> milestones() {
		return Set.copyOf(milestones);
	}

	public Set<String> atlas() {
		return Set.copyOf(atlas);
	}

	public Map<String, Integer> specializations() {
		return Map.copyOf(specializations);
	}

	public int corruption() {
		return corruption;
	}

	public int deepestDelve() {
		return deepestDelve;
	}

	public static ArpgCharacter restore(long experience, String primary, String secondary, String ascendancy,
			Set<String> milestones, Set<String> atlas, Map<String, Integer> specializations, int corruption, int depth) {
		var character = new ArpgCharacter();
		character.experience = Math.max(0, Math.min(MAX_EXPERIENCE, experience));
		character.primary = primary;
		character.secondary = secondary;
		character.ascendancy = ascendancy;
		character.milestones.addAll(milestones);
		character.atlas.addAll(atlas);
		specializations.forEach((id, xp) -> character.specializations.put(id, Math.max(0, Math.min(100_000, xp))));
		character.addCorruption(corruption);
		character.completeDelve(depth);
		return character;
	}
}
