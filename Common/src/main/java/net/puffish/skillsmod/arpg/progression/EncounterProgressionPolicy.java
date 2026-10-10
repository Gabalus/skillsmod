package net.puffish.skillsmod.arpg.progression;

import net.puffish.skillsmod.arpg.character.ArpgCharacter;

import java.util.Set;

/** One bounded reward calculation for both optional difficulty providers. */
public record EncounterProgressionPolicy(int schema, Set<String> expeditionDimensions,
		boolean allowApotheosisWorldBosses, int maxL2Level, int maxTraitRanks, int maxApotheosisTier,
		int l2PercentPerLevel, int traitPercentPerRank, int apotheosisPercentPerTier,
		int elitePercent, int invaderPercent, long maxKillExperience) {
	public EncounterProgressionPolicy {
		if (schema != 1 || expeditionDimensions == null || expeditionDimensions.size() > 128) {
			throw new IllegalArgumentException("Invalid encounter progression schema or dimensions");
		}
		expeditionDimensions = Set.copyOf(expeditionDimensions);
		for (String id : expeditionDimensions) {
			if (id.length() > 256 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
				throw new IllegalArgumentException("Invalid expedition dimension: " + id);
			}
		}
		if (maxL2Level < 0 || maxL2Level > 1000 || maxTraitRanks < 0 || maxTraitRanks > 1000
				|| maxApotheosisTier < 0 || maxApotheosisTier > 4 || maxKillExperience < 1 || maxKillExperience > 50_000) {
			throw new IllegalArgumentException("Encounter progression limits exceed supported bounds");
		}
		for (int percent : new int[]{l2PercentPerLevel, traitPercentPerRank, apotheosisPercentPerTier, elitePercent, invaderPercent}) {
			if (percent < 0 || percent > 100) {
				throw new IllegalArgumentException("Threat reward percentages must be between 0 and 100");
			}
		}
	}

	public static EncounterProgressionPolicy defaults() {
		return new EncounterProgressionPolicy(1, Set.of(), true, 100, 20, 4, 1, 3, 20, 25, 100, 50_000);
	}

	public boolean eligible(String dimension, boolean encounterMarked, boolean worldBossMarked,
			boolean farmSpawn, EncounterThreat threat) {
		// Explicit server markers support authored encounters, including controlled trial spawners.
		if (encounterMarked || worldBossMarked) {
			return true;
		}
		if (farmSpawn || threat.providerSummon()) {
			return false;
		}
		return expeditionDimensions.contains(dimension) || (allowApotheosisWorldBosses && threat.apotheosisInvader());
	}

	public long reward(int originalExperience, EncounterThreat threat) {
		long base = ArpgCharacter.killExperience(originalExperience);
		long bonus = (long) clamp(threat.l2Level(), maxL2Level) * l2PercentPerLevel
				+ (long) clamp(threat.l2TraitRanks(), maxTraitRanks) * traitPercentPerRank
				+ (long) clamp(threat.apotheosisTier(), maxApotheosisTier) * apotheosisPercentPerTier;
		// Invaders receive one rank bonus; elite and invader bonuses never stack.
		bonus += threat.apotheosisInvader() ? invaderPercent : threat.apotheosisElite() ? elitePercent : 0;
		return Math.min(maxKillExperience, base * (100L + bonus) / 100L);
	}

	private static int clamp(int value, int maximum) {
		return Math.max(0, Math.min(maximum, value));
	}
}
