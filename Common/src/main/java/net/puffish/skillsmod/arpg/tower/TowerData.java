package net.puffish.skillsmod.arpg.tower;

import com.google.gson.Gson;
import net.minecraft.server.MinecraftServer;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.arpg.progression.CompletionData;

import java.util.List;
import java.util.Set;

/** Atomically validates authored seams; no terrain writes or generated dimensions. */
public final class TowerData {
	public record Definitions(int schema, List<TowerLink> links, List<TowerSector> sectors) {
	}

	private record Network(TowerCatalog catalog, TowerProtection protection) {
	}

	private static volatile Network network = empty();
	private static long revision;

	private TowerData() {
	}

	public static TowerCatalog catalog() {
		return network.catalog();
	}

	public static TowerProtection protection() {
		return network.protection();
	}

	private static Network empty() {
		var catalog = new TowerCatalog(List.of(), Set.of());
		return new Network(catalog, new TowerProtection(List.of(), catalog));
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
			var catalog = new TowerCatalog(definitions.links(), CompletionData.catalog().rewards().keySet());
			var protection = new TowerProtection(definitions.sectors() == null ? List.of() : definitions.sectors(), catalog);
			network = new Network(catalog, protection);
			revision++;
		} catch (Exception error) {
			SkillsMod.getInstance().getLogger().error("Tower reload rejected; keeping previous network: " + error.getMessage());
		}
	}
}
