package net.puffish.skillsmod.arpg.item;

import com.google.gson.Gson;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.arpg.data.ArpgData;
import net.puffish.skillsmod.arpg.stat.ArpgModifierOperation;
import net.puffish.skillsmod.arpg.stat.ArpgStat;
import net.puffish.skillsmod.arpg.stat.ArpgStatModifier;
import net.puffish.skillsmod.server.setup.ServerRegistrar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ArpgItems {
	private static final Gson GSON = new Gson();
	public static final ComponentType<ArpgItemData> DATA = ComponentType.<ArpgItemData>builder().codec(Codec.STRING.xmap(ArpgItems::decode, ArpgItems::encode)).build();
	public static final Map<String, String> RUNE_MATERIALS = Map.of("ember", "blaze_powder", "frost", "snowball", "storm", "copper_ingot",
			"vita", "glistering_melon_slice", "arcane", "lapis_lazuli", "gale", "feather");

	private ArpgItems() {
	}

	public static void register(ServerRegistrar registrar) {
		registrar.register(Registries.DATA_COMPONENT_TYPE, SkillsMod.createIdentifier("arpg_item"), DATA);
	}

	public static String encode(ArpgItemData data) {
		return GSON.toJson(data);
	}

	public static ArpgItemData decode(String json) {
		if (json.length() > 16_384) {
			throw new IllegalArgumentException("ARPG item data exceeds size limit");
		}
		return GSON.fromJson(json, ArpgItemData.class);
	}

	public static ItemStack create(ArpgItemData data) {
		var base = ArpgData.content().bases().get(data.base());
		if (base == null) {
			throw new IllegalArgumentException("Unknown ARPG item base: " + data.base());
		}
		var item = Registries.ITEM.get(Identifier.of(base.item()));
		var stack = new ItemStack(item);
		apply(stack, data);
		return stack;
	}

	public static void apply(ItemStack stack, ArpgItemData data) {
		stack.set(DATA, data);
		var content = ArpgData.content();
		var base = content.bases().get(data.base());
		var unique = content.uniques().get(data.unique());
		var color = data.rarity().equals("unique") ? Formatting.GOLD : data.rarity().equals("rare") ? Formatting.YELLOW
				: data.rarity().equals("magic") ? Formatting.AQUA : Formatting.WHITE;
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(unique == null ? base.title() : unique.title()).formatted(color));
		var lore = new ArrayList<Text>();
		lore.add(Text.literal("Item Level " + data.itemLevel() + " | " + base.slot()).formatted(Formatting.GRAY));
		for (var m : base.modifiers()) {
			lore.add(Text.literal(describe(m) + " (implicit)").formatted(Formatting.DARK_AQUA));
		}
		for (var roll : data.affixes()) {
			var affix = content.affixes().get(roll.affix());
			if (affix != null) {
				lore.add(Text.literal("T" + roll.tier() + " " + affix.title() + ": " + describe(new ArpgStatModifier(affix.stat(), affix.operation(), roll.value()))).formatted(Formatting.BLUE));
			}
		}
		if (unique != null) {
			for (var rule : unique.rules()) {
				lore.add(Text.literal(content.rules().get(rule).description()).formatted(Formatting.GOLD));
			}
		}
		lore.add(Text.literal("Runes: " + String.join(" > ", data.runes()) + " | Forging Potential " + data.forgingPotential()).formatted(Formatting.GRAY));
		if (data.corrupted()) {
			lore.add(Text.literal("Corrupted").formatted(Formatting.RED));
		}
		stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
	}

	public static String describe(ArpgStatModifier modifier) {
		boolean flat = modifier.operation() == ArpgModifierOperation.FLAT;
		return String.format(Locale.ROOT, "%.2f%s %s %s", modifier.value() * (flat ? 1 : 100), flat ? "" : "%",
				modifier.operation().getId(), modifier.stat().getId().replace('_', ' '));
	}

	public static List<ArpgStatModifier> modifiers(ArpgItemData data) {
		var content = ArpgData.content();
		var base = content.bases().get(data.base());
		var result = new ArrayList<ArpgStatModifier>();
		if (base == null) {
			return result;
		}
		result.addAll(base.modifiers());
		for (var roll : data.affixes()) {
			var a = content.affixes().get(roll.affix());
			if (a != null) {
				result.add(new ArpgStatModifier(a.stat(), a.operation(), roll.value()));
			}
		}
		var unique = content.uniques().get(data.unique());
		if (unique != null) {
			result.addAll(unique.modifiers());
		}
		for (var rune : data.runes()) {
			result.add(switch (rune) {
				case "ember" -> new ArpgStatModifier(ArpgStat.FIRE_DAMAGE, ArpgModifierOperation.INCREASED, .08);
				case "frost" -> new ArpgStatModifier(ArpgStat.COLD_DAMAGE, ArpgModifierOperation.INCREASED, .08);
				case "storm" -> new ArpgStatModifier(ArpgStat.LIGHTNING_DAMAGE, ArpgModifierOperation.INCREASED, .08);
				case "vita" -> new ArpgStatModifier(ArpgStat.MAXIMUM_LIFE, ArpgModifierOperation.FLAT, 2);
				case "arcane" -> new ArpgStatModifier(ArpgStat.MAXIMUM_MANA, ArpgModifierOperation.FLAT, 15);
				case "gale" -> new ArpgStatModifier(ArpgStat.MOVEMENT_SPEED, ArpgModifierOperation.INCREASED, .03);
				default -> throw new IllegalArgumentException("Unknown rune " + rune);
			});
		}
		return result;
	}

	public static Set<String> rules(ArpgItemData data) {
		var result = new HashSet<String>();
		var unique = ArpgData.content().uniques().get(data.unique());
		if (unique != null) {
			result.addAll(unique.rules());
		}
		if (data.runes().equals(List.of("ember", "gale", "ember"))) {
			result.add("infernal_edge");
		}
		if (data.runes().equals(List.of("arcane", "vita", "arcane"))) {
			result.add("arcane_bulwark");
		}
		if (data.runes().equals(List.of("vita", "arcane", "vita"))) {
			result.add("blood_scripture");
		}
		return Set.copyOf(result);
	}
}
