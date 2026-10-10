package net.puffish.skillsmod.arpg.compat;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.puffish.skillsmod.arpg.combat.MeleeActionPolicy;
import net.puffish.skillsmod.main.EpicFightMeleeBridge;

/** Inspect the live weapon; activation always rechecks its current provider container. */
public final class MeleeCommand {
	private MeleeCommand() {
	}

	public static LiteralArgumentBuilder<ServerCommandSource> create() {
		return CommandManager.literal("melee").executes(MeleeCommand::status)
				.then(CommandManager.literal("status").executes(MeleeCommand::status))
				.then(CommandManager.literal("innate").executes(c -> use(c, MeleeActionPolicy.Action.INNATE)))
				.then(CommandManager.literal("stance").executes(c -> use(c, MeleeActionPolicy.Action.STANCE)));
	}

	private static int status(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		var status = EpicFightMeleeBridge.status(player);
		if (!status.available()) {
			context.getSource().sendError(Text.literal(status.rejection()));
			return 0;
		}
		player.sendMessage(Text.literal("Melee | " + status.weapon() + " | style " + status.style()).formatted(Formatting.GOLD), false);
		player.sendMessage(Text.literal("Innate: " + (status.skill().isEmpty() ? "none" : status.skill())
				+ " | charges " + status.charges() + " | resource " + status.resource()), false);
		if ("epicfight:liechtenauer".equals(status.skill())) {
			player.sendMessage(Text.literal(status.active() ? "Defensive Liechtenauer | " + status.remainingTicks() / 20.0 + " seconds remaining"
					: "Standard longsword stance | Liechtenauer changes combat motions and defense"), false);
		}
		if (!status.rejection().isEmpty()) {
			player.sendMessage(Text.literal(status.rejection()).formatted(Formatting.GRAY), false);
		} else {
			var row = action("[Use innate]", "/arpg melee innate");
			if ("epicfight:liechtenauer".equals(status.skill())) {
				row.append(" ").append(action(status.active() ? "[Leave defensive stance]" : "[Enter defensive stance]", "/arpg melee stance"));
			}
			player.sendMessage(row, false);
		}
		player.sendMessage(action("[Refresh]", "/arpg melee status"), false);
		player.sendMessage(Text.literal("Bind ARPG Melee Innate / ARPG Weapon Stance in Controls for use during combat. Epic Fight validates charge and costs."), false);
		return 1;
	}

	private static net.minecraft.text.MutableText action(String label, String command) {
		return Text.literal(label).formatted(Formatting.AQUA).styled(style -> style
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
	}

	private static int use(CommandContext<ServerCommandSource> context, MeleeActionPolicy.Action action) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		var result = EpicFightMeleeBridge.use(player, action);
		player.sendMessage(Text.literal(result.message()).formatted(result.accepted() ? Formatting.GREEN : Formatting.RED), true);
		return result.accepted() ? 1 : 0;
	}
}
