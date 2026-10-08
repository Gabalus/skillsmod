package net.puffish.skillsmod.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.puffish.skillsmod.arpg.character.ArpgCharacter;
import net.puffish.skillsmod.arpg.character.ArpgProgression;
import net.puffish.skillsmod.arpg.combat.ArpgCombatRuntime;
import net.puffish.skillsmod.arpg.combat.CombatPillar;
import net.puffish.skillsmod.arpg.combat.CombatResource;
import net.puffish.skillsmod.arpg.combat.GunnerCombatRuntime;
import net.puffish.skillsmod.arpg.combat.GunnerCombatSemantics;

import java.util.Arrays;

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
										.executes(ArpgAdminCommand::setLevel))))
				.then(CommandManager.literal("combat")
						.then(CommandManager.literal("gunner")
								.requires(source -> source.hasPermissionLevel(2))
								.then(CommandManager.literal("fire")
										.executes(ArpgAdminCommand::fireGunner))
								.then(CommandManager.literal("reload")
										.then(CommandManager.literal("start")
												.executes(ArpgAdminCommand::startGunnerReload))
										.then(CommandManager.literal("active")
												.executes(ArpgAdminCommand::attemptGunnerReload))))
						.then(CommandManager.literal("pillar")
								.requires(source -> source.hasPermissionLevel(2))
								.then(CommandManager.literal("set")
										.then(CommandManager.argument("pillar", StringArgumentType.word())
												.suggests((context, builder) -> CommandSource.suggestMatching(
														Arrays.stream(CombatPillar.values()).map(CombatPillar::id), builder))
												.executes(ArpgAdminCommand::setCombatPillar))))
						.then(CommandManager.literal("resource")
								.requires(source -> source.hasPermissionLevel(2))
								.then(CommandManager.literal("set")
										.then(CommandManager.argument("resource", StringArgumentType.word())
												.suggests((context, builder) -> CommandSource.suggestMatching(
														Arrays.stream(CombatResource.values()).map(CombatResource::id), builder))
												.then(CommandManager.argument("value", DoubleArgumentType.doubleArg(0.0))
														.executes(ArpgAdminCommand::setCombatResource)))))
						.then(CommandManager.literal("reset")
								.requires(source -> source.hasPermissionLevel(2))
								.executes(ArpgAdminCommand::resetCombat)));
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

	private static int setCombatPillar(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		var id = StringArgumentType.getString(context, "pillar");
		var pillar = CombatPillar.byId(id).orElse(null);
		if (pillar == null) {
			context.getSource().sendError(Text.literal("Unknown combat pillar: " + id));
			return 0;
		}
		boolean changed = ArpgCombatRuntime.setPillar(player, pillar);
		context.getSource().sendFeedback(() -> Text.literal(
				"Combat pillar=" + pillar.id() + (changed ? " | resources reset" : " | already active")), false);
		return 1;
	}

	private static int setCombatResource(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		var id = StringArgumentType.getString(context, "resource");
		var resource = CombatResource.byId(id).orElse(null);
		if (resource == null) {
			context.getSource().sendError(Text.literal("Unknown combat resource: " + id));
			return 0;
		}
		double value = DoubleArgumentType.getDouble(context, "value");
		try {
			var state = ArpgCombatRuntime.update(player, current -> current.withPool(
					resource,
					current.require(resource).withCurrent(value)
			));
			var pool = state.require(resource);
			context.getSource().sendFeedback(() -> Text.literal(
					"Combat " + resource.id() + "=" + pool.current() + "/" + pool.maximum()), false);
			return 1;
		} catch (IllegalArgumentException exception) {
			context.getSource().sendError(Text.literal(exception.getMessage()));
			return 0;
		}
	}

	private static int resetCombat(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var state = ArpgCombatRuntime.reset(context.getSource().getPlayerOrThrow());
		context.getSource().sendFeedback(() -> Text.literal(
				"Reset combat resources for pillar=" + state.pillar().id()), false);
		return 1;
	}

	private static int fireGunner(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var result = GunnerCombatRuntime.fire(
				context.getSource().getPlayerOrThrow(),
				new GunnerCombatSemantics.ShotInput(1.0, 12.0)
		);
		if (!result.fired()) {
			context.getSource().sendError(Text.literal("Gunner shot rejected: " + result.outcome().name().toLowerCase()));
			return 0;
		}
		context.getSource().sendFeedback(() -> Text.literal(
				"Gunner shot accepted | damage multiplier=" + result.damageMultiplier()), false);
		return 1;
	}

	private static int startGunnerReload(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var result = GunnerCombatRuntime.startReload(
				context.getSource().getPlayerOrThrow(),
				GunnerCombatSemantics.DEFAULT_RELOAD_TICKS
		);
		if (result != GunnerCombatRuntime.StartReloadOutcome.STARTED) {
			context.getSource().sendError(Text.literal("Gunner reload rejected: " + result.name().toLowerCase()));
			return 0;
		}
		context.getSource().sendFeedback(() -> Text.literal(
				"Gunner reload started | active window at 55%-70%"), false);
		return 1;
	}

	private static int attemptGunnerReload(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var result = GunnerCombatRuntime.attemptActiveReload(context.getSource().getPlayerOrThrow());
		if (result == GunnerCombatRuntime.ActiveReloadOutcome.PERFECT) {
			context.getSource().sendFeedback(() -> Text.literal(
					"Perfect Reload | ammunition restored, heat vented, momentum gained"), false);
			return 1;
		}
		context.getSource().sendError(Text.literal("Active reload result: " + result.name().toLowerCase()));
		return 0;
	}
}
