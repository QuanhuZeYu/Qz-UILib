import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaToolchainService

val sourceSets = extensions.getByType<SourceSetContainer>()
val javaToolchains = extensions.getByType<JavaToolchainService>()
val fontUnicodeRuntime = configurations.getByName("fontUnicodeRuntime")
val yamlRuntime = configurations.getByName("yamlRuntime")
val fontNativeRuntime = configurations.getByName("fontNativeRuntime")
val lwjgl2Bindings = configurations.getByName("lwjgl2Bindings")
val lwjgl2Sources = sourceSets.getByName("lwjgl2")
val coreSources = sourceSets.getByName("core")
val fontAdapterSources = sourceSets.getByName("fontAdapter")

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
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontUnicodeRuntime + yamlRuntime
}
val orderedBatchScenario = registerTestApplication("orderedBatchScenario", "运行无平台生产合批透明重叠与裁剪出图场景", "club.heiqi.uilib.ui.scene.integration.OrderedBatchScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontUnicodeRuntime + yamlRuntime
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

// 场景使用正式生产模块输出，无平台编译边界由 core.gradle.kts 管理。
val coreSceneDeclarations = listOf(
    Triple("pointerInputScenario", "无平台真实scene指针手势验收", "PointerInputScenario"),
    Triple("focusInputScenario", "无平台真实scene焦点归属与生命周期验收", "FocusInputScenario"),
    Triple("focusScopeScenario", "无平台真实scene范围进入与焦点恢复验收", "FocusScopeScenario"),
    Triple("focusNavigationScenario", "无平台真实scene焦点导航与键盘适配验收", "FocusNavigationScenario"),
    Triple("escapeInputScenario", "ESC范围消费和宿主关闭协调验收", "EscapeInputScenario"),
    Triple("pageSessionScenario", "正式页面声明与生命周期归属验收", "PageSessionScenario"),
    Triple("localizationScenario", "无平台语言事实文案与真实页面动态布局生命周期验收", "LocalizationScenario"),
    Triple("localizationResourceScenario", "独立真实语言资源加载重载与失败生命周期验收", "LocalizationResourceScenario"),
    Triple("standardControlsScenario", "标准控件动态主题与完整文案验收", "StandardControlsScenario"),
    Triple("focusRevealScenario", "主动导航最小显露与嵌套滚动", "FocusRevealScenario"),
    Triple("editingScenario", "独立编辑状态与单行正式输入验收", "EditingScenario"),
    Triple("multilineEditingScenario", "多行独立状态与正式Area页面路由验收", "MultilineEditingScenario"),
    Triple("coreStateScenario", "隔离运行状态与生命周期场景", "SceneCoreLoopTest"),
    Triple("configScenario", "独立核心真实配置声明存取与批次通知验收", "ConfigScenario"),
    Triple("yamlObjectScenario", "真实复杂对象YAML绑定、注释与修改持久化验收", "YamlObjectScenario"),
    Triple("fontConfigurationScenario", "无平台字体完整候选配置与迁移验收", "FontConfigurationScenario")
)

val coreSceneClasses = layout.buildDirectory.dir("classes/core-scene")
val compileCoreSceneScenario by tasks.registering(JavaCompile::class) {
    dependsOn(tasks.named(coreSources.classesTaskName), tasks.named(testSupportSources.classesTaskName))
    source(coreSceneDeclarations.map { (_, _, application) -> "src/test/java/club/heiqi/uilib/ui/scene/integration/$application.java" })
    source("src/test/java/club/heiqi/uilib/ui/scene/integration/SoftwareSceneScenario.java")
    classpath = coreSources.output + testSupportSources.output + fontUnicodeRuntime + yamlRuntime
    destinationDirectory.set(coreSceneClasses)
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.compilerArgs.add("-proc:none")
}
// 同一隔离编译单元的场景只声明入口与用途；编译依赖/类路径/门禁由此处统一维护。
val coreSceneApplications = coreSceneDeclarations.associate { (taskName, summary, application) ->
    taskName to registerTestApplication(taskName, summary, "club.heiqi.uilib.ui.scene.integration.$application") {
        dependsOn(compileCoreSceneScenario)
        classpath = files(coreSceneClasses) + coreSources.output + fontUnicodeRuntime + yamlRuntime
        if (taskName == "localizationResourceScenario") classpath += files("src/main/resources", "src/test/resources")
    }
}
tasks.named("check") { dependsOn(coreSceneApplications.filterKeys { it != "coreStateScenario" }.values) }
// 富文本生产闭环独立编译，只接正式字体适配及 native 依赖，不扩大核心 MC 类路径。
val richTextClasses = layout.buildDirectory.dir("classes/rich-text")
val compileRichTextScenario by tasks.registering(JavaCompile::class) {
    dependsOn(tasks.named(coreSources.classesTaskName), tasks.named(testSupportSources.classesTaskName))
    dependsOn(tasks.named(fontAdapterSources.classesTaskName))
    source("src/test/java/club/heiqi/uilib/ui/scene/integration/RichTextScenario.java")
    classpath = coreSources.output + fontAdapterSources.output + testSupportSources.output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
    destinationDirectory.set(richTextClasses)
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.compilerArgs.add("-proc:none")
}
val richTextScenario = registerTestApplication("richTextScenario", "正式富文本解析字体布局与唯一链接路由", "club.heiqi.uilib.ui.scene.integration.RichTextScenario") {
    dependsOn(compileRichTextScenario)
    classpath = files(richTextClasses) + coreSources.output + fontAdapterSources.output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
tasks.named("check") { dependsOn(richTextScenario) }
val coreFontClasses = layout.buildDirectory.dir("classes/core-font")
val compileCoreFontScenario by tasks.registering(JavaCompile::class) {
    dependsOn(tasks.named(coreSources.classesTaskName), tasks.named(testSupportSources.classesTaskName))
    source("src/test/java/club/heiqi/uilib/ui/scene/integration/CoreFontEngineScenario.java")
    classpath = coreSources.output + fontUnicodeRuntime + testSupportSources.output + yamlRuntime
    destinationDirectory.set(coreFontClasses)
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.compilerArgs.add("-proc:none")
}
val coreFontEngineScenario = registerTestApplication("coreFontEngineScenario", "不含Minecraft、Forge或LWJGL的真实核心合同运行", "club.heiqi.uilib.ui.scene.integration.CoreFontEngineScenario") {
    dependsOn(compileCoreFontScenario)
    classpath = files(coreFontClasses) + coreSources.output + fontUnicodeRuntime + yamlRuntime
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
    compileClasspath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + lwjgl2Sources.output + lwjgl2Bindings + testSupportSources.output + yamlRuntime
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
val imageLwjgl2Scenario = registerTestApplication("imageLwjgl2Scenario", "原LWJGL2 actual IMAGE像素与compat item scope正常异常状态（不启动MC）", "club.heiqi.uilib.ui.scene.integration.ImageLwjgl2Scenario", useDefaultLauncher = false) {
    dependsOn(tasks.named(lwjgl2TestSources.classesTaskName), "extractHeadlessNatives")
    classpath = lwjgl2TestSources.runtimeClasspath
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
    jvmArgs("-Dfile.encoding=UTF-8", "-Djava.library.path=" + layout.buildDirectory.dir("headless/natives").get().asFile.absolutePath)
}
val nativeFontScenario = registerTestApplication("nativeFontScenario", "Java8独立运行正式字体整形/彩色像素与资源生命周期场景", "club.heiqi.uilib.ui.scene.integration.NativeFontScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + yamlRuntime
    args(file("src/main/resources/assets/qz_uilib/fonts/noto").absolutePath)
}
val nativeFontVisualScenario = registerTestApplication("nativeFontVisualScenario", "回放生产字体定位和FT光栅生成多语言PNG", "club.heiqi.uilib.ui.scene.integration.NativeFontVisualScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + yamlRuntime
    systemProperty("java.awt.headless", "true")
    args(file("src/main/resources/assets/qz_uilib/fonts/noto").absolutePath,
        layout.buildDirectory.dir("font-machine/native").get().asFile.absolutePath)
}
val packagedUnicodeScenario = registerTestApplication("packagedUnicodeScenario", "隔离验证发布包私有ICU的Bidi、字素及泰文字典资源", "club.heiqi.uilib.ui.scene.integration.PackagedUnicodeScenario") {
    dependsOn(tasks.named("testClasses"), tasks.named("coreJar"))
    classpath = sourceSets.getByName("test").output
    doFirst { args(tasks.named<Jar>("coreJar").get().archiveFile.get().asFile.absolutePath) }
}
// 正式嵌套绑定必须同时覆盖旧Java8与GTNH的Java25多版本选择。
val packagedFontScenarios = listOf(8, 25).map { javaVersion ->
    registerTestApplication("packagedFontJava$javaVersion", "Java${javaVersion}验证正式包字体整形与彩色光栅", "club.heiqi.uilib.ui.scene.integration.PackagedFontScenario") {
        dependsOn(tasks.named("testClasses"), tasks.named("coreJar"), tasks.named("fontAdapterJar"))
        classpath = sourceSets.getByName("test").output
        javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(javaVersion)) })
        doFirst { args(tasks.named<Jar>("coreJar").get().archiveFile.get().asFile.absolutePath, tasks.named<Jar>("fontAdapterJar").get().archiveFile.get().asFile.absolutePath) }
    }
}
tasks.named("check") { dependsOn(packagedFontScenarios) }

val glBatchScenario = registerTestApplication("glBatchScenario", "独立GLFW上下文对照真实二维纹理及数组回放（需本机GL设备）", "club.heiqi.uilib.ui.scene.integration.GlBatchScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + yamlRuntime
    args(layout.buildDirectory.dir("font-machine/gl").get().asFile.absolutePath)
}
val fontProjectionScenario = registerTestApplication("fontProjectionScenario", "透视采样、矩阵快照与有界光栅档位验收", "club.heiqi.uilib.ui.scene.integration.FontProjectionScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
    args("cpu")
}
val fontContextGlScenario = registerTestApplication("fontContextGlScenario", "Core与Compatibility有效字体上下文捕获（需要设备）", "club.heiqi.uilib.ui.scene.integration.FontProjectionScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
tasks.named("check") { dependsOn(fontProjectionScenario) }
val vanillaTextScenario = registerTestApplication("vanillaTextScenario", "原版文字统一测量、安全裁剪换行、混淆与有界热缓存验收", "club.heiqi.uilib.ui.scene.integration.VanillaTextScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
val fontHostOrderScenario = registerTestApplication("fontHostOrderScenario", "宿主延期票据保序、异常清理与资源lease验收", "club.heiqi.uilib.ui.scene.integration.FontHostOrderScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
val fontDeferredStateScenario = registerTestApplication("fontDeferredStateScenario", "Core与Compatibility延期文字历史状态快照验收（需要设备）", "club.heiqi.uilib.ui.scene.integration.FontDeferredStateScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
val vanillaFontGlScenario = registerTestApplication("vanillaFontGlScenario", "原版世界文字透视、光照、历史状态与图集视图验收（需要设备）", "club.heiqi.uilib.ui.scene.integration.VanillaFontGlScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
    args(providers.gradleProperty("fontWorldAtlasMode").getOrElse("2d"))
}
val fontPlanSamplingScenario = registerTestApplication("fontPlanSamplingScenario", "近裁面可见墨迹、保守采样封顶与GUI快速路径验收", "club.heiqi.uilib.ui.scene.integration.FontPlanSamplingScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
val vanillaFontThreadScenario = registerTestApplication("vanillaFontThreadScenario", "真实安装后Splash线程十个标准入口不接管，主线程保留接管", "club.heiqi.uilib.ui.scene.integration.VanillaFontThreadScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("test").runtimeClasspath
}
tasks.named("check") { dependsOn(vanillaTextScenario, fontHostOrderScenario, fontPlanSamplingScenario, vanillaFontThreadScenario) }
val attributedGlyphGlScenario = registerTestApplication("attributedGlyphGlScenario", "整簇属性字形采样、彩色与装饰真实GL验收（需要设备）", "club.heiqi.uilib.ui.scene.integration.AttributedGlyphGlScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
    args(layout.buildDirectory.dir("font-machine/attributed-gl").get().asFile.absolutePath)
}
val presentationEpochScenario = registerTestApplication("presentationEpochScenario", "多消费者字形需求与总帧上传预算（需要设备）", "club.heiqi.uilib.ui.scene.integration.PresentationEpochScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
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
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + testSupportSources.output + sdlTextInputRuntime + yamlRuntime
    destinationDirectory.set(sdlTextInputClasses)
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    options.release.set(8)
    options.compilerArgs.add("-proc:none")
}
val sdlTextInputScenario = registerTestApplication("sdlTextInputScenario", "真实SDL hidden window、生产Watch观察与scene原子IME（不开游戏）", "club.heiqi.uilib.ui.scene.integration.SdlTextInputScenario", useDefaultLauncher = false) {
    dependsOn(compileSdlTextInputScenario)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    classpath = files(sdlTextInputClasses) + sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sdlTextInputRuntime + fontUnicodeRuntime + yamlRuntime
}

val clientFontServicesScenario = registerTestApplication("clientFontServicesScenario", "共享字体owner、原生framebuffer scene与输入资源生命周期（需要设备）", "club.heiqi.uilib.ui.scene.integration.ClientFontServicesScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
    args(layout.buildDirectory.dir("font-machine/client").get().asFile.absolutePath)
}
val glyphPipelineScenario = registerTestApplication("glyphPipelineScenario", "真实共享定位、后台光栅、有界图集和GL回放对照（需要设备）", "club.heiqi.uilib.ui.scene.integration.GlyphPipelineScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
    args(layout.buildDirectory.dir("font-machine/glyph").get().asFile.absolutePath)
}
val fontTextScenario = registerTestApplication("fontTextScenario", "真实共享Unicode/Bidi/复杂脚本与Emoji17全量候选匹配", "club.heiqi.uilib.ui.scene.integration.FontTextScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
    args(projectDir.absolutePath)
}
val fontRealStyleScenario = registerTestApplication("fontRealStyleScenario", "真实系统字族及可变字体实际样式验收", "club.heiqi.uilib.ui.scene.integration.FontRealStyleScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
val fontRealStyleGlScenario = registerTestApplication("fontRealStyleGlScenario", "真实微软雅黑原版样式GPU图像", "club.heiqi.uilib.ui.scene.integration.FontRealStyleGlScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
    args(layout.buildDirectory.file("font-real-style/font-real-style-msyh.png").get().asFile.absolutePath)
}
val attributedTextScenario = registerTestApplication("attributedTextScenario", "整簇属性文本、格式来源与逻辑采样分离验收", "club.heiqi.uilib.ui.scene.integration.AttributedTextScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
val glyphRasterStyleScenario = registerTestApplication("glyphRasterStyleScenario", "真实原生字形合成样式、墨迹范围与有界资源验收", "club.heiqi.uilib.ui.scene.integration.GlyphRasterStyleScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
    args(file("src/main/resources/assets/qz_uilib/fonts").absolutePath,
        layout.buildDirectory.dir("font-machine/raster-style").get().asFile.absolutePath)
}
tasks.named("check") { dependsOn(nativeFontScenario, nativeFontVisualScenario, packagedUnicodeScenario, fontTextScenario, glyphRasterStyleScenario, attributedTextScenario, fontRealStyleScenario) }

val minecraftDurabilityScenario = registerTestApplication("minecraftDurabilityScenario", "原版Item耐久事实无游戏回归", "club.heiqi.uilib.host.client.image.MinecraftDurabilityScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("test").runtimeClasspath + sourceSets.getByName("patchedMc").output
}
tasks.named("check") { dependsOn(minecraftDurabilityScenario) }

val imageServiceScenario = registerTestApplication("imageServiceScenario", "真实本地HTTP与图像共享、来源切换和scene文档策略", "club.heiqi.uilib.ui.scene.integration.ImageServiceScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
    systemProperty("java.awt.headless", "true")
}
tasks.named("check") { dependsOn(imageServiceScenario) }

val mathFontScenario = registerTestApplication("mathFontScenario", "正式数学字体 MATH 常量、真实伸缩字形及资源代际验收", "club.heiqi.uilib.ui.scene.integration.MathFontScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
val mathLayoutScenario = registerTestApplication("mathLayoutScenario", "自有数学盒的真实字体与嵌入基线净空验收", "club.heiqi.uilib.ui.scene.integration.MathLayoutScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
val documentGlScenario = registerTestApplication("documentGlScenario", "共同文档正文公式图像真实GPU与短视口裁剪（需要设备）", "club.heiqi.uilib.ui.scene.integration.DocumentGlScenario") {
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
    args(layout.buildDirectory.dir("document-gl").get().asFile.absolutePath)
}
val typedParagraphScenario = registerTestApplication("typedParagraphScenario", "typed共同声明、来源映射与DSL转换边界", "club.heiqi.uilib.ui.scene.integration.TypedParagraphScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontUnicodeRuntime + yamlRuntime
}
tasks.named("check") { dependsOn(typedParagraphScenario) }
val inlineBoxScenario = registerTestApplication("inlineBoxScenario", "真实Unicode/Bidi原子对象、安全断行与同代行高", "club.heiqi.uilib.ui.scene.integration.InlineBoxScenario") {
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
tasks.named("check") { dependsOn(inlineBoxScenario) }
val documentSceneScenario = registerTestApplication("documentSceneScenario", "共同文档真实字体、数学图像基线换行与裁剪输入", "club.heiqi.uilib.ui.scene.integration.DocumentSceneScenario") {
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
tasks.named("check") { dependsOn(documentSceneScenario) }
val mathSceneScenario = registerTestApplication("mathSceneScenario", "正式数学图像控件同代几何输入与生命周期验收", "club.heiqi.uilib.ui.scene.integration.MathSceneScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
val mathGlScenario = registerTestApplication("mathGlScenario", "正式数学字形规则线与内联图像真实 GPU 出图验收", "club.heiqi.uilib.ui.scene.integration.MathGlScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
    args(layout.buildDirectory.dir("math-embed/gpu").get().asFile.absolutePath)
}
val mathCatalogScenario = registerTestApplication("mathCatalogScenario", "固定数学符号命令宏目录的真实静态语义消费", "club.heiqi.uilib.ui.scene.integration.MathCatalogScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
val mathMacroScenario = registerTestApplication("mathMacroScenario", "数学词法宏的原文来源作用域与显式文档上下文", "club.heiqi.uilib.ui.scene.integration.MathMacroScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
val paintPrimitiveScenario = registerTestApplication("paintPrimitiveScenario", "同树局部后置绘制顺序与真实斜线GPU图元", "club.heiqi.uilib.ui.scene.integration.PaintPrimitiveScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
    args(layout.buildDirectory.dir("math-complete/paint-gpu").get().asFile.absolutePath)
}
val mathArrayScenario = registerTestApplication("mathArrayScenario", "二维数学环境共享列行及物品基线与显式编号验收", "club.heiqi.uilib.ui.scene.integration.MathArrayScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
val mathDecorationScenario = registerTestApplication("mathDecorationScenario", "数学饰件颜色盒与变换的真实字体有序几何验收", "club.heiqi.uilib.ui.scene.integration.MathDecorationScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
val mathStructureScenario = registerTestApplication("mathStructureScenario", "完整数学函数模式参数和环境的结构来源验收", "club.heiqi.uilib.ui.scene.text.math.MathStructureScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
}
tasks.named("check") { dependsOn(mathFontScenario, mathLayoutScenario, mathSceneScenario, mathCatalogScenario, mathMacroScenario, mathStructureScenario, mathArrayScenario, mathDecorationScenario) }
val mathTextFontScenario = registerTestApplication("mathTextFontScenario", "数学文字真实字族字重姿态及显式候选与资源代际", "club.heiqi.uilib.ui.scene.integration.MathTextFontScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
    args(layout.buildDirectory.dir("math-text-fonts").get().asFile.absolutePath)
}
tasks.named("check") { dependsOn(mathTextFontScenario) }
val mathCoverageScenario = registerTestApplication("mathCoverageScenario", "固定数学全身份语料的真实同树字形与GPU证据", "club.heiqi.uilib.ui.scene.integration.MathCoverageScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
    args(layout.buildDirectory.dir("math-complete/coverage").get().asFile.absolutePath)
}
val imageGlScenario = registerTestApplication("imageGlScenario", "真实RGBA图像fit与有序GL回放及状态/上传预算（需要设备）", "club.heiqi.uilib.ui.scene.integration.ImageGlScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + fontAdapterSources.output + sourceSets.getByName("test").output + fontNativeRuntime + fontUnicodeRuntime + yamlRuntime
    args(layout.buildDirectory.dir("image-system/gpu").get().asFile.absolutePath)
}

// 一次迁移解析属于 MC 宿主，但此真实旧文件路径本身只使用标准 Java I/O。
val legacyFontConfigurationScenario = registerTestApplication("legacyFontConfigurationScenario", "旧字体配置只读迁移及损坏文件失败验收", "club.heiqi.uilib.ui.scene.integration.LegacyFontConfigurationScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + sourceSets.getByName("test").output + fontUnicodeRuntime + yamlRuntime
}
tasks.named("check") { dependsOn(legacyFontConfigurationScenario) }
val fontConfigurationFilesScenario = registerTestApplication("fontConfigurationFilesScenario", "正式宿主YAML权威与Properties/Forge三来源迁移验收", "club.heiqi.uilib.ui.scene.integration.FontConfigurationFilesScenario") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.getByName("main").output + coreSources.output + sourceSets.getByName("test").output + fontUnicodeRuntime + yamlRuntime
}
tasks.named("check") { dependsOn(fontConfigurationFilesScenario) }

// 启动器只依赖测试应用框架；核心生产代码仅经成品 jar 供给，防止开发输出掩盖隔离失败。
val artifactScenarioClasses = layout.buildDirectory.dir("classes/artifact-scenario")
val compileCoreArtifactScenario by tasks.registering(JavaCompile::class) {
    dependsOn(tasks.named(testSupportSources.classesTaskName))
    source("src/test/java/club/heiqi/uilib/ui/scene/integration/CoreArtifactScenario.java")
    classpath = testSupportSources.output
    destinationDirectory.set(artifactScenarioClasses)
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.compilerArgs.add("-proc:none")
}
val coreArtifact = tasks.named<Jar>("coreJar").flatMap { it.archiveFile }
val fontAdapterArtifact = tasks.named<Jar>("fontAdapterJar").flatMap { it.archiveFile }
val coreArtifactScenario = registerTestApplication("coreArtifactScenario", "Java8真实核心成品独立配置布局启动验收", "club.heiqi.uilib.ui.scene.integration.CoreArtifactScenario") {
    dependsOn(compileCoreArtifactScenario, compileCoreSceneScenario, tasks.named("coreJar"))
    classpath = files(artifactScenarioClasses)
    inputs.file(coreArtifact)
    doFirst {
        args(coreArtifact.get().asFile.absolutePath, coreSceneClasses.get().asFile.absolutePath,
            testSupportSources.output.classesDirs.singleFile.absolutePath)
    }
}
val mcArtifactIsolationScenario = registerTestApplication("mcArtifactIsolationScenario", "验证MC成品与独立核心字体适配无重复定义", "club.heiqi.uilib.ui.scene.integration.CoreArtifactScenario") {
    dependsOn(compileCoreArtifactScenario, tasks.named("coreJar"), tasks.named("fontAdapterJar"), tasks.named("jar"), tasks.named("shadowJar"), tasks.named("reobfJar"))
    classpath = files(artifactScenarioClasses)
    val hostArtifacts = listOf("jar", "shadowJar", "reobfJar").map { name ->
        tasks.named(name, org.gradle.api.tasks.bundling.AbstractArchiveTask::class.java).flatMap { it.archiveFile }
    }
    inputs.files(coreArtifact, fontAdapterArtifact, hostArtifacts)
    doFirst {
        args(listOf("--host", coreArtifact.get().asFile.absolutePath, fontAdapterArtifact.get().asFile.absolutePath)
            + hostArtifacts.map { it.get().asFile.absolutePath })
    }
}
val desktopArtifact = tasks.named<Jar>("desktopJar").flatMap { it.archiveFile }
val desktopArtifactScenario = registerTestApplication("desktopArtifactScenario", "扫描正式桌面发行组合的平台与模块字节码隔离", "club.heiqi.uilib.ui.scene.integration.CoreArtifactScenario") {
    dependsOn(compileCoreArtifactScenario, tasks.named("coreJar"), tasks.named("fontAdapterJar"), tasks.named("desktopJar"))
    classpath = files(artifactScenarioClasses)
    inputs.files(coreArtifact, fontAdapterArtifact, desktopArtifact)
    doFirst {
        args("--desktop", coreArtifact.get().asFile.absolutePath, fontAdapterArtifact.get().asFile.absolutePath,
            desktopArtifact.get().asFile.absolutePath)
    }
}
tasks.named("desktopVerify") { dependsOn(desktopArtifactScenario) }
tasks.named("check") { dependsOn(desktopArtifactScenario) }
tasks.named("coreSceneCheck") { dependsOn(coreArtifactScenario) }
tasks.named("check") { dependsOn(coreArtifactScenario, mcArtifactIsolationScenario, packagedUnicodeScenario) }
