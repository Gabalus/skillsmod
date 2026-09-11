package net.puffish.skillsmod.arpg.reward;

import net.minecraft.util.Identifier;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.api.json.JsonElement;
import net.puffish.skillsmod.api.reward.Reward;
import net.puffish.skillsmod.api.reward.RewardConfigContext;
import net.puffish.skillsmod.api.reward.RewardDisposeContext;
import net.puffish.skillsmod.api.reward.RewardUpdateContext;
import net.puffish.skillsmod.api.util.Problem;
import net.puffish.skillsmod.api.util.Result;
import net.puffish.skillsmod.arpg.data.ArpgData;
import net.puffish.skillsmod.arpg.stat.ArpgPlayerStats;

import java.util.UUID;

public final class ArpgRuleReward implements Reward {
	private final Identifier source = SkillsMod.createIdentifier("arpg/rule/" + UUID.randomUUID());
	private final String rule;

	private ArpgRuleReward(String rule) {
		this.rule = rule;
	}

	public static void register() {
		SkillsAPI.registerReward(SkillsMod.createIdentifier("arpg_rule"), ArpgRuleReward::parse);
	}

	private static Result<ArpgRuleReward, Problem> parse(RewardConfigContext context) {
		return context.getData().andThen(JsonElement::getAsObject).andThen(object -> object.getString("rule"))
				.andThen(id -> ArpgData.content().rules().containsKey(id)
						? Result.success(new ArpgRuleReward(id)) : Result.failure(Problem.message("Unknown ARPG rule: " + id)));
	}

	@Override
	public void update(RewardUpdateContext context) {
		ArpgPlayerStats.setRule(context.getPlayer(), source, context.getCount() > 0 ? rule : null);
	}

	@Override
	public void dispose(RewardDisposeContext context) {
		for (var player : context.getServer().getPlayerManager().getPlayerList()) {
			ArpgPlayerStats.setRule(player, source, null);
		}
	}
}
