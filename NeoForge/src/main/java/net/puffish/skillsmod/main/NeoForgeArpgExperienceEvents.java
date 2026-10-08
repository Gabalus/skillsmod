package net.puffish.skillsmod.main;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.arpg.character.ArpgProgression;
import net.puffish.skillsmod.arpg.compat.ApotheosisProgressionCompat;
import net.puffish.skillsmod.arpg.compat.L2HostilityCompat;
import net.puffish.skillsmod.arpg.progression.EncounterProgressionData;
import net.puffish.skillsmod.arpg.progression.EncounterThreat;

/** Encounter-gated rewards; optional providers contribute threat, never unrestricted XP. */
@EventBusSubscriber(modid = SkillsAPI.MOD_ID)
public final class NeoForgeArpgExperienceEvents {
	private static final String FARM_SPAWN = "puffish_skills.arpg_farm_spawn";
	private static final String SETTLED = "puffish_skills.arpg_xp_settled";

	private NeoForgeArpgExperienceEvents() {
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
		if (event.isSpawnCancelled()) {
			return;
		}
		String reason = event.getSpawnType().name();
		boolean farm = switch (reason) {
			case "SPAWNER", "TRIAL_SPAWNER", "SPAWN_EGG", "DISPENSER", "BREEDING", "MOB_SUMMONED", "CONVERSION", "REINFORCEMENT" -> true;
			default -> false;
		};
		event.getEntity().getPersistentData().putBoolean(FARM_SPAWN, farm);
		captureTier(event.getEntity());
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onEntityJoin(EntityJoinLevelEvent event) {
		if (!event.getLevel().isClient() && event.getEntity() instanceof LivingEntity entity && !(entity instanceof PlayerEntity)) {
			if (event.loadedFromDisk()) {
				// Legacy mobs have no trustworthy spawn context. Never use a reload-time player's tier.
				var data = entity.getPersistentData();
				if (!data.contains(ApotheosisProgressionCompat.SPAWN_TIER)) {
					data.putInt(ApotheosisProgressionCompat.SPAWN_TIER, 0);
				}
				return;
			}
			// Invaders can be created directly instead of passing through FinalizeSpawnEvent.
			captureTier(entity);
		}
	}

	private static void captureTier(LivingEntity entity) {
		var data = entity.getPersistentData();
		if (!data.contains(ApotheosisProgressionCompat.SPAWN_TIER)) {
			var player = entity.getWorld().getClosestPlayer(entity, 64);
			data.putInt(ApotheosisProgressionCompat.SPAWN_TIER, ApotheosisProgressionCompat.currentTier(player));
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
		if (!(event.getAttackingPlayer() instanceof ServerPlayerEntity player)
				|| player instanceof FakePlayer || player.isCreative() || player.isSpectator()
				|| event.getEntity() instanceof PlayerEntity) {
			return;
		}

		var entity = event.getEntity();
		var data = entity.getPersistentData();
		if (data.getBoolean(SETTLED)) {
			return;
		}
		var l2 = L2HostilityCompat.profile(entity);
		var apoth = ApotheosisProgressionCompat.profile(entity);
		int traitRanks = (int) Math.min(1000L, l2.traits().values().stream().mapToLong(value -> Math.max(0, value)).sum());
		var threat = new EncounterThreat(l2.level(), traitRanks, apoth.tier(), apoth.elite(), apoth.invader(), l2.ineligibleSpawn());
		var policy = EncounterProgressionData.policy();
		if (!policy.eligible(entity.getWorld().getRegistryKey().getValue().toString(),
				entity.getCommandTags().contains("arpg:encounter"), entity.getCommandTags().contains("arpg:world_boss"),
				data.getBoolean(FARM_SPAWN), threat)) {
			return;
		}
		long reward = policy.reward(event.getOriginalExperience(), threat);
		if (reward <= 0L) {
			return;
		}

		// The same dead entity cannot settle twice, even if a provider fires another drop event.
		data.putBoolean(SETTLED, true);
		var character = ArpgProgression.character(player);
		int previousLevel = character.level();
		int gainedLevels = character.gainExperience(reward);
		ArpgProgression.sync(player);
		if (gainedLevels > 0) {
			player.sendMessage(Text.literal("ARPG level up: " + previousLevel + " -> " + character.level()
					+ " (" + reward + " encounter XP)"), false);
		}
	}
}
