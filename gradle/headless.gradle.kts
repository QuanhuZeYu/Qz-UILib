import java.io.File
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer

val sourceSets = extensions.getByType<SourceSetContainer>()
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
