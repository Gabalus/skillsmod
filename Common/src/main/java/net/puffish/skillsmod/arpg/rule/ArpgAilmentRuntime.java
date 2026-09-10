package net.puffish.skillsmod.arpg.rule;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatSnapshot;

import java.util.Map;
import java.util.WeakHashMap;

/** Server-authoritative application state for ARPG ailments. */
public final class ArpgAilmentRuntime {
	private static final int BLEED_TICK_INTERVAL = 20;
	private static final double MOVING_BLEED_MULTIPLIER = 2.0;
	private static final double EPSILON = 0.0001;
	private static final Map<LivingEntity, Bleed> bleeds = new WeakHashMap<>();

	private ArpgAilmentRuntime() {
	}

	public static synchronized boolean apply(
			ServerPlayerEntity player,
			LivingEntity target,
			ArpgRuleEngine.Trigger trigger,
			ArpgStatSnapshot snapshot
	) {
		if (!target.isAlive()) {
			return false;
		}
		var chanceStat = switch (trigger.action()) {
			case IGNITE -> ArpgStat.IGNITE_CHANCE;
			case BLEED -> ArpgStat.BLEED_CHANCE;
			case POISON -> ArpgStat.POISON_CHANCE;
			default -> null;
		};
		if (chanceStat == null) {
			return false;
		}
		double chance = Math.max(0.0, Math.min(1.0,
				Math.max(0.0, trigger.value()) + snapshot.apply(chanceStat, 0.0)));
		if (chance <= 0.0 || player.getRandom().nextDouble() >= chance) {
			return false;
		}

		int duration = scaledDuration(trigger.duration(), snapshot);
		return switch (trigger.action()) {
			case IGNITE -> ignite(target, duration);
			case BLEED -> bleed(target, duration, snapshot, player.getServerWorld().getTime());
			case POISON -> poison(player, target, duration);
			default -> false;
		};
	}

	public static synchronized boolean isBleeding(LivingEntity target) {
		return bleeds.containsKey(target);
	}

	/** Ticks custom Bleed without an attacker source so DoT damage cannot recursively proc on-hit rules. */
	public static synchronized void tick(LivingEntity target, long now) {
		var bleed = bleeds.get(target);
		if (bleed == null) {
			return;
		}
		if (!target.isAlive() || bleed.expiresAt() <= now) {
			bleeds.remove(target);
			return;
		}
		if (bleed.nextTickAt() > now) {
			return;
		}

		var velocity = target.getVelocity();
		boolean moving = velocity.x * velocity.x + velocity.z * velocity.z > 0.0025;
		double amount = bleed.damagePerTick() * (moving ? MOVING_BLEED_MULTIPLIER : 1.0);
		if (Double.isFinite(amount) && amount > EPSILON) {
			target.damage(target.getDamageSources().magic(), (float) amount);
		}
		bleeds.put(target, new Bleed(
				bleed.expiresAt(),
				bleed.damagePerTick(),
				now + BLEED_TICK_INTERVAL
		));
	}

	public static synchronized void clear(LivingEntity target) {
		bleeds.remove(target);
	}

	private static boolean ignite(LivingEntity target, int duration) {
		int before = target.getFireTicks();
		int after = Math.max(before, duration);
		if (after <= before) {
			return false;
		}
		target.setFireTicks(after);
		return true;
	}

	private static boolean poison(ServerPlayerEntity player, LivingEntity target, int duration) {
		return target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, duration, 0), player);
	}

	private static boolean bleed(LivingEntity target, int duration, ArpgStatSnapshot snapshot, long now) {
		double damage = snapshot.apply(ArpgStat.BLEED_DAMAGE, 1.0);
		damage = snapshot.apply(ArpgStat.DAMAGE_OVER_TIME, damage);
		if (!Double.isFinite(damage) || damage <= EPSILON) {
			return false;
		}
		long expiresAt = now + duration;
		var existing = bleeds.get(target);
		if (existing != null && existing.expiresAt() > now) {
			double strongest = Math.max(existing.damagePerTick(), damage);
			long longest = Math.max(existing.expiresAt(), expiresAt);
			if (strongest <= existing.damagePerTick() + EPSILON && longest == existing.expiresAt()) {
				return false;
			}
			bleeds.put(target, new Bleed(longest, strongest, existing.nextTickAt()));
			return true;
		}
		bleeds.put(target, new Bleed(expiresAt, damage, now + BLEED_TICK_INTERVAL));
		return true;
	}

	private static int scaledDuration(int baseDuration, ArpgStatSnapshot snapshot) {
		double scaled = snapshot.apply(ArpgStat.AILMENT_DURATION, Math.max(1, baseDuration));
		if (!Double.isFinite(scaled)) {
			return Math.max(1, baseDuration);
		}
		return (int) Math.max(1, Math.min(20 * 60, Math.round(scaled)));
	}

	private record Bleed(long expiresAt, double damagePerTick, long nextTickAt) {
	}
}
