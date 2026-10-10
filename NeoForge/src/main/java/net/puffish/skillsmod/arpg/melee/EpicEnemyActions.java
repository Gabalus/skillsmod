package net.puffish.skillsmod.arpg.melee;

import net.minecraft.entity.LivingEntity;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** Native attack/stun state gates ranged casts; it never replaces the mob's moveset. */
public final class EpicEnemyActions {
	private EpicEnemyActions() {
	}

	public static boolean ready(LivingEntity entity) {
		var patch = EpicFightCapabilities.getEntityPatch(entity, LivingEntityPatch.class);
		return patch != null && !patch.isStunned() && !patch.getEntityState().inaction()
				&& patch.getEntityState().canUseSkill();
	}
}
