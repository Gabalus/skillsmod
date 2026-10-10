package net.puffish.skillsmod.arpg.tower;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.HashMap;

/** Versioned, bounded persistence; corrupt and future saves fail explicitly. */
public final class TowerPuzzleStateNbt {
	private TowerPuzzleStateNbt() {
	}

	public static NbtCompound write(TowerPuzzleState state) {
		var root = new NbtCompound();
		root.putInt("schema", 1);
		var entries = new NbtCompound();
		state.entries().forEach((id, progress) -> {
			var tag = new NbtCompound();
			tag.putString("fingerprint", progress.fingerprint());
			tag.putInt("step", progress.step());
			tag.putLong("last_tick", progress.lastTick());
			entries.put(id, tag);
		});
		root.put("entries", entries);
		return root;
	}

	public static TowerPuzzleState read(NbtCompound root) {
		if (root.isEmpty()) {
			return TowerPuzzleState.empty();
		}
		if (!root.contains("schema", NbtElement.INT_TYPE) || root.getInt("schema") != 1 || !root.contains("entries", NbtElement.COMPOUND_TYPE)) {
			throw new IllegalArgumentException("Invalid tower puzzle save schema or entries");
		}
		var saved = root.getCompound("entries");
		if (saved.getKeys().size() > 256) {
			throw new IllegalArgumentException("Too many saved tower puzzles");
		}
		var entries = new HashMap<String, TowerPuzzleState.Progress>();
		for (var id : saved.getKeys()) {
			if (!(saved.get(id) instanceof NbtCompound tag) || !tag.contains("fingerprint", NbtElement.STRING_TYPE)
					|| !tag.contains("step", NbtElement.INT_TYPE) || !tag.contains("last_tick", NbtElement.LONG_TYPE)) {
				throw new IllegalArgumentException("Malformed tower puzzle entry");
			}
			entries.put(id, new TowerPuzzleState.Progress(tag.getString("fingerprint"), tag.getInt("step"), tag.getLong("last_tick")));
		}
		return new TowerPuzzleState(entries);
	}
}
