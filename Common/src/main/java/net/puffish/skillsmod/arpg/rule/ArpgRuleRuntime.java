package net.puffish.skillsmod.arpg.rule;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.puffish.skillsmod.arpg.data.ArpgData;
import net.puffish.skillsmod.arpg.stat.ArpgPlayerStats;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatCompiler;
import net.puffish.skillsmod.arpg.stat.ArpgStatSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/** Server-side stateful adapter around the pure {@link ArpgRuleEngine}. */
public final class ArpgRuleRuntime {
	private static final double CLOSE_DISTANCE_SQUARED = 36.0;
	private static final double RESOURCE_EPSILON = 0.0001;
	private static final Map<ServerPlayerEntity, Map<String, Long>> triggerCooldowns = new WeakHashMap<>();
	private static final Map<ServerPlayerEntity, Double> ward = new WeakHashMap<>();
	private static volatile ManaProvider manaProvider = player -> Double.NaN;
	private static volatile TriggerActionExecutor externalTriggerExecutor = (player, trigger) -> false;

	private ArpgRuleRuntime() {
	}

	@FunctionalInterface
	public interface ManaProvider {
		double fraction(ServerPlayerEntity player);
	}

	@FunctionalInterface
	public interface TriggerActionExecutor {
		boolean execute(ServerPlayerEntity player, ArpgRuleEngine.Trigger trigger);
	}

	/** Installs optional provider-owned resource semantics without creating a hard provider dependency. */
	public static synchronized void configureExternalRuntime(
			ManaProvider newManaProvider,
			TriggerActionExecutor newTriggerExecutor
	) {
		manaProvider = Objects.requireNonNull(newManaProvider);
		externalTriggerExecutor = Objects.requireNonNull(newTriggerExecutor);
	}

	public static ArpgRuleEngine.Evaluation evaluate(
			ServerPlayerEntity player,
			Entity target,
			ArpgRuleEngine.Event event,
			String skill,
			Set<String> tags
	) {
		return ArpgRuleEngine.evaluate(
				ArpgData.content(),
				ArpgPlayerStats.getRules(player),
				context(player, target, event, skill, tags)
		);
	}

	/** Includes persistent reward modifiers plus currently-active conditional rule modifiers. */
	public static ArpgStatSnapshot snapshot(
			ServerPlayerEntity player,
			Entity target,
			ArpgRuleEngine.Event event,
			String skill,
			Set<String> tags
	) {
		var modifiers = new ArrayList<>(ArpgPlayerStats.getModifiers(player));
		modifiers.addAll(evaluate(player, target, event, skill, tags).modifiers());
		return ArpgStatCompiler.compile(modifiers);
	}

	/** Executes vanilla-owned actions first, then delegates provider-owned actions when available. */
	public static synchronized int fireSupportedTriggers(
			ServerPlayerEntity player,
			Entity target,
			ArpgRuleEngine.Event event,
			String skill,
			Set<String> tags
	) {
		var evaluation = evaluate(player, target, event, skill, tags);
		var cooldowns = triggerCooldowns.computeIfAbsent(player, ignored -> new HashMap<>());
		long now = player.getServerWorld().getTime();
		int executed = 0;

		for (var trigger : evaluation.triggers()) {
			if (cooldowns.getOrDefault(trigger.id(), Long.MIN_VALUE) > now) {
				continue;
			}
			boolean success = executeVanilla(player, target, event, skill, tags, trigger)
					|| executeExternal(player, trigger);
			if (success) {
				cooldowns.put(trigger.id(), now + Math.max(1, trigger.cooldown()));
				executed++;
			}
		}
		return executed;
	}

	/** Consumes current Ward after normal damage mitigation and returns health damage that remains. */
	public static synchronized double absorbWard(
			ServerPlayerEntity player,
			Entity attacker,
			Set<String> tags,
			double incomingDamage
	) {
		double maximum = maximumWard(player, attacker, ArpgRuleEngine.Event.DAMAGE_TAKEN, "", tags);
		double current = ward.getOrDefault(player, 0.0);
		var result = ArpgResourceSemantics.absorbShield(current, maximum, incomingDamage);
		storeWard(player, result.remainingShield());
		return result.remainingDamage();
	}

	public static synchronized double currentWard(ServerPlayerEntity player) {
		return ward.getOrDefault(player, 0.0);
	}

	public static synchronized void clear(ServerPlayerEntity player) {
		triggerCooldowns.remove(player);
		ward.remove(player);
	}

	private static boolean executeVanilla(
			ServerPlayerEntity player,
			Entity target,
			ArpgRuleEngine.Event event,
			String skill,
			Set<String> tags,
			ArpgRuleEngine.Trigger trigger
	) {
		if (trigger.action() == ArpgRuleEngine.Action.HEAL) {
			float amount = (float) (player.getMaxHealth() * Math.max(0.0, trigger.value()));
			if (amount > 0.0f && player.getHealth() < player.getMaxHealth()) {
				player.heal(amount);
				return true;
			}
		}
		if (trigger.action() == ArpgRuleEngine.Action.WARD) {
			return grantWard(player, target, event, skill, tags, trigger.value());
		}
		return false;
	}

	private static boolean grantWard(
			ServerPlayerEntity player,
			Entity target,
			ArpgRuleEngine.Event event,
			String skill,
			Set<String> tags,
			double fraction
	) {
		double maximum = maximumWard(player, target, event, skill, tags);
		if (!Double.isFinite(maximum) || maximum <= 0.0) {
			storeWard(player, 0.0);
			return false;
		}
		double current = Math.max(0.0, Math.min(maximum, ward.getOrDefault(player, 0.0)));
		storeWard(player, current);
		double next = ArpgResourceSemantics.restoreFromMaximum(current, maximum, fraction);
		if (!Double.isFinite(next) || next <= current + RESOURCE_EPSILON) {
			return false;
		}
		storeWard(player, next);
		return true;
	}

	private static double maximumWard(
			ServerPlayerEntity player,
			Entity target,
			ArpgRuleEngine.Event event,
			String skill,
			Set<String> tags
	) {
		return snapshot(player, target, event, skill, tags).apply(ArpgStat.WARD, 0.0);
	}

	private static void storeWard(ServerPlayerEntity player, double amount) {
		if (!Double.isFinite(amount) || amount <= RESOURCE_EPSILON) {
			ward.remove(player);
		} else {
			ward.put(player, amount);
		}
	}

	private static boolean executeExternal(ServerPlayerEntity player, ArpgRuleEngine.Trigger trigger) {
		try {
			return externalTriggerExecutor.execute(player, trigger);
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static ArpgRuleEngine.Context context(
			ServerPlayerEntity player,
			Entity target,
			ArpgRuleEngine.Event event,
			String skill,
			Set<String> tags
	) {
		float maxHealth = Math.max(1.0f, player.getMaxHealth());
		boolean lowLife = player.getHealth() <= maxHealth * 0.5f;
		boolean fullLife = player.getHealth() >= maxHealth - 0.001f;
		boolean lowMana = ArpgResourceSemantics.isLowFraction(readManaFraction(player));
		boolean close = target != null && player.squaredDistanceTo(target) <= CLOSE_DISTANCE_SQUARED;
		boolean distant = target != null && !close;
		boolean ignited = target instanceof LivingEntity living && living.isOnFire();
		boolean poisoned = target instanceof LivingEntity living && living.hasStatusEffect(StatusEffects.POISON);
		boolean shield = player.isBlocking();
		boolean dualWield = !player.getMainHandStack().isEmpty() && !player.getOffHandStack().isEmpty();
		var velocity = player.getVelocity();
		boolean moving = velocity.x * velocity.x + velocity.z * velocity.z > 0.0025;

		return new ArpgRuleEngine.Context(
				event,
				skill,
				tags,
				lowLife,
				fullLife,
				lowMana,
				close,
				distant,
				ignited,
				false,
				poisoned,
				shield,
				dualWield,
				moving
		);
	}

	private static double readManaFraction(ServerPlayerEntity player) {
		try {
			return manaProvider.fraction(player);
		} catch (RuntimeException ignored) {
			return Double.NaN;
		}
	}
}
