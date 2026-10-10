package net.puffish.skillsmod.arpg.tower;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Recovery snapshots outlive catalog edits; never silently truncate malformed or oversized saves. */
public final class TowerRecoveryNbt {
	public static final int MAX_RECORDS = 4096;

	private TowerRecoveryNbt() {
	}

	public static NbtCompound write(Map<UUID, TowerRecoveryRecord> records) {
		if (records.size() > MAX_RECORDS) {
			throw new IllegalArgumentException("Too many tower recovery records");
		}
		var root = new NbtCompound();
		root.putInt("schema", 1);
		var entries = new NbtCompound();
		records.forEach((owner, record) -> {
			var tag = new NbtCompound();
			var ticket = record.ticket();
			tag.putString("policy", ticket.policy());
			tag.putString("phase", ticket.phase().name());
			tag.putBoolean("captured", ticket.captured());
			var point = new NbtCompound();
			point.putString("dimension", ticket.checkpoint().dimension());
			point.putDouble("x", ticket.checkpoint().x());
			point.putDouble("y", ticket.checkpoint().y());
			point.putDouble("z", ticket.checkpoint().z());
			tag.put("checkpoint", point);
			var items = new NbtList();
			record.items().forEach(stack -> items.add(stack.copy()));
			tag.put("items", items);
			entries.put(owner.toString(), tag);
		});
		root.put("entries", entries);
		return root;
	}

	public static Map<UUID, TowerRecoveryRecord> read(NbtCompound root) {
		if (root.isEmpty()) {
			return Map.of();
		}
		if (!root.contains("schema", NbtElement.INT_TYPE) || root.getInt("schema") != 1
				|| !(root.get("entries") instanceof NbtCompound entries) || entries.getKeys().size() > MAX_RECORDS) {
			throw new IllegalArgumentException("Invalid tower recovery save schema or records");
		}
		var records = new HashMap<UUID, TowerRecoveryRecord>();
		for (var owner : entries.getKeys()) {
			if (!(entries.get(owner) instanceof NbtCompound tag) || !tag.contains("policy", NbtElement.STRING_TYPE)
					|| !tag.contains("phase", NbtElement.STRING_TYPE) || !tag.contains("captured", NbtElement.BYTE_TYPE)
					|| !(tag.get("checkpoint") instanceof NbtCompound point) || !point.contains("dimension", NbtElement.STRING_TYPE)
					|| !point.contains("x", NbtElement.DOUBLE_TYPE) || !point.contains("y", NbtElement.DOUBLE_TYPE)
					|| !point.contains("z", NbtElement.DOUBLE_TYPE) || !(tag.get("items") instanceof NbtList items)
					|| items.size() > TowerRecoveryRecord.MAX_ITEMS || !items.isEmpty() && items.getHeldType() != NbtElement.COMPOUND_TYPE) {
				throw new IllegalArgumentException("Malformed tower recovery record");
			}
			var checkpoint = new TowerLink.Anchor(point.getString("dimension"), point.getDouble("x"), point.getDouble("y"), point.getDouble("z"));
			var ticket = new TowerRecoveryTicket(tag.getString("policy"), checkpoint,
					TowerRecoveryTicket.Phase.valueOf(tag.getString("phase")), tag.getBoolean("captured"));
			var stacks = new ArrayList<NbtCompound>();
			for (var item : items) {
				stacks.add((NbtCompound) item);
			}
			records.put(UUID.fromString(owner), new TowerRecoveryRecord(ticket, stacks));
		}
		return Map.copyOf(records);
	}
}
