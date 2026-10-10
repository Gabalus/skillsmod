package net.puffish.skillsmod.arpg.tower;

import java.util.List;
import java.util.Set;

public final class TowerEngineChecks {
	private static int checks;

	private TowerEngineChecks() {
	}

	private static void check(boolean value) {
		checks++;
		if (!value) {
			throw new AssertionError("Tower check " + checks);
		}
	}

	private static void rejects(Runnable action) {
		checks++;
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError("Expected tower rejection " + checks);
	}

	private static void rejectState(Runnable action) {
		checks++;
		try {
			action.run();
		} catch (IllegalStateException expected) {
			return;
		}
		throw new AssertionError("Expected recovery state rejection " + checks);
	}

	public static void main(String[] args) {
		checks = 0;
		var from = new TowerLink.Anchor("minecraft:overworld", .5, 66.5, .5);
		var to = new TowerLink.Anchor("minecraft:the_nether", 100.5, 66.5, 100.5);
		var link = new TowerLink("arpg:descent", from, to, 3, 3, 5, Set.of("arpg:first_rift"));
		check(!link.eligible(4, Set.of("arpg:first_rift"), false, true, true));
		check(!link.eligible(5, Set.of(), false, true, true));
		check(link.eligible(5, Set.of("arpg:first_rift"), false, true, true));
		check(!link.eligible(5, Set.of("arpg:first_rift"), true, true, true));
		check(!link.eligible(5, Set.of("arpg:first_rift"), false, false, true));
		check(!link.eligible(5, Set.of("arpg:first_rift"), false, true, false));
		check(link.nearby("minecraft:overworld", .5, 66.5, 8.5, 8));
		check(!link.nearby("minecraft:overworld", .5, 66.5, 8.6, 8));
		check(link.nearby("minecraft:the_nether", 100.5, 66.5, 100.5, 8));
		check(!link.nearby("minecraft:the_end", .5, 66.5, .5, 8));
		check(!link.nearby("minecraft:overworld", Double.NaN, 66.5, .5, 8));
		rejects(() -> link.nearby("minecraft:overworld", 0, 0, 0, Double.POSITIVE_INFINITY));
		rejects(() -> link.nearby("minecraft:overworld", 0, 0, 0, -1));
		rejects(() -> new TowerLink.Anchor("puffish_skills:rifts", 0, 70, 0));
		rejects(() -> new TowerLink.Anchor("minecraft:overworld", Double.NaN, 70, 0));
		rejects(() -> new TowerLink.Anchor("minecraft:overworld", 30_000_000, 70, 0));
		rejects(() -> new TowerLink("arpg:bad", from, from, 3, 3, 1, Set.of()));
		rejects(() -> new TowerLink("arpg:bad", from, to, Double.NaN, 3, 1, Set.of()));
		rejects(() -> new TowerLink("arpg:bad", from, to, 3, 2, 1, Set.of()));
		rejects(() -> new TowerLink("arpg:bad", from, to, 7, 3, 1, Set.of()));
		rejects(() -> new TowerLink("arpg:bad", from, to, 3, 3, 0, Set.of()));
		rejects(() -> new TowerLink("arpg:bad", from, to, 3, 3, 1, Set.of("bad")));
		var network = new TowerCatalog(List.of(link), Set.of("arpg:first_rift"));
		check(network.link(link.id()).equals(link));
		rejects(() -> network.link("arpg:missing"));
		rejects(() -> new TowerCatalog(List.of(link, link), Set.of("arpg:first_rift")));
		rejects(() -> new TowerCatalog(List.of(link), Set.of()));
		var back = new TowerLink("arpg:return", to, from, 3, 3, 1, Set.of());
		check(new TowerCatalog(List.of(link, back), Set.of("arpg:first_rift")).links().size() == 2);
		check(new TowerCatalog(List.of(), Set.of()).links().isEmpty());
		var sector = new TowerSector("arpg:entry", "minecraft:overworld", -10, 64, -10, 10, 80, 10);
		var destination = new TowerSector("arpg:destination", "minecraft:the_nether", 90, 64, 90, 110, 80, 110);
		var protection = new TowerProtection(List.of(sector, destination), network);
		check(protection.size() == 2);
		check(protection.pistonMayTouch("minecraft:overworld", -23, 70, 0));
		check(!protection.pistonMayTouch("minecraft:overworld", -24, 70, 0));
		check(!protection.pistonMayTouch("minecraft:the_end", 0, 70, 0));
		check(!protection.pistonMayTouch("minecraft:overworld", Integer.MAX_VALUE, 70, 0));
		check(protection.protects("minecraft:overworld", -10, 64, -10));
		check(protection.protects("minecraft:overworld", 10.99, 80.99, 10.99));
		check(!protection.protects("minecraft:overworld", 11, 70, 0));
		check(!protection.protects("minecraft:overworld", -10.01, 70, 0));
		check(!protection.protects("minecraft:overworld", 0, 63.99, 0));
		check(!protection.protects("minecraft:overworld", 0, 81, 0));
		check(!protection.protects("minecraft:the_end", 0, 70, 0));
		check(!protection.protects("minecraft:overworld", Double.NaN, 70, 0));
		check(protection.activation("minecraft:overworld", 0, 64, 0).equals(link.id()));
		check(protection.activation("minecraft:the_nether", 100, 64, 100).equals(link.id()));
		check(protection.activation("minecraft:overworld", 0, 65, 0).isEmpty());
		check(protection.activation("minecraft:overworld", 1, 64, 0).isEmpty());
		check(new TowerProtection(List.of(), network).activation("minecraft:overworld", 0, 64, 0).isEmpty());
		rejects(() -> new TowerSector("arpg:bad", "puffish_skills:rifts", 0, 0, 0, 1, 1, 1));
		rejects(() -> new TowerSector("arpg:bad", "minecraft:overworld", 1, 0, 0, 0, 1, 1));
		rejects(() -> new TowerSector("arpg:bad", "minecraft:overworld", 0, 0, 0, 5000, 1, 1));
		rejects(() -> new TowerProtection(List.of(sector, sector), network));
		var duplicateAnchor = new TowerLink("arpg:other_descent", from, to, 3, 3, 1, Set.of());
		var ambiguous = new TowerProtection(List.of(sector), new TowerCatalog(List.of(link, duplicateAnchor), Set.of("arpg:first_rift")));
		try {
			ambiguous.activation("minecraft:overworld", 0, 64, 0);
			throw new AssertionError("Ambiguous anchor activated");
		} catch (IllegalStateException expected) {
			check(true);
		}
		var copper = new TowerPuzzle.Relay("minecraft:overworld", -4, 64, 4, "minecraft:copper_block");
		var gold = new TowerPuzzle.Relay("minecraft:overworld", 4, 64, 4, "minecraft:gold_block");
		var puzzle = new TowerPuzzle("arpg:relays", link.id(), List.of(copper, gold));
		var puzzles = new TowerPuzzleCatalog(List.of(puzzle), network, protection);
		check(puzzles.click("minecraft:overworld", -4, 64, 4).index() == 0);
		check(puzzles.click("minecraft:the_nether", -4, 64, 4) == null);
		check(puzzles.links().get(link.id()).equals(puzzle));
		rejects(() -> new TowerPuzzle(puzzle.id(), link.id(), List.of(copper)));
		rejects(() -> new TowerPuzzle(puzzle.id(), link.id(), List.of(copper, copper)));
		rejects(() -> new TowerPuzzleCatalog(List.of(puzzle, puzzle), network, protection));
		rejects(() -> new TowerPuzzleCatalog(List.of(puzzle), network, new TowerProtection(List.of(), network)));
		var anchorRelay = new TowerPuzzle.Relay("minecraft:overworld", 0, 64, 0, "minecraft:lodestone");
		rejects(() -> new TowerPuzzleCatalog(List.of(new TowerPuzzle("arpg:anchor", link.id(), List.of(copper, anchorRelay))), network, protection));
		var otherDimension = new TowerPuzzle.Relay("minecraft:the_nether", 104, 64, 104, "minecraft:gold_block");
		rejects(() -> new TowerPuzzleCatalog(List.of(new TowerPuzzle("arpg:cross", link.id(), List.of(copper, otherDimension))), network, protection));
		var state = TowerPuzzleState.empty();
		check(!state.solved(puzzle));
		check(state.press(puzzle, 1, 10).entries().get(puzzle.id()).step() == 0);
		state = state.press(puzzle, 0, 10);
		check(state.entries().get(puzzle.id()).step() == 1);
		check(state.press(puzzle, 1, 11) == state);
		check(state.press(puzzle, 0, 14).entries().get(puzzle.id()).step() == 0);
		check(state.press(puzzle, 1, 211).entries().get(puzzle.id()).step() == 0);
		check(state.press(puzzle, 1, 9).entries().get(puzzle.id()).step() == 0);
		check(!TowerPuzzleState.empty().solved(puzzle));
		var solved = state.press(puzzle, 1, 14);
		check(solved.solved(puzzle));
		check(solved.press(puzzle, 0, 18) == solved);
		check(solved.press(puzzle, 0, 1) == solved);
		check(new TowerPuzzleState(solved.entries()).solved(puzzle));
		var changed = new TowerPuzzle(puzzle.id(), puzzle.link(), List.of(gold, copper));
		check(!solved.solved(changed));
		check(solved.press(changed, 0, 14).entries().get(puzzle.id()).step() == 1);
		var changedBlock = new TowerPuzzle(puzzle.id(), puzzle.link(), List.of(copper,
				new TowerPuzzle.Relay(gold.dimension(), gold.x(), gold.y(), gold.z(), "minecraft:iron_block")));
		check(!solved.solved(changedBlock));
		rejects(() -> solved.press(puzzle, -1, 20));
		rejects(() -> solved.press(puzzle, 2, 20));
		rejects(() -> solved.press(puzzle, 0, -1));
		rejects(() -> new TowerPuzzleState.Progress("bad", 0, 1));
		rejects(() -> new TowerPuzzleState.Progress(puzzle.fingerprint(), 9, 1));
		var checkpoint = new TowerLink.Anchor("minecraft:overworld", .5, 65, 4.5);
		var recovery = new TowerRecovery("arpg:recovery", Set.of(sector.id(), destination.id()), checkpoint);
		var recoveryCatalog = new TowerRecoveryCatalog(List.of(recovery), List.of(sector, destination));
		check(recoveryCatalog.at("minecraft:overworld", 0, 65, 4).equals(recovery));
		check(recoveryCatalog.at("minecraft:the_nether", 100, 70, 100).equals(recovery));
		check(recoveryCatalog.at("minecraft:the_end", 0, 65, 4) == null);
		check(recoveryCatalog.at("minecraft:overworld", 11, 65, 4) == null);
		check(recoveryCatalog.at("minecraft:overworld", Double.NaN, 65, 4) == null);
		rejects(() -> new TowerRecovery("arpg:empty", Set.of(), checkpoint));
		rejects(() -> new TowerRecoveryCatalog(List.of(recovery, recovery), List.of(sector, destination)));
		rejects(() -> new TowerRecoveryCatalog(List.of(recovery), List.of(sector)));
		rejects(() -> new TowerRecoveryCatalog(List.of(new TowerRecovery("arpg:outside", Set.of(sector.id()), to)), List.of(sector)));
		var overhead = new TowerLink.Anchor("minecraft:overworld", 0, 80.5, 0);
		rejects(() -> new TowerRecoveryCatalog(List.of(new TowerRecovery("arpg:head", Set.of(sector.id()), overhead)), List.of(sector)));
		var overlapping = new TowerSector("arpg:overlap", "minecraft:overworld", 0, 64, 0, 15, 80, 15);
		var conflict = new TowerRecovery("arpg:conflict", Set.of(overlapping.id()), checkpoint);
		rejects(() -> new TowerRecoveryCatalog(List.of(recovery, conflict), List.of(sector, destination, overlapping)));
		var samePolicyOverlap = new TowerRecovery("arpg:same", Set.of(sector.id(), overlapping.id()), checkpoint);
		check(new TowerRecoveryCatalog(List.of(samePolicyOverlap), List.of(sector, overlapping)).at("minecraft:overworld", 0, 65, 4).equals(samePolicyOverlap));
		var ticket = TowerRecoveryTicket.begin(recovery);
		check(ticket.phase() == TowerRecoveryTicket.Phase.WAITING_RESPAWN && !ticket.captured());
		var captured = ticket.capture();
		check(captured.captured());
		rejectState(captured::capture);
		rejectState(ticket::returned);
		var pending = captured.respawn();
		check(pending.phase() == TowerRecoveryTicket.Phase.RETURN_PENDING && pending.captured());
		check(pending.respawn() == pending);
		rejectState(pending::capture);
		var claimable = pending.returned();
		check(claimable.phase() == TowerRecoveryTicket.Phase.CLAIMABLE && claimable.checkpoint().equals(checkpoint));
		check(claimable.respawn() == claimable);
		rejectState(claimable::returned);
		check(ticket.respawn().returned().phase() == TowerRecoveryTicket.Phase.CLAIMABLE);
		var exit = new TowerExit("arpg:exit", new TowerPuzzle.Relay("minecraft:overworld", 0, 64, 8, "minecraft:crying_obsidian"));
		var exits = new TowerExitCatalog(List.of(exit), protection, puzzles);
		check(exits.at("minecraft:overworld", 0, 64, 8).equals(exit));
		check(exits.at("minecraft:the_end", 0, 64, 8) == null);
		check(exit.nearby("minecraft:overworld", .5, 65, 11.5));
		check(!exit.nearby("minecraft:overworld", .5, 65, 11.6));
		check(!exit.nearby("minecraft:overworld", Double.NaN, 65, 8.5));
		rejects(() -> new TowerExit("arpg:bad", copper));
		rejects(() -> new TowerExitCatalog(List.of(exit, exit), protection, puzzles));
		rejects(() -> new TowerExitCatalog(List.of(exit), new TowerProtection(List.of(), network), puzzles));
		var relayExit = new TowerExit("arpg:relay", new TowerPuzzle.Relay(copper.dimension(), copper.x(), copper.y(), copper.z(), "minecraft:crying_obsidian"));
		rejects(() -> new TowerExitCatalog(List.of(relayExit), protection, puzzles));
		var anchorExit = new TowerExit("arpg:anchor", new TowerPuzzle.Relay("minecraft:overworld", 0, 64, 0, "minecraft:crying_obsidian"));
		rejects(() -> new TowerExitCatalog(List.of(anchorExit), protection, puzzles));
		var overlappingExit = new TowerExit("arpg:other_exit", new TowerPuzzle.Relay("minecraft:overworld", 1, 64, 8, "minecraft:crying_obsidian"));
		var ambiguousExits = new TowerExitCatalog(List.of(exit, overlappingExit), protection, puzzles);
		rejectState(() -> ambiguousExits.nearby("minecraft:overworld", .5, 65, 8.5));
		check(TowerLandingSearch.find(0, 65, 0, point -> point.y() == 65, (x, z) -> 80).equals(new TowerLandingSearch.Point(0, 65, 0)));
		check(TowerLandingSearch.find(0, 65, 0, point -> point.y() == 90, (x, z) -> 90).equals(new TowerLandingSearch.Point(0, 90, 0)));
		var calls = new int[2];
		check(TowerLandingSearch.find(0, 65, 0, point -> {
			calls[0]++;
			return false;
		}, (x, z) -> {
			calls[1]++;
			return 90;
		}) == null);
		check(calls[0] == 810 && calls[1] == 81);
		check(TowerLandingSearch.find(0, 65, 0, point -> point.x() == 5, (x, z) -> Integer.MIN_VALUE) == null);
		var combatGate = new TowerLink("arpg:sanctum", from, to, 3, 3, 1, Set.of("arpg:first_rift", "arpg:tower_sentinel"));
		check(!combatGate.eligible(1, Set.of("arpg:first_rift"), false, true, true));
		check(!combatGate.eligible(1, Set.of("arpg:first_rift", "arpg:first_world_boss"), false, true, true));
		check(combatGate.eligible(1, Set.of("arpg:first_rift", "arpg:tower_sentinel"), false, true, true));
		check(SentinelPhase.at(120, 120) == SentinelPhase.GUARDING);
		check(SentinelPhase.at(60, 120) == SentinelPhase.GUARDING);
		check(SentinelPhase.at(Math.nextDown(60f), 120) == SentinelPhase.ENRAGED);
		check(SentinelPhase.at(Math.nextUp(60f), 120) == SentinelPhase.GUARDING);
		check(SentinelPhase.at(30, 60) == SentinelPhase.GUARDING);
		check(SentinelPhase.at(29, 60) == SentinelPhase.ENRAGED);
		check(SentinelPhase.at(90, 120) == SentinelPhase.GUARDING);
		check(SentinelPhase.healthFraction(180, 120) == 1);
		check(SentinelPhase.healthFraction(-1, 120) == 0);
		check(SentinelPhase.healthFraction(0, 120) == 0);
		check(SentinelPhase.healthFraction(Float.NaN, 120) == 0);
		check(SentinelPhase.healthFraction(120, 0) == 0);
		check(SentinelPhase.healthFraction(120, Float.POSITIVE_INFINITY) == 0);
		System.out.println("Tower network checks passed: " + checks);
	}
}
