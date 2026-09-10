package net.puffish.skillsmod.main;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.ShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.arpg.rule.ArpgRuleEngine;
import net.puffish.skillsmod.arpg.rule.ArpgRuleRuntime;
import net.puffish.skillsmod.arpg.stat.ArpgPlayerStats;
import net.puffish.skillsmod.arpg.stat.ArpgStat;

import java.util.Set;

/** NeoForge-owned runtime hooks for ARPG mechanics that cannot live in the loader-neutral core. */
@EventBusSubscriber(modid = SkillsAPI.MOD_ID)
public final class NeoForgeArpgEvents {
	private static final Set<String> MELEE_TAGS = Set.of("attack", "melee", "hit", "physical");
	private static final Set<String> INDIRECT_TAGS = Set.of("hit", "projectile");

	private NeoForgeArpgEvents() {
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayerEntity player) {
			clearTransient(player);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayerEntity player) {
			clearTransient(player);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onDatapackSync(OnDatapackSyncEvent event) {
		if (event.getPlayer() == null) {
			for (var player : event.getPlayerList().getServer().getPlayerManager().getPlayerList()) {
				clearTransient(player);
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onCriticalHit(CriticalHitEvent event) {
		if (!(event.getEntity() instanceof ServerPlayerEntity player)) {
			return;
		}

		var snapshot = ArpgRuleRuntime.snapshot(
				player,
				event.getTarget(),
				ArpgRuleEngine.Event.ATTACK,
				"",
				MELEE_TAGS
		);
		double criticalChance = Math.max(0.0, Math.min(1.0, snapshot.apply(ArpgStat.CRITICAL_CHANCE, 0.0)));
		if (!event.isCriticalHit() && criticalChance > 0.0 && player.getRandom().nextDouble() < criticalChance) {
			event.setCriticalHit(true);
		}

		if (event.isCriticalHit() && snapshot.asMap().containsKey(ArpgStat.CRITICAL_MULTIPLIER)) {
			double multiplier = snapshot.apply(ArpgStat.CRITICAL_MULTIPLIER, event.getDamageMultiplier());
			event.setDamageMultiplier((float) Math.max(0.0, multiplier));
		}

		if (event.isCriticalHit()) {
			ArpgRuleRuntime.fireSupportedTriggers(
					player,
					event.getTarget(),
					ArpgRuleEngine.Event.CRIT,
					"",
					MELEE_TAGS
			);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onLivingDamage(LivingDamageEvent.Post event) {
		if (event.getHealthDamage() <= 0.0f) {
			return;
		}

		var source = event.getSource();
		if (event.getEntity() instanceof ServerPlayerEntity victim) {
			ArpgRuleRuntime.fireSupportedTriggers(
					victim,
					source.getAttacker(),
					ArpgRuleEngine.Event.DAMAGE_TAKEN,
					"",
					Set.of()
			);
		}

		if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
			ArpgRuleRuntime.fireSupportedTriggers(
					attacker,
					event.getEntity(),
					ArpgRuleEngine.Event.HIT,
					"",
					damageTags(source.getSource(), attacker)
			);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onLivingDeath(LivingDeathEvent event) {
		var source = event.getSource();
		if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
			ArpgRuleRuntime.fireSupportedTriggers(
					attacker,
					event.getEntity(),
					ArpgRuleEngine.Event.KILL,
					"",
					damageTags(source.getSource(), attacker)
			);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onShieldBlock(ShieldBlockEvent event) {
		if (event.getEntity() instanceof ServerPlayerEntity player && event.getBlockedDamage() > 0.0f) {
			Entity attacker = event.getDamageSource().getAttacker();
			ArpgRuleRuntime.fireSupportedTriggers(
					player,
					attacker,
					ArpgRuleEngine.Event.BLOCK,
					"",
					Set.of()
			);
		}
	}

	private static Set<String> damageTags(Entity direct, ServerPlayerEntity attacker) {
		return direct == attacker ? MELEE_TAGS : INDIRECT_TAGS;
	}

	private static void clearTransient(ServerPlayerEntity player) {
		ArpgPlayerStats.clear(player);
		ArpgRuleRuntime.clear(player);
	}
}
