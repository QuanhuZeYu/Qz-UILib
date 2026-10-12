import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.SourceSetContainer

val sourceSets = extensions.getByType<SourceSetContainer>()
val coreContentRuntime = configurations.getByName("coreContentRuntime")
val fontBindings = configurations.getByName("fontBindings")
// RFG 的 runClient/runServer 直接读取此配置，而非 sourceSets.main.runtimeClasspath。
configurations.named("runtimeClasspath") { extendsFrom(coreContentRuntime) }

// Java源码使用各sourceSet的独立默认根：IDEA会把共享src/main/java从其他模块去重，
// 不理解按Java包include/exclude分配归属；共享根会使core依赖指向无源码模块。
// 正式核心只持有无平台逻辑与内容资源；宿主源码通过输出依赖调用同一份定义。
val coreSources = sourceSets.create("core") {
    compileClasspath = coreContentRuntime
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
    // GTNH/RFG沿用main编译任务；实际源码根明确归Minecraft宿主。
    java.setSrcDirs(listOf("src/minecraft/java"))
    resources.setSrcDirs(listOf("src/minecraft/resources"))
    compileClasspath += coreSources.output + fontAdapterSources.output + coreContentRuntime
    runtimeClasspath += coreSources.output + fontAdapterSources.output + coreContentRuntime
}
sourceSets.getByName("test") {
    compileClasspath += coreSources.output + fontAdapterSources.output + coreContentRuntime
    runtimeClasspath += coreSources.output + fontAdapterSources.output + coreContentRuntime
}
// convention 对主/测试编译任务另设 classpath，必须接到实际 JavaCompile 才能生效。
listOf("compileJava", "compileTestJava").forEach { name ->
    tasks.named<JavaCompile>(name) {
        dependsOn(tasks.named(coreSources.classesTaskName), tasks.named(fontAdapterSources.classesTaskName))
        classpath += coreSources.output + fontAdapterSources.output + coreContentRuntime
    }
}
tasks.withType<Test>().configureEach {
    classpath += coreSources.output + fontAdapterSources.output + coreContentRuntime
}

// RFG开发启动只装配dev-preshadow宿主jar，不读取main.runtimeClasspath。
// 共享GL与绑定已在该jar中；独立核心和字体适配补原始输出，现代ICU优先于MC旧库。
listOf("runClient", "runServer").forEach { name ->
    tasks.named<JavaExec>(name) {
        dependsOn(tasks.named(coreSources.classesTaskName), tasks.named(fontAdapterSources.classesTaskName))
        classpath = coreContentRuntime + coreSources.output + fontAdapterSources.output + classpath
    }
}

// 共同内容依赖统一私有shadow；完整保留Unicode、实体表等资源，不最小化误删动态入口。
val coreJar by tasks.registering(ShadowJar::class) {
    group = "build"
    description = "生成可独立运行的 Java8 UILib 核心（含私有内容依赖及字体/Unicode 资源）"
    archiveClassifier.set("core")
    from(coreSources.output)
    configurations.set(listOf(coreContentRuntime))
    minimizeJar.set(false)
    enableAutoRelocation.set(false)
    relocate("com.ibm.icu", "club.heiqi.uilib.shadow.com.ibm.icu")
    relocate("org.yaml.snakeyaml", "club.heiqi.uilib.shadow.org.yaml.snakeyaml")
    relocate("org.commonmark", "club.heiqi.uilib.shadow.org.commonmark")
    relocate("org.jsoup", "club.heiqi.uilib.shadow.org.jsoup")
    relocate("org.nibor.autolink", "club.heiqi.uilib.shadow.org.nibor.autolink")
    exclude("META-INF/versions/**", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
val fontAdapterJar by tasks.registering(Jar::class) {
    group = "build"
    description = "生成独立 CPU 字体适配及私有 native 绑定产物"
    archiveClassifier.set("font-adapter")
    from(fontAdapterSources.output)
}
// 宿主的内容依赖统一由核心产物供给，不重复携带；开发运行使用未重定位输出。
tasks.named<ShadowJar>("shadowJar") {
    relocate("com.ibm.icu", "club.heiqi.uilib.shadow.com.ibm.icu")
    relocate("org.yaml.snakeyaml", "club.heiqi.uilib.shadow.org.yaml.snakeyaml")
    relocate("org.commonmark", "club.heiqi.uilib.shadow.org.commonmark")
    relocate("org.jsoup", "club.heiqi.uilib.shadow.org.jsoup")
    relocate("org.nibor.autolink", "club.heiqi.uilib.shadow.org.nibor.autolink")
}
tasks.named("assemble") { dependsOn(coreJar, fontAdapterJar) }
