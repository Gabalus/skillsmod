package net.puffish.skillsmod.server.network.packets.out;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.Identifier;
import net.puffish.skillsmod.arpg.sandbox.CraftworkView;
import net.puffish.skillsmod.network.OutPacket;
import net.puffish.skillsmod.network.Packets;

public record CraftworkOutPacket(CraftworkView view, boolean open, String message) implements OutPacket {
	@Override
	public Identifier getId() {
		return Packets.CRAFTWORK;
	}

	@Override
	public void write(RegistryByteBuf buf) {
		buf.writeBoolean(open);
		buf.writeString(message, 512);
		buf.writeString(view.operation(), 256);
		buf.writeString(view.mechanic(), 256);
		buf.writeString(view.expected(), 256);
		buf.writeVarInt(view.step());
		buf.writeVarInt(view.total());
		buf.writeVarInt(view.score());
		buf.writeVarInt(view.mistakes());
		buf.writeVarInt(view.temperature());
		buf.writeVarInt(view.x());
		buf.writeVarInt(view.y());
		buf.writeLong(view.snapshotTick());
		buf.writeBoolean(view.complete());
		buf.writeBoolean(view.failed());
		buf.writeVarInt(view.smithing());
		buf.writeVarInt(view.runecraft());
		buf.writeVarInt(view.forgeQuality());
		buf.writeVarInt(view.runeQuality());
		buf.writeVarInt(view.actions().size());
		for (var action : view.actions()) {
			buf.writeString(action, 256);
		}
		buf.writeVarInt(view.recipes().size());
		for (var entry : view.recipes().entrySet()) {
			buf.writeString(entry.getKey(), 256);
			buf.writeBoolean(entry.getValue());
			buf.writeString(view.recipeCosts().get(entry.getKey()), 512);
		}
	}
}
