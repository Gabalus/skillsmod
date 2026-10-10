package net.puffish.skillsmod.arpg.combat;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

/** Bounded non-damaging statuses and reactions, independent of provider animations or effects. */
public record ElementalState(int wetTicks, int chillStacks, int chillTicks, int freezeTicks,
		int freezeRecoveryTicks, UUID coldOwner, String coldSkill) {
	public static final int MAX_DURATION = 1200;
	public static final int WET_DURATION = 100;
	public static final int FREEZE_DURATION = 30;
	public static final int FREEZE_RECOVERY = 200;

	public ElementalState {
		if (wetTicks < 0 || wetTicks > MAX_DURATION || chillStacks < 0 || chillStacks > 2
				|| chillTicks < 0 || chillTicks > MAX_DURATION || (chillStacks == 0) != (chillTicks == 0)
				|| freezeTicks < 0 || freezeTicks > FREEZE_DURATION || freezeRecoveryTicks < freezeTicks
				|| freezeRecoveryTicks > FREEZE_RECOVERY || freezeTicks > 0 && chillStacks > 0
				|| coldSkill == null || coldSkill.length() > 128
				|| (chillStacks > 0 || freezeRecoveryTicks > 0) && coldOwner == null) {
			throw new IllegalArgumentException("Invalid elemental state");
		}
	}

	public static ElementalState empty() {
		return new ElementalState(0, 0, 0, 0, 0, null, "");
	}

	public boolean active() {
		return wetTicks > 0 || chillStacks > 0 || freezeRecoveryTicks > 0;
	}

	public ElementalState water() {
		return new ElementalState(Math.max(wetTicks, WET_DURATION), chillStacks, chillTicks, freezeTicks,
				freezeRecoveryTicks, coldOwner, coldSkill);
	}

	public ElementalState cold(UUID owner, String skill, int duration, boolean canFreeze) {
		if (owner == null || skill == null || skill.length() > 128 || duration < 1 || duration > MAX_DURATION) {
			throw new IllegalArgumentException("Invalid cold application");
		}
		if (freezeTicks > 0) {
			return this;
		}
		int buildup = chillStacks + (wetTicks > 0 ? 2 : 1);
		if (buildup >= 3 && canFreeze && freezeRecoveryTicks == 0) {
			return new ElementalState(wetTicks, 0, 0, FREEZE_DURATION, FREEZE_RECOVERY, owner, skill);
		}
		return new ElementalState(wetTicks, Math.min(2, buildup), Math.max(chillTicks, duration), 0,
				freezeRecoveryTicks, owner, skill);
	}

	/** Fire thaws ARPG cold and evaporates residual wetness, preserving freeze recovery. */
	public ElementalState fire() {
		return new ElementalState(0, 0, 0, 0, freezeRecoveryTicks,
				freezeRecoveryTicks > 0 ? coldOwner : null, freezeRecoveryTicks > 0 ? coldSkill : "");
	}

	public ElementalState tick(boolean touchingWater) {
		int wet = touchingWater ? Math.max(wetTicks - 1, WET_DURATION) : Math.max(0, wetTicks - 1);
		int chill = Math.max(0, chillTicks - 1);
		int frozen = Math.max(0, freezeTicks - 1);
		int recovery = Math.max(0, freezeRecoveryTicks - 1);
		boolean attributed = chill > 0 || recovery > 0;
		return new ElementalState(wet, chill > 0 ? chillStacks : 0, chill, frozen, recovery,
				attributed ? coldOwner : null, attributed ? coldSkill : "");
	}

	public double movementMultiplier() {
		return freezeTicks > 0 ? 0 : 1 - .15 * chillStacks;
	}

	public Set<String> targetTags() {
		var tags = new HashSet<String>();
		if (wetTicks > 0) {
			tags.add("target_wet");
		}
		if (chillStacks > 0) {
			tags.add("target_chilled");
		}
		if (freezeTicks > 0) {
			tags.add("target_frozen");
		}
		return Set.copyOf(tags);
	}
}
