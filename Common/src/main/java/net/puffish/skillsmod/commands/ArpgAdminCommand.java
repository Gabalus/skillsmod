package net.puffish.skillsmod.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.puffish.skillsmod.arpg.character.ArpgCharacter;
import net.puffish.skillsmod.arpg.character.ArpgProgression;

/** Operator-only helpers for testing server-authoritative ARPG progression. */
public final class ArpgAdminCommand {
	private ArpgAdminCommand() {
	}

	/**
	 * Returns an additional /arpg root. Brigadier merges these children into the player-facing root
	 * after {@link ArpgCommand} has been registered.
	 */
	public static LiteralArgumentBuilder<ServerCommandSource> create() {
		return CommandManager.literal("arpg")
				.then(CommandManager.literal("xp")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.literal("add")
								.then(CommandManager.argument("amount",
										LongArgumentType.longArg(1L, ArpgCharacter.MAX_EXPERIENCE))
										.executes(ArpgAdminCommand::addExperience))))
				.then(CommandManager.literal("level")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.literal("set")
								.then(CommandManager.argument("level",
										IntegerArgumentType.integer(1, ArpgCharacter.MAX_LEVEL))
										.executes(ArpgAdminCommand::setLevel))));
	}

	private static int addExperience(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		long amount = LongArgumentType.getLong(context, "amount");
		var character = ArpgProgression.character(player);
		int previousLevel = character.level();
		character.gainExperience(amount);
		ArpgProgression.sync(player);
		int newLevel = character.level();

		context.getSource().sendFeedback(() -> Text.literal(
				"Added " + amount + " ARPG XP | level " + previousLevel + " -> " + newLevel
						+ " | total XP=" + character.experience()), false);
		return 1;
	}

	private static int setLevel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		int targetLevel = IntegerArgumentType.getInteger(context, "level");
		var character = ArpgProgression.character(player);
		int previousLevel = character.level();
		character.setLevel(targetLevel);
		ArpgProgression.sync(player);

		context.getSource().sendFeedback(() -> Text.literal(
				"Set ARPG level " + previousLevel + " -> " + targetLevel
						+ " | total XP=" + character.experience()), false);
		return 1;
	}
}
