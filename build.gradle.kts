plugins {
    id("com.github.ElytraServers.elytra-conventions") version("v1.1.2")
    id("com.gtnewhorizons.gtnhconvention")
}

// Maven GAV group 与 Java package 解耦：公开仓使用 asia.quanhuzeyu.uilib，代码包名保持 club.heiqi.uilib。
// 工作站本地开发组合与原 -P 共用项目属性，避免依赖和宿主版本分别取值。
version = findProperty("uilibVersion")?.toString() ?: "5.0.0-beta1"
group = findProperty("uilibGroup")?.toString()
    ?: if (version.toString().startsWith("4.")) "club.heiqi.uilib" else "asia.quanhuzeyu.uilib"
extra["modVersion"] = version.toString()

apply(from = "gradle/uilib-dependencies.gradle.kts")
apply(from = "gradle/minecraft.gradle.kts")
apply(from = "gradle/packaging.gradle.kts")
apply(from = "gradle/release-assets.gradle.kts")
apply(from = "gradle/embedded.gradle.kts")
apply(from = "gradle/verification.gradle.kts")
apply(from = "gradle/java8-paths.gradle.kts")
apply(from = "gradle/compat-interface.gradle.kts")
