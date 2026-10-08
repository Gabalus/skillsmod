package net.puffish.skillsmod.client.network.packets.in;

import net.minecraft.network.PacketByteBuf;
import net.puffish.skillsmod.arpg.sandbox.CraftworkView;
import net.puffish.skillsmod.network.InPacket;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public record CraftworkInPacket(CraftworkView view, boolean open, String message) implements InPacket {
	public static CraftworkInPacket read(PacketByteBuf buf) {
		boolean open = buf.readBoolean();
		String message = buf.readString(512);
		String operation = buf.readString(256);
		String mechanic = buf.readString(256);
		String expected = buf.readString(256);
		int step = buf.readVarInt();
		int total = buf.readVarInt();
		int score = buf.readVarInt();
		int mistakes = buf.readVarInt();
		int temperature = buf.readVarInt();
		int x = buf.readVarInt();
		int y = buf.readVarInt();
		long tick = buf.readLong();
		boolean complete = buf.readBoolean();
		boolean failed = buf.readBoolean();
		int smithing = buf.readVarInt();
		int runecraft = buf.readVarInt();
		int forgeQuality = buf.readVarInt();
		int runeQuality = buf.readVarInt();
		var actions = new ArrayList<String>();
		int count = boundedCount(buf);
		for (int i = 0; i < count; i++) {
			actions.add(buf.readString(256));
		}
		var recipes = new LinkedHashMap<String, Boolean>();
		var costs = new LinkedHashMap<String, String>();
		count = boundedCount(buf);
		for (int i = 0; i < count; i++) {
			String id = buf.readString(256);
			if (recipes.put(id, buf.readBoolean()) != null) {
				throw new IllegalArgumentException("Duplicate craftwork recipe in packet");
			}
			costs.put(id, buf.readString(512));
		}
		return new CraftworkInPacket(new CraftworkView(operation, mechanic, expected, step, total, score, mistakes,
				temperature, x, y, tick, complete, failed, smithing, runecraft, forgeQuality, runeQuality, actions, recipes, costs), open, message);
	}

	private static int boundedCount(PacketByteBuf buf) {
		int count = buf.readVarInt();
		if (count < 0 || count > 64) {
			throw new IllegalArgumentException("Invalid craftwork collection size");
		}
		return count;
	}
}
