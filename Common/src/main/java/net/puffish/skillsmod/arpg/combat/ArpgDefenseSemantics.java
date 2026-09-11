package net.puffish.skillsmod.arpg.combat;

import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatModifier;
import net.puffish.skillsmod.arpg.stat.ArpgStatSnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Pure, deterministic defense rules shared by loader event adapters and tests. */
public final class ArpgDefenseSemantics {
	private ArpgDefenseSemantics() {
	}

	public enum Outcome {
		HIT,
		DODGE,
		BLOCK
	}

	/** ARPG chance stats are stored as fractions: 0.25 means 25%. */
	public static double chance(double value) {
		if (!Double.isFinite(value)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, value));
	}

	public static boolean succeeds(double rawChance, double roll) {
		if (!Double.isFinite(roll) || roll < 0.0 || roll >= 1.0) {
			return false;
		}
		return roll < chance(rawChance);
	}

	/** Attack avoidance resolves Evasion first, then generic attack Block. */
	public static Outcome resolveAttack(ArpgStatSnapshot snapshot, double evasionRoll, double blockRoll) {
		if (succeeds(snapshot.apply(ArpgStat.EVASION, 0.0), evasionRoll)) {
			return Outcome.DODGE;
		}
		if (succeeds(snapshot.apply(ArpgStat.BLOCK_CHANCE, 0.0), blockRoll)) {
			return Outcome.BLOCK;
		}
		return Outcome.HIT;
	}

	/** Spell defense is intentionally separate: Evasion never avoids a spell. */
	public static Outcome resolveSpell(ArpgStatSnapshot snapshot, double spellBlockRoll) {
		return succeeds(snapshot.apply(ArpgStat.SPELL_BLOCK, 0.0), spellBlockRoll)
				? Outcome.BLOCK
				: Outcome.HIT;
	}

	/**
	 * Iron Fortress converts every Evasion modifier into the same Armor modifier.
	 * This makes Evasion compile to zero while preserving flat/increased/more semantics on Armor.
	 */
	public static List<ArpgStatModifier> applyIronFortress(
			Collection<ArpgStatModifier> modifiers,
			boolean enabled
	) {
		if (!enabled) {
			return List.copyOf(modifiers);
		}
		var converted = new ArrayList<ArpgStatModifier>(modifiers.size());
		for (var modifier : modifiers) {
			converted.add(modifier.stat() == ArpgStat.EVASION
					? new ArpgStatModifier(ArpgStat.ARMOR, modifier.operation(), modifier.value())
					: modifier);
		}
		return List.copyOf(converted);
	}
}
