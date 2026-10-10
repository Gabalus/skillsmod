package net.puffish.skillsmod.main;

import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

/** Keeps addon classes out of the base installation's class loading path. */
public final class OptionalEpicMelee {
	private OptionalEpicMelee() {
	}

	public static void registerIfLoaded(IEventBus bus) {
		if (!ModList.get().isLoaded("epicfight")) {
			return;
		}
		try {
			Class.forName("net.puffish.skillsmod.arpg.melee.ArpgEpicMelee").getMethod("register", IEventBus.class).invoke(null, bus);
		} catch (ReflectiveOperationException | LinkageError error) {
			throw new IllegalStateException("Cannot register the ARPG melee kit with Epic Fight", error);
		}
	}

	public static String invoke(ServerPlayerEntity player, String method, String attack) {
		if (!ModList.get().isLoaded("epicfight")) {
			return "Epic Fight is not installed.";
		}
		try {
			return (String) Class.forName("net.puffish.skillsmod.arpg.melee.ArpgEpicMelee")
					.getMethod(method, ServerPlayerEntity.class, String.class).invoke(null, player, attack);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			net.puffish.skillsmod.SkillsMod.getInstance().getLogger().error("ARPG melee kit request failed: " + error);
			return "ARPG melee kit is unavailable; check the server log.";
		}
	}
}
