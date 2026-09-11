package net.puffish.skillsmod.arpg.stat;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Set;

public final class ArpgPlayerStats {
	private static final Map<ServerPlayerEntity, Profile> profiles = new WeakHashMap<>();

	private ArpgPlayerStats() {
	}

	public static synchronized void putModifier(
			ServerPlayerEntity player,
			Identifier sourceId,
			ArpgStatModifier modifier
	) {
		getProfile(player).put(sourceId, modifier);
	}

	public static synchronized void removeModifier(ServerPlayerEntity player, Identifier sourceId) {
		var profile = profiles.get(player);
		if (profile != null) {
			profile.remove(sourceId);
		}
	}

	public static synchronized ArpgStatSnapshot getSnapshot(ServerPlayerEntity player) {
		return getProfile(player).snapshot();
	}

	public static synchronized double apply(ServerPlayerEntity player, ArpgStat stat, double baseValue) {
		return getSnapshot(player).apply(stat, baseValue);
	}

	public static synchronized void clear(ServerPlayerEntity player) {
		profiles.remove(player);
	}

	private static Profile getProfile(ServerPlayerEntity player) {
		return profiles.computeIfAbsent(player, ignored -> new Profile());
	}

	public static synchronized void setRule(ServerPlayerEntity player, Identifier source, String rule) {
		var profile = getProfile(player);
		var previous = rule == null ? profile.rules.remove(source) : profile.rules.put(source, rule);
		if (!java.util.Objects.equals(previous, rule)) {
			profile.revision++;
		}
	}

	public static synchronized Set<String> getRules(ServerPlayerEntity player) {
		return Set.copyOf(getProfile(player).rules.values());
	}

	public static synchronized List<ArpgStatModifier> getModifiers(ServerPlayerEntity player) {
		return List.copyOf(getProfile(player).modifiers.values());
	}

	public static synchronized long revision(ServerPlayerEntity player) {
		return getProfile(player).revision;
	}

	public static synchronized void clearGroup(ServerPlayerEntity player, String prefix) {
		var profile = getProfile(player);
		boolean changed = profile.modifiers.keySet().removeIf(id -> id.getNamespace().equals("puffish_skills") && id.getPath().startsWith(prefix));
		changed |= profile.rules.keySet().removeIf(id -> id.getNamespace().equals("puffish_skills") && id.getPath().startsWith(prefix));
		if (changed) {
			profile.dirty = true;
			profile.revision++;
		}
	}

	private static final class Profile {
		private final Map<Identifier, ArpgStatModifier> modifiers = new HashMap<>();
		private final Map<Identifier, String> rules = new HashMap<>();
		private long revision;
		private ArpgStatSnapshot snapshot = ArpgStatCompiler.compile(modifiers.values());
		private boolean dirty;

		private void put(Identifier sourceId, ArpgStatModifier modifier) {
			var previous = modifiers.put(sourceId, modifier);
			if (!modifier.equals(previous)) {
				dirty = true;
				revision++;
			}
		}

		private void remove(Identifier sourceId) {
			if (modifiers.remove(sourceId) != null) {
				dirty = true;
				revision++;
			}
		}

		private ArpgStatSnapshot snapshot() {
			if (dirty) {
				snapshot = ArpgStatCompiler.compile(modifiers.values());
				dirty = false;
			}
			return snapshot;
		}
	}
}
