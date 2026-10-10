package net.puffish.skillsmod.arpg.combat;

/** Narrow validation for the first provider-backed melee actions; Epic Fight validates costs afterward. */
public final class MeleeActionPolicy {
	private MeleeActionPolicy() {
	}

	public enum Action {
		INNATE, STANCE
	}

	public record Input(boolean survival, boolean primaryChosen, boolean martial, boolean battleMode,
			boolean holding, boolean executableState, boolean disabled, boolean weaponMatches, String skill) {
	}

	public static String rejection(Input input, Action action) {
		if (!input.survival()) {
			return "Melee actions require a living survival player.";
		}
		if (!input.primaryChosen()) {
			return "Choose your starting class first.";
		}
		if (!input.martial()) {
			return "These melee controls require the Martial combat pillar.";
		}
		if (!input.battleMode()) {
			return "Enter Epic Fight combat mode first.";
		}
		if (!input.weaponMatches()) {
			return "The equipped weapon and innate skill do not match yet.";
		}
		if (action == Action.STANCE && !"epicfight:liechtenauer".equals(input.skill())) {
			return "This stance control requires a longsword with Liechtenauer.";
		}
		if (!"epicfight:liechtenauer".equals(input.skill()) && !"epicfight:sweeping_edge".equals(input.skill())) {
			return "This slice supports longsword Liechtenauer and sword Sweeping Edge. Other skills use Epic Fight controls.";
		}
		if (input.disabled() || input.holding() || !input.executableState()) {
			return "Finish your current action or recovery before using this skill.";
		}
		return "";
	}
}
