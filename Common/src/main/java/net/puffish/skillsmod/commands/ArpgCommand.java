package net.puffish.skillsmod.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.arpg.character.ArpgProgression;
import net.puffish.skillsmod.arpg.data.ArpgData;
import net.puffish.skillsmod.arpg.skill.ArpgWeaponSkillExecutor;

import java.util.Collection;

/** Player-facing, server-authoritative ARPG character progression and development commands. */
public final class ArpgCommand {
	private ArpgCommand() {
	}

	public static LiteralArgumentBuilder<ServerCommandSource> create() {
		return CommandManager.literal("arpg")
				.executes(ArpgCommand::status)
				.then(CommandManager.literal("status")
						.executes(ArpgCommand::status))
				.then(CommandManager.literal("choose")
						.then(choice("primary"))
						.then(choice("secondary"))
						.then(ascendancyChoice()))
				.then(CommandManager.literal("specialize")
						.then(CommandManager.argument("skill", StringArgumentType.word())
								.suggests((context, builder) -> CommandSource.suggestMatching(
										ArpgData.content().skills().keySet(), builder))
								.executes(ArpgCommand::specialize)))
				.then(CommandManager.literal("unspecialize")
						.then(CommandManager.argument("skill", StringArgumentType.word())
								.suggests((context, builder) -> {
									var player = context.getSource().getPlayer();
									Collection<String> skills = player == null
											? java.util.List.of()
											: ArpgProgression.character(player).specializations().keySet();
									return CommandSource.suggestMatching(skills, builder);
								})
								.executes(ArpgCommand::unspecialize)))
				.then(CommandManager.literal("skill")
						.then(CommandManager.literal("use")
								.then(CommandManager.argument("skill", StringArgumentType.word())
										.suggests((context, builder) -> CommandSource.suggestMatching(
												ArpgData.content().skills().values().stream()
														.filter(skill -> "weapon".equals(skill.provider()))
														.map(skill -> skill.id()), builder))
										.executes(ArpgCommand::useSkill))));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> choice(String choice) {
		return CommandManager.literal(choice)
				.then(CommandManager.argument("discipline", StringArgumentType.word())
						.suggests((context, builder) -> CommandSource.suggestMatching(
								ArpgData.content().disciplines().keySet(), builder))
						.executes(context -> choose(context, choice, "discipline")));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> ascendancyChoice() {
		return CommandManager.literal("ascendancy")
				.then(CommandManager.argument("ascendancy", StringArgumentType.word())
						.suggests((context, builder) -> {
							var player = context.getSource().getPlayer();
							if (player == null) {
								return CommandSource.suggestMatching(java.util.List.of(), builder);
							}
							var state = ArpgProgression.character(player);
							var discipline = ArpgData.content().disciplines().get(state.primary());
							return CommandSource.suggestMatching(
									discipline == null ? java.util.Set.of() : discipline.ascendancies(), builder);
						})
						.executes(context -> choose(context, "ascendancy", "ascendancy")));
	}

	private static int status(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		var state = ArpgProgression.character(player);
		String primary = state.primary().isEmpty() ? "unselected" : state.primary();
		String secondary = state.secondary().isEmpty() ? "unselected" : state.secondary();
		String ascendancy = state.ascendancy().isEmpty() ? "unselected" : state.ascendancy();
		String specializations = state.specializations().isEmpty()
				? "none"
				: String.join(", ", state.specializations().keySet().stream().sorted().toList());

		context.getSource().sendFeedback(() -> Text.literal(
				"ARPG level " + state.level()
						+ " | primary=" + primary
						+ " | secondary=" + secondary
						+ " | ascendancy=" + ascendancy), false);
		context.getSource().sendFeedback(() -> Text.literal(
				"Points: passive=" + state.passivePoints()
						+ ", confluence=" + state.confluencePoints()
						+ ", ascendancy=" + state.ascendancyPoints()
						+ " | specializations=" + specializations), false);
		return 1;
	}

	private static int choose(
			CommandContext<ServerCommandSource> context,
			String choice,
			String argument
	) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		var id = StringArgumentType.getString(context, argument);
		try {
			ArpgProgression.choose(player, choice, id);
			context.getSource().sendFeedback(
					() -> Text.literal("Selected ARPG " + choice + ": " + id), false);
			return 1;
		} catch (IllegalArgumentException | IllegalStateException exception) {
			context.getSource().sendError(Text.literal(exception.getMessage()));
			return 0;
		}
	}

	private static int specialize(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		var skill = StringArgumentType.getString(context, "skill");
		if (!ArpgData.content().skills().containsKey(skill)) {
			context.getSource().sendError(Text.literal("Unknown ARPG skill: " + skill));
			return 0;
		}
		try {
			ArpgProgression.character(player).specialize(skill);
			ArpgProgression.sync(player);
			context.getSource().sendFeedback(() -> Text.literal("Specialized ARPG skill: " + skill), false);
			return 1;
		} catch (IllegalStateException exception) {
			context.getSource().sendError(Text.literal(exception.getMessage()));
			return 0;
		}
	}

	private static int unspecialize(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		var skill = StringArgumentType.getString(context, "skill");
		var character = ArpgProgression.character(player);
		if (!character.specializations().containsKey(skill)) {
			context.getSource().sendError(Text.literal("Skill is not specialized: " + skill));
			return 0;
		}

		var category = SkillsMod.createIdentifier(ArpgProgression.skillCategory(skill));
		var mod = SkillsMod.getInstance();
		mod.resetSkills(player, category);
		mod.lockCategory(player, category);
		character.unspecialize(skill);
		ArpgProgression.sync(player);
		context.getSource().sendFeedback(() -> Text.literal("Unspecialized ARPG skill: " + skill), false);
		return 1;
	}

	private static int useSkill(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		var skill = StringArgumentType.getString(context, "skill");
		var result = ArpgWeaponSkillExecutor.use(player, skill);
		if (!result.success()) {
			context.getSource().sendError(Text.literal(result.message()));
			return 0;
		}
		context.getSource().sendFeedback(
				() -> Text.literal("Used " + skill + ": " + result.message()), false);
		return 1;
	}
}
