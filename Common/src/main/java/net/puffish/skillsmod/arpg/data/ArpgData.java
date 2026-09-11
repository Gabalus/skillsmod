package net.puffish.skillsmod.arpg.data;

import net.minecraft.server.MinecraftServer;
import net.puffish.skillsmod.SkillsMod;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ArpgData {
	private static ArpgContent content;
	private static long revision;

	private ArpgData() {
	}

	public static ArpgContent content() {
		if (content == null) {
			try (var stream = ArpgData.class.getResourceAsStream("/data/puffish_skills/arpg/catalog.json")) {
				if (stream == null) {
					throw new IllegalStateException("Missing bundled ARPG catalog");
				}
				content = ArpgContent.read(new InputStreamReader(stream, StandardCharsets.UTF_8));
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}
		return content;
	}

	public static void reload(MinecraftServer server) {
		var id = SkillsMod.createIdentifier("arpg/catalog.json");
		try (var reader = server.getResourceManager().getResourceOrThrow(id).getReader()) {
			var replacement = ArpgContent.read(reader);
			content = replacement;
			revision++;
		} catch (IOException | RuntimeException e) {
			SkillsMod.getInstance().getLogger().error("ARPG catalog reload rejected; keeping previous definitions: " + e.getMessage());
			content();
		}
	}

	public static long revision() {
		return revision;
	}
}
