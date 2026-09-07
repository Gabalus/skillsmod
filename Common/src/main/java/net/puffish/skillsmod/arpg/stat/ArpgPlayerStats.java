package net.puffish.skillsmod.arpg.stat;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

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

	private static final class Profile {
		private final Map<Identifier, ArpgStatModifier> modifiers = new HashMap<>();
		private ArpgStatSnapshot snapshot = ArpgStatCompiler.compile(modifiers.values());
		private boolean dirty;

		private void put(Identifier sourceId, ArpgStatModifier modifier) {
			var previous = modifiers.put(sourceId, modifier);
			if (!modifier.equals(previous)) {
				dirty = true;
			}
		}

		private void remove(Identifier sourceId) {
			if (modifiers.remove(sourceId) != null) {
				dirty = true;
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
