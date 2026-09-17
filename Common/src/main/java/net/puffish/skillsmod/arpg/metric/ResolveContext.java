package net.puffish.skillsmod.arpg.metric;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public record ResolveContext(String scope, Set<String> flags) {
	public ResolveContext {
		if (scope == null) {
			throw new IllegalArgumentException("scope cannot be null");
		}
		scope = normalize(scope, "scope");
		if (flags == null) {
			throw new IllegalArgumentException("flags cannot be null");
		}
		var normalizedFlags = new TreeSet<String>();
		for (var flag : flags) {
			normalizedFlags.add(normalize(flag, "flag"));
		}
		flags = Collections.unmodifiableSet(normalizedFlags);
	}

	public static ResolveContext global() {
		return new ResolveContext("global", Set.of());
	}

	public static ResolveContext global(Set<String> flags) {
		return new ResolveContext("global", flags);
	}

	public boolean hasFlag(String flag) {
		return flags.contains(normalize(flag, "flag"));
	}

	private static String normalize(String value, String name) {
		if (value == null) {
			throw new IllegalArgumentException(name + " cannot be null");
		}
		var normalized = value.trim().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException(name + " cannot be blank");
		}
		return normalized;
	}
}
