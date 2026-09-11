package net.puffish.skillsmod.arpg.skill;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.puffish.skillsmod.arpg.combat.ArpgDamageKind;
import net.puffish.skillsmod.arpg.combat.ArpgTargetTags;
import net.puffish.skillsmod.arpg.combat.DamagePipeline;
import net.puffish.skillsmod.arpg.combat.HitContext;
import net.puffish.skillsmod.arpg.data.ArpgData;
import net.puffish.skillsmod.arpg.item.ArpgItemTags;
import net.puffish.skillsmod.arpg.rule.ArpgRuleEngine;
import net.puffish.skillsmod.arpg.rule.ArpgRuleRuntime;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatCompiler;
import net.puffish.skillsmod.arpg.stat.ArpgStatSnapshot;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Server-authoritative executor for catalog-defined custom weapon skills. */
public final class ArpgWeaponSkillExecutor {
	private static final double DEFAULT_HALF_ANGLE = 60.0;
	private static final ArpgStatSnapshot EMPTY_SNAPSHOT = ArpgStatCompiler.compile(List.of());

	private ArpgWeaponSkillExecutor() {
	}

	public record Result(boolean success, int targetsHit, String message) {
		private static Result success(int targetsHit, double resource) {
			return new Result(true, targetsHit,
					"Hit " + targetsHit + " target(s); combat resource "
							+ String.format(Locale.ROOT, "%.1f", resource) + ".");
		}

		private static Result deny(String message) {
			return new Result(false, 0, message);
		}
	}

	public static Result use(ServerPlayerEntity player, String skillId) {
		var access = ArpgSkillAccess.check(player, skillId, "weapon");
		if (!access.allowed()) {
			return Result.deny(access.message());
		}

		var skill = ArpgData.content().skills().get(skillId);
		if (skill == null) {
			return Result.deny("Unknown ARPG skill: " + skillId);
		}
		if (!"hit".equals(skill.effect())) {
			return Result.deny("Skill effect is not executable yet: " + skill.effect());
		}
		if (!skill.tags().contains(SkillTag.ATTACK) || !skill.tags().contains(SkillTag.MELEE)) {
			return Result.deny("The weapon executor currently supports melee attack skills only.");
		}
		if (!"any".equals(skill.weapon()) && !"melee".equals(skill.weapon())) {
			return Result.deny("This skill requires an unsupported weapon family: " + skill.weapon());
		}
		if (player.getMainHandStack().isEmpty()) {
			return Result.deny("A melee weapon is required.");
		}

		var ruleTags = ArpgItemTags.merge(ruleTags(skill.tags()), player.getMainHandStack());
		var activationSnapshot = ArpgRuleRuntime.snapshot(
				player, null, ArpgRuleEngine.Event.ATTACK, skill.id(), ruleTags);
		var use = ArpgSkillRuntime.tryUse(player, skill, activationSnapshot);
		if (!use.allowed()) {
			return Result.deny(use.message());
		}

		ArpgRuleRuntime.fireSupportedTriggers(
				player, null, ArpgRuleEngine.Event.ATTACK, skill.id(), ruleTags);

		var look = player.getRotationVec(1.0F);
		var bounds = player.getBoundingBox().expand(skill.range(), 2.5, skill.range());
		var targets = player.getServerWorld().getEntitiesByClass(
				LivingEntity.class,
				bounds,
				target -> target != player && target.isAlive()
		);

		int hit = 0;
		for (var target : targets) {
			double dx = target.getX() - player.getX();
			double dz = target.getZ() - player.getZ();
			if (!ArpgSkillUseSemantics.inCone(look.x, look.z, dx, dz, skill.range(), DEFAULT_HALF_ANGLE)) {
				continue;
			}
			if (damage(player, target, skill, ruleTags)) {
				hit++;
			}
		}

		return Result.success(hit, use.resource());
	}

	private static boolean damage(
			ServerPlayerEntity player,
			LivingEntity target,
			net.puffish.skillsmod.arpg.data.ArpgContent.Skill skill,
			Set<String> ruleTags
	) {
		var targetRuleTags = ArpgTargetTags.merge(ruleTags, target);
		var snapshot = ArpgRuleRuntime.snapshot(
				player, target, ArpgRuleEngine.Event.HIT, skill.id(), targetRuleTags);
		double criticalChance = Math.max(0.0, Math.min(1.0,
				snapshot.apply(ArpgStat.CRITICAL_CHANCE, 0.0)));
		boolean critical = criticalChance > 0.0 && player.getRandom().nextDouble() < criticalChance;

		double weaponDamage = Math.max(0.0, player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE));
		double baseDamage = weaponDamage * skill.coefficient();
		var hit = new HitContext(Map.of(skill.damageType(), baseDamage), skill.tags(), critical, false);
		var resolved = DamagePipeline.resolve(hit, snapshot, EMPTY_SNAPSHOT, List.of(), 0.0, false, false);
		if (resolved.total() <= 0.0) {
			return false;
		}

		var context = new ArpgSkillDamageContext.Active(
				player,
				ArpgDamageKind.ATTACK_SKILL,
				skill.id(),
				targetRuleTags,
				critical
		);
		var damaged = new boolean[1];
		ArpgSkillDamageContext.run(context, () -> damaged[0] = target.damage(
				target.getDamageSources().playerAttack(player),
				(float) resolved.total()
		));
		return damaged[0];
	}

	private static Set<String> ruleTags(Set<SkillTag> tags) {
		return tags.stream()
				.map(tag -> tag.name().toLowerCase(Locale.ROOT))
				.collect(Collectors.toUnmodifiableSet());
	}
}
