package net.puffish.skillsmod.arpg.status;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.puffish.skillsmod.arpg.combat.ElementalState;
import net.puffish.skillsmod.arpg.combat.ElementalStateNbt;
import net.puffish.skillsmod.arpg.combat.DamageType;
import net.puffish.skillsmod.arpg.combat.ElementalReaction;
import net.puffish.skillsmod.arpg.combat.ArpgDamageKind;
import net.puffish.skillsmod.arpg.compat.IronsDamageSourceCompat;
import net.puffish.skillsmod.arpg.skill.ArpgSkillDamageContext;

import java.util.Map;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

/** Server-owned control effects; never modifies provider casting, animation or native statuses. */
public final class NeoForgeElementalStatuses {
	private static final String DATA = "puffish_skills.elemental_statuses";
	private static final Identifier MOVEMENT = Identifier.of("puffish_skills", "elemental_movement");
	private static final TagKey<net.minecraft.entity.EntityType<?>> FREEZE_IMMUNE =
			TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of("puffish_skills", "freeze_immune"));
	private static final Map<LivingEntity, ShatterAttempt> SHATTERS = new WeakHashMap<>();

	private NeoForgeElementalStatuses() {
	}

	public static ElementalState state(LivingEntity target) {
		var state = ElementalStateNbt.read(target.getPersistentData().getCompound(DATA));
		return target.isTouchingWater() ? state.water() : state;
	}

	private static void save(LivingEntity target, ElementalState state) {
		if (state.active()) {
			target.getPersistentData().put(DATA, ElementalStateNbt.write(state));
		} else {
			target.getPersistentData().remove(DATA);
		}
		movement(target, state);
	}

	public static boolean freezeImmune(LivingEntity target) {
		return target.getType().isIn(FREEZE_IMMUNE);
	}

	public static boolean applyCold(LivingEntity owner, LivingEntity target, int duration, String skill) {
		if (owner == null || target == null || !(target.getWorld() instanceof ServerWorld world)
				|| owner.getWorld() != world || !owner.isAlive() || !eligible(target) || !eligible(owner)
				|| owner == target || owner.isTeammate(target) || duration < 1 || duration > ElementalState.MAX_DURATION
				|| skill == null || skill.length() > 128) {
			return false;
		}
		var before = state(target);
		var after = before.cold(owner.getUuid(), skill, duration, !freezeImmune(target));
		if (after.equals(before)) {
			return false;
		}
		save(target, after);
		world.spawnParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getBodyY(.5), target.getZ(),
				after.freezeTicks() > 0 ? 12 : 4, .3, .4, .3, .02);
		return true;
	}

	/** Runs only for damage actually reaching health after ward and mitigation. */
	public static void damaged(LivingEntity target, DamageSource source, float amount) {
		completeShatter(target, source, amount);
		if (amount <= 0 || !Float.isFinite(amount) || !(target.getWorld() instanceof ServerWorld world)
				|| !eligible(target) || !fire(source)) {
			return;
		}
		var before = state(target);
		var after = before.fire();
		if (!after.equals(before)) {
			save(target, after);
			world.spawnParticles(ParticleTypes.CLOUD, target.getX(), target.getBodyY(.5), target.getZ(),
					8, .3, .4, .3, .02);
		}
	}

	/** Runs after native reductions, before ward/absorption, on the existing damage call. */
	public static float prepareHit(LivingEntity target, DamageSource source, float amount) {
		if (amount <= 0 || !Float.isFinite(amount) || !(target.getWorld() instanceof ServerWorld world)
				|| !eligible(target) || !(source.getAttacker() instanceof LivingEntity owner) || !eligible(owner)
				|| owner == target || owner.getWorld() != world || owner.isTeammate(target)
				|| NeoForgeAilments.damageType(source) != null) {
			return amount;
		}
		var context = owner instanceof ServerPlayerEntity player ? ArpgSkillDamageContext.currentFor(player) : null;
		if (context != null && (context.kind() == ArpgDamageKind.DAMAGE_OVER_TIME || context.kind() == ArpgDamageKind.TRIGGER)) {
			return amount;
		}
		boolean melee = source.getSource() == owner && (source.isOf(DamageTypes.PLAYER_ATTACK)
				|| source.isOf(DamageTypes.MOB_ATTACK) || source.isOf(DamageTypes.MOB_ATTACK_NO_AGGRO));
		var type = IronsDamageSourceCompat.school(source).orElse(null);
		if (type == null) {
			type = context != null ? ElementalReaction.singleType(context.tags()) : melee ? DamageType.PHYSICAL : null;
		}
		var before = state(target);
		var reaction = ElementalReaction.select(before, type, melee, false);
		if (reaction == ElementalReaction.NONE) {
			return amount;
		}
		if (reaction == ElementalReaction.SHATTER) {
			int tick = world.getServer().getTicks();
			var existing = SHATTERS.get(target);
			if (!ElementalReaction.Reservation.canReserve(existing == null ? null : existing.reservation(), tick)) {
				return amount;
			}
			// Reserve before callbacks; a nested hit cannot claim this frame's bonus again.
			SHATTERS.put(target, new ShatterAttempt(new WeakReference<>(source), new ElementalReaction.Reservation(before, tick, false)));
		} else {
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getBodyY(.5), target.getZ(),
					4, .3, .4, .3, .02);
		}
		return (float) reaction.scale(amount);
	}

	private static void completeShatter(LivingEntity target, DamageSource source, float amount) {
		if (!(target.getWorld() instanceof ServerWorld world)) {
			return;
		}
		var attempt = SHATTERS.get(target);
		int tick = world.getServer().getTicks();
		if (attempt == null || attempt.source().get() != source || !attempt.reservation().canComplete(tick, amount)) {
			return;
		}
		var context = source.getAttacker() instanceof ServerPlayerEntity player ? ArpgSkillDamageContext.currentFor(player) : null;
		if (context != null && (context.kind() == ArpgDamageKind.DAMAGE_OVER_TIME || context.kind() == ArpgDamageKind.TRIGGER)) {
			return;
		}
		SHATTERS.put(target, new ShatterAttempt(attempt.source(), attempt.reservation().complete()));
		var current = state(target);
		var after = attempt.reservation().settle(current, tick, amount);
		if (!after.equals(current)) {
			save(target, after);
			world.spawnParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getBodyY(.5), target.getZ(),
					16, .4, .5, .4, .05);
		}
	}

	private static boolean fire(DamageSource source) {
		if (source.isIn(DamageTypeTags.IS_FIRE) || IronsDamageSourceCompat.school(source).orElse(null) == DamageType.FIRE) {
			return true;
		}
		var context = source.getAttacker() instanceof ServerPlayerEntity player
				? ArpgSkillDamageContext.currentFor(player) : null;
		return context != null && context.tags().contains("fire") && context.tags().stream()
				.filter(tag -> java.util.Set.of("physical", "cold", "lightning", "blood", "holy", "ender", "nature").contains(tag))
				.findAny().isEmpty();
	}

	public static void tick(LivingEntity target) {
		if (!(target.getWorld() instanceof ServerWorld world)) {
			return;
		}
		var attempt = SHATTERS.get(target);
		if (attempt != null && attempt.reservation().tick() != world.getServer().getTicks()) {
			SHATTERS.remove(target);
		}
		if (!target.isAlive()) {
			clear(target);
			return;
		}
		if (target.getPersistentData().contains(DATA) || target.isTouchingWater()) {
			save(target, state(target).tick(target.isTouchingWater()));
		} else {
			movement(target, ElementalState.empty());
		}
	}

	public static void clear(LivingEntity target) {
		SHATTERS.remove(target);
		target.getPersistentData().remove(DATA);
		movement(target, ElementalState.empty());
	}

	public static void copyStatus(LivingEntity original, LivingEntity replacement) {
		if (replacement.getWorld() instanceof ServerWorld) {
			save(replacement, ElementalStateNbt.read(original.getPersistentData().getCompound(DATA)));
		}
	}

	private static boolean eligible(LivingEntity target) {
		return target.isAlive() && !(target instanceof FakePlayer)
				&& (!(target instanceof ServerPlayerEntity player) || !player.isCreative() && !player.isSpectator());
	}

	private static void movement(LivingEntity target, ElementalState state) {
		var attribute = target.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (attribute == null) {
			return;
		}
		double multiplier = eligible(target) ? state.movementMultiplier() : 1;
		if (state.coldOwner() != null && target.getWorld() instanceof ServerWorld world
				&& world.getEntity(state.coldOwner()) instanceof LivingEntity owner && owner.isTeammate(target)) {
			multiplier = 1;
		}
		var existing = attribute.getModifier(MOVEMENT);
		double amount = multiplier - 1;
		if (existing == null && amount == 0 || existing != null && existing.value() == amount) {
			return;
		}
		attribute.removeModifier(MOVEMENT);
		if (amount != 0) {
			attribute.addTemporaryModifier(new EntityAttributeModifier(MOVEMENT, amount,
					EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private record ShatterAttempt(WeakReference<DamageSource> source, ElementalReaction.Reservation reservation) {
	}
}
