package net.puffish.skillsmod.arpg.sandbox;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Runs without Minecraft/JUnit dependencies as well as through the JUnit wrapper. */
public final class SandboxEngineChecks {
	private static int checks;
	private SandboxEngineChecks() {
	}

	private static void check(boolean result, String name) {
		checks++;
		if (!result) {
			throw new AssertionError(name);
		}
	}

	private static void rejects(Runnable operation, String name) {
		try {
			operation.run();
		} catch (IllegalArgumentException | IllegalStateException | NullPointerException expected) {
			checks++;
			return;
		}

		throw new AssertionError("Expected rejection: " + name);
	}

	public static void main(String[] args) {
		checks = 0;
		var catalog = new SandboxCatalog(List.of(new SandboxCatalog.Module("arpg:craftwork", 1, Map.of())),
		List.of(new SandboxCatalog.Node("arpg:metalworking", "arpg:craftwork", Set.of()), new SandboxCatalog.Node("arpg:inscription", "arpg:craftwork", Set.of("arpg:metalworking"))),
		List.of(new SandboxCatalog.Operation("arpg:forge", "arpg:craftwork", "arpg:metalworking", "arpg:smithing", List.of("heat", "draw", "heavy", "quench"), 60, "arpg:sequence")));
		var empty = SandboxState.empty();
		rejects(() -> empty.learn(catalog, "arpg:inscription", Map.of()), "knowledge prerequisite");
		rejects(() -> empty.start(catalog, "arpg:forge", UUID.randomUUID(), 0, Map.of()), "locked recipe");
		var learned = empty.learn(catalog, "arpg:metalworking", Map.of());
		check(learned.learn(catalog, "arpg:metalworking", Map.of()).equals(learned), "idempotent knowledge");
		var id = UUID.randomUUID();
		var state = learned.start(catalog, "arpg:forge", id, 100, Map.of());
		final var active = state;
		rejects(() -> active.start(catalog, "arpg:forge", UUID.randomUUID(), 100, Map.of()), "one active session");
		rejects(() -> active.finish(id), "incomplete settlement");
		rejects(() -> active.act(0, "heat", 102), "server action rate");
		rejects(() -> active.act(1, "heat", 104), "out of order action");
		state = state.act(0, "wrong", 104);
		check(state.session().step() == 0 && state.session().mistakes() == 1, "wrong action reduces quality without advancing");
		state = state.act(0, "heat", 108);
		final var advanced = state;
		rejects(() -> advanced.act(0, "heat", 112), "replayed action");
		state = state.act(1, "draw", 112).act(2, "heavy", 116).act(3, "quench", 120);
		var finished = state.finish(id);
		check(finished.receipts().get(id).score() == 85, "quality calculated by server");
		check(finished.mastery().get("arpg:smithing") == 8, "sandbox mastery award");
		check(finished.finish(id).equals(finished), "duplicate completion cannot reward twice");
		rejects(() -> finished.start(catalog, "arpg:forge", id, 124, Map.of()), "settled ID cannot restart");
		var automated = learned.automated(catalog, "arpg:forge", UUID.randomUUID(), Map.of());
		check(automated.score() == 60 && automated.automated(), "automation baseline ceiling");
		check(learned.mastery().isEmpty(), "automation cannot grant manual mastery");
		rejects(() -> new SandboxState.Quality(UUID.randomUUID(), "arpg:forge", 100, true), "automation quality validation");
		var item = CraftedItemData.empty().add(finished.receipts().get(id));
		var rune = new SandboxState.Quality(UUID.randomUUID(), "arpg:inscribe", 70, false);
		check(item.add(rune).stages().size() == 2, "multiple quality stages preserve forging");
		rejects(() -> item.add(finished.receipts().get(id)), "same item cannot farm finishing repeatedly");
		var failed = active;
		for (int i = 1; i <= 8; i++) {
			failed = failed.act(0, "wrong", 100 + i * 4);
		}

		final var terminal = failed;
		check(terminal.session().failed(), "bounded failure");
		rejects(() -> terminal.act(0, "heat", 140), "failure cannot be retried indefinitely");
		check(terminal.cancel().session() == null, "cancellation releases session");
		var module = new SandboxCatalog.Module("test:tech", 1, Map.of("create", "6.0.10"));
		var integration = new SandboxCatalog(List.of(module), List.of(new SandboxCatalog.Node("test:coil", "test:tech", Set.of())), List.of());
		rejects(() -> empty.learn(integration, "test:coil", Map.of()), "missing optional provider");
		rejects(() -> empty.learn(integration, "test:coil", Map.of("create", "6.0.9")), "unsupported exact version");
		check(empty.learn(integration, "test:coil", Map.of("create", "6.0.10")).knowledge().contains("test:coil"), "future integration activates on supported provider");
		rejects(() -> new SandboxCatalog(List.of(module), List.of(new SandboxCatalog.Node("test:a", "test:tech", Set.of("test:b")), new SandboxCatalog.Node("test:b", "test:tech", Set.of("test:a"))), List.of()), "unlock cycle");
		rejects(() -> new SandboxCatalog(List.of(module), List.of(new SandboxCatalog.Node("test:a", "test:tech", Set.of("test:missing"))), List.of()), "missing unlock binding");
		rejects(() -> new SandboxCatalog(List.of(module, module), List.of(), List.of()), "duplicate module");
		rejects(() -> new SandboxCatalog.Operation("test:op", "test:tech", "test:coil", "test:mastery", List.of("act"), 100, "arpg:sequence"), "invalid automation definition");
		rejects(() -> new SandboxCatalog.Module("test:tech", 2, Map.of()), "future definition schema");
		check(finished.knowledge().contains("arpg:metalworking"), "knowledge retained after crafting");
		var core = SandboxCatalog.core();
		var thermal = SandboxState.empty().learn(core, "arpg:metalworking", Map.of()).start(core, "arpg:forge", UUID.randomUUID(), 100, Map.of());
		var perfectThermal = thermal.act(0, "heat", 104).act(1, "draw", 229).act(2, "heavy", 249).act(3, "quench", 304);
		check(perfectThermal.session().quality() == 100, "temperature management earns premium forging");
		var rushedThermal = thermal.act(0, "heat", 104).act(1, "draw", 108).act(2, "heavy", 112).act(3, "quench", 116);
		check(rushedThermal.session().quality() < 100, "rushed forging loses quality");
		rejects(() -> SandboxState.empty().learn(core, "arpg:metalworking", Map.of()).learn(core, "arpg:inscription", Map.of()), "sandbox mastery unlock requirement");
var runeState = perfectThermal.finish(perfectThermal.session().id()).learn(core, "arpg:inscription", Map.of()).start(core, "arpg:inscribe", UUID.randomUUID(), 100, Map.of());
		var runeDone = runeState.act(0, "north", 104).act(1, "east", 108).act(2, "south", 112).act(3, "west", 116).act(4, "seal", 120);
		check(runeDone.session().process().get("closed") == 1 && runeDone.session().quality() == 100, "closed rune route is stable");
		var runeBroken = runeState.act(0, "east", 104).act(0, "north", 108).act(1, "east", 112).act(2, "south", 116).act(3, "west", 120).act(4, "seal", 124);
		check(runeBroken.session().process().get("closed") == 0 && runeBroken.session().quality() < 100, "wrong stroke changes route and stability");
		CraftMechanics.register("test:custom", (session, action, tick, correct) -> new CraftMechanics.Result(12, Map.of("purity", 80)));
		check(CraftMechanics.available("test:custom"), "custom minigame evaluator registration");
		rejects(() -> CraftMechanics.register("test:custom", (session, action, tick, correct) -> new CraftMechanics.Result(0, Map.of())), "duplicate mechanic registration");
		var missingMechanicCatalog = new SandboxCatalog(List.of(new SandboxCatalog.Module("test:optional", 1, Map.of())),
List.of(new SandboxCatalog.Node("test:knowledge", "test:optional", Set.of())),
List.of(new SandboxCatalog.Operation("test:craft", "test:optional", "test:knowledge", "test:mastery", List.of("act"), 60, "test:missing")));
var retainedKnowledge = SandboxState.empty().learn(missingMechanicCatalog, "test:knowledge", Map.of());
rejects(() -> retainedKnowledge.requireRecipe(missingMechanicCatalog, "test:craft", Map.of()), "missing optional evaluator blocks crafting without deleting knowledge");

		check(core.binding("arpg:forge").ingredientItem().equals("minecraft:iron_ingot"), "data driven ingredient binding");
		check(core.binding("arpg:inscribe").targetItemTag().equals("minecraft:swords"), "data driven target eligibility");
		rejects(() -> new CraftBinding("arpg:forge", "minecraft:swords", "minecraft:anvil", "minecraft:iron_ingot", 0), "invalid ingredient quantity");
		rejects(() -> new SandboxCatalog(List.of(module), List.of(), List.of(), List.of(core.binding("arpg:forge"))), "unbound recipe rejected");
		var premium = CraftedItemData.empty().add(new SandboxState.Quality(UUID.randomUUID(), "arpg:forge", 100, false))
				.add(new SandboxState.Quality(UUID.randomUUID(), "arpg:inscribe", 100, false));
		var premiumBonuses = CraftQualityEffects.from(premium);
		check(Math.abs(premiumBonuses.meleeDamage() - 0.15) < 0.000001, "forging damage bounded to fifteen percent");
		check(Math.abs(premiumBonuses.spellDamage() - 0.08) < 0.000001, "inscription damage bounded to eight percent");
		check(CraftQualityEffects.from(CraftedItemData.empty()).equals(new CraftQualityEffects.Bonuses(0, 0)), "unworked items receive no quality bonus");
		var baselineItem = CraftedItemData.empty().add(learned.automated(catalog, "arpg:forge", UUID.randomUUID(), Map.of()));
		check(CraftQualityEffects.from(baselineItem).meleeDamage() < premiumBonuses.meleeDamage(), "automation preserves manual premium");
		rejects(() -> new SandboxCatalog.Operation("test:command", "test:tech", "test:coil", "test:mastery", List.of("two words"), 60, "arpg:sequence"), "actions are valid command tokens");
		System.out.println("Sandbox engine: " + checks + " checks passed");
	}
}
