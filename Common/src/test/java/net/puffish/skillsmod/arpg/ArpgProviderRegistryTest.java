package net.puffish.skillsmod.arpg;

import net.puffish.skillsmod.arpg.compat.ArpgProviderRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArpgProviderRegistryTest {
	@AfterEach
	void reset() {
		ArpgProviderRegistry.configure(Map.of());
	}

	@Test
	void missingProvidersDefaultToInactive() {
		ArpgProviderRegistry.configure(Map.of());

		assertFalse(ArpgProviderRegistry.loaded("irons_spellbooks"));
		assertFalse(ArpgProviderRegistry.loaded("bettercombat"));
		assertFalse(ArpgProviderRegistry.loaded("apotheosis"));
		assertFalse(ArpgProviderRegistry.loaded("l2hostility"));
		assertFalse(ArpgProviderRegistry.loaded("l2artifacts"));
		assertFalse(ArpgProviderRegistry.loaded("celestial_artifacts"));
	}

	@Test
	void configuredProvidersAreReportedIndependently() {
		ArpgProviderRegistry.configure(Map.of(
				"irons_spellbooks", true,
				"bettercombat", true,
				"apotheosis", false,
				"l2hostility", true
		));

		assertTrue(ArpgProviderRegistry.loaded("irons_spellbooks"));
		assertTrue(ArpgProviderRegistry.loaded("bettercombat"));
		assertTrue(ArpgProviderRegistry.loaded("l2hostility"));
		assertFalse(ArpgProviderRegistry.loaded("apotheosis"));
		assertFalse(ArpgProviderRegistry.loaded("l2artifacts"));
		assertFalse(ArpgProviderRegistry.loaded("celestial_artifacts"));
	}
}
