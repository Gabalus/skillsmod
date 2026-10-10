package net.puffish.skillsmod.arpg.combat;

/**
 * Immutable transient timing state for one Martial player. Persistent meters remain in
 * {@link CombatState}; this window only tracks short-lived server tick boundaries.
 */
public record MartialCombatWindow(
		long guardStartedTick,
		long combatUntilTick,
		long counterUntilTick,
		long lastRecoveryTick
) {
	public static final int PERFECT_GUARD_WINDOW_TICKS = 4;
	public static final int COMBAT_MEMORY_TICKS = 100;
	public static final int RECOVERY_INTERVAL_TICKS = 10;
	private static final long INACTIVE = -1L;

	public MartialCombatWindow {
		if (guardStartedTick < INACTIVE || combatUntilTick < INACTIVE
				|| counterUntilTick < INACTIVE || lastRecoveryTick < 0L) {
			throw new IllegalArgumentException("Martial combat window contains an invalid tick");
		}
	}

	public static MartialCombatWindow idle(long now) {
		validateTick(now);
		return new MartialCombatWindow(INACTIVE, INACTIVE, INACTIVE, now);
	}

	public MartialCombatWindow observeGuard(long now, boolean guarding) {
		validateTick(now);
		long started = guardStartedTick;
		if (!guarding) {
			started = INACTIVE;
		} else if (started == INACTIVE || now < started) {
			started = now;
		}
		if (started == guardStartedTick) {
			return this;
		}
		return new MartialCombatWindow(started, combatUntilTick, counterUntilTick, lastRecoveryTick);
	}

	public MartialCombatSemantics.GuardTiming timing(long now, boolean blockAccepted) {
		validateTick(now);
		if (!blockAccepted) {
			return MartialCombatSemantics.GuardTiming.MISSED;
		}
		long started = guardStartedTick == INACTIVE ? now : guardStartedTick;
		return now - started < PERFECT_GUARD_WINDOW_TICKS
				? MartialCombatSemantics.GuardTiming.PERFECT
				: MartialCombatSemantics.GuardTiming.HELD;
	}

	public MartialCombatWindow afterGuard(long now, MartialCombatSemantics.GuardResult result) {
		validateTick(now);
		if (result == null) {
			throw new IllegalArgumentException("Guard result cannot be null");
		}
		long combatUntil = Math.max(combatUntilTick, now + COMBAT_MEMORY_TICKS);
		long counterUntil = result.counterWindowTicks() > 0
				? now + result.counterWindowTicks()
				: INACTIVE;
		return new MartialCombatWindow(guardStartedTick, combatUntil, counterUntil, lastRecoveryTick);
	}

	public boolean inCombat(long now) {
		validateTick(now);
		return combatUntilTick >= now;
	}

	public boolean counterReady(long now) {
		validateTick(now);
		return counterUntilTick >= now;
	}

	public CounterUse consumeCounter(long now) {
		validateTick(now);
		if (!counterReady(now)) {
			return new CounterUse(false, this);
		}
		return new CounterUse(true, new MartialCombatWindow(
				guardStartedTick,
				combatUntilTick,
				INACTIVE,
				lastRecoveryTick
		));
	}

	public boolean recoveryDue(long now) {
		validateTick(now);
		return now < lastRecoveryTick || now - lastRecoveryTick >= RECOVERY_INTERVAL_TICKS;
	}

	public double recoverySeconds(long now) {
		validateTick(now);
		return now <= lastRecoveryTick ? 0.0 : (now - lastRecoveryTick) / 20.0;
	}

	public MartialCombatWindow markRecovery(long now) {
		validateTick(now);
		return new MartialCombatWindow(guardStartedTick, combatUntilTick, counterUntilTick, now);
	}

	private static void validateTick(long tick) {
		if (tick < 0L) {
			throw new IllegalArgumentException("Server tick cannot be negative");
		}
	}

	public record CounterUse(boolean applied, MartialCombatWindow window) {
	}
}
