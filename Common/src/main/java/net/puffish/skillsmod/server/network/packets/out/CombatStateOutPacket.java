package net.puffish.skillsmod.server.network.packets.out;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.Identifier;
import net.puffish.skillsmod.arpg.combat.CombatState;
import net.puffish.skillsmod.network.OutPacket;
import net.puffish.skillsmod.network.Packets;

public record CombatStateOutPacket(CombatState state) implements OutPacket {
	@Override
	public void write(RegistryByteBuf buf) {
		buf.writeString(state.pillar().id());
		buf.writeLong(state.revision());
		buf.writeVarInt(state.resources().size());
		for (var entry : state.resources().entrySet()) {
			buf.writeString(entry.getKey().id());
			buf.writeDouble(entry.getValue().current());
			buf.writeDouble(entry.getValue().maximum());
		}
	}

	@Override
	public Identifier getId() {
		return Packets.COMBAT_STATE;
	}
}
