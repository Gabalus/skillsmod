package net.puffish.skillsmod.arpg.tower;

import com.google.gson.Gson;
import net.minecraft.server.MinecraftServer;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.arpg.progression.CompletionData;

import java.util.List;
import java.util.Set;

/** Atomically validates authored seams; no terrain writes or generated dimensions. */
public final class TowerData {
	public record Definitions(int schema, List<TowerLink> links) {
	}

	private static volatile TowerCatalog catalog = new TowerCatalog(List.of(), Set.of());
	private static long revision;

	private TowerData() {
	}

	public static TowerCatalog catalog() {
		return catalog;
	}

	public static long revision() {
		return revision;
	}

	public static void reload(MinecraftServer server) {
		try (var reader = server.getResourceManager().getResourceOrThrow(
				SkillsMod.createIdentifier("arpg/tower_links.json")).getReader()) {
			var definitions = new Gson().fromJson(reader, Definitions.class);
			if (definitions == null || definitions.schema() != 1) {
				throw new IllegalArgumentException("Unsupported tower link schema");
			}
			catalog = new TowerCatalog(definitions.links(), CompletionData.catalog().rewards().keySet());
			revision++;
		} catch (Exception error) {
			SkillsMod.getInstance().getLogger().error("Tower reload rejected; keeping previous network: " + error.getMessage());
		}
	}
}
