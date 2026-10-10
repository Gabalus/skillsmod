package net.puffish.skillsmod.arpg.progression;

import com.google.gson.Gson;
import net.minecraft.server.MinecraftServer;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.arpg.sandbox.SandboxData;

import java.util.List;

/** Data-pack completion rewards with graph/recipe validation and atomic catalog replacement. */
public final class CompletionData {
	public record Definitions(int schema, List<CompletionReward> rewards, String apotheosisWorldBoss) {
	}

	private static final Gson GSON = new Gson();
	private static volatile CompletionCatalog catalog = CompletionCatalog.defaults();

	private CompletionData() {
	}

	public static CompletionCatalog catalog() {
		return catalog;
	}

	public static void reload(MinecraftServer server) {
		try (var reader = server.getResourceManager().getResourceOrThrow(
				SkillsMod.createIdentifier("arpg/completions.json")).getReader()) {
			var definitions = GSON.fromJson(reader, Definitions.class);
			if (definitions == null || definitions.schema() != 1) {
				throw new IllegalArgumentException("Unsupported completion catalog schema");
			}
			var replacement = new CompletionCatalog(definitions.rewards(), definitions.apotheosisWorldBoss());
			for (var reward : replacement.rewards().values()) {
				for (String knowledge : reward.knowledge()) {
					SandboxData.catalog().node(knowledge);
				}
			}
			catalog = replacement;
		} catch (Exception error) {
			SkillsMod.getInstance().getLogger().error("Completion reload rejected; keeping previous catalog: " + error.getMessage());
		}
	}
}
