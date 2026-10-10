package net.puffish.skillsmod.arpg.tower;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.RegistryKeys;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Separate registry type lets Epic Fight author the Sentinel without patching ordinary husks. */
public final class TowerEntities {
	private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(RegistryKeys.ENTITY_TYPE, "puffish_skills");
	public static final DeferredHolder<EntityType<?>, EntityType<TowerSentinelEntity>> SENTINEL = ENTITIES.register("tower_sentinel", () ->
			EntityType.Builder.<TowerSentinelEntity>create(TowerSentinelEntity::new, SpawnGroup.MONSTER)
					.dimensions(.6f, 1.95f).maxTrackingRange(8).build("puffish_skills:tower_sentinel"));

	private TowerEntities() {
	}

	public static void register(IEventBus bus) {
		ENTITIES.register(bus);
		bus.addListener(TowerEntities::attributes);
	}

	private static void attributes(EntityAttributeCreationEvent event) {
		event.put(SENTINEL.get(), ZombieEntity.createZombieAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 120)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7).build());
	}
}
