package net.puffish.skillsmod.client.network.packets.in;

import net.minecraft.network.PacketByteBuf;
import net.puffish.skillsmod.arpg.combat.MeleeKitView;
import net.puffish.skillsmod.network.InPacket;

public record MeleeKitInPacket(MeleeKitView view, boolean open) implements InPacket {
	public static MeleeKitInPacket read(PacketByteBuf buf) {
		boolean open = buf.readBoolean();
		return new MeleeKitInPacket(MeleeKitView.read(buf), open);
	}
}
