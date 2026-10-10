package net.puffish.skillsmod.arpg.combat;

import java.util.UUID;

/** Target-locked, interruptible telegraph; provider spells own the released effect. */
public final class EnemySpellAction {
	public static final int WINDUP = 30;
	public static final int COOLDOWN = 200;
	private int cooldown = 100;
	private int windup;
	private UUID target;

	public Event tick(UUID currentTarget, boolean eligible) {
		if (cooldown > 0) {
			cooldown--;
		}
		if (windup > 0) {
			if (!eligible || !target.equals(currentTarget)) {
				windup = 0;
				target = null;
				return Event.CANCEL;
			}
			if (--windup == 0) {
				target = null;
				return Event.RELEASE;
			}
			return Event.WINDUP;
		}
		if (cooldown == 0 && eligible && currentTarget != null) {
			cooldown = COOLDOWN;
			windup = WINDUP;
			target = currentTarget;
			return Event.START;
		}
		return Event.NONE;
	}

	public boolean casting() {
		return windup > 0;
	}

	public int cooldown() {
		return cooldown;
	}

	/** Unloaded time does not expire the cooldown; unfinished casts never resume. */
	public void restore(int savedCooldown) {
		cooldown = Math.max(0, Math.min(COOLDOWN, savedCooldown));
		windup = 0;
		target = null;
	}

	public enum Event {
		NONE, START, WINDUP, RELEASE, CANCEL
	}
}
