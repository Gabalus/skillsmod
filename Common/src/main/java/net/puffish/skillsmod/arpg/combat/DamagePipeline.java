package net.puffish.skillsmod.arpg.combat;

import net.puffish.skillsmod.arpg.skill.SkillTag;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatSnapshot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/** Pure combat arithmetic. Native armor may be left to the loader by passing armor = 0. */
public final class DamagePipeline {
	private DamagePipeline() {
	}

	public static Result resolve(HitContext hit, ArpgStatSnapshot attack, ArpgStatSnapshot defense,
			List<DamageConversion> conversions, double armor, boolean blocked, boolean elementalOnly) {
		var tags = EnumSet.noneOf(ArpgStat.class);
		if (hit.tags().contains(SkillTag.ATTACK)) {
			tags.add(ArpgStat.ATTACK_DAMAGE);
		}
		if (hit.tags().contains(SkillTag.SPELL)) {
			tags.add(ArpgStat.SPELL_DAMAGE);
		}
		if (hit.tags().contains(SkillTag.MELEE)) {
			tags.add(ArpgStat.MELEE_DAMAGE);
		}
		if (hit.tags().contains(SkillTag.PROJECTILE)) {
			tags.add(ArpgStat.PROJECTILE_DAMAGE);
		}
		if (hit.tags().contains(SkillTag.AREA)) {
			tags.add(ArpgStat.AREA_DAMAGE);
		}
		if (hit.tags().contains(SkillTag.MINION)) {
			tags.add(ArpgStat.SUMMON_DAMAGE);
		}
		if (hit.damageOverTime()) {
			tags.add(ArpgStat.DAMAGE_OVER_TIME);
		}

		var packets = new ArrayList<Packet>();
		for (var type : DamageType.values()) {
			double base = hit.damage().getOrDefault(type, 0.0);
			// Added damage never creates new damage-over-time channels.
			if (!hit.damageOverTime()) {
				base += attack.get(type.damageStat()).flat();
			}
			if (base > 0) {
				packets.add(new Packet(type, base, EnumSet.of(type.damageStat())));
			}
		}

		for (var type : DamageType.values()) {
			var outgoing = conversions.stream().filter(c -> c.from() == type).toList();
			if (outgoing.isEmpty()) {
				continue;
			}
			double total = outgoing.stream().mapToDouble(DamageConversion::fraction).sum();
			var next = new ArrayList<Packet>();
			for (var packet : packets) {
				if (packet.type() != type) {
					next.add(packet);
					continue;
				}
				next.add(new Packet(type, packet.amount() * Math.max(0, 1 - total), packet.history()));
				for (var conversion : outgoing) {
					var history = EnumSet.copyOf(packet.history());
					history.add(conversion.to().damageStat());
					next.add(new Packet(conversion.to(), packet.amount() * conversion.fraction() / Math.max(1, total), history));
				}
			}
			packets = next;
		}

		Map<DamageType, Double> scaled = new EnumMap<>(DamageType.class);
		for (var packet : packets) {
			if (elementalOnly && packet.type() != DamageType.FIRE && packet.type() != DamageType.COLD && packet.type() != DamageType.LIGHTNING) {
				continue;
			}
			var applicable = EnumSet.copyOf(packet.history());
			applicable.addAll(tags);
			double increased = 0;
			double more = 1;
			for (var stat : applicable) {
				increased += attack.get(stat).increased();
				more *= attack.get(stat).multiplier();
			}
			double amount = packet.amount() * Math.max(0, 1 + increased) * more;
			if (hit.critical() && !hit.damageOverTime()) {
				amount *= Math.max(1, attack.apply(ArpgStat.CRITICAL_MULTIPLIER, 1.5));
			}
			scaled.merge(packet.type(), amount, Double::sum);
		}

		Map<DamageType, Double> mitigated = new EnumMap<>(DamageType.class);
		for (var entry : scaled.entrySet()) {
			var type = entry.getKey();
			double amount = entry.getValue();
			if (type == DamageType.PHYSICAL && !hit.damageOverTime()) {
				double reduction = armor <= 0 ? 0 : Math.min(0.9, armor / (armor + 5 * amount));
				amount *= 1 - reduction;
			} else if (type != DamageType.PHYSICAL) {
				amount = mitigateResistance(amount, type, attack, defense, hit.damageOverTime());
			}
			mitigated.put(type, blocked ? 0.0 : amount);
		}
		return new Result(Map.copyOf(scaled), Map.copyOf(mitigated), hit.critical(), blocked);
	}

	/** Shared ARPG resistance/penetration arithmetic for custom hits and provider bridges. */
	public static double mitigateResistance(
			double amount,
			DamageType type,
			ArpgStatSnapshot attack,
			ArpgStatSnapshot defense,
			boolean damageOverTime
	) {
		if (!Double.isFinite(amount) || amount < 0.0) {
			throw new IllegalArgumentException("Damage must be finite and nonnegative");
		}
		if (type == null || type == DamageType.PHYSICAL || attack == null || defense == null) {
			throw new IllegalArgumentException("Resistance mitigation requires a non-physical type and snapshots");
		}

		double resistance = Math.min(0.75, defense.apply(type.resistanceStat(), 0));
		double penetration = damageOverTime ? 0 : attack.apply(type.penetrationStat(), 0);
		return amount * (1 - Math.max(-1, resistance - penetration));
	}

	private record Packet(DamageType type, double amount, EnumSet<ArpgStat> history) {
	}

	public record Result(Map<DamageType, Double> beforeMitigation, Map<DamageType, Double> damage, boolean critical, boolean blocked) {
		public double total() {
			return damage.values().stream().mapToDouble(Double::doubleValue).sum();
		}
	}
}
