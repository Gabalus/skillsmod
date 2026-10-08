package net.puffish.skillsmod.arpg.sandbox;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.puffish.skillsmod.server.data.ServerData;
import java.util.UUID;
import java.util.List;
import java.util.LinkedHashMap;

/** Server-owned command prototype. Station UIs and external machines call the same domain rules. */
public final class CraftworkRuntime {
	private CraftworkRuntime() {
	}

	public static SandboxState state(ServerPlayerEntity player) {
		return ServerData.getOrCreate(player.server).getPlayerData(player).getSandbox();
	}

	private static void save(ServerPlayerEntity player, SandboxState state) {
		ServerData.getOrCreate(player.server).getPlayerData(player).setSandbox(state);
	}

	public static void learn(ServerPlayerEntity player, String id) {
		save(player, state(player).learn(SandboxData.catalog(), id, SandboxData.providers()));
	}

	public static CraftworkView view(ServerPlayerEntity player) {
		var state = state(player);
		var session = state.session();
		long tick = player.getWorld().getTime();
		var choices = new LinkedHashMap<String, Boolean>();
		var costs = new LinkedHashMap<String, String>();
		SandboxData.catalog().bindings().keySet().stream().sorted().limit(64).forEach(id -> {
			boolean known;
			try {
				state.requireRecipe(SandboxData.catalog(), id, SandboxData.providers());
				known = true;
			} catch (IllegalArgumentException | IllegalStateException error) {
				known = false;
			}
			choices.put(id, known);
			var binding = SandboxData.catalog().binding(id);
			costs.put(id, binding.ingredientCount() + " x " + binding.ingredientItem());
		});
		var quality = player.getMainHandStack().getOrDefault(CraftworkItems.QUALITY, CraftedItemData.empty());
		var forge = quality.stages().get("arpg:forge");
		var rune = quality.stages().get("arpg:inscribe");
		int temperature = session == null ? 0 : session.process().getOrDefault("temperature", 0);
		if (session != null && "arpg:thermal_forge".equals(session.mechanic()) && !session.complete()) {
			temperature = (int) Math.max(0, temperature - Math.min(1000, Math.max(0, tick - session.lastTick())));
		}
		return new CraftworkView(session == null ? "" : session.operation(), session == null ? "" : session.mechanic(),
				session == null ? "" : session.expected(), session == null ? 0 : session.step(), session == null ? 0 : session.sequence().size(),
				session == null ? 0 : session.score(), session == null ? 0 : session.mistakes(), temperature,
				session == null ? 0 : session.process().getOrDefault("x", 0), session == null ? 0 : session.process().getOrDefault("y", 0), tick,
				session != null && session.complete(), session != null && session.failed(),
				state.mastery().getOrDefault("arpg:smithing", 0), state.mastery().getOrDefault("arpg:runecraft", 0),
				forge == null ? -1 : forge.score(), rune == null ? -1 : rune.score(),
				session == null ? List.of() : session.sequence().stream().distinct().toList(), choices, costs);
	}

	public static void start(ServerPlayerEntity player, String operation) {
		var binding = SandboxData.catalog().binding(operation);
		requireStation(player, binding);
		var stack = player.getMainHandStack();
		if (stack.getCount() != 1 || !stack.isIn(TagKey.of(RegistryKeys.ITEM, Identifier.of(binding.targetItemTag())))) {
			throw new IllegalStateException("Hold one eligible item for this finishing recipe");
		}

		if (stack.contains(CraftworkItems.PENDING)) {
			throw new IllegalStateException("Item has pending craftwork; finish or cancel it first");
		}

		var itemData = stack.getOrDefault(CraftworkItems.QUALITY, CraftedItemData.empty());
		if (itemData.stages().containsKey(operation)) {
			throw new IllegalStateException("This finishing stage is already applied");
		}

		var next = state(player).start(SandboxData.catalog(), operation, UUID.randomUUID(), player.getWorld().getTime(), SandboxData.providers());
		var material = Registries.ITEM.get(Identifier.of(binding.ingredientItem()));
		int available = 0;
		for (int i = 0; i < player.getInventory().size(); i++) {
			var candidate = player.getInventory().getStack(i);
			if (candidate != stack && candidate.isOf(material) && !candidate.contains(CraftworkItems.PENDING)) {
				available += candidate.getCount();
			}
		}
		if (available < binding.ingredientCount()) {
			throw new IllegalStateException("Missing finishing material: " + binding.ingredientItem());
		}
		int remaining = binding.ingredientCount();
		for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
			var candidate = player.getInventory().getStack(i);
			if (candidate != stack && candidate.isOf(material) && !candidate.contains(CraftworkItems.PENDING)) {
				int taken = Math.min(remaining, candidate.getCount());
				candidate.decrement(taken);
				remaining -= taken;
			}
		}
		stack.set(CraftworkItems.PENDING, next.session().id().toString());
		save(player, next);
		player.getInventory().markDirty();
	}

	public static void act(ServerPlayerEntity player, int sequence, String action) {
		var current = state(player);
		if (current.session() == null) {
			throw new IllegalStateException("No active session");
		}

		requireStation(player, SandboxData.catalog().binding(current.session().operation()));
		requireBoundItem(player, current.session().id());
		save(player, current.act(sequence, action, player.getWorld().getTime()));
	}

	public static void finish(ServerPlayerEntity player) {
		var current = state(player);
		var stack = player.getMainHandStack();
		var pending = stack.get(CraftworkItems.PENDING);
		if (pending == null) {
			throw new IllegalStateException("Hold the item with pending craftwork");
		}

		var id = UUID.fromString(pending);
		requireBoundItem(player, id);
		if (current.session() != null) {
			current.requireRecipe(SandboxData.catalog(), current.session().operation(), SandboxData.providers());
		}

		var next = current.finish(id);
		requireStation(player, SandboxData.catalog().binding(next.receipts().get(id).operation()));
		var quality = next.receipts().get(id);
		var data = stack.getOrDefault(CraftworkItems.QUALITY, CraftedItemData.empty());
		var existing = data.stages().get(quality.operation());
		if (existing != null && !existing.receipt().equals(id)) {
			throw new IllegalStateException("Item already has a different finishing receipt");
		}

		if (existing == null) {
			stack.set(CraftworkItems.QUALITY, data.add(quality));
		}

		stack.remove(CraftworkItems.PENDING);
		save(player, next);
		player.getInventory().markDirty();
	}

	public static void cancel(ServerPlayerEntity player) {
		var current = state(player);
		if (current.session() != null) {
			String token = current.session().id().toString();
			for (int i = 0; i < player.getInventory().size(); i++) {
				var stack = player.getInventory().getStack(i);
				if (token.equals(stack.get(CraftworkItems.PENDING))) {
					stack.remove(CraftworkItems.PENDING);
				}
			}

			save(player, current.cancel());
			player.getInventory().markDirty();
		} else {
			var stack = player.getMainHandStack();
			var pending = stack.get(CraftworkItems.PENDING);
			if (pending != null && !current.receipts().containsKey(UUID.fromString(pending))) {
				stack.remove(CraftworkItems.PENDING);
				player.getInventory().markDirty();
			}
		}

		// Used finishing material is not refunded. Cancelling cannot generate resources.
	}

	private static void requireBoundItem(ServerPlayerEntity player, UUID id) {
		var stack = player.getMainHandStack();
		if (stack.getCount() != 1 || !id.toString().equals(stack.get(CraftworkItems.PENDING))) {
			throw new IllegalStateException("Hold the original craftwork item");
		}
	}

	private static void requireStation(ServerPlayerEntity player, CraftBinding binding) {
		if (!player.isAlive() || player.isSpectator()) {
			throw new IllegalStateException("Player is not alive");
		}

		var pos = player.getBlockPos();
		for (var nearby : BlockPos.iterate(pos.add(-2, -1, -2), pos.add(2, 1, 2))) {
			if (player.getWorld().getBlockState(nearby).isIn(TagKey.of(RegistryKeys.BLOCK, Identifier.of(binding.stationBlockTag())))) {
				return;
			}
		}

		throw new IllegalStateException("Craftwork requires a nearby station: " + binding.stationBlockTag() + "");
	}
}
