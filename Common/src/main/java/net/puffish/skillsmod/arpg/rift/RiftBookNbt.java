package net.puffish.skillsmod.arpg.rift;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.HashMap;
import java.util.UUID;

/** Versioned session persistence; return anchors survive disconnects and server restart. */
public final class RiftBookNbt {
	private RiftBookNbt() {
	}

	public static NbtCompound anchor(RiftSession.ReturnPoint point) {
		var tag = new NbtCompound();
		tag.putString("dimension", point.dimension());
		tag.putDouble("x", point.x());
		tag.putDouble("y", point.y());
		tag.putDouble("z", point.z());
		tag.putFloat("yaw", point.yaw());
		tag.putFloat("pitch", point.pitch());
		tag.putString("mode", point.mode());
		return tag;
	}

	public static RiftSession.ReturnPoint anchor(NbtCompound tag) {
		return new RiftSession.ReturnPoint(tag.getString("dimension"), tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"),
				tag.getFloat("yaw"), tag.getFloat("pitch"), tag.getString("mode"));
	}

	public static NbtCompound write(RiftBook book) {
		var tag = new NbtCompound();
		tag.putInt("schema", 2);
		var entries = new NbtCompound();
		book.sessions().forEach((player, session) -> {
			var entry = new NbtCompound();
			entry.putString("id", session.id().toString());
			entry.putInt("slot", session.slot());
			entry.putInt("tier", session.apotheosisTier());
			entry.putString("completion", session.completion());
			entry.putString("phase", session.phase().name());
			entry.put("origin", anchor(session.origin()));
			if (session.boss() != null) {
				entry.putString("boss", session.boss().toString());
			}
			entry.putInt("build", session.buildIndex());
			entry.putLong("deadline", session.deadline());
			entries.put(player.toString(), entry);
		});
		tag.put("sessions", entries);
		return tag;
	}

	public static RiftBook read(NbtCompound tag) {
		if (tag.isEmpty()) {
			return new RiftBook(java.util.Map.of());
		}
		int schema = tag.getInt("schema");
		if ((schema != 1 && schema != 2) || !(tag.get("sessions") instanceof NbtCompound)) {
			throw new IllegalArgumentException("Unsupported rift save schema");
		}
		var entries = tag.getCompound("sessions");
		if (entries.getKeys().size() > RiftBook.MAX_RECORDS) {
			throw new IllegalArgumentException("Too many rift anchors");
		}
		var sessions = new HashMap<UUID, RiftSession>();
		for (String key : entries.getKeys()) {
			if (!(entries.get(key) instanceof NbtCompound entry)) {
				throw new IllegalArgumentException("Malformed rift session");
			}
			if (schema == 2 && !entry.contains("tier", NbtElement.INT_TYPE)) {
				throw new IllegalArgumentException("Missing rift tier snapshot");
			}
			var player = UUID.fromString(key);
			sessions.put(player, new RiftSession(UUID.fromString(entry.getString("id")), player, entry.getInt("slot"),
					entry.getString("completion"), RiftSession.Phase.valueOf(entry.getString("phase")), anchor(entry.getCompound("origin")),
					entry.contains("boss") ? UUID.fromString(entry.getString("boss")) : null, entry.getInt("build"), entry.getLong("deadline"), schema == 1 ? 0 : entry.getInt("tier")));
		}
		return new RiftBook(sessions);
	}
}
