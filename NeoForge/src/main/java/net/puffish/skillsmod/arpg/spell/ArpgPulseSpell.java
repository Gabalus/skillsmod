package net.puffish.skillsmod.arpg.spell;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.puffish.skillsmod.arpg.combat.ArpgDamageKind;
import net.puffish.skillsmod.arpg.rule.ArpgRuleEngine;
import net.puffish.skillsmod.arpg.rule.ArpgRuleRuntime;
import net.puffish.skillsmod.arpg.skill.ArpgSkillDamageContext;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatCompiler;
import net.puffish.skillsmod.arpg.status.NeoForgeElementalStatuses;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Bounded PvE bursts using Iron's normal cast lifecycle, spell source and provider resource costs. */
public final class ArpgPulseSpell extends AbstractSpell {
	private final boolean frost;
	private final Identifier id;
	private final DefaultConfig config;

	public ArpgPulseSpell(boolean frost) {
		this.frost = frost;
		this.id = Identifier.of("puffish_skills", frost ? "rime_pulse" : "storm_pulse");
		this.config = new DefaultConfig().setMinRarity(SpellRarity.UNCOMMON)
				.setSchoolResource(frost ? SchoolRegistry.ICE_RESOURCE : SchoolRegistry.LIGHTNING_RESOURCE)
				.setMaxLevel(5).setCooldownSeconds(frost ? 10 : 8).build();
		this.baseManaCost = frost ? 25 : 20;
		this.manaCostPerLevel = 5;
		this.baseSpellPower = frost ? 4 : 6;
		this.spellPowerPerLevel = 2;
		this.castTime = 15;
	}

	@Override
	public Identifier getSpellResource() {
		return id;
	}

	@Override
	public DefaultConfig getDefaultConfig() {
		return config;
	}

	@Override
	public CastType getCastType() {
		return CastType.LONG;
	}

	@Override
	public Optional<SoundEvent> getCastFinishSound() {
		return Optional.of(frost ? SoundEvents.BLOCK_GLASS_BREAK : SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT);
	}

	@Override
	public List<MutableText> getUniqueInfo(int spellLevel, LivingEntity caster) {
		return List.of(Text.translatable("spell.puffish_skills.pulse.info", getSpellPower(spellLevel, caster)),
				Text.translatable(frost ? "spell.puffish_skills.rime_pulse.info" : "spell.puffish_skills.storm_pulse.info"));
	}

	@Override
	public void onCast(World world, int spellLevel, LivingEntity caster, CastSource source, MagicData magic) {
		if (!(world instanceof ServerWorld server)) {
			return;
		}
		Set<String> tags = Set.of("spell", "area", "hit", frost ? "cold" : "lightning");
		float power = getSpellPower(spellLevel, caster);
		for (var target : server.getEntitiesByClass(HostileEntity.class, caster.getBoundingBox().expand(4.0),
				mob -> mob.isAlive() && mob != caster && !caster.isTeammate(mob)
						&& caster.squaredDistanceTo(mob) <= 16.0 && caster.canSee(mob))) {
			if (caster instanceof ServerPlayerEntity player) {
				// Provider spell power already includes projected global ARPG attributes.
				// Apply only context-dependent rule modifiers here, preserving the spell ID for hit procs.
				var modifiers = ArpgRuleRuntime.evaluate(player, target, ArpgRuleEngine.Event.HIT, getSpellId(), tags).modifiers();
				var snapshot = ArpgStatCompiler.compile(modifiers);
				double damage = snapshot.apply(ArpgStat.AREA_DAMAGE, snapshot.apply(
						frost ? ArpgStat.COLD_DAMAGE : ArpgStat.LIGHTNING_DAMAGE,
						snapshot.apply(ArpgStat.SPELL_DAMAGE, power)));
				var context = new ArpgSkillDamageContext.Active(player, ArpgDamageKind.SPELL, getSpellId(), tags, false);
				ArpgSkillDamageContext.run(context, () -> hit(caster, target, (float) damage, spellLevel));
			} else {
				hit(caster, target, power, spellLevel);
			}
		}
		server.spawnParticles(frost ? ParticleTypes.SNOWFLAKE : ParticleTypes.ELECTRIC_SPARK,
				caster.getX(), caster.getY() + 1.0, caster.getZ(), 60, 2.0, 0.5, 2.0, 0.05);
		super.onCast(world, spellLevel, caster, source, magic);
	}

	private void hit(LivingEntity caster, HostileEntity target, float damage, int spellLevel) {
		if (damage > 0.0f && Float.isFinite(damage) && DamageSources.applyDamage(target, damage, getDamageSource(caster))) {
			if (frost) {
				NeoForgeElementalStatuses.applyCold(caster, target, 40 + 10 * spellLevel, getSpellId());
			} else {
				target.takeKnockback(0.35, caster.getX() - target.getX(), caster.getZ() - target.getZ());
			}
		}
	}
}
