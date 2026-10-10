package net.puffish.skillsmod.server.network.packets.out;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.Identifier;
import net.puffish.skillsmod.arpg.combat.MeleeKitView;
import net.puffish.skillsmod.network.OutPacket;
import net.puffish.skillsmod.network.Packets;

public record MeleeKitOutPacket(MeleeKitView view, boolean open) implements OutPacket {
	@Override
	public void write(RegistryByteBuf buf) {
		buf.writeBoolean(open);
		view.write(buf);
	}

	@Override
	public Identifier getId() {
		return Packets.MELEE_KIT;
	}
}
