package net.puffish.skillsmod.client.network.packets.in;

import net.minecraft.network.PacketByteBuf;
import net.puffish.skillsmod.arpg.combat.CombatPillar;
import net.puffish.skillsmod.arpg.combat.CombatResource;
import net.puffish.skillsmod.arpg.combat.CombatState;
import net.puffish.skillsmod.arpg.combat.ResourcePool;
import net.puffish.skillsmod.network.InPacket;

import java.util.EnumMap;

public record CombatStateInPacket(CombatState state) implements InPacket {
	public static CombatStateInPacket read(PacketByteBuf buf) {
		var pillar = CombatPillar.byId(buf.readString())
				.orElseThrow(() -> new IllegalArgumentException("Unknown combat pillar in server packet"));
		long revision = buf.readLong();
		int count = buf.readVarInt();
		if (revision < 0L || count < 1 || count > CombatResource.values().length) {
			throw new IllegalArgumentException("Invalid combat state packet");
		}

		var expected = CombatState.fresh(pillar);
		var resources = new EnumMap<CombatResource, ResourcePool>(CombatResource.class);
		for (int i = 0; i < count; i++) {
			var resource = CombatResource.byId(buf.readString())
					.orElseThrow(() -> new IllegalArgumentException("Unknown combat resource in server packet"));
			var pool = new ResourcePool(buf.readDouble(), buf.readDouble());
			if (!expected.resources().containsKey(resource) || resources.put(resource, pool) != null) {
				throw new IllegalArgumentException("Invalid resource profile in combat state packet");
			}
		}
		if (!resources.keySet().equals(expected.resources().keySet())) {
			throw new IllegalArgumentException("Incomplete resource profile in combat state packet");
		}
		return new CombatStateInPacket(new CombatState(pillar, resources, revision));
	}
}
