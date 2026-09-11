package net.puffish.skillsmod.arpg.combat;

import net.puffish.skillsmod.arpg.stat.ArpgStat;

public enum DamageType {
	PHYSICAL, FIRE, COLD, LIGHTNING, BLOOD, HOLY, ENDER, NATURE;

	public ArpgStat damageStat() {
		return ArpgStat.valueOf(name() + "_DAMAGE");
	}

	public ArpgStat resistanceStat() {
		return this == PHYSICAL ? ArpgStat.ARMOR : ArpgStat.valueOf(name() + "_RESISTANCE");
	}

	public ArpgStat penetrationStat() {
		return this == PHYSICAL ? ArpgStat.ARMOR : ArpgStat.valueOf(name() + "_PENETRATION");
	}
}
