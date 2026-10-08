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
		System.out.println("Tower network checks passed: " + checks);
	}
}
