package net.puffish.skillsmod.arpg.progression;

import com.google.gson.Gson;
import net.puffish.skillsmod.arpg.sandbox.SandboxCatalog;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompletionRewardTest {
	@Test
	void completionRegressionChecks() {
		CompletionRewardChecks.main(new String[0]);
	}

	@Test
	void bundledCompletionGraphMatchesDefaultsAndKnownRecipes() throws Exception {
		try (var reader = new InputStreamReader(getClass().getResourceAsStream(
				"/data/puffish_skills/arpg/completions.json"), StandardCharsets.UTF_8)) {
			var definitions = new Gson().fromJson(reader, CompletionData.Definitions.class);
			var catalog = new CompletionCatalog(definitions.rewards(), definitions.apotheosisWorldBoss());
			assertEquals(1, definitions.schema());
			assertEquals(CompletionCatalog.defaults().rewards(), catalog.rewards());
			assertEquals(CompletionCatalog.defaults().apotheosisWorldBoss(), catalog.apotheosisWorldBoss());
			for (var reward : catalog.rewards().values()) {
				for (String knowledge : reward.knowledge()) {
					assertEquals(knowledge, SandboxCatalog.core().node(knowledge).id());
				}
			}
		}
	}
}
