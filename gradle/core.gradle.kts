import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.SourceSetContainer

val sourceSets = extensions.getByType<SourceSetContainer>()
val fontUnicodeRuntime = configurations.getByName("fontUnicodeRuntime")
val fontBindings = configurations.getByName("fontBindings")
val corePackages = arrayOf("club/heiqi/uilib/ui/scene/**", "club/heiqi/uilib/ui/reactive/**", "club/heiqi/uilib/ui/config/**")

// 正式核心只持有无平台逻辑与内容资源；宿主源码通过输出依赖调用同一份定义。
val coreSources = sourceSets.create("core") {
    java.srcDir("src/main/java")
    java.include(*corePackages)
    resources.srcDir("src/main/resources")
    resources.include("assets/qz_uilib/unicode/**", "assets/qz_uilib/fonts/**")
    compileClasspath = fontUnicodeRuntime
    runtimeClasspath = output + compileClasspath
}
val fontAdapterSources = sourceSets.create("fontAdapter") {
    java.srcDir("src/main/java")
    java.include("club/heiqi/uilib/host/font/**")
    resources.srcDir(layout.buildDirectory.dir("generated/isolated-font"))
    compileClasspath = coreSources.output + fontBindings
    runtimeClasspath = output + coreSources.runtimeClasspath + configurations.getByName("fontNativeRuntime")
}
tasks.named(fontAdapterSources.processResourcesTaskName) { dependsOn("packageFontBindings") }
listOf(coreSources, fontAdapterSources).forEach { sources ->
    tasks.named<JavaCompile>(sources.compileJavaTaskName) {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
        // 沿用 convention 的现代 javac 与 release8，避免 Java8 编译器不支持 --release。
        options.release.set(8)
        options.compilerArgs.add("-proc:none")
    }
}
sourceSets.getByName("main") {
    java.exclude(*corePackages, "club/heiqi/uilib/host/font/**")
    resources.exclude("assets/qz_uilib/unicode/**", "assets/qz_uilib/fonts/**", "assets/qz_uilib/runtime/font/**")
    compileClasspath += coreSources.output + fontAdapterSources.output + fontUnicodeRuntime
    runtimeClasspath += coreSources.output + fontAdapterSources.output + fontUnicodeRuntime
}
sourceSets.getByName("lwjgl2") {
    compileClasspath += coreSources.output + fontAdapterSources.output
    runtimeClasspath += coreSources.output + fontAdapterSources.output + fontUnicodeRuntime
}
sourceSets.getByName("test") {
    compileClasspath += coreSources.output + fontAdapterSources.output + fontUnicodeRuntime
    runtimeClasspath += coreSources.output + fontAdapterSources.output + fontUnicodeRuntime
}
// convention 对主/测试编译任务另设 classpath，必须接到实际 JavaCompile 才能生效。
listOf("compileJava", "compileTestJava").forEach { name ->
    tasks.named<JavaCompile>(name) {
        dependsOn(tasks.named(coreSources.classesTaskName), tasks.named(fontAdapterSources.classesTaskName))
        classpath += coreSources.output + fontAdapterSources.output + fontUnicodeRuntime
    }
}
tasks.withType<Test>().configureEach {
    classpath += coreSources.output + fontAdapterSources.output + fontUnicodeRuntime
}

// ICU 隔离沿用宿主现有 shadow 包名；完整资源保留，不以最小化误删字典/反射入口。
val coreJar by tasks.registering(ShadowJar::class) {
    group = "build"
    description = "生成可独立运行的 Java8 UILib 核心（含私有 ICU 和字体/Unicode 资源）"
    archiveClassifier.set("core")
    from(coreSources.output)
    configurations.set(listOf(fontUnicodeRuntime))
    minimizeJar.set(false)
    enableAutoRelocation.set(false)
    relocate("com.ibm.icu", "club.heiqi.uilib.shadow.com.ibm.icu")
    exclude("META-INF/versions/**", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
val fontAdapterJar by tasks.registering(Jar::class) {
    group = "build"
    description = "生成独立 CPU 字体适配及私有 native 绑定产物"
    archiveClassifier.set("font-adapter")
    from(fontAdapterSources.output)
}
// 宿主引用 ICU 但不重复携带它；发行组合由核心产物供给，开发运行使用未重定位输出。
tasks.named<ShadowJar>("shadowJar") {
    relocate("com.ibm.icu", "club.heiqi.uilib.shadow.com.ibm.icu")
}
tasks.named("assemble") { dependsOn(coreJar, fontAdapterJar) }
