package net.puffish.skillsmod.main;

import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.arpg.tower.ImmersiveTowerPortals;
import net.puffish.skillsmod.arpg.tower.TowerData;

/** Public authored-sector protection and physical server-owned portal activation. */
@EventBusSubscriber(modid = SkillsAPI.MOD_ID)
public final class NeoForgeTowerEvents {
	private NeoForgeTowerEvents() {
	}

	private static boolean protectedAt(ServerWorld world, BlockPos pos) {
		return TowerData.protection().protects(world.getRegistryKey().getValue().toString(), pos.getX(), pos.getY(), pos.getZ());
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void breakBlock(BlockEvent.BreakEvent event) {
		if (event.getLevel() instanceof ServerWorld world && protectedAt(world, event.getPos())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void place(BlockEvent.EntityPlaceEvent event) {
		if (event.getLevel() instanceof ServerWorld world && (protectedAt(world, event.getPos())
				|| event instanceof BlockEvent.EntityMultiPlaceEvent multi
				&& multi.getReplacedBlockSnapshots().stream().anyMatch(snapshot -> protectedAt(world, snapshot.getPos())))) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void fluid(BlockEvent.FluidPlaceBlockEvent event) {
		if (event.getLevel() instanceof ServerWorld world && protectedAt(world, event.getPos())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void piston(PistonEvent.Pre event) {
		if (event.getLevel() instanceof ServerWorld world && TowerData.protection().pistonMayTouch(
				world.getRegistryKey().getValue().toString(), event.getPos().getX(), event.getPos().getY(), event.getPos().getZ())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void trample(BlockEvent.FarmlandTrampleEvent event) {
		if (event.getLevel() instanceof ServerWorld world && protectedAt(world, event.getPos())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void explosion(ExplosionEvent.Detonate event) {
		if (event.getLevel() instanceof ServerWorld world) {
			event.getAffectedBlocks().removeIf(pos -> protectedAt(world, pos));
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void interact(PlayerInteractEvent.RightClickBlock event) {
		if (!(event.getLevel() instanceof ServerWorld world) || !(protectedAt(world, event.getPos())
				|| event.getFace() != null && protectedAt(world, event.getPos().offset(event.getFace())))) {
			return;
		}
		event.setCanceled(true);
		if (event.getHand() != Hand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayerEntity player)
				|| player instanceof FakePlayer || !world.getBlockState(event.getPos()).isOf(Blocks.LODESTONE)) {
			return;
		}
		try {
			var pos = event.getPos();
			String link = TowerData.protection().activation(world.getRegistryKey().getValue().toString(), pos.getX(), pos.getY(), pos.getZ());
			if (!link.isEmpty()) {
				ImmersiveTowerPortals.open(player, link);
				player.sendMessage(Text.literal("Tower passage opened. Cross from its front face; your return passage is on the other side."), true);
			}
		} catch (IllegalArgumentException | IllegalStateException error) {
			player.sendMessage(Text.literal(error.getMessage()), true);
		}
	}
}
