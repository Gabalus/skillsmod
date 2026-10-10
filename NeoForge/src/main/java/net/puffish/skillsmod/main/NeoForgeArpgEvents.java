package net.puffish.skillsmod.main;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.arpg.combat.ArpgAttackScaling;
import net.puffish.skillsmod.arpg.combat.ArpgCombatRuntime;
import net.puffish.skillsmod.arpg.combat.ArpgDefenseSemantics;
import net.puffish.skillsmod.arpg.combat.ArpgTargetTags;
import net.puffish.skillsmod.arpg.combat.CombatPillar;
import net.puffish.skillsmod.arpg.combat.DamagePipeline;
import net.puffish.skillsmod.arpg.combat.GunnerCombatRuntime;
import net.puffish.skillsmod.arpg.combat.MartialCombatSemantics;
import net.puffish.skillsmod.arpg.combat.MartialCombatWindow;
import net.puffish.skillsmod.arpg.compat.IronsDamageSourceCompat;
import net.puffish.skillsmod.arpg.rule.ArpgAilmentRuntime;
import net.puffish.skillsmod.arpg.rule.ArpgRuleEngine;
import net.puffish.skillsmod.arpg.rule.ArpgRuleRuntime;
import net.puffish.skillsmod.arpg.skill.ArpgSkillDamageContext;
import net.puffish.skillsmod.arpg.skill.ArpgSkillRuntime;
import net.puffish.skillsmod.arpg.stat.ArpgPlayerStats;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatCompiler;
import net.puffish.skillsmod.arpg.stat.ArpgStatSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** NeoForge-owned runtime hooks for ARPG mechanics that cannot live in the loader-neutral core. */
@EventBusSubscriber(modid = SkillsAPI.MOD_ID)
public final class NeoForgeArpgEvents {
	private static final Set<String> MELEE_TAGS = Set.of("attack", "melee", "hit", "physical");
	private static final Set<String> PROJECTILE_TAGS = Set.of("attack", "projectile", "hit", "physical");
	private static final ArpgStatSnapshot EMPTY_SNAPSHOT = ArpgStatCompiler.compile(List.of());
	private static final Map<UUID, MartialCombatWindow> MARTIAL_WINDOWS = new HashMap<>();
	private static final double MARTIAL_GUARD_EFFICIENCY = 0.75;
	private static final double MARTIAL_STAMINA_COST_FACTOR = 1.0;

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
	public static void onPlayerClone(PlayerEvent.Clone event) {
		if (event.isWasDeath()) {
			ArpgAilmentRuntime.clear(event.getOriginal());
			ArpgAilmentRuntime.clear(event.getEntity());
		} else {
			net.puffish.skillsmod.arpg.status.NeoForgeAilments.copyStatus(event.getOriginal(), event.getEntity());
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onEntityTick(EntityTickEvent.Post event) {
		if (event.getEntity() instanceof LivingEntity living && living.getWorld() instanceof ServerWorld world) {
			ArpgAilmentRuntime.tick(living, world.getTime());
			if (living instanceof ServerPlayerEntity player) {
				tickMartialCombat(player, world.getTime());
				var gunner = GunnerCombatRuntime.tick(player);
				if (gunner.completedReload()) {
					player.sendMessage(Text.literal("Reload Complete"), true);
				} else if (gunner.overheatCleared()) {
					player.sendMessage(Text.literal("Weapon Cooled"), true);
				}
			}
		}
	}

	/** Applies the Martial guard result through NeoForge's canonical shield-block stage. */
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onShieldBlock(LivingShieldBlockEvent event) {
		if (EpicFightStaminaBridge.ownsMartialCombat()) {
			return;
		}
		if (!(event.getEntity() instanceof ServerPlayerEntity defender)
				|| event.getDamageSource().getAttacker() == null
				|| event.getOriginalBlockedDamage() <= 0.0f) {
			return;
		}
		var state = ArpgCombatRuntime.state(defender);
		if (state.pillar() != CombatPillar.MARTIAL) {
			return;
		}

		long now = defender.getServerWorld().getTime();
		var window = martialWindow(defender, now).observeGuard(now, defender.isBlocking());
		var timing = window.timing(now, event.getOriginalBlock());
		double originalDamage = event.getOriginalBlockedDamage();
		var result = MartialCombatSemantics.resolveGuard(
				state,
				timing,
				new MartialCombatSemantics.GuardInput(
						originalDamage,
						originalDamage,
						MARTIAL_GUARD_EFFICIENCY,
						MARTIAL_STAMINA_COST_FACTOR
				)
		);
		ArpgCombatRuntime.update(defender, current -> result.state());
		MARTIAL_WINDOWS.put(defender.getUuid(), window.afterGuard(now, result));

		if (timing != MartialCombatSemantics.GuardTiming.MISSED) {
			double blocked = Math.max(0.0, Math.min(originalDamage, originalDamage - result.healthDamage()));
			event.setBlocked(true);
			event.setBlockedDamage((float) blocked);
			if (result.outcome() == MartialCombatSemantics.Outcome.PERFECT_PARRY) {
				event.setShieldDamage(0.0f);
				defender.sendMessage(Text.literal("Perfect Parry - Counter Ready"), true);
			} else if (result.outcome() == MartialCombatSemantics.Outcome.GUARD_BROKEN) {
				defender.stopUsingItem();
				defender.sendMessage(Text.literal("Guard Broken"), true);
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onCriticalHit(CriticalHitEvent event) {
		if (!(event.getEntity() instanceof ServerPlayerEntity player)) {
			return;
		}

		var tags = ArpgTargetTags.merge(MELEE_TAGS, event.getTarget());
		var snapshot = ArpgRuleRuntime.snapshot(
				player,
				event.getTarget(),
				ArpgRuleEngine.Event.ATTACK,
				"",
				tags
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
					tags
			);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onIncomingDamage(LivingIncomingDamageEvent event) {
		var source = event.getSource();
		var school = IronsDamageSourceCompat.school(source);
		var skillDamage = skillDamage(source);

		if (event.getEntity() instanceof ServerPlayerEntity defender) {
			if (school.isPresent()) {
				var tags = skillDamage != null ? skillDamage.tags() : IronsDamageSourceCompat.tags(source);
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
						? ArpgRuleRuntime.snapshot(attacker, defender, ArpgRuleEngine.Event.HIT,
								skillDamage == null ? "" : skillDamage.skill(), tags)
						: EMPTY_SNAPSHOT;
				double mitigated = DamagePipeline.mitigateResistance(
						event.getAmount(), school.orElseThrow(), attack, defense, false);
				event.setAmount((float) mitigated);
				return;
			}

			var incomingDelivery = defensiveDelivery(source);
			if (incomingDelivery != null) {
				var tags = skillDamage != null
						? skillDamage.tags()
						: incomingDelivery == ArpgAttackScaling.Delivery.MELEE ? MELEE_TAGS : PROJECTILE_TAGS;
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
		if (skillDamage != null) {
			// The active-skill executor already applied its catalog coefficient and ARPG offensive stats.
			return;
		}

		var delivery = attackDelivery(source);
		if (delivery == null) {
			return;
		}

		var baseTags = delivery == ArpgAttackScaling.Delivery.MELEE ? MELEE_TAGS : PROJECTILE_TAGS;
		var tags = ArpgTargetTags.merge(baseTags, event.getEntity());
		var snapshot = ArpgRuleRuntime.snapshot(
				attacker,
				event.getEntity(),
				ArpgRuleEngine.Event.ATTACK,
				OptionalEpicMelee.skillId(source),
				tags
		);
		double scaled = ArpgAttackScaling.scaleExistingPhysicalAttack(event.getAmount(), snapshot, delivery);
		scaled = applyMartialCounter(attacker, delivery, scaled);
		if (delivery == ArpgAttackScaling.Delivery.PROJECTILE) {
			scaled *= GunnerCombatRuntime.damageMultiplier(attacker);
		}
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
		if (net.puffish.skillsmod.arpg.status.NeoForgeAilments.damageType(source) != null) {
			return;
		}
		var skillDamage = skillDamage(source);
		var baseTags = skillDamage == null ? damageTags(source) : skillDamage.tags();
		var tags = ArpgTargetTags.merge(baseTags, event.getEntity());
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

		if (source.getAttacker() instanceof net.puffish.skillsmod.arpg.tower.TowerSentinelEntity sentinel
				&& source.getSource() == sentinel && defensiveDelivery(source) == ArpgAttackScaling.Delivery.MELEE
				&& sentinel.getRandom().nextFloat() < .1f) {
			net.puffish.skillsmod.arpg.status.NeoForgeAilments.applyStatus(sentinel, event.getEntity(),
					net.puffish.skillsmod.arpg.combat.AilmentType.BLEED, 1, 60, "puffish_skills:sentinel_slash");
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
			String skill = skillDamage == null ? OptionalEpicMelee.skillId(source) : skillDamage.skill();
			if (skillDamage == null && attackDelivery(source) == ArpgAttackScaling.Delivery.PROJECTILE) {
				GunnerCombatRuntime.registerProjectileHit(
						attacker,
						event.getNewDamage(),
						attacker.squaredDistanceTo(event.getEntity()) <= 64.0
				);
			}
			if (skillDamage != null && skillDamage.critical()) {
				ArpgRuleRuntime.fireSupportedTriggers(
						attacker,
						event.getEntity(),
						ArpgRuleEngine.Event.CRIT,
						skill,
						tags
				);
			}
			ArpgRuleRuntime.fireSupportedTriggers(
					attacker,
					event.getEntity(),
					ArpgRuleEngine.Event.HIT,
					skill,
					tags
			);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onLivingDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayerEntity victim) {
			MARTIAL_WINDOWS.remove(victim.getUuid());
			GunnerCombatRuntime.clear(victim);
		}
		var source = event.getSource();
		if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
			var skillDamage = ArpgSkillDamageContext.currentFor(attacker);
			if (skillDamage == null && attackDelivery(source) == ArpgAttackScaling.Delivery.PROJECTILE) {
				GunnerCombatRuntime.registerProjectileKill(attacker);
			}
			var baseTags = skillDamage == null ? damageTags(source) : skillDamage.tags();
			var tags = ArpgTargetTags.merge(baseTags, event.getEntity());
			ArpgRuleRuntime.fireSupportedTriggers(
					attacker,
					event.getEntity(),
					ArpgRuleEngine.Event.KILL,
					skillDamage == null ? OptionalEpicMelee.skillId(source) : skillDamage.skill(),
					tags
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

	private static ArpgSkillDamageContext.Active skillDamage(DamageSource source) {
		return source.getAttacker() instanceof ServerPlayerEntity attacker
				? ArpgSkillDamageContext.currentFor(attacker)
				: null;
	}

	private static ArpgAttackScaling.Delivery attackDelivery(DamageSource source) {
		if (source.isOf(DamageTypes.PLAYER_ATTACK)) {
			return ArpgAttackScaling.Delivery.MELEE;
		}
		if (source.isOf(DamageTypes.ARROW)
				|| source.isOf(DamageTypes.TRIDENT)
				|| source.getSource() instanceof ProjectileEntity) {
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
				|| source.isOf(DamageTypes.MOB_PROJECTILE)
				|| source.getSource() instanceof ProjectileEntity) {
			return ArpgAttackScaling.Delivery.PROJECTILE;
		}
		return null;
	}

	private static Set<String> damageTags(DamageSource source) {
		var ailment = net.puffish.skillsmod.arpg.status.NeoForgeAilments.damageType(source);
		if (ailment != null) {
			return ailment.tags();
		}
		var skillDamage = skillDamage(source);
		if (skillDamage != null) {
			return skillDamage.tags();
		}
		var delivery = defensiveDelivery(source);
		if (delivery == ArpgAttackScaling.Delivery.MELEE) {
			return MELEE_TAGS;
		}
		if (delivery == ArpgAttackScaling.Delivery.PROJECTILE) {
			return PROJECTILE_TAGS;
		}
		return IronsDamageSourceCompat.tags(source);
	}

	private static void tickMartialCombat(ServerPlayerEntity player, long now) {
		var state = ArpgCombatRuntime.state(player);
		if (state.pillar() != CombatPillar.MARTIAL) {
			MARTIAL_WINDOWS.remove(player.getUuid());
			return;
		}
		if (EpicFightStaminaBridge.ownsMartialCombat()) {
			MARTIAL_WINDOWS.remove(player.getUuid());
			if (now % 5L == 0L) {
				EpicFightStaminaBridge.sync(player);
			}
			return;
		}

		var window = martialWindow(player, now).observeGuard(now, player.isBlocking());
		if (window.recoveryDue(now)) {
			double seconds = window.recoverySeconds(now);
			if (seconds > 0.0) {
				boolean inCombat = window.inCombat(now);
				ArpgCombatRuntime.update(
						player,
						current -> MartialCombatSemantics.recover(current, seconds, inCombat)
				);
			}
			window = window.markRecovery(now);
		}
		MARTIAL_WINDOWS.put(player.getUuid(), window);
	}

	private static double applyMartialCounter(
			ServerPlayerEntity attacker,
			ArpgAttackScaling.Delivery delivery,
			double damage
	) {
		if (EpicFightStaminaBridge.ownsMartialCombat()
				|| delivery != ArpgAttackScaling.Delivery.MELEE
				|| ArpgCombatRuntime.state(attacker).pillar() != CombatPillar.MARTIAL) {
			return damage;
		}
		var window = MARTIAL_WINDOWS.get(attacker.getUuid());
		if (window == null) {
			return damage;
		}
		long now = attacker.getServerWorld().getTime();
		var use = window.consumeCounter(now);
		if (!use.applied()) {
			return damage;
		}
		MARTIAL_WINDOWS.put(attacker.getUuid(), use.window());
		attacker.sendMessage(Text.literal("Counter Strike x1.5"), true);
		return damage * MartialCombatSemantics.PERFECT_COUNTER_DAMAGE_MULTIPLIER;
	}

	private static MartialCombatWindow martialWindow(ServerPlayerEntity player, long now) {
		return MARTIAL_WINDOWS.computeIfAbsent(player.getUuid(), ignored -> MartialCombatWindow.idle(now));
	}

	private static void clearTransient(ServerPlayerEntity player) {
		ArpgPlayerStats.clear(player);
		ArpgRuleRuntime.clear(player);
		ArpgSkillRuntime.clear(player);
		MARTIAL_WINDOWS.remove(player.getUuid());
		GunnerCombatRuntime.clear(player);
	}
}
