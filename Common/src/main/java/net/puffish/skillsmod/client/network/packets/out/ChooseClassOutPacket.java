package net.puffish.skillsmod.client.network.packets.out;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.Identifier;
import net.puffish.skillsmod.network.OutPacket;
import net.puffish.skillsmod.network.Packets;

public record ChooseClassOutPacket(String discipline) implements OutPacket {
	@Override
	public void write(RegistryByteBuf buf) {
		buf.writeString(discipline, 64);
	}

	@Override
	public Identifier getId() {
		return Packets.CHOOSE_CLASS;
	}
}
