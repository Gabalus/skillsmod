package net.puffish.skillsmod.arpg.combat;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElementalReactionTest {
	private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private static ElementalState frozen() {
		return ElementalState.empty().water().cold(OWNER, "rime", 80, true).cold(OWNER, "rime", 80, true);
	}

	@Test
	void wetLightningAmplifiesOnlyTheExistingHitAndPreservesStatuses() {
		var state = frozen();
		var reaction = ElementalReaction.select(state, DamageType.LIGHTNING, false, false);
		assertEquals(ElementalReaction.CONDUCTIVITY, reaction);
		assertEquals(12, reaction.scale(10));
		assertEquals(state, reaction.settle(state));
		assertEquals(ElementalReaction.NONE, ElementalReaction.select(ElementalState.empty(), DamageType.LIGHTNING, false, false));
	}

	@Test
	void physicalMeleeShatterConsumesFreezeWithoutRefundingRecoveryOrWetness() {
		var state = frozen();
		var reaction = ElementalReaction.select(state, DamageType.PHYSICAL, true, false);
		assertEquals(ElementalReaction.SHATTER, reaction);
		assertEquals(12.5, reaction.scale(10));
		var after = reaction.settle(state);
		assertEquals(0, after.freezeTicks());
		assertEquals(200, after.freezeRecoveryTicks());
		assertEquals(100, after.wetTicks());
		assertEquals(OWNER, after.coldOwner());
		assertEquals("rime", after.coldSkill());
		assertEquals(ElementalReaction.NONE, ElementalReaction.select(after, DamageType.PHYSICAL, true, false));
	}

	@Test
	void dotsProjectilesUnknownChannelsAndUnfrozenMeleeCannotShatter() {
		assertEquals(ElementalReaction.NONE, ElementalReaction.select(frozen(), DamageType.PHYSICAL, false, false));
		assertEquals(ElementalReaction.NONE, ElementalReaction.select(frozen(), DamageType.PHYSICAL, true, true));
		assertEquals(ElementalReaction.NONE, ElementalReaction.select(frozen(), DamageType.LIGHTNING, false, true));
		assertEquals(ElementalReaction.NONE, ElementalReaction.select(frozen(), null, true, false));
		assertEquals(ElementalReaction.NONE, ElementalReaction.select(ElementalState.empty(), DamageType.PHYSICAL, true, false));
		assertEquals(DamageType.PHYSICAL, ElementalReaction.singleType(Set.of("attack", "melee", "physical", "target_frozen")));
		assertEquals(null, ElementalReaction.singleType(Set.of("fire", "physical", "melee")));
		assertEquals(null, ElementalReaction.singleType(Set.of("attack", "melee")));
	}

	@Test
	void reservationBlocksNestedClaimsAndCompletesOnlyOnceOnPositiveHealthDamage() {
		var state = frozen();
		var reservation = new ElementalReaction.Reservation(state, 20, false);
		assertFalse(ElementalReaction.Reservation.canReserve(reservation, 20));
		assertFalse(reservation.canComplete(20, 0));
		assertFalse(reservation.canComplete(20, Double.NaN));
		assertFalse(reservation.canComplete(21, 10));
		assertEquals(state, reservation.settle(state, 20, 0));
		assertEquals(0, reservation.settle(state, 20, 10).freezeTicks());
		assertTrue(reservation.canComplete(20, 10));
		var completed = reservation.complete();
		assertFalse(completed.canComplete(20, 10));
		assertFalse(ElementalReaction.Reservation.canReserve(completed, 20));
		assertEquals(state, completed.settle(state, 20, 10));
		assertTrue(ElementalReaction.Reservation.canReserve(completed, 21));
		assertTrue(ElementalReaction.Reservation.canReserve(null, 20));
	}

	@Test
	void changedFreezeCannotBeConsumedAndDamageRemainsFinite() {
		var state = frozen();
		var reservation = new ElementalReaction.Reservation(state, 20, false);
		var advanced = state.tick(false);
		assertEquals(advanced, reservation.settle(advanced, 20, 10));
		assertEquals(state.fire(), reservation.settle(state.fire(), 20, 10));
		assertTrue(ElementalReaction.sameFreeze(state, new ElementalState(0, 0, 0, 30, 200, OWNER, "rime")));
		assertEquals((double) Float.MAX_VALUE, ElementalReaction.SHATTER.scale(Float.MAX_VALUE));
		assertThrows(IllegalArgumentException.class, () -> ElementalReaction.SHATTER.scale(Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> ElementalReaction.CONDUCTIVITY.scale(-1));
		assertThrows(IllegalArgumentException.class, () -> new ElementalReaction.Reservation(ElementalState.empty(), 20, false));
	}
}
