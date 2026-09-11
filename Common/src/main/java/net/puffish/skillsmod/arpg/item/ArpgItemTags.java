package net.puffish.skillsmod.arpg.item;

import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/** Loader-neutral item metadata bridge for optional ARPG provider mods. */
public final class ArpgItemTags {
	private static volatile Function<ItemStack, Set<String>> resolver = stack -> Set.of();

	private ArpgItemTags() {
	}

	public static void configure(Function<ItemStack, Set<String>> itemResolver) {
		resolver = itemResolver == null ? stack -> Set.of() : itemResolver;
	}

	public static Set<String> get(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return Set.of();
		}
		var tags = resolver.apply(stack);
		return tags == null || tags.isEmpty() ? Set.of() : Set.copyOf(tags);
	}

	public static Set<String> merge(Set<String> base, ItemStack stack) {
		var external = get(stack);
		if (external.isEmpty()) {
			return base == null ? Set.of() : Set.copyOf(base);
		}
		var merged = new HashSet<String>();
		if (base != null) {
			merged.addAll(base);
		}
		merged.addAll(external);
		return Set.copyOf(merged);
	}
}
