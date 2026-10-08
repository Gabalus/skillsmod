package net.puffish.skillsmod.arpg.tower;

import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.puffish.skillsmod.arpg.character.ArpgProgression;
import net.puffish.skillsmod.arpg.progression.CompletionData;
import net.puffish.skillsmod.arpg.rift.RiftRuntime;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Optional IP 6.0.7 NBT adapter. Uses registered entities, with no hard dependency or command execution. */
public final class ImmersiveTowerPortals {
	public static final String TAG = "puffish_skills:tower_portal";
	private static final Identifier TYPE = Identifier.of("immersive_portals:portal");
	private static final int MAX_PAIRS = 16;
	private static final Map<UUID, Pair> PAIRS = new HashMap<>();

	private record Pair(TowerLink link, Entity forward, Entity reverse, long revision, long expires) {
		void discard() {
			forward.discard();
			reverse.discard();
		}
	}

	private ImmersiveTowerPortals() {
	}

	public static boolean available() {
		return Registries.ENTITY_TYPE.getOrEmpty(TYPE).isPresent();
	}

	private static ServerWorld world(MinecraftServer server, TowerLink.Anchor anchor) {
		return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(anchor.dimension())));
	}

	private static boolean eligible(ServerPlayerEntity player, TowerLink link) {
		var character = ArpgProgression.character(player);
		return CompletionData.catalog().rewards().keySet().containsAll(link.prerequisites())
				&& link.eligible(character.level(), character.completions().keySet(),
						RiftRuntime.book(player.server).get(player.getUuid()) != null,
						player.isAlive(), !player.isCreative() && !player.isSpectator())
				&& !RiftRuntime.inRifts(player.getWorld())
				&& TowerPuzzleRuntime.solved(player, link.id());
	}

	public static void open(ServerPlayerEntity player, String id) {
		if (!available()) {
			throw new IllegalStateException("Install Immersive Portals for NeoForge on server and clients; no teleport fallback is used");
		}
		var link = TowerData.catalog().link(id);
		if (!eligible(player, link) || !link.nearby(player.getWorld().getRegistryKey().getValue().toString(),
				player.getX(), player.getY(), player.getZ(), 8)) {
			throw new IllegalStateException("Solve this passage's puzzle, meet its first-clear gates and stand within eight blocks of an anchor");
		}
		if (!PAIRS.containsKey(player.getUuid()) && PAIRS.size() >= MAX_PAIRS) {
			throw new IllegalStateException("Tower portal capacity reached; try again later");
		}
		var from = world(player.server, link.from());
		var to = world(player.server, link.to());
		if (!safe(from, link.from(), link) || !safe(to, link.to(), link)) {
			throw new IllegalStateException("Both anchors need loaded chunks, clear apertures, solid landing floors and world-border clearance");
		}
		var existing = PAIRS.get(player.getUuid());
		if (existing != null && existing.link().equals(link) && existing.revision() == TowerData.revision()
				&& !existing.forward().isRemoved() && !existing.reverse().isRemoved()
				&& player.server.getOverworld().getTime() < existing.expires()) {
			return;
		}
		Entity forward = null;
		Entity reverse = null;
		try {
			forward = create(from, link, player.getUuid(), false);
			reverse = create(to, link, player.getUuid(), true);
			if (!from.spawnEntity(forward) || !to.spawnEntity(reverse)) {
				throw new IllegalStateException("Immersive Portals refused the authored link");
			}
			close(player.getUuid());
			PAIRS.put(player.getUuid(), new Pair(link, forward, reverse, TowerData.revision(), player.server.getOverworld().getTime() + 6000));
		} catch (RuntimeException error) {
			if (forward != null) {
				forward.discard();
			}
			if (reverse != null) {
				reverse.discard();
			}
			throw new IllegalStateException("Tower portal creation failed: " + error.getMessage(), error);
		}
	}

	private static Entity create(ServerWorld world, TowerLink link, UUID owner, boolean reverse) {
		var entity = Registries.ENTITY_TYPE.getOrEmpty(TYPE).orElseThrow().create(world);
		if (entity == null) {
			throw new IllegalStateException("Missing portal entity factory");
		}
		var data = TowerPortalNbt.create(link, owner, reverse);
		entity.readNbt(data);
		entity.addCommandTag(TAG);
		return entity;
	}

	/** Conservatively requires the whole seam and both landing strips to be clear, without terrain writes. */
	private static boolean safe(ServerWorld world, TowerLink.Anchor anchor, TowerLink link) {
		if (world == null) {
			return false;
		}
		int bottom = (int) Math.floor(anchor.y() - link.height() / 2);
		int top = (int) Math.ceil(anchor.y() + link.height() / 2);
		if (bottom <= world.getBottomY() || top >= world.getTopY()) {
			return false;
		}
		for (int x = (int) Math.floor(anchor.x() - link.width() / 2); x < Math.ceil(anchor.x() + link.width() / 2); x++) {
			for (int z = (int) Math.floor(anchor.z() - 1); z <= Math.floor(anchor.z() + 1); z++) {
				var floor = new BlockPos(x, bottom - 1, z);
				if (!world.isChunkLoaded(floor) || !world.getWorldBorder().contains(floor)
						|| !world.getBlockState(floor).isFullCube(world, floor)
						|| !world.getBlockState(floor).getFluidState().isEmpty()
						|| world.getBlockState(floor).isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)) {
					return false;
				}
				for (int y = bottom; y < top; y++) {
					if (!world.getBlockState(new BlockPos(x, y, z)).isAir()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	public static void tick(MinecraftServer server) {
		var iterator = PAIRS.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			var pair = entry.getValue();
			var player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null || !eligible(player, pair.link()) || pair.revision() != TowerData.revision()
					|| server.getOverworld().getTime() >= pair.expires() || pair.forward().isRemoved() || pair.reverse().isRemoved()
					|| !pair.link().nearby(player.getWorld().getRegistryKey().getValue().toString(), player.getX(), player.getY(), player.getZ(), 48)
					|| !safe(world(server, pair.link().from()), pair.link().from(), pair.link())
					|| !safe(world(server, pair.link().to()), pair.link().to(), pair.link())) {
				pair.discard();
				iterator.remove();
			}
		}
	}

	public static void close(UUID player) {
		var pair = PAIRS.remove(player);
		if (pair != null) {
			pair.discard();
		}
	}

	public static void reset() {
		PAIRS.values().forEach(Pair::discard);
		PAIRS.clear();
	}
}
