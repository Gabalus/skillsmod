package net.puffish.skillsmod.arpg.combat;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatStateNbtTest {
	@Test
	void roundTripsPillarResourcesAndRevision() {
		var state = CombatState.fresh(CombatPillar.ARCANE)
				.reduce(CombatResource.MANA, 32.0)
				.gain(CombatResource.INSTABILITY, 14.0);

		assertEquals(state, CombatStateNbt.read(CombatStateNbt.write(state)));
	}

	@Test
	void missingLegacyStateUsesTheRequestedFallbackPillar() {
		var state = CombatStateNbt.read(new NbtCompound(), CombatPillar.HUNTER);
		assertEquals(CombatState.fresh(CombatPillar.HUNTER), state);
	}

	@Test
	void damagedPoolsFallBackWithoutDiscardingValidPools() {
		var nbt = CombatStateNbt.write(CombatState.fresh(CombatPillar.MARTIAL)
				.reduce(CombatResource.STAMINA, 25.0));
		var resources = nbt.getCompound("resources");
		var posture = resources.getCompound("posture");
		posture.putDouble("maximum", -1.0);

		var restored = CombatStateNbt.read(nbt);
		assertEquals(75.0, restored.require(CombatResource.STAMINA).current(), 0.000001);
		assertEquals(ResourcePool.empty(100.0), restored.require(CombatResource.POSTURE));
	}

	@Test
	void unknownFutureSchemaUsesACompleteFreshProfile() {
		var nbt = CombatStateNbt.write(CombatState.fresh(CombatPillar.GUNNER));
		nbt.putInt("schema", CombatStateNbt.CURRENT_SCHEMA + 1);

		assertEquals(
				CombatState.fresh(CombatPillar.ENGINEER),
				CombatStateNbt.read(nbt, CombatPillar.ENGINEER)
		);
	}
}
