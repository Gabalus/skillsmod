package net.puffish.skillsmod.arpg.character;

import net.minecraft.nbt.NbtCompound;
import net.puffish.skillsmod.arpg.progression.CompletionCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArpgCharacterNbtTest {
	@Test
	void rewardsAndPendingDiscoveriesSurviveRestartAndCannotReplay() {
		var original = new ArpgCharacter();
		original.choosePrimary("warrior");
		original.setLevel(30);
		var catalog = CompletionCatalog.defaults();
		original.awardCompletion(catalog.reward("arpg:first_rift"));
		original.awardCompletion(catalog.reward("arpg:rune_rift"));
		original.awardCompletion(catalog.reward("arpg:trial_1"));
		var restored = ArpgCharacterNbt.read(ArpgCharacterNbt.write(original));
		assertEquals(original.completions(), restored.completions());
		assertEquals(original.discoveredKnowledge(), restored.discoveredKnowledge());
		assertEquals(2, restored.passivePoints());
		assertEquals(2, restored.ascendancyPoints());
		assertFalse(restored.awardCompletion(catalog.reward("arpg:first_rift")));
	}

	@Test
	void legacySchemaPreservesBalancesOnceAndFurtherLevelsGrantNothing() {
		var original = new ArpgCharacter();
		original.choosePrimary("warrior");
		original.setLevel(30);
		original.chooseSecondary("arcanist");
		original.completeMilestone("campaign_crypt");
		var legacy = ArpgCharacterNbt.write(original);
		legacy.putInt("schema", 1);
		legacy.remove("completions");
		legacy.remove("legacy_passive");
		legacy.remove("legacy_confluence");
		var migrated = ArpgCharacterNbt.read(legacy);
		assertEquals(32, migrated.passivePoints());
		assertEquals(3, migrated.confluencePoints());
		migrated.setLevel(100);
		var restarted = ArpgCharacterNbt.read(ArpgCharacterNbt.write(migrated));
		assertEquals(32, restarted.passivePoints());
		assertEquals(3, restarted.confluencePoints());
		assertEquals(2, ArpgCharacterNbt.write(restarted).getInt("schema"));
	}

	@Test
	void absentStateStartsAtZeroAndFutureSchemasFailExplicitly() {
		assertEquals(0, ArpgCharacterNbt.read(new NbtCompound()).passivePoints());
		var future = new NbtCompound();
		future.putInt("schema", 3);
		assertThrows(IllegalArgumentException.class, () -> ArpgCharacterNbt.read(future));
	}

	@Test
	void malformedReceiptsAreRejectedRatherThanSilentlyDropped() {
		var character = new ArpgCharacter();
		var tag = ArpgCharacterNbt.write(character);
		tag.getCompound("completions").putString("arpg:first_rift", "corrupt");
		assertThrows(IllegalArgumentException.class, () -> ArpgCharacterNbt.read(tag));
	}
}
