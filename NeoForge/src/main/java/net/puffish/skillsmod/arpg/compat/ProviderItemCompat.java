package net.puffish.skillsmod.arpg.compat;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.HashSet;
import java.util.Set;

/** Aggregates item metadata from optional ARPG provider mods. */
public final class ProviderItemCompat {
	private ProviderItemCompat() {
	}

	public static Set<String> tags(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return Set.of();
		}

		var tags = new HashSet<>(ApotheosisItemCompat.tags(stack));
		var itemId = Registries.ITEM.getId(stack.getItem());
		if (itemId != null) {
			String namespace = itemId.getNamespace();
			if (ArpgProviderRegistry.loaded("l2artifacts") && "l2artifacts".equals(namespace)) {
				tags.add("artifact");
				tags.add("l2_artifact");
				tags.add("provider_item:" + itemId);
			}
			if (ArpgProviderRegistry.loaded("celestial_artifacts")
					&& ("celestial_artifacts".equals(namespace) || "celestialartifacts".equals(namespace))) {
				tags.add("artifact");
				tags.add("celestial_artifact");
				tags.add("provider_item:" + itemId);
			}
		}
		return Set.copyOf(tags);
	}
}
