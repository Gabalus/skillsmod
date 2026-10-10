package net.puffish.skillsmod.arpg.compat;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Read-only inspection of the first living target along a sixteen-block, terrain-limited ray. */
public final class ThreatCommand {
	private ThreatCommand() {
	}

	public static LiteralArgumentBuilder<ServerCommandSource> create() {
		return CommandManager.literal("threat").executes(ThreatCommand::inspect);
	}

	private static int inspect(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		if (!L2HostilityCompat.available()) {
			context.getSource().sendError(Text.literal(ArpgProviderRegistry.loaded("l2hostility")
					? "L2 Hostility adapter is unavailable; check the server log." : "L2 Hostility is not installed."));
			return 0;
		}
		var start = player.getEyePos();
		var end = player.raycast(16.0, 0.0f, false).getPos();
		LivingEntity target = null;
		double closest = Double.POSITIVE_INFINITY;
		for (var candidate : player.getServerWorld().getEntitiesByClass(LivingEntity.class,
				player.getBoundingBox().expand(16.0), entity -> entity != player && entity.isAlive() && !entity.isSpectator())) {
			var hit = candidate.getBoundingBox().expand(0.15).raycast(start, end);
			if (hit.isPresent() && hit.get().squaredDistanceTo(start) < closest) {
				target = candidate;
				closest = hit.get().squaredDistanceTo(start);
			}
		}
		if (target == null) {
			context.getSource().sendError(Text.literal("Look at a living enemy within 16 blocks."));
			return 0;
		}
		var profile = L2HostilityCompat.profile(target);
		player.sendMessage(Text.literal(target.getName().getString() + " | L2 level " + profile.level()
				+ " | health " + Math.round(target.getHealth()) + "/" + Math.round(target.getMaxHealth()))
				.formatted(Formatting.GOLD), false);
		if (profile.traits().isEmpty()) {
			player.sendMessage(Text.literal("No active L2 traits reported."), false);
		} else {
			profile.traits().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).limit(20)
					.forEach(trait -> player.sendMessage(Text.literal(trait.getKey() + " — rank " + trait.getValue()), false));
		}
		if (profile.ineligibleSpawn()) {
			player.sendMessage(Text.literal("Provider marks this spawn as summoned, a minion or no-drop; ordinary encounter rewards are excluded.")
					.formatted(Formatting.GRAY), false);
		}
		return 1;
	}
}
