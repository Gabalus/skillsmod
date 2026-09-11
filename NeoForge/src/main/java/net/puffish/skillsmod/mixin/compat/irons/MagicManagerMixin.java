package net.puffish.skillsmod.mixin.compat.irons;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.puffish.skillsmod.main.IronsBloodMagicBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps continuous Iron's spells channeling from life instead of consulting stale mana. */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.capabilities.magic.MagicManager", remap = false)
public abstract class MagicManagerMixin {
	@Redirect(
			method = "tick",
			at = @At(value = "INVOKE", target = "Lio/redspace/ironsspellbooks/api/magic/MagicData;getMana()F", remap = false),
			remap = false
	)
	private float arpg$replaceContinuousManaCheck(MagicData magicData) {
		var player = ((MagicDataAccessor) (Object) magicData).arpg$getServerPlayer();
		if (player == null || !magicData.isCasting()) {
			return magicData.getMana();
		}
		var skillId = magicData.getCastingSpellId();
		if (!IronsBloodMagicBridge.replacesMana(player, skillId)) {
			return magicData.getMana();
		}
		var spell = SpellRegistry.getSpell(skillId);
		int manaCost = spell.getManaCost(magicData.getCastingSpellLevel());
		return IronsBloodMagicBridge.canPayLife(player, manaCost) ? Float.MAX_VALUE : Float.NEGATIVE_INFINITY;
	}
}
