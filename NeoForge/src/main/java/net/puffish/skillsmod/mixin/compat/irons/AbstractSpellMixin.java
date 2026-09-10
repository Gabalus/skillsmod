package net.puffish.skillsmod.mixin.compat.irons;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.puffish.skillsmod.main.IronsBloodMagicBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces Iron's mana gate only for ARPG rules that explicitly spend life instead. */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.api.spells.AbstractSpell", remap = false)
public abstract class AbstractSpellMixin {
	@Shadow(remap = false)
	public abstract String getSpellId();

	@Shadow(remap = false)
	public abstract int getManaCost(int spellLevel);

	@Redirect(
			method = "canBeCastedBy",
			at = @At(value = "INVOKE", target = "Lio/redspace/ironsspellbooks/api/magic/MagicData;getMana()F", remap = false),
			remap = false
	)
	private float arpg$replaceManaValidation(MagicData magicData) {
		var player = ((MagicDataAccessor) (Object) magicData).arpg$getServerPlayer();
		if (player != null && IronsBloodMagicBridge.replacesMana(player, getSpellId())) {
			return Float.MAX_VALUE;
		}
		return magicData.getMana();
	}

	@Inject(method = "castSpell", at = @At("HEAD"), cancellable = true, remap = false)
	private void arpg$recheckLifeCost(
			World world,
			int spellLevel,
			ServerPlayerEntity player,
			CastSource castSource,
			boolean triggerCooldown,
			CallbackInfo callback
	) {
		if (!castSource.consumesMana() || !IronsBloodMagicBridge.replacesMana(player, getSpellId())) {
			return;
		}
		if (!IronsBloodMagicBridge.canPayLife(player, getManaCost(spellLevel))) {
			player.sendMessage(Text.literal("Not enough Life to cast this spell."), true);
			callback.cancel();
		}
	}
}
