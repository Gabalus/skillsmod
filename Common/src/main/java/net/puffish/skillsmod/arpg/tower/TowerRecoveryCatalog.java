package net.puffish.skillsmod.arpg.tower;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Rejects overlapping assignments rather than selecting an arbitrary death policy. */
public final class TowerRecoveryCatalog {
	private final Map<TowerSector, TowerRecovery> assignments;

	public TowerRecoveryCatalog(List<TowerRecovery> definitions, List<TowerSector> sectors) {
		if (definitions == null || definitions.size() > 64 || sectors == null || sectors.size() > 128) {
			throw new IllegalArgumentException("Invalid tower recovery catalog size");
		}
		var byId = new HashMap<String, TowerSector>();
		for (var sector : sectors) {
			if (byId.putIfAbsent(sector.id(), sector) != null) {
				throw new IllegalArgumentException("Duplicate recovery sector");
			}
		}
		var assignments = new HashMap<TowerSector, TowerRecovery>();
		var ids = new HashSet<String>();
		for (var policy : definitions) {
			if (!ids.add(policy.id())) {
				throw new IllegalArgumentException("Duplicate recovery policy");
			}
			boolean checkpointInside = false;
			for (var id : policy.sectors()) {
				var sector = byId.get(id);
				if (sector == null || assignments.putIfAbsent(sector, policy) != null) {
					throw new IllegalArgumentException("Unknown or multiply assigned recovery sector");
				}
				var point = policy.checkpoint();
				checkpointInside |= sector.contains(point.dimension(), point.x(), point.y(), point.z())
						&& sector.contains(point.dimension(), point.x(), point.y() + 1, point.z());
			}
			if (!checkpointInside) {
				throw new IllegalArgumentException("Recovery checkpoint must fit inside its assigned protected sectors");
			}
		}
		for (var a : assignments.keySet()) {
			for (var b : assignments.keySet()) {
				if (!assignments.get(a).id().equals(assignments.get(b).id()) && overlaps(a, b)) {
					throw new IllegalArgumentException("Conflicting recovery sectors overlap");
				}
			}
		}
		this.assignments = Map.copyOf(assignments);
	}

	private static boolean overlaps(TowerSector a, TowerSector b) {
		return a.dimension().equals(b.dimension()) && a.minX() <= b.maxX() && a.maxX() >= b.minX()
				&& a.minY() <= b.maxY() && a.maxY() >= b.minY() && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
	}

	public TowerRecovery at(String dimension, double x, double y, double z) {
		return assignments.entrySet().stream().filter(entry -> entry.getKey().contains(dimension, x, y, z))
				.map(Map.Entry::getValue).findFirst().orElse(null);
	}
}
