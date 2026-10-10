package net.puffish.skillsmod.arpg.tower;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.List;

/** Encoded item snapshots avoid registry dependencies during saves and preserve unavailable mod items. */
public record TowerRecoveryRecord(TowerRecoveryTicket ticket, List<NbtCompound> items) {
	public static final int MAX_ITEMS = 1024;

	public TowerRecoveryRecord {
		if (ticket == null || items == null || items.size() > MAX_ITEMS) {
			throw new IllegalArgumentException("Invalid tower recovery record or item capacity exceeded");
		}
		for (var item : items) {
			if (item == null || !item.contains("id", NbtElement.STRING_TYPE) || item.getString("id").isEmpty()
					|| item.contains("count") && (!item.contains("count", NbtElement.INT_TYPE) || item.getInt("count") < 1)
					|| item.contains("components") && !item.contains("components", NbtElement.COMPOUND_TYPE)) {
				throw new IllegalArgumentException("Malformed tower item snapshot");
			}
		}
		items = items.stream().map(NbtCompound::copy).toList();
	}

	@Override
	public List<NbtCompound> items() {
		return items.stream().map(NbtCompound::copy).toList();
	}
}
