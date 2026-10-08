package net.puffish.skillsmod.arpg.combat;

import net.minecraft.server.network.ServerPlayerEntity;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.server.data.ServerData;

import java.util.function.UnaryOperator;

/** Single server-side entry point for reading, changing and synchronizing player combat state. */
public final class ArpgCombatRuntime {
	private ArpgCombatRuntime() {
	}

	public static CombatState state(ServerPlayerEntity player) {
		return ServerData.getOrCreate(player.server).getPlayerData(player).getCombat();
	}

	public static boolean setPillar(ServerPlayerEntity player, CombatPillar pillar) {
		var changed = ServerData.getOrCreate(player.server).getPlayerData(player).setCombatPillar(pillar);
		if (changed) {
			sync(player);
		}
		return changed;
	}

	public static CombatState update(ServerPlayerEntity player, UnaryOperator<CombatState> operation) {
		var data = ServerData.getOrCreate(player.server).getPlayerData(player);
		var current = data.getCombat();
		var next = operation.apply(current);
		if (next == null) {
			throw new IllegalArgumentException("Combat state operation cannot return null");
		}
		if (next.pillar() != current.pillar()) {
			throw new IllegalArgumentException("Combat state operations cannot switch pillars");
		}
		if (data.setCombat(next)) {
			sync(player);
		}
		return data.getCombat();
	}

	public static CombatState reset(ServerPlayerEntity player) {
		var data = ServerData.getOrCreate(player.server).getPlayerData(player);
		data.setCombat(CombatState.fresh(data.getCombat().pillar()));
		sync(player);
		return data.getCombat();
	}

	public static void sync(ServerPlayerEntity player) {
		SkillsMod.getInstance().syncCombatState(player);
	}
}
