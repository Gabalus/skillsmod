package net.puffish.skillsmod.arpg.compat;

import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Optional reflection-only metadata adapter for Apotheosis affix items. */
public final class ApotheosisItemCompat {
	private static volatile Access access;
	private static volatile boolean resolutionAttempted;

	private ApotheosisItemCompat() {
	}

	public static Set<String> tags(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !ArpgProviderRegistry.loaded("apotheosis")) {
			return Set.of();
		}
		var resolved = resolveAccess();
		if (resolved == null) {
			return Set.of();
		}

		try {
			var tags = new HashSet<String>();
			Object rawAffixes = resolved.getAffixes().invoke(null, stack);
			if (rawAffixes instanceof Map<?, ?> affixes && !affixes.isEmpty()) {
				tags.add("apotheosis");
				tags.add("affixed_item");
				for (var instance : affixes.values()) {
					String id = affixId(instance);
					if (!id.isBlank()) {
						tags.add("apoth_affix:" + id);
					}
				}
			}

			Object rarityHolder = resolved.getRarity().invoke(null, stack);
			String rarity = holderId(rarityHolder);
			if (!rarity.isBlank()) {
				tags.add("apotheosis");
				tags.add("apoth_rarity:" + rarity);
			}
			return Set.copyOf(tags);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return Set.of();
		}
	}

	private static Access resolveAccess() {
		if (resolutionAttempted) {
			return access;
		}
		synchronized (ApotheosisItemCompat.class) {
			if (resolutionAttempted) {
				return access;
			}
			resolutionAttempted = true;
			try {
				Class<?> helper = Class.forName("dev.shadowsoffire.apotheosis.affix.AffixHelper");
				access = new Access(
						helper.getMethod("getAffixes", Class.forName("net.minecraft.world.item.ItemStack")),
						helper.getMethod("getRarity", Class.forName("net.minecraft.world.item.ItemStack"))
				);
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				access = null;
			}
			return access;
		}
	}

	private static String affixId(Object instance) {
		if (instance == null) {
			return "";
		}
		try {
			Object holder = instance.getClass().getMethod("affix").invoke(instance);
			return holderId(holder);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return "";
		}
	}

	private static String holderId(Object holder) {
		if (holder == null) {
			return "";
		}
		try {
			Object id = holder.getClass().getMethod("getId").invoke(holder);
			return id == null ? "" : id.toString();
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return "";
		}
	}

	private record Access(Method getAffixes, Method getRarity) {
	}
}
