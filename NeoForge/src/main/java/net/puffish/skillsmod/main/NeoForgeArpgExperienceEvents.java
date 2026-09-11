package net.puffish.skillsmod.main;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.arpg.character.ArpgCharacter;
import net.puffish.skillsmod.arpg.character.ArpgProgression;

/** Converts player-caused living-entity experience drops into persistent ARPG character experience. */
@EventBusSubscriber(modid = SkillsAPI.MOD_ID)
public final class NeoForgeArpgExperienceEvents {
	private NeoForgeArpgExperienceEvents() {
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
		if (!(event.getAttackingPlayer() instanceof ServerPlayerEntity player)) {
			return;
		}

		long reward = ArpgCharacter.killExperience(event.getOriginalExperience());
		if (reward <= 0L) {
			return;
		}

		var character = ArpgProgression.character(player);
		int previousLevel = character.level();
		int gainedLevels = character.gainExperience(reward);
		ArpgProgression.sync(player);

		if (gainedLevels > 0) {
			player.sendMessage(Text.literal(
					"ARPG level up: " + previousLevel + " -> " + character.level()
							+ " (" + reward + " XP from kill)"), false);
		}
	}
}
