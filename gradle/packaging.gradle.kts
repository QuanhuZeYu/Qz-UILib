import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.SourceSetContainer
import java.util.zip.ZipFile

val sourceSets = extensions.getByType<SourceSetContainer>()
val gallery = configurations.getByName("uilibGallery")
val verification = configurations.getByName("uilibCoreVerification")
val manual = sourceSets.create("manualTest") {
    compileClasspath = sourceSets.getByName("main").output + sourceSets.getByName("main").compileClasspath + gallery + configurations.getByName("uilibMedia8") + verification
}
tasks.named<JavaCompile>(manual.compileJavaTaskName) {
    options.compilerArgs.add("-proc:none")
    options.annotationProcessorPath = tasks.named<JavaCompile>("compileJava").get().options.annotationProcessorPath
}
val manualTestDevJar by tasks.registering(Zip::class) {
    dependsOn("classes", "manualTestClasses")
    archiveBaseName.set("qz_uilib_manualtest"); archiveVersion.set(project.version.toString()); archiveClassifier.set("dev"); archiveExtension.set("jar")
    destinationDirectory.set(layout.buildDirectory.dir("manual-test"))
    from(manual.output)
    // 独立消费包的 MIT 按来源目录保留，外层根目录保留宿主 LICENSE。
    from(provider { zipTree(gallery.singleFile) }) {
        exclude("META-INF/MANIFEST.MF")
        filesMatching("LICENSE") { path = "META-INF/licenses/core-gallery/LICENSE" }
    }
    // 核心验证服务、数据及第三方通知归人工广场附加包。
    from(provider { zipTree(verification.singleFile) }) {
        exclude("META-INF/MANIFEST.MF")
        filesMatching("LICENSE") { path = "META-INF/licenses/core-verification/LICENSE" }
    }
}
val manualTestJar by tasks.registering(ReobfuscatedJar::class) {
    dependsOn(manualTestDevJar, "reobfJar")
    val production = tasks.named<ReobfuscatedJar>("reobfJar")
    inputJar.set(manualTestDevJar.flatMap { it.archiveFile })
    archiveBaseName.set("qz_uilib_manualtest"); archiveVersion.set(project.version.toString()); archiveClassifier.set("")
    destinationDirectory.set(layout.buildDirectory.dir("manual-test"))
    mcVersion.set(production.flatMap { it.mcVersion }); srg.set(production.flatMap { it.srg })
    fieldCsv.set(production.flatMap { it.fieldCsv }); methodCsv.set(production.flatMap { it.methodCsv })
    exceptorCfg.set(production.flatMap { it.exceptorCfg }); recompMcJar.set(production.flatMap { it.recompMcJar })
    referenceClasspath.from(sourceSets.getByName("main").compileClasspath, sourceSets.getByName("main").output)
    extraSrgEntries.set(emptyList<String>())
    doLast {
        ZipFile(archiveFile.get().asFile).use { zip ->
            listOf("club/heiqi/uilib/manualtest/ManualTestMod.class", "club/heiqi/uilib/internal/devtools/CoreLoopScreen.class", "club/heiqi/uilib/internal/devtools/PerformanceScreen.class", "club/heiqi/uilib/testing/gallery/TestGallery.class", "club/heiqi/uilib/testing/gallery/mail/MailboxPage.class", "club/heiqi/uilib/testing/gallery/PerformanceGallery.class", "club/heiqi/uilib/testing/host/LocalMediaFiles.class", "uilib-media/segment.ts", "META-INF/services/club.heiqi.uilib.testing.gallery.GalleryChecks", "club/heiqi/uilib/testing/gallery/verification/CoreGalleryChecks.class").forEach { check(zip.getEntry(it) != null) { "人工附加包缺少 $it" } }
            listOf("LICENSE", "META-INF/licenses/core-gallery/LICENSE", "META-INF/licenses/core-verification/LICENSE", "META-INF/THIRD-PARTY-NOTICES.md", "assets/qz_uilib/unicode/emoji17/LICENSE.txt", "assets/qz_uilib/math/catalog/LICENSE-KaTeX.txt").forEach { check(zip.getEntry(it) != null) { "人工附加包缺少许可或关联通知：$it" } }
            check(zip.getEntry("club/heiqi/uilib/ui/reactive/Signal.class") == null) { "附加包不能重复打入核心" }
            val removed = listOf("CoreLoopPage", "CoreLocalizationPage", "CoreRichTextPage", "CoreImagePage", "CoreMathPage", "CoreDocumentPage", "UsageScenarios", "ProjectSettings", "TextScenarios", "MediaScenarios", "InteractionScenarios", "ResourceScenarios", "GallerySamples")
            check(zip.entries().asSequence().none { entry -> removed.any { entry.name.startsWith("club/heiqi/uilib/testing/gallery/$it") } })
            // 附加包保留字体、图像与语言样本；页面类按上方清单筛选。
        }
    }
}
tasks.register("galleryBuild") { group = "build"; description = "显式装配 Minecraft 1.7.10 人工广场"; dependsOn(manualTestJar) }
// 外层 Jar 与根 MIT 许可同步；第三方许可随各自组件保留。
// 按两种 Jar 任务共用的 CopySpec 配置许可，适配 Gradle 9 的类型层次。
val licenseFile = rootProject.file("LICENSE")
listOf("jar", "shadowJar", "sourcesJar", "apiJar", "reobfJar", "manualTestDevJar").forEach { name ->
    tasks.named(name) { (this as org.gradle.api.file.CopySpec).from(licenseFile) }
}

val forbidden = "club/heiqi/uilib/internal/devtools/"
tasks.withType<Jar>().configureEach { exclude("$forbidden**", "META-INF/versions/**") }
tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach { exclude("$forbidden**") }
tasks.withType<ShadowJar>().configureEach {
    if (name == "shadowJar") listOf("com.ibm.icu", "org.yaml.snakeyaml", "org.commonmark", "org.jsoup", "org.nibor.autolink").forEach { relocate(it, "club.heiqi.uilib.shadow.$it") }
}
// sources/api 仅携带本仓宿主源码；核心与平台由各自正式产物持有。
val guardedNames = listOf("jar", "shadowJar", "sourcesJar", "apiJar", "reobfJar")
val archives = guardedNames.map { tasks.named(it, AbstractArchiveTask::class.java).flatMap { task -> task.archiveFile } }
tasks.register("verifyDevToolsNotPackaged") {
    group = "verification"; dependsOn(guardedNames); inputs.files(archives)
    doLast {
        archives.forEach { artifact ->
            ZipFile(artifact.get().asFile).use { zip ->
                val hit = zip.entries().asSequence().firstOrNull { it.name.startsWith(forbidden) || it.name.startsWith("club/heiqi/uilib/testing/") || it.name.startsWith("assets/qz_uilib_test/") || it.name.startsWith("assets/qz_uilib/gallery/") || it.name.startsWith("uilib/localization/") }
                check(hit == null) { "正式产物混入测试代码或资源：$hit" }
            }
        }
    }
}
tasks.named("check") { dependsOn("verifyDevToolsNotPackaged") }
