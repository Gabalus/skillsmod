import java.io.File
import java.util.zip.ZipFile

plugins {
	id("dev.architectury.loom")
	id("checkstyle")
}

val arpgIronsRuntime = providers.gradleProperty("arpg_irons_runtime")
	.map(String::toBoolean)
	.orElse(false)
val arpgFullRuntime = providers.gradleProperty("arpg_full_runtime")
	.map(String::toBoolean)
	.orElse(false)

val arpgEpicRuntime = providers.gradleProperty("arpg_epic_runtime")
	.map(String::toBoolean)
	.orElse(false)
val epicFightCoordinate = "maven.modrinth:vu3NZ5Ma:8HHhJt6i"

repositories {
	maven(url = "https://maven.neoforged.net/releases/")
	maven(url = "https://api.modrinth.com/maven") {
		content {
			includeGroup("maven.modrinth")
		}
	}
	maven(url = "https://www.cursemaven.com") {
		content {
			includeGroup("curse.maven")
		}
	}
}

base.archivesName.set("${project.properties["archives_base_name"]}")
version = "${project.properties["mod_version"]}-${project.properties["minecraft_version"]}-neoforge"
group = "${project.properties["maven_group"]}"

evaluationDependsOn(":Common")

java {
	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
	minecraft("com.mojang:minecraft:${project.properties["minecraft_version"]}")
	mappings(loom.layered {
		mappings("net.fabricmc:yarn:${project.properties["yarn_mappings"]}:v2")
		mappings("dev.architectury:yarn-mappings-patch-neoforge:${project.properties["yarn_mappings_patch"]}")
	})

	neoForge("net.neoforged:neoforge:${if (arpgEpicRuntime.get()) "21.1.219" else project.properties["neoforge_version"]}")

	implementation(project(path = ":Common", configuration = "namedElements"))

	// Compile against Iron's exact 1.21.1 API for narrow optional mixins; it remains optional at runtime.
	add("modCompileOnly", "maven.modrinth:s4OWxYQQ:slKLosTb")

	if (arpgIronsRuntime.get() || arpgFullRuntime.get()) {
		// Iron's Spells 'n Spellbooks 1.21.1-3.16.3 and its runtime dependency family.
		add("modRuntimeOnly", "maven.modrinth:s4OWxYQQ:slKLosTb")
		add("modRuntimeOnly", "maven.modrinth:8BmcQJ2H:QEqpUJ1G")
		add("modRuntimeOnly", "maven.modrinth:gedNE4y2:q60QWuOK")
		add("modRuntimeOnly", "maven.modrinth:vvuO3ImH:yohfFbgD")
		add("modRuntimeOnly", "maven.modrinth:9nfaJPtX:sQyzhxuH")
	}

	if (arpgEpicRuntime.get()) {
		add("modRuntimeOnly", epicFightCoordinate)
	}

	if (arpgFullRuntime.get() && !arpgEpicRuntime.get()) {
		// Better Combat 2.4.0 + Cloth Config. playerAnimator is already supplied by the Iron's profile.
		add("modRuntimeOnly", "maven.modrinth:5sy6g3kz:VhIOvcXP")
		add("modRuntimeOnly", "maven.modrinth:9s6osm5g:izKINKFg")

	}

	if (arpgFullRuntime.get()) {
		// Apotheosis 8.7.0 and all required 1.21.1 modules. Modrinth Maven does not resolve mod dependencies transitively.
		add("modRuntimeOnly", "maven.modrinth:rqFWfVlz:wB4eASdJ")
		add("modRuntimeOnly", "maven.modrinth:tCkE8p2N:nU7CXkMr")
		add("modRuntimeOnly", "maven.modrinth:DGaH8Rh0:Xtaunf84")
		add("modRuntimeOnly", "maven.modrinth:DfxVkOAO:pWfxcfO2")
		add("modRuntimeOnly", "maven.modrinth:pL8MtgqY:56c0M28v")
		add("modRuntimeOnly", "maven.modrinth:nU0bVIaL:BIogJv2D")

		// L2 Hostility / L2 Artifacts substrate and required shared libraries.
		add("modRuntimeOnly", "maven.modrinth:4Vh3BQ3F:640EOvKh")
		add("modRuntimeOnly", "maven.modrinth:oPrh2Lz3:vxfEwwS7")
		add("modRuntimeOnly", "maven.modrinth:CbV689EN:w8M00mGg")
		add("modRuntimeOnly", "maven.modrinth:8RtpLoXH:IMGXB86Q")

		// Celestial Artifacts 2.0.4 for NeoForge 1.21.1. Curios is already supplied by the Iron's profile.
		add("modRuntimeOnly", "curse.maven:celestial-artifacts-988784:8805750")
	}
}

loom {
	mixin.useLegacyMixinAp = false
}

tasks.test {
	dependsOn(project(":Common").tasks.test)
}

tasks.check {
	dependsOn(project(":Common").tasks.check)
}

tasks.register("verifyArpgIronsRuntime") {
	group = "verification"
	description = "Resolves the opt-in Minecraft 1.21.1 + Iron's 3.16.x NeoForge runtime profile."

	doLast {
		if (!arpgIronsRuntime.get() && !arpgFullRuntime.get()) {
			throw GradleException("Run with -Parpg_irons_runtime=true or -Parpg_full_runtime=true.")
		}
		val resolvedFiles = configurations.getByName("modRuntimeOnly").resolve()
		check(resolvedFiles.isNotEmpty()) { "Iron's runtime profile resolved no mod files." }
		logger.lifecycle("Resolved ${resolvedFiles.size} Iron's/ARPG runtime mod files.")
	}
}

tasks.register("verifyArpgFullRuntime") {
	group = "verification"
	description = "Resolves the full 1.21.1 ARPG provider runtime: Iron's, Better Combat, Apotheosis, L2 mods and Celestial Artifacts."

	doLast {
		if (!arpgFullRuntime.get()) {
			throw GradleException("Run with -Parpg_full_runtime=true to enable the full ARPG provider profile.")
		}
		val resolvedFiles = configurations.getByName("modRuntimeOnly").resolve()
		check(resolvedFiles.size >= 15) {
			"Full ARPG runtime resolved only ${resolvedFiles.size} files; expected provider mods and their required libraries."
		}
		logger.lifecycle("Resolved ${resolvedFiles.size} full ARPG provider runtime files.")
	}
}

tasks.register("verifyArpgEpicRuntime") {
	group = "verification"
	description = "Resolves the Epic Fight 21.17.3.1 / NeoForge 21.1.219 profile and checks its Sentinel assets."

	doLast {
		check(arpgEpicRuntime.get()) { "Run with -Parpg_epic_runtime=true." }
		val configuration = configurations.getByName("modRuntimeOnly")
		val artifacts = configuration.resolvedConfiguration.resolvedArtifacts
		check(artifacts.none { it.moduleVersion.id.name == "5sy6g3kz" || it.moduleVersion.id.name == "better-combat" }) {
			"Epic Fight profile must not include Better Combat."
		}
		val epic = artifacts.single { it.moduleVersion.id.name == "vu3NZ5Ma" && it.moduleVersion.id.version == "8HHhJt6i" }
		val javap = ProcessBuilder(File(System.getProperty("java.home"), "bin/javap").absolutePath,
				"-classpath", epic.file.absolutePath, "-s",
				"yesman.epicfight.world.capabilities.EpicFightCapabilities",
				"yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch")
			.redirectErrorStream(true).start()
		val api = javap.inputStream.bufferedReader().use { it.readText() }
		check(javap.waitFor() == 0) { "Epic Fight API inspection failed: $api" }
		check(api.contains("getEntityPatch(net.minecraft.world.entity.Entity, java.lang.Class")) {
			"Pinned Epic Fight no longer exposes the expected entity-patch accessor."
		}
		check(api.contains("public float getStamina();") && api.contains("public float getMaxStamina();")) {
			"Pinned Epic Fight no longer exposes the expected read-only stamina accessors."
		}
		layout.buildDirectory.file("reports/epic-fight-stamina-api.txt").get().asFile.apply {
			parentFile.mkdirs()
			writeText(api)
		}
		val report = layout.buildDirectory.file("reports/epic-fight-runtime.txt").get().asFile
		report.parentFile.mkdirs()
		ZipFile(epic.file).use { jar ->
			val metadata = jar.getEntry("META-INF/neoforge.mods.toml") ?: error("Epic Fight NeoForge metadata missing")
			val text = jar.getInputStream(metadata).bufferedReader().use { it.readText() }
			check(text.contains("epicfight")) { "Unexpected Epic Fight artifact metadata" }
			val assets = listOf("entity/biped", "entity/biped_old_texture",
					"animations/zombie/attack1", "animations/zombie/attack2", "animations/zombie/attack3",
					"animations/biped/living/idle", "animations/biped/living/walk", "animations/biped/living/death",
					"animations/biped/living/fall", "animations/biped/living/mount", "animations/biped/living/landing",
					"animations/biped/combat/hit_short", "animations/biped/combat/hit_long", "animations/biped/combat/knockdown")
			assets.forEach { asset -> check(jar.getEntry("assets/epicfight/animmodels/$asset.json") != null) { "Missing Sentinel asset: $asset" } }
			report.writeText("Epic Fight: $epicFightCoordinate\nNeoForge: 21.1.219\nBetter Combat: excluded\nAssets checked: ${assets.size}\n\n$text")
		}
		logger.lifecycle("Epic Fight runtime and Sentinel assets verified. Report: $report")
	}
}

tasks.jar {
	from(project.rootDir.resolve("LICENSE.txt"))
	from(project.rootDir.resolve("LICENSE-RESOURCES.txt"))
}

tasks.processResources {
	from(project(":Common").sourceSets.main.get().resources)

	inputs.property("version", project.properties["mod_version"])
	filesMatching("META-INF/neoforge.mods.toml") {
		expand(mapOf("version" to project.properties["mod_version"]))
	}
}

tasks.compileJava {
	source(project(":Common").sourceSets.main.get().java)
}
