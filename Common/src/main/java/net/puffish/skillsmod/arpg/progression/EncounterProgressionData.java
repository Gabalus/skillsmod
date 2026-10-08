package net.puffish.skillsmod.arpg.progression;

import com.google.gson.Gson;
import net.minecraft.server.MinecraftServer;
import net.puffish.skillsmod.SkillsMod;

/** A single overridable policy; invalid reloads preserve the previous validated policy. */
public final class EncounterProgressionData {
	private static final Gson GSON = new Gson();
	private static volatile EncounterProgressionPolicy policy = EncounterProgressionPolicy.defaults();

	private EncounterProgressionData() {
	}

	public static EncounterProgressionPolicy policy() {
		return policy;
	}

	public static void reload(MinecraftServer server) {
		try (var reader = server.getResourceManager().getResourceOrThrow(
				SkillsMod.createIdentifier("arpg/encounter_progression.json")).getReader()) {
			var replacement = GSON.fromJson(reader, EncounterProgressionPolicy.class);
			if (replacement == null) {
				throw new IllegalArgumentException("Missing encounter progression policy");
			}
			policy = replacement;
		} catch (Exception error) {
			SkillsMod.getInstance().getLogger().error("Encounter progression reload rejected; keeping previous policy: " + error.getMessage());
		}
	}
}
