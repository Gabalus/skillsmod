package net.puffish.skillsmod.arpg.sandbox;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

/** Versioned save format. Unknown future schemas fail rather than silently wiping progress. */
public final class SandboxStateNbt {
	private SandboxStateNbt() {
	}

	public static NbtCompound write(SandboxState state) {
		var tag = new NbtCompound();
		tag.putInt("schema", 1);
		var knowledge = new NbtList();
		state.knowledge().stream().sorted().forEach(id -> knowledge.add(NbtString.of(id)));
		tag.put("knowledge", knowledge);
		var mastery = new NbtCompound();
		state.mastery().forEach(mastery::putInt);
		tag.put("mastery", mastery);
		var receipts = new NbtCompound();
		state.receipts().forEach((id, quality) -> {
			var entry = new NbtCompound(); entry.putString("operation", quality.operation()); entry.putInt("score", quality.score()); entry.putBoolean("automated", quality.automated()); receipts.put(id.toString(), entry);
		});
		tag.put("receipts", receipts);
		if (state.session() != null) {
			var session = state.session();
			var entry = new NbtCompound();
			entry.putString("id", session.id().toString());
			entry.putString("operation", session.operation());
			entry.putString("mastery", session.mastery());
			var sequence = new NbtList();
			session.sequence().forEach(action -> sequence.add(NbtString.of(action)));
			entry.put("sequence", sequence);
			entry.putInt("step", session.step());
			entry.putInt("score", session.score());
			entry.putInt("mistakes", session.mistakes());
			entry.putLong("last_tick", session.lastTick());
			entry.putString("mechanic", session.mechanic());
			var process = new NbtCompound();
			session.process().forEach(process::putInt);
			entry.put("process", process);
			tag.put("session", entry);
		}

		return tag;
	}

	public static SandboxState read(NbtCompound tag) {
		if (tag.isEmpty()) {
			return SandboxState.empty();
		}

		if (tag.getInt("schema") != 1) {
			throw new IllegalArgumentException("Unsupported sandbox save schema");
		}

		var knowledge = new HashSet<String>();
		var list = tag.getList("knowledge", NbtElement.STRING_TYPE);
		for (int i = 0; i < list.size(); i++) {
			knowledge.add(list.getString(i));
		}

		var mastery = new HashMap<String, Integer>();
		var masteryTag = tag.getCompound("mastery");
		for (var id : masteryTag.getKeys()) {
			mastery.put(id, masteryTag.getInt(id));
		}

		var receipts = new HashMap<UUID, SandboxState.Quality>();
		var receiptTag = tag.getCompound("receipts");
		for (var id : receiptTag.getKeys()) {
			var entry = receiptTag.getCompound(id);
			var uuid = UUID.fromString(id);
			receipts.put(uuid, new SandboxState.Quality(uuid, entry.getString("operation"), entry.getInt("score"), entry.getBoolean("automated")));
		}

		CraftSession session = null;
		if (tag.contains("session", NbtElement.COMPOUND_TYPE)) {
			var entry = tag.getCompound("session");
			var actions = new ArrayList<String>();
			var sequence = entry.getList("sequence", NbtElement.STRING_TYPE);
			for (int i = 0; i < sequence.size(); i++) {
				actions.add(sequence.getString(i));
			}

			var process = new HashMap<String, Integer>();
			var processTag = entry.getCompound("process");
			for (var key : processTag.getKeys()) {
				process.put(key, processTag.getInt(key));
			}

			session = new CraftSession(UUID.fromString(entry.getString("id")), entry.getString("operation"), entry.getString("mastery"), actions,
			entry.getInt("step"), entry.getInt("score"), entry.getInt("mistakes"), entry.getLong("last_tick"), entry.getString("mechanic"), process);
		}

		return new SandboxState(knowledge, mastery, receipts, session);
	}
}
