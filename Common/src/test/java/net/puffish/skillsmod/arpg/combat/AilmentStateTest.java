package net.puffish.skillsmod.arpg.combat;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AilmentStateTest {
	private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

	private static AilmentState.Application application(AilmentType type, UUID owner, double damage, int duration) {
		return new AilmentState.Application(type, owner, "puffish_skills:test", damage, duration, 20);
	}

	@Test
	void exactTickBoundaryMovementAndExpiryProduceNoCatchupDamage() {
		var state = AilmentState.empty().apply(application(AilmentType.BLEED, OWNER, 3, 40));
		for (int i = 0; i < 19; i++) {
			var step = state.tick(true);
			assertTrue(step.pulses().isEmpty());
			state = step.state();
		}
		var step = state.tick(true);
		assertEquals(6, step.pulses().getFirst().damage());
		state = step.state();
		for (int i = 0; i < 19; i++) {
			state = state.tick(false).state();
		}
		step = state.tick(false);
		assertEquals(3, step.pulses().getFirst().damage());
		assertTrue(step.state().applications().isEmpty());
		assertTrue(step.state().tick(true).pulses().isEmpty());
	}

	@Test
	void strongestOwnerWinsWithoutWeakApplicationsExtendingItOrResettingPulseTiming() {
		var state = AilmentState.empty().apply(application(AilmentType.IGNITE, OWNER, 3, 80));
		state = state.tick(false).state();
		assertEquals(state, state.apply(application(AilmentType.IGNITE, OTHER, 1, 1200)));
		var stronger = state.apply(application(AilmentType.IGNITE, OTHER, 4, 40));
		assertEquals(OTHER, stronger.applications().getFirst().owner());
		assertEquals(40, stronger.applications().getFirst().remaining());
		assertEquals(19, stronger.applications().getFirst().untilTick());
		var refreshed = stronger.apply(application(AilmentType.IGNITE, OTHER, 4, 80));
		assertEquals(19, refreshed.applications().getFirst().untilTick());
	}

	@Test
	void poisonStacksAreBoundedAndStrongerReplacementKeepsOtherApplications() {
		var state = AilmentState.empty();
		for (int i = 0; i < 8; i++) {
			state = state.apply(application(AilmentType.POISON, OWNER, 1, 40));
		}
		assertEquals(8, state.applications().size());
		assertEquals(state, state.apply(application(AilmentType.POISON, OTHER, 1, 80)));
		state = state.apply(application(AilmentType.POISON, OTHER, 2, 40));
		assertEquals(8, state.applications().size());
		assertEquals(1, state.applications().stream().filter(a -> a.owner().equals(OTHER)).count());
		state = state.apply(application(AilmentType.BLEED, OWNER, 1, 40));
		assertEquals(8, state.remove(AilmentType.BLEED).applications().size());
		assertFalse(state.remove(AilmentType.POISON).has(AilmentType.POISON));
		assertTrue(state.remove(AilmentType.POISON).has(AilmentType.BLEED));
	}

	@Test
	void saveRetainsOwnerSkillDurationAndPartialIntervalAndRejectsCorruption() {
		var state = AilmentState.empty().apply(application(AilmentType.BLEED, OWNER, 3, 80));
		state = state.tick(false).state();
		assertEquals(state, AilmentStateNbt.read(AilmentStateNbt.write(state)));
		var future = AilmentStateNbt.write(state);
		future.putInt("schema", 2);
		assertTrue(AilmentStateNbt.read(future).applications().isEmpty());
		var corrupt = AilmentStateNbt.write(state);
		corrupt.getList("applications", 10).getCompound(0).putDouble("damage", Double.NaN);
		assertTrue(AilmentStateNbt.read(corrupt).applications().isEmpty());
		assertTrue(AilmentStateNbt.read(new NbtCompound()).applications().isEmpty());
	}

	@Test
	void malformedInputsAndStacksAreRejectedAndTagsNeverClaimAHitOrSpell() {
		assertThrows(IllegalArgumentException.class, () -> application(AilmentType.BLEED, OWNER, Double.NaN, 40));
		assertThrows(IllegalArgumentException.class, () -> application(AilmentType.BLEED, OWNER, 1, 1201));
		assertThrows(IllegalArgumentException.class, () -> new AilmentState(List.of(
				application(AilmentType.BLEED, OWNER, 1, 40), application(AilmentType.BLEED, OTHER, 1, 40))));
		for (var type : AilmentType.values()) {
			assertTrue(type.tags().contains("damage_over_time"));
			assertFalse(type.tags().contains("hit"));
			assertFalse(type.tags().contains("spell"));
		}
	}
}
