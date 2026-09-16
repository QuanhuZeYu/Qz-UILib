
import java.io.File
import java.util.zip.ZipFile

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
}

// ============================================================================
// headless 设施（main 域 club.heiqi.uilib.internal.devtools.headless）
// 定位与顶层语义见 docs/历史报告/规划与设计/规划-headless运行时与验收设施.md；
// 硬约束（2026-09-17 用户裁定）：不影响打包体积——类只存在于 build/classes，不进入任何产物。
// ============================================================================
val headlessPackagePath = "club/heiqi/uilib/internal/devtools/headless"

// 1) Jar 型产物统一排除（jar / shadowJar / sourcesJar / apiJar）。
//    reobfJar 是 ReobfuscatedJar（非 Jar 子类、无 exclude），吃的是 dev jar 产物，排除随输入传播——由下面的门禁实测确认。
tasks.withType<Jar>().configureEach {
    exclude("$headlessPackagePath/**")
}
// apiJar 用的是旧类型 org.gradle.jvm.tasks.Jar（不是 bundling.Jar 的子类），必须单独排除——
// 这一处漏排正是第一次门禁跑出来的真实缺陷（apiJar 里混进了 headless 包）。
tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
    exclude("$headlessPackagePath/**")
}

// 2) 门禁：逐个打开产物断言不含该包。排除是意图，门禁才是保证；
//    校验集合按 AbstractArchiveTask 全量枚举 + 显式依赖本仓产物任务，构建链新增打包任务不会静默漏检。
val guardedArchiveTasks = listOf("jar", "shadowJar", "sourcesJar", "apiJar", "reobfJar")
val verifyHeadlessNotPackaged by tasks.registering {
    group = "verification"
    description = "校验 headless 设施未进入任何打包产物"
    dependsOn(guardedArchiveTasks)
    val guarded: List<Provider<RegularFile>> = guardedArchiveTasks.map { name ->
        tasks.named(name, AbstractArchiveTask::class.java).flatMap { task -> task.archiveFile }
    }
    val allArchives: Provider<List<File>> = provider {
        tasks.withType(AbstractArchiveTask::class.java).toList().map { task -> task.archiveFile.get().asFile }
    }
    val forbidden = "$headlessPackagePath/"
    doLast {
        val guardedFiles = guarded.map { archive -> archive.get().asFile }
        val missing = guardedFiles.count { file -> !file.isFile }
        if (missing > 0) {
            throw GradleException("verifyHeadlessNotPackaged: 有 " + missing + " 个产物未生成，门禁失效")
        }
        val extras = allArchives.get().filter { file -> file.isFile && !guardedFiles.contains(file) }
        (guardedFiles + extras).forEach { file ->
            var hit: String? = null
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entryName = entries.nextElement().name
                    if (entryName.startsWith(forbidden)) {
                        hit = entryName
                        break
                    }
                }
            }
            if (hit != null) {
                throw GradleException("headless 设施进入了打包产物 " + file.name + "：" + hit)
            }
            logger.lifecycle("verifyHeadlessNotPackaged: " + file.name + " 不含 " + forbidden)
        }
    }
}
tasks.named("check") { dependsOn(verifyHeadlessNotPackaged) }

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
tasks.withType<Test>().configureEach {
    dependsOn(tasks.named("exportHeadlessClasspath"))
    systemProperty("qz.headless.classpathFile", headlessRuntimeDir.get().file("classpath.txt").asFile.absolutePath)
    systemProperty("qz.headless.nativesDir", headlessRuntimeDir.get().dir("natives").asFile.absolutePath)
}

val exportHeadlessClasspath by tasks.registering(ExportHeadlessClasspath::class) {
    group = "headless"
    description = "导出 headless 直启 classpath（main 输出 + 真 LWJGL2 + 运行期依赖）"
    dependsOn(tasks.named("classes"), extractHeadlessNatives)
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
