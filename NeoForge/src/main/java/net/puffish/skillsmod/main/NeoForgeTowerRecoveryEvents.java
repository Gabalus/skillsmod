package net.puffish.skillsmod.main;

import net.minecraft.entity.ItemEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.arpg.tower.TowerRecoveryRuntime;

/** Never captures cancelled drop events, mob loot or ordinary sandbox deaths. */
@EventBusSubscriber(modid = SkillsAPI.MOD_ID)
public final class NeoForgeTowerRecoveryEvents {
	private NeoForgeTowerRecoveryEvents() {
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void drops(LivingDropsEvent event) {
		if (event.getEntity() instanceof ServerPlayerEntity player
				&& TowerRecoveryRuntime.capture(player, event.getDrops().stream().map(ItemEntity::getStack).toList())) {
			event.getDrops().clear();
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void clone(PlayerEvent.Clone event) {
		if (event.isWasDeath() && event.getOriginal() instanceof ServerPlayerEntity original
				&& event.getEntity() instanceof ServerPlayerEntity replacement) {
			TowerRecoveryRuntime.clonedAfterDeath(original, replacement);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void login(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayerEntity player) {
			TowerRecoveryRuntime.login(player);
		}
	}

	@SubscribeEvent
	public static void tick(ServerTickEvent.Post event) {
		TowerRecoveryRuntime.tick(event.getServer());
	}
}
