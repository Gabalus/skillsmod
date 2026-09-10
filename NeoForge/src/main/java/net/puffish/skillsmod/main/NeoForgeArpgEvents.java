package net.puffish.skillsmod.main;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.arpg.combat.ArpgAttackScaling;
import net.puffish.skillsmod.arpg.combat.ArpgDefenseSemantics;
import net.puffish.skillsmod.arpg.combat.DamagePipeline;
import net.puffish.skillsmod.arpg.compat.IronsDamageSourceCompat;
import net.puffish.skillsmod.arpg.rule.ArpgAilmentRuntime;
import net.puffish.skillsmod.arpg.rule.ArpgRuleEngine;
import net.puffish.skillsmod.arpg.rule.ArpgRuleRuntime;
import net.puffish.skillsmod.arpg.stat.ArpgPlayerStats;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatCompiler;
import net.puffish.skillsmod.arpg.stat.ArpgStatSnapshot;

import java.util.List;
import java.util.Set;

/** NeoForge-owned runtime hooks for ARPG mechanics that cannot live in the loader-neutral core. */
@EventBusSubscriber(modid = SkillsAPI.MOD_ID)
public final class NeoForgeArpgEvents {
	private static final Set<String> MELEE_TAGS = Set.of("attack", "melee", "hit", "physical");
	private static final Set<String> PROJECTILE_TAGS = Set.of("attack", "projectile", "hit", "physical");
	private static final ArpgStatSnapshot EMPTY_SNAPSHOT = ArpgStatCompiler.compile(List.of());

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

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onEntityTick(EntityTickEvent.Post event) {
		if (event.getEntity() instanceof LivingEntity living && living.getWorld() instanceof ServerWorld world) {
			ArpgAilmentRuntime.tick(living, world.getTime());
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

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onIncomingDamage(LivingIncomingDamageEvent event) {
		var source = event.getSource();
		var school = IronsDamageSourceCompat.school(source);

		if (event.getEntity() instanceof ServerPlayerEntity defender) {
			if (school.isPresent()) {
				var tags = IronsDamageSourceCompat.tags(source);
				var defense = ArpgRuleRuntime.snapshot(
						defender,
						source.getAttacker(),
						ArpgRuleEngine.Event.DAMAGE_TAKEN,
						"",
						tags
				);
				if (ArpgDefenseSemantics.resolveSpell(defense, defender.getRandom().nextDouble())
						== ArpgDefenseSemantics.Outcome.BLOCK) {
					event.setAmount(0.0f);
					fireDefenseTrigger(defender, source, tags, ArpgRuleEngine.Event.BLOCK);
					return;
				}

				var attack = source.getAttacker() instanceof ServerPlayerEntity attacker
						? ArpgRuleRuntime.snapshot(attacker, defender, ArpgRuleEngine.Event.HIT, "", tags)
						: EMPTY_SNAPSHOT;
				double mitigated = DamagePipeline.mitigateResistance(
						event.getAmount(), school.orElseThrow(), attack, defense, false);
				event.setAmount((float) mitigated);
				return;
			}

			var incomingDelivery = defensiveDelivery(source);
			if (incomingDelivery != null) {
				var tags = incomingDelivery == ArpgAttackScaling.Delivery.MELEE ? MELEE_TAGS : PROJECTILE_TAGS;
				var defense = ArpgRuleRuntime.snapshot(
						defender,
						source.getAttacker(),
						ArpgRuleEngine.Event.DAMAGE_TAKEN,
						"",
						tags
				);
				var outcome = ArpgDefenseSemantics.resolveAttack(
						defense,
						defender.getRandom().nextDouble(),
						defender.getRandom().nextDouble()
				);
				if (outcome != ArpgDefenseSemantics.Outcome.HIT) {
					event.setAmount(0.0f);
					fireDefenseTrigger(
							defender,
							source,
							tags,
							outcome == ArpgDefenseSemantics.Outcome.DODGE
									? ArpgRuleEngine.Event.DODGE
									: ArpgRuleEngine.Event.BLOCK
					);
					return;
				}
			}
		}

		if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) {
			return;
		}

		var delivery = attackDelivery(source);
		if (delivery == null) {
			return;
		}

		var tags = delivery == ArpgAttackScaling.Delivery.MELEE ? MELEE_TAGS : PROJECTILE_TAGS;
		var snapshot = ArpgRuleRuntime.snapshot(
				attacker,
				event.getEntity(),
				ArpgRuleEngine.Event.ATTACK,
				"",
				tags
		);
		double scaled = ArpgAttackScaling.scaleExistingPhysicalAttack(event.getAmount(), snapshot, delivery);
		event.setAmount((float) scaled);
	}

	/** Ward is consumed after armor/resistance reductions but before health and vanilla absorption are touched. */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
		if (!(event.getEntity() instanceof ServerPlayerEntity defender) || event.getNewDamage() <= 0.0f) {
			return;
		}
		var source = event.getSource();
		double remaining = ArpgRuleRuntime.absorbWard(
				defender,
				source.getAttacker(),
				damageTags(source),
				event.getNewDamage()
		);
		if (remaining < event.getNewDamage()) {
			event.setNewDamage((float) remaining);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onLivingDamage(LivingDamageEvent.Post event) {
		var source = event.getSource();
		var tags = damageTags(source);
		if (event.getBlockedDamage() > 0.0f && event.getEntity() instanceof ServerPlayerEntity blocker) {
			ArpgRuleRuntime.fireSupportedTriggers(
					blocker,
					source.getAttacker(),
					ArpgRuleEngine.Event.BLOCK,
					"",
					tags
			);
		}

		if (event.getNewDamage() <= 0.0f) {
			return;
		}

		if (event.getEntity() instanceof ServerPlayerEntity victim) {
			ArpgRuleRuntime.fireSupportedTriggers(
					victim,
					source.getAttacker(),
					ArpgRuleEngine.Event.DAMAGE_TAKEN,
					"",
					tags
			);
		}

		if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
			ArpgRuleRuntime.fireSupportedTriggers(
					attacker,
					event.getEntity(),
					ArpgRuleEngine.Event.HIT,
					"",
					tags
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
					damageTags(source)
			);
		}
	}

	private static void fireDefenseTrigger(
			ServerPlayerEntity defender,
			DamageSource source,
			Set<String> tags,
			ArpgRuleEngine.Event event
	) {
		ArpgRuleRuntime.fireSupportedTriggers(
				defender,
				source.getAttacker(),
				event,
				"",
				tags
		);
	}

	private static ArpgAttackScaling.Delivery attackDelivery(DamageSource source) {
		if (source.isOf(DamageTypes.PLAYER_ATTACK)) {
			return ArpgAttackScaling.Delivery.MELEE;
		}
		if (source.isOf(DamageTypes.ARROW) || source.isOf(DamageTypes.TRIDENT)) {
			return ArpgAttackScaling.Delivery.PROJECTILE;
		}
		return null;
	}

	private static ArpgAttackScaling.Delivery defensiveDelivery(DamageSource source) {
		if (source.isOf(DamageTypes.PLAYER_ATTACK)
				|| source.isOf(DamageTypes.MOB_ATTACK)
				|| source.isOf(DamageTypes.MOB_ATTACK_NO_AGGRO)) {
			return ArpgAttackScaling.Delivery.MELEE;
		}
		if (source.isOf(DamageTypes.ARROW)
				|| source.isOf(DamageTypes.TRIDENT)
				|| source.isOf(DamageTypes.MOB_PROJECTILE)) {
			return ArpgAttackScaling.Delivery.PROJECTILE;
		}
		return null;
	}

	private static Set<String> damageTags(DamageSource source) {
		var delivery = defensiveDelivery(source);
		if (delivery == ArpgAttackScaling.Delivery.MELEE) {
			return MELEE_TAGS;
		}
		if (delivery == ArpgAttackScaling.Delivery.PROJECTILE) {
			return PROJECTILE_TAGS;
		}
		return IronsDamageSourceCompat.tags(source);
	}

	private static void clearTransient(ServerPlayerEntity player) {
		ArpgPlayerStats.clear(player);
		ArpgRuleRuntime.clear(player);
	}
}
