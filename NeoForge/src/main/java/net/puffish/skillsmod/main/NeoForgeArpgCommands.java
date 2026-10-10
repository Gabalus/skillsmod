package net.puffish.skillsmod.main;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.puffish.skillsmod.arpg.sandbox.SandboxData;
import net.puffish.skillsmod.commands.CraftworkCommand;

import java.util.HashMap;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.commands.ArpgAdminCommand;
import net.puffish.skillsmod.commands.ArpgCommand;

/** Registers player-facing ARPG commands independently of the operator-only legacy command tree. */
@EventBusSubscriber(modid = SkillsAPI.MOD_ID)
public final class NeoForgeArpgCommands {
	private NeoForgeArpgCommands() {
	}

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		var versions = new HashMap<String, String>();
		for (var mod : ModList.get().getMods()) {
			versions.put(mod.getModId(), mod.getVersion().toString());
		}
		SandboxData.configureProviders(versions);
		event.getDispatcher().register(CraftworkCommand.create());
		event.getDispatcher().register(net.puffish.skillsmod.arpg.rift.RiftCommand.create());
		event.getDispatcher().register(net.puffish.skillsmod.arpg.tower.TowerCommand.create());
		event.getDispatcher().register(ArpgCommand.create());
		event.getDispatcher().register(net.minecraft.server.command.CommandManager.literal("arpg")
				.then(net.puffish.skillsmod.arpg.compat.MeleeCommand.create()));
		event.getDispatcher().register(ArpgAdminCommand.create());
	}
}
