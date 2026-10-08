package net.puffish.skillsmod.arpg.tower;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerCatalogTest {
	@Test
	void gateAndAnchorRegressionChecks() {
		TowerEngineChecks.main(new String[0]);
	}

	@Test
	void bundledNetworkIsOptIn() throws Exception {
		try (var reader = new InputStreamReader(getClass().getResourceAsStream(
				"/data/puffish_skills/arpg/tower_links.json"), StandardCharsets.UTF_8)) {
			var definitions = new Gson().fromJson(reader, TowerData.Definitions.class);
			assertTrue(new TowerCatalog(definitions.links(), Set.of()).links().isEmpty());
		}
	}
}
