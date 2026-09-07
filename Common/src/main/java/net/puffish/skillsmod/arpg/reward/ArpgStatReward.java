package net.puffish.skillsmod.arpg.reward;

import net.minecraft.util.Identifier;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.api.json.JsonElement;
import net.puffish.skillsmod.api.json.JsonObject;
import net.puffish.skillsmod.api.reward.Reward;
import net.puffish.skillsmod.api.reward.RewardConfigContext;
import net.puffish.skillsmod.api.reward.RewardDisposeContext;
import net.puffish.skillsmod.api.reward.RewardUpdateContext;
import net.puffish.skillsmod.api.util.Problem;
import net.puffish.skillsmod.api.util.Result;
import net.puffish.skillsmod.arpg.compat.IronsSpellbooksCompat;
import net.puffish.skillsmod.arpg.stat.ArpgModifierOperation;
import net.puffish.skillsmod.arpg.stat.ArpgPlayerStats;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatModifier;
import net.puffish.skillsmod.util.LegacyUtils;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.ArrayList;
import java.util.List;

public class ArpgStatReward implements Reward {
	public static final Identifier ID = SkillsMod.createIdentifier("arpg_stat");

	private final List<Identifier> ids = new ArrayList<>();
	private final ArpgStatModifier modifier;

	private ArpgStatReward(ArpgStatModifier modifier) {
		this.modifier = modifier;
	}

	public static void register() {
		SkillsAPI.registerReward(ID, ArpgStatReward::parse);
	}

	private static Result<ArpgStatReward, Problem> parse(RewardConfigContext context) {
		return context.getData()
				.andThen(JsonElement::getAsObject)
				.andThen(LegacyUtils.wrapNoUnused(ArpgStatReward::parse, context));
	}

	private static Result<ArpgStatReward, Problem> parse(JsonObject rootObject) {
		var problems = new ArrayList<Problem>();

		var optStatId = rootObject.getString("stat")
				.ifFailure(problems::add)
				.getSuccess();
		var optOperationId = rootObject.getString("operation")
				.ifFailure(problems::add)
				.getSuccess();
		var optValue = rootObject.getFloat("value")
				.ifFailure(problems::add)
				.getSuccess();

		ArpgStat stat = null;
		if (optStatId.isPresent()) {
			stat = ArpgStat.byId(optStatId.orElseThrow()).orElse(null);
			if (stat == null) {
				problems.add(Problem.message("Unknown ARPG stat `" + optStatId.orElseThrow() + "`"));
			}
		}

		ArpgModifierOperation operation = null;
		if (optOperationId.isPresent()) {
			operation = ArpgModifierOperation.byId(optOperationId.orElseThrow()).orElse(null);
			if (operation == null) {
				problems.add(Problem.message("Unknown ARPG modifier operation `" + optOperationId.orElseThrow() + "`"));
			}
		}

		if (problems.isEmpty()) {
			try {
				return Result.success(new ArpgStatReward(new ArpgStatModifier(
						stat,
						operation,
						optValue.orElseThrow()
				)));
			} catch (IllegalArgumentException e) {
				return Result.failure(Problem.message(e.getMessage()));
			}
		}
		return Result.failure(Problem.combine(problems));
	}

	@Override
	public void update(RewardUpdateContext context) {
		var count = context.getCount();
		var player = context.getPlayer();

		while (ids.size() < count) {
			ids.add(SkillsMod.createIdentifier(
					"arpg/modifier/" + RandomStringUtils.random(16, "abcdefghijklmnopqrstuvwxyz0123456789")
			));
		}

		for (var i = 0; i < ids.size(); i++) {
			var id = ids.get(i);
			if (i < count) {
				ArpgPlayerStats.putModifier(player, id, modifier);
				IronsSpellbooksCompat.applyModifier(player, id, modifier);
			} else {
				ArpgPlayerStats.removeModifier(player, id);
				IronsSpellbooksCompat.removeModifier(player, id, modifier.stat());
			}
		}
	}

	@Override
	public void dispose(RewardDisposeContext context) {
		for (var player : context.getServer().getPlayerManager().getPlayerList()) {
			for (var id : ids) {
				ArpgPlayerStats.removeModifier(player, id);
				IronsSpellbooksCompat.removeModifier(player, id, modifier.stat());
			}
		}
		ids.clear();
	}
}
