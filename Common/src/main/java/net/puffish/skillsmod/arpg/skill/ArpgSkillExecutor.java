package net.puffish.skillsmod.arpg.skill;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Objects;

/** Loader-neutral entry point for server-authoritative active skill execution. */
public final class ArpgSkillExecutor {
	private static volatile Handler handler = (player, skillId) -> Result.unavailable();

	private ArpgSkillExecutor() {
	}

	@FunctionalInterface
	public interface Handler {
		Result use(ServerPlayerEntity player, String skillId);
	}

	public record Result(boolean success, String message, int targetsHit, double resourceRemaining, int cooldownTicks) {
		public Result {
			message = message == null ? "" : message;
		}

		public static Result success(String message, int targetsHit, double resourceRemaining, int cooldownTicks) {
			return new Result(true, message, targetsHit, resourceRemaining, cooldownTicks);
		}

		public static Result failure(String message, double resourceRemaining) {
			return new Result(false, message, 0, resourceRemaining, 0);
		}

		private static Result unavailable() {
			return failure("No active-skill runtime is installed on this loader.", 0.0);
		}
	}

	public static void configure(Handler newHandler) {
		handler = Objects.requireNonNull(newHandler);
	}

	public static Result use(ServerPlayerEntity player, String skillId) {
		if (player == null || skillId == null || skillId.isBlank()) {
			return Result.failure("A player and skill ID are required.", 0.0);
		}
		return handler.use(player, skillId);
	}
}
