
plugins {
    id("com.github.ElytraServers.elytra-conventions") version("v1.1.2")
    id("com.gtnewhorizons.gtnhconvention")
}

val configureCodeChickenMappings by tasks.registering {
    group = "minecraft"
    description = "Writes the CodeChickenLib deobf mapping directory before client runs."

    val minecraftVersion = providers.gradleProperty("minecraftVersion")
    val forgeVersion = providers.gradleProperty("forgeVersion")
    val mappingDir = providers.provider {
        gradle.gradleUserHomeDir.resolve(
            "caches/minecraft/net/minecraftforge/forge/" +
                "${minecraftVersion.get()}-${forgeVersion.get()}-${minecraftVersion.get()}/unpacked/conf"
        )
    }
    val configFile = layout.projectDirectory.file("run/client/config/CodeChickenLib.cfg")

    inputs.property("mappingDir", mappingDir.map { it.absolutePath })
    outputs.file(configFile)

    doLast {
        val requiredFiles = listOf("packaged.srg", "fields.csv", "methods.csv")
        val missingFiles = requiredFiles.filterNot { mappingDir.get().resolve(it).isFile }
        check(missingFiles.isEmpty()) {
            "Missing CodeChickenLib MCP mapping files in ${mappingDir.get().absolutePath}: " +
                missingFiles.joinToString(", ")
        }

        val outputFile = configFile.asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            #CodeChickenLib development configuration file.

            dump_asm=false

            #Path to directory holding packaged.srg, fields.csv and methods.csv for mcp remapping
            mappingDir=${mappingDir.get().absolutePath}

            textify=true
            """.trimIndent() + System.lineSeparator()
        )
    }
}

tasks.matching { it.name.startsWith("runClient") }.configureEach {
    dependsOn(configureCodeChickenMappings)
}
