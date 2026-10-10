package net.puffish.skillsmod.arpg.combat;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.puffish.skillsmod.client.network.packets.in.MeleeKitInPacket;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeleeKitViewTest {
	private MeleeKitView.Action action(MeleeKit.Attack attack, double cost, int recovery, String rejection) {
		return new MeleeKitView.Action(attack, attack.level(), cost, recovery, true, 250, rejection);
	}

	@Test
	void packetRoundTripRetainsReadinessCostsSpecializationAndOpenIntent() {
		var view = new MeleeKitView("longsword", "Defensive Liechtenauer", "Refresh to update", 12, 20,
				List.of(action(MeleeKit.Attack.HEAVY, 7.8, 0, ""), action(MeleeKit.Attack.DRIVING, 6, 8, "Recovering")));
		for (boolean open : new boolean[]{false, true}) {
			var buf = new PacketByteBuf(Unpooled.buffer());
			try {
				buf.writeBoolean(open);
				view.write(buf);
				var packet = MeleeKitInPacket.read(buf);
				assertEquals(open, packet.open());
				assertEquals(view, packet.view());
				assertEquals(2, packet.view().actions().getFirst().points());
				assertTrue(packet.view().actions().getFirst().available());
				assertFalse(packet.view().actions().getLast().available());
				assertEquals(0, buf.readableBytes());
			} finally {
				buf.release();
			}
		}
	}

	@Test
	void unreadyUnknownAndUnspecializedStatesRemainExplicit() {
		assertFalse(action(MeleeKit.Attack.HEAVY, -1, 0, "").available());
		assertFalse(action(MeleeKit.Attack.HEAVY, 8, 1, "").available());
		assertFalse(action(MeleeKit.Attack.HEAVY, 8, 0, "Equip a sword").available());
		var unselected = new MeleeKitView.Action(MeleeKit.Attack.HEAVY, 3, 8, 0, false, 0, "");
		assertEquals(0, unselected.points());
		assertEquals(20, new MeleeKitView.Action(MeleeKit.Attack.HEAVY, 3, 8, 0, true, 100_000, "").points());
		assertTrue(MeleeKitView.unavailable("Epic Fight is missing").actions().isEmpty());
	}

	@Test
	void malformedSnapshotsCannotCreateDuplicateButtonsOrInvalidResourceDisplays() {
		var action = action(MeleeKit.Attack.HEAVY, 8, 0, "");
		assertThrows(IllegalArgumentException.class, () -> new MeleeKitView("", "", "", 0, 20, List.of(action, action)));
		assertThrows(IllegalArgumentException.class, () -> new MeleeKitView("", "", "", Double.NaN, 20, List.of()));
		assertThrows(IllegalArgumentException.class, () -> new MeleeKitView("", "", "", 21, 20, List.of()));
		assertThrows(IllegalArgumentException.class, () -> action(MeleeKit.Attack.HEAVY, -.5, 0, ""));
		assertThrows(IllegalArgumentException.class, () -> action(MeleeKit.Attack.HEAVY, Double.POSITIVE_INFINITY, 0, ""));
		assertThrows(IllegalArgumentException.class, () -> action(MeleeKit.Attack.HEAVY, 8, 81, ""));
		assertThrows(IllegalArgumentException.class, () -> action(MeleeKit.Attack.HEAVY, 8, 0, "x".repeat(257)));
		assertThrows(IllegalArgumentException.class, () -> new MeleeKitView.Action(MeleeKit.Attack.HEAVY, 3, 8, 0, false, 1, ""));
	}

	@Test
	void decoderRejectsOversizedCountsAndUnrecognizedActionNames() {
		for (int count : new int[]{-1, 3, 1}) {
			var buf = new PacketByteBuf(Unpooled.buffer());
			try {
				buf.writeString("");
				buf.writeString("");
				buf.writeString("");
				buf.writeDouble(0);
				buf.writeDouble(20);
				buf.writeVarInt(count);
				if (count == 1) {
					buf.writeString("unexpected_command");
				}
				assertThrows(IllegalArgumentException.class, () -> MeleeKitView.read(buf));
			} finally {
				buf.release();
			}
		}
	}
}
