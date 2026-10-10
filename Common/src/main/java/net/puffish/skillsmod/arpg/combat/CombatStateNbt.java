package net.puffish.skillsmod.arpg.combat;

import net.minecraft.nbt.NbtCompound;

import java.util.EnumMap;

/** Versioned persistent representation of the server-owned combat state. */
public final class CombatStateNbt {
	public static final int CURRENT_SCHEMA = 1;

	private CombatStateNbt() {
	}

	public static NbtCompound write(CombatState state) {
		var nbt = new NbtCompound();
		nbt.putInt("schema", CURRENT_SCHEMA);
		nbt.putString("pillar", state.pillar().id());
		nbt.putLong("revision", state.revision());

		var resources = new NbtCompound();
		for (var entry : state.resources().entrySet()) {
			var pool = new NbtCompound();
			pool.putDouble("current", entry.getValue().current());
			pool.putDouble("maximum", entry.getValue().maximum());
			resources.put(entry.getKey().id(), pool);
		}
		nbt.put("resources", resources);
		return nbt;
	}

	public static CombatState read(NbtCompound nbt) {
		return read(nbt, CombatPillar.MARTIAL);
	}

	/**
	 * Reads known fields and falls back field-by-field when an old or damaged save is encountered.
	 * Unknown future schemas are not interpreted as the current layout.
	 */
	public static CombatState read(NbtCompound nbt, CombatPillar fallbackPillar) {
		if (nbt == null || fallbackPillar == null || nbt.getInt("schema") > CURRENT_SCHEMA) {
			return CombatState.fresh(fallbackPillar == null ? CombatPillar.MARTIAL : fallbackPillar);
		}

		var pillar = CombatPillar.byId(nbt.getString("pillar")).orElse(fallbackPillar);
		var defaults = CombatState.fresh(pillar);
		var restored = new EnumMap<CombatResource, ResourcePool>(CombatResource.class);
		var resources = nbt.getCompound("resources");

		for (var entry : defaults.resources().entrySet()) {
			var pool = entry.getValue();
			var element = resources.get(entry.getKey().id());
			if (element instanceof NbtCompound poolNbt) {
				pool = readPool(poolNbt, pool);
			}
			restored.put(entry.getKey(), pool);
		}

		return new CombatState(pillar, restored, Math.max(0L, nbt.getLong("revision")));
	}

	private static ResourcePool readPool(NbtCompound nbt, ResourcePool fallback) {
		double current = nbt.getDouble("current");
		double maximum = nbt.getDouble("maximum");
		try {
			return new ResourcePool(current, maximum);
		} catch (IllegalArgumentException exception) {
			return fallback;
		}
	}
}
