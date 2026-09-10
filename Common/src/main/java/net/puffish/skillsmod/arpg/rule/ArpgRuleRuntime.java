package net.puffish.skillsmod.arpg.rule;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.puffish.skillsmod.arpg.data.ArpgData;
import net.puffish.skillsmod.arpg.stat.ArpgPlayerStats;
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
	private static final Map<ServerPlayerEntity, Map<String, Long>> triggerCooldowns = new WeakHashMap<>();
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
			boolean success = executeVanilla(player, trigger) || executeExternal(player, trigger);
			if (success) {
				cooldowns.put(trigger.id(), now + Math.max(1, trigger.cooldown()));
				executed++;
			}
		}
		return executed;
	}

	public static synchronized void clear(ServerPlayerEntity player) {
		triggerCooldowns.remove(player);
	}

	private static boolean executeVanilla(ServerPlayerEntity player, ArpgRuleEngine.Trigger trigger) {
		if (trigger.action() == ArpgRuleEngine.Action.HEAL) {
			float amount = (float) (player.getMaxHealth() * Math.max(0.0, trigger.value()));
			if (amount > 0.0f && player.getHealth() < player.getMaxHealth()) {
				player.heal(amount);
				return true;
			}
		}
		return false;
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
