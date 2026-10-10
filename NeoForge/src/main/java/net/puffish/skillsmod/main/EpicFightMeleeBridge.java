package net.puffish.skillsmod.main;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.arpg.character.ArpgProgression;
import net.puffish.skillsmod.arpg.combat.ArpgCombatRuntime;
import net.puffish.skillsmod.arpg.combat.CombatPillar;
import net.puffish.skillsmod.arpg.combat.MeleeActionPolicy;

import java.lang.reflect.Method;

/** Optional pinned-provider access. Never assigns skills, modifies charge or emits a second attack. */
public final class EpicFightMeleeBridge {
	private static Access access;
	private static boolean attempted;
	private static boolean warned;

	private EpicFightMeleeBridge() {
	}

	public record Status(boolean available, String weapon, String style, String skill, boolean active,
			int remainingTicks, int charges, String resource, String rejection) {
		private static Status unavailable(String reason) {
			return new Status(false, "", "", "", false, 0, 0, "", reason);
		}
	}

	public record Result(boolean accepted, String message) {
	}

	public static Status status(ServerPlayerEntity player) {
		if (!EpicFightStaminaBridge.ownsMartialCombat()) {
			return Status.unavailable("Epic Fight is not installed.");
		}
		try {
			var a = resolve();
			if (a == null) {
				return Status.unavailable("Epic Fight melee adapter is unavailable; check the server log.");
			}
			var context = a.context(player);
			if (context == null) {
				return Status.unavailable("Epic Fight player patch is not ready yet.");
			}
			return a.status(context);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			warn(error);
			return Status.unavailable("Epic Fight melee inspection failed; check the server log.");
		}
	}

	public static Result use(ServerPlayerEntity player, MeleeActionPolicy.Action action) {
		if (!EpicFightStaminaBridge.ownsMartialCombat()) {
			return new Result(false, "Epic Fight is not installed.");
		}
		try {
			var a = resolve();
			var context = a == null ? null : a.context(player);
			if (context == null) {
				return new Result(false, "Epic Fight melee adapter or player patch is not ready.");
			}
			String rejection = MeleeActionPolicy.rejection(context.input(), action);
			if (!rejection.isEmpty()) {
				return new Result(false, rejection);
			}
			// Use the same server request validation as the provider's native skill packet.
			// Empty arguments are intentional: these two non-held skills need no client aim/charge payload.
			boolean accepted = (Boolean) a.cast.invoke(context.container(), context.patch(), new NbtCompound());
			if (!accepted) {
				return new Result(false, "Epic Fight rejected the skill: check charge, resources and combat state.");
			}
			EpicFightStaminaBridge.sync(player);
			boolean active = (Boolean) a.active.invoke(context.container());
			return new Result(true, "epicfight:liechtenauer".equals(context.input().skill())
					? active ? "Liechtenauer defensive stance entered." : "Returned to the standard longsword stance."
					: "Sweeping Edge accepted by Epic Fight.");
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			warn(error);
			return new Result(false, "Epic Fight melee request failed; check the server log.");
		}
	}

	private static Access resolve() throws ReflectiveOperationException {
		if (!attempted) {
			attempted = true;
			access = new Access();
		}
		return access;
	}

	private static void warn(Throwable error) {
		if (!warned) {
			warned = true;
			SkillsMod.getInstance().getLogger().error("Epic Fight melee adapter unavailable: " + error);
		}
	}

	private record Context(Object patch, Object capability, Object container, Object skill, MeleeActionPolicy.Input input) {
	}

	private static final class Access {
		private final Class<?> serverPatch = Class.forName("yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch");
		private final Class<?> playerPatch = Class.forName("yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch");
		private final Class<?> livingPatch = Class.forName("yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch");
		private final Class<?> capabilities = Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities");
		private final Class<?> containerClass = Class.forName("yesman.epicfight.skill.SkillContainer");
		private final Class<?> skillClass = Class.forName("yesman.epicfight.skill.Skill");
		private final Class<?> itemClass = Class.forName("yesman.epicfight.world.capabilities.item.CapabilityItem");
		private final Class<?> stateClass = Class.forName("yesman.epicfight.api.animation.types.EntityState");
		private final Object innateSlot = Class.forName("yesman.epicfight.skill.SkillSlots").getField("WEAPON_INNATE").get(null);
		private final Method patch = capabilities.getMethod("getEntityPatch", Entity.class, Class.class);
		private final Method item = capabilities.getMethod("getItemStackCapability", ItemStack.class);
		private final Method getContainer = playerPatch.getMethod("getSkill", Class.forName("yesman.epicfight.skill.SkillSlot"));
		private final Method getSkill = containerClass.getMethod("getSkill");
		private final Method getInnate = itemClass.getMethod("getInnateSkill", playerPatch, ItemStack.class);
		private final Method registryName = skillClass.getMethod("getRegistryName");
		private final Method getState = livingPatch.getMethod("getEntityState");
		private final Method executable = stateClass.getMethod("canUseSkill");
		private final Method battle = playerPatch.getMethod("isEpicFightMode");
		private final Method holding = playerPatch.getMethod("isHoldingAny");
		private final Method primaryHand = livingPatch.getMethod("getPrimaryHand");
		private final Method disabled = containerClass.getMethod("isDisabled");
		private final Method active = containerClass.getMethod("isActivated");
		private final Method remaining = containerClass.getMethod("getRemainDuration");
		private final Method charges = containerClass.getMethod("getStack");
		private final Method resource = skillClass.getMethod("getResourceType");
		private final Method category = itemClass.getMethod("getWeaponCategory");
		private final Method style = itemClass.getMethod("getStyle", livingPatch);
		private final Method cast = containerClass.getMethod("requestCasting", serverPatch, NbtCompound.class);

		private Access() throws ReflectiveOperationException {
		}

		private Context context(ServerPlayerEntity player) throws ReflectiveOperationException {
			Object p = patch.invoke(null, player, serverPatch);
			if (p == null) {
				return null;
			}
			var stack = player.getStackInHand((Hand) primaryHand.invoke(p));
			Object capability = item.invoke(null, stack);
			Object container = getContainer.invoke(p, innateSlot);
			Object skill = container == null ? null : getSkill.invoke(container);
			String id = skill == null ? "" : String.valueOf(registryName.invoke(skill));
			boolean matches = !stack.isEmpty() && capability != null && skill != null
					&& getInnate.invoke(capability, p, stack) == skill;
			var input = new MeleeActionPolicy.Input(player.isAlive() && !player.isCreative() && !player.isSpectator()
					&& !SkillsMod.getInstance().getPlatform().isFakePlayer(player),
					!ArpgProgression.character(player).primary().isEmpty(),
					ArpgCombatRuntime.state(player).pillar() == CombatPillar.MARTIAL,
					(Boolean) battle.invoke(p), (Boolean) holding.invoke(p),
					(Boolean) executable.invoke(getState.invoke(p)), container == null || (Boolean) disabled.invoke(container), matches, id);
			return new Context(p, capability, container, skill, input);
		}

		private Status status(Context c) throws ReflectiveOperationException {
			return new Status(true, c.capability() == null ? "" : String.valueOf(category.invoke(c.capability())),
					c.capability() == null ? "" : String.valueOf(style.invoke(c.capability(), c.patch())), c.input().skill(),
					c.container() != null && (Boolean) active.invoke(c.container()),
					c.container() == null ? 0 : ((Number) remaining.invoke(c.container())).intValue(),
					c.container() == null ? 0 : ((Number) charges.invoke(c.container())).intValue(),
					c.skill() == null ? "" : String.valueOf(resource.invoke(c.skill())),
					MeleeActionPolicy.rejection(c.input(), MeleeActionPolicy.Action.INNATE));
		}
	}
}
