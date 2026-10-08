package net.puffish.skillsmod.client.data;

import net.puffish.skillsmod.arpg.combat.CombatState;

import java.util.Optional;

/** Latest server snapshot used by future HUD and pillar-specific screens. */
public final class ClientCombatStateData {
	private CombatState state;

	public Optional<CombatState> get() {
		return Optional.ofNullable(state);
	}

	public void set(CombatState state) {
		this.state = state;
	}

	public void clear() {
		state = null;
	}
}
