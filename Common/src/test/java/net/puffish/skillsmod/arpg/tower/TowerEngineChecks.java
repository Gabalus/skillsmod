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
		System.out.println("Tower network checks passed: " + checks);
	}
}
