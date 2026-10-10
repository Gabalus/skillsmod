package net.puffish.skillsmod.arpg.rift;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.arpg.character.ArpgProgression;
import net.puffish.skillsmod.arpg.compat.ApotheosisProgressionCompat;
import net.puffish.skillsmod.arpg.progression.CompletionData;
import net.puffish.skillsmod.arpg.progression.CompletionRuntime;
import net.puffish.skillsmod.server.data.ServerData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Bounded, solo development rifts. All arena writes occur in the dedicated void dimension. */
public final class RiftRuntime {
	public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, SkillsMod.createIdentifier("rifts"));
	public static final String BOSS_TAG = "arpg:rift_boss";
	private static final String OWNER = "puffish_skills.rift_owner";
	private static final String SESSION = "puffish_skills.rift_session";
	private static final String ANCHOR = "puffish_skills.rift_return";

	private RiftRuntime() {
	}

	public static boolean inRifts(World world) {
		return world.getRegistryKey().equals(WORLD);
	}

	public static RiftBook book(MinecraftServer server) {
		return ServerData.getOrCreate(server).rifts();
	}

	public static void enter(ServerPlayerEntity player, String completion) {
		if (inRifts(player.getWorld()) || !player.isAlive() || player.isCreative() || player.isSpectator()) {
			throw new IllegalStateException("Enter a rift from the sandbox in survival/adventure mode");
		}
		var world = player.server.getWorld(WORLD);
		if (world == null) {
			throw new IllegalStateException("Rift dimension is missing; load its data pack and restart the server");
		}
		var reward = CompletionData.catalog().reward(completion);
		var character = ArpgProgression.character(player);
		if (!reward.type().equals("rift") || character.primary().isEmpty() || character.level() < reward.minimumLevel()
				|| !character.completions().keySet().containsAll(reward.prerequisites())) {
			throw new IllegalStateException("Choose a primary discipline and meet this rift's level/first-clear prerequisites");
		}
		if (ServerData.getOrCreate(player.server).towerRecovery(player.getUuid()) != null) {
			throw new IllegalStateException("Resolve pending tower recovery with /tower recover before entering a rift");
		}
		if (!ServerData.getOrCreate(player.server).riftLoot(player.getUuid()).isEmpty()) {
			throw new IllegalStateException("Recover pending rift items before entering another rift");
		}
		var origin = new RiftSession.ReturnPoint(player.getWorld().getRegistryKey().getValue().toString(),
				player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch(), player.interactionManager.getGameMode().getName());
		book(player.server).reserve(player.getUuid(), UUID.randomUUID(), completion, origin, player.server.getOverworld().getTime(), ApotheosisProgressionCompat.currentTier(player));
		player.sendMessage(Text.literal("Preparing rift arena. Stay near your entry position; /rift leave cancels."), false);
	}

	public static void tick(MinecraftServer server) {
		var book = book(server);
		long now = server.getOverworld().getTime();
		var building = book.sessions().values().stream().filter(s -> s.phase() == RiftSession.Phase.BUILDING)
				.sorted(Comparator.comparingInt(RiftSession::slot)).toList();
		UUID buildPlayer = building.isEmpty() ? null : building.get((int) (now % building.size())).player();
		for (var session : book.sessions().values()) {
			var player = server.getPlayerManager().getPlayer(session.player());
			if (session.phase() == RiftSession.Phase.EXIT_PENDING) {
				if (player != null && player.isAlive() && now % 20 == 0) {
					returnPlayer(player, session.origin());
				}
				continue;
			}
			if (player == null || now >= session.deadline()) {
				leave(server, session.player(), session.phase() == RiftSession.Phase.CLEARED ? "Returning from cleared rift" : "Rift expired or player disconnected");
				continue;
			}
			if (session.phase() == RiftSession.Phase.BUILDING) {
				if (!player.isAlive() || player.isCreative() || player.isSpectator()
						|| !player.getWorld().getRegistryKey().getValue().toString().equals(session.origin().dimension())
						|| player.squaredDistanceTo(session.origin().x(), session.origin().y(), session.origin().z()) > 64) {
					leave(server, session.player(), "Rift preparation canceled: entry position changed");
				} else if (session.player().equals(buildPlayer)) {
					build(server, session, player);
				}
				continue;
			}
			if (!player.isAlive()) {
				continue;
			}
			if (!inRifts(player.getWorld()) || !session.contains(player.getX(), player.getY(), player.getZ())
					|| player.interactionManager.getGameMode() != GameMode.ADVENTURE) {
				leave(server, session.player(), "Rift ended: arena boundary or game mode changed");
			} else if (session.phase() == RiftSession.Phase.RUNNING
					&& player.getServerWorld().getEntity(session.boss()) == null) {
				leave(server, session.player(), "Rift ended: boss is no longer present");
			}
		}
		for (var player : server.getPlayerManager().getPlayerList()) {
			if (inRifts(player.getWorld()) && book.get(player.getUuid()) == null && player.isAlive()) {
				recoverOrphan(player);
			} else if (!inRifts(player.getWorld()) && book.get(player.getUuid()) == null && player.isAlive()
					&& !ServerData.getOrCreate(server).riftLoot(player.getUuid()).isEmpty()) {
				deliverLoot(player);
			}
		}
	}

	private static void build(MinecraftServer server, RiftSession session, ServerPlayerEntity player) {
		var world = server.getWorld(WORLD);
		if (world == null) {
			leave(server, session.player(), "Rift dimension unavailable");
			return;
		}
		int end = Math.min(RiftSession.BUILD_VOLUME, session.buildIndex() + 256);
		for (int index = session.buildIndex(); index < end; index++) {
			int x = index % RiftSession.WIDTH;
			int z = (index / RiftSession.WIDTH) % RiftSession.WIDTH;
			int y = index / (RiftSession.WIDTH * RiftSession.WIDTH);
			var pos = new BlockPos(session.centerX() - RiftSession.RADIUS + x, RiftSession.FLOOR + y, session.centerZ() - RiftSession.RADIUS + z);
			boolean shell = x == 0 || z == 0 || x == RiftSession.WIDTH - 1 || z == RiftSession.WIDTH - 1 || y == 0 || y == RiftSession.HEIGHT - 1;
			var block = shell ? Blocks.BEDROCK : (y == 1 && x == RiftSession.RADIUS && z == RiftSession.RADIUS ? Blocks.SEA_LANTERN : Blocks.AIR);
			world.setBlockState(pos, block.getDefaultState(), 2);
		}
		var next = session.build(end - session.buildIndex());
		book(server).update(next);
		if (next.buildIndex() == RiftSession.BUILD_VOLUME) {
			start(server, next, player, world);
		}
	}

	private static void start(MinecraftServer server, RiftSession session, ServerPlayerEntity player, ServerWorld world) {
		var boss = EntityType.HUSK.create(world);
		if (boss == null) {
			leave(server, player.getUuid(), "Could not create rift boss");
			return;
		}
		boss.refreshPositionAndAngles(session.centerX() + 6.5, RiftSession.FLOOR + 1, session.centerZ() + .5, 90, 0);
		boss.setPersistent();
		boss.setCustomName(Text.literal("Rift Warden"));
		boss.setCustomNameVisible(true);
		boss.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(120);
		boss.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(7);
		boss.setHealth(boss.getMaxHealth());
		boss.addCommandTag(BOSS_TAG);
		boss.addCommandTag("arpg:encounter");
		boss.getPersistentData().putString(OWNER, player.getUuid().toString());
		boss.getPersistentData().putString(SESSION, session.id().toString());
		boss.getPersistentData().putInt(ApotheosisProgressionCompat.SPAWN_TIER, session.apotheosisTier());
		var running = session.start(boss.getUuid());
		book(server).update(running);
		if (!world.spawnEntity(boss)) {
			leave(server, player.getUuid(), "Could not spawn rift boss");
			return;
		}
		player.getPersistentData().put(ANCHOR, RiftBookNbt.anchor(session.origin()));
		player.changeGameMode(GameMode.ADVENTURE);
		if (!player.teleport(world, session.centerX() - 6.5, RiftSession.FLOOR + 1, session.centerZ() + .5, Set.of(), -90, 0)) {
			leave(server, player.getUuid(), "Could not enter rift");
			return;
		}
		player.fallDistance = 0;
		boss.setTarget(player);
		player.sendMessage(Text.literal("Defeat the Rift Warden. /rift leave abandons the encounter."), false);
	}

	public static RiftSession bossSession(LivingEntity entity) {
		if (!(entity.getWorld() instanceof ServerWorld world) || !entity.getCommandTags().contains(BOSS_TAG)) {
			return null;
		}
		try {
			var session = book(world.getServer()).get(UUID.fromString(entity.getPersistentData().getString(OWNER)));
			return session != null && session.phase() == RiftSession.Phase.RUNNING && entity.getUuid().equals(session.boss())
					&& session.id().toString().equals(entity.getPersistentData().getString(SESSION)) ? session : null;
		} catch (IllegalArgumentException error) {
			return null;
		}
	}

	public static void bossDefeated(LivingEntity entity, ServerPlayerEntity player) {
		var session = bossSession(entity);
		if (session == null || !session.player().equals(player.getUuid()) || !inRifts(player.getWorld())
				|| !session.contains(player.getX(), player.getY(), player.getZ()) || player.interactionManager.getGameMode() != GameMode.ADVENTURE) {
			return;
		}
		try {
			var cleared = session.clear(player.getUuid(), entity.getUuid(), player.server.getOverworld().getTime());
			book(player.server).update(cleared);
			CompletionRuntime.complete(player, session.completion());
			player.sendMessage(Text.literal("Rift cleared. Collect your loot; returning in five seconds."), false);
		} catch (IllegalArgumentException | IllegalStateException error) {
			leave(player.server, player.getUuid(), "Rift reward rejected: " + error.getMessage());
		}
	}

	public static void queueLoot(MinecraftServer server, UUID player, List<ItemStack> items) {
		var data = ServerData.getOrCreate(server);
		var all = new ArrayList<>(data.riftLoot(player));
		all.addAll(items);
		data.setRiftLoot(player, all);
	}

	public static void leave(MinecraftServer server, UUID owner, String message) {
		var session = book(server).get(owner);
		if (session == null || session.phase() == RiftSession.Phase.EXIT_PENDING) {
			return;
		}
		var world = server.getWorld(WORLD);
		if (world != null) {
			if (session.boss() != null && world.getEntity(session.boss()) != null) {
				world.getEntity(session.boss()).discard();
			}
			var items = world.getEntitiesByClass(ItemEntity.class, arenaBox(session),
					item -> owner.toString().equals(item.getPersistentData().getString(OWNER)));
			queueLoot(server, owner, items.stream().map(ItemEntity::getStack).toList());
			items.forEach(ItemEntity::discard);
		}
		book(server).update(session.exit());
		var player = server.getPlayerManager().getPlayer(owner);
		if (session.phase() == RiftSession.Phase.BUILDING) {
			book(server).returned(owner);
		} else if (player != null) {
			player.sendMessage(Text.literal(message), false);
		}
	}

	public static Box arenaBox(RiftSession session) {
		return new Box(session.centerX() - RiftSession.RADIUS, RiftSession.FLOOR, session.centerZ() - RiftSession.RADIUS,
				session.centerX() + RiftSession.RADIUS + 1, RiftSession.FLOOR + RiftSession.HEIGHT, session.centerZ() + RiftSession.RADIUS + 1);
	}

	public static void recover(MinecraftServer server) {
		for (var session : book(server).sessions().values()) {
			leave(server, session.player(), "Server restart interrupted the rift");
		}
		book(server).recoverAfterRestart();
	}

	public static void markLoot(ItemEntity item, RiftSession session) {
		item.getPersistentData().putString(OWNER, session.player().toString());
		item.getPersistentData().putString(SESSION, session.id().toString());
		item.setOwner(session.player());
	}

	/** Returns true when the item was queued and must not join the arena world. */
	public static boolean recoverItem(ItemEntity item, MinecraftServer server) {
		var data = item.getPersistentData();
		if (!data.contains(OWNER)) {
			var session = book(server).sessions().values().stream()
					.filter(s -> (s.phase() == RiftSession.Phase.RUNNING || s.phase() == RiftSession.Phase.CLEARED)
							&& s.contains(item.getX(), item.getY(), item.getZ())).findFirst();
			if (session.isEmpty()) {
				return false;
			}
			markLoot(item, session.get());
		}
		UUID owner;
		try {
			owner = UUID.fromString(data.getString(OWNER));
		} catch (IllegalArgumentException error) {
			return false;
		}
		var session = book(server).get(owner);
		if (session == null || session.phase() == RiftSession.Phase.EXIT_PENDING || !session.id().toString().equals(data.getString(SESSION))) {
			queueLoot(server, owner, List.of(item.getStack()));
			return true;
		}
		return false;
	}

	public static boolean mayPickup(ServerPlayerEntity player, ItemEntity item) {
		var session = book(player.server).get(player.getUuid());
		return session != null && (session.phase() == RiftSession.Phase.RUNNING || session.phase() == RiftSession.Phase.CLEARED)
				&& player.getUuid().toString().equals(item.getPersistentData().getString(OWNER))
				&& session.id().toString().equals(item.getPersistentData().getString(SESSION))
				&& session.contains(player.getX(), player.getY(), player.getZ());
	}

	public static void recoverPlayer(ServerPlayerEntity player) {
		var session = book(player.server).get(player.getUuid());
		if (session != null && session.phase() == RiftSession.Phase.EXIT_PENDING && player.isAlive()) {
			returnPlayer(player, session.origin());
		} else if (session == null && inRifts(player.getWorld()) && player.isAlive()) {
			recoverOrphan(player);
		}
	}

	private static void recoverOrphan(ServerPlayerEntity player) {
		try {
			returnPlayer(player, RiftBookNbt.anchor(player.getPersistentData().getCompound(ANCHOR)));
		} catch (IllegalArgumentException error) {
			var spawn = player.server.getOverworld().getSpawnPos();
			returnPlayer(player, new RiftSession.ReturnPoint("minecraft:overworld", spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5, 0, 0, "survival"));
		}
	}

	private static void returnPlayer(ServerPlayerEntity player, RiftSession.ReturnPoint point) {
		var world = player.server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(point.dimension())));
		var origin = BlockPos.ofFloored(point.x(), point.y(), point.z());
		if (world == null || inRifts(world)) {
			world = player.server.getOverworld();
			origin = world.getSpawnPos();
		}
		BlockPos target = safePosition(world, origin);
		if (target == null) {
			world = player.server.getOverworld();
			var spawn = world.getSpawnPos();
			target = safePosition(world, new BlockPos(spawn.getX(), world.getTopY(Heightmap.Type.MOTION_BLOCKING, spawn.getX(), spawn.getZ()), spawn.getZ()));
		}
		if (target == null) {
			return;
		}
		if (!player.teleport(world, target.getX() + .5, target.getY(), target.getZ() + .5, Set.of(), point.yaw(), point.pitch())) {
			return;
		}
		player.changeGameMode(point.mode().equals("adventure") ? GameMode.ADVENTURE : GameMode.SURVIVAL);
		player.fallDistance = 0;
		if (deliverLoot(player)) {
			player.getPersistentData().remove(ANCHOR);
			if (book(player.server).get(player.getUuid()) != null) {
				book(player.server).returned(player.getUuid());
			}
		}
	}

	private static boolean deliverLoot(ServerPlayerEntity player) {
		var data = ServerData.getOrCreate(player.server);
		var remaining = new ArrayList<ItemStack>();
		for (var stack : data.riftLoot(player.getUuid())) {
			player.getInventory().insertStack(stack);
			if (!stack.isEmpty() && player.dropItem(stack, false) == null) {
				remaining.add(stack);
			}
		}
		data.setRiftLoot(player.getUuid(), remaining);
		return remaining.isEmpty();
	}

	private static BlockPos safePosition(ServerWorld world, BlockPos origin) {
		for (int radius = 0; radius <= 4; radius++) {
			for (int x = -radius; x <= radius; x++) {
				for (int z = -radius; z <= radius; z++) {
					if (Math.max(Math.abs(x), Math.abs(z)) != radius) {
						continue;
					}
					for (int step = 0; step <= 8; step++) {
						int y = (step + 1) / 2 * (step % 2 == 0 ? -1 : 1);
						var feet = origin.add(x, y, z);
						if (world.getBlockState(feet).isAir() && world.getBlockState(feet.up()).isAir()
								&& world.getBlockState(feet.down()).isSolidBlock(world, feet.down())
								&& world.getBlockState(feet.down()).getFluidState().isEmpty()) {
							return feet;
						}
					}
				}
			}
		}
		return null;
	}
}
