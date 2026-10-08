package net.puffish.skillsmod.arpg.sandbox;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable integration definitions. Provider absence disables a branch, never player knowledge. */
public final class SandboxCatalog {
	public record Module(String id, int schema, Map<String, String> requiredVersions) {
		public Module {
			checkId(id);
			if (schema != 1) {
				throw new IllegalArgumentException("Unsupported module schema: " + schema);
			}

			requiredVersions = Map.copyOf(requiredVersions);
			if (requiredVersions.values().stream().anyMatch(String::isBlank)) {
				throw new IllegalArgumentException("Empty provider version");
			}
		}
	}

	public record Node(String id, String module, Set<String> prerequisites, Map<String, Integer> masteryRequirements) {
		public Node(String id, String module, Set<String> prerequisites) {
			this(id, module, prerequisites, Map.of());
		}
		public Node {
			checkId(id);
			checkId(module);
			prerequisites = Set.copyOf(prerequisites);
			prerequisites.forEach(SandboxCatalog::checkId);
			masteryRequirements = masteryRequirements == null ? Map.of() : Map.copyOf(masteryRequirements);
			masteryRequirements.forEach((key, threshold) -> {
				checkId(key);
				if (threshold < 0 || threshold > 10000) {
					throw new IllegalArgumentException("Invalid mastery prerequisite");
				}
			});
		}
	}

	public record Operation(String id, String module, String knowledge, String mastery, List<String> sequence, int automatedCeiling, String mechanic) {
		public Operation {
			checkId(id);
			checkId(module);
			checkId(knowledge);
			checkId(mastery);
			checkId(mechanic);

			sequence = List.copyOf(sequence);
			if (sequence.isEmpty() || sequence.size() > 64 || sequence.stream().anyMatch(action -> !action.matches("[a-z0-9_.:-]{1,128}"))) {
				throw new IllegalArgumentException("Invalid operation sequence");
			}

			if (automatedCeiling < 0 || automatedCeiling > 60) {
				throw new IllegalArgumentException("Automation exceeds baseline quality");
			}
		}
	}

	private final Map<String, Module> modules;
	private final Map<String, Node> nodes;
	private final Map<String, Operation> operations;
	private final Map<String, CraftBinding> bindings;
	public SandboxCatalog(List<Module> modules, List<Node> nodes, List<Operation> operations) {
		this(modules, nodes, operations, List.of());
	}

	public SandboxCatalog(List<Module> modules, List<Node> nodes, List<Operation> operations, List<CraftBinding> bindings) {
		this.modules = index(modules, Module::id);
		this.nodes = index(nodes, Node::id);
		this.operations = index(operations, Operation::id);
		this.bindings = index(bindings, CraftBinding::operation);
		for (var binding : bindings) {
			if (!this.operations.containsKey(binding.operation())) {
				throw new IllegalArgumentException("Binding references unknown operation " + binding.operation());
			}
		}
		for (var node : nodes) {
			if (!this.modules.containsKey(node.module())) {
				throw new IllegalArgumentException("Unknown module " + node.module());
			}

			for (var parent : node.prerequisites()) {
				if (!this.nodes.containsKey(parent)) {
					throw new IllegalArgumentException("Unknown prerequisite " + parent);
				}
			}
		}

		var visiting = new HashSet<String>();
		var visited = new HashSet<String>();
		for (var id : this.nodes.keySet()) {
			visit(id, visiting, visited);
		}

		for (var operation : operations) {
			if (!this.modules.containsKey(operation.module()) || !this.nodes.containsKey(operation.knowledge())) {
				throw new IllegalArgumentException("Unbound operation " + operation.id());
			}

			if (!this.nodes.get(operation.knowledge()).module().equals(operation.module())) {
				throw new IllegalArgumentException("Operation knowledge belongs to another module");
			}
		}
	}

	private void visit(String id, Set<String> visiting, Set<String> visited) {
		if (visited.contains(id)) {
			return;
		}

		if (!visiting.add(id)) {
			throw new IllegalArgumentException("Unlock cycle at " + id);
		}

		for (var parent : nodes.get(id).prerequisites()) {
			visit(parent, visiting, visited);
		}

		visiting.remove(id);
		visited.add(id);
	}

	private static <T> Map<String, T> index(List<T> entries, java.util.function.Function<T, String> id) {
		var result = new LinkedHashMap<String, T>();
		for (var entry : entries) {
			if (result.putIfAbsent(id.apply(entry), entry) != null) {
				throw new IllegalArgumentException("Duplicate ID " + id.apply(entry));
			}
		}

		return Collections.unmodifiableMap(result);
	}

	public static void checkId(String id) {
		if (id == null || id.length() > 256 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
			throw new IllegalArgumentException("Expected namespaced ID: " + id);
		}
	}

	public Map<String, Operation> operations() {
		return operations;
	}

	public Map<String, CraftBinding> bindings() {
		return bindings;
	}

	public CraftBinding binding(String operation) {
		var binding = bindings.get(operation);
		if (binding == null) {
			throw new IllegalStateException("No station binding for " + operation);
		}
		return binding;
	}

	public Node node(String id) {
		return Objects.requireNonNull(nodes.get(id), "Unknown knowledge " + id);
	}

	public Operation operation(String id) {
		return Objects.requireNonNull(operations.get(id), "Unknown operation " + id);
	}

	public boolean available(String module, Map<String, String> installedVersions) {
		var definition = Objects.requireNonNull(modules.get(module), "Unknown module " + module);
		return definition.requiredVersions().entrySet().stream().allMatch(e -> e.getValue().equals(installedVersions.get(e.getKey())));
	}

	public static SandboxCatalog core() {
		return new SandboxCatalog(List.of(new Module("arpg:craftwork", 1, Map.of())),
		List.of(new Node("arpg:metalworking", "arpg:craftwork", Set.of()),
		new Node("arpg:inscription", "arpg:craftwork", Set.of("arpg:metalworking"), Map.of("arpg:smithing", 10))),
		List.of(new Operation("arpg:forge", "arpg:craftwork", "arpg:metalworking", "arpg:smithing", List.of("heat", "draw", "heavy", "quench"), 60, "arpg:thermal_forge"),
		new Operation("arpg:inscribe", "arpg:craftwork", "arpg:inscription", "arpg:runecraft", List.of("north", "east", "south", "west", "seal"), 60, "arpg:rune_route")),
		List.of(new CraftBinding("arpg:forge", "minecraft:swords", "minecraft:anvil", "minecraft:iron_ingot", 1),
		new CraftBinding("arpg:inscribe", "minecraft:swords", "minecraft:anvil", "minecraft:lapis_lazuli", 1)));
	}
}
