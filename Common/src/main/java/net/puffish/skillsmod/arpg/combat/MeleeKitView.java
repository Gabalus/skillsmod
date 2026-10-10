package net.puffish.skillsmod.arpg.combat;

import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Explicitly refreshed server snapshot; it never authorizes a later combat request. */
public record MeleeKitView(String equipment, String stance, String message, double stamina, double maximumStamina, List<Action> actions) {
	public MeleeKitView {
		checkText(equipment, 128);
		checkText(stance, 128);
		checkText(message, 256);
		if (!Double.isFinite(stamina) || !Double.isFinite(maximumStamina) || stamina < 0
				|| maximumStamina < stamina || maximumStamina > Float.MAX_VALUE || actions == null || actions.size() > 2) {
			throw new IllegalArgumentException("Invalid melee snapshot");
		}
		var seen = new HashSet<MeleeKit.Attack>();
		for (var action : actions) {
			if (action == null || !seen.add(action.attack())) {
				throw new IllegalArgumentException("Duplicate or missing melee action");
			}
		}
		actions = List.copyOf(actions);
	}

	public static MeleeKitView unavailable(String message) {
		return new MeleeKitView("Unavailable", "Unavailable", message, 0, 0, List.of());
	}

	private static void checkText(String text, int maximum) {
		if (text == null || text.length() > maximum) {
			throw new IllegalArgumentException("Invalid melee snapshot text");
		}
	}

	public record Action(MeleeKit.Attack attack, int level, double cost, int recovery, boolean specialized, int experience, String rejection) {
		public Action {
			checkText(rejection, 256);
			if (attack == null || level < 1 || level > 100 || !Double.isFinite(cost) || cost < 0 && cost != -1 || cost > Float.MAX_VALUE
					|| recovery < 0 || recovery > attack.cooldown() || experience < 0 || experience > 100_000
					|| !specialized && experience != 0) {
				throw new IllegalArgumentException("Invalid melee action snapshot");
			}
		}

		public int points() {
			return specialized ? Math.min(20, 1 + experience / 250) : 0;
		}

		public boolean available() {
			return rejection.isEmpty() && recovery == 0 && cost >= 0;
		}
	}

	public void write(PacketByteBuf buf) {
		buf.writeString(equipment, 128);
		buf.writeString(stance, 128);
		buf.writeString(message, 256);
		buf.writeDouble(stamina);
		buf.writeDouble(maximumStamina);
		buf.writeVarInt(actions.size());
		for (var action : actions) {
			buf.writeString(action.attack().path(), 32);
			buf.writeVarInt(action.level());
			buf.writeDouble(action.cost());
			buf.writeVarInt(action.recovery());
			buf.writeBoolean(action.specialized());
			buf.writeVarInt(action.experience());
			buf.writeString(action.rejection(), 256);
		}
	}

	public static MeleeKitView read(PacketByteBuf buf) {
		String equipment = buf.readString(128);
		String stance = buf.readString(128);
		String message = buf.readString(256);
		double stamina = buf.readDouble();
		double maximum = buf.readDouble();
		int count = buf.readVarInt();
		if (count < 0 || count > 2) {
			throw new IllegalArgumentException("Invalid melee snapshot action count");
		}
		var actions = new ArrayList<Action>();
		for (int i = 0; i < count; i++) {
			String path = buf.readString(32);
			MeleeKit.Attack attack = switch (path) {
				case "measured_strike" -> MeleeKit.Attack.HEAVY;
				case "driving_slash" -> MeleeKit.Attack.DRIVING;
				default -> throw new IllegalArgumentException("Unknown melee snapshot action");
			};
			actions.add(new Action(attack, buf.readVarInt(), buf.readDouble(), buf.readVarInt(),
					buf.readBoolean(), buf.readVarInt(), buf.readString(256)));
		}
		return new MeleeKitView(equipment, stance, message, stamina, maximum, actions);
	}
}
