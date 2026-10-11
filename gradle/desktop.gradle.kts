import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaToolchainService

val sourceSets = extensions.getByType<SourceSetContainer>()
val coreSources = sourceSets.getByName("core")
val fontAdapterSources = sourceSets.getByName("fontAdapter")
val fontBindings = configurations.getByName("fontBindings")
val fontNativeRuntime = configurations.getByName("fontNativeRuntime")
val fontUnicodeRuntime = configurations.getByName("fontUnicodeRuntime")
val yamlRuntime = configurations.getByName("yamlRuntime")
val javaToolchains = extensions.getByType<JavaToolchainService>()
val desktopMain = "club.heiqi.uilib.host.desktop.DesktopApplication"

// 同一生产实现供两个宿主编译；桌面没有 MC 类转换器，只移除它专用的逐类标记。
// 不保留另一份 GL 实现，也不把 MC 的 compileClasspath 引入桌面。
val desktopHostJava = layout.buildDirectory.dir("generated/desktop-host-java")
val prepareDesktopHostSources by tasks.registering(Sync::class) {
    from("src/main/java") {
        include("club/heiqi/uilib/host/client/gl/**",
            "club/heiqi/uilib/host/client/font/ClientFontServices.java",
            "club/heiqi/uilib/host/client/font/FramebufferCoordinates.java",
            "club/heiqi/uilib/host/client/font/FramebufferSceneRenderer.java",
            "club/heiqi/uilib/host/client/font/context/**",
            "club/heiqi/uilib/ui/render/batch/**",
            "club/heiqi/uilib/host/client/image/ClientImageServices.java",
            "club/heiqi/uilib/host/client/image/JavaImageLoader.java")
        filter { line: String -> if (line.trim() == "@me.eigenraven.lwjgl3ify.api.Lwjgl3Aware") "" else line }
    }
    into(desktopHostJava)
    filteringCharset = "UTF-8"
}
val desktopSources = sourceSets.create("desktop") {
    java.srcDir("src/desktop/java")
    java.srcDir(desktopHostJava)
    resources.srcDir("src/desktop/resources")
    compileClasspath = coreSources.output + fontAdapterSources.output + fontBindings + fontUnicodeRuntime + yamlRuntime
    runtimeClasspath = output + coreSources.output + fontAdapterSources.output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
tasks.named<JavaCompile>(desktopSources.compileJavaTaskName) {
    dependsOn(prepareDesktopHostSources, tasks.named(coreSources.classesTaskName), tasks.named(fontAdapterSources.classesTaskName))
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
    from(desktopSources.output)
    configurations.set(listOf(fontNativeRuntime))
    minimizeJar.set(false)
    enableAutoRelocation.set(false)
    relocate("com.ibm.icu", "club.heiqi.uilib.shadow.com.ibm.icu")
    relocate("org.yaml.snakeyaml", "club.heiqi.uilib.shadow.org.yaml.snakeyaml")
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
    @get:Input abstract val firstThread: org.gradle.api.provider.Property<Boolean>
    @TaskAction fun generate() {
        val directory = outputDirectory.get().asFile
        val desktopMain = entryPoint.get()
        val mac = firstThread.get()
        directory.mkdirs()
        directory.resolve("qz-desktop.bat").writeText(
            "@echo off\r\nsetlocal\r\nset \"DESKTOP_JAVA=java\"\r\nif defined JAVA_HOME set \"DESKTOP_JAVA=%JAVA_HOME%\\bin\\java.exe\"\r\n\"%DESKTOP_JAVA%\" -Dfile.encoding=UTF-8 -Xmx1g -cp \"%~dp0..\\lib\\*\" $desktopMain %*\r\nexit /b %errorlevel%\r\n", Charsets.UTF_8)
        // Java8 不使用 @argfile；lib 仅安装下方三个正式产物，允许目录含空格。
        directory.resolve("qz-desktop.sh").writeText(
            "#!/bin/sh\nDESKTOP_HOME=\"\$(CDPATH= cd -- \"\$(dirname -- \"\$0\")/..\" && pwd)\"\nDESKTOP_JAVA=java\nif [ -n \"\$JAVA_HOME\" ]; then DESKTOP_JAVA=\"\$JAVA_HOME/bin/java\"; fi\nexec \"\$DESKTOP_JAVA\" "+ (if (mac) "-XstartOnFirstThread " else "") + "-Dfile.encoding=UTF-8 -Xmx1g -cp \"\$DESKTOP_HOME/lib/*\" $desktopMain \"\$@\"\n", Charsets.UTF_8)
    }
}
val generateDesktopLaunchers by tasks.registering(GenerateDesktopLaunchers::class) {
    outputDirectory.set(desktopScripts)
    entryPoint.set(desktopMain)
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
val desktopTestSources = sourceSets.create("desktopTest") {
    java.srcDir("src/desktopTest/java")
    compileClasspath = desktopSources.output + coreSources.output + fontAdapterSources.output + fontBindings + fontUnicodeRuntime + yamlRuntime
    runtimeClasspath = output + desktopArtifacts
}
tasks.named<JavaCompile>(desktopTestSources.compileJavaTaskName) {
    dependsOn(tasks.named(desktopSources.classesTaskName))
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
    configurations.set(emptyList())
    minimizeJar.set(false)
    enableAutoRelocation.set(false)
    relocate("com.ibm.icu", "club.heiqi.uilib.shadow.com.ibm.icu")
    relocate("org.yaml.snakeyaml", "club.heiqi.uilib.shadow.org.yaml.snakeyaml")
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
val desktopRun = desktopApplication("desktopRun", "以正式发行组合启动独立桌面单窗口", false)
val desktopVerify = desktopApplication("desktopVerify", "以正式发行组合运行有限真实窗口绘制交互与释放验收", true)
tasks.named("check") { dependsOn(desktopVerify) }
tasks.named("assemble") { dependsOn(desktopJar, desktopDist) }
