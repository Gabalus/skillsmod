package net.puffish.skillsmod.main;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

/** Isolates provider classes from installations without Iron's Spells. */
public final class OptionalIronsSpells {
	private static java.lang.reflect.Method enemyAvailable;
	private static java.lang.reflect.Method enemyRelease;
	private static boolean enemyFailureLogged;
	private OptionalIronsSpells() {
	}

	public static void registerIfLoaded(IEventBus bus) {
		if (!ModList.get().isLoaded("irons_spellbooks")) {
			return;
		}
		try {
			Class.forName("net.puffish.skillsmod.arpg.spell.ArpgIronsSpells")
					.getMethod("register", IEventBus.class).invoke(null, bus);
			var enemies = Class.forName("net.puffish.skillsmod.arpg.spell.EnemyIronsSpells");
			enemyAvailable = enemies.getMethod("available");
			enemyRelease = enemies.getMethod("release", net.minecraft.entity.LivingEntity.class);
		} catch (ReflectiveOperationException | LinkageError exception) {
			throw new IllegalStateException("Cannot register ARPG spells against the installed Iron's API", exception);
		}
	}

	public static boolean enemyAvailable() {
		return enemyCall(enemyAvailable);
	}

	public static boolean releaseEnemySpell(net.minecraft.entity.LivingEntity caster) {
		return enemyCall(enemyRelease, caster);
	}

	private static boolean enemyCall(java.lang.reflect.Method method, Object... arguments) {
		if (method == null || enemyFailureLogged) {
			return false;
		}
		try {
			return (boolean) method.invoke(null, arguments);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			enemyFailureLogged = true;
			net.puffish.skillsmod.SkillsMod.getInstance().getLogger().error("Enemy Iron's spell adapter disabled: " + error);
			return false;
		}
	}
}
