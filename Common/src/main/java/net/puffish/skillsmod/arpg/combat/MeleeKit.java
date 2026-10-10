package net.puffish.skillsmod.arpg.combat;

/** First single-weapon kit. Animation damage and collision remain Epic Fight-owned. */
public final class MeleeKit {
	public static final int COMMITMENT_TICKS = 8;

	private MeleeKit() {
	}

	public enum Attack {
		HEAVY("measured_strike", "Measured Strike", 3, 8, 80),
		DRIVING("driving_slash", "Driving Slash", 5, 6, 60);

		private final String path;
		private final String title;
		private final int level;
		private final int stamina;
		private final int cooldown;

		Attack(String path, String title, int level, int stamina, int cooldown) {
			this.path = path;
			this.title = title;
			this.level = level;
			this.stamina = stamina;
			this.cooldown = cooldown;
		}

		public String path() {
			return path;
		}

		public String id() {
			return "puffish_skills:" + path;
		}

		public String title() {
			return title;
		}

		public int level() {
			return level;
		}

		public int stamina() {
			return stamina;
		}

		public int cooldown() {
			return cooldown;
		}
	}

	public record Input(boolean survival, boolean chosenClass, int level, boolean martial, boolean battle,
			String weapon, boolean mainHand, boolean offhandEmpty, boolean busy) {
	}

	public static String rejection(Input input, Attack attack) {
		if (!input.survival() || !input.chosenClass() || !input.martial()) {
			return "Requires a living survival character with a chosen class and the Martial pillar.";
		}
		if (input.level() < attack.level()) {
			return "Requires ARPG level " + attack.level() + ".";
		}
		if (!input.battle()) {
			return "Enter Epic Fight combat mode first.";
		}
		if (!"sword".equals(input.weapon()) && !"longsword".equals(input.weapon())) {
			return "Equip a supported Epic Fight sword or longsword.";
		}
		if (!input.mainHand() || !input.offhandEmpty()) {
			return "This kit requires a main-hand weapon and an empty offhand.";
		}
		if (input.busy()) {
			return "Finish your current action, item use or recovery first.";
		}
		return "";
	}
}
