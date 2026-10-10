package net.puffish.skillsmod.arpg.rift;

import net.minecraft.command.argument.IdentifierArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.puffish.skillsmod.arpg.compat.ApotheosisProgressionCompat;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.puffish.skillsmod.arpg.progression.CompletionData;

/** Player-owned entry/exit controls; completion rewards are never client-reported. */
public final class RiftCommand {
	private RiftCommand() {
	}

	public static LiteralArgumentBuilder<ServerCommandSource> create() {
		return CommandManager.literal("rift")
				.executes(RiftCommand::status)
				.then(CommandManager.literal("status").executes(RiftCommand::status))
				.then(CommandManager.literal("tiers").executes(context -> RiftTierMenu.show(context.getSource().getPlayerOrThrow())))
				.then(CommandManager.literal("tier").then(CommandManager.argument("tier", IntegerArgumentType.integer(0, 4))
						.executes(RiftCommand::tier)))
				.then(CommandManager.literal("leave").executes(RiftCommand::leave))
				.then(CommandManager.literal("enter").then(CommandManager.argument("completion", IdentifierArgumentType.identifier())
						.suggests((context, builder) -> CommandSource.suggestMatching(CompletionData.catalog().rewards().values().stream()
								.filter(reward -> reward.type().equals("rift")).map(reward -> reward.id()), builder))
						.executes(RiftCommand::enter)));
	}

	private static int enter(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		try {
			RiftRuntime.enter(context.getSource().getPlayerOrThrow(), IdentifierArgumentType.getIdentifier(context, "completion").toString());
			return 1;
		} catch (IllegalArgumentException | IllegalStateException error) {
			context.getSource().sendError(Text.literal(error.getMessage()));
			return 0;
		}
	}

	private static int tier(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		try {
			if (!player.isAlive() || player.isCreative() || player.isSpectator()
					|| !player.getWorld().getRegistryKey().equals(net.minecraft.world.World.OVERWORLD)
					|| RiftRuntime.book(player.server).get(player.getUuid()) != null) {
				throw new IllegalStateException("Change tiers in the overworld in survival/adventure mode, after leaving your rift");
			}
			ApotheosisProgressionCompat.activate(player, IntegerArgumentType.getInteger(context, "tier"));
			return RiftTierMenu.show(player);
		} catch (IllegalArgumentException | IllegalStateException error) {
			context.getSource().sendError(Text.literal(error.getMessage()));
			return 0;
		}
	}

	private static int leave(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		RiftRuntime.leave(player.server, player.getUuid(), "Rift abandoned");
		RiftRuntime.recoverPlayer(player);
		return 1;
	}

	private static int status(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		var session = RiftRuntime.book(player.server).get(player.getUuid());
		context.getSource().sendFeedback(() -> Text.literal(session == null ? "No active rift. Use /rift tiers to review tiers, then /rift enter arpg:first_rift"
				: "Rift " + session.completion() + " | " + session.phase() + " | entry tier (XP): " + ApotheosisProgressionCompat.tierName(session.apotheosisTier()) + " | build " + session.buildIndex() + "/" + RiftSession.BUILD_VOLUME), false);
		return 1;
	}
}
