package net.puffish.skillsmod.arpg.combat;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

public final class MeleeCooldownsNbt {
	private MeleeCooldownsNbt() {
	}

	public static NbtCompound write(MeleeCooldowns state) {
		var tag = new NbtCompound();
		tag.putInt("schema", 1);
		tag.putLong("heavy", state.heavy());
		tag.putLong("driving", state.driving());
		tag.putLong("commitment", state.commitment());
		return tag;
	}

	public static MeleeCooldowns read(NbtCompound tag) {
		if (tag.isEmpty()) {
			return MeleeCooldowns.empty();
		}
		if (!tag.contains("schema", NbtElement.INT_TYPE) || tag.getInt("schema") != 1
				|| !tag.contains("heavy", NbtElement.LONG_TYPE) || !tag.contains("driving", NbtElement.LONG_TYPE)
				|| !tag.contains("commitment", NbtElement.LONG_TYPE)) {
			throw new IllegalArgumentException("Malformed melee cooldown save");
		}
		return new MeleeCooldowns(tag.getLong("heavy"), tag.getLong("driving"), tag.getLong("commitment"));
	}
}
