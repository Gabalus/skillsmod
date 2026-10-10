package net.puffish.skillsmod.server.network.packets.in;

import net.minecraft.network.PacketByteBuf;
import net.puffish.skillsmod.network.InPacket;

public record ChooseClassInPacket(String discipline) implements InPacket {
	public static ChooseClassInPacket read(PacketByteBuf buf) {
		return new ChooseClassInPacket(buf.readString(64));
	}
}
