package net.puffish.skillsmod.arpg.skill;

import net.minecraft.server.network.ServerPlayerEntity;
import net.puffish.skillsmod.arpg.character.ArpgCharacter;
import net.puffish.skillsmod.arpg.character.ArpgProgression;
import net.puffish.skillsmod.arpg.data.ArpgContent;
import net.puffish.skillsmod.arpg.data.ArpgData;

/** Server-authoritative active-skill access rules shared by provider bridges. */
public final class ArpgSkillAccess {
	private ArpgSkillAccess() {
	}

	public enum Denial {
		NONE,
		UNKNOWN_SKILL,
		WRONG_PROVIDER,
		NO_PRIMARY,
		LEVEL,
		DISCIPLINE
	}

	public record Result(boolean allowed, Denial denial, String message) {
		private static Result allow() {
			return new Result(true, Denial.NONE, "");
		}

		private static Result deny(Denial denial, String message) {
			return new Result(false, denial, message);
		}
	}

	public static Result check(ServerPlayerEntity player, String skillId, String provider) {
		var skill = ArpgData.content().skills().get(skillId);
		if (skill == null) {
			return Result.deny(Denial.UNKNOWN_SKILL, "That skill is not enabled by the ARPG catalog.");
		}
		return check(ArpgProgression.character(player), skill, provider);
	}

	public static Result check(ArpgCharacter character, ArpgContent.Skill skill, String provider) {
		if (!skill.provider().equals(provider)) {
			return Result.deny(Denial.WRONG_PROVIDER, "That skill belongs to a different combat provider.");
		}
		if (character.primary().isEmpty()) {
			return Result.deny(Denial.NO_PRIMARY, "Choose a primary discipline before using ARPG skills.");
		}
		if (character.level() < skill.level()) {
			return Result.deny(Denial.LEVEL, "Requires ARPG level " + skill.level() + ".");
		}
		if (!skill.discipline().isEmpty()
				&& !skill.discipline().equals(character.primary())
				&& !skill.discipline().equals(character.secondary())) {
			return Result.deny(Denial.DISCIPLINE, "Requires the " + skill.discipline() + " discipline.");
		}
		return Result.allow();
	}
}
