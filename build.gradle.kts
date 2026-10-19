plugins {
    id("com.github.ElytraServers.elytra-conventions") version("v1.1.2")
    id("com.gtnewhorizons.gtnhconvention")
}

group = "club.heiqi.uilib"
// 工作站本地开发组合与原 -P 共用项目属性，避免依赖和宿主版本分别取值。
version = findProperty("uilibVersion")?.toString() ?: "4.11.1"
extra["modVersion"] = version.toString()

apply(from = "gradle/uilib-dependencies.gradle.kts")
apply(from = "gradle/minecraft.gradle.kts")
apply(from = "gradle/packaging.gradle.kts")
apply(from = "gradle/embedded.gradle.kts")
apply(from = "gradle/verification.gradle.kts")
apply(from = "gradle/java8-paths.gradle.kts")
