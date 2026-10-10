package net.puffish.skillsmod.arpg.rift;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.puffish.skillsmod.arpg.compat.ApotheosisProgressionCompat;

/** Native unlocks and activation, presented alongside the reserved rift XP tier. */
public final class RiftTierMenu {
	private RiftTierMenu() {
	}

	public static int show(ServerPlayerEntity player) {
		player.sendMessage(Text.literal("Expedition world tiers").formatted(Formatting.GOLD, Formatting.BOLD), false);
		if (!ApotheosisProgressionCompat.available(player)) {
			player.sendMessage(Text.literal("Apotheosis tier controls are unavailable. Rifts use baseline tier XP.")
					.formatted(Formatting.YELLOW), false);
			return 0;
		}
		var session = RiftRuntime.book(player.server).get(player.getUuid());
		boolean mayChange = session == null && player.isAlive() && !player.isCreative() && !player.isSpectator()
				&& player.getWorld().getRegistryKey().equals(World.OVERWORLD);
		int current = ApotheosisProgressionCompat.currentTier(player);
		player.sendMessage(Text.literal("Current: " + ApotheosisProgressionCompat.tierName(current)
				+ ". Higher tiers strengthen Apotheosis enemies and improve its loot. Ctrl+T shows native requirements."), false);
		for (int tier = 0; tier <= 4; tier++) {
			var line = Text.literal(ApotheosisProgressionCompat.tierName(tier) + " — ");
			if (!ApotheosisProgressionCompat.unlocked(player, tier)) {
				line.append(Text.literal("Locked: complete Apotheosis requirements").formatted(Formatting.GRAY));
			} else if (!mayChange) {
				line.append(Text.literal("Unlocked; activate in the overworld after leaving your rift").formatted(Formatting.YELLOW));
			} else {
				String command = "/rift tier " + tier;
				line.append(Text.literal(tier == current ? "[Activate current tier]" : "[Activate]").styled(style -> style
						.withColor(Formatting.AQUA).withUnderline(true)
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Applies this native world tier, including its player bonuses.")))));
			}
			player.sendMessage(line, false);
		}
		if (session != null) {
			player.sendMessage(Text.literal("Reserved rift entry tier (XP): " + ApotheosisProgressionCompat.tierName(session.apotheosisTier())
					+ ". This snapshot remains fixed for this encounter."), false);
		}
		player.sendMessage(Text.literal("Rift entry snapshots the XP tier. Native tier controls and loot still follow Apotheosis rules."), false);
		return 1;
	}
}
