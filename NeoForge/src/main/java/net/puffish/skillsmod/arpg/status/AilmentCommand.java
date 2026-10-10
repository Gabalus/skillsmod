package net.puffish.skillsmod.arpg.status;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.puffish.skillsmod.arpg.combat.AilmentType;

/** Bounded read-only status inspection; no operator access or optional providers required. */
public final class AilmentCommand {
	private AilmentCommand() {
	}

	public static LiteralArgumentBuilder<ServerCommandSource> create() {
		return CommandManager.literal("ailments")
				.executes(context -> show(context.getSource(), context.getSource().getPlayerOrThrow()))
				.then(CommandManager.literal("target").executes(context -> {
					var source = context.getSource();
					var player = source.getPlayerOrThrow();
					var start = player.getEyePos();
					var end = player.raycast(16, 0, false).getPos();
					LivingEntity target = null;
					double closest = Double.POSITIVE_INFINITY;
					for (var candidate : player.getServerWorld().getEntitiesByClass(LivingEntity.class,
							player.getBoundingBox().expand(16), entity -> entity != player && entity.isAlive() && !entity.isSpectator())) {
						var hit = candidate.getBoundingBox().expand(.15).raycast(start, end);
						if (hit.isPresent() && hit.get().squaredDistanceTo(start) < closest) {
							closest = hit.get().squaredDistanceTo(start);
							target = candidate;
						}
					}
					if (target == null) {
						source.sendError(Text.literal("Look at a living target within 16 blocks."));
						return 0;
					}
					return show(source, target);
				}));
	}

	private static int show(ServerCommandSource source, LivingEntity target) {
		var applications = NeoForgeAilments.state(target).applications();
		var elemental = NeoForgeElementalStatuses.state(target);
		source.sendFeedback(() -> Text.literal(target.getName().getString() + " — ARPG ailments"), false);
		if (applications.isEmpty() && !elemental.active()) {
			source.sendFeedback(() -> Text.literal("No active ARPG ailments. Provider-native effects are separate."), false);
		}
		if (elemental.wetTicks() > 0) {
			source.sendFeedback(() -> Text.literal("Wet | " + seconds(elemental.wetTicks())
					+ " seconds | cold buildup x2; lightning hit +20%; ARPG ignite extinguished"), false);
		}
		if (elemental.chillStacks() > 0) {
			source.sendFeedback(() -> Text.literal("Chill | " + elemental.chillStacks()
					+ (NeoForgeElementalStatuses.freezeImmune(target) ? " stacks (freeze immune) | " : "/3 buildup | ")
					+ seconds(elemental.chillTicks()) + " seconds | movement -" + (15 * elemental.chillStacks()) + "%"), false);
		}
		if (elemental.freezeTicks() > 0) {
			source.sendFeedback(() -> Text.literal("Frozen | " + seconds(elemental.freezeTicks())
					+ " seconds | movement rooted; physical melee shatter +25%"), false);
		}
		if (elemental.freezeRecoveryTicks() > 0) {
			source.sendFeedback(() -> Text.literal("Freeze recovery | " + seconds(elemental.freezeRecoveryTicks()) + " seconds"), false);
		}
		for (var type : AilmentType.values()) {
			var matching = applications.stream().filter(a -> a.type() == type).toList();
			if (!matching.isEmpty()) {
				int duration = matching.stream().mapToInt(a -> a.remaining()).max().orElse(0);
				source.sendFeedback(() -> Text.literal(type.id() + " | " + matching.size() + " stack(s) | up to "
						+ String.format(java.util.Locale.ROOT, "%.1f", duration / 20.0) + " seconds"), false);
			}
		}
		return 1;
	}

	private static String seconds(int ticks) {
		return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0);
	}
}
