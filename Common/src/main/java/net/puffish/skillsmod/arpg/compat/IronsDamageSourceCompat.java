package net.puffish.skillsmod.arpg.compat;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Iron's damage-type discovery without a compile-time dependency on Iron's classes. */
public final class IronsDamageSourceCompat {
	private static final String MOD_ID = "irons_spellbooks";
	private static final RegistryKey<DamageType> FIRE_MAGIC = key("fire_magic");
	private static final RegistryKey<DamageType> ICE_MAGIC = key("ice_magic");
	private static final RegistryKey<DamageType> LIGHTNING_MAGIC = key("lightning_magic");
	private static final RegistryKey<DamageType> HOLY_MAGIC = key("holy_magic");
	private static final RegistryKey<DamageType> ENDER_MAGIC = key("ender_magic");
	private static final RegistryKey<DamageType> BLOOD_MAGIC = key("blood_magic");
	private static final RegistryKey<DamageType> NATURE_MAGIC = key("nature_magic");
	private static final RegistryKey<DamageType> EVOCATION_MAGIC = key("evocation_magic");
	private static final RegistryKey<DamageType> ELDRITCH_MAGIC = key("eldritch_magic");

	private IronsDamageSourceCompat() {
	}

	public static Optional<net.puffish.skillsmod.arpg.combat.DamageType> school(DamageSource source) {
		if (source.isOf(FIRE_MAGIC)) {
			return Optional.of(net.puffish.skillsmod.arpg.combat.DamageType.FIRE);
		}
		if (source.isOf(ICE_MAGIC)) {
			return Optional.of(net.puffish.skillsmod.arpg.combat.DamageType.COLD);
		}
		if (source.isOf(LIGHTNING_MAGIC)) {
			return Optional.of(net.puffish.skillsmod.arpg.combat.DamageType.LIGHTNING);
		}
		if (source.isOf(HOLY_MAGIC)) {
			return Optional.of(net.puffish.skillsmod.arpg.combat.DamageType.HOLY);
		}
		if (source.isOf(ENDER_MAGIC)) {
			return Optional.of(net.puffish.skillsmod.arpg.combat.DamageType.ENDER);
		}
		if (source.isOf(BLOOD_MAGIC)) {
			return Optional.of(net.puffish.skillsmod.arpg.combat.DamageType.BLOOD);
		}
		if (source.isOf(NATURE_MAGIC)) {
			return Optional.of(net.puffish.skillsmod.arpg.combat.DamageType.NATURE);
		}
		return Optional.empty();
	}

	public static Set<String> tags(DamageSource source) {
		var school = school(source);
		if (school.isPresent()) {
			return Set.of("spell", "hit", school.orElseThrow().name().toLowerCase(Locale.ROOT));
		}
		if (source.isOf(EVOCATION_MAGIC) || source.isOf(ELDRITCH_MAGIC)) {
			return Set.of("spell", "hit");
		}
		return Set.of();
	}

	private static RegistryKey<DamageType> key(String path) {
		return RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(MOD_ID, path));
	}
}
