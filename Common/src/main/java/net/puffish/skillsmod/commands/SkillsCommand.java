package net.puffish.skillsmod.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.puffish.skillsmod.arpg.stat.ArpgPlayerStats;
import net.puffish.skillsmod.commands.arguments.CategoryArgumentType;
import net.puffish.skillsmod.commands.arguments.SkillArgumentType;
import net.puffish.skillsmod.util.CommandUtils;

import java.util.Locale;

public class SkillsCommand {
	public static LiteralArgumentBuilder<ServerCommandSource> create() {
		return CommandManager.literal("skills")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.literal("unlock")
						.then(CommandManager.argument("players", EntityArgumentType.players())
								.then(CommandManager.argument("category", CategoryArgumentType.category())
										.then(CommandManager.argument("skill", SkillArgumentType.skillFromCategory("category"))
												.executes(SkillsCommand::unlock)
										)
								)
						)
				)
				.then(CommandManager.literal("lock")
						.then(CommandManager.argument("players", EntityArgumentType.players())
								.then(CommandManager.argument("category", CategoryArgumentType.category())
										.then(CommandManager.argument("skill", SkillArgumentType.skillFromCategory("category"))
												.executes(SkillsCommand::lock)
										)
								)
						)
				)
				.then(CommandManager.literal("reset")
						.then(CommandManager.argument("players", EntityArgumentType.players())
								.then(CommandManager.argument("category", CategoryArgumentType.category())
										.executes(SkillsCommand::reset)
								)
						)
				)
				.then(CommandManager.literal("arpg")
						.then(CommandManager.argument("players", EntityArgumentType.players())
								.executes(SkillsCommand::arpg)
						)
				);
	}

	private static int unlock(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var players = EntityArgumentType.getPlayers(context, "players");
		var category = CategoryArgumentType.getCategory(context, "category");
		var skill = SkillArgumentType.getSkillFromCategory(context, "skill", category);

		for (var player : players) {
			skill.unlock(player);
		}
		CommandUtils.sendSuccess(
				context,
				players,
				"skills.unlock",
				category.getId(),
				skill.getId()
		);
		return players.size();
	}

	private static int lock(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var players = EntityArgumentType.getPlayers(context, "players");
		var category = CategoryArgumentType.getCategory(context, "category");
		var skill = SkillArgumentType.getSkillFromCategory(context, "skill", category);

		for (var player : players) {
			skill.lock(player);
		}
		CommandUtils.sendSuccess(
				context,
				players,
				"skills.lock",
				category.getId(),
				skill.getId()
		);
		return players.size();
	}

	private static int reset(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var players = EntityArgumentType.getPlayers(context, "players");
		var category = CategoryArgumentType.getCategory(context, "category");

		for (var player : players) {
			category.resetSkills(player);
		}
		CommandUtils.sendSuccess(
				context,
				players,
				"skills.reset",
				category.getId()
		);
		return players.size();
	}

	private static int arpg(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var players = EntityArgumentType.getPlayers(context, "players");
		for (var player : players) {
			var snapshot = ArpgPlayerStats.getSnapshot(player);
			context.getSource().sendFeedback(
					() -> Text.literal("ARPG stats for " + player.getName().getString() + ":"),
					false
			);
			if (snapshot.asMap().isEmpty()) {
				context.getSource().sendFeedback(() -> Text.literal("  no active ARPG modifiers"), false);
				continue;
			}
			for (var entry : snapshot.asMap().entrySet()) {
				var value = entry.getValue();
				var line = String.format(
						Locale.ROOT,
						"  %s: flat=%.3f increased=%.2f%% multiplier=%.4f",
						entry.getKey().getId(),
						value.flat(),
						value.increased() * 100.0,
						value.multiplier()
				);
				context.getSource().sendFeedback(() -> Text.literal(line), false);
			}
		}
		return players.size();
	}
}
