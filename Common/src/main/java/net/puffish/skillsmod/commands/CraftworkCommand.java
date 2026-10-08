package net.puffish.skillsmod.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.puffish.skillsmod.arpg.sandbox.CraftworkRuntime;

/** Authenticated prototype inputs; knowledge grants remain operator-only until rifts call learn(). */
public final class CraftworkCommand {
	private CraftworkCommand() {
	}

	private interface Action {
		void run(ServerPlayerEntity player);
	}

	private static int run(ServerCommandSource source, Action action) {
		var player = source.getPlayer();
		if (player == null) {
			source.sendError(Text.literal("A player is required"));
			return 0;
		}

		try {
			action.run(player);
			net.puffish.skillsmod.SkillsMod.getInstance().syncCraftwork(player, false, "");
			source.sendFeedback(() -> Text.literal(describe(player)), false);
			return 1;
		} catch (IllegalArgumentException | IllegalStateException | NullPointerException error) {
			var message = error.getMessage() == null ? "Invalid craftwork request" : error.getMessage();
			source.sendError(Text.literal(message));
			net.puffish.skillsmod.SkillsMod.getInstance().syncCraftwork(player, false, message);
			return 0;
		}
	}

	private static String describe(ServerPlayerEntity player) {
		var state = CraftworkRuntime.state(player);
		var session = state.session();
		return "Knowledge: " + state.knowledge() + " | Mastery: " + state.mastery()
		+ " | Discoveries: " + net.puffish.skillsmod.arpg.character.ArpgProgression.character(player).discoveredKnowledge()
		+ " | Item quality: " + player.getMainHandStack().getOrDefault(net.puffish.skillsmod.arpg.sandbox.CraftworkItems.QUALITY, net.puffish.skillsmod.arpg.sandbox.CraftedItemData.empty()).stages()
		+ (session == null ? " | No active craft" : " | " + session.operation() + " step " + session.step() + " expected " + session.expected() + " process " + session.process() + " mistakes " + session.mistakes() + (session.failed() ? " FAILED: cancel" : ""));
	}

	public static LiteralArgumentBuilder<ServerCommandSource> create() {
		return CommandManager.literal("craftwork")
		.executes(context -> run(context.getSource(), player -> CraftworkRuntime.state(player)))
		.then(CommandManager.literal("refresh").executes(context -> {
			var player = context.getSource().getPlayer();
			if (player == null) {
				return 0;
			}
			net.puffish.skillsmod.SkillsMod.getInstance().syncCraftwork(player, false, "");
			return 1;
		}))
		.then(CommandManager.literal("open").executes(context -> run(context.getSource(), player -> net.puffish.skillsmod.SkillsMod.getInstance().syncCraftwork(player, true, ""))))
		.then(CommandManager.literal("learn").requires(source -> source.hasPermissionLevel(2))
		.then(CommandManager.argument("knowledge", StringArgumentType.word())
		.executes(context -> run(context.getSource(), player -> CraftworkRuntime.learn(player, StringArgumentType.getString(context, "knowledge"))))))
		.then(CommandManager.literal("study")
		.executes(context -> run(context.getSource(), net.puffish.skillsmod.arpg.progression.CompletionRuntime::studyAvailable))
		.then(CommandManager.argument("knowledge", StringArgumentType.word())
		.executes(context -> run(context.getSource(), player -> net.puffish.skillsmod.arpg.progression.CompletionRuntime.study(player, StringArgumentType.getString(context, "knowledge"))))))
		.then(CommandManager.literal("start").then(CommandManager.argument("operation", StringArgumentType.word())
		.executes(context -> run(context.getSource(), player -> CraftworkRuntime.start(player, StringArgumentType.getString(context, "operation"))))))
		.then(CommandManager.literal("act").then(CommandManager.argument("sequence", IntegerArgumentType.integer(0, 64))
		.then(CommandManager.argument("action", StringArgumentType.word())
		.executes(context -> run(context.getSource(), player -> CraftworkRuntime.act(player, IntegerArgumentType.getInteger(context, "sequence"), StringArgumentType.getString(context, "action")))))))
		.then(CommandManager.literal("finish").executes(context -> run(context.getSource(), CraftworkRuntime::finish)))
		.then(CommandManager.literal("cancel").executes(context -> run(context.getSource(), CraftworkRuntime::cancel)));
	}
}
