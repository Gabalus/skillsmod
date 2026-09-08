package net.puffish.skillsmod.arpg.character;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public final class ArpgCharacterNbt {
	private ArpgCharacterNbt() {
	}

	public static NbtCompound write(ArpgCharacter character) {
		var nbt = new NbtCompound();
		nbt.putInt("schema", 1);
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
		return nbt;
	}

	public static ArpgCharacter read(NbtCompound nbt) {
		var skills = new HashMap<String, Integer>();
		var skillsNbt = nbt.getCompound("specializations");
		skillsNbt.getKeys().forEach(id -> skills.put(id, skillsNbt.getInt(id)));
		return ArpgCharacter.restore(nbt.getLong("experience"), nbt.getString("primary"), nbt.getString("secondary"),
				nbt.getString("ascendancy"), readStrings(nbt, "milestones"), readStrings(nbt, "atlas"), skills,
				nbt.getInt("corruption"), nbt.getInt("delve"));
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
