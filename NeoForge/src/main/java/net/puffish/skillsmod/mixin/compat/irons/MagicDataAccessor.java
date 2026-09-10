package net.puffish.skillsmod.mixin.compat.irons;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.api.magic.MagicData", remap = false)
public interface MagicDataAccessor {
	@Accessor(value = "serverPlayer", remap = false)
	ServerPlayerEntity arpg$getServerPlayer();
}
