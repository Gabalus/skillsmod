package net.puffish.skillsmod.arpg.status;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.puffish.skillsmod.arpg.combat.AilmentState;
import net.puffish.skillsmod.arpg.combat.AilmentStateNbt;
import net.puffish.skillsmod.arpg.combat.AilmentType;
import net.puffish.skillsmod.arpg.combat.ArpgDamageKind;
import net.puffish.skillsmod.arpg.combat.DamagePipeline;
import net.puffish.skillsmod.arpg.combat.DamageType;
import net.puffish.skillsmod.arpg.rule.ArpgAilmentRuntime;
import net.puffish.skillsmod.arpg.rule.ArpgRuleEngine;
import net.puffish.skillsmod.arpg.rule.ArpgRuleRuntime;
import net.puffish.skillsmod.arpg.skill.ArpgSkillDamageContext;
import net.puffish.skillsmod.arpg.stat.ArpgStatCompiler;

import java.util.List;

/** Saved server status backend; provider-native statuses are left under provider ownership. */
public final class NeoForgeAilments implements ArpgAilmentRuntime.Backend {
	private static final String DATA = "puffish_skills.ailments";
	private static final NeoForgeAilments INSTANCE = new NeoForgeAilments();

	private NeoForgeAilments() {
	}

	public static void configure() {
		ArpgAilmentRuntime.configure(INSTANCE);
	}

	public static AilmentState state(LivingEntity target) {
		return AilmentStateNbt.read(target.getPersistentData().getCompound(DATA));
	}

	private static void save(LivingEntity target, AilmentState state) {
		if (state.applications().isEmpty()) {
			target.getPersistentData().remove(DATA);
		} else {
			target.getPersistentData().put(DATA, AilmentStateNbt.write(state));
		}
	}

	/** Ability executors use this entry point for either a player or an enemy caster. */
	public static boolean applyStatus(LivingEntity owner, LivingEntity target, AilmentType type,
			double damage, int duration, String skill) {
		return INSTANCE.apply(owner, target, type, damage, duration, skill);
	}

	@Override
	public boolean apply(LivingEntity owner, LivingEntity target, AilmentType type, double damage, int duration, String skill) {
		if (owner == null || target == null || type == null || !(target.getWorld() instanceof ServerWorld) || owner.getWorld() != target.getWorld()
				|| !owner.isAlive() || !target.isAlive() || owner == target || owner.isTeammate(target)
				|| owner instanceof FakePlayer || target instanceof FakePlayer
				|| owner instanceof ServerPlayerEntity ownerPlayer && (ownerPlayer.isCreative() || ownerPlayer.isSpectator())
				|| target instanceof ServerPlayerEntity targetPlayer && (targetPlayer.isCreative() || targetPlayer.isSpectator())
				|| type == AilmentType.IGNITE && (target.isFireImmune() || target.isTouchingWater())
				|| !Double.isFinite(damage) || damage <= 0 || damage > AilmentState.MAX_DAMAGE
				|| duration < 1 || duration > AilmentState.MAX_DURATION || skill == null || skill.length() > 128) {
			return false;
		}
		var state = state(target);
		var next = state.apply(new AilmentState.Application(type, owner.getUuid(), skill, damage, duration, AilmentState.INTERVAL));
		if (next.equals(state)) {
			return false;
		}
		save(target, next);
		return true;
	}

	@Override
	public boolean has(LivingEntity target, AilmentType type) {
		return state(target).has(type);
	}

	@Override
	public void clear(LivingEntity target) {
		target.getPersistentData().remove(DATA);
	}

	@Override
	public void tick(LivingEntity target) {
		if (!(target.getWorld() instanceof ServerWorld world) || !target.getPersistentData().contains(DATA)) {
			return;
		}
		if (!target.isAlive()) {
			clear(target);
			return;
		}
		var velocity = target.getVelocity();
		var current = state(target);
		if (target.isTouchingWater()) {
			current = current.remove(AilmentType.IGNITE);
		}
		var step = current.tick(velocity.x * velocity.x + velocity.z * velocity.z > .0025);
		// Settle time before callbacks: no reentrant damage can replay the same pulse.
		save(target, step.state());
		for (var pulse : step.pulses()) {
			if (!target.isAlive()) {
				clear(target);
				break;
			}
			var application = pulse.application();
			var entity = world.getEntity(application.owner());
			var owner = entity instanceof LivingEntity living && living.isAlive() ? living : null;
			if (target instanceof ServerPlayerEntity targetPlayer && (targetPlayer.isCreative() || targetPlayer.isSpectator())
					|| owner instanceof FakePlayer
					|| owner instanceof ServerPlayerEntity ownerPlayer && (ownerPlayer.isCreative() || ownerPlayer.isSpectator())
					|| owner != null && owner.isTeammate(target)) {
				continue;
			}
			double damage = pulse.damage();
			if (target instanceof ServerPlayerEntity player && application.type().damage() != DamageType.PHYSICAL) {
				var defense = ArpgRuleRuntime.snapshot(player, owner, ArpgRuleEngine.Event.DAMAGE_TAKEN, "", application.type().tags());
				damage = DamagePipeline.mitigateResistance(damage, application.type().damage(),
						ArpgStatCompiler.compile(List.of()), defense, true);
			}
			var key = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of("puffish_skills", application.type().id()));
			var source = new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(key), null, owner);
			float amount = (float) damage;
			if (owner instanceof ServerPlayerEntity player) {
				var context = new ArpgSkillDamageContext.Active(player, ArpgDamageKind.DAMAGE_OVER_TIME,
						application.skill(), application.type().tags(), false);
				ArpgSkillDamageContext.run(context, () -> target.damage(source, amount));
			} else {
				target.damage(source, amount);
			}
		}
		if (!target.isAlive()) {
			clear(target);
		}
	}

	public static AilmentType damageType(DamageSource source) {
		for (var type : AilmentType.values()) {
			if (source.isOf(RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of("puffish_skills", type.id())))) {
				return type;
			}
		}
		return null;
	}
}
