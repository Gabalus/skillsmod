package net.puffish.skillsmod.main;

import net.minecraft.server.network.ServerPlayerEntity;
import net.puffish.skillsmod.arpg.data.ArpgData;
import net.puffish.skillsmod.arpg.rule.ArpgResourceSemantics;
import net.puffish.skillsmod.arpg.rule.ArpgRuleEngine;
import net.puffish.skillsmod.arpg.rule.ArpgRuleRuntime;

import java.util.Locale;
import java.util.stream.Collectors;

/** Server-authoritative resource replacement shared by Iron's events and optional mixins. */
public final class IronsBloodMagicBridge {
	private IronsBloodMagicBridge() {
	}

	public static boolean replacesMana(ServerPlayerEntity player, String skillId) {
		var skill = ArpgData.content().skills().get(skillId);
		if (skill == null || !"irons".equals(skill.provider())) {
			return false;
		}
		var tags = skill.tags().stream()
				.map(tag -> tag.name().toLowerCase(Locale.ROOT))
				.collect(Collectors.toUnmodifiableSet());
		var evaluation = ArpgRuleRuntime.evaluate(
				player,
				null,
				ArpgRuleEngine.Event.CAST,
				skillId,
				tags
		);
		return evaluation.bloodMagic() || evaluation.spellLifeCost();
	}

	public static boolean canPayLife(ServerPlayerEntity player, int manaCost) {
		return ArpgResourceSemantics.canPayLifeCost(player.getHealth(), manaCost);
	}

	public static boolean payLife(ServerPlayerEntity player, int manaCost) {
		if (!canPayLife(player, manaCost)) {
			return false;
		}
		double next = ArpgResourceSemantics.spendLifeForMana(player.getHealth(), manaCost);
		player.setHealth((float) next);
		return true;
	}
}
