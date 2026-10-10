package net.puffish.skillsmod.arpg.combat;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElementalStateTest {
	private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Test
	void dryBuildupConsumesChillAndRetainsTheTriggeringOwnerAtFreeze() {
		var state = ElementalState.empty();
		assertEquals(1, state.movementMultiplier());
		state = state.cold(OWNER, "rime", 80, true);
		assertEquals(1, state.chillStacks());
		assertEquals(.85, state.movementMultiplier(), .000001);
		state = state.cold(OWNER, "rime", 40, true);
		assertEquals(2, state.chillStacks());
		assertEquals(80, state.chillTicks());
		assertEquals(.7, state.movementMultiplier(), .000001);
		state = state.cold(OTHER, "cold_blade", 40, true);
		assertEquals(0, state.chillStacks());
		assertEquals(30, state.freezeTicks());
		assertEquals(200, state.freezeRecoveryTicks());
		assertEquals(0, state.movementMultiplier());
		assertEquals(OTHER, state.coldOwner());
		assertEquals("cold_blade", state.coldSkill());
		assertEquals(state, state.cold(OWNER, "rime", 1200, true));
	}

	@Test
	void waterRefreshesWetAndLeavingWaterExpiresItWithoutCatchup() {
		var state = ElementalState.empty().water();
		for (int i = 0; i < 100; i++) {
			state = state.tick(true);
			assertEquals(100, state.wetTicks());
		}
		for (int i = 0; i < 99; i++) {
			state = state.tick(false);
		}
		assertEquals(1, state.wetTicks());
		assertFalse(state.tick(false).active());
	}

	@Test
	void wetDoublesBuildupAndImmuneTargetsRemainAtBoundedChill() {
		var wet = ElementalState.empty().water().cold(OWNER, "rime", 80, true);
		assertEquals(2, wet.chillStacks());
		assertEquals(30, wet.cold(OWNER, "rime", 80, true).freezeTicks());
		var immune = ElementalState.empty().water();
		for (int i = 0; i < 100; i++) {
			immune = immune.cold(OWNER, "rime", 80, false);
		}
		assertEquals(2, immune.chillStacks());
		assertEquals(0, immune.freezeTicks());
		assertEquals(0, immune.freezeRecoveryTicks());
	}

	@Test
	void freezeExpiresExactlyOnceAndRecoveryPreventsImmediateRefreezing() {
		var state = ElementalState.empty().water().cold(OWNER, "rime", 80, true)
				.cold(OWNER, "rime", 80, true);
		for (int i = 0; i < 29; i++) {
			state = state.tick(false);
		}
		assertEquals(1, state.freezeTicks());
		state = state.tick(false);
		assertEquals(0, state.freezeTicks());
		assertEquals(170, state.freezeRecoveryTicks());
		state = state.cold(OWNER, "rime", 1200, true);
		assertEquals(2, state.chillStacks());
		assertEquals(0, state.cold(OWNER, "rime", 1200, true).freezeTicks());
		for (int i = 0; i < 170; i++) {
			state = state.tick(false);
		}
		assertEquals(0, state.freezeRecoveryTicks());
		assertEquals(30, state.cold(OWNER, "rime", 80, true).freezeTicks());
	}

	@Test
	void fireConsumesWetAndColdButNeverRefundsFreezeRecovery() {
		var state = ElementalState.empty().water().cold(OWNER, "rime", 80, true);
		assertEquals(ElementalState.empty(), state.fire());
		state = state.cold(OWNER, "rime", 80, true).fire();
		assertEquals(0, state.wetTicks());
		assertEquals(0, state.chillStacks());
		assertEquals(0, state.freezeTicks());
		assertEquals(200, state.freezeRecoveryTicks());
		assertEquals(OWNER, state.coldOwner());
		assertEquals(state, state.fire());
	}

	@Test
	void savePreservesPartialTimersAndColdAttributionWhileRejectingInvalidSaves() {
		var state = ElementalState.empty().water().cold(OWNER, "rime", 80, true)
				.cold(OTHER, "cold_blade", 80, true).tick(false);
		assertEquals(state, ElementalStateNbt.read(ElementalStateNbt.write(state)));
		var future = ElementalStateNbt.write(state);
		future.putInt("schema", 2);
		assertEquals(ElementalState.empty(), ElementalStateNbt.read(future));
		var corrupt = ElementalStateNbt.write(state);
		corrupt.remove("cold_owner");
		assertEquals(ElementalState.empty(), ElementalStateNbt.read(corrupt));
		corrupt = ElementalStateNbt.write(state);
		corrupt.putInt("freeze_recovery", 1200);
		assertEquals(ElementalState.empty(), ElementalStateNbt.read(corrupt));
		assertEquals(ElementalState.empty(), ElementalStateNbt.read(new NbtCompound()));
	}

	@Test
	void malformedStatesAreRejectedAndExpiryCleansUpAttribution() {
		assertThrows(IllegalArgumentException.class, () -> ElementalState.empty().cold(OWNER, "rime", 1201, true));
		assertThrows(IllegalArgumentException.class, () -> ElementalState.empty().cold(null, "rime", 40, true));
		assertThrows(IllegalArgumentException.class, () -> new ElementalState(0, 3, 40, 0, 0, OWNER, "rime"));
		assertThrows(IllegalArgumentException.class, () -> new ElementalState(0, 1, 0, 0, 0, OWNER, "rime"));
		var state = ElementalState.empty().cold(OWNER, "rime", 1, true).tick(false);
		assertEquals(ElementalState.empty(), state);
		assertTrue(ElementalState.empty().water().active());
	}

	@Test
	void targetTagsRemainSeparateFromDamageTypeAndDeliveryTags() {
		var state = ElementalState.empty().water().cold(OWNER, "rime", 80, true);
		assertEquals(Set.of("target_wet", "target_chilled"), state.targetTags());
		state = state.cold(OWNER, "rime", 80, true);
		assertEquals(Set.of("target_wet", "target_frozen"), state.targetTags());
		assertTrue(state.fire().targetTags().isEmpty());
	}
}
