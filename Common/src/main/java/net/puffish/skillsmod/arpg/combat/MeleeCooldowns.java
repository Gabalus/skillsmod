package net.puffish.skillsmod.arpg.combat;

/** Saved per-character absolute overworld game ticks, independent of equipment and provider containers. */
public record MeleeCooldowns(long heavy, long driving, long commitment) {
	public MeleeCooldowns {
		if (heavy < 0 || driving < 0 || commitment < 0) {
			throw new IllegalArgumentException("Negative melee cooldown");
		}
	}

	public static MeleeCooldowns empty() {
		return new MeleeCooldowns(0, 0, 0);
	}

	public MeleeCooldowns normalized(long now) {
		if (now < 0) {
			throw new IllegalArgumentException("Negative game time");
		}
		return new MeleeCooldowns(Math.min(heavy, deadline(now, MeleeKit.Attack.HEAVY.cooldown())),
				Math.min(driving, deadline(now, MeleeKit.Attack.DRIVING.cooldown())),
				Math.min(commitment, deadline(now, MeleeKit.COMMITMENT_TICKS)));
	}

	public int remaining(MeleeKit.Attack attack, long now) {
		if (now < 0) {
			throw new IllegalArgumentException("Negative game time");
		}
		long ready = Math.max(attack == MeleeKit.Attack.HEAVY ? heavy : driving, commitment);
		return ready <= now ? 0 : (int) Math.min(Integer.MAX_VALUE, ready - now);
	}

	public MeleeCooldowns used(MeleeKit.Attack attack, long now) {
		if (remaining(attack, now) != 0) {
			throw new IllegalStateException("Melee skill is still recovering");
		}
		return new MeleeCooldowns(attack == MeleeKit.Attack.HEAVY ? deadline(now, attack.cooldown()) : heavy,
				attack == MeleeKit.Attack.DRIVING ? deadline(now, attack.cooldown()) : driving,
				deadline(now, MeleeKit.COMMITMENT_TICKS));
	}

	private static long deadline(long now, int ticks) {
		return now > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : now + ticks;
	}
}
