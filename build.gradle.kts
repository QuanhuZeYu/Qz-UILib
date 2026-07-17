
import org.gradle.api.publish.tasks.GenerateModuleMetadata

plugins {
    id("com.github.ElytraServers.elytra-conventions") version("v1.1.2")
    id("com.gtnewhorizons.gtnhconvention")
}

val isJitPack = providers.environmentVariable("JITPACK")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)

tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = !isJitPack.get()
}
