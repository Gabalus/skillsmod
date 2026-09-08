package net.puffish.skillsmod.arpg.combat;

import net.puffish.skillsmod.arpg.skill.SkillTag;

import java.util.Map;
import java.util.Set;

public record HitContext(Map<DamageType, Double> damage, Set<SkillTag> tags, boolean critical, boolean damageOverTime) {
	public HitContext {
		damage = Map.copyOf(damage);
		tags = Set.copyOf(tags);
		if (damage.values().stream().anyMatch(value -> !Double.isFinite(value) || value < 0)) {
			throw new IllegalArgumentException("Damage must be finite and nonnegative");
		}
	}
}
