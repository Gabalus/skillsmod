package net.puffish.skillsmod.arpg.skill;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;

/** Marks synchronous damage emitted by the ARPG active-skill executor. */
public final class ArpgSkillDamageContext {
	private static final ThreadLocal<Active> active = new ThreadLocal<>();

	private ArpgSkillDamageContext() {
	}

	public static Active currentFor(ServerPlayerEntity attacker) {
		var value = active.get();
		return value != null && value.attacker() == attacker ? value : null;
	}

	public static void run(Active value, Runnable action) {
		var previous = active.get();
		active.set(value);
		try {
			action.run();
		} finally {
			if (previous == null) {
				active.remove();
			} else {
				active.set(previous);
			}
		}
	}

	public record Active(ServerPlayerEntity attacker, String skill, Set<String> tags, boolean critical) {
		public Active {
			tags = Set.copyOf(tags);
		}
	}
}
