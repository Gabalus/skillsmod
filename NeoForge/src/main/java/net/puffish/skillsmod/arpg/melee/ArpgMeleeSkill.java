package net.puffish.skillsmod.arpg.melee;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Hand;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.arpg.character.ArpgProgression;
import net.puffish.skillsmod.arpg.combat.ArpgCombatRuntime;
import net.puffish.skillsmod.arpg.combat.CombatPillar;
import net.puffish.skillsmod.arpg.combat.MeleeKit;
import net.puffish.skillsmod.arpg.combat.MeleeKitRuntime;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.skill.SkillBuilder;
import yesman.epicfight.skill.SkillCategories;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.SkillSlots;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

import java.util.Locale;

/** Stamina-paid addon action; damage is exclusively the selected provider animation's hit phase. */
public final class ArpgMeleeSkill extends Skill {
	private final MeleeKit.Attack attack;

	public static final class Builder extends SkillBuilder<Builder> {
		public Builder(MeleeKit.Attack attack) {
			super(builder -> new ArpgMeleeSkill(builder, attack));
			setCategory(SkillCategories.WEAPON_INNATE);
			setResource(Resource.STAMINA);
			setActivateType(ActivateType.ONE_SHOT);
		}
	}

	private ArpgMeleeSkill(Builder builder, MeleeKit.Attack attack) {
		super(builder);
		this.attack = attack;
		consumption = attack.stamina();
		maxStackSize = 1;
	}

	@Override
	public void loadDatapackParameters(NbtCompound parameters) {
		super.loadDatapackParameters(parameters);
		consumption = Float.isFinite(consumption) ? Math.max(1, Math.min(30, consumption)) : attack.stamina();
		maxDuration = 0;
		maxStackSize = 1;
	}

	public String rejection(ServerPlayerPatch patch) {
		var player = patch.getOriginal();
		var character = ArpgProgression.character(player);
		var category = EpicFightCapabilities.getItemStackCapability(player.getMainHandStack()).getWeaponCategory();
		var input = new MeleeKit.Input(player.isAlive() && !player.isCreative() && !player.isSpectator()
				&& !SkillsMod.getInstance().getPlatform().isFakePlayer(player), !character.primary().isEmpty(), character.level(),
				ArpgCombatRuntime.state(player).pillar() == CombatPillar.MARTIAL, patch.isEpicFightMode(),
				category.toString().toLowerCase(Locale.ROOT), patch.getPrimaryHand() == Hand.MAIN_HAND, player.getOffHandStack().isEmpty(),
				patch.isHoldingAny() || player.hasVehicle() || player.isUsingItem() || patch.isInAir() || !patch.getEntityState().canUseSkill());
		String rejection = MeleeKit.rejection(input, attack);
		if (!rejection.isEmpty()) {
			return rejection;
		}
		int remaining = MeleeKitRuntime.remaining(player, attack);
		return remaining == 0 ? "" : "Recovering for " + remaining + " ticks.";
	}

	@Override
	public boolean canExecute(SkillContainer container) {
		if (container.isDisabled() || container.getExecutor().isLogicalClient()) {
			return false;
		}
		return rejection(container.getServerExecutor()).isEmpty();
	}

	@Override
	public void executeOnServer(SkillContainer container, NbtCompound arguments) {
		var patch = container.getServerExecutor();
		// requestCasting has validated eligibility and consumed provider stamina exactly once.
		MeleeKitRuntime.used(patch.getOriginal(), attack);
		super.executeOnServer(container, arguments);
		boolean longsword = "longsword".equals(EpicFightCapabilities.getItemStackCapability(
				patch.getOriginal().getMainHandStack()).getWeaponCategory().toString().toLowerCase(Locale.ROOT));
		if (attack == MeleeKit.Attack.DRIVING) {
			patch.playAnimationSynchronized(longsword ? Animations.LONGSWORD_DASH : Animations.SWORD_DASH, 0.0f);
		} else {
			var innate = patch.getSkill(SkillSlots.WEAPON_INNATE);
			boolean defensive = innate != null && innate.getSkill() != null && innate.isActivated()
					&& "epicfight:liechtenauer".equals(innate.getSkill().getRegistryName().toString());
			if (longsword) {
				patch.playAnimationSynchronized(defensive ? Animations.LONGSWORD_LIECHTENAUER_AUTO3 : Animations.LONGSWORD_AUTO3, 0.0f);
			} else {
				patch.playAnimationSynchronized(Animations.SWORD_AUTO3, 0.0f);
			}
		}
	}
}
