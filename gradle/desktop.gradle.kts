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

val core21 = tasks.named<JavaCompile>("compileCore21Java")
val font21 = tasks.named<JavaCompile>("compileFontAdapter21Java")
val gl21 = tasks.named<JavaCompile>("compileGlRenderer21Java")
val binding21 = tasks.named<JavaCompile>("compileLwjgl3Desktop21Java")
val mainlineOutput = files(core21.flatMap { it.destinationDirectory }, font21.flatMap { it.destinationDirectory },
    gl21.flatMap { it.destinationDirectory }, binding21.flatMap { it.destinationDirectory })
val compileDesktop21Java by tasks.registering(JavaCompile::class) {
    dependsOn(core21, font21, gl21, binding21)
    source(desktopSources.java)
    classpath = mainlineOutput + fontBindings + coreContentRuntime
    destinationDirectory.set(layout.buildDirectory.dir("classes/java/desktop21"))
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    sourceCompatibility = "21"; targetCompatibility = "21"; options.release.set(21)
    options.compilerArgs.add("-proc:none")
}
// fontNativeRuntime 沿 font-runtime.gradle.kts 的构建机 os/arch 选择 native；此分发不是跨平台包。
// CPU 字体的隔离绑定资源仍只归 font-adapter，不从该输出重复嵌入桌面。
val desktopJar by tasks.registering(ShadowJar::class) {
    group = "build"
    description = "生成当前平台 Java21 独立桌面宿主及 GLFW/OpenGL bindings/native"
    archiveClassifier.set("desktop")
    dependsOn(compileDesktop21Java, gl21, binding21, tasks.named(desktopSources.processResourcesTaskName))
    from(compileDesktop21Java.flatMap { it.destinationDirectory }, gl21.flatMap { it.destinationDirectory },
        binding21.flatMap { it.destinationDirectory }, desktopSources.output.resourcesDir)
    configurations.set(listOf(fontNativeRuntime))
    minimizeJar.set(false)
    enableAutoRelocation.set(false)
    exclude("META-INF/versions/**", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.attributes("Main-Class" to desktopMain,
        "UILib-Native-Platform" to System.getProperty("os.name"),
        "UILib-Native-Architecture" to System.getProperty("os.arch"))
}
val coreArtifact = tasks.named<Jar>("coreJar").flatMap { it.archiveFile }
val adapterArtifact = tasks.named<Jar>("fontAdapterJar").flatMap { it.archiveFile }
val desktopArtifact = desktopJar.flatMap { it.archiveFile }
val mediaArtifact = tasks.named<Jar>("mediaAdapterJar").flatMap { it.archiveFile }
val desktopArtifacts = files(desktopArtifact, coreArtifact, adapterArtifact, mediaArtifact)
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
            // Java21 使用通配类路径；lib 仅安装下方四个正式产物，允许目录含空格。
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
    description = "安装当前平台 desktop/core/font-adapter 成品及 Java21 启动脚本"
    dependsOn(desktopJar, tasks.named("coreJar"), tasks.named("fontAdapterJar"), tasks.named("mediaAdapterJar"), generateDesktopLaunchers)
    into(layout.buildDirectory.dir("install/desktop"))
    from(desktopArtifacts) { into("lib") }
    from(desktopScripts) { into("bin"); filePermissions { unix("rwxr-xr-x") } }
}
val desktopDist by tasks.registering(Zip::class) {
    group = "distribution"
    description = "打包当前构建平台的独立桌面分发；使用系统 Java21 或 JAVA_HOME"
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
    java.srcDir("src/testHost/java")
    compileClasspath = testApplicationOutput + sourceSets.getByName("gallery").output + mainlineOutput + files(compileDesktop21Java.flatMap { it.destinationDirectory }) + sourceSets.getByName("mediaAdapter").output + sourceSets.getByName("mediaTest").output + fontBindings + coreContentRuntime
    runtimeClasspath = output + desktopArtifacts
}
tasks.named<JavaCompile>(desktopTestSources.compileJavaTaskName) {
    dependsOn(compileDesktop21Java, "mediaTestClasses", "testSupportClasses")
    // 消费21生产class必须由现代编译器读取；测试自身仍可独立输出52。
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    options.release.set(8)
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
    from(sourceSets.getByName("mediaTest").output)
    from(testApplicationOutput)
    from(sourceSets.getByName("gallery").output)
    configurations.set(emptyList())
    minimizeJar.set(false)
    enableAutoRelocation.set(false)
}
fun desktopApplication(name: String, summary: String, verify: Boolean) = tasks.register<JavaExec>(name) {
    group = if (verify) "verification" else "application"
    description = summary
    dependsOn(installDesktop)
    mainClass.set(if (verify) "club.heiqi.uilib.host.desktop.DesktopVerification" else desktopMain)
    // 测试只追加消费者 jar；所有生产类仍来自分发的四个正式 jar。
    classpath = if (verify) files(desktopTestJar.flatMap { it.archiveFile }) + desktopArtifacts else desktopArtifacts
    if (verify) dependsOn(desktopTestJar)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    jvmArgs("-Dfile.encoding=UTF-8", "-Xmx1g")
    if (providers.gradleProperty("desktopHidden").isPresent) systemProperty("uilib.desktop.visible", "false")
    if (mac) jvmArgs("-XstartOnFirstThread")
    workingDir(layout.buildDirectory.dir("install/desktop"))
}
// 人工功能入口只打开共享目录；具体案例由广场导航选择。
val runDesktopSettings = desktopApplication("runDesktopSettings", "以正式发行组合打开共享配置页，不启动MC", false).apply { configure {
    mainClass.set(desktopSettingsMain)
}
}
val desktopVerify = desktopApplication("desktopVerify", "以正式发行组合运行有限真实窗口绘制交互与释放验收", true)
val desktopGallery = desktopApplication("desktopGallery", "打开统一测试广场，在目录选择邮箱、配置、交互或媒体案例", true).apply { configure {
    group = "application"
    mainClass.set("club.heiqi.uilib.host.desktop.DesktopGallery")
} }
val desktopGalleryMediaVerify = desktopApplication("desktopGalleryMediaVerify", "真实广场媒体输入、播放与离页关闭验收", true).apply { configure {
    mainClass.set("club.heiqi.uilib.host.desktop.DesktopGalleryMediaVerification")
    args(layout.buildDirectory.dir("reports/gallery-media").get().asFile.absolutePath)
} }
val desktopLiveMediaVerify = desktopApplication("desktopLiveMediaVerify", "自包含真实直播窗口、音频、过期与恢复验收", true).apply { configure {
    mainClass.set("club.heiqi.uilib.host.desktop.DesktopMediaUxVerification")
    args(layout.buildDirectory.dir("reports/media-ux").get().asFile.absolutePath)
} }
val desktopNetworkMediaVerify = desktopApplication("desktopNetworkMediaVerify", "受控网络来源分P清晰度与过时结果实机验收", true).apply { configure {
    mainClass.set("club.heiqi.uilib.host.desktop.DesktopNetworkMediaVerification")
    args(layout.buildDirectory.dir("reports/network-media-desktop").get().asFile.absolutePath)
} }
val desktopBilibiliVerify = desktopApplication("desktopBilibiliVerify", "独立公网Bilibili点播与直播真实交互验收", true).apply { configure {
    mainClass.set("club.heiqi.uilib.host.desktop.DesktopBilibiliVerification")
    args(layout.buildDirectory.dir("reports/bilibili-desktop").get().asFile.absolutePath,
        providers.gradleProperty("bilibiliVideo").getOrElse("BV1GJ411x7h7"),
        providers.gradleProperty("bilibiliLive").getOrElse("https://live.bilibili.com/6"))
} }
val desktopTooltipVerify = desktopApplication("desktopTooltipVerify", "真实回调与GPU验收通用Tooltip", true).apply { configure {
    mainClass.set("club.heiqi.uilib.host.desktop.DesktopTooltipVerification")
    args(layout.buildDirectory.dir("reports/tooltip-desktop").get().asFile.absolutePath)
} }
val desktopDropSortVerify = desktopApplication("desktopDropSortVerify", "隐藏真实回调验收接收区与多布局排序", true).apply { configure {
    mainClass.set("club.heiqi.uilib.host.desktop.DesktopDropSortVerification")
    args(layout.buildDirectory.dir("reports/drop-sort-desktop").get().asFile.absolutePath)
    mustRunAfter("desktopDraggableDeepVerify")
} }
val desktopScrollAreaVerify = desktopApplication("desktopScrollAreaVerify", "隐藏真实回调验收滚动条与双轴滚动", true).apply { configure {
    mainClass.set("club.heiqi.uilib.host.desktop.DesktopScrollAreaVerification")
    args(layout.buildDirectory.dir("reports/scroll-area-desktop").get().asFile.absolutePath)
    mustRunAfter(desktopDropSortVerify)
} }
val desktopMailboxPerformance = desktopApplication("desktopMailboxPerformance", "完整收件箱真实桌面计时", true).apply { configure {
    group = "benchmark"
    mainClass.set("club.heiqi.uilib.host.desktop.DesktopMailboxPerformance")
    systemProperty("uilib.version", project.version.toString())
    systemProperty("mailbox.paged", providers.gradleProperty("mailboxPaged").getOrElse("true"))
    systemProperty("mailbox.loads", providers.gradleProperty("mailboxLoads").getOrElse("IDLE,SCROLL,UPDATE,RESIZE"))
    args(file(providers.gradleProperty("performanceOutput").getOrElse(layout.buildDirectory.dir("reports/mailbox-desktop").get().asFile.absolutePath)).absolutePath,
        providers.gradleProperty("performanceScale").getOrElse("200"), providers.gradleProperty("performanceWarmup").getOrElse("30"),
        providers.gradleProperty("performanceFrames").getOrElse("120"))
} }
val desktopGalleryVerify = desktopApplication("desktopGalleryVerify", "真实桌面运行收件箱与正式配置页共享旅程", true).apply { configure {
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
val desktopDraggableVerify = desktopApplication("desktopDraggableVerify", "真实回调与GPU验收原生拖拽和生命周期", true).apply { configure {
    mainClass.set("club.heiqi.uilib.host.desktop.DesktopDraggableVerification")
    args(layout.buildDirectory.dir("reports/draggable-desktop").get().asFile.absolutePath)
    mustRunAfter(desktopSettingsVerify)
} }
val desktopDraggableDeepVerify = desktopApplication("desktopDraggableDeepVerify", "隐藏真实GLFW验收深入拖拽与变高行排序", true).apply { configure {
    mainClass.set("club.heiqi.uilib.host.desktop.DesktopDraggableDeepVerification")
    args(layout.buildDirectory.dir("reports/desktop-draggable-deep").get().asFile.absolutePath)
    mustRunAfter(desktopDraggableVerify)
} }
listOf("desktopPerformance", "desktopPerformanceBenchmark").forEach { taskName ->
    desktopApplication(taskName, "独立性能压测广场与有限真实Desktop基线", true).configure {
        group = if (taskName == "desktopPerformance") "application" else "benchmark"
        mainClass.set("club.heiqi.uilib.host.desktop.DesktopPerformance")
        systemProperty("uilib.version", project.version.toString())
        args(if (taskName == "desktopPerformanceBenchmark") "--benchmark" else "--manual",
            providers.gradleProperty("performanceOutput").getOrElse(layout.buildDirectory.dir("reports/performance-desktop").get().asFile.absolutePath),
            providers.gradleProperty("performanceScale").getOrElse("200"),
            providers.gradleProperty("performanceWarmup").getOrElse("30"),
            providers.gradleProperty("performanceFrames").getOrElse("120"))
    }
}
// 专项场景仍可按任务名定位；日常验收与 check 统一通过聚合入口取得相同覆盖。
val galleryChecks = listOf(desktopGalleryVerify, desktopGalleryResultScenario, desktopSettingsVerify,
    desktopDraggableVerify, desktopDraggableDeepVerify, desktopDropSortVerify, desktopScrollAreaVerify, desktopGalleryMediaVerify, desktopTooltipVerify, desktopLiveMediaVerify, desktopNetworkMediaVerify)
galleryChecks.forEach { task -> task.configure { group = null } }
val galleryVerify by tasks.registering {
    group = "verification"
    description = "统一验收测试广场的目录、邮箱、配置、交互、媒体与释放"
    dependsOn(galleryChecks)
}
tasks.named("check") { dependsOn(desktopVerify, galleryVerify) }
val desktopDraggableBenchmark = desktopApplication("desktopDraggableBenchmark", "隐藏真实GL窗口有限拖拽压测", true).apply { configure {
    group = "benchmark"
    mainClass.set("club.heiqi.uilib.host.desktop.DesktopDraggablePerformance")
    systemProperty("uilib.version", project.version.toString())
    systemProperty("drag.revision", providers.gradleProperty("dragRevision").getOrElse("unknown"))
    if (providers.gradleProperty("dragProfile").getOrElse("false").toBoolean()) jvmArgs("-Xprof")
    args(file(providers.gradleProperty("dragOutput").getOrElse(layout.buildDirectory.dir("reports/draggable-performance").get().asFile.absolutePath)).absolutePath,
        providers.gradleProperty("dragScale").getOrElse("50,200,1000"),
        providers.gradleProperty("dragWarmup").getOrElse("30"), providers.gradleProperty("dragFrames").getOrElse("120"),
        providers.gradleProperty("dragLoads").getOrElse("plain,sort"), providers.gradleProperty("dragEdgeFrames").getOrElse("0"))
} }
tasks.named("assemble") { dependsOn(desktopJar, desktopDist) }
