plugins {
    id("com.github.ElytraServers.elytra-conventions") version("v1.1.2")
    id("com.gtnewhorizons.gtnhconvention")
}

// 项目入口只组织职责模块；版本与发布仍由既有convention插件管理。
apply(from = "gradle/font-runtime.gradle.kts")
apply(from = "gradle/core.gradle.kts")
apply(from = "gradle/packaging.gradle.kts")
apply(from = "gradle/headless.gradle.kts")
apply(from = "gradle/verification.gradle.kts")
