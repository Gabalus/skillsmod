package net.puffish.skillsmod.arpg.skill;

import net.minecraft.server.network.ServerPlayerEntity;
import net.puffish.skillsmod.arpg.data.ArpgContent;
import net.puffish.skillsmod.arpg.stat.ArpgStatSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Transient server-owned resource/cooldown state for custom ARPG weapon skills. */
public final class ArpgSkillRuntime {
	private static final double MAX_RESOURCE = 100.0;
	private static final double RESOURCE_REGEN_PER_TICK = 0.25;
	private static final Map<ServerPlayerEntity, State> states = new WeakHashMap<>();

	private ArpgSkillRuntime() {
	}

	public enum Denial {
		NONE,
		COOLDOWN,
		RESOURCE
	}

	public record UseResult(boolean allowed, Denial denial, double resource, int cooldownTicks, String message) {
		private static UseResult allow(double resource, int cooldownTicks) {
			return new UseResult(true, Denial.NONE, resource, cooldownTicks, "");
		}

		private static UseResult deny(Denial denial, double resource, int cooldownTicks, String message) {
			return new UseResult(false, denial, resource, cooldownTicks, message);
		}
	}

	public static synchronized UseResult tryUse(
			ServerPlayerEntity player,
			ArpgContent.Skill skill,
			ArpgStatSnapshot snapshot
	) {
		long now = player.getServerWorld().getTime();
		var state = state(player, now);
		regenerate(state, now);

		long readyAt = state.cooldowns.getOrDefault(skill.id(), 0L);
		if (readyAt > now) {
			int remaining = (int) Math.min(Integer.MAX_VALUE, readyAt - now);
			return UseResult.deny(Denial.COOLDOWN, state.resource, remaining,
					"Skill is on cooldown for " + remaining + " ticks.");
		}

		double cost = ArpgSkillUseSemantics.resourceCost(skill, snapshot);
		if (!Double.isFinite(cost) || state.resource + 1.0e-9 < cost) {
			return UseResult.deny(Denial.RESOURCE, state.resource, 0,
					"Not enough combat resource (need " + format(cost) + ", have " + format(state.resource) + ").");
		}

		int cooldown = ArpgSkillUseSemantics.cooldownTicks(skill, snapshot);
		state.resource = Math.max(0.0, state.resource - cost);
		state.cooldowns.put(skill.id(), now + cooldown);
		return UseResult.allow(state.resource, cooldown);
	}

	public static synchronized double resource(ServerPlayerEntity player) {
		long now = player.getServerWorld().getTime();
		var state = state(player, now);
		regenerate(state, now);
		return state.resource;
	}

	public static synchronized void clear(ServerPlayerEntity player) {
		states.remove(player);
	}

	private static State state(ServerPlayerEntity player, long now) {
		return states.computeIfAbsent(player, ignored -> new State(MAX_RESOURCE, now));
	}

	private static void regenerate(State state, long now) {
		if (now <= state.lastTick) {
			return;
		}
		double elapsed = now - state.lastTick;
		state.resource = Math.min(MAX_RESOURCE, state.resource + elapsed * RESOURCE_REGEN_PER_TICK);
		state.lastTick = now;
		state.cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
	}

	private static String format(double value) {
		return Double.isFinite(value) ? String.format(java.util.Locale.ROOT, "%.1f", value) : "infinite";
	}

	private static final class State {
		private double resource;
		private long lastTick;
		private final Map<String, Long> cooldowns = new HashMap<>();

		private State(double resource, long lastTick) {
			this.resource = resource;
			this.lastTick = lastTick;
		}
	}
}
