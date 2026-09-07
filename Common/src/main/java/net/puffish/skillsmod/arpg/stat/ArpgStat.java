package net.puffish.skillsmod.arpg.stat;

import java.util.Locale;
import java.util.Optional;

public enum ArpgStat {
	MAXIMUM_LIFE,
	MAXIMUM_MANA,
	ARMOR,
	EVASION,
	WARD,
	ATTACK_SPEED,
	CAST_SPEED,
	PHYSICAL_DAMAGE,
	FIRE_DAMAGE,
	COLD_DAMAGE,
	LIGHTNING_DAMAGE,
	BLOOD_DAMAGE,
	HOLY_DAMAGE,
	ENDER_DAMAGE,
	SPELL_DAMAGE,
	ATTACK_DAMAGE,
	MELEE_DAMAGE,
	PROJECTILE_DAMAGE,
	CRITICAL_CHANCE,
	CRITICAL_MULTIPLIER,
	COOLDOWN_RECOVERY,
	BLOCK_CHANCE;

	public String getId() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Optional<ArpgStat> byId(String id) {
		for (var stat : values()) {
			if (stat.getId().equals(id)) {
				return Optional.of(stat);
			}
		}
		return Optional.empty();
	}
}
