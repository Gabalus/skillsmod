package net.puffish.skillsmod.main;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

/** Keeps addon classes out of the base installation's class loading path. */
public final class OptionalEpicMelee {
	private static java.lang.reflect.Method damageSkill;
	private static java.lang.reflect.Method enemyReady;
	private static boolean enemyFailureLogged;
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
			enemyReady = Class.forName("net.puffish.skillsmod.arpg.melee.EpicEnemyActions").getMethod("ready", net.minecraft.entity.LivingEntity.class);
		} catch (ReflectiveOperationException | LinkageError error) {
			throw new IllegalStateException("Cannot register the ARPG melee kit with Epic Fight", error);
		}
	}

	public static boolean enemyReady(net.minecraft.entity.LivingEntity entity) {
		if (!ModList.get().isLoaded("epicfight")) {
			return true;
		}
		if (enemyReady == null || enemyFailureLogged) {
			return false;
		}
		try {
			return (boolean) enemyReady.invoke(null, entity);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			enemyFailureLogged = true;
			net.puffish.skillsmod.SkillsMod.getInstance().getLogger().error("Enemy Epic Fight spell gate disabled: " + error);
			return false;
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

	public static net.puffish.skillsmod.arpg.combat.MeleeKitView view(ServerPlayerEntity player) {
		if (!ModList.get().isLoaded("epicfight")) {
			return net.puffish.skillsmod.arpg.combat.MeleeKitView.unavailable("Epic Fight is not installed.");
		}
		try {
			return (net.puffish.skillsmod.arpg.combat.MeleeKitView) Class.forName("net.puffish.skillsmod.arpg.melee.ArpgEpicMelee")
					.getMethod("view", ServerPlayerEntity.class).invoke(null, player);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			net.puffish.skillsmod.SkillsMod.getInstance().getLogger().error("ARPG melee snapshot failed: " + error);
			return net.puffish.skillsmod.arpg.combat.MeleeKitView.unavailable("The melee adapter is unavailable. Check the server log.");
		}
	}
}
