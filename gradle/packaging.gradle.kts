import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.io.File
import java.util.zip.ZipFile

import org.gradle.api.tasks.SourceSetContainer

val sourceSets = extensions.getByType<SourceSetContainer>()
// 显式人工测试附加包：与发布包分离，不进入 assemble/publish，真实实例自动安装 /qzcore。
val mediaManualArtifact = tasks.named<Jar>("mediaAdapterLegacyJar").flatMap { it.archiveFile }
val manualTestSources = sourceSets.create("manualTest") {
    java.srcDir("src/testHost/java")
    compileClasspath = sourceSets.getByName("main").output + sourceSets.getByName("core").output + sourceSets.getByName("fontAdapter").output + sourceSets.getByName("gallery").output + files(mediaManualArtifact) + sourceSets.getByName("main").compileClasspath
}
tasks.named<JavaCompile>(manualTestSources.compileJavaTaskName) {
    dependsOn("mediaAdapterLegacyJar")
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
    from(manualTestSources.output)
    from(sourceSets.getByName("gallery").output)
}
// 人工MC运行只追加独立legacy适配Jar；私有FFmpeg/OpenAL仍在其隔离运行时，不进入宿主顶层。
listOf("runClient", "runServer").forEach { name ->
    tasks.named<JavaExec>(name) { dependsOn("mediaAdapterLegacyJar"); classpath += files(mediaManualArtifact) }
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
            check(zip.getEntry("club/heiqi/uilib/testing/gallery/TestGallery.class") != null) { "共享测试广场缺失" }
            check(zip.getEntry("club/heiqi/uilib/testing/gallery/mail/MailboxPage.class") != null) { "收件箱参考案例缺失" }
            check(zip.getEntry("club/heiqi/uilib/testing/gallery/PerformanceGallery.class") != null) { "专用性能广场缺失" }
            check(zip.getEntry("club/heiqi/uilib/internal/devtools/PerformanceScreen.class") != null) { "MC性能宿主入口缺失" }
            val removedPages = listOf("CoreLoopPage", "CoreLocalizationPage", "CoreRichTextPage", "CoreImagePage", "CoreMathPage", "CoreDocumentPage",
                "UsageScenarios", "ProjectSettings", "TextScenarios", "MediaScenarios", "InteractionScenarios", "ResourceScenarios", "GallerySamples")
            check(zip.entries().asSequence().none { entry -> removedPages.any { entry.name.startsWith("club/heiqi/uilib/testing/gallery/$it") } }) { "附加包残留旧测试页面" }
            check(zip.getEntry("assets/qz_uilib_test/textures/sample.png") == null) { "附加包残留旧广场图片" }
            check(zip.getEntry("uilib/localization/core-loop/zh_CN.properties") == null) { "附加包残留旧广场语言样本" }
            check(zip.getEntry("club/heiqi/uilib/ui/reactive/Signal.class") == null) { "附加包不能重复打入核心类" }
        }
    }
}


// ============================================================================
// 内部开发工具（club.heiqi.uilib.internal.devtools）从正式产物排除；
// 人工验收包在本脚本前段单独装配，不复制生产核心。
// 测试应用与发行隔离要求见 specs/verification/2026-10-07-uilib-testing-spec.md。
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
// 配套源码覆盖各生产职责；MC已包含带标记的LWJGL3原文件，避免加入同名桌面变体。
val companionSources = listOf("core", "fontAdapter", "glRenderer", "lwjgl2", "desktop").map { sourceSets.getByName(it) }
tasks.named<Jar>("sourcesJar") {
    dependsOn("packageMediaBindings")
    companionSources.forEach { from(it.allSource) }
    from(sourceSets.getByName("mediaAdapter").allSource)
    exclude("assets/qz_uilib/runtime/font/**", "assets/qz_uilib/runtime/media/**")
}
tasks.named<org.gradle.jvm.tasks.Jar>("apiJar") {
    companionSources.forEach { from(it.allJava, it.output.classesDirs) }
    from(sourceSets.getByName("mediaAdapter").allJava, tasks.named<JavaCompile>("compileMediaAdapterLegacyJava").flatMap { it.destinationDirectory })
}
val guardedArchiveTasks = listOf("jar", "shadowJar", "sourcesJar", "apiJar", "reobfJar", "coreJar", "fontAdapterJar", "coreLegacyJar", "fontAdapterLegacyJar", "desktopJar", "mediaAdapterJar", "mediaAdapterLegacyJar")
val verifyDevToolsNotPackaged by tasks.registering {
    group = "verification"
    description = "校验内部开发工具（internal.devtools 整包）未进入任何打包产物"
    dependsOn(guardedArchiveTasks)
    val guarded: List<Provider<RegularFile>> = guardedArchiveTasks.map { name ->
        tasks.named(name, AbstractArchiveTask::class.java).flatMap { task -> task.archiveFile }
    }
    val allArchives: Provider<List<File>> = provider {
        tasks.withType(AbstractArchiveTask::class.java).toList().filter { it.name !in setOf("manualTestDevJar", "manualTestJar", "desktopTestJar", "mediaTestJar", "mediaTestLegacyJar", "lwjgl2TestJar") }.map { task -> task.archiveFile.get().asFile }
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
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val entryName = entry.name
                    if (entryName.startsWith(forbidden) || entryName.startsWith("club/heiqi/uilib/testing/") || (entryName.startsWith("assets/qz_uilib_test/") || entryName.startsWith("assets/qz_uilib/gallery/")) || entryName.startsWith("uilib/localization/")) {
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

// 数学能力依赖核心持有的正式字体；版权与完整许可必须与原资源一起进入 coreJar。
// MC 重混淆产物依赖该发行组成部分，单独生成 MC 成品也保留资源门禁。
tasks.named("reobfJar") { dependsOn("coreLegacyJar") }
listOf("coreJar", "coreLegacyJar").forEach { taskName -> tasks.named<Jar>(taskName) {
    // 固定到声明的资源目录；执行期不调用脚本的 Project.file，保持桌面启动可缓存。
    val coreResources = layout.projectDirectory.dir("src/core/resources")
    doLast {
        ZipFile(archiveFile.get().asFile).use { zip ->
            listOf("STIXTwoMath-Regular.otf", "OFL.txt", "SOURCE.md").forEach { name ->
                val resource = "assets/qz_uilib/fonts/math/$name"
                val entry = zip.getEntry(resource) ?: error("数学字体发行资源缺失：$resource")
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                check(bytes.contentEquals(coreResources.file(resource).asFile.readBytes())) { "数学字体发行资源被改写：$resource" }
            }
        }
    }
} }
