import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaToolchainService

val sourceSets = extensions.getByType<SourceSetContainer>()
val javaToolchains = extensions.getByType<JavaToolchainService>()
val fontUnicodeRuntime = configurations.getByName("fontUnicodeRuntime")
val fontNativeRuntime = configurations.getByName("fontNativeRuntime")
val lwjgl2Bindings = configurations.getByName("lwjgl2Bindings")
val lwjgl2Sources = sourceSets.getByName("lwjgl2")

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
val coreSceneDeclarations = listOf(
    Triple("pointerInputScenario", "无平台真实scene指针手势验收", "PointerInputScenario"),
    Triple("focusInputScenario", "无平台真实scene焦点归属与生命周期验收", "FocusInputScenario"),
    Triple("focusScopeScenario", "无平台真实scene范围进入与焦点恢复验收", "FocusScopeScenario"),
    Triple("focusNavigationScenario", "无平台真实scene焦点导航与键盘适配验收", "FocusNavigationScenario"),
    Triple("escapeInputScenario", "ESC范围消费和宿主关闭协调验收", "EscapeInputScenario"),
    Triple("pageSessionScenario", "正式页面声明与生命周期归属验收", "PageSessionScenario"),
    Triple("standardControlsScenario", "标准控件动态主题与完整文案验收", "StandardControlsScenario"),
    Triple("focusRevealScenario", "主动导航最小显露与嵌套滚动", "FocusRevealScenario"),
    Triple("editingScenario", "独立编辑状态与单行正式输入验收", "EditingScenario"),
    Triple("coreStateScenario", "隔离运行状态与生命周期场景", "SceneCoreLoopTest")
)

val coreSceneClasses = layout.buildDirectory.dir("classes/core-scene")
val compileCoreSceneScenario by tasks.registering(JavaCompile::class) {
    dependsOn(tasks.named(coreSources.classesTaskName), tasks.named(testSupportSources.classesTaskName))
    source(coreSceneDeclarations.map { (_, _, application) -> "src/test/java/club/heiqi/uilib/ui/scene/integration/$application.java" })
    source("src/test/java/club/heiqi/uilib/ui/scene/integration/SoftwareSceneScenario.java")
    classpath = coreSources.output + testSupportSources.output
    destinationDirectory.set(coreSceneClasses)
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.compilerArgs.add("-proc:none")
}
// 同一隔离编译单元的场景只声明入口与用途；编译依赖/类路径/门禁由此处统一维护。
val coreSceneApplications = coreSceneDeclarations.associate { (taskName, summary, application) ->
    taskName to registerTestApplication(taskName, summary, "club.heiqi.uilib.ui.scene.integration.$application") {
        dependsOn(compileCoreSceneScenario)
        classpath = files(coreSceneClasses) + coreSources.output + fontUnicodeRuntime
    }
}
tasks.named("check") { dependsOn(coreSceneApplications.filterKeys { it != "coreStateScenario" }.values) }
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
    dependsOn(coreSceneApplications.values, coreFontEngineScenario, testApplicationScenario)
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
// SDL场景单独编译与执行，不将MC/Forge、GLFW或字体native服务放入运行路径。
// SDL使用GTNH宿主实际缓存的时间戳绑定，core固定3.4.2；正常解析依赖，不绑定本机缓存目录。
val sdlTextInputRuntime by configurations.creating
dependencies {
    add(sdlTextInputRuntime.name, "org.lwjgl:lwjgl:3.4.2") { isTransitive = false }
    add(sdlTextInputRuntime.name, "org.lwjgl:lwjgl-sdl:3.4.2-20260225.192616-2") { isTransitive = false }
    val platform = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val classifier = when {
        platform.contains("win") -> if (arch == "aarch64") "natives-windows-arm64" else if (arch == "x86") "natives-windows-x86" else "natives-windows"
        platform.contains("mac") -> if (arch == "aarch64") "natives-macos-arm64" else "natives-macos"
        else -> if (arch == "aarch64") "natives-linux-arm64" else "natives-linux"
    }
    add(sdlTextInputRuntime.name, "org.lwjgl:lwjgl:3.4.2:$classifier") { isTransitive = false }
    add(sdlTextInputRuntime.name, "org.lwjgl:lwjgl-sdl:3.4.2-20260225.192616-2:$classifier") { isTransitive = false }
}
val sdlTextInputClasses = layout.buildDirectory.dir("classes/sdl-text-input")
val compileSdlTextInputScenario by tasks.registering(JavaCompile::class) {
    dependsOn(tasks.named("classes"), tasks.named(testSupportSources.classesTaskName))
    source("src/sdlTest/java")
    classpath = sourceSets.getByName("main").output + testSupportSources.output + sdlTextInputRuntime
    destinationDirectory.set(sdlTextInputClasses)
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    options.release.set(8)
    options.compilerArgs.add("-proc:none")
}
val sdlTextInputScenario = registerTestApplication("sdlTextInputScenario", "真实SDL hidden window、生产Watch观察与scene原子IME（不开游戏）", "club.heiqi.uilib.ui.scene.integration.SdlTextInputScenario", useDefaultLauncher = false) {
    dependsOn(compileSdlTextInputScenario)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    classpath = files(sdlTextInputClasses) + sourceSets.getByName("main").output + sdlTextInputRuntime + fontUnicodeRuntime
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

