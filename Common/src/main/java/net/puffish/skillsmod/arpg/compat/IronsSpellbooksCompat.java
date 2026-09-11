package net.puffish.skillsmod.arpg.compat;

import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.puffish.skillsmod.arpg.stat.ArpgModifierOperation;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatModifier;

import java.util.Map;

public final class IronsSpellbooksCompat {
	private static final String MOD_ID = "irons_spellbooks";
	private static final Map<ArpgStat, String> ATTRIBUTE_PATHS = Map.ofEntries(
			Map.entry(ArpgStat.MAXIMUM_MANA, "max_mana"),
			Map.entry(ArpgStat.CAST_SPEED, "cast_time_reduction"),
			Map.entry(ArpgStat.FIRE_DAMAGE, "fire_spell_power"),
			Map.entry(ArpgStat.COLD_DAMAGE, "ice_spell_power"),
			Map.entry(ArpgStat.LIGHTNING_DAMAGE, "lightning_spell_power"),
			Map.entry(ArpgStat.HOLY_DAMAGE, "holy_spell_power"),
			Map.entry(ArpgStat.ENDER_DAMAGE, "ender_spell_power"),
			Map.entry(ArpgStat.BLOOD_DAMAGE, "blood_spell_power"),
			Map.entry(ArpgStat.SPELL_DAMAGE, "spell_power"),
			Map.entry(ArpgStat.COOLDOWN_RECOVERY, "cooldown_reduction")
	);

	private IronsSpellbooksCompat() {
	}

	public static void applyModifier(
			ServerPlayerEntity player,
			Identifier sourceId,
			ArpgStatModifier modifier
	) {
		var attributePath = ATTRIBUTE_PATHS.get(modifier.stat());
		if (attributePath == null || !canProject(modifier)) {
			return;
		}

		var attributeId = Identifier.of(MOD_ID, attributePath);
		var attribute = Registries.ATTRIBUTE.get(attributeId);
		if (attribute == null) {
			return;
		}

		var instance = player.getAttributeInstance(Registries.ATTRIBUTE.getEntry(attribute));
		if (instance == null) {
			return;
		}

		instance.removeModifier(sourceId);
		instance.addTemporaryModifier(new EntityAttributeModifier(
				sourceId,
				projectedValue(modifier),
				projectedOperation(modifier.operation())
		));
	}

	public static void removeModifier(
			ServerPlayerEntity player,
			Identifier sourceId,
			ArpgStat stat
	) {
		var attributePath = ATTRIBUTE_PATHS.get(stat);
		if (attributePath == null) {
			return;
		}

		var attribute = Registries.ATTRIBUTE.get(Identifier.of(MOD_ID, attributePath));
		if (attribute == null) {
			return;
		}

		var instance = player.getAttributeInstance(Registries.ATTRIBUTE.getEntry(attribute));
		if (instance != null) {
			instance.removeModifier(sourceId);
		}
	}

	static boolean canProject(ArpgStatModifier modifier) {
		return modifier.operation() != ArpgModifierOperation.FLAT
				|| modifier.stat() == ArpgStat.MAXIMUM_MANA;
	}

	static EntityAttributeModifier.Operation projectedOperation(ArpgModifierOperation operation) {
		return switch (operation) {
			case FLAT -> EntityAttributeModifier.Operation.ADD_VALUE;
			case INCREASED, REDUCED -> EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE;
			case MORE, LESS -> EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
			default -> throw new IllegalStateException("Unsupported ARPG modifier operation: " + operation);
		};
	}

	static double projectedValue(ArpgStatModifier modifier) {
		return switch (modifier.operation()) {
			case REDUCED, LESS -> -modifier.value();
			case FLAT, INCREASED, MORE -> modifier.value();
			default -> throw new IllegalStateException(
					"Unsupported ARPG modifier operation: " + modifier.operation()
			);
		};
	}
}
