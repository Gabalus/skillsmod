package net.puffish.skillsmod.arpg.tower;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;

import java.util.UUID;

/** Versioned geometry contract read by the NeoForge Immersive Portals 6.0.7 provider. */
public final class TowerPortalNbt {
	private TowerPortalNbt() {
	}

	public static NbtCompound create(TowerLink link, UUID owner, boolean reverse) {
		if (owner == null || owner.equals(new UUID(0, 0))) {
			throw new IllegalArgumentException("Tower portals require a player UUID");
		}
		var from = reverse ? link.to() : link.from();
		var to = reverse ? link.from() : link.to();
		// Do not serialize an uninitialized provider entity: its geometry is still null.
		var data = new NbtCompound();
		var position = new NbtList();
		position.add(NbtDouble.of(from.x()));
		position.add(NbtDouble.of(from.y()));
		position.add(NbtDouble.of(from.z()));
		data.put("Pos", position);
		var motion = new NbtList();
		motion.add(NbtDouble.of(0));
		motion.add(NbtDouble.of(0));
		motion.add(NbtDouble.of(0));
		data.put("Motion", motion);
		var rotation = new NbtList();
		rotation.add(NbtFloat.of(0));
		rotation.add(NbtFloat.of(0));
		data.put("Rotation", rotation);
		data.putDouble("width", link.width());
		data.putDouble("height", link.height());
		data.putDouble("thickness", 0);
		vector(data, "axisW", reverse ? -1 : 1, 0, 0);
		vector(data, "axisH", 0, 1, 0);
		vector(data, "destination", to.x(), to.y(), to.z());
		data.putString("dimensionTo", to.dimension());
		// IP's helper uses two longs rather than vanilla's UUID int-array encoding.
		data.putLong("specificPlayerMost", owner.getMostSignificantBits());
		data.putLong("specificPlayerLeast", owner.getLeastSignificantBits());
		data.putBoolean("teleportable", true);
		data.putBoolean("interactable", false);
		data.putBoolean("teleportChangesScale", false);
		data.putBoolean("teleportChangesGravity", false);
		data.putDouble("scale", 1);
		data.putString("portalTag", link.id());
		return data;
	}

	private static void vector(NbtCompound data, String key, double x, double y, double z) {
		data.putDouble(key + "X", x);
		data.putDouble(key + "Y", y);
		data.putDouble(key + "Z", z);
	}
}
