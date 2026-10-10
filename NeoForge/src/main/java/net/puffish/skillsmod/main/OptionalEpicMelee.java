package net.puffish.skillsmod.main;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

/** Keeps addon classes out of the base installation's class loading path. */
public final class OptionalEpicMelee {
	private static java.lang.reflect.Method damageSkill;
	private static boolean attributionFailureLogged;
	private OptionalEpicMelee() {
	}

	public static void registerIfLoaded(IEventBus bus) {
		if (!ModList.get().isLoaded("epicfight")) {
			return;
		}
		try {
			Class.forName("net.puffish.skillsmod.arpg.melee.ArpgEpicMelee").getMethod("register", IEventBus.class).invoke(null, bus);
			damageSkill = Class.forName("net.puffish.skillsmod.arpg.melee.ArpgMeleeAttribution").getMethod("skillId", DamageSource.class);
		} catch (ReflectiveOperationException | LinkageError error) {
			throw new IllegalStateException("Cannot register the ARPG melee kit with Epic Fight", error);
		}
	}

	public static String skillId(DamageSource source) {
		if (damageSkill == null || !(source.getAttacker() instanceof ServerPlayerEntity)) {
			return "";
		}
		try {
			return (String) damageSkill.invoke(null, source);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			if (!attributionFailureLogged) {
				attributionFailureLogged = true;
				net.puffish.skillsmod.SkillsMod.getInstance().getLogger().error("ARPG melee attribution failed: " + error);
			}
			return "";
		}
	}

	public static String invoke(ServerPlayerEntity player, String method, String attack) {
		if (!ModList.get().isLoaded("epicfight")) {
			return "Epic Fight is not installed.";
		}
		try {
			return (String) Class.forName("net.puffish.skillsmod.arpg.melee.ArpgEpicMelee")
					.getMethod(method, ServerPlayerEntity.class, String.class).invoke(null, player, attack);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			net.puffish.skillsmod.SkillsMod.getInstance().getLogger().error("ARPG melee kit request failed: " + error);
			return "ARPG melee kit is unavailable; check the server log.";
		}
	}
}
