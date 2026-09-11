package net.puffish.skillsmod.arpg.compat;

import java.util.List;
import java.util.Map;

/**
 * Loader-neutral snapshot of external ARPG provider mods that are present at runtime.
 *
 * <p>The custom ARPG core remains authoritative. Providers supply execution/content substrates
 * (spells, basic combat, affix/item mechanics, elite traits and artifact effects) and may be absent
 * without making the core fail to load.</p>
 */
public final class ArpgProviderRegistry {
	private static final List<Definition> DEFINITIONS = List.of(
			new Definition("irons_spellbooks", "Iron's Spells 'n Spellbooks", "spell execution/content"),
			new Definition("bettercombat", "Better Combat", "basic melee/animation substrate"),
			new Definition("apotheosis", "Apotheosis", "affix/gem/item substrate"),
			new Definition("l2hostility", "L2 Hostility", "elite/monster trait substrate"),
			new Definition("l2artifacts", "L2 Artifacts", "artifact/set-effect substrate"),
			new Definition("celestial_artifacts", "Celestial Artifacts", "curio/unique-effect substrate")
	);

	private static volatile List<Provider> providers = DEFINITIONS.stream()
			.map(definition -> new Provider(definition.id(), definition.name(), definition.role(), false))
			.toList();

	private ArpgProviderRegistry() {
	}

	/** Called by the loader adapter after its mod list is available. */
	public static void configure(Map<String, Boolean> loadedById) {
		providers = DEFINITIONS.stream()
				.map(definition -> new Provider(
						definition.id(),
						definition.name(),
						definition.role(),
						loadedById.getOrDefault(definition.id(), false)
				))
				.toList();
	}

	public static List<Provider> all() {
		return providers;
	}

	public static boolean loaded(String id) {
		return providers.stream().anyMatch(provider -> provider.id().equals(id) && provider.loaded());
	}

	public record Provider(String id, String name, String role, boolean loaded) {
	}

	private record Definition(String id, String name, String role) {
	}
}
