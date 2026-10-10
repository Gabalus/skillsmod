package net.puffish.skillsmod.arpg.spell;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Loaded only after Iron's is detected; registers spells on its provider-owned registry. */
public final class ArpgIronsSpells {
	private static final DeferredRegister<AbstractSpell> SPELLS = DeferredRegister.create(
			SpellRegistry.SPELL_REGISTRY_KEY, "puffish_skills");

	static {
		SPELLS.register("storm_pulse", () -> new ArpgPulseSpell(false));
		SPELLS.register("rime_pulse", () -> new ArpgPulseSpell(true));
	}

	private ArpgIronsSpells() {
	}

	public static void register(IEventBus bus) {
		SPELLS.register(bus);
	}
}
