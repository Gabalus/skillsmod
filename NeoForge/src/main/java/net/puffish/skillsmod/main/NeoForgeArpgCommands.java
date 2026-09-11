package net.puffish.skillsmod.main;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.commands.ArpgCommand;

/** Registers player-facing ARPG commands independently of the operator-only legacy command tree. */
@EventBusSubscriber(modid = SkillsAPI.MOD_ID)
public final class NeoForgeArpgCommands {
	private NeoForgeArpgCommands() {
	}

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(ArpgCommand.create());
	}
}
