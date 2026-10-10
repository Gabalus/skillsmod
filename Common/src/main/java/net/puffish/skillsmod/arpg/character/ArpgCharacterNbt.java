package net.puffish.skillsmod.arpg.character;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.puffish.skillsmod.arpg.progression.CompletionReceipt;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public final class ArpgCharacterNbt {
	private ArpgCharacterNbt() {
	}

	public static NbtCompound write(ArpgCharacter character) {
		var nbt = new NbtCompound();
		nbt.putInt("schema", 2);
		nbt.putLong("experience", character.experience());
		nbt.putString("primary", character.primary());
		nbt.putString("secondary", character.secondary());
		nbt.putString("ascendancy", character.ascendancy());
		nbt.put("milestones", strings(character.milestones()));
		nbt.put("atlas", strings(character.atlas()));
		var skills = new NbtCompound();
		character.specializations().forEach(skills::putInt);
		nbt.put("specializations", skills);
		nbt.putInt("corruption", character.corruption());
		nbt.putInt("delve", character.deepestDelve());
		nbt.putInt("legacy_passive", character.legacyPassivePoints());
		nbt.putInt("legacy_confluence", character.legacyConfluencePoints());
		var completions = new NbtCompound();
		character.completions().forEach((id, receipt) -> {
			var entry = new NbtCompound();
			entry.putInt("passive", receipt.passivePoints());
			entry.putInt("confluence", receipt.confluencePoints());
			entry.putInt("trial", receipt.trial());
			entry.put("knowledge", strings(receipt.knowledge()));
			completions.put(id, entry);
		});
		nbt.put("completions", completions);
		return nbt;
	}

	public static ArpgCharacter read(NbtCompound nbt) {
		if (nbt.isEmpty()) {
			return new ArpgCharacter();
		}
		int schema = nbt.getInt("schema");
		if (schema != 1 && schema != 2) {
			throw new IllegalArgumentException("Unsupported ARPG character save schema: " + schema);
		}
		var skills = new HashMap<String, Integer>();
		var skillsNbt = nbt.getCompound("specializations");
		skillsNbt.getKeys().forEach(id -> skills.put(id, skillsNbt.getInt(id)));
		var character = ArpgCharacter.restore(nbt.getLong("experience"), nbt.getString("primary"), nbt.getString("secondary"),
				nbt.getString("ascendancy"), readStrings(nbt, "milestones"), readStrings(nbt, "atlas"), skills,
				nbt.getInt("corruption"), nbt.getInt("delve"));
		if (schema == 1) {
			int passive = (int) Math.min(ArpgCharacter.MAX_PASSIVE_POINTS, character.level() - 1
					+ character.milestones().stream().filter(id -> id.startsWith("campaign_")).count() * 3);
			int confluence = character.secondary().isEmpty() ? 0 : Math.min(12, 1 + (character.level() - 20) / 5);
			character.restoreCompletionProgress(passive, Math.max(0, confluence), java.util.Map.of());
		} else {
			var receipts = new HashMap<String, CompletionReceipt>();
			var entries = nbt.getCompound("completions");
			if (entries.getKeys().size() > ArpgCharacter.MAX_COMPLETIONS) {
				throw new IllegalArgumentException("Too many completion receipts");
			}
			for (String id : entries.getKeys()) {
				if (!(entries.get(id) instanceof NbtCompound entry)) {
					throw new IllegalArgumentException("Malformed completion receipt " + id);
				}
				receipts.put(id, new CompletionReceipt(id, entry.getInt("passive"), entry.getInt("confluence"),
						entry.getInt("trial"), readStrings(entry, "knowledge")));
			}
			character.restoreCompletionProgress(nbt.getInt("legacy_passive"), nbt.getInt("legacy_confluence"), receipts);
		}
		return character;
	}

	private static NbtList strings(Set<String> values) {
		var list = new NbtList();
		values.stream().sorted().forEach(value -> list.add(NbtString.of(value)));
		return list;
	}

	private static Set<String> readStrings(NbtCompound nbt, String key) {
		var result = new HashSet<String>();
		for (var value : nbt.getList(key, NbtElement.STRING_TYPE)) {
			result.add(value.asString());
		}
		return result;
	}
}
