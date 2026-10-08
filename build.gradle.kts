
import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar
import org.gradle.api.tasks.bundling.Zip
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test

plugins {
    id("com.github.ElytraServers.elytra-conventions") version("v1.1.2")
    id("com.gtnewhorizons.gtnhconvention")
}

// 测试堆 1g：headless 软件渲染验收共享一张 GlyphRuntimeTables（约 123MiB direct-index 表），
// 512m 默认堆下与其他字体测试类同 JVM 跑会互相挤压 OOM。
tasks.withType<Test>().configureEach {
    maxHeapSize = "1024m"
    // 归零核心采用直启集成场景，check 必须执行 coreLoopScenario；当前无 JUnit 用例。
    failOnNoDiscoveredTests.set(false)
}

// 集成场景直启只包含核心与场景类，不将 MC/Forge/LWJGL 放到执行类路径。
val coreLoopScenario = registerTestApplication("coreLoopScenario", "运行无平台核心业务与场景闭环", "club.heiqi.uilib.ui.scene.integration.SceneCoreLoopTest") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output
}
val orderedBatchScenario = registerTestApplication("orderedBatchScenario", "运行无平台生产合批透明重叠与裁剪出图场景", "club.heiqi.uilib.ui.scene.integration.OrderedBatchScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output
    systemProperty("java.awt.headless", "true")
    args(layout.buildDirectory.dir("font-machine/batch").get().asFile.absolutePath)
}
tasks.named("check") { dependsOn(coreLoopScenario, orderedBatchScenario) }

// GPU bindings由宿主提供；CPU字体bindings以嵌套jar隔离发行，不覆盖LWJGL2/3宿主的同名类。
val fontBindings by configurations.creating
val fontNativeRuntime by configurations.creating { extendsFrom(fontBindings) }
val fontUnicodeRuntime by configurations.creating
val isolatedFontRuntime by configurations.creating
val lwjgl2Bindings by configurations.creating
dependencies {
    add(fontUnicodeRuntime.name, "com.ibm.icu:icu4j:76.1")
    add(lwjgl2Bindings.name, "org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209") { isTransitive = false }
    listOf("lwjgl", "lwjgl-freetype", "lwjgl-harfbuzz").forEach {
        add(isolatedFontRuntime.name, "org.lwjgl:$it:3.4.2") { isTransitive = false }
    }
    listOf("natives-windows", "natives-windows-x86", "natives-windows-arm64", "natives-linux", "natives-linux-arm64", "natives-macos", "natives-macos-arm64").forEach { classifier ->
        listOf("lwjgl", "lwjgl-freetype").forEach { module ->
            add(isolatedFontRuntime.name, "org.lwjgl:$module:3.4.2:$classifier") { isTransitive = false }
        }
    }
    listOf("lwjgl", "lwjgl-freetype", "lwjgl-harfbuzz", "lwjgl-opengl", "lwjgl-glfw").forEach {
        add(fontBindings.name, "org.lwjgl:$it:3.4.2") { isTransitive = false }
    }
    val platform = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val nativeClassifier = when {
        platform.contains("win") -> if (arch == "aarch64") "natives-windows-arm64" else if (arch == "x86") "natives-windows-x86" else "natives-windows"
        platform.contains("mac") -> if (arch == "aarch64") "natives-macos-arm64" else "natives-macos"
        else -> if (arch == "aarch64") "natives-linux-arm64" else "natives-linux"
    }
    listOf("lwjgl", "lwjgl-freetype", "lwjgl-opengl", "lwjgl-glfw").forEach {
        add(fontNativeRuntime.name, "org.lwjgl:$it:3.4.2:$nativeClassifier") { isTransitive = false }
    }
}
configurations.named("compileOnly") { extendsFrom(fontBindings) }
// PointerBuffer与BufferUtils须使用LWJGL3核心，不能被旧LWJGL2编译classpath遮蔽。
tasks.named<JavaCompile>("compileJava") { classpath = fontBindings + classpath }
tasks.named<JavaCompile>("compileTestJava") { classpath = fontBindings + classpath }
val isolatedFontResources = layout.buildDirectory.dir("generated/isolated-font")
val packageFontBindings by tasks.registering {
    inputs.files(isolatedFontRuntime)
    outputs.dir(isolatedFontResources)
    doLast {
        val destination = isolatedFontResources.get().dir("assets/qz_uilib/runtime/font").asFile
        destination.mkdirs()
        val artifacts = isolatedFontRuntime.files.sortedBy { it.name }
        // 私有字体固定使用完整的官方JNI实现。3.4.2的Java25多版本FFM在
        // FT_New_Memory_Face的invokePPNNPI生成时发生Operand stack underflow。
        // 仅移除随包字体绑定的版本覆盖，保留根类及全部native；不改宿主LWJGL或全局配置。
        // 升级绑定时重新评估：FFM完成同包Java8/25回归后再恢复多版本覆盖。
        artifacts.forEach { artifact ->
            ZipFile(artifact).use { source ->
                ZipOutputStream(File(destination, artifact.name).outputStream()).use { output ->
                    val entries = source.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.name.startsWith("META-INF/versions/")) {
                            val copy = ZipEntry(entry.name)
                            copy.time = 0L
                            output.putNextEntry(copy)
                            if (!entry.isDirectory) source.getInputStream(entry).use { it.copyTo(output) }
                            output.closeEntry()
                        }
                    }
                }
            }
        }
        File(destination, "index.txt").writeText(artifacts.joinToString("\n") { it.name } + "\n", Charsets.UTF_8)
    }
}
sourceSets.getByName("main").resources.srcDir(isolatedFontResources)
tasks.named("processResources") { dependsOn(packageFontBindings) }
tasks.named<Jar>("sourcesJar") {
    dependsOn(packageFontBindings)
    exclude("assets/qz_uilib/runtime/font/**")
    from("src/lwjgl2/java")
}
// 测试应用独立编译；纯Java8，不加入正式发行。
val testSupportSources = sourceSets.create("testSupport") {
    java.srcDir("src/testSupport/java")
    compileClasspath = files()
    runtimeClasspath = output
}
tasks.named<JavaCompile>(testSupportSources.compileJavaTaskName) {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.compilerArgs.add("-proc:none")
}
sourceSets.getByName("test") {
    compileClasspath += testSupportSources.output
    runtimeClasspath += testSupportSources.output
}
// convention插件为test任务单独设置classpath，显式把共同应用输出接到实际编译任务。
tasks.named<JavaCompile>("compileTestJava") {
    dependsOn(tasks.named(testSupportSources.classesTaskName))
    classpath += testSupportSources.output
}

// 共享启动设置；宿主classpath和参数仍由测试声明。
fun registerTestApplication(taskName: String, summary: String, application: String, useDefaultLauncher: Boolean = true, configure: JavaExec.() -> Unit) =
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        description = summary
        mainClass.set(application)
        if (useDefaultLauncher) javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
        jvmArgs("-Dfile.encoding=UTF-8")
        dependsOn(tasks.named(testSupportSources.classesTaskName))
        configure()
        classpath += testSupportSources.output
    }

val lwjgl2Sources = sourceSets.create("lwjgl2") {
    java.srcDir("src/lwjgl2/java")
    compileClasspath = sourceSets.getByName("main").output + lwjgl2Bindings
    runtimeClasspath = output + compileClasspath
}
tasks.named<JavaCompile>(lwjgl2Sources.compileJavaTaskName) {
    dependsOn(tasks.named("compileJava"))
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.compilerArgs.add("-proc:none")
}
tasks.named<Jar>("jar") { from(lwjgl2Sources.output) }
tasks.named<Jar>("shadowJar") { from(lwjgl2Sources.output) }
// 真实scene与响应式核心独立编译：此路径不经过MC源或宿主classpath。
val coreSources = sourceSets.create("core") {
    java.srcDir("src/main/java")
    java.include("club/heiqi/uilib/ui/scene/**", "club/heiqi/uilib/ui/reactive/**")
    compileClasspath = fontUnicodeRuntime
    runtimeClasspath = output + compileClasspath
}
tasks.named<JavaCompile>(coreSources.compileJavaTaskName) {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.compilerArgs.add("-proc:none")
}
val coreSceneClasses = layout.buildDirectory.dir("classes/core-scene")
val compileCoreSceneScenario by tasks.registering(JavaCompile::class) {
    dependsOn(tasks.named(coreSources.classesTaskName), tasks.named(testSupportSources.classesTaskName))
    source("src/test/java/club/heiqi/uilib/ui/scene/integration/SceneCoreLoopTest.java",
        "src/test/java/club/heiqi/uilib/ui/scene/integration/SoftwareSceneScenario.java",
        "src/test/java/club/heiqi/uilib/ui/scene/integration/PointerInputScenario.java")
    classpath = coreSources.output + testSupportSources.output
    destinationDirectory.set(coreSceneClasses)
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.compilerArgs.add("-proc:none")
}
val pointerInputScenario = registerTestApplication("pointerInputScenario", "无平台真实scene指针手势验收", "club.heiqi.uilib.ui.scene.integration.PointerInputScenario") {
    dependsOn(compileCoreSceneScenario)
    classpath = files(coreSceneClasses) + coreSources.output + fontUnicodeRuntime
}
val coreStateScenario = registerTestApplication("coreStateScenario", "隔离运行状态与生命周期场景", "club.heiqi.uilib.ui.scene.integration.SceneCoreLoopTest") {
    dependsOn(compileCoreSceneScenario)
    classpath = files(coreSceneClasses) + coreSources.output + fontUnicodeRuntime
}
tasks.named("check") { dependsOn(pointerInputScenario) }
val coreFontClasses = layout.buildDirectory.dir("classes/core-font")
val compileCoreFontScenario by tasks.registering(JavaCompile::class) {
    source(fileTree("src/main/java/club/heiqi/uilib/ui/scene/text") { include("**/*.java") })
    source("src/test/java/club/heiqi/uilib/ui/scene/integration/CoreFontEngineScenario.java")
    classpath = fontUnicodeRuntime + testSupportSources.output
    destinationDirectory.set(coreFontClasses)
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.compilerArgs.add("-proc:none")
}
val coreFontEngineScenario = registerTestApplication("coreFontEngineScenario", "不含Minecraft、Forge或LWJGL的真实核心合同运行", "club.heiqi.uilib.ui.scene.integration.CoreFontEngineScenario") {
    dependsOn(compileCoreFontScenario)
    classpath = files(coreFontClasses, "src/main/resources") + fontUnicodeRuntime
}
val testApplicationScenario = registerTestApplication("testApplicationScenario", "测试应用作用域与失败语义", "club.heiqi.uilib.testing.TestApplicationScenario") {
    classpath = testSupportSources.output
}
tasks.named("check") { dependsOn(coreFontEngineScenario, testApplicationScenario) }
tasks.register("coreSceneCheck") {
    group = "verification"
    description = "完整核心状态、scene、输入、字体合同和应用生命周期验收，不编译MC源"
    // coreStateScenario已按既有模式内嵌SoftwareSceneScenario，避免重复启动。
    dependsOn(coreStateScenario, pointerInputScenario, coreFontEngineScenario, testApplicationScenario)
}
val lwjgl2TestSources = sourceSets.create("lwjgl2Test") {
    java.srcDir("src/lwjgl2Test/java")
    compileClasspath = sourceSets.getByName("main").output + lwjgl2Sources.output + lwjgl2Bindings + testSupportSources.output
    runtimeClasspath = output + compileClasspath + fontUnicodeRuntime
}
tasks.named<JavaCompile>(lwjgl2TestSources.compileJavaTaskName) {
    dependsOn(tasks.named(lwjgl2Sources.classesTaskName))
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.compilerArgs.add("-proc:none")
}
val lwjgl2HostScenario = registerTestApplication("lwjgl2HostScenario", "原LWJGL2默认Display context、正式字体owner和共享GPU像素验收", "club.heiqi.uilib.ui.scene.integration.Lwjgl2HostScenario", useDefaultLauncher = false) {
    dependsOn(tasks.named(lwjgl2TestSources.classesTaskName), "extractHeadlessNatives")
    classpath = lwjgl2TestSources.runtimeClasspath
    val legacyJava = providers.gradleProperty("fontLegacyJava")
    if (legacyJava.isPresent) setExecutable(legacyJava.get())
    else javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
    args(providers.gradleProperty("fontWorldAtlasMode").getOrElse("2d"))
    jvmArgs("-Dfile.encoding=UTF-8", "-Djava.library.path=" + layout.buildDirectory.dir("headless/natives").get().asFile.absolutePath)
}
val nativeFontScenario = registerTestApplication("nativeFontScenario", "Java8独立运行正式字体整形/彩色像素与资源生命周期场景", "club.heiqi.uilib.ui.scene.integration.NativeFontScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output + fontNativeRuntime
    args(file("src/main/resources/assets/qz_uilib/fonts/noto").absolutePath)
}
val nativeFontVisualScenario = registerTestApplication("nativeFontVisualScenario", "回放生产字体定位和FT光栅生成多语言PNG", "club.heiqi.uilib.ui.scene.integration.NativeFontVisualScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output + fontNativeRuntime
    systemProperty("java.awt.headless", "true")
    args(file("src/main/resources/assets/qz_uilib/fonts/noto").absolutePath,
        layout.buildDirectory.dir("font-machine/native").get().asFile.absolutePath)
}
val packagedUnicodeScenario = registerTestApplication("packagedUnicodeScenario", "隔离验证发布包私有ICU的Bidi、字素及泰文字典资源", "club.heiqi.uilib.ui.scene.integration.PackagedUnicodeScenario") {
    dependsOn(tasks.named("testClasses"), tasks.named("shadowJar"))
    classpath = sourceSets.getByName("test").output
    doFirst { args(tasks.named<Jar>("shadowJar").get().archiveFile.get().asFile.absolutePath) }
}
// 正式嵌套绑定必须同时覆盖旧Java8与GTNH的Java25多版本选择。
val packagedFontScenarios = listOf(8, 25).map { javaVersion ->
    registerTestApplication("packagedFontJava$javaVersion", "Java${javaVersion}验证正式包字体整形与彩色光栅", "club.heiqi.uilib.ui.scene.integration.PackagedFontScenario") {
        dependsOn(tasks.named("testClasses"), tasks.named("shadowJar"))
        classpath = sourceSets.getByName("test").output
        javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(javaVersion)) })
        doFirst { args(tasks.named<Jar>("shadowJar").get().archiveFile.get().asFile.absolutePath) }
    }
}
tasks.named("check") { dependsOn(packagedFontScenarios) }

val glBatchScenario = registerTestApplication("glBatchScenario", "独立GLFW上下文对照真实二维纹理及数组回放（需本机GL设备）", "club.heiqi.uilib.ui.scene.integration.GlBatchScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output + fontNativeRuntime
    args(layout.buildDirectory.dir("font-machine/gl").get().asFile.absolutePath)
}
val fontProjectionScenario = registerTestApplication("fontProjectionScenario", "透视采样、矩阵快照与有界光栅档位验收", "club.heiqi.uilib.ui.scene.integration.FontProjectionScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime
    args("cpu")
}
val fontContextGlScenario = registerTestApplication("fontContextGlScenario", "Core与Compatibility有效字体上下文捕获（需要设备）", "club.heiqi.uilib.ui.scene.integration.FontProjectionScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime
}
tasks.named("check") { dependsOn(fontProjectionScenario) }
val vanillaTextScenario = registerTestApplication("vanillaTextScenario", "原版文字统一测量、安全裁剪换行、混淆与有界热缓存验收", "club.heiqi.uilib.ui.scene.integration.VanillaTextScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime
}
val fontHostOrderScenario = registerTestApplication("fontHostOrderScenario", "宿主延期票据保序、异常清理与资源lease验收", "club.heiqi.uilib.ui.scene.integration.FontHostOrderScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime
}
val fontDeferredStateScenario = registerTestApplication("fontDeferredStateScenario", "Core与Compatibility延期文字历史状态快照验收（需要设备）", "club.heiqi.uilib.ui.scene.integration.FontDeferredStateScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime
}
val vanillaFontGlScenario = registerTestApplication("vanillaFontGlScenario", "原版世界文字透视、光照、历史状态与图集视图验收（需要设备）", "club.heiqi.uilib.ui.scene.integration.VanillaFontGlScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime
    args(providers.gradleProperty("fontWorldAtlasMode").getOrElse("2d"))
}
val fontPlanSamplingScenario = registerTestApplication("fontPlanSamplingScenario", "近裁面可见墨迹、保守采样封顶与GUI快速路径验收", "club.heiqi.uilib.ui.scene.integration.FontPlanSamplingScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime
}
val vanillaFontThreadScenario = registerTestApplication("vanillaFontThreadScenario", "真实安装后Splash线程十个标准入口不接管，主线程保留接管", "club.heiqi.uilib.ui.scene.integration.VanillaFontThreadScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("test").runtimeClasspath
}
tasks.named("check") { dependsOn(vanillaTextScenario, fontHostOrderScenario, fontPlanSamplingScenario, vanillaFontThreadScenario) }
val attributedGlyphGlScenario = registerTestApplication("attributedGlyphGlScenario", "整簇属性字形采样、彩色与装饰真实GL验收（需要设备）", "club.heiqi.uilib.ui.scene.integration.AttributedGlyphGlScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime
    args(layout.buildDirectory.dir("font-machine/attributed-gl").get().asFile.absolutePath)
}
val presentationEpochScenario = registerTestApplication("presentationEpochScenario", "多消费者字形需求与总帧上传预算（需要设备）", "club.heiqi.uilib.ui.scene.integration.PresentationEpochScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime
}
val clientFontServicesScenario = registerTestApplication("clientFontServicesScenario", "共享字体owner、原生framebuffer scene与输入资源生命周期（需要设备）", "club.heiqi.uilib.ui.scene.integration.ClientFontServicesScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime
    args(layout.buildDirectory.dir("font-machine/client").get().asFile.absolutePath)
}
val glyphPipelineScenario = registerTestApplication("glyphPipelineScenario", "真实共享定位、后台光栅、有界图集和GL回放对照（需要设备）", "club.heiqi.uilib.ui.scene.integration.GlyphPipelineScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime
    args(layout.buildDirectory.dir("font-machine/glyph").get().asFile.absolutePath)
}
val fontTextScenario = registerTestApplication("fontTextScenario", "真实共享Unicode/Bidi/复杂脚本与Emoji17全量候选匹配", "club.heiqi.uilib.ui.scene.integration.FontTextScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime
    args(projectDir.absolutePath)
}
val attributedTextScenario = registerTestApplication("attributedTextScenario", "整簇属性文本、格式来源与逻辑采样分离验收", "club.heiqi.uilib.ui.scene.integration.AttributedTextScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime
}
val glyphRasterStyleScenario = registerTestApplication("glyphRasterStyleScenario", "真实原生字形合成样式、墨迹范围与有界资源验收", "club.heiqi.uilib.ui.scene.integration.GlyphRasterStyleScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime
    args(file("src/main/resources/assets/qz_uilib/fonts").absolutePath,
        layout.buildDirectory.dir("font-machine/raster-style").get().asFile.absolutePath)
}
tasks.named("check") { dependsOn(nativeFontScenario, nativeFontVisualScenario, packagedUnicodeScenario, fontTextScenario, glyphRasterStyleScenario, attributedTextScenario) }


// 显式人工测试附加包：与发布包分离，不进入 assemble/publish，真实实例自动安装 /qzcore。
val manualTestSources = sourceSets.create("manualTest") {
    compileClasspath = sourceSets.getByName("main").output + sourceSets.getByName("main").compileClasspath
}
tasks.named<JavaCompile>(manualTestSources.compileJavaTaskName) {
    // 附加入口只有 Java 8 语法，不加载从业务 classpath 发现的编译处理器。
    options.compilerArgs.add("-proc:none")
    options.annotationProcessorPath = tasks.named<JavaCompile>("compileJava").get().options.annotationProcessorPath
}
val manualTestDevJar by tasks.registering(Zip::class) {
    dependsOn(tasks.named("classes"), tasks.named("manualTestClasses"))
    archiveBaseName.set("qz_uilib_manualtest")
    archiveVersion.set("core-loop-1")
    archiveClassifier.set("dev")
    archiveExtension.set("jar")
    destinationDirectory.set(layout.buildDirectory.dir("manual-test"))
    from(sourceSets.getByName("main").output) { include("club/heiqi/uilib/internal/devtools/**") }
    from(manualTestSources.output)
}
val manualTestJar by tasks.registering(ReobfuscatedJar::class) {
    dependsOn(manualTestDevJar, tasks.named("reobfJar"))
    val production = tasks.named<ReobfuscatedJar>("reobfJar")
    inputJar.set(manualTestDevJar.flatMap { it.archiveFile })
    archiveBaseName.set("qz_uilib_manualtest")
    archiveVersion.set("core-loop-1")
    archiveClassifier.set("")
    destinationDirectory.set(layout.buildDirectory.dir("manual-test"))
    mcVersion.set(production.flatMap { it.mcVersion })
    srg.set(production.flatMap { it.srg })
    fieldCsv.set(production.flatMap { it.fieldCsv })
    methodCsv.set(production.flatMap { it.methodCsv })
    exceptorCfg.set(production.flatMap { it.exceptorCfg })
    recompMcJar.set(production.flatMap { it.recompMcJar })
    referenceClasspath.from(sourceSets.getByName("main").compileClasspath, sourceSets.getByName("main").output)
    extraSrgEntries.set(emptyList<String>())
    doLast {
        ZipFile(archiveFile.get().asFile).use { zip ->
            check(zip.getEntry("club/heiqi/uilib/manualtest/ManualTestMod.class") != null) { "人工测试入口缺失" }
            check(zip.getEntry("club/heiqi/uilib/internal/devtools/CoreLoopScreen.class") != null) { "人工测试面板缺失" }
            check(zip.getEntry("club/heiqi/uilib/ui/reactive/Signal.class") == null) { "附加包不能重复打入核心类" }
        }
    }
}


// ============================================================================
// 内部开发工具（main 域 club.heiqi.uilib.internal.devtools）
// 含 headless 出图、scene 测试场地（playground）、磨玻璃实验室（glass）、网络自检端点
// （NetSelfCheck* / NetRuntimeSelfChecks）与开发环境完整命令（QzUiLibClientCommand /
// DevToolsClientBootstrap）。定位与顶层语义见
// docs/历史报告/规划与设计/规划-headless运行时与验收设施.md；
// 硬约束（2026-09-17 用户裁定，2026-09-18 扩到整包）：不影响打包体积——类只存在于
// build/classes，不进入任何产物。发布产物内保留的 /qzuilib 通道命令在 client.command，
// 由 ClientProxy 按「开发环境 + 类存在」双重探测择一注册。
// ============================================================================
val internalDevToolsPackagePath = "club/heiqi/uilib/internal/devtools"

// 1) Jar 型产物统一排除（jar / shadowJar / sourcesJar / apiJar）。
//    reobfJar 是 ReobfuscatedJar（非 Jar 子类、无 exclude），吃的是 dev jar 产物，排除随输入传播——由下面的门禁实测确认。
tasks.withType<Jar>().configureEach {
    exclude("$internalDevToolsPackagePath/**")
    // Forge1.7.10会扫描每个class，ASM5拒绝依赖的Java9+多版本实现；Java8使用的根实现完整保留。
    exclude("META-INF/versions/**")
}
// apiJar 用的是旧类型 org.gradle.jvm.tasks.Jar（不是 bundling.Jar 的子类），必须单独排除——
// 这一处漏排正是第一次门禁跑出来的真实缺陷（apiJar 里混进了 headless 包）。
tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
    if (name != "manualTestJar") exclude("$internalDevToolsPackagePath/**")
}

// 2) 门禁：逐个打开产物断言不含该包。排除是意图，门禁才是保证；
//    校验集合按 AbstractArchiveTask 全量枚举 + 显式依赖本仓产物任务，构建链新增打包任务不会静默漏检。
val guardedArchiveTasks = listOf("jar", "shadowJar", "sourcesJar", "apiJar", "reobfJar")
val verifyDevToolsNotPackaged by tasks.registering {
    group = "verification"
    description = "校验内部开发工具（internal.devtools 整包）未进入任何打包产物"
    dependsOn(guardedArchiveTasks)
    val guarded: List<Provider<RegularFile>> = guardedArchiveTasks.map { name ->
        tasks.named(name, AbstractArchiveTask::class.java).flatMap { task -> task.archiveFile }
    }
    val allArchives: Provider<List<File>> = provider {
        tasks.withType(AbstractArchiveTask::class.java).toList().filter { it.name != "manualTestDevJar" && it.name != "manualTestJar" }.map { task -> task.archiveFile.get().asFile }
    }
    val forbidden = "$internalDevToolsPackagePath/"
    doLast {
        val guardedFiles = guarded.map { archive -> archive.get().asFile }
        val missing = guardedFiles.count { file -> !file.isFile }
        if (missing > 0) {
            throw GradleException("verifyDevToolsNotPackaged: 有 " + missing + " 个产物未生成，门禁失效")
        }
        val extras = allArchives.get().filter { file -> file.isFile && !guardedFiles.contains(file) }
        (guardedFiles + extras).forEach { file ->
            var hit: String? = null
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entryName = entries.nextElement().name
                    if (entryName.startsWith(forbidden) || entryName.startsWith("club/heiqi/uilib/testing/")) {
                        hit = entryName
                        break
                    }
                }
            }
            if (hit != null) {
                throw GradleException("内部开发工具进入了打包产物 " + file.name + "：" + hit)
            }
            logger.lifecycle("verifyDevToolsNotPackaged: " + file.name + " 不含 " + forbidden)
        }
    }
}
tasks.named("check") { dependsOn(verifyDevToolsNotPackaged) }

// ============================================================================
// 3) headless 运行期供给：LWJGL2 主 jar 与 natives 只在 compileClasspath 上（真机由 MC 客户端供给），
//    headless 直启必须自带。这里从既有 compileClasspath 提取，不新增依赖声明：
//    直启 classpath = main 输出（含 headless 类）+ 真 LWJGL2（须排在 lwjgl3ify shim 之前）+ 运行期依赖。
// ============================================================================
val headlessRuntimeDir = layout.buildDirectory.dir("headless")
val mainSourceOutput: FileCollection = sourceSets.getByName("main").output

val headlessLwjglRuntimeJars: Provider<List<File>> = configurations.named("compileClasspath").map { cfg ->
    cfg.files.filter { file ->
        val name = file.name
        name.startsWith("lwjgl-") || name.startsWith("lwjgl_util-")
    }.sortedBy { file -> file.name }
}

val headlessNativeJars: Provider<List<File>> = configurations.named("compileClasspath").map { cfg ->
    cfg.files.filter { file -> file.name.startsWith("lwjgl-platform-") }.sortedBy { file -> file.name }
}

val extractHeadlessNatives by tasks.registering(Sync::class) {
    group = "headless"
    description = "解压 LWJGL2 natives 到 build/headless/natives（直启用 -Djava.library.path）"
    into(headlessRuntimeDir.map { dir -> dir.dir("natives") })
    from(headlessNativeJars.map { jars -> jars.map { jar -> zipTree(jar) } })
    // 三个平台的 natives jar 各自带 META-INF/MANIFEST.MF，解压到同一目录必然重复。
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

/**
 * 导出直启 classpath 的任务类型：用任务属性（ConfigurableFileCollection / RegularFileProperty）承载输入输出，
 * 而不是捕获 provider 到 doLast——后者在配置缓存下不可序列化（实测报 NamedDomainObjectProvider 赋值失败）。
 */
abstract class ExportHeadlessClasspath : DefaultTask() {
    @get:InputFiles
    abstract val classpath: ConfigurableFileCollection
    @get:InputFiles
    abstract val fullClasspath: ConfigurableFileCollection
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty
    @get:Input
    abstract val javaExecutable: Property<String>

    @TaskAction
    fun export() {
        val directory = outputDirectory.get().asFile
        directory.mkdirs()
        val unique = LinkedHashSet<File>(classpath.files)
        val classpathFile = File(directory, "classpath.txt")
        classpathFile.writeText(unique.joinToString(File.pathSeparator))

        // 启动器：把「一条命令出图」做成设施的一部分，而不是让 agent 自己拼 classpath。
        // Java 的 @argfile 机制让超长 classpath 不必挤进 shell 命令行；参数文件按当前平台生成。
        val argFile = File(directory, "shot-args.txt")
        argFile.writeText(buildString {
            append("-Djava.library.path=").append(File(directory, "natives").absolutePath).append('\n')
            append("-Xmx2g").append('\n')
            append("-cp").append('\n')
            append(unique.joinToString(File.pathSeparator)).append('\n')
            append("club.heiqi.uilib.internal.devtools.headless.HeadlessShotMain").append('\n')
        })
        val java = javaExecutable.get()
        writeLauncher(directory, java, "shot-args.txt", "qz-shot.bat")

        // 完整开发类路径：最小集优先，再补 compileClasspath 其余（MC/Forge 等）。
        // 用途：页面渲染一旦触及 MC 类型（IChatComponent / ChatComponentText），最小集链接不上——
        // 这不是「facility 坏了」，而是「这条页面路径本来就要 MC 类型」，故单独供给而不是把最小集撑大。
        val fullOrdered = LinkedHashSet<File>()
        fullOrdered.addAll(unique)
        fullOrdered.addAll(fullClasspath.files)
        File(directory, "classpath-full.txt").writeText(fullOrdered.joinToString(File.pathSeparator))
        File(directory, "shot-args-full.txt").writeText(buildString {
            append("-Djava.library.path=").append(File(directory, "natives").absolutePath).append('\n')
            append("-Xmx2g").append('\n')
            append("-cp").append('\n')
            append(fullOrdered.joinToString(File.pathSeparator)).append('\n')
            append("club.heiqi.uilib.internal.devtools.headless.HeadlessShotMain").append('\n')
        })
        writeLauncher(directory, java, "shot-args-full.txt", "qz-shot-full.bat")
        logger.lifecycle("exportHeadlessClasspath: " + unique.size + " 项 -> " + classpathFile
                + "（启动器 qz-shot.bat / qz-shot.sh / qz-shot-full.bat）")
    }

    /** 写一个平台启动器（Windows .bat 与 POSIX .sh 各一份）。 */
    private fun writeLauncher(directory: File, java: String, argFileName: String, batName: String) {
        File(directory, batName).writeText(buildString {
            append("@echo off\r\n")
            append("rem headless 出图启动器（由 exportHeadlessClasspath 生成）\r\n")
            append('"').append(java).append('"').append(" @\"%~dp0").append(argFileName).append("\" %*\r\n")
        })
        val shName = batName.replace(".bat", ".sh")
        val shell = File(directory, shName)
        shell.writeText(buildString {
            append("#!/bin/sh\n")
            append("# headless 出图启动器（由 exportHeadlessClasspath 生成）\n")
            append("exec \"").append(java).append("\" \"@\$(dirname \"\$0\")/").append(argFileName)
                    .append("\" \"\$@\"\n")
        })
        shell.setExecutable(true)
    }
}

// 4) 对拍测试供给：把「直启 classpath 文件」与「natives 目录」交给 test JVM。
//    测试侧走进程外直启（与 agent 真实使用路径一致），因此 test JVM 自身不需要 LWJGL2 依赖，
//    也避开了 lwjgl3ify shim 与真 LWJGL2 在同一条 classpath 上的先后之争。
//    注入缺失时直启类测试会 Assume 跳过：构建期在这里 fail 一次，避免整类门禁被静默跳过
//    （独立复核实测：删掉这两条 systemProperty 后构建仍 SUCCESSFUL，只有 XML 的 skipped 属性变了）。
tasks.withType<Test>().configureEach {
    dependsOn(tasks.named("exportHeadlessClasspath"))
    val headlessClasspathFile = headlessRuntimeDir.get().file("classpath.txt").asFile
    val headlessNativesDir = headlessRuntimeDir.get().dir("natives").asFile
    doFirst {
        check(headlessClasspathFile.isFile && headlessClasspathFile.length() > 0L) {
            "headless 直启 classpath 缺失或为空：${headlessClasspathFile} —— exportHeadlessClasspath 未产出，直启类测试会被整类跳过"
        }
        check(headlessNativesDir.isDirectory) {
            "headless natives 目录缺失：" + headlessNativesDir
        }
        // chat/hud 的玻璃档门禁走完整集（classpath-full.txt）直启：缺失时那条判据会 Assume 跳过，
        // 与最小集同理 —— 构建期 fail 一次，别让门禁静默变绿。
        val headlessFullClasspathFile = headlessRuntimeDir.get().file("classpath-full.txt").asFile
        check(headlessFullClasspathFile.isFile && headlessFullClasspathFile.length() > 0L) {
            "headless 完整集 classpath 缺失或为空：${headlessFullClasspathFile} —— chat/hud 的玻璃档门禁会被静默跳过"
        }
    }
    systemProperty("qz.headless.classpathFile", headlessClasspathFile.absolutePath)
    systemProperty("qz.headless.nativesDir", headlessNativesDir.absolutePath)
}

val exportHeadlessClasspath by tasks.registering(ExportHeadlessClasspath::class) {
    group = "headless"
    description = "导出 headless 直启 classpath（main 输出 + 真 LWJGL2 + 运行期依赖）"
    dependsOn(tasks.named("classes"), tasks.named("extractHeadlessNatives"))
    // 顺序即优先级：main 输出（含 headless 类）→ 真 LWJGL2（必须排在 lwjgl3ify shim 之前）→ 运行期依赖。
    // 注意必须用 sourceSets.main.output，而不是 classes 任务的 outputs——classes 是生命周期任务，outputs 为空。
    classpath.from(mainSourceOutput)
    classpath.from(headlessLwjglRuntimeJars)
    classpath.from(configurations.named("runtimeClasspath"))
    fullClasspath.from(configurations.named("compileClasspath"))
    // MC 类不在 compileClasspath 上：RFG 把重编译后的 Minecraft 放在 patchedMc 源集输出里，
    // mcLauncher 提供启动期类型。渲染触及 MC 类型的页面（chat 探针的 IChatComponent）必须带上它们。
    fullClasspath.from(sourceSets.getByName("patchedMc").output)
    fullClasspath.from(sourceSets.getByName("mcLauncher").output)
    outputDirectory.set(headlessRuntimeDir)
    javaExecutable.set(File(System.getProperty("java.home"), "bin/java").absolutePath)
}
