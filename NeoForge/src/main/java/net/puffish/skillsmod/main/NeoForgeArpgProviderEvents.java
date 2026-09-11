package net.puffish.skillsmod.main;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.arpg.combat.ArpgTargetTags;
import net.puffish.skillsmod.arpg.compat.ArpgProviderRegistry;
import net.puffish.skillsmod.arpg.compat.L2HostilityCompat;
import net.puffish.skillsmod.arpg.compat.ProviderItemCompat;
import net.puffish.skillsmod.arpg.item.ArpgItemTags;

import java.util.Map;

/** NeoForge bridge for optional ARPG provider discovery and provider-backed tag adapters. */
@EventBusSubscriber(modid = SkillsAPI.MOD_ID)
public final class NeoForgeArpgProviderEvents {
	private NeoForgeArpgProviderEvents() {
	}

	@SubscribeEvent
	public static void onServerStarting(ServerStartingEvent event) {
		refreshProviders();
	}

	private static void refreshProviders() {
		var mods = ModList.get();
		ArpgProviderRegistry.configure(Map.of(
				"irons_spellbooks", mods.isLoaded("irons_spellbooks"),
				"bettercombat", mods.isLoaded("bettercombat"),
				"apotheosis", mods.isLoaded("apotheosis"),
				"l2hostility", mods.isLoaded("l2hostility"),
				"l2artifacts", mods.isLoaded("l2artifacts"),
				"celestial_artifacts", mods.isLoaded("celestial_artifacts")
		));
		ArpgTargetTags.configure(L2HostilityCompat::tags);
		ArpgItemTags.configure(ProviderItemCompat::tags);
	}
}
