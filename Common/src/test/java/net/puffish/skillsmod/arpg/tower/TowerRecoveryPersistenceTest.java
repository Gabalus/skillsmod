package net.puffish.skillsmod.arpg.tower;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerRecoveryPersistenceTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
	}

	private static TowerRecoveryTicket ticket() {
		return TowerRecoveryTicket.begin(new TowerRecovery("arpg:recovery", Set.of("arpg:entry"),
				new TowerLink.Anchor("minecraft:overworld", .5, 65, 4.5))).capture().respawn();
	}

	@Test
	void recoveryRetainsOwnerCheckpointPhaseAndItemComponents() {
		var lookup = BuiltinRegistries.createWrapperLookup();
		var owner = UUID.randomUUID();
		var stack = new ItemStack(Items.DIAMOND_SWORD);
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Recovered blade"));
		stack.setDamage(12);
		var record = new TowerRecoveryRecord(ticket(), List.of(stack));
		var restored = TowerRecoveryNbt.read(TowerRecoveryNbt.write(Map.of(owner, record), lookup), lookup);
		assertEquals(Set.of(owner), restored.keySet());
		assertEquals(record.ticket(), restored.get(owner).ticket());
		assertTrue(ItemStack.areEqual(stack, restored.get(owner).items().get(0)));
		assertEquals(Map.of(), TowerRecoveryNbt.read(new NbtCompound(), lookup));
	}

	@Test
	void copiesProtectQueueAndPartialClaimsRetainOnlyTheRemainder() {
		var stack = new ItemStack(Items.DIAMOND, 64);
		var record = new TowerRecoveryRecord(ticket().returned(), List.of(stack));
		stack.setCount(1);
		assertEquals(64, record.items().get(0).getCount());
		var insertion = record.items().get(0);
		insertion.decrement(40);
		assertEquals(64, record.items().get(0).getCount());
		var remainder = new TowerRecoveryRecord(record.ticket(), List.of(insertion));
		assertEquals(24, remainder.items().get(0).getCount());
		var owner = UUID.randomUUID();
		var lookup = BuiltinRegistries.createWrapperLookup();
		var restored = TowerRecoveryNbt.read(TowerRecoveryNbt.write(Map.of(owner, remainder), lookup), lookup);
		assertEquals(24, restored.get(owner).items().get(0).getCount());
		assertThrows(IllegalArgumentException.class, () -> new TowerRecoveryRecord(ticket(), java.util.Collections.nCopies(1025, insertion)));
	}

	@Test
	void rejectsFutureAndMalformedSavesWithoutDiscardingItems() {
		var lookup = BuiltinRegistries.createWrapperLookup();
		var owner = UUID.randomUUID();
		var root = TowerRecoveryNbt.write(Map.of(owner, new TowerRecoveryRecord(ticket(), List.of(new ItemStack(Items.DIAMOND)))), lookup);
		root.putInt("schema", 2);
		assertThrows(IllegalArgumentException.class, () -> TowerRecoveryNbt.read(root, lookup));
		root.putInt("schema", 1);
		root.getCompound("entries").getCompound(owner.toString()).getCompound("checkpoint").remove("x");
		assertThrows(IllegalArgumentException.class, () -> TowerRecoveryNbt.read(root, lookup));
		root.putString("entries", "bad");
		assertThrows(IllegalArgumentException.class, () -> TowerRecoveryNbt.read(root, lookup));
	}
}
