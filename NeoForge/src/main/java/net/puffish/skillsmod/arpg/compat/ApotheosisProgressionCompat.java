package net.puffish.skillsmod.arpg.compat;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.puffish.skillsmod.SkillsMod;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Reads Apotheosis 1.21 mob markers and captures the spawn-context tier without changing it. */
public final class ApotheosisProgressionCompat {
	public static final String SPAWN_TIER = "puffish_skills.arpg_apoth_spawn_tier";
	private static Method tierGetter;
	private static Method tierSetter;
	private static Method unlockGetter;
	private static Object[] tiers;
	private static boolean attempted;
	private static boolean warned;

	private ApotheosisProgressionCompat() {
	}

	/** Resolves all tier operations together so menus cannot offer an unusable action. */
	public static boolean available(PlayerEntity player) {
		if (!ArpgProviderRegistry.loaded("apotheosis") || player == null) {
			return false;
		}
		if (!attempted) {
			attempted = true;
			try {
				Class<?> type = Class.forName("dev.shadowsoffire.apotheosis.tiers.WorldTier");
				tiers = type.getEnumConstants();
				String[] expected = {"HAVEN", "FRONTIER", "ASCENT", "SUMMIT", "PINNACLE"};
				if (tiers == null || tiers.length != expected.length) {
					throw new IllegalStateException("Unsupported world tiers");
				}
				for (int index = 0; index < expected.length; index++) {
					if (!((Enum<?>) tiers[index]).name().equals(expected[index])) {
						throw new IllegalStateException("Unsupported world tier order");
					}
				}
				for (Method method : type.getMethods()) {
					if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() < 1
							|| !method.getParameterTypes()[0].isInstance(player)) {
						continue;
					}
					if (method.getName().equals("getTier") && method.getParameterCount() == 1 && method.getReturnType() == type) {
						tierGetter = method;
					} else if (method.getParameterCount() == 2 && method.getParameterTypes()[1] == type) {
						if (method.getName().equals("setTier") && method.getReturnType() == void.class) {
							tierSetter = method;
						} else if (method.getName().equals("isUnlocked") && method.getReturnType() == boolean.class) {
							unlockGetter = method;
						}
					}
				}
				if (tierGetter == null || tierSetter == null || unlockGetter == null) {
					throw new NoSuchMethodException("Apotheosis world tier operations");
				}
			} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
				tierGetter = null;
				tierSetter = null;
				unlockGetter = null;
				warn(error);
			}
		}
		return tierGetter != null && tierSetter != null && unlockGetter != null;
	}

	public static int currentTier(PlayerEntity player) {
		if (!available(player)) {
			return 0;
		}
		try {
			return ((Enum<?>) tierGetter.invoke(null, player)).ordinal();
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			warn(error);
			return 0;
		}
	}

	public static boolean unlocked(PlayerEntity player, int tier) {
		if (tier < 0 || tier > 4 || !available(player)) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(unlockGetter.invoke(null, player, tiers[tier]));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			warn(error);
			return false;
		}
	}

	/** Uses native synchronization and tier augments; never grants unlock advancements. */
	public static void activate(PlayerEntity player, int tier) {
		if (!available(player)) {
			throw new IllegalStateException("Apotheosis tier controls are unavailable");
		}
		if (!unlocked(player, tier)) {
			throw new IllegalStateException("Complete this tier's Apotheosis unlock requirements first (Ctrl+T)");
		}
		try {
			tierSetter.invoke(null, player, tiers[tier]);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			warn(error);
			throw new IllegalStateException("Apotheosis could not activate this tier; check the server log");
		}
	}

	public static String tierName(int tier) {
		return switch (tier) {
			case 1 -> "Frontier";
			case 2 -> "Ascent";
			case 3 -> "Summit";
			case 4 -> "Pinnacle";
			default -> "Haven";
		};
	}

	private static void warn(Throwable error) {
		if (!warned) {
			warned = true;
			SkillsMod.getInstance().getLogger().warn("Apotheosis tier adapter failed; controls fail closed and XP reads use tier 0: " + error.getClass().getSimpleName());
		}
	}

	public static Profile profile(LivingEntity entity) {
		if (!ArpgProviderRegistry.loaded("apotheosis")) {
			return new Profile(0, false, false);
		}
		var data = entity.getPersistentData();
		boolean invader = data.getBoolean("apoth.boss");
		boolean elite = data.getBoolean("apoth.miniboss");
		int tier = Math.max(0, Math.min(4, data.getInt(SPAWN_TIER)));
		return new Profile(tier, elite, invader);
	}

	public record Profile(int tier, boolean elite, boolean invader) {
	}
}
