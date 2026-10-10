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
				.then(CommandManager.literal("screen").executes(c -> screen(c, true)))
				.then(CommandManager.literal("refresh").executes(c -> screen(c, false)))
				.then(CommandManager.literal("tree")
						.then(CommandManager.literal("heavy").executes(c -> tree(c, net.puffish.skillsmod.arpg.combat.MeleeKit.Attack.HEAVY)))
						.then(CommandManager.literal("driving").executes(c -> tree(c, net.puffish.skillsmod.arpg.combat.MeleeKit.Attack.DRIVING))))
				.then(CommandManager.literal("status").executes(MeleeCommand::status))
				.then(CommandManager.literal("innate").executes(c -> use(c, MeleeActionPolicy.Action.INNATE)))
				.then(CommandManager.literal("stance").executes(c -> use(c, MeleeActionPolicy.Action.STANCE)))
				.then(CommandManager.literal("heavy").executes(c -> kit(c, "heavy")))
				.then(CommandManager.literal("driving").executes(c -> kit(c, "driving")));
	}

	private static int screen(CommandContext<ServerCommandSource> context, boolean open) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		net.puffish.skillsmod.SkillsMod.getInstance().openMeleeKit(player, net.puffish.skillsmod.main.OptionalEpicMelee.view(player), open);
		return 1;
	}

	private static int tree(CommandContext<ServerCommandSource> context, net.puffish.skillsmod.arpg.combat.MeleeKit.Attack attack) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		var mod = net.puffish.skillsmod.SkillsMod.getInstance();
		var access = net.puffish.skillsmod.arpg.skill.ArpgSkillAccess.check(player, attack.id(), "epicfight");
		if (!mod.getPlatform().isModLoaded("epicfight") || !access.allowed()) {
			context.getSource().sendError(Text.literal(access.allowed() ? "Epic Fight is not installed." : access.message()));
			return 0;
		}
		try {
			var character = net.puffish.skillsmod.arpg.character.ArpgProgression.character(player);
			if (!character.specializations().containsKey(attack.id())) {
				character.specialize(attack.id());
			}
			net.puffish.skillsmod.arpg.character.ArpgProgression.sync(player);
			mod.openScreen(player, java.util.Optional.of(net.puffish.skillsmod.SkillsMod.createIdentifier(
					net.puffish.skillsmod.arpg.character.ArpgProgression.skillCategory(attack.id()))));
			return 1;
		} catch (IllegalStateException exception) {
			context.getSource().sendError(Text.literal(exception.getMessage()));
			return 0;
		}
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
		for (var attack : new String[]{"heavy", "driving"}) {
			String skill = "puffish_skills:" + ("heavy".equals(attack) ? "measured_strike" : "driving_slash");
			player.sendMessage(Text.literal(net.puffish.skillsmod.main.OptionalEpicMelee.invoke(player, "status", attack))
					.append(" ").append(action("[Use]", "/arpg melee " + attack))
					.append(" ").append(action("[Specialize]", "/arpg specialize " + skill)), false);
		}
		player.sendMessage(action("[Refresh]", "/arpg melee status"), false);
		player.sendMessage(Text.literal("Bind ARPG Melee Innate, Weapon Stance, Heavy Strike and Driving Slash in Controls for combat. Epic Fight validates charge and costs."), false);
		return 1;
	}

	private static net.minecraft.text.MutableText action(String label, String command) {
		return Text.literal(label).formatted(Formatting.AQUA).styled(style -> style
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
	}

	private static int kit(CommandContext<ServerCommandSource> context, String attack) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		String result = net.puffish.skillsmod.main.OptionalEpicMelee.invoke(player, "use", attack);
		player.sendMessage(Text.literal(result.isEmpty() ? "heavy".equals(attack) ? "Measured Strike accepted." : "Driving Slash accepted." : result)
				.formatted(result.isEmpty() ? Formatting.GREEN : Formatting.RED), true);
		return result.isEmpty() ? 1 : 0;
	}

	private static int use(CommandContext<ServerCommandSource> context, MeleeActionPolicy.Action action) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrThrow();
		var result = EpicFightMeleeBridge.use(player, action);
		player.sendMessage(Text.literal(result.message()).formatted(result.accepted() ? Formatting.GREEN : Formatting.RED), true);
		return result.accepted() ? 1 : 0;
	}
}
