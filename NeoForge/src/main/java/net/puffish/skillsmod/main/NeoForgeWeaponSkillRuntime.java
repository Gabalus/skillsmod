package net.puffish.skillsmod.main;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.puffish.skillsmod.arpg.combat.ArpgDamageContext;
import net.puffish.skillsmod.arpg.combat.DamagePipeline;
import net.puffish.skillsmod.arpg.combat.HitContext;
import net.puffish.skillsmod.arpg.data.ArpgContent;
import net.puffish.skillsmod.arpg.data.ArpgData;
import net.puffish.skillsmod.arpg.rule.ArpgRuleEngine;
import net.puffish.skillsmod.arpg.rule.ArpgRuleRuntime;
import net.puffish.skillsmod.arpg.skill.ArpgSkillAccess;
import net.puffish.skillsmod.arpg.skill.ArpgSkillExecutor;
import net.puffish.skillsmod.arpg.skill.ArpgSkillUseSemantics;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatCompiler;
import net.puffish.skillsmod.arpg.stat.ArpgStatSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NeoForge executor for catalog-owned weapon skills.
 *
 * <p>It intentionally never calls {@code player.attack(...)}. Better Combat remains the owner of
 * basic attacks while ARPG active skills own their targeting and damage semantics.</p>
 */
public final class NeoForgeWeaponSkillRuntime {
	private static final double MAX_MOMENTUM = 100.0;
	private static final double BASIC_HIT_MOMENTUM = 10.0;
	private static final double CLEAVE_HALF_ANGLE = 55.0;
	private static final Map<UUID, Double> momentum = new HashMap<>();
	private static final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
	private static final ArpgStatSnapshot EMPTY_SNAPSHOT = ArpgStatCompiler.compile(List.of());

	private NeoForgeWeaponSkillRuntime() {
	}

	public static synchronized void install() {
		ArpgSkillExecutor.configure(NeoForgeWeaponSkillRuntime::use);
	}

	public static synchronized double momentum(ServerPlayerEntity player) {
		return momentum.getOrDefault(player.getUuid(), 0.0);
	}

	public static synchronized double grantBasicHitMomentum(ServerPlayerEntity player) {
		double next = Math.min(MAX_MOMENTUM, momentum(player) + BASIC_HIT_MOMENTUM);
		momentum.put(player.getUuid(), next);
		return next;
	}

	public static synchronized ArpgSkillExecutor.Result use(ServerPlayerEntity player, String skillId) {
		var access = ArpgSkillAccess.check(player, skillId, "weapon");
		if (!access.allowed()) {
			return ArpgSkillExecutor.Result.failure(access.message(), momentum(player));
		}

		ArpgContent.Skill skill = ArpgData.content().skills().get(skillId);
		if (skill == null) {
			return ArpgSkillExecutor.Result.failure("Unknown ARPG skill: " + skillId, momentum(player));
		}
		if (!"cleave".equals(skill.id())) {
			return ArpgSkillExecutor.Result.failure(
					"The active executor does not implement " + skill.title() + " yet.", momentum(player));
		}
		if (!validWeapon(player, skill)) {
			return ArpgSkillExecutor.Result.failure("Cleave requires a melee weapon in your main hand.", momentum(player));
		}

		long now = player.getServerWorld().getTime();
		long readyAt = cooldowns(player).getOrDefault(skill.id(), Long.MIN_VALUE);
		if (readyAt > now) {
			return ArpgSkillExecutor.Result.failure(
					"Cleave is on cooldown for " + (readyAt - now) + " ticks.", momentum(player));
		}

		var tags = new ArpgDamageContext(ArpgDamageContext.Kind.ATTACK_SKILL, skill.id(), skill.tags()).ruleTags();
		var activationSnapshot = ArpgRuleRuntime.snapshot(
				player, null, ArpgRuleEngine.Event.ATTACK, skill.id(), tags);
		double cost = ArpgSkillUseSemantics.resourceCost(skill, activationSnapshot);
		double current = momentum(player);
		if (!Double.isFinite(cost) || current + 1.0e-9 < cost) {
			return ArpgSkillExecutor.Result.failure(
					"Not enough Momentum: need " + format(cost) + ", have " + format(current) + ".", current);
		}

		int cooldown = ArpgSkillUseSemantics.cooldownTicks(skill, activationSnapshot);
		momentum.put(player.getUuid(), Math.max(0.0, current - cost));
		cooldowns(player).put(skill.id(), now + cooldown);

		int targetsHit = executeCleave(player, skill);
		ArpgRuleRuntime.fireSupportedTriggers(
				player, null, ArpgRuleEngine.Event.ATTACK, skill.id(), tags);
		return ArpgSkillExecutor.Result.success(
				"Used Cleave: hit " + targetsHit + " target(s).",
				targetsHit,
				momentum(player),
				cooldown
		);
	}

	private static int executeCleave(ServerPlayerEntity player, ArpgContent.Skill skill) {
		var world = player.getServerWorld();
		var forward = player.getRotationVec(1.0F);
		var candidates = world.getEntitiesByClass(
				LivingEntity.class,
				player.getBoundingBox().expand(skill.range()),
				entity -> entity != player && entity.isAlive() && !(entity instanceof ServerPlayerEntity)
		);
		int hit = 0;
		for (var target : candidates) {
			if (!ArpgSkillUseSemantics.inCone(
					forward.x, forward.z,
					target.getX() - player.getX(), target.getZ() - player.getZ(),
					skill.range(), CLEAVE_HALF_ANGLE)) {
				continue;
			}
			if (damage(player, target, skill)) {
				hit++;
			}
		}
		return hit;
	}

	private static boolean damage(ServerPlayerEntity player, LivingEntity target, ArpgContent.Skill skill) {
		var context = new ArpgDamageContext(ArpgDamageContext.Kind.ATTACK_SKILL, skill.id(), skill.tags());
		var tags = context.ruleTags();
		var evaluation = ArpgRuleRuntime.evaluate(player, target, ArpgRuleEngine.Event.ATTACK, skill.id(), tags);
		var snapshot = ArpgRuleRuntime.snapshot(player, target, ArpgRuleEngine.Event.ATTACK, skill.id(), tags);
		double criticalChance = Math.max(0.0, Math.min(1.0, snapshot.apply(ArpgStat.CRITICAL_CHANCE, 0.0)));
		boolean critical = player.getRandom().nextDouble() < criticalChance;
		double weaponDamage = Math.max(0.0, player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE));
		var hit = new HitContext(
				Map.of(skill.damageType(), weaponDamage * skill.coefficient()),
				skill.tags(),
				critical,
				false
		);
		var result = DamagePipeline.resolve(
				hit,
				snapshot,
				EMPTY_SNAPSHOT,
				evaluation.conversions(),
				0.0,
				false,
				evaluation.elementalOnly()
		);
		double amount = result.total();
		if (!Double.isFinite(amount) || amount <= 0.0) {
			return false;
		}
		return ArpgDamageContext.call(
				context,
				() -> target.damage(player.getDamageSources().playerAttack(player), (float) Math.min(1_000_000.0, amount))
		);
	}

	private static boolean validWeapon(ServerPlayerEntity player, ArpgContent.Skill skill) {
		if ("any".equals(skill.weapon())) {
			return true;
		}
		if ("melee".equals(skill.weapon())) {
			return !player.getMainHandStack().isEmpty()
					&& player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) > 1.0;
		}
		return false;
	}

	private static Map<String, Long> cooldowns(ServerPlayerEntity player) {
		return cooldowns.computeIfAbsent(player.getUuid(), ignored -> new HashMap<>());
	}

	private static String format(double value) {
		return String.format(java.util.Locale.ROOT, "%.1f", value);
	}
}
