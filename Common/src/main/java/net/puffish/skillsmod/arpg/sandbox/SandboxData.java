package net.puffish.skillsmod.arpg.sandbox;

import com.google.gson.Gson;
import net.minecraft.server.MinecraftServer;
import net.puffish.skillsmod.SkillsMod;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Data packs add arpg/sandbox/*.json definitions. Reload publishes only a validated catalog. */
public final class SandboxData {
	public record Definitions(List<SandboxCatalog.Module> modules, List<SandboxCatalog.Node> nodes, List<SandboxCatalog.Operation> operations, List<CraftBinding> bindings) {
	}

	private static final Gson GSON = new Gson();
	private static volatile SandboxCatalog catalog = SandboxCatalog.core();
	private static volatile Map<String, String> providers = Map.of();
	private SandboxData() {
	}

	public static SandboxCatalog catalog() {
		return catalog;
	}

	public static Map<String, String> providers() {
		return providers;
	}

	public static void configureProviders(Map<String, String> versions) {
		providers = Map.copyOf(versions);
	}

	public static void reload(MinecraftServer server) {
		var modules = new ArrayList<SandboxCatalog.Module>();
		var nodes = new ArrayList<SandboxCatalog.Node>();
		var operations = new ArrayList<SandboxCatalog.Operation>();
		var bindings = new ArrayList<CraftBinding>();
		try {
			var resources = server.getResourceManager().findResources("arpg/sandbox", id -> id.getPath().endsWith(".json"));
			if (resources.isEmpty()) {
				throw new IllegalArgumentException("Missing sandbox definitions");
			}

			for (var entry : resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
				try (var reader = entry.getValue().getReader()) {
					var data = GSON.fromJson(reader, Definitions.class);
					if (data == null) {
						throw new IllegalArgumentException("Empty sandbox definitions");
					}

					modules.addAll(data.modules());
					nodes.addAll(data.nodes());
					operations.addAll(data.operations());
					if (data.bindings() != null) {
						bindings.addAll(data.bindings());
					}
				}
			}

			var replacement = new SandboxCatalog(modules, nodes, operations, bindings);
			for (var binding : bindings) {
				if (replacement.available(replacement.operation(binding.operation()).module(), providers)
						&& !net.minecraft.registry.Registries.ITEM.containsId(net.minecraft.util.Identifier.of(binding.ingredientItem()))) {
					throw new IllegalArgumentException("Unknown finishing ingredient " + binding.ingredientItem());
				}
			}
			catalog = replacement;
		} catch (Exception error) {
			SkillsMod.getInstance().getLogger().error("Sandbox reload rejected; keeping previous definitions: " + error.getMessage());
		}
	}
}
