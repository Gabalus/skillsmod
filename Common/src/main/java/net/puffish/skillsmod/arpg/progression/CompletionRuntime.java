package net.puffish.skillsmod.arpg.progression;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.puffish.skillsmod.arpg.character.ArpgProgression;
import net.puffish.skillsmod.arpg.sandbox.CraftworkRuntime;

/** Trusted encounter controllers award completions; players can study only earned discoveries. */
public final class CompletionRuntime {
	public static final String ENTITY_MARKER = "arpg:completion=";

	private CompletionRuntime() {
	}

	public static boolean complete(ServerPlayerEntity player, String id) {
		var reward = CompletionData.catalog().reward(id);
		var character = ArpgProgression.character(player);
		if (!character.awardCompletion(reward)) {
			return false;
		}
		studyAvailable(player);
		ArpgProgression.sync(player);
		var receipt = character.completions().get(id);
		player.sendMessage(Text.literal("Completed " + id + " | passive +" + receipt.passivePoints()
				+ " | confluence +" + receipt.confluencePoints() + " | discoveries " + receipt.knowledge()), false);
		return true;
	}

	public static boolean study(ServerPlayerEntity player, String id) {
		if (!ArpgProgression.character(player).discoveredKnowledge().contains(id)) {
			throw new IllegalStateException("This recipe has not been discovered in an encounter");
		}
		if (CraftworkRuntime.state(player).knowledge().contains(id)) {
			return false;
		}
		CraftworkRuntime.learn(player, id);
		return true;
	}

	public static int studyAvailable(ServerPlayerEntity player) {
		var discoveries = ArpgProgression.character(player).discoveredKnowledge().stream().sorted().toList();
		int learned = 0;
		boolean progressed;
		do {
			progressed = false;
			for (String id : discoveries) {
				try {
					if (!CraftworkRuntime.state(player).knowledge().contains(id)) {
						CraftworkRuntime.learn(player, id);
						learned++;
						progressed = true;
					}
				} catch (IllegalArgumentException | IllegalStateException expected) {
					// Discovery is retained until mastery/prerequisites/provider requirements can be met.
				}
			}
		} while (progressed);
		return learned;
	}
}
