package net.puffish.skillsmod.arpg.compat;

import net.minecraft.component.DataComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

/** Reads Better Combat's public preset-id data component without a hard Better Combat dependency. */
public final class BetterCombatItemCompat {
	private static final Identifier PRESET_ID = Identifier.of("bettercombat", "preset_id");

	private BetterCombatItemCompat() {
	}

	public static Set<String> tags(ItemStack stack) {
		if (!ArpgProviderRegistry.loaded("bettercombat") || stack == null || stack.isEmpty()) {
			return Set.of();
		}

		DataComponentType<?> componentType = Registries.DATA_COMPONENT_TYPE.get(PRESET_ID);
		if (componentType == null) {
			return Set.of();
		}

		Object preset = stack.get(componentType);
		if (preset == null) {
			return Set.of();
		}

		var tags = new HashSet<String>();
		tags.add("better_combat_weapon");
		tags.add("provider:bettercombat");
		String presetId = preset.toString().trim();
		if (!presetId.isEmpty()) {
			tags.add("bettercombat_preset:" + presetId);
		}
		return Set.copyOf(tags);
	}
}
