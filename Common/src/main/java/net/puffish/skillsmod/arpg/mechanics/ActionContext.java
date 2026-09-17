package net.puffish.skillsmod.arpg.mechanics;

import net.puffish.skillsmod.arpg.metric.QualifierSet;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public record ActionContext(
		String actionId,
		String ownerId,
		String sourceEntityId,
		String targetEntityId,
		String weaponKey,
		QualifierSet tags,
		Map<String, Double> resourceState,
		TriggerTrace triggerTrace
) {
	public ActionContext {
		actionId = required(actionId, "actionId");
		ownerId = required(ownerId, "ownerId");
		sourceEntityId = optional(sourceEntityId);
		targetEntityId = optional(targetEntityId);
		weaponKey = optional(weaponKey);
		if (tags == null) {
			throw new IllegalArgumentException("tags cannot be null");
		}
		if (resourceState == null) {
			throw new IllegalArgumentException("resourceState cannot be null");
		}
		var resources = new TreeMap<String, Double>();
		for (var entry : resourceState.entrySet()) {
			var key = required(entry.getKey(), "resource key");
			var value = entry.getValue();
			if (value == null || !Double.isFinite(value)) {
				throw new IllegalArgumentException("resource values must be finite");
			}
			resources.put(key, value);
		}
		resourceState = Collections.unmodifiableMap(resources);
		if (triggerTrace == null) {
			throw new IllegalArgumentException("triggerTrace cannot be null");
		}
		if (!triggerTrace.rootOwnerId().equals(ownerId)) {
			throw new IllegalArgumentException("trigger trace root owner does not match ownerId");
		}
	}

	public static ActionContext root(
			String actionId,
			String ownerId,
			String sourceEntityId,
			String weaponKey,
			QualifierSet tags,
			Map<String, Double> resourceState
	) {
		var normalizedAction = required(actionId, "actionId");
		var normalizedOwner = required(ownerId, "ownerId");
		return new ActionContext(
				normalizedAction,
				normalizedOwner,
				sourceEntityId,
				"",
				weaponKey,
				tags,
				resourceState,
				TriggerTrace.root(normalizedAction, normalizedOwner)
		);
	}

	public ActionContext child(
			String childActionId,
			String childSourceEntityId,
			QualifierSet childTags,
			Map<String, Double> childResourceState,
			TriggerTrace childTrace
	) {
		return new ActionContext(
				childActionId,
				ownerId,
				childSourceEntityId,
				"",
				weaponKey,
				childTags,
				childResourceState,
				childTrace
		);
	}

	public ActionContext withTarget(String targetEntityId) {
		return new ActionContext(
				actionId,
				ownerId,
				sourceEntityId,
				targetEntityId,
				weaponKey,
				tags,
				resourceState,
				triggerTrace
		);
	}

	public ActionContext withTrace(TriggerTrace trace) {
		return new ActionContext(
				actionId,
				ownerId,
				sourceEntityId,
				targetEntityId,
				weaponKey,
				tags,
				resourceState,
				trace
		);
	}

	private static String required(String value, String name) {
		if (value == null) {
			throw new IllegalArgumentException(name + " cannot be null");
		}
		var normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException(name + " cannot be blank");
		}
		return normalized;
	}

	private static String optional(String value) {
		return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
	}
}
