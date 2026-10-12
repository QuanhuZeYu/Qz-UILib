import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.SourceSetContainer

val sourceSets = extensions.getByType<SourceSetContainer>()
val fontUnicodeRuntime = configurations.getByName("fontUnicodeRuntime")
val yamlRuntime = configurations.getByName("yamlRuntime")
val fontBindings = configurations.getByName("fontBindings")
// RFG 的 runClient/runServer 直接读取此配置，而非 sourceSets.main.runtimeClasspath。
configurations.named("runtimeClasspath") { extendsFrom(yamlRuntime) }

// Java源码使用各sourceSet的独立默认根：IDEA会把共享src/main/java从其他模块去重，
// 不理解按Java包include/exclude分配归属；共享根会使core依赖指向无源码模块。
// 正式核心只持有无平台逻辑与内容资源；宿主源码通过输出依赖调用同一份定义。
val coreSources = sourceSets.create("core") {
    resources.srcDir("src/main/resources")
    resources.include("assets/qz_uilib/unicode/**", "assets/qz_uilib/fonts/**", "assets/qz_uilib/math/**")
    compileClasspath = fontUnicodeRuntime + yamlRuntime
    runtimeClasspath = output + compileClasspath
}
val fontAdapterSources = sourceSets.create("fontAdapter") {
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
    resources.exclude("assets/qz_uilib/unicode/**", "assets/qz_uilib/fonts/**", "assets/qz_uilib/math/**", "assets/qz_uilib/runtime/font/**")
    compileClasspath += coreSources.output + fontAdapterSources.output + fontUnicodeRuntime + yamlRuntime
    runtimeClasspath += coreSources.output + fontAdapterSources.output + fontUnicodeRuntime + yamlRuntime
}
sourceSets.getByName("lwjgl2") {
    compileClasspath += coreSources.output + fontAdapterSources.output + yamlRuntime
    runtimeClasspath += coreSources.output + fontAdapterSources.output + fontUnicodeRuntime + yamlRuntime
}
sourceSets.getByName("test") {
    compileClasspath += coreSources.output + fontAdapterSources.output + fontUnicodeRuntime + yamlRuntime
    runtimeClasspath += coreSources.output + fontAdapterSources.output + fontUnicodeRuntime + yamlRuntime
}
// convention 对主/测试编译任务另设 classpath，必须接到实际 JavaCompile 才能生效。
listOf("compileJava", "compileTestJava").forEach { name ->
    tasks.named<JavaCompile>(name) {
        dependsOn(tasks.named(coreSources.classesTaskName), tasks.named(fontAdapterSources.classesTaskName))
        classpath += coreSources.output + fontAdapterSources.output + fontUnicodeRuntime + yamlRuntime
    }
}
tasks.withType<Test>().configureEach {
    classpath += coreSources.output + fontAdapterSources.output + fontUnicodeRuntime + yamlRuntime
}

// ICU 与 YAML 沿用私有 shadow 包名；完整资源保留，不以最小化误删字典/反射入口。
val coreJar by tasks.registering(ShadowJar::class) {
    group = "build"
    description = "生成可独立运行的 Java8 UILib 核心（含私有 ICU、YAML 和字体/Unicode 资源）"
    archiveClassifier.set("core")
    from(coreSources.output)
    configurations.set(listOf(fontUnicodeRuntime, yamlRuntime))
    minimizeJar.set(false)
    enableAutoRelocation.set(false)
    relocate("com.ibm.icu", "club.heiqi.uilib.shadow.com.ibm.icu")
    relocate("org.yaml.snakeyaml", "club.heiqi.uilib.shadow.org.yaml.snakeyaml")
    exclude("META-INF/versions/**", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
val fontAdapterJar by tasks.registering(Jar::class) {
    group = "build"
    description = "生成独立 CPU 字体适配及私有 native 绑定产物"
    archiveClassifier.set("font-adapter")
    from(fontAdapterSources.output)
}
// 宿主引用 ICU/YAML 但不重复携带它们；发行组合由核心产物供给，开发运行使用未重定位输出。
tasks.named<ShadowJar>("shadowJar") {
    relocate("com.ibm.icu", "club.heiqi.uilib.shadow.com.ibm.icu")
    relocate("org.yaml.snakeyaml", "club.heiqi.uilib.shadow.org.yaml.snakeyaml")
}
tasks.named("assemble") { dependsOn(coreJar, fontAdapterJar) }
