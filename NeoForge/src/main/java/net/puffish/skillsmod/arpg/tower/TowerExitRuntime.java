package net.puffish.skillsmod.arpg.tower;

import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.puffish.skillsmod.arpg.rift.RiftRuntime;

import java.util.Set;

public final class TowerExitRuntime {
	private static final String ATTEMPT = "puffish_skills.tower_exit_attempt";

	private TowerExitRuntime() {
	}

	public static void leave(ServerPlayerEntity player, TowerExit exit) {
		if (exit == null || player instanceof FakePlayer || !player.isAlive() || player.isCreative() || player.isSpectator()
				|| RiftRuntime.inRifts(player.getWorld()) || RiftRuntime.book(player.server).get(player.getUuid()) != null
				|| !exit.nearby(player.getWorld().getRegistryKey().getValue().toString(), player.getX(), player.getY(), player.getZ())) {
			throw new IllegalStateException("Stand within three blocks of an authored tower exit in survival, outside a rift session");
		}
		if (!TowerRecoveryRuntime.mayTravel(player)) {
			throw new IllegalStateException("Claim pending items with /tower recover before leaving the tower");
		}
		var marker = exit.marker();
		var pos = new BlockPos(marker.x(), marker.y(), marker.z());
		if (!player.getServerWorld().isChunkLoaded(pos) || !Registries.BLOCK.getId(player.getWorld().getBlockState(pos).getBlock()).toString().equals(marker.block())) {
			throw new IllegalStateException("The authored exit marker is missing; ask an operator to restore it");
		}
		long now = player.server.getOverworld().getTime();
		var persistent = player.getPersistentData();
		if (persistent.contains(ATTEMPT) && now >= persistent.getLong(ATTEMPT) && now - persistent.getLong(ATTEMPT) < 40) {
			throw new IllegalStateException("Wait two seconds before retrying the tower exit");
		}
		persistent.putLong(ATTEMPT, now);
		var world = player.server.getOverworld();
		var spawn = world.getSpawnPos();
		var target = TowerLandingSearch.find(spawn.getX(), spawn.getY(), spawn.getZ(), point -> {
			var feet = new BlockPos(point.x(), point.y(), point.z());
			return !TowerData.protection().protects(world.getRegistryKey().getValue().toString(), point.x(), point.y(), point.z())
					&& TowerSafeLanding.safe(world, feet);
		}, (x, z) -> world.isChunkLoaded(new BlockPos(x, spawn.getY(), z))
				? world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) : Integer.MIN_VALUE);
		if (target == null) {
			throw new IllegalStateException("No clear loaded sandbox landing near overworld spawn. Ask an operator to restore/load it; you remain in the tower");
		}
		if (!player.teleport(world, target.x() + .5, target.y(), target.z() + .5, Set.of(), player.getYaw(), player.getPitch())) {
			throw new IllegalStateException("Tower departure failed; try again");
		}
		ImmersiveTowerPortals.close(player.getUuid());
		player.fallDistance = 0;
		player.sendMessage(Text.literal("Returned to the sandbox near overworld spawn."), false);
	}
}
