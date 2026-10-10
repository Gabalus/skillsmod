package net.puffish.skillsmod.arpg.tower;

import net.minecraft.nbt.NbtCompound;
import net.puffish.skillsmod.server.data.PlayerData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerPuzzlePersistenceTest {
	private static TowerPuzzle puzzle() {
		return new TowerPuzzle("arpg:test_relays", "arpg:descent", List.of(
				new TowerPuzzle.Relay("minecraft:overworld", 1, 64, 4, "minecraft:copper_block"),
				new TowerPuzzle.Relay("minecraft:overworld", 4, 64, 4, "minecraft:gold_block")));
	}

	@Test
	void playerSaveRetainsPartialAttemptAndSolvedReceipt() {
		var puzzle = puzzle();
		var player = PlayerData.empty();
		player.setTowerPuzzles(TowerPuzzleState.empty().press(puzzle, 0, 10));
		var restored = PlayerData.read(player.writeNbt(new NbtCompound()));
		assertEquals(player.getTowerPuzzles(), restored.getTowerPuzzles());
		assertFalse(restored.getTowerPuzzles().solved(puzzle));
		restored.setTowerPuzzles(restored.getTowerPuzzles().press(puzzle, 1, 14));
		assertTrue(PlayerData.read(restored.writeNbt(new NbtCompound())).getTowerPuzzles().solved(puzzle));
		assertTrue(PlayerData.read(new NbtCompound()).getTowerPuzzles().entries().isEmpty());
	}

	@Test
	void futureMalformedAndOversizedSavesAreRejected() {
		var root = TowerPuzzleStateNbt.write(TowerPuzzleState.empty().press(puzzle(), 0, 10));
		root.putInt("schema", 2);
		assertThrows(IllegalArgumentException.class, () -> TowerPuzzleStateNbt.read(root));
		root.putInt("schema", 1);
		root.getCompound("entries").getCompound(puzzle().id()).putInt("last_tick", 10);
		assertThrows(IllegalArgumentException.class, () -> TowerPuzzleStateNbt.read(root));
		var badPlayer = new NbtCompound();
		badPlayer.putString("tower_puzzles", "bad");
		assertThrows(IllegalArgumentException.class, () -> PlayerData.read(badPlayer));
		var oversized = new NbtCompound();
		oversized.putInt("schema", 1);
		var entries = new NbtCompound();
		for (int i = 0; i < 257; i++) {
			entries.put("arpg:puzzle_" + i, new NbtCompound());
		}
		oversized.put("entries", entries);
		assertThrows(IllegalArgumentException.class, () -> TowerPuzzleStateNbt.read(oversized));
	}
}
