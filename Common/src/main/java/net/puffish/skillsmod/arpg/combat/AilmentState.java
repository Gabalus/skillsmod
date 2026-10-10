package net.puffish.skillsmod.arpg.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Bounded saved applications; durations advance only while the victim ticks. */
public record AilmentState(List<Application> applications) {
	public static final int INTERVAL = 20;
	public static final int MAX_DURATION = 1200;
	public static final double MAX_DAMAGE = 10000;

	public AilmentState {
		applications = List.copyOf(applications);
		for (var type : AilmentType.values()) {
			if (applications.stream().filter(a -> a.type() == type).count() > type.stackLimit()) {
				throw new IllegalArgumentException("Too many ailment stacks");
			}
		}
	}

	public static AilmentState empty() {
		return new AilmentState(List.of());
	}

	public boolean has(AilmentType type) {
		return applications.stream().anyMatch(a -> a.type() == type);
	}

	public AilmentState remove(AilmentType type) {
		return new AilmentState(applications.stream().filter(a -> a.type() != type).toList());
	}

	public AilmentState apply(Application incoming) {
		var next = new ArrayList<>(applications);
		var type = incoming.type();
		var matching = next.stream().filter(a -> a.type() == type).toList();
		if (matching.size() >= incoming.type().stackLimit()) {
			var weakest = matching.stream().min(java.util.Comparator.comparingDouble(Application::damage)).orElseThrow();
			if (incoming.damage() < weakest.damage()) {
				return this;
			}
			if (incoming.type().stackLimit() == 1 && incoming.damage() == weakest.damage()) {
				if (incoming.remaining() <= weakest.remaining()) {
					return this;
				}
				incoming = new Application(incoming.type(), incoming.owner(), incoming.skill(), incoming.damage(),
						incoming.remaining(), weakest.untilTick());
			} else if (incoming.type().stackLimit() > 1 && incoming.damage() == weakest.damage()) {
				return this;
			}
			if (type.stackLimit() == 1) {
				incoming = new Application(type, incoming.owner(), incoming.skill(), incoming.damage(),
						incoming.remaining(), weakest.untilTick());
			}
			next.remove(weakest);
		}
		next.add(incoming);
		return new AilmentState(next);
	}

	public Step tick(boolean moving) {
		var next = new ArrayList<Application>();
		var pulses = new ArrayList<Pulse>();
		for (var application : applications) {
			int remaining = application.remaining() - 1;
			int untilTick = application.untilTick() - 1;
			if (untilTick == 0) {
				double amount = application.damage() * (moving && application.type() == AilmentType.BLEED ? 2 : 1);
				pulses.add(new Pulse(application, amount));
				untilTick = INTERVAL;
			}
			if (remaining > 0) {
				next.add(new Application(application.type(), application.owner(), application.skill(),
						application.damage(), remaining, untilTick));
			}
		}
		return new Step(new AilmentState(next), List.copyOf(pulses));
	}

	public record Application(AilmentType type, UUID owner, String skill, double damage, int remaining, int untilTick) {
		public Application {
			if (type == null || owner == null || skill == null || skill.length() > 128 || !Double.isFinite(damage)
					|| damage <= 0 || damage > MAX_DAMAGE || remaining < 1 || remaining > MAX_DURATION
					|| untilTick < 1 || untilTick > INTERVAL) {
				throw new IllegalArgumentException("Invalid ailment application");
			}
		}
	}

	public record Pulse(Application application, double damage) {
	}

	public record Step(AilmentState state, List<Pulse> pulses) {
	}
}
