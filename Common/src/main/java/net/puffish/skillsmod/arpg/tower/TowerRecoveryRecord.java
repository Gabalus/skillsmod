package net.puffish.skillsmod.arpg.tower;

import net.minecraft.item.ItemStack;

import java.util.List;

/** Copies stacks at both boundaries so inventory insertion cannot mutate saved recovery items. */
public record TowerRecoveryRecord(TowerRecoveryTicket ticket, List<ItemStack> items) {
	public static final int MAX_ITEMS = 1024;

	public TowerRecoveryRecord {
		if (ticket == null || items == null || items.size() > MAX_ITEMS) {
			throw new IllegalArgumentException("Invalid tower recovery record or item capacity exceeded");
		}
		items = items.stream().filter(item -> !item.isEmpty()).map(ItemStack::copy).toList();
	}

	@Override
	public List<ItemStack> items() {
		return items.stream().map(ItemStack::copy).toList();
	}
}
