package net.puffish.skillsmod.arpg.combat;

import net.minecraft.server.network.ServerPlayerEntity;
import net.puffish.skillsmod.server.data.ServerData;

/** Uses the UUID-owned ARPG save, so respawn, reconnect and item changes cannot reset cooldowns. */
public final class MeleeKitRuntime {
	private MeleeKitRuntime() {
	}

	public static int remaining(ServerPlayerEntity player, MeleeKit.Attack attack) {
		long now = Math.max(0, player.server.getOverworld().getTime());
		var data = ServerData.getOrCreate(player.server).getPlayerData(player);
		var state = data.getMeleeCooldowns().normalized(now);
		data.setMeleeCooldowns(state);
		return state.remaining(attack, now);
	}

	public static void used(ServerPlayerEntity player, MeleeKit.Attack attack) {
		long now = Math.max(0, player.server.getOverworld().getTime());
		var data = ServerData.getOrCreate(player.server).getPlayerData(player);
		data.setMeleeCooldowns(data.getMeleeCooldowns().normalized(now).used(attack, now));
	}
}
