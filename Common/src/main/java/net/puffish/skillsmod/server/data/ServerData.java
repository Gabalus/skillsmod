package net.puffish.skillsmod.server.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.arpg.rift.RiftBook;
import net.puffish.skillsmod.arpg.rift.RiftBookNbt;
import net.puffish.skillsmod.arpg.tower.TowerRecoveryRecord;
import net.puffish.skillsmod.arpg.tower.TowerRecoveryNbt;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

public class ServerData extends PersistentState {
	private final Map<UUID, PlayerData> players = new HashMap<>();
	private RiftBook rifts = new RiftBook(Map.of());
	private final Map<UUID, TowerRecoveryRecord> towerRecovery = new HashMap<>();
	private final Map<UUID, List<ItemStack>> riftLoot = new HashMap<>();

	private ServerData() {

	}

	private static ServerData read(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		var playersData = new ServerData();
		if (tag.contains("rifts") && !(tag.get("rifts") instanceof NbtCompound)) {
			throw new IllegalArgumentException("Malformed rift save");
		}
		if (tag.contains("tower_recovery") && !(tag.get("tower_recovery") instanceof NbtCompound)) {
			throw new IllegalArgumentException("Malformed tower recovery save");
		}
		playersData.towerRecovery.putAll(TowerRecoveryNbt.read(tag.getCompound("tower_recovery"), lookup));
		playersData.rifts = RiftBookNbt.read(tag.getCompound("rifts"));
		var loot = tag.getCompound("rift_loot");
		if (loot.getKeys().size() > RiftBook.MAX_RECORDS) {
			throw new IllegalArgumentException("Too many rift recovery records");
		}
		for (String key : loot.getKeys()) {
			var items = new ArrayList<ItemStack>();
			if (!(loot.get(key) instanceof NbtList entries)
					|| (!entries.isEmpty() && entries.getHeldType() != NbtElement.COMPOUND_TYPE)) {
				throw new IllegalArgumentException("Malformed rift recovery list");
			}
			if (entries.size() > 1024) {
				throw new IllegalArgumentException("Too many rift recovery items");
			}
			for (var entry : entries) {
				items.add(ItemStack.fromNbt(lookup, entry).orElseThrow(() -> new IllegalArgumentException("Malformed rift recovery item")));
			}
			playersData.riftLoot.put(UUID.fromString(key), items);
		}

		var playersNbt = tag.getCompound("players");
		playersNbt.getKeys().forEach(key -> playersData.players.put(
				UUID.fromString(key),
				PlayerData.read(playersNbt.getCompound(key))
		));

		return playersData;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		var playersNbt = new NbtCompound();
		for (var entry : players.entrySet()) {
			playersNbt.put(
					entry.getKey().toString(),
					entry.getValue().writeNbt(new NbtCompound())
			);
		}
		nbt.put("players", playersNbt);
		nbt.put("tower_recovery", TowerRecoveryNbt.write(towerRecovery, lookup));
		nbt.put("rifts", RiftBookNbt.write(rifts));
		var loot = new NbtCompound();
		riftLoot.forEach((player, items) -> {
			var entries = new NbtList();
			for (var item : items) {
				if (!item.isEmpty()) {
					entries.add(item.encode(lookup));
				}
			}
			loot.put(player.toString(), entries);
		});
		nbt.put("rift_loot", loot);

		return nbt;
	}

	public static PersistentState.Type<ServerData> getPersistentStateType() {
		return new PersistentState.Type<>(
				ServerData::new,
				ServerData::read,
				null
		);
	}

	public static ServerData getOrCreate(MinecraftServer server) {
		var persistentStateManager = server.getOverworld().getPersistentStateManager();

		return persistentStateManager.getOrCreate(
				getPersistentStateType(),
				SkillsAPI.MOD_ID
		);
	}

	public PlayerData getPlayerData(ServerPlayerEntity player) {
		return players.computeIfAbsent(player.getUuid(), uuid -> PlayerData.empty());
	}

	public TowerRecoveryRecord towerRecovery(UUID player) {
		return towerRecovery.get(player);
	}

	public void setTowerRecovery(UUID player, TowerRecoveryRecord record) {
		if (record == null) {
			towerRecovery.remove(player);
		} else {
			if (!towerRecovery.containsKey(player) && towerRecovery.size() >= TowerRecoveryNbt.MAX_RECORDS) {
				throw new IllegalStateException("Tower recovery storage is full");
			}
			towerRecovery.put(player, record);
		}
	}

	public RiftBook rifts() {
		return rifts;
	}

	public List<ItemStack> riftLoot(UUID player) {
		return riftLoot.getOrDefault(player, List.of()).stream().map(ItemStack::copy).toList();
	}

	public void setRiftLoot(UUID player, List<ItemStack> items) {
		if (items.size() > 1024 || (!riftLoot.containsKey(player) && riftLoot.size() >= RiftBook.MAX_RECORDS)) {
			throw new IllegalStateException("Rift recovery storage is full");
		}
		var nonempty = items.stream().filter(item -> !item.isEmpty()).map(ItemStack::copy).toList();
		if (nonempty.isEmpty()) {
			riftLoot.remove(player);
		} else {
			riftLoot.put(player, nonempty);
		}
	}

	public void putPlayerData(ServerPlayerEntity player, PlayerData data) {
		players.put(player.getUuid(), data);
	}

	@Override
	public boolean isDirty() {
		return true;
	}
}
