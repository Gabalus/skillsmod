package net.puffish.skillsmod.arpg.character;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.arpg.data.ArpgData;
import net.puffish.skillsmod.server.data.ServerData;

public final class ArpgProgression {
	private ArpgProgression() {
	}

	public static ArpgCharacter character(ServerPlayerEntity player) {
		return ServerData.getOrCreate(player.server).getPlayerData(player).getArpg();
	}

	public static boolean canAllocate(ServerPlayerEntity player, Identifier category, String node) {
		if (!category.getNamespace().equals("puffish_skills") || !category.getPath().startsWith("arpg_")) {
			return true;
		}
		var character = character(player);
		var id = category.getPath();
		if (id.equals("arpg_test")) {
			return player.hasPermissionLevel(2);
		}
		if (character.primary().isEmpty()) {
			return false;
		}
		if (id.equals("arpg_universal")) {
			return !node.endsWith("_start") || node.equals(character.primary() + "_start");
		}
		if (id.startsWith("arpg_asc_")) {
			return !character.ascendancy().isEmpty() && id.equals("arpg_asc_" + character.ascendancy());
		}
		if (id.startsWith("arpg_confluence_")) {
			return !character.secondary().isEmpty() && id.equals("arpg_confluence_" + character.confluence());
		}
		if (id.startsWith("arpg_skill_")) {
			return character.specializations().keySet().stream().anyMatch(skill -> id.equals(skillCategory(skill)));
		}
		return id.equals("arpg_atlas") && character.milestones().contains("campaign_crypt");
	}

	public static String skillCategory(String skill) {
		return "arpg_skill_" + skill.replace(':', '_');
	}

	public static void sync(ServerPlayerEntity player) {
		var state = character(player);
		var mod = SkillsMod.getInstance();
		if (state.primary().isEmpty()) {
			return;
		}
		unlock(player, "arpg_universal", state.passivePoints());
		mod.unlockSkill(player, SkillsMod.createIdentifier("arpg_universal"), state.primary() + "_start");
		if (!state.ascendancy().isEmpty()) {
			String category = "arpg_asc_" + state.ascendancy();
			unlock(player, category, state.ascendancyPoints());
			// The central identity node is free and acts as the shared origin for the four radial branches.
			mod.unlockSkill(player, SkillsMod.createIdentifier(category), state.ascendancy() + "_start");
		}
		if (!state.secondary().isEmpty()) {
			unlock(player, "arpg_confluence_" + state.confluence(), state.confluencePoints());
		}
		for (var skill : state.specializations().keySet()) {
			unlock(player, skillCategory(skill), state.specializationPoints(skill));
		}
		if (state.milestones().contains("campaign_crypt")) {
			unlock(player, "arpg_atlas", Math.min(24, state.atlas().size()));
		}
	}

	private static void unlock(ServerPlayerEntity player, String path, int points) {
		var mod = SkillsMod.getInstance();
		var id = SkillsMod.createIdentifier(path);
		mod.setPoints(player, id, SkillsMod.createIdentifier("arpg_progression"), points, true);
		if (!mod.isCategoryUnlocked(player, id).orElse(false)) {
			mod.unlockCategory(player, id);
		}
	}

	public static void choose(ServerPlayerEntity player, String choice, String id) {
		var state = character(player);
		var catalog = ArpgData.content();
		if (choice.equals("ascendancy")) {
			var discipline = catalog.disciplines().get(state.primary());
			if (discipline == null || !discipline.ascendancies().contains(id)) {
				throw new IllegalArgumentException("Ascendancy must belong to your primary discipline");
			}
			state.chooseAscendancy(id);
		} else {
			if (!catalog.disciplines().containsKey(id)) {
				throw new IllegalArgumentException("Unknown discipline: " + id);
			}
			if (choice.equals("primary")) {
				state.choosePrimary(id);
			} else {
				state.chooseSecondary(id);
			}
		}
		sync(player);
	}
}
