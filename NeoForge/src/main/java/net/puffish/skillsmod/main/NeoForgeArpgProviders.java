package net.puffish.skillsmod.main;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.arpg.compat.ArpgProviderRegistry;

import java.util.HashMap;

/** Populates the loader-neutral ARPG provider registry from NeoForge's loaded mod list. */
@EventBusSubscriber(modid = SkillsAPI.MOD_ID)
public final class NeoForgeArpgProviders {
	private NeoForgeArpgProviders() {
	}

	@SubscribeEvent
	public static void onServerStarting(ServerStartingEvent event) {
		var loaded = new HashMap<String, Boolean>();
		for (var provider : ArpgProviderRegistry.all()) {
			loaded.put(provider.id(), ModList.get().isLoaded(provider.id()));
		}
		ArpgProviderRegistry.configure(loaded);
	}
}
