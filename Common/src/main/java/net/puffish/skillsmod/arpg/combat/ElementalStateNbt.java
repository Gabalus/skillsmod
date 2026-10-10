package net.puffish.skillsmod.arpg.combat;

import net.minecraft.nbt.NbtCompound;

/** Separate schema keeps existing damage-over-time saves compatible. */
public final class ElementalStateNbt {
	private ElementalStateNbt() {
	}

	public static NbtCompound write(ElementalState state) {
		var nbt = new NbtCompound();
		nbt.putInt("schema", 1);
		nbt.putInt("wet", state.wetTicks());
		nbt.putInt("chill_stacks", state.chillStacks());
		nbt.putInt("chill", state.chillTicks());
		nbt.putInt("freeze", state.freezeTicks());
		nbt.putInt("freeze_recovery", state.freezeRecoveryTicks());
		if (state.coldOwner() != null) {
			nbt.putUuid("cold_owner", state.coldOwner());
		}
		nbt.putString("cold_skill", state.coldSkill());
		return nbt;
	}

	public static ElementalState read(NbtCompound nbt) {
		try {
			if (nbt.getInt("schema") != 1) {
				return ElementalState.empty();
			}
			return new ElementalState(nbt.getInt("wet"), nbt.getInt("chill_stacks"), nbt.getInt("chill"),
					nbt.getInt("freeze"), nbt.getInt("freeze_recovery"),
					nbt.contains("cold_owner") ? nbt.getUuid("cold_owner") : null, nbt.getString("cold_skill"));
		} catch (RuntimeException error) {
			return ElementalState.empty();
		}
	}
}
