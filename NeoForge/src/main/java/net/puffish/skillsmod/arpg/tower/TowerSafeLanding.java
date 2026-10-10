package net.puffish.skillsmod.arpg.tower;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.puffish.skillsmod.arpg.rift.RiftRuntime;

/** Shared conservative standing clearance; does not load chunks or write terrain. */
public final class TowerSafeLanding {
	private TowerSafeLanding() {
	}

	public static boolean safe(ServerWorld world, BlockPos feet) {
		return world != null && !RiftRuntime.inRifts(world) && feet.getY() > world.getBottomY() && feet.getY() + 1 < world.getTopY()
				&& world.isChunkLoaded(feet) && world.getWorldBorder().contains(feet) && world.getWorldBorder().contains(feet.up())
				&& world.getBlockState(feet).isAir() && world.getBlockState(feet.up()).isAir()
				&& world.getBlockState(feet.down()).isFullCube(world, feet.down())
				&& world.getBlockState(feet.down()).getFluidState().isEmpty() && !world.getBlockState(feet.down()).isOf(Blocks.MAGMA_BLOCK);
	}
}
