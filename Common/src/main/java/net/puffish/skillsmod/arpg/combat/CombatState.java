package net.puffish.skillsmod.arpg.combat;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Immutable server-owned combat meters for one active pillar. */
public record CombatState(CombatPillar pillar, Map<CombatResource, ResourcePool> resources, long revision) {
	public CombatState {
		if (pillar == null || resources == null) {
			throw new IllegalArgumentException("Combat pillar and resources cannot be null");
		}
		if (revision < 0L) {
			throw new IllegalArgumentException("Combat state revision cannot be negative");
		}
		var copy = new EnumMap<CombatResource, ResourcePool>(CombatResource.class);
		for (var entry : resources.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) {
				throw new IllegalArgumentException("Combat resources cannot contain null entries");
			}
			copy.put(entry.getKey(), entry.getValue());
		}
		if (copy.isEmpty()) {
			throw new IllegalArgumentException("Combat state must contain at least one resource");
		}
		resources = Collections.unmodifiableMap(copy);
	}

	public static CombatState fresh(CombatPillar pillar) {
		if (pillar == null) {
			throw new IllegalArgumentException("Combat pillar cannot be null");
		}
		var pools = new EnumMap<CombatResource, ResourcePool>(CombatResource.class);
		switch (pillar) {
			case MARTIAL -> {
				pools.put(CombatResource.STAMINA, ResourcePool.full(100.0));
				pools.put(CombatResource.POSTURE, ResourcePool.empty(100.0));
				pools.put(CombatResource.MOMENTUM, ResourcePool.empty(100.0));
			}
			case GUNNER -> {
				pools.put(CombatResource.AMMUNITION, ResourcePool.full(12.0));
				pools.put(CombatResource.HEAT, ResourcePool.empty(100.0));
				pools.put(CombatResource.MOMENTUM, ResourcePool.empty(100.0));
			}
			case HUNTER -> {
				pools.put(CombatResource.FOCUS, ResourcePool.empty(100.0));
				pools.put(CombatResource.CONCEALMENT, ResourcePool.full(100.0));
				pools.put(CombatResource.MOMENTUM, ResourcePool.empty(100.0));
			}
			case ARCANE -> {
				pools.put(CombatResource.MANA, ResourcePool.full(100.0));
				pools.put(CombatResource.INSTABILITY, ResourcePool.empty(100.0));
				pools.put(CombatResource.FOCUS, ResourcePool.empty(100.0));
			}
			case COMMANDER -> {
				pools.put(CombatResource.COMMAND, ResourcePool.empty(100.0));
				pools.put(CombatResource.COHESION, ResourcePool.full(100.0));
			}
			case ENGINEER -> {
				pools.put(CombatResource.POWER, ResourcePool.full(100.0));
				pools.put(CombatResource.SCRAP, ResourcePool.empty(100.0));
				pools.put(CombatResource.HEAT, ResourcePool.empty(100.0));
			}
			case ALCHEMIST -> {
				pools.put(CombatResource.CATALYST, ResourcePool.full(100.0));
				pools.put(CombatResource.TOXICITY, ResourcePool.empty(100.0));
			}
			case LIVING -> {
				pools.put(CombatResource.BIOMASS, ResourcePool.full(100.0));
				pools.put(CombatResource.TISSUE_CONDITION, ResourcePool.full(100.0));
				pools.put(CombatResource.INSTABILITY, ResourcePool.empty(100.0));
			}
			default -> throw new IllegalArgumentException("Unsupported combat pillar: " + pillar);
		}
		return new CombatState(pillar, pools, 0L);
	}

	public ResourcePool require(CombatResource resource) {
		var pool = resources.get(resource);
		if (pool == null) {
			throw new IllegalArgumentException(resource.id() + " is not active for the " + pillar.id() + " pillar");
		}
		return pool;
	}

	public CombatState withPool(CombatResource resource, ResourcePool pool) {
		if (!resources.containsKey(resource)) {
			throw new IllegalArgumentException(resource.id() + " is not active for the " + pillar.id() + " pillar");
		}
		if (pool == null) {
			throw new IllegalArgumentException("Resource pool cannot be null");
		}
		if (pool.equals(resources.get(resource))) {
			return this;
		}
		var next = new EnumMap<CombatResource, ResourcePool>(resources);
		next.put(resource, pool);
		return new CombatState(pillar, next, revision + 1L);
	}

	public CombatState gain(CombatResource resource, double amount) {
		return withPool(resource, require(resource).gain(amount));
	}

	public CombatState reduce(CombatResource resource, double amount) {
		return withPool(resource, require(resource).reduce(amount));
	}

	public Spend spend(CombatResource resource, double amount) {
		var result = require(resource).spend(amount);
		return result.applied()
				? new Spend(true, withPool(resource, result.pool()), result.amount())
				: new Spend(false, this, 0.0);
	}

	/** Stable string snapshot used by ActionContext and provider bridges. */
	public Map<String, Double> snapshot() {
		var snapshot = new java.util.TreeMap<String, Double>();
		for (var entry : resources.entrySet()) {
			snapshot.put(entry.getKey().id(), entry.getValue().current());
		}
		return Collections.unmodifiableMap(snapshot);
	}

	public record Spend(boolean applied, CombatState state, double amount) {
	}
}
