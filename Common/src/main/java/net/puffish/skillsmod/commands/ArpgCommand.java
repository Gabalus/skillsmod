package net.puffish.skillsmod.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.util.Formatting;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.arpg.character.ArpgCharacter;
import net.puffish.skillsmod.arpg.character.ArpgProgression;
import net.puffish.skillsmod.arpg.combat.ArpgCombatRuntime;
import net.puffish.skillsmod.arpg.compat.ArpgProviderRegistry;
import net.puffish.skillsmod.arpg.data.ArpgData;
import net.puffish.skillsmod.arpg.skill.ArpgWeaponSkillExecutor;
import net.puffish.skillsmod.arpg.skill.ArpgSkillAccess;

import java.util.Collection;
import java.util.Locale;
import java.util.stream.Collectors;

/** Player-facing, server-authoritative ARPG character progression and development commands. */
public final class ArpgCommand {
	private ArpgCommand() {
	}

	public static LiteralArgumentBuilder<ServerCommandSource> create() {
		return CommandManager.literal("arpg")
				.executes(ArpgCommand::status)
				.then(CommandManager.literal("status")
						.executes(ArpgCommand::status))
				.then(CommandManager.literal("providers")
						.executes(ArpgCommand::providers))
				.then(CommandManager.literal("spells")
						.executes(ArpgCommand::spells))
				.then(CommandManager.literal("progression")
						.executes(ArpgCommand::progression))
				.then(CommandManager.literal("completions")
						.executes(ArpgCommand::completions))
				.then(CommandManager.literal("combat")
						.executes(ArpgCommand::combatStatus)
						.then(CommandManager.literal("status")
								.executes(ArpgCommand::combatStatus)))
				.then(CommandManager.literal("choose")
						.then(choice("primary"))
						.then(choice("secondary"))
						.then(ascendancyChoice()))
				.then(CommandManager.literal("trial")
						.executes(ArpgCommand::trialStatus)
						.then(CommandManager.literal("status")
								.executes(ArpgCommand::trialStatus))
						.then(CommandManager.literal("complete")
								.requires(source -> source.hasPermissionLevel(2))
								.executes(ArpgCommand::completeTrial))
						.then(CommandManager.literal("pass")
								.requires(source -> source.hasPermissionLevel(2))
								.executes(ArpgCommand::completeTrial)))
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

	private static int spells(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		var character = ArpgProgression.character(player);
		boolean loaded = ArpgProviderRegistry.loaded("irons_spellbooks");
		player.sendMessage(Text.literal("Spells — obtain a scroll and equip it in an Iron's spellbook; use its casting controls.")
				.formatted(Formatting.GOLD), false);
		for (var skill : ArpgData.content().skills().values().stream().filter(s -> "irons".equals(s.provider()))
				.sorted(java.util.Comparator.comparingInt(net.puffish.skillsmod.arpg.data.ArpgContent.Skill::level)
						.thenComparing(net.puffish.skillsmod.arpg.data.ArpgContent.Skill::title)).limit(32).toList()) {
			var access = ArpgSkillAccess.check(character, skill, "irons");
			boolean specialized = character.specializations().containsKey(skill.id());
			String status = !loaded ? "Iron's Spells is missing" : !access.allowed() ? access.message()
					: specialized ? "Specialized" : "Ready";
			var row = Text.literal(skill.title() + " | level " + skill.level() + " | " + status)
					.formatted(loaded && access.allowed() ? Formatting.GREEN : Formatting.GRAY);
			if (loaded && access.allowed() && !specialized) {
				row.append(Text.literal(" [Specialize]").formatted(Formatting.AQUA).styled(style -> style
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/arpg specialize " + skill.id()))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
								Text.literal("Uses a specialization slot. Manage its tree in the Character hub's Skills tab.")))));
			}
			player.sendMessage(row, false);
		}
		return 1;
	}

	private static int completions(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		var state = ArpgProgression.character(player);
		var pending = state.discoveredKnowledge().stream()
				.filter(id -> !net.puffish.skillsmod.arpg.sandbox.CraftworkRuntime.state(player).knowledge().contains(id)).sorted().toList();
		context.getSource().sendFeedback(() -> Text.literal("First clears: " + state.completions().size()
				+ " | " + state.completions().keySet().stream().sorted().limit(16).toList()
				+ " | earned passive=" + state.passivePoints() + " | earned confluence=" + state.earnedConfluencePoints()
				+ " | pending discoveries=" + pending.stream().limit(16).toList() + " | use /craftwork study"), false);
		return 1;
	}

	private static int progression(CommandContext<ServerCommandSource> context) {
		var policy = net.puffish.skillsmod.arpg.progression.EncounterProgressionData.policy();
		context.getSource().sendFeedback(() -> Text.literal("Encounter XP: dimensions=" + policy.expeditionDimensions()
				+ " | server markers=arpg:encounter, arpg:world_boss"
				+ " | Apotheosis world bosses=" + policy.allowApotheosisWorldBosses()
				+ " | XP cap=" + policy.maxKillExperience()), false);
		context.getSource().sendFeedback(() -> Text.literal("Threat bonuses (additive): L2 level " + policy.l2PercentPerLevel()
				+ "% (cap " + policy.maxL2Level() + "), trait rank " + policy.traitPercentPerRank()
				+ "% (cap " + policy.maxTraitRanks() + "), Apotheosis spawn tier " + policy.apotheosisPercentPerTier()
				+ "% (cap " + policy.maxApotheosisTier() + "), elite " + policy.elitePercent()
				+ "%, invader " + policy.invaderPercent() + "%"), false);
		return 1;
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
						+ " | XP=" + state.experience()
						+ " | primary=" + primary
						+ " | secondary=" + secondary
						+ " | ascendancy=" + ascendancy), false);
		context.getSource().sendFeedback(() -> Text.literal(
				"Points: passive=" + state.passivePoints()
						+ ", confluence=" + state.confluencePoints()
						+ ", ascendancy=" + state.ascendancyPoints()
						+ " | trials=" + state.completedTrials() + "/" + ArpgCharacter.MAX_TRIALS
						+ " | specializations=" + specializations), false);
		return 1;
	}

	private static int trialStatus(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		var state = ArpgProgression.character(player);
		int next = state.nextTrial();

		if (next == 0) {
			context.getSource().sendFeedback(() -> Text.literal(
					"Ascendancy Trials complete: " + ArpgCharacter.MAX_TRIALS + "/" + ArpgCharacter.MAX_TRIALS
							+ " | ascendancy points=" + state.ascendancyPoints()), false);
			return 1;
		}

		int requiredLevel = ArpgCharacter.trialRequiredLevel(next);
		String readiness;
		if (state.primary().isEmpty()) {
			readiness = "choose a primary discipline first";
		} else if (state.level() < requiredLevel) {
			readiness = "requires ARPG level " + requiredLevel;
		} else {
			readiness = "READY - complete an authored trial (operator reward prototype)";
		}
		context.getSource().sendFeedback(() -> Text.literal(
				"Ascendancy Trials: " + state.completedTrials() + "/" + ArpgCharacter.MAX_TRIALS
						+ " | next=Trial " + next
						+ " | " + readiness
						+ " | ascendancy points=" + state.ascendancyPoints()), false);
		return 1;
	}

	private static int completeTrial(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		var state = ArpgProgression.character(player);
		int trial = state.nextTrial();

		if (trial == 0) {
			context.getSource().sendError(Text.literal("All Ascendancy Trials are already complete"));
			return 0;
		}

		try {
			if (!state.completeTrial(trial)) {
				context.getSource().sendError(Text.literal("Trial " + trial + " is already complete"));
				return 0;
			}
			ArpgProgression.sync(player);
			context.getSource().sendFeedback(() -> Text.literal(
					"Completed Ascendancy Trial " + trial
							+ ". Ascendancy points: " + state.ascendancyPoints()), false);
			if (trial == 1 && state.ascendancy().isEmpty()) {
				context.getSource().sendFeedback(() -> Text.literal(
						"Your first Ascendancy is now unlocked. Use /arpg choose ascendancy <id>."), false);
			}
			return 1;
		} catch (IllegalArgumentException | IllegalStateException exception) {
			context.getSource().sendError(Text.literal(exception.getMessage()));
			return 0;
		}
	}

	private static int providers(CommandContext<ServerCommandSource> context) {
		for (var provider : ArpgProviderRegistry.all()) {
			String state = provider.loaded() ? "LOADED" : "missing";
			context.getSource().sendFeedback(() -> Text.literal(
					"[" + state + "] " + provider.name() + " - " + provider.role()), false);
		}
		return 1;
	}

	private static int combatStatus(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var state = ArpgCombatRuntime.state(context.getSource().getPlayerOrThrow());
		var resources = state.resources().entrySet().stream()
				.map(entry -> entry.getKey().id() + "="
						+ format(entry.getValue().current()) + "/" + format(entry.getValue().maximum()))
				.sorted()
				.collect(Collectors.joining(", "));
		context.getSource().sendFeedback(() -> Text.literal(
				"Combat pillar=" + state.pillar().id()
						+ " | revision=" + state.revision()
						+ " | " + resources), false);
		return 1;
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
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
