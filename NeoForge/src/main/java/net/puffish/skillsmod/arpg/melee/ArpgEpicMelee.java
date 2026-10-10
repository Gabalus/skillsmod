package net.puffish.skillsmod.arpg.melee;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.puffish.skillsmod.arpg.combat.MeleeKit;
import net.puffish.skillsmod.arpg.combat.MeleeKitRuntime;
import net.puffish.skillsmod.main.EpicFightStaminaBridge;
import yesman.epicfight.network.EpicFightNetworkManager;
import yesman.epicfight.registry.EpicFightRegistries;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.SkillSlot;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

/** Registers additive provider skills and only touches the two dedicated ARPG containers. */
public final class ArpgEpicMelee {
	private static final DeferredRegister<Skill> SKILLS = DeferredRegister.create(EpicFightRegistries.Keys.SKILL, "puffish_skills");
	private static final net.neoforged.neoforge.registries.DeferredHolder<Skill, ArpgMeleeSkill> HEAVY = SKILLS.register(
			"measured_strike", () -> new ArpgMeleeSkill.Builder(MeleeKit.Attack.HEAVY).build(net.minecraft.util.Identifier.of("puffish_skills", "measured_strike")));
	private static final net.neoforged.neoforge.registries.DeferredHolder<Skill, ArpgMeleeSkill> DRIVING = SKILLS.register(
			"driving_slash", () -> new ArpgMeleeSkill.Builder(MeleeKit.Attack.DRIVING).build(net.minecraft.util.Identifier.of("puffish_skills", "driving_slash")));

	private ArpgEpicMelee() {
	}

	public static void register(IEventBus bus) {
		SkillSlot.ENUM_MANAGER.registerEnumCls("puffish_skills", ArpgMeleeSlots.class);
		SKILLS.register(bus);
	}

	private static SkillContainer container(ServerPlayerPatch patch, MeleeKit.Attack attack) {
		var slot = attack == MeleeKit.Attack.HEAVY ? ArpgMeleeSlots.ARPG_HEAVY : ArpgMeleeSlots.ARPG_DRIVING;
		var container = patch.getSkill(slot);
		var skill = attack == MeleeKit.Attack.HEAVY ? HEAVY.get() : DRIVING.get();
		if (container == null) {
			throw new IllegalStateException("ARPG melee slot is missing from the player patch");
		}
		if (container.getSkill() != null && container.getSkill() != skill) {
			throw new IllegalStateException("Another skill occupies the dedicated ARPG melee slot");
		}
		if (container.getSkill() == null && container.setSkill(skill)) {
			EpicFightNetworkManager.sendToPlayer(container.createSyncPacketToLocalPlayer(), patch.getOriginal());
			patch.sendToAllPlayersTrackingMe(container.createSyncPacketToRemotePlayer());
		}
		return container;
	}

	public static String use(ServerPlayerEntity player, String name) {
		var attack = "heavy".equals(name) ? MeleeKit.Attack.HEAVY : "driving".equals(name) ? MeleeKit.Attack.DRIVING : null;
		if (attack == null) {
			return "Unknown melee kit action.";
		}
		var patch = EpicFightCapabilities.getServerPlayerPatch(player);
		if (patch == null) {
			return "Epic Fight player patch is not ready.";
		}
		var container = container(patch, attack);
		var skill = (ArpgMeleeSkill) container.getSkill();
		String rejection = skill.rejection(patch);
		if (!rejection.isEmpty()) {
			return rejection;
		}
		if (!container.requestCasting(patch, new NbtCompound())) {
			return "Epic Fight rejected the attack: check stamina and combat state.";
		}
		EpicFightStaminaBridge.sync(player);
		return "";
	}

	public static String status(ServerPlayerEntity player, String name) {
		var attack = "heavy".equals(name) ? MeleeKit.Attack.HEAVY : MeleeKit.Attack.DRIVING;
		int remaining = MeleeKitRuntime.remaining(player, attack);
		var patch = EpicFightCapabilities.getServerPlayerPatch(player);
		var skill = attack == MeleeKit.Attack.HEAVY ? HEAVY.get() : DRIVING.get();
		String cost = patch == null ? "base stamina " + attack.stamina()
				: "stamina " + String.format(java.util.Locale.ROOT, "%.1f", skill.staminaCost(patch));
		return attack.title() + " | level " + attack.level() + " | " + cost
				+ " | " + (remaining == 0 ? "off cooldown" : remaining / 20.0 + "s recovery");
	}

	public static net.puffish.skillsmod.arpg.combat.MeleeKitView view(ServerPlayerEntity player) {
		var patch = EpicFightCapabilities.getServerPlayerPatch(player);
		if (patch == null) {
			return net.puffish.skillsmod.arpg.combat.MeleeKitView.unavailable("Epic Fight is preparing your character. Refresh shortly.");
		}
		var character = net.puffish.skillsmod.arpg.character.ArpgProgression.character(player);
		var nativeStatus = net.puffish.skillsmod.main.EpicFightMeleeBridge.status(player);
		var actions = new java.util.ArrayList<net.puffish.skillsmod.arpg.combat.MeleeKitView.Action>();
		for (var attack : MeleeKit.Attack.values()) {
			var skill = attack == MeleeKit.Attack.HEAVY ? HEAVY.get() : DRIVING.get();
			double cost = skill.staminaCost(patch);
			String rejection = skill.rejection(patch);
			var slot = patch.getSkill(attack == MeleeKit.Attack.HEAVY ? ArpgMeleeSlots.ARPG_HEAVY : ArpgMeleeSlots.ARPG_DRIVING);
			if (rejection.isEmpty() && (slot == null || slot.getSkill() != null && (slot.getSkill() != skill || slot.isDisabled()))) {
				rejection = "The attack slot is unavailable.";
			}
			if (!Double.isFinite(cost) || cost < 0 || cost > Float.MAX_VALUE) {
				cost = -1;
				rejection = "Stamina cost is unavailable.";
			} else if (rejection.isEmpty() && patch.getStamina() < cost) {
				rejection = "Not enough stamina. Recover before attacking.";
			}
			var definition = net.puffish.skillsmod.arpg.data.ArpgData.content().skills().get(attack.id());
			actions.add(new net.puffish.skillsmod.arpg.combat.MeleeKitView.Action(attack,
					definition == null ? attack.level() : Math.max(attack.level(), definition.level()), cost,
					MeleeKitRuntime.remaining(player, attack), character.specializations().containsKey(attack.id()),
					character.specializations().getOrDefault(attack.id(), 0), rejection.substring(0, Math.min(256, rejection.length()))));
		}
		String weapon = EpicFightCapabilities.getItemStackCapability(player.getMainHandStack()).getWeaponCategory().toString();
		String stance = nativeStatus.active() && "epicfight:liechtenauer".equals(nativeStatus.skill())
				? "Defensive Liechtenauer: " + nativeStatus.remainingTicks() / 20.0 + "s" : "Standard weapon stance";
		return new net.puffish.skillsmod.arpg.combat.MeleeKitView(weapon.substring(0, Math.min(128, weapon.length())), stance,
				"Snapshot: refresh after changing equipment or recovering. Every action is checked again by the server.",
				Math.max(0, Math.min(patch.getMaxStamina(), patch.getStamina())), Math.max(0, patch.getMaxStamina()), actions);
	}
}
