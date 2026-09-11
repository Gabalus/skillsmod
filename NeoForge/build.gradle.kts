plugins {
	id("dev.architectury.loom")
	id("checkstyle")
}

val arpgIronsRuntime = providers.gradleProperty("arpg_irons_runtime")
	.map(String::toBoolean)
	.orElse(false)

repositories {
	maven(url = "https://maven.neoforged.net/releases/")
	maven(url = "https://api.modrinth.com/maven") {
		content {
			includeGroup("maven.modrinth")
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

	neoForge("net.neoforged:neoforge:${project.properties["neoforge_version"]}")

	implementation(project(path = ":Common", configuration = "namedElements"))

	// Compile against Iron's exact 1.21.1 API for narrow optional mixins; it remains optional at runtime.
	add("modCompileOnly", "maven.modrinth:s4OWxYQQ:slKLosTb")

	if (arpgIronsRuntime.get()) {
		// Iron's Spells 'n Spellbooks 1.21.1-3.16.3 and the exact dependency family used by its 1.21 branch.
		add("modRuntimeOnly", "maven.modrinth:s4OWxYQQ:slKLosTb")
		add("modRuntimeOnly", "maven.modrinth:8BmcQJ2H:QEqpUJ1G")
		add("modRuntimeOnly", "maven.modrinth:gedNE4y2:q60QWuOK")
		add("modRuntimeOnly", "maven.modrinth:vvuO3ImH:yohfFbgD")
		add("modRuntimeOnly", "maven.modrinth:9nfaJPtX:sQyzhxuH")
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
		if (!arpgIronsRuntime.get()) {
			throw GradleException("Run with -Parpg_irons_runtime=true to enable the Iron's runtime profile.")
		}
		val resolvedFiles = configurations.getByName("modRuntimeOnly").resolve()
		check(resolvedFiles.isNotEmpty()) { "Iron's runtime profile resolved no mod files." }
		logger.lifecycle("Resolved ${resolvedFiles.size} ARPG runtime mod files.")
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
