package net.puffish.skillsmod.arpg.tower;

import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.arpg.rift.RiftRuntime;
import net.puffish.skillsmod.server.data.ServerData;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Private death-drop queue. Full inventories retain items; failed returns never edit terrain. */
public final class TowerRecoveryRuntime {
	private TowerRecoveryRuntime() {
	}

	public static boolean mayTravel(ServerPlayerEntity player) {
		return ServerData.getOrCreate(player.server).towerRecovery(player.getUuid()) == null;
	}

	private static TowerRecovery policy(ServerPlayerEntity player) {
		if (player instanceof FakePlayer || player.isCreative() || player.isSpectator() || RiftRuntime.inRifts(player.getWorld())
				|| RiftRuntime.book(player.server).get(player.getUuid()) != null) {
			return null;
		}
		return TowerData.recovery().at(player.getWorld().getRegistryKey().getValue().toString(), player.getX(), player.getY(), player.getZ());
	}

	/** Called only for uncancelled player death drops; commit copies before the caller clears entities. */
	public static boolean capture(ServerPlayerEntity player, List<ItemStack> drops) {
		var policy = policy(player);
		if (policy == null) {
			return false;
		}
		var data = ServerData.getOrCreate(player.server);
		var previous = data.towerRecovery(player.getUuid());
		if (previous != null && previous.ticket().phase() == TowerRecoveryTicket.Phase.WAITING_RESPAWN && previous.ticket().captured()) {
			return true;
		}
		var items = new ArrayList<ItemStack>(previous == null ? List.of() : previous.items());
		items.addAll(drops);
		try {
			data.setTowerRecovery(player.getUuid(), new TowerRecoveryRecord(TowerRecoveryTicket.begin(policy).capture(), items));
			ImmersiveTowerPortals.close(player.getUuid());
			return true;
		} catch (IllegalArgumentException | IllegalStateException error) {
			SkillsMod.getInstance().getLogger().error("Tower death recovery refused; leaving vanilla drops: " + error.getMessage());
			return false;
		}
	}

	/** Clone confirms actual death, including keepInventory deaths without a drop event. */
	public static void clonedAfterDeath(ServerPlayerEntity original, ServerPlayerEntity replacement) {
		var data = ServerData.getOrCreate(replacement.server);
		var record = data.towerRecovery(replacement.getUuid());
		if (record == null || record.ticket().phase() != TowerRecoveryTicket.Phase.WAITING_RESPAWN) {
			var policy = policy(original);
			if (policy == null) {
				return;
			}
			record = new TowerRecoveryRecord(TowerRecoveryTicket.begin(policy), record == null ? List.of() : record.items());
		}
		try {
			data.setTowerRecovery(replacement.getUuid(), new TowerRecoveryRecord(record.ticket().respawn(), record.items()));
		} catch (IllegalStateException error) {
			SkillsMod.getInstance().getLogger().error("Tower checkpoint recovery refused: " + error.getMessage());
		}
	}

	public static void login(ServerPlayerEntity player) {
		var data = ServerData.getOrCreate(player.server);
		var record = data.towerRecovery(player.getUuid());
		if (record != null && player.isAlive() && record.ticket().phase() == TowerRecoveryTicket.Phase.WAITING_RESPAWN) {
			data.setTowerRecovery(player.getUuid(), new TowerRecoveryRecord(record.ticket().respawn(), record.items()));
		}
		if (record != null) {
			player.sendMessage(Text.literal("Tower recovery pending. /tower recover retries a blocked return or claims remaining items."), false);
		}
	}

	public static void tick(MinecraftServer server) {
		if (server.getOverworld().getTime() % 20 != 0) {
			return;
		}
		for (var player : server.getPlayerManager().getPlayerList()) {
			var record = ServerData.getOrCreate(server).towerRecovery(player.getUuid());
			if (record != null && record.ticket().phase() == TowerRecoveryTicket.Phase.RETURN_PENDING) {
				recover(player, false);
			}
		}
	}

	public static boolean recover(ServerPlayerEntity player, boolean feedback) {
		var data = ServerData.getOrCreate(player.server);
		var record = data.towerRecovery(player.getUuid());
		if (record == null) {
			return message(player, feedback, "No tower recovery items or return are pending.", false);
		}
		if (player instanceof FakePlayer || !player.isAlive() || player.isCreative() || player.isSpectator()
				|| RiftRuntime.inRifts(player.getWorld()) || RiftRuntime.book(player.server).get(player.getUuid()) != null
				|| record.ticket().phase() == TowerRecoveryTicket.Phase.WAITING_RESPAWN) {
			return message(player, feedback, "Respawn and leave any rift session before claiming tower recovery in survival.", false);
		}
		var point = record.ticket().checkpoint();
		var world = player.server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(point.dimension())));
		var feet = BlockPos.ofFloored(point.x(), point.y(), point.z());
		if (!safe(world, feet)) {
			return message(player, feedback, "Tower checkpoint is missing, unloaded, obstructed or unsafe. Items remain saved; ask an operator to restore it.", false);
		}
		if (record.ticket().phase() == TowerRecoveryTicket.Phase.RETURN_PENDING) {
			ImmersiveTowerPortals.close(player.getUuid());
			if (!player.teleport(world, feet.getX() + .5, feet.getY(), feet.getZ() + .5, Set.of(), player.getYaw(), player.getPitch())) {
				return false;
			}
			player.fallDistance = 0;
			record = new TowerRecoveryRecord(record.ticket().returned(), record.items());
			data.setTowerRecovery(player.getUuid(), record);
			player.sendMessage(Text.literal("Returned to the tower checkpoint."), false);
		}
		if (point.distanceSquared(player.getWorld().getRegistryKey().getValue().toString(), player.getX(), player.getY(), player.getZ()) > 36) {
			return message(player, feedback, "Claim remaining tower items within six blocks of your recovery checkpoint.", false);
		}
		var remaining = new ArrayList<ItemStack>();
		for (var stack : record.items()) {
			player.getInventory().insertStack(stack);
			if (!stack.isEmpty()) {
				remaining.add(stack);
			}
		}
		data.setTowerRecovery(player.getUuid(), remaining.isEmpty() ? null : new TowerRecoveryRecord(record.ticket(), remaining));
		player.sendMessage(Text.literal(remaining.isEmpty() ? "Tower recovery complete."
				: remaining.size() + " item stacks remain saved. Free inventory space, then use /tower recover here."), false);
		return true;
	}

	private static boolean safe(ServerWorld world, BlockPos feet) {
		return world != null && !RiftRuntime.inRifts(world) && feet.getY() > world.getBottomY() && feet.getY() + 1 < world.getTopY()
				&& world.isChunkLoaded(feet) && world.getWorldBorder().contains(feet) && world.getWorldBorder().contains(feet.up())
				&& world.getBlockState(feet).isAir() && world.getBlockState(feet.up()).isAir()
				&& world.getBlockState(feet.down()).isFullCube(world, feet.down())
				&& world.getBlockState(feet.down()).getFluidState().isEmpty() && !world.getBlockState(feet.down()).isOf(Blocks.MAGMA_BLOCK);
	}

	private static boolean message(ServerPlayerEntity player, boolean feedback, String text, boolean result) {
		if (feedback) {
			player.sendMessage(Text.literal(text), false);
		}
		return result;
	}
}
