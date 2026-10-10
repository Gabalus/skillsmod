package net.puffish.skillsmod.arpg.combat;

/** Transient server timing for active reloads, jams, overheat and combat recovery. */
public record GunnerCombatWindow(
		long reloadStartedTick,
		long reloadCompleteTick,
		long activeReloadStartTick,
		long activeReloadEndTick,
		long lockedUntilTick,
		long combatUntilTick,
		long lastRecoveryTick,
		boolean overheated
) {
	public static final int ACTIVE_RELOAD_START_PERCENT = 55;
	public static final int ACTIVE_RELOAD_END_PERCENT = 70;
	public static final int JAM_TICKS = 15;
	public static final int COMBAT_MEMORY_TICKS = 100;
	public static final int RECOVERY_INTERVAL_TICKS = 10;
	public static final double OVERHEAT_RELEASE_FRACTION = 0.60;
	private static final long INACTIVE = -1L;

	public GunnerCombatWindow {
		if (reloadStartedTick < INACTIVE || reloadCompleteTick < INACTIVE
				|| activeReloadStartTick < INACTIVE || activeReloadEndTick < INACTIVE
				|| lockedUntilTick < INACTIVE || combatUntilTick < INACTIVE
				|| lastRecoveryTick < 0L) {
			throw new IllegalArgumentException("Gunner combat window contains an invalid tick");
		}
	}

	public static GunnerCombatWindow idle(long now) {
		validateTick(now);
		return new GunnerCombatWindow(
				INACTIVE, INACTIVE, INACTIVE, INACTIVE,
				INACTIVE, INACTIVE, now, false
		);
	}

	public boolean reloading() {
		return reloadStartedTick != INACTIVE;
	}

	public boolean locked(long now) {
		validateTick(now);
		return lockedUntilTick >= now;
	}

	public StartReload startReload(long now, int durationTicks) {
		validateTick(now);
		if (durationTicks <= 0) {
			throw new IllegalArgumentException("Reload duration must be positive");
		}
		if (reloading() || locked(now)) {
			return new StartReload(false, this);
		}
		long complete = now + durationTicks;
		long activeStart = now + Math.max(1L, durationTicks * ACTIVE_RELOAD_START_PERCENT / 100L);
		long activeEnd = now + Math.max(1L, durationTicks * ACTIVE_RELOAD_END_PERCENT / 100L);
		return new StartReload(true, new GunnerCombatWindow(
				now,
				complete,
				activeStart,
				Math.min(complete - 1L, activeEnd),
				lockedUntilTick,
				combatUntilTick,
				lastRecoveryTick,
				false
		));
	}

	public ActiveReload attemptActiveReload(long now) {
		validateTick(now);
		if (!reloading()) {
			return new ActiveReload(ActiveReloadOutcome.NOT_RELOADING, this);
		}
		if (now >= activeReloadStartTick && now <= activeReloadEndTick) {
			return new ActiveReload(ActiveReloadOutcome.PERFECT, finishReload());
		}
		return new ActiveReload(ActiveReloadOutcome.JAMMED, new GunnerCombatWindow(
				INACTIVE, INACTIVE, INACTIVE, INACTIVE,
				now + JAM_TICKS,
				combatUntilTick,
				lastRecoveryTick,
				overheated
		));
	}

	public boolean automaticReloadReady(long now) {
		validateTick(now);
		return reloading() && now >= reloadCompleteTick;
	}

	public GunnerCombatWindow finishReload() {
		return new GunnerCombatWindow(
				INACTIVE, INACTIVE, INACTIVE, INACTIVE,
				lockedUntilTick, combatUntilTick, lastRecoveryTick, overheated
		);
	}

	public GunnerCombatWindow markCombat(long now) {
		validateTick(now);
		return new GunnerCombatWindow(
				reloadStartedTick, reloadCompleteTick, activeReloadStartTick, activeReloadEndTick,
				lockedUntilTick, Math.max(combatUntilTick, now + COMBAT_MEMORY_TICKS),
				lastRecoveryTick, overheated
		);
	}

	public GunnerCombatWindow observeHeat(ResourcePool heat) {
		if (heat == null) {
			throw new IllegalArgumentException("Heat pool cannot be null");
		}
		boolean nextOverheated = overheated;
		if (heat.isFull()) {
			nextOverheated = true;
		} else if (heat.fraction() <= OVERHEAT_RELEASE_FRACTION) {
			nextOverheated = false;
		}
		if (nextOverheated == overheated) {
			return this;
		}
		return new GunnerCombatWindow(
				reloadStartedTick, reloadCompleteTick, activeReloadStartTick, activeReloadEndTick,
				lockedUntilTick, combatUntilTick, lastRecoveryTick, nextOverheated
		);
	}

	public boolean inCombat(long now) {
		validateTick(now);
		return combatUntilTick >= now;
	}

	public boolean recoveryDue(long now) {
		validateTick(now);
		return now < lastRecoveryTick || now - lastRecoveryTick >= RECOVERY_INTERVAL_TICKS;
	}

	public double recoverySeconds(long now) {
		validateTick(now);
		return now <= lastRecoveryTick ? 0.0 : (now - lastRecoveryTick) / 20.0;
	}

	public GunnerCombatWindow markRecovery(long now) {
		validateTick(now);
		return new GunnerCombatWindow(
				reloadStartedTick, reloadCompleteTick, activeReloadStartTick, activeReloadEndTick,
				lockedUntilTick, combatUntilTick, now, overheated
		);
	}

	private static void validateTick(long tick) {
		if (tick < 0L) {
			throw new IllegalArgumentException("Server tick cannot be negative");
		}
	}

	public enum ActiveReloadOutcome {
		NOT_RELOADING,
		PERFECT,
		JAMMED
	}

	public record StartReload(boolean applied, GunnerCombatWindow window) {
	}

	public record ActiveReload(ActiveReloadOutcome outcome, GunnerCombatWindow window) {
	}
}
