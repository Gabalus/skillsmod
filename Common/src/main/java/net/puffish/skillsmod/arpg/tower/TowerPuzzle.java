package net.puffish.skillsmod.arpg.tower;

import net.puffish.skillsmod.arpg.progression.CompletionReward;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;

/** Ordered physical relays gate one passage; puzzle solves never mint progression rewards. */
public record TowerPuzzle(String id, String link, List<Relay> sequence) {
	public record Relay(String dimension, int x, int y, int z, String block) {
		public Relay {
			CompletionReward.checkId(dimension);
			CompletionReward.checkId(block);
			if (dimension.equals("puffish_skills:rifts") || Math.abs((long) x) > 29_999_900
					|| Math.abs((long) z) > 29_999_900 || y < -2048 || y > 2047) {
				throw new IllegalArgumentException("Invalid puzzle relay position");
			}
		}

		public String position() {
			return dimension + ":" + x + ":" + y + ":" + z;
		}
	}

	public TowerPuzzle {
		CompletionReward.checkId(id);
		CompletionReward.checkId(link);
		sequence = List.copyOf(sequence);
		if (sequence.size() < 2 || sequence.size() > 8) {
			throw new IllegalArgumentException("A tower puzzle needs two to eight relays");
		}
		var positions = new HashSet<String>();
		for (var relay : sequence) {
			if (!positions.add(relay.position())) {
				throw new IllegalArgumentException("Duplicate relay in puzzle");
			}
		}
	}

	/** Receipts are bound to the full ordered definition, not only its reusable ID. */
	public String fingerprint() {
		var text = new StringBuilder(id).append('|').append(link);
		sequence.forEach(relay -> text.append('|').append(relay.position()).append('|').append(relay.block()));
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is required by the Java runtime", error);
		}
	}
}
