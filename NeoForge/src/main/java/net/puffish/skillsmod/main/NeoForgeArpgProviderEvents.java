package net.puffish.skillsmod.main;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.arpg.compat.ArpgProviderRegistry;

import java.util.Map;

/** NeoForge bridge for optional ARPG provider discovery and diagnostics. */
@EventBusSubscriber(modid = SkillsAPI.MOD_ID)
public final class NeoForgeArpgProviderEvents {
	private NeoForgeArpgProviderEvents() {
	}

	@SubscribeEvent
	public static void onServerStarting(ServerStartingEvent event) {
		refreshProviders();
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		refreshProviders();
		var root = event.getDispatcher().getRoot().getChild("arpg");
		if (root == null) {
			return;
		}

		root.addChild(providerCommand().build());
	}

	private static LiteralArgumentBuilder<ServerCommandSource> providerCommand() {
		return CommandManager.literal("providers")
				.executes(context -> {
					long loaded = ArpgProviderRegistry.all().stream().filter(ArpgProviderRegistry.Provider::loaded).count();
					context.getSource().sendFeedback(
							() -> Text.literal("ARPG providers loaded: " + loaded + "/" + ArpgProviderRegistry.all().size()),
							false
					);
					for (var provider : ArpgProviderRegistry.all()) {
						String state = provider.loaded() ? "LOADED" : "absent";
						context.getSource().sendFeedback(
								() -> Text.literal("[" + state + "] " + provider.name() + " — " + provider.role()),
								false
						);
					}
					return 1;
				});
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
	}
}
