package net.puffish.skillsmod.arpg.combat;

/** Pure, provider-independent stamina, guard and posture rules for the Martial pillar. */
public final class MartialCombatSemantics {
	public static final int PERFECT_COUNTER_WINDOW_TICKS = 12;
	public static final double PERFECT_COUNTER_DAMAGE_MULTIPLIER = 1.5;
	private static final double PERFECT_STAMINA_MULTIPLIER = 0.15;
	private static final double PERFECT_POSTURE_MULTIPLIER = 0.10;
	private static final double HELD_POSTURE_MULTIPLIER = 0.65;
	private static final double HIT_POSTURE_MULTIPLIER = 0.35;
	private static final double BROKEN_GUARD_DAMAGE_MULTIPLIER = 0.50;

	private MartialCombatSemantics() {
	}

	public static GuardResult resolveGuard(CombatState state, GuardTiming timing, GuardInput input) {
		requireMartial(state);
		if (timing == null || input == null) {
			throw new IllegalArgumentException("Guard timing and input cannot be null");
		}

		var stamina = state.require(CombatResource.STAMINA);
		var posture = state.require(CombatResource.POSTURE);
		var momentum = state.require(CombatResource.MOMENTUM);
		double staminaCost = input.postureDamage() * input.staminaCostFactor();
		double postureAdded;
		double healthDamage;
		int counterWindow = 0;
		Outcome outcome;

		if (timing == GuardTiming.PERFECT) {
			staminaCost *= PERFECT_STAMINA_MULTIPLIER;
			postureAdded = input.postureDamage() * PERFECT_POSTURE_MULTIPLIER;
			healthDamage = 0.0;
			counterWindow = PERFECT_COUNTER_WINDOW_TICKS;
			outcome = Outcome.PERFECT_PARRY;
		} else if (timing == GuardTiming.HELD) {
			postureAdded = input.postureDamage() * HELD_POSTURE_MULTIPLIER;
			healthDamage = input.healthDamage() * (1.0 - input.guardEfficiency());
			outcome = Outcome.BLOCKED;
		} else {
			staminaCost = 0.0;
			postureAdded = input.postureDamage() * HIT_POSTURE_MULTIPLIER;
			healthDamage = input.healthDamage();
			outcome = Outcome.HIT;
		}

		CombatState next = state;
		double staminaSpent = 0.0;
		if (staminaCost > 0.0) {
			var spend = next.spend(CombatResource.STAMINA, staminaCost);
			if (!spend.applied()) {
				staminaSpent = stamina.current();
				next = next.withPool(CombatResource.STAMINA, ResourcePool.empty(stamina.maximum()));
				postureAdded = posture.maximum() - posture.current();
				healthDamage = Math.max(healthDamage,
						input.healthDamage() * BROKEN_GUARD_DAMAGE_MULTIPLIER);
				outcome = Outcome.GUARD_BROKEN;
			} else {
				staminaSpent = spend.amount();
				next = spend.state();
			}
		}

		double postureBefore = next.require(CombatResource.POSTURE).current();
		next = next.gain(CombatResource.POSTURE, postureAdded);
		postureAdded = next.require(CombatResource.POSTURE).current() - postureBefore;
		if (outcome != Outcome.GUARD_BROKEN && next.require(CombatResource.POSTURE).isFull()) {
			outcome = Outcome.GUARD_BROKEN;
			healthDamage = Math.max(healthDamage,
					input.healthDamage() * BROKEN_GUARD_DAMAGE_MULTIPLIER);
			counterWindow = 0;
		}
		if (outcome == Outcome.PERFECT_PARRY) {
			double reward = Math.min(momentum.maximum(), input.postureDamage() * 0.25);
			next = next.gain(CombatResource.MOMENTUM, reward);
		}

		return new GuardResult(next, outcome, healthDamage, postureAdded, staminaSpent, counterWindow);
	}

	public static CombatState recover(CombatState state, double seconds, boolean inCombat) {
		requireMartial(state);
		if (!Double.isFinite(seconds) || seconds < 0.0) {
			throw new IllegalArgumentException("Recovery time must be finite and nonnegative");
		}
		double staminaPerSecond = inCombat ? 8.0 : 18.0;
		double posturePerSecond = inCombat ? 6.0 : 22.0;
		double momentumPerSecond = inCombat ? 3.0 : 10.0;
		return state
				.gain(CombatResource.STAMINA, staminaPerSecond * seconds)
				.reduce(CombatResource.POSTURE, posturePerSecond * seconds)
				.reduce(CombatResource.MOMENTUM, momentumPerSecond * seconds);
	}

	private static void requireMartial(CombatState state) {
		if (state == null || state.pillar() != CombatPillar.MARTIAL) {
			throw new IllegalArgumentException("Martial combat semantics require a Martial combat state");
		}
	}

	public enum GuardTiming {
		MISSED,
		HELD,
		PERFECT
	}

	public enum Outcome {
		HIT,
		BLOCKED,
		PERFECT_PARRY,
		GUARD_BROKEN
	}

	public record GuardInput(
			double healthDamage,
			double postureDamage,
			double guardEfficiency,
			double staminaCostFactor
	) {
		public GuardInput {
			if (!Double.isFinite(healthDamage) || healthDamage < 0.0
					|| !Double.isFinite(postureDamage) || postureDamage < 0.0
					|| !Double.isFinite(guardEfficiency) || guardEfficiency < 0.0 || guardEfficiency > 1.0
					|| !Double.isFinite(staminaCostFactor) || staminaCostFactor < 0.0) {
				throw new IllegalArgumentException("Guard input values are outside their legal ranges");
			}
		}
	}

	public record GuardResult(
			CombatState state,
			Outcome outcome,
			double healthDamage,
			double postureAdded,
			double staminaSpent,
			int counterWindowTicks
	) {
	}
}
