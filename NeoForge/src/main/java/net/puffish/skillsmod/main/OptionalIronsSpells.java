package net.puffish.skillsmod.main;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

/** Isolates provider classes from installations without Iron's Spells. */
public final class OptionalIronsSpells {
	private OptionalIronsSpells() {
	}

	public static void registerIfLoaded(IEventBus bus) {
		if (!ModList.get().isLoaded("irons_spellbooks")) {
			return;
		}
		try {
			Class.forName("net.puffish.skillsmod.arpg.spell.ArpgIronsSpells")
					.getMethod("register", IEventBus.class).invoke(null, bus);
		} catch (ReflectiveOperationException | LinkageError exception) {
			throw new IllegalStateException("Cannot register ARPG spells against the installed Iron's API", exception);
		}
	}
}
