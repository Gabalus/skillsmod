package net.puffish.skillsmod.arpg.progression;

import net.puffish.skillsmod.arpg.character.ArpgCharacter;
import net.puffish.skillsmod.arpg.sandbox.SandboxCatalog;
import net.puffish.skillsmod.arpg.sandbox.SandboxState;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dependency-free completion/currency regression checks. */
public final class CompletionRewardChecks {
	private static int checks;

	private CompletionRewardChecks() {
	}

	public static void main(String[] args) {
		checks = 0;
		var catalog = CompletionCatalog.defaults();
		var character = new ArpgCharacter();
		expectFailure(() -> character.awardCompletion(catalog.reward("arpg:first_rift")));
		check(character.completions().isEmpty(), "Rejected reward cannot create a receipt");
		character.choosePrimary("warrior");
		character.gainExperience(100_000_000);
		check(character.passivePoints() == 0, "XP grants no passive points");
		character.chooseSecondary("arcanist");
		check(character.confluencePoints() == 0, "XP grants no confluence points");
		character.completeMilestone("campaign_test");
		check(character.passivePoints() == 0, "Legacy milestone strings cannot mint passive points");
		expectFailure(() -> character.awardCompletion(catalog.reward("arpg:rune_rift")));
		check(character.completions().isEmpty(), "Missing prerequisite leaves state unchanged");
		check(character.awardCompletion(catalog.reward("arpg:first_rift")), "First clear settles");
		check(character.passivePoints() == 1, "Rift awards one point");
		check(character.discoveredKnowledge().contains("arpg:metalworking"), "Rift retains its recipe discovery");
		check(!character.awardCompletion(catalog.reward("arpg:first_rift")), "Replay grants nothing");
		check(character.passivePoints() == 1, "Replay cannot inflate points");
		var edited = new CompletionReward("arpg:first_rift", "rift", 1, 20, 12, 0, Set.of(), Set.of("arpg:inscription"));
		check(!character.awardCompletion(edited), "Catalog balance edits cannot reopen a receipt");
		check(!character.discoveredKnowledge().contains("arpg:inscription"), "Replay cannot substitute recipe rewards");
		check(character.awardCompletion(catalog.reward("arpg:rune_rift")), "Second rift follows completion prerequisite");
		check(character.passivePoints() == 2, "Distinct first clears each award their own points");
		var sandbox = SandboxState.empty().learn(SandboxCatalog.core(), "arpg:metalworking", Map.of());
		expectFailure(() -> sandbox.learn(SandboxCatalog.core(), "arpg:inscription", Map.of()));
		check(character.discoveredKnowledge().contains("arpg:inscription"), "Mastery failure does not erase discovery");
		check(character.awardCompletion(catalog.reward("arpg:first_world_boss")), "World boss settles independently");
		check(character.passivePoints() == 4 && character.confluencePoints() == 1, "World boss grants bounded currencies");
		for (int trial = 1; trial <= 4; trial++) {
			check(character.awardCompletion(catalog.reward("arpg:trial_" + trial)), "Authored trial settles in sequence");
		}
		check(character.ascendancyPoints() == 8, "Trial completions award ascendancy points");
		check(!character.awardCompletion(catalog.reward("arpg:trial_4")), "Trial replay cannot award again");
		character.setLevel(1);
		check(character.passivePoints() == 4 && character.earnedConfluencePoints() == 1, "Admin level changes do not erase completion currencies");
		var capped = new ArpgCharacter();
		capped.choosePrimary("warrior");
		for (int i = 0; i < 76; i++) {
			capped.awardCompletion(new CompletionReward("test:rift_" + i, "rift", 1, 20, 12, 0, Set.of(), Set.of()));
		}
		check(capped.passivePoints() == 1500, "Passive currency cap holds across completions");
		check(capped.earnedConfluencePoints() == 12, "Confluence currency cap holds before choosing secondary");
		check(capped.completions().get("test:rift_75").passivePoints() == 0, "Receipt snapshots actual capped awards");
		check(capped.confluencePoints() == 0, "Unchosen secondary does not expose confluence budget");
		var restored = new ArpgCharacter();
		restored.choosePrimary("warrior");
		restored.restoreCompletionProgress(29, 3, Map.of("arpg:first_rift", character.completions().get("arpg:first_rift")));
		check(restored.passivePoints() == 30, "Legacy balance and receipt points combine once");
		restored.gainExperience(100_000_000);
		check(restored.passivePoints() == 30, "Legacy migration balance is frozen after levelling");
		check(!restored.awardCompletion(catalog.reward("arpg:first_rift")), "Restored receipt rejects replay");
		expectFailure(() -> restored.restoreCompletionProgress(-1, 0, Map.of()));
		expectFailure(() -> restored.restoreCompletionProgress(0, 0, Map.of("test:mismatch", character.completions().get("arpg:first_rift"))));
		expectFailure(() -> new CompletionCatalog(List.of(catalog.reward("arpg:first_rift"), catalog.reward("arpg:first_rift")), ""));
		expectFailure(() -> new CompletionCatalog(List.of(catalog.reward("arpg:rune_rift")), ""));
		expectFailure(() -> new CompletionCatalog(List.of(catalog.reward("arpg:first_rift")), "arpg:first_rift"));
		var first = new CompletionReward("test:a", "rift", 1, 1, 0, 0, Set.of("test:b"), Set.of());
		var second = new CompletionReward("test:b", "rift", 1, 1, 0, 0, Set.of("test:a"), Set.of());
		expectFailure(() -> new CompletionCatalog(List.of(first, second), ""));
		var deep = new java.util.ArrayList<CompletionReward>();
		for (int i = 0; i < 129; i++) {
			deep.add(new CompletionReward("test:deep_" + i, "rift", 1, 1, 0, 0,
					i == 0 ? Set.of() : Set.of("test:deep_" + (i - 1)), Set.of()));
		}
		expectFailure(() -> new CompletionCatalog(deep, ""));
		check(new CompletionCatalog(deep.subList(0, 128), "").rewards().size() == 128, "Bounded deep graph remains valid");
		expectFailure(() -> new CompletionReward("invalid", "rift", 1, 1, 0, 0, Set.of(), Set.of()));
		expectFailure(() -> new CompletionReward("test:invalid", "rift", 1, 21, 0, 0, Set.of(), Set.of()));
		expectFailure(() -> new CompletionReward("test:invalid", "trial", 1, 0, 0, 1, Set.of(), Set.of()));
		var lowLevel = new ArpgCharacter();
		lowLevel.choosePrimary("warrior");
		lowLevel.awardCompletion(catalog.reward("arpg:first_rift"));
		expectFailure(() -> lowLevel.awardCompletion(catalog.reward("arpg:trial_1")));
		check(lowLevel.completedTrials() == 0 && lowLevel.completions().size() == 1, "Trial level failure cannot partially settle");
		var towerPlayer = new ArpgCharacter();
		towerPlayer.choosePrimary("warrior");
		expectFailure(() -> towerPlayer.awardCompletion(catalog.reward("arpg:tower_sentinel")));
		check(towerPlayer.completions().isEmpty(), "Sentinel prerequisite failure retains no receipt");
		towerPlayer.awardCompletion(catalog.reward("arpg:first_rift"));
		towerPlayer.awardCompletion(catalog.reward("arpg:first_world_boss"));
		check(!towerPlayer.completions().containsKey("arpg:tower_sentinel"), "Unrelated world boss cannot unlock sanctum");
		int passiveBefore = towerPlayer.passivePoints();
		int confluenceBefore = towerPlayer.earnedConfluencePoints();
		check(towerPlayer.awardCompletion(catalog.reward("arpg:tower_sentinel")), "Dedicated sentinel first clear creates receipt");
		check(towerPlayer.completions().containsKey("arpg:tower_sentinel"), "Passage can read the dedicated receipt");
		check(towerPlayer.passivePoints() == passiveBefore && towerPlayer.earnedConfluencePoints() == confluenceBefore,
				"Sentinel unlock cannot repay prior boss currencies");
		check(!towerPlayer.awardCompletion(catalog.reward("arpg:tower_sentinel")), "Sentinel replay cannot settle twice");
		System.out.println("Completion rewards: " + checks + " checks passed");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
		checks++;
	}

	private static void expectFailure(Runnable action) {
		try {
			action.run();
		} catch (IllegalArgumentException | IllegalStateException expected) {
			checks++;
			return;
		}
		throw new AssertionError("Invalid reward operation was accepted");
	}
}
