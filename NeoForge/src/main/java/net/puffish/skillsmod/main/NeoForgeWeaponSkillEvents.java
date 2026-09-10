package net.puffish.skillsmod.main;

import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.arpg.combat.ArpgDamageContext;

/** Better-Combat-safe resource generation from successful basic player melee attacks. */
@EventBusSubscriber(modid = SkillsAPI.MOD_ID)
public final class NeoForgeWeaponSkillEvents {
	private NeoForgeWeaponSkillEvents() {
	}

	@SubscribeEvent
	public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayerEntity player) {
			NeoForgeWeaponSkillRuntime.clear(player);
		}
	}

	@SubscribeEvent
	public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayerEntity player) {
			NeoForgeWeaponSkillRuntime.clear(player);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLivingDamage(LivingDamageEvent.Post event) {
		if (event.getNewDamage() <= 0.0f || ArpgDamageContext.current() != null) {
			return;
		}
		if (!event.getSource().isOf(DamageTypes.PLAYER_ATTACK)) {
			return;
		}
		if (event.getSource().getAttacker() instanceof ServerPlayerEntity player) {
			NeoForgeWeaponSkillRuntime.grantBasicHitMomentum(player);
		}
	}
}
