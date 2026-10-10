package net.puffish.skillsmod.arpg.combat;

/** Pure ammunition, heat and aggression rules for the Gunner pillar. */
public final class GunnerCombatSemantics {
	public static final int DEFAULT_RELOAD_TICKS = 40;
	public static final double MAX_MOMENTUM_DAMAGE_BONUS = 0.25;
	private static final double PERFECT_RELOAD_HEAT_VENT = 40.0;
	private static final double STANDARD_RELOAD_HEAT_VENT = 20.0;
	private static final double PERFECT_RELOAD_MOMENTUM = 20.0;
	private static final double KILL_MOMENTUM = 20.0;
	private static final double KILL_AMMUNITION_REFUND = 2.0;

	private GunnerCombatSemantics() {
	}

	public static FireResult tryFire(CombatState state, ShotInput input) {
		requireGunner(state);
		if (input == null) {
			throw new IllegalArgumentException("Shot input cannot be null");
		}
		if (state.require(CombatResource.HEAT).isFull()) {
			return new FireResult(FireOutcome.OVERHEATED, state, 1.0, true);
		}

		var ammunition = state.spend(CombatResource.AMMUNITION, input.ammunitionCost());
		if (!ammunition.applied()) {
			return new FireResult(FireOutcome.EMPTY, state, 1.0, false);
		}

		double multiplier = damageMultiplier(state);
		var next = ammunition.state().gain(CombatResource.HEAT, input.heat());
		return new FireResult(
				FireOutcome.FIRED,
				next,
				multiplier,
				next.require(CombatResource.HEAT).isFull()
		);
	}

	public static CombatState reload(CombatState state, ReloadQuality quality) {
		requireGunner(state);
		if (quality == null) {
			throw new IllegalArgumentException("Reload quality cannot be null");
		}
		var ammunition = state.require(CombatResource.AMMUNITION);
		var next = state.withPool(CombatResource.AMMUNITION, ResourcePool.full(ammunition.maximum()));
		if (quality == ReloadQuality.PERFECT) {
			return next
					.reduce(CombatResource.HEAT, PERFECT_RELOAD_HEAT_VENT)
					.gain(CombatResource.MOMENTUM, PERFECT_RELOAD_MOMENTUM);
		}
		return next.reduce(CombatResource.HEAT, STANDARD_RELOAD_HEAT_VENT);
	}

	public static CombatState registerHit(CombatState state, double damage, boolean closeRange) {
		requireGunner(state);
		validateNonnegative(damage, "Projectile damage");
		double gain = Math.min(15.0, damage * 0.6) * (closeRange ? 1.5 : 1.0);
		return state.gain(CombatResource.MOMENTUM, gain);
	}

	public static CombatState registerKill(CombatState state) {
		requireGunner(state);
		return state
				.gain(CombatResource.MOMENTUM, KILL_MOMENTUM)
				.gain(CombatResource.AMMUNITION, KILL_AMMUNITION_REFUND);
	}

	public static CombatState recover(CombatState state, double seconds, boolean inCombat) {
		requireGunner(state);
		validateNonnegative(seconds, "Recovery time");
		double heatPerSecond = inCombat ? 8.0 : 20.0;
		double momentumPerSecond = inCombat ? 2.0 : 12.0;
		return state
				.reduce(CombatResource.HEAT, heatPerSecond * seconds)
				.reduce(CombatResource.MOMENTUM, momentumPerSecond * seconds);
	}

	public static double damageMultiplier(CombatState state) {
		requireGunner(state);
		return 1.0 + MAX_MOMENTUM_DAMAGE_BONUS
				* state.require(CombatResource.MOMENTUM).fraction();
	}

	private static void requireGunner(CombatState state) {
		if (state == null || state.pillar() != CombatPillar.GUNNER) {
			throw new IllegalArgumentException("Gunner combat semantics require a Gunner combat state");
		}
	}

	private static void validateNonnegative(double value, String name) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(name + " must be finite and nonnegative");
		}
	}

	public enum FireOutcome {
		FIRED,
		EMPTY,
		OVERHEATED
	}

	public enum ReloadQuality {
		STANDARD,
		PERFECT
	}

	public record ShotInput(double ammunitionCost, double heat) {
		public ShotInput {
			if (!Double.isFinite(ammunitionCost) || ammunitionCost <= 0.0
					|| !Double.isFinite(heat) || heat < 0.0) {
				throw new IllegalArgumentException("Shot costs must be finite; ammunition must be positive");
			}
		}
	}

	public record FireResult(
			FireOutcome outcome,
			CombatState state,
			double damageMultiplier,
			boolean overheated
	) {
	}
}
