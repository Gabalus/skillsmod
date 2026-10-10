package net.puffish.skillsmod.arpg.combat;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.Locale;

/** Invalid/future saves fail closed instead of producing unbounded or ownerless damage. */
public final class AilmentStateNbt {
	private AilmentStateNbt() {
	}

	public static NbtCompound write(AilmentState state) {
		var nbt = new NbtCompound();
		nbt.putInt("schema", 1);
		var list = new NbtList();
		for (var application : state.applications()) {
			var item = new NbtCompound();
			item.putString("type", application.type().id());
			item.putUuid("owner", application.owner());
			item.putString("skill", application.skill());
			item.putDouble("damage", application.damage());
			item.putInt("remaining", application.remaining());
			item.putInt("until_tick", application.untilTick());
			list.add(item);
		}
		nbt.put("applications", list);
		return nbt;
	}

	public static AilmentState read(NbtCompound nbt) {
		try {
			if (nbt.getInt("schema") != 1 || !(nbt.get("applications") instanceof NbtList list)
					|| list.size() > 10 || (!list.isEmpty() && list.getHeldType() != NbtElement.COMPOUND_TYPE)) {
				return AilmentState.empty();
			}
			var applications = new ArrayList<AilmentState.Application>();
			for (int i = 0; i < list.size(); i++) {
				var item = list.getCompound(i);
				applications.add(new AilmentState.Application(
						AilmentType.valueOf(item.getString("type").toUpperCase(Locale.ROOT)), item.getUuid("owner"),
						item.getString("skill"), item.getDouble("damage"), item.getInt("remaining"), item.getInt("until_tick")));
			}
			return new AilmentState(applications);
		} catch (RuntimeException error) {
			return AilmentState.empty();
		}
	}
}
