import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaToolchainService
import java.io.File
import java.util.zip.ZipFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode

val sourceSets = extensions.getByType<SourceSetContainer>()
val javaToolchains = extensions.getByType<JavaToolchainService>()
val main = sourceSets.getByName("main")
val tests = sourceSets.getByName("test")
val core = configurations.getByName("uilibCoreDevelopment")
val font = configurations.getByName("uilibFont8")
val gl = configurations.getByName("uilibGl8")
val lwjgl2 = configurations.getByName("uilibLwjgl2")
val lwjgl3 = configurations.getByName("uilibLwjgl3_8")
val content = configurations.getByName("coreContentRuntime")
val support = configurations.getByName("uilibCoreVerification")
val platformSupport = configurations.getByName("uilibPlatformVerification")
val lwjgl2Bindings = configurations.getByName("lwjgl2Bindings")
val nativeRuntime = configurations.getByName("fontNativeRuntime")
val independent = main.output + core + font + gl + lwjgl2 + lwjgl3 + tests.output + support + platformSupport + nativeRuntime + content
val fullRuntime = content + tests.runtimeClasspath + sourceSets.getByName("patchedMc").output

tasks.withType<org.gradle.api.plugins.quality.Checkstyle>().configureEach { maxHeapSize = "2g" }
tasks.withType<Test>().configureEach { maxHeapSize = "1024m"; failOnNoDiscoveredTests.set(false) }
fun scenario(name: String, summary: String, application: String, runtime: org.gradle.api.file.FileCollection = independent, java: Int = 8, configure: JavaExec.() -> Unit = {}) = tasks.register<JavaExec>(name) {
    group = "verification"; description = summary; mainClass.set(application)
    dependsOn("testClasses")
    classpath = runtime
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(java)) })
    jvmArgs("-Dfile.encoding=UTF-8")
    maxHeapSize = "1024m"
    configure()
}
val prefix = "club.heiqi.uilib.ui.scene.integration."
val launchRoot = layout.projectDirectory.asFile.toPath()
fun launchPath(file: File): String {
    val path = file.toPath().toAbsolutePath()
    return if (launchRoot.root == path.root) launchRoot.relativize(path).toString() else path.toString()
}
val cpu = listOf(
    Triple("vanillaTextScenario", "原版文字统一测量裁剪换行与热缓存", "VanillaTextScenario"),
    Triple("fontHostOrderScenario", "宿主延期票据保序异常清理与lease", "FontHostOrderScenario"),
    Triple("fontRealStyleScenario", "真实系统字族可变字体实际样式", "FontRealStyleScenario"),
    Triple("attributedTextScenario", "整簇属性格式来源与采样分离", "AttributedTextScenario"),
    Triple("legacyFontConfigurationScenario", "旧字体文件迁移及损坏失败", "LegacyFontConfigurationScenario"),
    Triple("fontConfigurationFilesScenario", "宿主YAML权威及三来源迁移", "FontConfigurationFilesScenario"),
    Triple("resourceManagerFontSourcesScenario", "游戏资源字体快照与退休代际", "ResourceManagerFontSourcesScenario")
).map { (name, description, type) -> scenario(name, description, prefix + type) }
val fontProjection = scenario("fontProjectionScenario", "透视矩阵与有界光栅档位", prefix + "FontProjectionScenario") { args("cpu") }
val fontText = scenario("fontTextScenario", "真实共享Unicode/Bidi/脚本与Emoji17全量匹配", prefix + "FontTextScenario") {
    dependsOn("extractVerificationResources")
    args(layout.buildDirectory.dir("verification-inputs").get().asFile.absolutePath)
}
val vanillaThread = scenario("vanillaFontThreadScenario", "Splash线程标准入口隔离", prefix + "VanillaFontThreadScenario", fullRuntime)
val durability = scenario("minecraftDurabilityScenario", "实际Item耐久事实", "club.heiqi.uilib.host.client.image.MinecraftDurabilityScenario", fullRuntime)
val asset = scenario("coreAssetResourceScenario", "MC资源链Core资产及用户覆盖重载", "club.heiqi.uilib.host.client.font.CoreAssetResourceScenario", fullRuntime)
val itemFacts = scenario("minecraftItemImageFactsScenario", "真实MC物品提示转换、palette快照与共同绘制输入", "club.heiqi.uilib.host.client.image.MinecraftItemImageFactsScenario")
tasks.named("check") { dependsOn(cpu, fontProjection, fontText, vanillaThread, durability, asset, itemFacts) }

// 保留显式 GPU 场景入口；普通 check 维持原来的设备需求范围。
scenario("fontContextGlScenario", "有效字体上下文Core与Compatibility捕获", prefix + "FontProjectionScenario")
scenario("fontRealStyleGlScenario", "真实微软雅黑原版样式GPU图像", prefix + "FontRealStyleGlScenario") { args(layout.buildDirectory.file("font-real-style/font-real-style-msyh.png").get().asFile.absolutePath) }

val lwjgl2NativeDirectory = layout.buildDirectory.dir("lwjgl2-test/natives")
val nativeJars = configurations.named("compileClasspath").map { configuration -> configuration.files.filter { it.name.startsWith("lwjgl-platform-") }.sortedBy { it.name } }
val extractNatives = tasks.register<Sync>("extractLwjgl2TestNatives") {
    into(lwjgl2NativeDirectory)
    from(nativeJars.map { jars -> jars.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
val legacyTests = sourceSets.create("lwjgl2Test") {
    compileClasspath = main.output + core + font + gl + lwjgl2 + lwjgl2Bindings + support + platformSupport + configurations.getByName("uilibMedia8") + content + main.compileClasspath.filter { !it.name.contains("jabel") }
    runtimeClasspath = output + main.output + core + font + gl + lwjgl2 + lwjgl2Bindings + support + platformSupport + content
}
tasks.named<JavaCompile>(legacyTests.compileJavaTaskName) {
    sourceCompatibility = "1.8"; targetCompatibility = "1.8"; options.release.set(8)
    options.compilerArgs.add("-proc:none"); options.annotationProcessorPath = files()
}
fun display(name: String, description: String, application: String, full: Boolean = false) = scenario(name, description, application, if(full) legacyTests.runtimeClasspath + sourceSets.getByName("patchedMc").output + main.compileClasspath.filter { !it.name.contains("jabel") } else legacyTests.runtimeClasspath) {
    dependsOn(legacyTests.classesTaskName, extractNatives)
}
val switch = display("minecraftScreenSwitchScenario", "实际MC屏幕当帧切页与旧输入隔离", prefix + "MinecraftScreenSwitchScenario", true)
val nativeInput = display("minecraftNativeInputScenario", "无窗口真实历史按压释放与命令文本顺序", prefix + "MinecraftScreenSwitchScenario", true)
nativeInput.configure { args("native-only") }
val cleanup = display("minecraftScreenCleanupScenario", "正式session与客户端停机首错和依赖清理", "club.heiqi.uilib.ui.host.minecraft.MinecraftScreenCleanupScenario", true)
val shutdown = display("minecraftFontShutdownScenario", "字体桥失败仍结束真实owner", prefix + "MinecraftFontShutdownScenario", true)
display("imageLwjgl2Scenario", "LWJGL2实际IMAGE像素及item scope", prefix + "ImageLwjgl2Scenario")
// screenSwitch 需要真实显示栈；cleanup/nativeInput 为无窗口原生闭环，保留在 check。
tasks.named("check") { dependsOn(cleanup, nativeInput) }
tasks.register<Jar>("lwjgl2TestJar") { group = "verification"; archiveClassifier.set("lwjgl2-test"); from(legacyTests.output) }

val productionNames = listOf("jar", "shadowJar", "reobfJar")
val hostArtifacts = productionNames.map { tasks.named(it, AbstractArchiveTask::class.java).flatMap { task -> task.archiveFile } }
val settings = scenario("forgeSettingsEntryScenario", "真实Forge解析成品元数据与配置入口", prefix + "ForgeSettingsEntryScenario", fullRuntime) {
    dependsOn(productionNames); inputs.files(hostArtifacts)
    doFirst { args(hostArtifacts.map { launchPath(it.get().asFile) }) }
}
val coreArtifact = configurations.getByName("uilibCore8")
val fontArtifact = configurations.getByName("uilibFont8")
val isolation = scenario("mcArtifactIsolationScenario", "MC成品与独立Core字体无重复定义", prefix + "CoreArtifactScenario", support) {
    dependsOn(productionNames); inputs.files(coreArtifact, fontArtifact, hostArtifacts)
    doFirst { args(listOf("--host") + coreArtifact.files.map { launchPath(it) } + fontArtifact.files.map { launchPath(it) } + hostArtifacts.map { launchPath(it.get().asFile) }) }
}
tasks.named("check") { dependsOn(settings, isolation) }
// Java8 正式宿主与独立字体/媒体必须在同一 LWJGL2 JVM 共存；消费者由 Platform 唯一维护。
val mediaArtifact = configurations.getByName("uilibMedia8")
val packagedRuntime = support + platformSupport + configurations.getByName("uilibGallery") + coreArtifact + fontArtifact + mediaArtifact + files(tasks.named<Jar>("jar").flatMap { it.archiveFile }) + lwjgl2Bindings
val packagedMedia = scenario("packagedMediaJava8", "正式Java8字体窗口媒体与宿主音频实际共存", "club.heiqi.uilib.testing.media.PackagedMediaScenario", packagedRuntime) {
    dependsOn("jar", extractNatives)
}
scenario("lwjgl2VideoVerify", "原LWJGL2同源GPU与私有媒体实际共存", prefix + "Lwjgl2VideoVerification", packagedRuntime) {
    dependsOn("jar", extractNatives)
}
val hostDisplay = display("lwjgl2HostScenario", "LWJGL2默认Display及正式字体GPU像素", prefix + "Lwjgl2HostScenario")
hostDisplay.configure {
    args(providers.gradleProperty("fontWorldAtlasMode").getOrElse("2d"))
    providers.gradleProperty("fontLegacyJava").orNull?.let { legacyJava ->
        javaLauncher.unset()
        setExecutable(legacyJava)
    }
}
// packagedMediaJava8 需要真实窗口与音频输出，改为显式设备入口。

// 保留标准运行classpath门禁，但不执行任何游戏任务。
val runtime = files(listOf("runClient", "runServer").map { tasks.named<JavaExec>(it).get().classpath })
tasks.named("verifyRunClasspathIsolation") {
    dependsOn("classes"); inputs.files(runtime)
    doLast {
        fun major(input: java.io.InputStream, name: String) { java.io.DataInputStream(input).use { data ->
            check(data.readInt() == 0xCAFEBABE.toInt()); data.readUnsignedShort()
            val version = data.readUnsignedShort(); check(version == 52) { "MC运行classpath含错误字节码$name major=$version" }
        } }
        runtime.files.forEach { file ->
            if(file.isDirectory) File(file, "club/heiqi/uilib").takeIf { it.isDirectory }?.walkTopDown()?.filter { it.isFile && it.extension == "class" }?.forEach { major(it.inputStream(), it.path) }
            else if(file.isFile && file.extension == "jar") ZipFile(file).use { zip -> zip.entries().asSequence().filter { it.name.startsWith("club/heiqi/uilib/") && !it.name.startsWith("club/heiqi/uilib/shadow/") && it.name.endsWith(".class") }.forEach { major(zip.getInputStream(it), file.name + ":" + it.name) } }
        }
    }
}
// 显式设备验收聚合：窗口、GPU 与显示栈场景需要用户授权的真实设备，不进入默认 build/check。
tasks.register("deviceCheck") {
    group = "verification"
    description = "显式设备验收：真实窗口、GPU 与显示栈场景"
    dependsOn(switch, shutdown, packagedMedia,
            "fontContextGlScenario", "fontRealStyleGlScenario",
            "imageLwjgl2Scenario", "lwjgl2HostScenario", "lwjgl2VideoVerify")
}

// Java8 Windows native loader无法加载本机中文绝对DLL路径；只在验证进程边界复制到唯一临时目录。
// 每个消费者在自己的finalizer释放临时副本，生产加载器与解析产物不改动。
listOf("minecraftScreenSwitchScenario", "minecraftNativeInputScenario", "minecraftScreenCleanupScenario", "minecraftFontShutdownScenario", "imageLwjgl2Scenario", "lwjgl2HostScenario", "lwjgl2VideoVerify", "packagedMediaJava8").forEach { taskName ->
    val runtimeDirectory = objects.property(File::class.java)
    val cleanup = tasks.register(taskName + "CleanupNatives") {
        doLast { runtimeDirectory.orNull?.let { temporary -> delete(temporary) } }
    }
    tasks.named<JavaExec>(taskName) {
        dependsOn(extractNatives)
        doFirst {
            val temporary = java.nio.file.Files.createTempDirectory("qz-uilib-minecraft-lwjgl2-").toFile()
            runtimeDirectory.set(temporary)
            copy { from(lwjgl2NativeDirectory); into(temporary) }
            jvmArgs("-Djava.library.path=" + temporary.absolutePath)
        }
        finalizedBy(cleanup)
    }
}
