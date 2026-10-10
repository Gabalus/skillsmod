package net.puffish.skillsmod.arpg.progression;

/** Server-observed threat, never a client-submitted reward multiplier. */
public record EncounterThreat(int l2Level, int l2TraitRanks, int apotheosisTier,
		boolean apotheosisElite, boolean apotheosisInvader, boolean providerSummon) {
	public static final EncounterThreat NONE = new EncounterThreat(0, 0, 0, false, false, false);
}
