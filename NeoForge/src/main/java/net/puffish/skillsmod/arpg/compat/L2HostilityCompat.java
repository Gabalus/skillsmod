package net.puffish.skillsmod.arpg.compat;

import net.minecraft.entity.LivingEntity;
import net.puffish.skillsmod.SkillsMod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Optional, reflection-only bridge to L2 Hostility's 1.21 mob trait capability. */
public final class L2HostilityCompat {
	private static volatile Access access;
	private static volatile boolean resolutionAttempted;
	private static boolean warned;

	private L2HostilityCompat() {
	}

	public static boolean available() {
		return ArpgProviderRegistry.loaded("l2hostility") && resolveAccess() != null;
	}

	public static Profile profile(LivingEntity entity) {
		if (entity == null || !ArpgProviderRegistry.loaded("l2hostility")) {
			return Profile.EMPTY;
		}

		var resolved = resolveAccess();
		if (resolved == null) {
			return Profile.EMPTY;
		}

		try {
			Object optional = resolved.getExisting().invoke(resolved.capabilityType(), entity);
			if (!(optional instanceof Optional<?> value) || value.isEmpty()) {
				return Profile.EMPTY;
			}
			Object cap = value.get();
			int level = ((Number) resolved.getLevel().invoke(cap)).intValue();
			boolean ineligible = resolved.summoned().getBoolean(cap) || resolved.minion().getBoolean(cap)
					|| resolved.noDrop().getBoolean(cap)
					|| (resolved.copied() != null && resolved.copied().getBoolean(cap));
			Object rawTraits = resolved.traits().get(cap);
			if (!(rawTraits instanceof Map<?, ?> traits)) {
				return new Profile(level, Map.of(), ineligible);
			}

			var normalized = new HashMap<String, Integer>();
			for (var entry : traits.entrySet()) {
				if (!(entry.getValue() instanceof Number rank)) {
					continue;
				}
				String id = traitId(entry.getKey());
				if (!id.isBlank() && rank.intValue() > 0) {
					normalized.put(id, rank.intValue());
				}
			}
			return new Profile(level, Map.copyOf(normalized), ineligible);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			warn(error);
			return Profile.EMPTY;
		}
	}

	public static Set<String> tags(LivingEntity entity) {
		var profile = profile(entity);
		if (!profile.elite()) {
			return Set.of();
		}

		var tags = new HashSet<String>();
		tags.add("elite");
		tags.add("l2hostility");
		tags.add("l2_level:" + profile.level());
		for (var entry : profile.traits().entrySet()) {
			tags.add("l2_trait:" + entry.getKey());
			tags.add("l2_trait_rank:" + entry.getKey() + ":" + entry.getValue());
		}
		return Set.copyOf(tags);
	}

	private static Access resolveAccess() {
		if (resolutionAttempted) {
			return access;
		}
		synchronized (L2HostilityCompat.class) {
			if (resolutionAttempted) {
				return access;
			}
			resolutionAttempted = true;
			try {
				Class<?> misc = Class.forName("dev.xkmc.l2hostility.init.registrate.LHMiscs");
				Object mobEntry = misc.getField("MOB").get(null);
				// Invoke through the public interface, not the potentially non-public supplier class.
				Method type = Class.forName("dev.xkmc.l2core.init.reg.simple.AttVal").getMethod("type");
				Object capabilityType = type.invoke(mobEntry);
				Method getExisting = findOneArg(capabilityType.getClass(), "getExisting");
				Class<?> capClass = Class.forName("dev.xkmc.l2hostility.content.capability.mob.MobTraitCap");
				Method getLevel = capClass.getMethod("getLevel");
				Field traits = capClass.getField("traits");
				access = new Access(capabilityType, getExisting, getLevel, traits,
						capClass.getField("summoned"), capClass.getField("minion"), capClass.getField("noDrop"), optionalField(capClass, "copied"));
			} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
				access = null;
				warn(error);
			}
			return access;
		}
	}

	private static Method findOneArg(Class<?> type, String name) throws NoSuchMethodException {
		for (var method : type.getMethods()) {
			if (method.getName().equals(name) && method.getParameterCount() == 1) {
				return method;
			}
		}
		throw new NoSuchMethodException(type.getName() + "#" + name);
	}

	private static Field optionalField(Class<?> type, String name) {
		try {
			return type.getField(name);
		} catch (NoSuchFieldException ignored) {
			return null;
		}
	}

	private static String traitId(Object trait) {
		if (trait == null) {
			return "";
		}
		try {
			Object value = trait.getClass().getMethod("getRegistryName").invoke(trait);
			return value == null ? "" : value.toString();
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return trait.toString();
		}
	}

	private static void warn(Throwable error) {
		if (!warned) {
			warned = true;
			SkillsMod.getInstance().getLogger().warn("L2 Hostility mob adapter unavailable; using baseline threat: " + error.getClass().getSimpleName());
		}
	}

	private record Access(Object capabilityType, Method getExisting, Method getLevel, Field traits,
			Field summoned, Field minion, Field noDrop, Field copied) {
	}

	public record Profile(int level, Map<String, Integer> traits, boolean ineligibleSpawn) {
		private static final Profile EMPTY = new Profile(0, Map.of());

		public Profile(int level, Map<String, Integer> traits) {
			this(level, traits, false);
		}

		public Profile {
			traits = traits == null ? Map.of() : Map.copyOf(traits);
		}

		public boolean elite() {
			return level > 0 || !traits.isEmpty();
		}
	}
}
