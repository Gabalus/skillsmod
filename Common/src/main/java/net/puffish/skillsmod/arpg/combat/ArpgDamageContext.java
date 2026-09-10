package net.puffish.skillsmod.arpg.combat;

import net.puffish.skillsmod.arpg.skill.SkillTag;

import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Identifies damage that is already owned by the ARPG executor.
 *
 * <p>The context is thread-scoped because Minecraft damage callbacks execute synchronously on the
 * server thread. Loader event bridges can therefore distinguish an active skill from a normal
 * vanilla/Better Combat attack without inventing a custom damage type or calling player.attack().</p>
 */
public record ArpgDamageContext(Kind kind, String skill, Set<SkillTag> tags) {
	private static final ThreadLocal<ArpgDamageContext> CURRENT = new ThreadLocal<>();

	public ArpgDamageContext {
		if (kind == null) {
			throw new IllegalArgumentException("Damage kind is required");
		}
		skill = skill == null ? "" : skill;
		tags = tags == null ? Set.of() : Set.copyOf(tags);
	}

	public enum Kind {
		BASIC_ATTACK,
		ATTACK_SKILL,
		SPELL,
		DAMAGE_OVER_TIME,
		TRIGGERED
	}

	public Set<String> ruleTags() {
		return tags.stream()
				.map(tag -> tag.name().toLowerCase(Locale.ROOT))
				.collect(Collectors.toUnmodifiableSet());
	}

	public static ArpgDamageContext current() {
		return CURRENT.get();
	}

	public static <T> T call(ArpgDamageContext context, Supplier<T> action) {
		if (context == null || action == null) {
			throw new IllegalArgumentException("Context and action are required");
		}
		var previous = CURRENT.get();
		CURRENT.set(context);
		try {
			return action.get();
		} finally {
			if (previous == null) {
				CURRENT.remove();
			} else {
				CURRENT.set(previous);
			}
		}
	}
}
