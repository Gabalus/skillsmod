package net.puffish.skillsmod.arpg.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Loader-neutral bridge for target metadata supplied by optional provider mods.
 *
 * <p>Provider adapters may expose tags such as {@code elite} or an external trait id. Combat
 * systems consume only these normalized tags and never depend directly on provider classes.</p>
 */
public final class ArpgTargetTags {
	private static volatile Function<LivingEntity, Set<String>> resolver = entity -> Set.of();

	private ArpgTargetTags() {
	}

	public static void configure(Function<LivingEntity, Set<String>> targetResolver) {
		resolver = targetResolver == null ? entity -> Set.of() : targetResolver;
	}

	public static Set<String> get(Entity target) {
		if (!(target instanceof LivingEntity living)) {
			return Set.of();
		}
		var tags = resolver.apply(living);
		return tags == null || tags.isEmpty() ? Set.of() : Set.copyOf(tags);
	}

	public static Set<String> merge(Set<String> base, Entity target) {
		var external = get(target);
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
