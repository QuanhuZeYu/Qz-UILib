
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.testing.Test

plugins {
    id("com.github.ElytraServers.elytra-conventions") version("v1.1.2")
    id("com.gtnewhorizons.gtnhconvention")
}

// 测试堆 1g：headless 软件渲染验收共享一张 GlyphRuntimeTables（约 123MiB direct-index 表），
// 512m 默认堆下与其他字体测试类同 JVM 跑会互相挤压 OOM。
tasks.withType<Test>().configureEach {
    maxHeapSize = "1024m"
}

val isJitPack = providers.environmentVariable("JITPACK")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)

tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = !isJitPack.get()
}
