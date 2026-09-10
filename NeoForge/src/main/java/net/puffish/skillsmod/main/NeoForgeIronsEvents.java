package net.puffish.skillsmod.main;

import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.arpg.character.ArpgProgression;
import net.puffish.skillsmod.arpg.data.ArpgData;
import net.puffish.skillsmod.arpg.rule.ArpgResourceSemantics;
import net.puffish.skillsmod.arpg.rule.ArpgRuleEngine;
import net.puffish.skillsmod.arpg.rule.ArpgRuleRuntime;
import net.puffish.skillsmod.arpg.skill.ArpgSkillAccess;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Optional Iron's event integration implemented without Iron's classes in method signatures. */
public final class NeoForgeIronsEvents {
	private static final String MOD_ID = "irons_spellbooks";
	private static final String PRE_CAST_EVENT = "io.redspace.ironsspellbooks.api.events.SpellPreCastEvent";
	private static final String ON_CAST_EVENT = "io.redspace.ironsspellbooks.api.events.SpellOnCastEvent";
	private static final String MAGIC_DATA = "io.redspace.ironsspellbooks.api.magic.MagicData";
	private static final String SYNC_MANA_PACKET = "io.redspace.ironsspellbooks.network.SyncManaPacket";
	private static boolean registered;
	private static boolean invocationFailureLogged;

	private NeoForgeIronsEvents() {
	}

	public static synchronized void registerIfLoaded(IEventBus eventBus) {
		if (registered || !ModList.get().isLoaded(MOD_ID)) {
			return;
		}
		try {
			var preCast = EventAccess.create(PRE_CAST_EVENT);
			var onCast = EventAccess.create(ON_CAST_EVENT);
			var manaAccess = ManaAccess.create();
			register(eventBus, preCast.type(), event -> onPreCast(event, preCast));
			register(eventBus, onCast.type(), event -> onCast(event, onCast));
			ArpgRuleRuntime.configureExternalRuntime(manaAccess::fraction, manaAccess::executeTrigger);
			registered = true;
		} catch (ReflectiveOperationException | RuntimeException exception) {
			SkillsMod.getInstance().getLogger().error(
					"Failed to register Iron's ARPG event bridge: " + exception.getMessage()
			);
		}
	}

	private static void onPreCast(Event event, EventAccess access) {
		try {
			var player = access.player(event);
			if (player == null) {
				return;
			}
			var skillId = access.skillId(event);
			var result = ArpgSkillAccess.check(player, skillId, "irons");
			if (!result.allowed() && event instanceof ICancellableEvent cancellable) {
				cancellable.setCanceled(true);
				player.sendMessage(Text.literal(result.message()), true);
			}
		} catch (ReflectiveOperationException | RuntimeException exception) {
			logInvocationFailure(exception);
		}
	}

	private static void onCast(Event event, EventAccess access) {
		try {
			var player = access.player(event);
			if (player == null) {
				return;
			}
			var skillId = access.skillId(event);
			if (!ArpgSkillAccess.check(player, skillId, "irons").allowed()) {
				return;
			}

			var skill = ArpgData.content().skills().get(skillId);
			if (skill == null) {
				return;
			}
			Set<String> tags = skill.tags().stream()
					.map(tag -> tag.name().toLowerCase(Locale.ROOT))
					.collect(Collectors.toUnmodifiableSet());
			ArpgRuleRuntime.fireSupportedTriggers(
					player,
					null,
					ArpgRuleEngine.Event.CAST,
					skillId,
					tags
			);

			var character = ArpgProgression.character(player);
			if (character.specializations().containsKey(skillId)) {
				int before = character.specializationPoints(skillId);
				character.gainSkillExperience(skillId, 10);
				if (character.specializationPoints(skillId) != before) {
					ArpgProgression.sync(player);
				}
			}
		} catch (ReflectiveOperationException | RuntimeException exception) {
			logInvocationFailure(exception);
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void register(IEventBus eventBus, Class<? extends Event> eventType, Consumer<Event> listener) {
		eventBus.addListener(EventPriority.NORMAL, (Class) eventType, (Consumer) listener);
	}

	private static void logInvocationFailure(Exception exception) {
		if (!invocationFailureLogged) {
			invocationFailureLogged = true;
			SkillsMod.getInstance().getLogger().error(
					"Iron's ARPG event bridge invocation failed: " + exception.getMessage()
			);
		}
	}

	private record EventAccess(
			Class<? extends Event> type,
			Method getEntity,
			Method getSpellId
	) {
		private static EventAccess create(String className) throws ReflectiveOperationException {
			var raw = Class.forName(className);
			if (!Event.class.isAssignableFrom(raw)) {
				throw new IllegalArgumentException(className + " is not a NeoForge event");
			}
			@SuppressWarnings("unchecked")
			var eventType = (Class<? extends Event>) raw;
			return new EventAccess(eventType, raw.getMethod("getEntity"), raw.getMethod("getSpellId"));
		}

		private ServerPlayerEntity player(Event event) throws ReflectiveOperationException {
			var entity = getEntity.invoke(event);
			return entity instanceof ServerPlayerEntity player ? player : null;
		}

		private String skillId(Event event) throws ReflectiveOperationException {
			var value = getSpellId.invoke(event);
			if (value instanceof String string && !string.isBlank()) {
				return string;
			}
			throw new IllegalStateException("Iron's cast event returned an invalid spell ID");
		}
	}

	private record ManaAccess(
			Method getPlayerMagicData,
			Method getMana,
			Method setMana,
			Constructor<?> syncManaPacket
	) {
		private static ManaAccess create() throws ReflectiveOperationException {
			var magicData = Class.forName(MAGIC_DATA);
			var syncPacket = Class.forName(SYNC_MANA_PACKET);
			return new ManaAccess(
					findMethod(magicData, "getPlayerMagicData", 1),
					findMethod(magicData, "getMana", 0),
					findMethod(magicData, "setMana", 1),
					findConstructor(syncPacket, magicData)
			);
		}

		private double fraction(ServerPlayerEntity player) {
			try {
				var data = data(player);
				double maximum = maxMana(player);
				double current = mana(data);
				if (!Double.isFinite(maximum) || maximum <= 0.0 || !Double.isFinite(current)) {
					return Double.NaN;
				}
				return Math.max(0.0, Math.min(1.0, current / maximum));
			} catch (ReflectiveOperationException | RuntimeException exception) {
				logInvocationFailure(exception);
				return Double.NaN;
			}
		}

		private boolean executeTrigger(ServerPlayerEntity player, ArpgRuleEngine.Trigger trigger) {
			if (trigger.action() != ArpgRuleEngine.Action.MANA) {
				return false;
			}
			try {
				var data = data(player);
				double maximum = maxMana(player);
				double current = mana(data);
				double target = ArpgResourceSemantics.restoreFromMaximum(current, maximum, trigger.value());
				if (!Double.isFinite(target) || target <= current + 0.0001) {
					return false;
				}
				setMana.invoke(data, (float) target);
				double actual = mana(data);
				if (!Double.isFinite(actual) || actual <= current + 0.0001) {
					return false;
				}
				try {
					sync(player, data);
				} catch (ReflectiveOperationException | RuntimeException exception) {
					logInvocationFailure(exception);
				}
				return true;
			} catch (ReflectiveOperationException | RuntimeException exception) {
				logInvocationFailure(exception);
				return false;
			}
		}

		private Object data(ServerPlayerEntity player) throws ReflectiveOperationException {
			var value = getPlayerMagicData.invoke(null, player);
			if (value == null) {
				throw new IllegalStateException("Iron's returned no MagicData for a server player");
			}
			return value;
		}

		private double mana(Object data) throws ReflectiveOperationException {
			var value = getMana.invoke(data);
			if (value instanceof Number number) {
				return number.doubleValue();
			}
			throw new IllegalStateException("Iron's MagicData returned a non-numeric mana value");
		}

		private double maxMana(ServerPlayerEntity player) {
			var attribute = Registries.ATTRIBUTE.get(Identifier.of(MOD_ID, "max_mana"));
			if (attribute == null) {
				return Double.NaN;
			}
			var instance = player.getAttributeInstance(Registries.ATTRIBUTE.getEntry(attribute));
			return instance == null ? Double.NaN : instance.getValue();
		}

		private void sync(ServerPlayerEntity player, Object data) throws ReflectiveOperationException {
			var packet = syncManaPacket.newInstance(data);
			if (!(packet instanceof CustomPayload payload)) {
				throw new IllegalStateException("Iron's SyncManaPacket is not a custom payload");
			}
			player.networkHandler.send(new CustomPayloadS2CPacket(payload));
		}

		private static Method findMethod(Class<?> type, String name, int parameters) throws NoSuchMethodException {
			for (var method : type.getMethods()) {
				if (method.getName().equals(name) && method.getParameterCount() == parameters) {
					return method;
				}
			}
			throw new NoSuchMethodException(type.getName() + "." + name);
		}

		private static Constructor<?> findConstructor(Class<?> type, Class<?> argument) throws NoSuchMethodException {
			for (var constructor : type.getConstructors()) {
				var parameters = constructor.getParameterTypes();
				if (parameters.length == 1 && parameters[0].isAssignableFrom(argument)) {
					return constructor;
				}
			}
			throw new NoSuchMethodException(type.getName() + " constructor for " + argument.getName());
		}
	}
}
