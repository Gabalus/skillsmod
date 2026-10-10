package net.puffish.skillsmod.arpg.spell;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import net.minecraft.entity.LivingEntity;

/** Narrow native mob-spell release, isolated from installations without Iron's. */
public final class EnemyIronsSpells {
	private EnemyIronsSpells() {
	}

	public static boolean available() {
		var spell = SpellRegistry.ICICLE_SPELL.get();
		return spell.isEnabled() && spell.getMinLevel() <= 1 && spell.getMaxLevel() >= 1
				&& spell.getCastType() == CastType.INSTANT;
	}

	public static boolean release(LivingEntity caster) {
		if (caster.getWorld().isClient() || !caster.isAlive() || !available()) {
			return false;
		}
		var spell = SpellRegistry.ICICLE_SPELL.get();
		var magic = new MagicData(true);
		if (!spell.checkPreCastConditions(caster.getWorld(), 1, caster, magic)) {
			return false;
		}
		magic.initiateCast(spell, 1, 0, CastSource.MOB, "");
		boolean released = false;
		try {
			spell.onServerPreCast(caster.getWorld(), 1, caster, magic);
			spell.onCast(caster.getWorld(), 1, caster, CastSource.MOB, magic);
			released = true;
		} finally {
			spell.onServerCastComplete(caster.getWorld(), 1, caster, magic, !released);
		}
		return true;
	}
}
