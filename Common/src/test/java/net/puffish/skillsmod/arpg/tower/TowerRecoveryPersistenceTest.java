package net.puffish.skillsmod.arpg.tower;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TowerRecoveryPersistenceTest {
	private static TowerRecoveryTicket ticket() {
		return TowerRecoveryTicket.begin(new TowerRecovery("arpg:recovery", Set.of("arpg:entry"),
				new TowerLink.Anchor("minecraft:overworld", .5, 65, 4.5))).capture().respawn();
	}

	private static NbtCompound item(String id, int count) {
		var item = new NbtCompound();
		item.putString("id", id);
		item.putInt("count", count);
		var components = new NbtCompound();
		components.putString("minecraft:custom_name", "{\"text\":\"Recovered blade\"}");
		components.putInt("minecraft:damage", 12);
		item.put("components", components);
		return item;
	}

	@Test
	void recoveryRetainsOwnerCheckpointPhaseAndExactItemComponents() {
		var owner = UUID.randomUUID();
		var snapshot = item("minecraft:diamond_sword", 1);
		var record = new TowerRecoveryRecord(ticket(), List.of(snapshot));
		var restored = TowerRecoveryNbt.read(TowerRecoveryNbt.write(Map.of(owner, record)));
		assertEquals(Set.of(owner), restored.keySet());
		assertEquals(record.ticket(), restored.get(owner).ticket());
		assertEquals(snapshot, restored.get(owner).items().get(0));
		assertEquals(Map.of(), TowerRecoveryNbt.read(new NbtCompound()));
	}

	@Test
	void copiesProtectQueueAndPartialClaimsRetainOnlyTheRemainder() {
		var snapshot = item("minecraft:diamond", 64);
		var record = new TowerRecoveryRecord(ticket().returned(), List.of(snapshot));
		snapshot.putInt("count", 1);
		snapshot.getCompound("components").remove("minecraft:custom_name");
		assertEquals(64, record.items().get(0).getInt("count"));
		assertEquals(2, record.items().get(0).getCompound("components").getKeys().size());
		var insertion = record.items().get(0);
		insertion.putInt("count", 24);
		assertEquals(64, record.items().get(0).getInt("count"));
		var remainder = new TowerRecoveryRecord(record.ticket(), List.of(insertion));
		var owner = UUID.randomUUID();
		var restored = TowerRecoveryNbt.read(TowerRecoveryNbt.write(Map.of(owner, remainder)));
		assertEquals(24, restored.get(owner).items().get(0).getInt("count"));
		assertThrows(IllegalArgumentException.class, () -> new TowerRecoveryRecord(ticket(), java.util.Collections.nCopies(1025, insertion)));
	}

	@Test
	void unknownModSnapshotsRemainRecoverableWithoutRegistryBootstrap() {
		var owner = UUID.randomUUID();
		var snapshot = item("example_removed_mod:runic_blade", 1);
		snapshot.getCompound("components").putString("example_removed_mod:rune", "storm");
		var restored = TowerRecoveryNbt.read(TowerRecoveryNbt.write(Map.of(owner, new TowerRecoveryRecord(ticket(), List.of(snapshot)))));
		assertEquals(snapshot, restored.get(owner).items().get(0));
	}

	@Test
	void rejectsFutureMalformedAndOversizedSavesWithoutTruncation() {
		var owner = UUID.randomUUID();
		var root = TowerRecoveryNbt.write(Map.of(owner, new TowerRecoveryRecord(ticket(), List.of(item("minecraft:diamond", 1)))));
		root.putInt("schema", 2);
		assertThrows(IllegalArgumentException.class, () -> TowerRecoveryNbt.read(root));
		root.putInt("schema", 1);
		root.getCompound("entries").getCompound(owner.toString()).getCompound("checkpoint").remove("x");
		assertThrows(IllegalArgumentException.class, () -> TowerRecoveryNbt.read(root));
		root.putString("entries", "bad");
		assertThrows(IllegalArgumentException.class, () -> TowerRecoveryNbt.read(root));
		var broken = item("minecraft:diamond", 0);
		assertThrows(IllegalArgumentException.class, () -> new TowerRecoveryRecord(ticket(), List.of(broken)));
		var records = new java.util.HashMap<UUID, TowerRecoveryRecord>();
		for (int i = 0; i < 4097; i++) {
			records.put(new UUID(0, i), new TowerRecoveryRecord(ticket(), List.of()));
		}
		assertThrows(IllegalArgumentException.class, () -> TowerRecoveryNbt.write(records));
	}
}
