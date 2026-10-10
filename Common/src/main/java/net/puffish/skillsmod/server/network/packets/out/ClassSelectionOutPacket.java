package net.puffish.skillsmod.server.network.packets.out;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.Identifier;
import net.puffish.skillsmod.arpg.character.ClassSelectionView;
import net.puffish.skillsmod.network.OutPacket;
import net.puffish.skillsmod.network.Packets;

public record ClassSelectionOutPacket(ClassSelectionView view, String message) implements OutPacket {
	@Override
	public void write(RegistryByteBuf buf) {
		buf.writeString(view.primary(), 64);
		buf.writeString(message, 256);
		buf.writeVarInt(view.options().size());
		for (var option : view.options()) {
			buf.writeString(option.id(), 64);
			buf.writeString(option.title(), 80);
			buf.writeVarInt(option.strength());
			buf.writeVarInt(option.dexterity());
			buf.writeVarInt(option.intelligence());
		}
	}

	@Override
	public Identifier getId() {
		return Packets.CLASS_SELECTION;
	}
}
