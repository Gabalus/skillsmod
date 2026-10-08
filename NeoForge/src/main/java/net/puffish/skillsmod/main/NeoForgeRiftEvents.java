package net.puffish.skillsmod.main;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.arpg.rift.RiftRuntime;

/** Loader-owned realm protection, authoritative boss credit and disconnect/respawn recovery. */
@EventBusSubscriber(modid = SkillsAPI.MOD_ID)
public final class NeoForgeRiftEvents {
	private NeoForgeRiftEvents() {
	}

	@SubscribeEvent
	public static void started(ServerStartedEvent event) {
		RiftRuntime.recover(event.getServer());
	}

	@SubscribeEvent
	public static void tick(ServerTickEvent.Post event) {
		RiftRuntime.tick(event.getServer());
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void login(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayerEntity player) {
			RiftRuntime.recoverPlayer(player);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayerEntity player) {
			RiftRuntime.leave(player.server, player.getUuid(), "Rift ended on disconnect");
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayerEntity player) {
			RiftRuntime.leave(player.server, player.getUuid(), "Rift failed: player died");
			RiftRuntime.recoverPlayer(player);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void incoming(LivingIncomingDamageEvent event) {
		var victim = event.getEntity();
		if (!RiftRuntime.inRifts(victim.getWorld())) {
			return;
		}
		var attacker = event.getSource().getAttacker();
		if (victim.getCommandTags().contains(RiftRuntime.BOSS_TAG)) {
			var session = RiftRuntime.bossSession(victim);
			if (session == null || !(attacker instanceof ServerPlayerEntity player) || !session.player().equals(player.getUuid())
					|| !RiftRuntime.inRifts(player.getWorld()) || !session.contains(player.getX(), player.getY(), player.getZ())) {
				event.setCanceled(true);
			}
		} else if (victim instanceof ServerPlayerEntity && attacker instanceof ServerPlayerEntity) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
	public static void drops(LivingDropsEvent event) {
		var victim = event.getEntity();
		if (!RiftRuntime.inRifts(victim.getWorld()) || !(victim.getWorld() instanceof ServerWorld world)) {
			return;
		}
		if (victim instanceof ServerPlayerEntity player) {
			var session = RiftRuntime.book(player.server).get(player.getUuid());
			if (session != null) {
				RiftRuntime.queueLoot(player.server, player.getUuid(), event.getDrops().stream().map(ItemEntity::getStack).toList());
				event.getDrops().clear();
				RiftRuntime.leave(player.server, player.getUuid(), "Rift failed: player died");
			}
		} else {
			var session = RiftRuntime.bossSession(victim);
			if (session != null) {
				event.getDrops().forEach(item -> RiftRuntime.markLoot(item, session));
			}
			if (event.getSource().getAttacker() instanceof ServerPlayerEntity player) {
				RiftRuntime.bossDefeated(victim, player);
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void join(EntityJoinLevelEvent event) {
		if (!(event.getLevel() instanceof ServerWorld world) || !RiftRuntime.inRifts(world)) {
			return;
		}
		if (event.getEntity() instanceof LivingEntity entity && entity.getCommandTags().contains(RiftRuntime.BOSS_TAG)
				&& RiftRuntime.bossSession(entity) == null) {
			event.setCanceled(true);
		} else if (event.getEntity() instanceof ItemEntity item && RiftRuntime.recoverItem(item, world.getServer())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void breakBlock(BlockEvent.BreakEvent event) {
		if (event.getLevel() instanceof ServerWorld world && RiftRuntime.inRifts(world)) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void place(BlockEvent.EntityPlaceEvent event) {
		if (event.getLevel() instanceof ServerWorld world && RiftRuntime.inRifts(world)) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void fluid(BlockEvent.FluidPlaceBlockEvent event) {
		if (event.getLevel() instanceof ServerWorld world && RiftRuntime.inRifts(world)) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void interact(PlayerInteractEvent.RightClickBlock event) {
		if (event.getLevel() instanceof ServerWorld world && RiftRuntime.inRifts(world)) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void explosion(ExplosionEvent.Detonate event) {
		if (RiftRuntime.inRifts(event.getLevel())) {
			event.getAffectedBlocks().clear();
		}
	}
}
