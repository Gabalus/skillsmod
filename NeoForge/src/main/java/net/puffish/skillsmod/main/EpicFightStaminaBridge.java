package net.puffish.skillsmod.main;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.fml.ModList;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.arpg.combat.ArpgCombatRuntime;
import net.puffish.skillsmod.arpg.combat.ProviderStaminaSnapshot;

import java.lang.reflect.Method;

/** Optional, read-only Epic Fight 21.17.3.1 stamina access; Epic Fight owns all costs and recovery. */
public final class EpicFightStaminaBridge {
	private static Access access;
	private static boolean attempted;
	private static boolean failureLogged;

	private EpicFightStaminaBridge() {
	}

	public static boolean ownsMartialCombat() {
		return ModList.get().isLoaded("epicfight");
	}

	public static void sync(ServerPlayerEntity player) {
		if (!ownsMartialCombat()) {
			return;
		}
		try {
			if (!attempted) {
				attempted = true;
				access = Access.create();
			}
			if (access == null) {
				return;
			}
			var patch = access.patch().invoke(null, player, access.playerPatch());
			if (patch == null) {
				return;
			}
			double current = ((Number) access.stamina().invoke(patch)).doubleValue();
			double maximum = ((Number) access.maximum().invoke(patch)).doubleValue();
			ArpgCombatRuntime.update(player, state -> ProviderStaminaSnapshot.apply(state, current, maximum));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
			if (!failureLogged) {
				failureLogged = true;
				SkillsMod.getInstance().getLogger().error(
						"Epic Fight stamina display unavailable; provider combat remains authoritative: " + exception
				);
			}
		}
	}

	private record Access(Class<?> playerPatch, Method patch, Method stamina, Method maximum) {
		private static Access create() throws ReflectiveOperationException {
			var capabilities = Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities");
			var playerPatch = Class.forName("yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch");
			return new Access(playerPatch, capabilities.getMethod("getEntityPatch", Entity.class, Class.class),
					playerPatch.getMethod("getStamina"), playerPatch.getMethod("getMaxStamina"));
		}
	}
}
