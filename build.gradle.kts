plugins {
    id("com.github.ElytraServers.elytra-conventions") version("v1.1.2")
    id("com.gtnewhorizons.gtnhconvention")
}

// 项目入口只组织职责模块；版本与发布仍由既有convention插件管理。
apply(from = "gradle/font-runtime.gradle.kts")
apply(from = "gradle/content-relocation.gradle.kts")
apply(from = "gradle/core.gradle.kts")
apply(from = "gradle/rendering.gradle.kts")
apply(from = "gradle/gallery.gradle.kts")
apply(from = "gradle/media.gradle.kts")
apply(from = "gradle/desktop.gradle.kts")
apply(from = "gradle/packaging.gradle.kts")
apply(from = "gradle/embedded.gradle.kts")
apply(from = "gradle/verification.gradle.kts")
