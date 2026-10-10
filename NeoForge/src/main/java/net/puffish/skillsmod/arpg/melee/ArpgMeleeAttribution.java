package net.puffish.skillsmod.arpg.melee;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import yesman.epicfight.api.animation.AnimationVariables;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.damagesource.EpicFightDamageSource;

/** Provider-owned lifecycle: StaticAnimation.end removes independent variables, including on interruption. */
public final class ArpgMeleeAttribution {
	private static final AnimationVariables.IndependentVariableKey<String> SKILL = AnimationVariables.unsyncIndependent(animator -> "", true);

	private ArpgMeleeAttribution() {
	}

	public static void play(ServerPlayerPatch patch, AssetAccessor<? extends StaticAnimation> animation, String skill) {
		// Playing first ends the preceding animation, even when the same native animation is reused.
		patch.playAnimationSynchronized(animation, 0.0f);
		patch.getAnimator().getVariables().put(SKILL, animation, skill);
	}

	public static String skillId(DamageSource source) {
		if (!(source instanceof EpicFightDamageSource epic) || epic.getAnimation() == null
				|| !(source.getAttacker() instanceof ServerPlayerEntity player) || source.getSource() != player) {
			return "";
		}
		var patch = EpicFightCapabilities.getServerPlayerPatch(player);
		return patch == null ? "" : patch.getAnimator().getVariables().get(SKILL, epic.getAnimation()).orElse("");
	}
}
