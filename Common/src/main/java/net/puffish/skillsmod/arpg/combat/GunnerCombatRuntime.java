package net.puffish.skillsmod.arpg.combat;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Authoritative Gunner bridge shared by NeoForge and optional firearm providers.
 * Persistent meters live in {@link ArpgCombatRuntime}; reload timing remains transient.
 */
public final class GunnerCombatRuntime {
	private static final Map<UUID, GunnerCombatWindow> WINDOWS = new HashMap<>();

	private GunnerCombatRuntime() {
	}

	/**
	 * Attempts to spend ammunition and build heat for a provider-owned shot.
	 * Providers must call this before spawning a projectile or resolving a hitscan attack.
	 */
	public static FireAttempt fire(
			ServerPlayerEntity player,
			GunnerCombatSemantics.ShotInput input
	) {
		var state = ArpgCombatRuntime.state(player);
		if (state.pillar() != CombatPillar.GUNNER) {
			return new FireAttempt(FireOutcome.WRONG_PILLAR, state, 1.0);
		}

		long now = now(player);
		var window = window(player, now).observeHeat(state.require(CombatResource.HEAT));
		if (window.reloading()) {
			WINDOWS.put(player.getUuid(), window);
			return new FireAttempt(FireOutcome.RELOADING, state, 1.0);
		}
		if (window.locked(now)) {
			WINDOWS.put(player.getUuid(), window);
			return new FireAttempt(FireOutcome.JAMMED, state, 1.0);
		}
		if (window.overheated()) {
			WINDOWS.put(player.getUuid(), window);
			return new FireAttempt(FireOutcome.OVERHEATED, state, 1.0);
		}

		var result = GunnerCombatSemantics.tryFire(state, input);
		if (result.outcome() == GunnerCombatSemantics.FireOutcome.EMPTY) {
			WINDOWS.put(player.getUuid(), window);
			return new FireAttempt(FireOutcome.EMPTY, state, 1.0);
		}
		if (result.outcome() == GunnerCombatSemantics.FireOutcome.OVERHEATED) {
			WINDOWS.put(player.getUuid(), window.observeHeat(state.require(CombatResource.HEAT)));
			return new FireAttempt(FireOutcome.OVERHEATED, state, 1.0);
		}

		var next = ArpgCombatRuntime.update(player, ignored -> result.state());
		window = window.markCombat(now).observeHeat(next.require(CombatResource.HEAT));
		WINDOWS.put(player.getUuid(), window);
		return new FireAttempt(FireOutcome.FIRED, next, result.damageMultiplier());
	}

	public static StartReloadOutcome startReload(ServerPlayerEntity player, int durationTicks) {
		var state = ArpgCombatRuntime.state(player);
		if (state.pillar() != CombatPillar.GUNNER) {
			return StartReloadOutcome.WRONG_PILLAR;
		}
		if (state.require(CombatResource.AMMUNITION).isFull()) {
			return StartReloadOutcome.FULL;
		}

		long now = now(player);
		var current = window(player, now).observeHeat(state.require(CombatResource.HEAT));
		var result = current.startReload(now, durationTicks);
		WINDOWS.put(player.getUuid(), result.window());
		return result.applied() ? StartReloadOutcome.STARTED : StartReloadOutcome.BUSY;
	}

	public static ActiveReloadOutcome attemptActiveReload(ServerPlayerEntity player) {
		var state = ArpgCombatRuntime.state(player);
		if (state.pillar() != CombatPillar.GUNNER) {
			return ActiveReloadOutcome.WRONG_PILLAR;
		}

		long now = now(player);
		var result = window(player, now).attemptActiveReload(now);
		var nextWindow = result.window();
		if (result.outcome() == GunnerCombatWindow.ActiveReloadOutcome.PERFECT) {
			var next = ArpgCombatRuntime.update(
					player,
					current -> GunnerCombatSemantics.reload(
							current, GunnerCombatSemantics.ReloadQuality.PERFECT)
			);
			nextWindow = nextWindow.observeHeat(next.require(CombatResource.HEAT));
			WINDOWS.put(player.getUuid(), nextWindow);
			return ActiveReloadOutcome.PERFECT;
		}
		WINDOWS.put(player.getUuid(), nextWindow);
		return result.outcome() == GunnerCombatWindow.ActiveReloadOutcome.JAMMED
				? ActiveReloadOutcome.JAMMED
				: ActiveReloadOutcome.NOT_RELOADING;
	}

	/** Applies passive cooling, momentum decay and unattended reload completion. */
	public static TickResult tick(ServerPlayerEntity player) {
		var state = ArpgCombatRuntime.state(player);
		if (state.pillar() != CombatPillar.GUNNER) {
			WINDOWS.remove(player.getUuid());
			return TickResult.NONE;
		}

		long now = now(player);
		var window = window(player, now).observeHeat(state.require(CombatResource.HEAT));
		boolean wasOverheated = window.overheated();
		boolean completedReload = false;

		if (window.automaticReloadReady(now)) {
			state = ArpgCombatRuntime.update(
					player,
					current -> GunnerCombatSemantics.reload(
							current, GunnerCombatSemantics.ReloadQuality.STANDARD)
			);
			window = window.finishReload().observeHeat(state.require(CombatResource.HEAT));
			completedReload = true;
		}

		if (window.recoveryDue(now)) {
			double seconds = window.recoverySeconds(now);
			if (seconds > 0.0) {
				boolean inCombat = window.inCombat(now);
				state = ArpgCombatRuntime.update(
						player,
						current -> GunnerCombatSemantics.recover(current, seconds, inCombat)
				);
			}
			window = window.markRecovery(now).observeHeat(state.require(CombatResource.HEAT));
		}

		WINDOWS.put(player.getUuid(), window);
		return new TickResult(completedReload, wasOverheated && !window.overheated());
	}

	public static void registerProjectileHit(
			ServerPlayerEntity player,
			double damage,
			boolean closeRange
	) {
		var state = ArpgCombatRuntime.state(player);
		if (state.pillar() != CombatPillar.GUNNER) {
			return;
		}
		long now = now(player);
		ArpgCombatRuntime.update(
				player,
				current -> GunnerCombatSemantics.registerHit(current, damage, closeRange)
		);
		WINDOWS.put(player.getUuid(), window(player, now).markCombat(now));
	}

	public static void registerProjectileKill(ServerPlayerEntity player) {
		var state = ArpgCombatRuntime.state(player);
		if (state.pillar() != CombatPillar.GUNNER) {
			return;
		}
		long now = now(player);
		ArpgCombatRuntime.update(player, GunnerCombatSemantics::registerKill);
		WINDOWS.put(player.getUuid(), window(player, now).markCombat(now));
	}

	public static double damageMultiplier(ServerPlayerEntity player) {
		var state = ArpgCombatRuntime.state(player);
		return state.pillar() == CombatPillar.GUNNER
				? GunnerCombatSemantics.damageMultiplier(state)
				: 1.0;
	}

	public static void clear(ServerPlayerEntity player) {
		WINDOWS.remove(player.getUuid());
	}

	private static long now(ServerPlayerEntity player) {
		return player.getServerWorld().getTime();
	}

	private static GunnerCombatWindow window(ServerPlayerEntity player, long now) {
		return WINDOWS.computeIfAbsent(player.getUuid(), ignored -> GunnerCombatWindow.idle(now));
	}

	public enum FireOutcome {
		FIRED,
		EMPTY,
		OVERHEATED,
		RELOADING,
		JAMMED,
		WRONG_PILLAR
	}

	public enum StartReloadOutcome {
		STARTED,
		FULL,
		BUSY,
		WRONG_PILLAR
	}

	public enum ActiveReloadOutcome {
		PERFECT,
		JAMMED,
		NOT_RELOADING,
		WRONG_PILLAR
	}

	public record FireAttempt(FireOutcome outcome, CombatState state, double damageMultiplier) {
		public boolean fired() {
			return outcome == FireOutcome.FIRED;
		}
	}

	public record TickResult(boolean completedReload, boolean overheatCleared) {
		private static final TickResult NONE = new TickResult(false, false);
	}
}
