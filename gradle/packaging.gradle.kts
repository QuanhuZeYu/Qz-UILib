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
    archiveBaseName.set("qz_uilib_manualtest"); archiveVersion.set("core-loop-1"); archiveClassifier.set("dev"); archiveExtension.set("jar")
    destinationDirectory.set(layout.buildDirectory.dir("manual-test"))
    from(manual.output)
    from(provider { zipTree(gallery.singleFile) }) { exclude("META-INF/MANIFEST.MF") }
    from(provider { zipTree(verification.singleFile) }) { include("club/heiqi/uilib/testing/host/LocalMediaFiles*.class") }
}
val manualTestJar by tasks.registering(ReobfuscatedJar::class) {
    dependsOn(manualTestDevJar, "reobfJar")
    val production = tasks.named<ReobfuscatedJar>("reobfJar")
    inputJar.set(manualTestDevJar.flatMap { it.archiveFile })
    archiveBaseName.set("qz_uilib_manualtest"); archiveVersion.set("core-loop-1"); archiveClassifier.set("")
    destinationDirectory.set(layout.buildDirectory.dir("manual-test"))
    mcVersion.set(production.flatMap { it.mcVersion }); srg.set(production.flatMap { it.srg })
    fieldCsv.set(production.flatMap { it.fieldCsv }); methodCsv.set(production.flatMap { it.methodCsv })
    exceptorCfg.set(production.flatMap { it.exceptorCfg }); recompMcJar.set(production.flatMap { it.recompMcJar })
    referenceClasspath.from(sourceSets.getByName("main").compileClasspath, sourceSets.getByName("main").output)
    extraSrgEntries.set(emptyList<String>())
    doLast {
        ZipFile(archiveFile.get().asFile).use { zip ->
            listOf("club/heiqi/uilib/manualtest/ManualTestMod.class", "club/heiqi/uilib/internal/devtools/CoreLoopScreen.class", "club/heiqi/uilib/internal/devtools/PerformanceScreen.class", "club/heiqi/uilib/testing/gallery/TestGallery.class", "club/heiqi/uilib/testing/gallery/mail/MailboxPage.class", "club/heiqi/uilib/testing/gallery/PerformanceGallery.class", "club/heiqi/uilib/testing/host/LocalMediaFiles.class", "uilib-media/segment.ts").forEach { check(zip.getEntry(it) != null) { "人工附加包缺少 $it" } }
            check(zip.getEntry("club/heiqi/uilib/ui/reactive/Signal.class") == null) { "附加包不能重复打入核心" }
            val removed = listOf("CoreLoopPage", "CoreLocalizationPage", "CoreRichTextPage", "CoreImagePage", "CoreMathPage", "CoreDocumentPage", "UsageScenarios", "ProjectSettings", "TextScenarios", "MediaScenarios", "InteractionScenarios", "ResourceScenarios", "GallerySamples")
            check(zip.entries().asSequence().none { entry -> removed.any { entry.name.startsWith("club/heiqi/uilib/testing/gallery/$it") } })
            check(zip.getEntry("assets/qz_uilib_test/textures/sample.png") == null)
            check(zip.getEntry("uilib/localization/core-loop/zh_CN.properties") == null)
        }
    }
}
tasks.register("galleryBuild") { group = "build"; description = "显式装配 Minecraft 1.7.10 人工广场"; dependsOn(manualTestJar) }
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
