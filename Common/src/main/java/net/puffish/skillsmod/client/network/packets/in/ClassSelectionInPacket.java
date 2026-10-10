package net.puffish.skillsmod.client.network.packets.in;

import net.minecraft.network.PacketByteBuf;
import net.puffish.skillsmod.arpg.character.ClassSelectionView;
import net.puffish.skillsmod.network.InPacket;

import java.util.ArrayList;

public record ClassSelectionInPacket(ClassSelectionView view, String message) implements InPacket {
	public static ClassSelectionInPacket read(PacketByteBuf buf) {
		String primary = buf.readString(64);
		String message = buf.readString(256);
		int count = buf.readVarInt();
		if (count < 0 || count > 32) {
			throw new IllegalArgumentException("Invalid class selection option count");
		}
		var options = new ArrayList<ClassSelectionView.Option>();
		for (int i = 0; i < count; i++) {
			options.add(new ClassSelectionView.Option(buf.readString(64), buf.readString(80),
					buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
		}
		return new ClassSelectionInPacket(new ClassSelectionView(primary, options), message);
	}
}
