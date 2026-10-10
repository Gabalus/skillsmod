package net.puffish.skillsmod.arpg.melee;

import yesman.epicfight.skill.SkillCategories;
import yesman.epicfight.skill.SkillCategory;
import yesman.epicfight.skill.SkillSlot;

/** Extra slots loaded by Epic Fight's deterministic extensible-enum setup. */
public enum ArpgMeleeSlots implements SkillSlot {
	ARPG_HEAVY,
	ARPG_DRIVING;

	private final int ordinal;

	ArpgMeleeSlots() {
		ordinal = SkillSlot.ENUM_MANAGER.assign(this);
	}

	@Override
	public SkillCategory category() {
		return SkillCategories.WEAPON_INNATE;
	}

	@Override
	public int universalOrdinal() {
		return ordinal;
	}
}
