import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaToolchainService

val sourceSets = extensions.getByType<SourceSetContainer>()
val coreSources = sourceSets.getByName("core")
val fontAdapterSources = sourceSets.getByName("fontAdapter")
val fontBindings = configurations.getByName("fontBindings")
val fontNativeRuntime = configurations.getByName("fontNativeRuntime")
val coreContentRuntime = configurations.getByName("coreContentRuntime")
val javaToolchains = extensions.getByType<JavaToolchainService>()
val desktopMain = "club.heiqi.uilib.host.desktop.DesktopApplication"
val desktopSettingsMain = "club.heiqi.uilib.host.desktop.DesktopSettingsMain"

// 只装配窗口与输入；共享呈现和具体绑定直接使用独立编译输出。
val glRendererSources = sourceSets.getByName("glRenderer")
val lwjgl3Sources = sourceSets.getByName("lwjgl3")
val desktopSources = sourceSets.create("desktop") {
    compileClasspath = glRendererSources.output + lwjgl3Sources.output + coreSources.output + fontAdapterSources.output + fontBindings + coreContentRuntime
    runtimeClasspath = output + glRendererSources.output + lwjgl3Sources.output + coreSources.output + fontAdapterSources.output + fontNativeRuntime + coreContentRuntime
}
tasks.named<JavaCompile>(desktopSources.compileJavaTaskName) {
    dependsOn(tasks.named(coreSources.classesTaskName), tasks.named(fontAdapterSources.classesTaskName))
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.release.set(8)
    options.compilerArgs.add("-proc:none")
    classpath = desktopSources.compileClasspath
}

// fontNativeRuntime 沿 font-runtime.gradle.kts 的构建机 os/arch 选择 native；此分发不是跨平台包。
// CPU 字体的隔离绑定资源仍只归 font-adapter，不从该输出重复嵌入桌面。
val desktopJar by tasks.registering(ShadowJar::class) {
    group = "build"
    description = "生成当前平台 Java8 独立桌面宿主及 GLFW/OpenGL bindings/native"
    archiveClassifier.set("desktop")
    from(desktopSources.output, glRendererSources.output, lwjgl3Sources.output)
    configurations.set(listOf(fontNativeRuntime))
    minimizeJar.set(false)
    enableAutoRelocation.set(false)
    relocate("com.ibm.icu", "club.heiqi.uilib.shadow.com.ibm.icu")
    relocate("org.yaml.snakeyaml", "club.heiqi.uilib.shadow.org.yaml.snakeyaml")
    relocate("org.commonmark", "club.heiqi.uilib.shadow.org.commonmark")
    relocate("org.jsoup", "club.heiqi.uilib.shadow.org.jsoup")
    relocate("org.nibor.autolink", "club.heiqi.uilib.shadow.org.nibor.autolink")
    exclude("META-INF/versions/**", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.attributes("Main-Class" to desktopMain,
        "UILib-Native-Platform" to System.getProperty("os.name"),
        "UILib-Native-Architecture" to System.getProperty("os.arch"))
}
val coreArtifact = tasks.named<Jar>("coreJar").flatMap { it.archiveFile }
val adapterArtifact = tasks.named<Jar>("fontAdapterJar").flatMap { it.archiveFile }
val desktopArtifact = desktopJar.flatMap { it.archiveFile }
val desktopArtifacts = files(desktopArtifact, coreArtifact, adapterArtifact)
val desktopScripts = layout.buildDirectory.dir("generated/desktop-launchers")
val mac = System.getProperty("os.name").lowercase().contains("mac")

// 用任务属性承载输出和平台参数，生成器不捕获项目脚本对象，兼容配置缓存。
abstract class GenerateDesktopLaunchers : DefaultTask() {
    @get:OutputDirectory abstract val outputDirectory: org.gradle.api.file.DirectoryProperty
    @get:Input abstract val entryPoint: org.gradle.api.provider.Property<String>
    @get:Input abstract val settingsEntryPoint: org.gradle.api.provider.Property<String>
    @get:Input abstract val firstThread: org.gradle.api.provider.Property<Boolean>
    @TaskAction fun generate() {
        val directory = outputDirectory.get().asFile
        val launchers = linkedMapOf("qz-desktop" to entryPoint.get(),
            "qz-desktop-settings" to settingsEntryPoint.get())
        val mac = firstThread.get()
        directory.mkdirs()
        for ((name, desktopMain) in launchers) {
            directory.resolve("$name.bat").writeText(
                "@echo off\r\nsetlocal\r\nset \"DESKTOP_JAVA=java\"\r\nif defined JAVA_HOME set \"DESKTOP_JAVA=%JAVA_HOME%\\bin\\java.exe\"\r\n\"%DESKTOP_JAVA%\" -Dfile.encoding=UTF-8 -Xmx1g -cp \"%~dp0..\\lib\\*\" $desktopMain %*\r\nexit /b %errorlevel%\r\n", Charsets.UTF_8)
            // Java8 不使用 @argfile；lib 仅安装下方三个正式产物，允许目录含空格。
            directory.resolve(if (name == "qz-desktop") "$name.sh" else name).writeText(
                "#!/bin/sh\nDESKTOP_HOME=\"\$(CDPATH= cd -- \"\$(dirname -- \"\$0\")/..\" && pwd)\"\nDESKTOP_JAVA=java\nif [ -n \"\$JAVA_HOME\" ]; then DESKTOP_JAVA=\"\$JAVA_HOME/bin/java\"; fi\nexec \"\$DESKTOP_JAVA\" "+ (if (mac) "-XstartOnFirstThread " else "") + "-Dfile.encoding=UTF-8 -Xmx1g -cp \"\$DESKTOP_HOME/lib/*\" $desktopMain \"\$@\"\n", Charsets.UTF_8)
        }
    }
}
val generateDesktopLaunchers by tasks.registering(GenerateDesktopLaunchers::class) {
    outputDirectory.set(desktopScripts)
    entryPoint.set(desktopMain)
    settingsEntryPoint.set(desktopSettingsMain)
    firstThread.set(mac)
}
val installDesktop by tasks.registering(Sync::class) {
    group = "distribution"
    description = "安装当前平台 desktop/core/font-adapter 成品及 Java8 启动脚本"
    dependsOn(desktopJar, tasks.named("coreJar"), tasks.named("fontAdapterJar"), generateDesktopLaunchers)
    into(layout.buildDirectory.dir("install/desktop"))
    from(desktopArtifacts) { into("lib") }
    from(desktopScripts) { into("bin"); filePermissions { unix("rwxr-xr-x") } }
}
val desktopDist by tasks.registering(Zip::class) {
    group = "distribution"
    description = "打包当前构建平台的独立桌面分发；使用系统 Java8 或 JAVA_HOME"
    dependsOn(installDesktop)
    archiveClassifier.set("desktop-dist")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(layout.buildDirectory.dir("install/desktop/lib")) { into("qz-uilib-desktop/lib") }
    from(layout.buildDirectory.dir("install/desktop/bin")) {
        into("qz-uilib-desktop/bin")
        filePermissions { unix("rwxr-xr-x") }
    }
}
// 自动点击、像素验收与关闭检查属于独立测试应用，正式发行不携带测试代码。
// verification脚本随后创建testSupport；延迟取得输出，只接到测试消费者。
val testApplicationOutput = files(provider { sourceSets.getByName("testSupport").output })
val desktopTestSources = sourceSets.create("desktopTest") {
    compileClasspath = testApplicationOutput + sourceSets.getByName("gallery").output + desktopSources.output + glRendererSources.output + lwjgl3Sources.output + coreSources.output + fontAdapterSources.output + fontBindings + coreContentRuntime
    runtimeClasspath = output + desktopArtifacts
}
tasks.named<JavaCompile>(desktopTestSources.compileJavaTaskName) {
    dependsOn(tasks.named(desktopSources.classesTaskName), "testSupportClasses")
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.release.set(8)
    options.compilerArgs.add("-proc:none")
    classpath = desktopTestSources.compileClasspath
}
val desktopTestJar by tasks.registering(ShadowJar::class) {
    group = "verification"
    description = "生成独立桌面验收消费者，仅测试定义，不嵌入任何生产依赖"
    archiveClassifier.set("desktop-test")
    from(desktopTestSources.output)
    from(testApplicationOutput)
    from(sourceSets.getByName("gallery").output)
    configurations.set(emptyList())
    minimizeJar.set(false)
    enableAutoRelocation.set(false)
    relocate("com.ibm.icu", "club.heiqi.uilib.shadow.com.ibm.icu")
    relocate("org.yaml.snakeyaml", "club.heiqi.uilib.shadow.org.yaml.snakeyaml")
    relocate("org.commonmark", "club.heiqi.uilib.shadow.org.commonmark")
    relocate("org.jsoup", "club.heiqi.uilib.shadow.org.jsoup")
    relocate("org.nibor.autolink", "club.heiqi.uilib.shadow.org.nibor.autolink")
}
fun desktopApplication(name: String, summary: String, verify: Boolean) = tasks.register<JavaExec>(name) {
    group = if (verify) "verification" else "application"
    description = summary
    dependsOn(installDesktop)
    mainClass.set(if (verify) "club.heiqi.uilib.host.desktop.DesktopVerification" else desktopMain)
    // 测试只追加消费者 jar；所有生产类仍来自分发的三个正式 jar。
    classpath = if (verify) files(desktopTestJar.flatMap { it.archiveFile }) + desktopArtifacts else desktopArtifacts
    if (verify) dependsOn(desktopTestJar)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
    jvmArgs("-Dfile.encoding=UTF-8", "-Xmx1g")
    if (mac) jvmArgs("-XstartOnFirstThread")
    workingDir(layout.buildDirectory.dir("install/desktop"))
}
// 人工入口统一装配当前共享广场，仍只在测试消费者 jar 中携带页面和样本。
fun galleryApplication(name: String, summary: String) = desktopApplication(name, summary, true).apply { configure {
    group = "application"
    mainClass.set("club.heiqi.uilib.host.desktop.DesktopGallery")
} }
val desktopRun = galleryApplication("desktopRun", "打开当前共享测试广场的场景目录，不启动MC")
val runDesktopSettings = desktopApplication("runDesktopSettings", "以正式发行组合打开共享配置页，不启动MC", false).apply { configure {
    mainClass.set(desktopSettingsMain)
}
}
val desktopVerify = desktopApplication("desktopVerify", "以正式发行组合运行有限真实窗口绘制交互与释放验收", true)
val desktopGallery = galleryApplication("desktopGallery", "打开与MC和自动机共用的测试广场")
val desktopMailbox = galleryApplication("desktopMailbox", "直接打开与MC共用的收件箱案例").apply { configure {
    args("--mailbox")
} }
val desktopMailboxPerformance = desktopApplication("desktopMailboxPerformance", "完整收件箱真实桌面计时", true).apply { configure {
    mainClass.set("club.heiqi.uilib.host.desktop.DesktopMailboxPerformance")
    systemProperty("uilib.version", project.version.toString())
    systemProperty("mailbox.paged", providers.gradleProperty("mailboxPaged").getOrElse("true"))
    systemProperty("mailbox.loads", providers.gradleProperty("mailboxLoads").getOrElse("IDLE,SCROLL,UPDATE,RESIZE"))
    args(file(providers.gradleProperty("performanceOutput").getOrElse(layout.buildDirectory.dir("reports/mailbox-desktop").get().asFile.absolutePath)).absolutePath,
        providers.gradleProperty("performanceScale").getOrElse("200"), providers.gradleProperty("performanceWarmup").getOrElse("30"),
        providers.gradleProperty("performanceFrames").getOrElse("120"))
} }
val desktopGalleryVerify = desktopApplication("desktopGalleryVerify", "真实桌面运行共享广场旅程与图像像素重载释放", true).apply { configure {
    mainClass.set("club.heiqi.uilib.host.desktop.DesktopGallery")
    args("--verify", layout.buildDirectory.dir("reports/gallery-desktop").get().asFile.absolutePath)
    mustRunAfter(desktopVerify)
}
}
val desktopGalleryResultScenario = desktopApplication("desktopGalleryResultScenario", "真实测试应用复用结果目录的失败与清理反例", true).apply { configure {
    mainClass.set("club.heiqi.uilib.host.desktop.DesktopGalleryResultScenario")
    args(layout.buildDirectory.dir("reports/gallery-result").get().asFile.absolutePath)
} }
val desktopSettingsVerify = desktopApplication("desktopSettingsVerify", "真实桌面验收共享配置页编辑、应用、保存、重载与关闭", true).apply { configure {
    mainClass.set("club.heiqi.uilib.host.desktop.DesktopSettingsVerification")
    args(layout.buildDirectory.dir("reports/settings-desktop").get().asFile.absolutePath)
    mustRunAfter(desktopGalleryVerify)
}
}
listOf("desktopPerformance", "desktopPerformanceBenchmark").forEach { taskName ->
    desktopApplication(taskName, "独立性能压测广场与有限真实Desktop基线", true).configure {
        mainClass.set("club.heiqi.uilib.host.desktop.DesktopPerformance")
        systemProperty("uilib.version", project.version.toString())
        args(if (taskName == "desktopPerformanceBenchmark") "--benchmark" else "--manual",
            providers.gradleProperty("performanceOutput").getOrElse(layout.buildDirectory.dir("reports/performance-desktop").get().asFile.absolutePath),
            providers.gradleProperty("performanceScale").getOrElse("200"),
            providers.gradleProperty("performanceWarmup").getOrElse("30"),
            providers.gradleProperty("performanceFrames").getOrElse("120"))
    }
}
tasks.named("check") { dependsOn(desktopVerify, desktopGalleryVerify, desktopGalleryResultScenario, desktopSettingsVerify) }
tasks.named("assemble") { dependsOn(desktopJar, desktopDist) }
