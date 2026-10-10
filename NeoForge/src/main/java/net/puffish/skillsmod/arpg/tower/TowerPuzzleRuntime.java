package net.puffish.skillsmod.arpg.tower;

import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.puffish.skillsmod.arpg.rift.RiftRuntime;
import net.puffish.skillsmod.server.data.ServerData;

/** Server-owned relay input and portal gating; no progression currency is awarded. */
public final class TowerPuzzleRuntime {
	private TowerPuzzleRuntime() {
	}

	public static boolean solved(ServerPlayerEntity player, String link) {
		var puzzle = TowerData.puzzles().links().get(link);
		return puzzle == null || ServerData.getOrCreate(player.server).getPlayerData(player).getTowerPuzzles().solved(puzzle);
	}

	public static boolean press(ServerPlayerEntity player, BlockPos pos) {
		var world = player.getServerWorld();
		var click = TowerData.puzzles().click(world.getRegistryKey().getValue().toString(), pos.getX(), pos.getY(), pos.getZ());
		if (click == null) {
			return false;
		}
		if (!player.isAlive() || player.isCreative() || player.isSpectator() || RiftRuntime.inRifts(world)
				|| RiftRuntime.book(player.server).get(player.getUuid()) != null
				|| player.squaredDistanceTo(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 36) {
			throw new IllegalStateException("Activate tower relays in survival, within six blocks, outside a rift session");
		}
		var puzzle = click.puzzle();
		if (!Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString().equals(puzzle.sequence().get(click.index()).block())) {
			throw new IllegalStateException("This relay's authored block is missing; ask the operator to restore it");
		}
		var data = ServerData.getOrCreate(player.server).getPlayerData(player);
		var previous = data.getTowerPuzzles();
		var next = previous.press(puzzle, click.index(), player.server.getOverworld().getTime());
		data.setTowerPuzzles(next);
		if (next.solved(puzzle)) {
			player.sendMessage(Text.literal("Relay sequence solved. Your passage is unlocked; activate its lodestone."), true);
		} else if (next != previous) {
			int step = next.entries().get(puzzle.id()).step();
			player.sendMessage(Text.literal(step == 0 ? "Wrong relay. Sequence reset."
					: "Relay " + step + "/" + puzzle.sequence().size() + " activated."), true);
		}
		return true;
	}
}
