package net.puffish.skillsmod.arpg.combat;

/** Projects a provider's stamina fraction onto the existing character meter without charging it. */
public final class ProviderStaminaSnapshot {
	private ProviderStaminaSnapshot() {
	}

	public static CombatState apply(CombatState state, double current, double maximum) {
		if (state.pillar() != CombatPillar.MARTIAL || !Double.isFinite(current)
				|| !Double.isFinite(maximum) || maximum <= 0.0) {
			return state;
		}
		double fraction = Math.max(0.0, Math.min(1.0, current / maximum));
		var meter = state.require(CombatResource.STAMINA);
		return state.withPool(CombatResource.STAMINA, meter.withCurrent(fraction * meter.maximum()));
	}
}
