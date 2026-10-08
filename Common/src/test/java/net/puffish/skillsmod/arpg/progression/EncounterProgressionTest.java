package net.puffish.skillsmod.arpg.progression;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncounterProgressionTest {
	@Test
	void encounterRewardRegressionChecks() {
		EncounterProgressionChecks.main(new String[0]);
	}

	@Test
	void bundledPolicyMatchesValidatedDefaults() throws Exception {
		try (var reader = new InputStreamReader(getClass().getResourceAsStream(
				"/data/puffish_skills/arpg/encounter_progression.json"), StandardCharsets.UTF_8)) {
			assertEquals(EncounterProgressionPolicy.defaults(), new Gson().fromJson(reader, EncounterProgressionPolicy.class));
		}
	}

	@Test
	void incompleteJsonCannotBypassCanonicalValidation() {
		assertThrows(RuntimeException.class, () -> new Gson().fromJson("{\"schema\":1}", EncounterProgressionPolicy.class));
	}
}
