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
	private static boolean attempted;
	private static boolean warned;

	private ApotheosisProgressionCompat() {
	}

	public static int currentTier(PlayerEntity player) {
		if (!ArpgProviderRegistry.loaded("apotheosis") || player == null) {
			return 0;
		}
		try {
			if (!attempted) {
				attempted = true;
				Class<?> type = Class.forName("dev.shadowsoffire.apotheosis.tiers.WorldTier");
				for (Method method : type.getMethods()) {
					if (method.getName().equals("getTier") && Modifier.isStatic(method.getModifiers())
							&& method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(player)) {
						tierGetter = method;
						break;
					}
				}
				if (tierGetter == null) {
					throw new NoSuchMethodException("Apotheosis WorldTier#getTier");
				}
			}
			if (tierGetter != null && tierGetter.invoke(null, player) instanceof Enum<?> tier) {
				return Math.min(4, tier.ordinal());
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			if (!warned) {
				warned = true;
				SkillsMod.getInstance().getLogger().warn("Apotheosis tier adapter unavailable; using tier 0: " + error.getClass().getSimpleName());
			}
		}
		return 0;
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
