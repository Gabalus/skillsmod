package net.puffish.skillsmod.arpg.tower;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

/** Compact chat navigation; every clicked command rechecks server-owned state. */
public final class TowerMenu {
	private TowerMenu() {
	}

	public static int show(ServerPlayerEntity player) {
		player.sendMessage(Text.literal("Tower passages").formatted(Formatting.GOLD, Formatting.BOLD), false);
		var dimension = player.getWorld().getRegistryKey().getValue().toString();
		var nearby = TowerData.catalog().links().values().stream()
				.filter(link -> link.nearby(dimension, player.getX(), player.getY(), player.getZ(), 8))
				.sorted(java.util.Comparator.comparing(TowerLink::id)).toList();
		if (!ImmersiveTowerPortals.available()) {
			player.sendMessage(Text.literal("Portals are unavailable. The server and clients need Immersive Portals.")
					.formatted(Formatting.YELLOW), false);
		}
		if (nearby.isEmpty()) {
			player.sendMessage(Text.literal("Move near a tower lodestone, then refresh to see its passages."), false);
		}
		for (var link : nearby.stream().limit(8).toList()) {
			var reason = ImmersiveTowerPortals.blockedReason(player, link);
			var line = Text.literal(friendlyName(link.id()) + " — ");
			if (reason == null && ImmersiveTowerPortals.available()) {
				line.append(action("Open passage", "/tower open " + link.id(), "Opens your passage for five minutes."));
			} else {
				line.append(Text.literal(reason == null ? "Portals unavailable." : reason).formatted(Formatting.YELLOW));
			}
			player.sendMessage(line, false);
		}
		if (nearby.size() > 8) {
			player.sendMessage(Text.literal("More passages overlap here; use /tower open with a passage name."), false);
		}
		if (!TowerRecoveryRuntime.mayTravel(player)) {
			player.sendMessage(Text.literal("Recovery pending — ")
					.append(action("Recover items", "/tower recover", "Returns to your checkpoint or claims saved items. Free inventory space first.")), false);
		}
		try {
			if (TowerData.exits().nearby(dimension, player.getX(), player.getY(), player.getZ()) != null) {
				player.sendMessage(action("Leave tower", "/tower leave", "Returns to a safe location near the overworld spawn."), false);
			} else {
				player.sendMessage(Text.literal("To leave, move within three blocks of the crying obsidian exit marker."), false);
			}
		} catch (IllegalArgumentException | IllegalStateException error) {
			player.sendMessage(Text.literal("Exit markers overlap here; ask an operator to check this room."), false);
		}
		player.sendMessage(action("Refresh", "/tower", "Checks nearby passages and your current progress.")
				.append(Text.literal("  "))
				.append(action("Close passage", "/tower close", "Closes your current portal pair.")), false);
		return 1;
	}

	private static MutableText action(String label, String command, String hint) {
		return Text.literal("[" + label + "]").formatted(Formatting.UNDERLINE).styled(style -> style.withColor(Formatting.AQUA)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(hint))));
	}

	static String friendlyName(String id) {
		String path = id.substring(id.indexOf(':') + 1).replace('_', ' ').replace('/', ' ');
		return path.substring(0, 1).toUpperCase(Locale.ROOT) + path.substring(1);
	}
}
