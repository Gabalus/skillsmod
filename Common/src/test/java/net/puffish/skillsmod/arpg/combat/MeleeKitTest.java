package net.puffish.skillsmod.arpg.combat;

import net.minecraft.nbt.NbtCompound;
import net.puffish.skillsmod.server.data.PlayerData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeleeKitTest {
	@Test
	void levelWeaponAndBusyGatesApplyToBothAttacks() {
		for (var attack : MeleeKit.Attack.values()) {
			for (var weapon : new String[]{"sword", "longsword"}) {
				assertEquals("", MeleeKit.rejection(new MeleeKit.Input(true, true, attack.level(), true, true, weapon, true, true, false), attack));
			}
			assertFalse(MeleeKit.rejection(new MeleeKit.Input(true, true, attack.level() - 1, true, true, "sword", true, true, false), attack).isEmpty());
			assertFalse(MeleeKit.rejection(new MeleeKit.Input(true, true, 10, true, true, "axe", true, true, false), attack).isEmpty());
			assertFalse(MeleeKit.rejection(new MeleeKit.Input(true, true, 10, true, true, "sword", true, true, true), attack).isEmpty());
			assertFalse(MeleeKit.rejection(new MeleeKit.Input(true, true, 10, true, true, "sword", false, true, false), attack).isEmpty());
			assertFalse(MeleeKit.rejection(new MeleeKit.Input(true, true, 10, true, true, "sword", true, false, false), attack).isEmpty());
		}
	}

	@Test
	void survivalClassPillarAndModeCannotBeBypassed() {
		for (int missing = 0; missing < 4; missing++) {
			assertFalse(MeleeKit.rejection(new MeleeKit.Input(missing != 0, missing != 1, 10, missing != 2,
					missing != 3, "longsword", true, true, false), MeleeKit.Attack.HEAVY).isEmpty());
		}
	}

	@Test
	void sharedCommitmentBlocksSameTickSecondActionAndRecoveryHasExactBoundary() {
		var state = MeleeCooldowns.empty().used(MeleeKit.Attack.HEAVY, 100);
		assertEquals(80, state.remaining(MeleeKit.Attack.HEAVY, 100));
		assertEquals(8, state.remaining(MeleeKit.Attack.DRIVING, 100));
		assertThrows(IllegalStateException.class, () -> state.used(MeleeKit.Attack.DRIVING, 100));
		assertEquals(1, state.remaining(MeleeKit.Attack.HEAVY, 179));
		assertEquals(0, state.remaining(MeleeKit.Attack.HEAVY, 180));
		assertEquals(0, state.remaining(MeleeKit.Attack.DRIVING, 108));
	}

	@Test
	void characterSaveRetainsCooldownsWhileLegacyStateDefaultsEmpty() {
		var data = PlayerData.empty();
		data.setMeleeCooldowns(MeleeCooldowns.empty().used(MeleeKit.Attack.DRIVING, 100));
		var restored = PlayerData.read(data.writeNbt(new NbtCompound()));
		assertEquals(60, restored.getMeleeCooldowns().remaining(MeleeKit.Attack.DRIVING, 100));
		assertEquals(data.getMeleeCooldowns(), restored.getMeleeCooldowns());
		assertEquals(MeleeCooldowns.empty(), PlayerData.read(new NbtCompound()).getMeleeCooldowns());
	}

	@Test
	void corruptFutureAndRollbackStateAreBounded() {
		var state = new MeleeCooldowns(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE).normalized(100);
		assertEquals(80, state.remaining(MeleeKit.Attack.HEAVY, 100));
		assertEquals(60, state.remaining(MeleeKit.Attack.DRIVING, 100));
		assertEquals(0, state.remaining(MeleeKit.Attack.HEAVY, 180));
		assertThrows(IllegalArgumentException.class, () -> new MeleeCooldowns(-1, 0, 0));
		var tag = MeleeCooldownsNbt.write(state);
		tag.putInt("schema", 2);
		assertThrows(IllegalArgumentException.class, () -> MeleeCooldownsNbt.read(tag));
		var malformed = new NbtCompound();
		malformed.putString("melee_cooldowns", "corrupt");
		assertThrows(IllegalArgumentException.class, () -> PlayerData.read(malformed));
	}
}
