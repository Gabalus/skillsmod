package net.puffish.skillsmod.arpg.sandbox;

import com.google.gson.Gson;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.server.setup.ServerRegistrar;

public final class CraftworkItems {
	private static final Gson GSON = new Gson();
	public static final ComponentType<CraftedItemData> QUALITY = ComponentType.<CraftedItemData>builder().codec(Codec.STRING.xmap(CraftworkItems::decode, GSON::toJson)).build();
	public static final ComponentType<String> PENDING = ComponentType.<String>builder().codec(Codec.STRING).build();
	private CraftworkItems() {
	}

	private static CraftedItemData decode(String json) {
		if (json.length() > 32768) {
			throw new IllegalArgumentException("Craft quality exceeds size limit");
		}

		var data = GSON.fromJson(json, CraftedItemData.class);
		if (data == null) {
			throw new IllegalArgumentException("Missing craft quality");
		}

		return new CraftedItemData(data.stages());
	}

	public static void register(ServerRegistrar registrar) {
		registrar.register(Registries.DATA_COMPONENT_TYPE, SkillsMod.createIdentifier("craft_quality"), QUALITY);
		registrar.register(Registries.DATA_COMPONENT_TYPE, SkillsMod.createIdentifier("craft_pending"), PENDING);
	}
}
