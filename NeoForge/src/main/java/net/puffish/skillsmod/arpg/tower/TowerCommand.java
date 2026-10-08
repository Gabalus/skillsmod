package net.puffish.skillsmod.arpg.tower;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/** Opens only authored, nearby, server-gated seams. Never grants completion credit. */
public final class TowerCommand {
	private TowerCommand() {
	}

	public static LiteralArgumentBuilder<ServerCommandSource> create() {
		return CommandManager.literal("tower")
				.then(CommandManager.literal("status").executes(context -> {
					context.getSource().sendFeedback(() -> Text.literal("Immersive Portals: " + ImmersiveTowerPortals.available()
							+ " | authored links: " + TowerData.catalog().links().keySet()), false);
					return 1;
				}))
				.then(CommandManager.literal("close").executes(context -> {
					ImmersiveTowerPortals.close(context.getSource().getPlayerOrThrow().getUuid());
					return 1;
				}))
				.then(CommandManager.literal("open").then(CommandManager.argument("link", IdentifierArgumentType.identifier())
						.suggests((context, builder) -> CommandSource.suggestMatching(TowerData.catalog().links().keySet(), builder))
						.executes(context -> {
							try {
								ImmersiveTowerPortals.open(context.getSource().getPlayerOrThrow(), IdentifierArgumentType.getIdentifier(context, "link").toString());
								context.getSource().sendFeedback(() -> Text.literal("Tower seam opened for five minutes; approach its front face to cross."), false);
								return 1;
							} catch (IllegalArgumentException | IllegalStateException error) {
								context.getSource().sendError(Text.literal(error.getMessage()));
								return 0;
							}
						})));
	}
}
