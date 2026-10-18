plugins {
    id("com.github.ElytraServers.elytra-conventions") version("v1.1.2")
    id("com.gtnewhorizons.gtnhconvention")
}

group = "club.heiqi.uilib"
version = providers.gradleProperty("uilibVersion").getOrElse("4.11.1")
extra["modVersion"] = version.toString()

apply(from = "gradle/uilib-dependencies.gradle.kts")
apply(from = "gradle/minecraft.gradle.kts")
apply(from = "gradle/packaging.gradle.kts")
apply(from = "gradle/embedded.gradle.kts")
apply(from = "gradle/verification.gradle.kts")
apply(from = "gradle/java8-paths.gradle.kts")
