import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaToolchainService

val sourceSets = extensions.getByType<SourceSetContainer>()
val coreSources = sourceSets.getByName("core")
val fontBindings = configurations.getByName("fontBindings")
val lwjgl2Bindings = configurations.getByName("lwjgl2Bindings")

// 每个Java目标内共享同一编译输出；MC和主线独立编译同一算法源。
val glRendererSources = sourceSets.create("glRenderer") {
    compileClasspath = coreSources.output
    runtimeClasspath = output + coreSources.runtimeClasspath
}
val lwjgl2Sources = sourceSets.create("lwjgl2") {
    compileClasspath = glRendererSources.output + lwjgl2Bindings
    runtimeClasspath = output + glRendererSources.runtimeClasspath + lwjgl2Bindings
}

// LWJGL3ify只需要具体binding的逐类标记；MC编译原文件，桌面编译该单文件去标记变体。
// 共享GL实现不复制，旧GL11/Display仍留给MC转换链处理，不能给整个产物标记Aware。
val desktopBindingJava = layout.buildDirectory.dir("generated/desktop-lwjgl3-java")
val prepareDesktopLwjgl3Binding by tasks.registering(Sync::class) {
    from("src/lwjgl3/java") {
        include("club/heiqi/uilib/host/client/gl/Lwjgl3GlApi.java")
        filter { line: String -> if (line.trim() == "@me.eigenraven.lwjgl3ify.api.Lwjgl3Aware") "" else line }
    }
    into(desktopBindingJava)
    filteringCharset = "UTF-8"
}
val lwjgl3Sources = sourceSets.create("lwjgl3") {
    java.setSrcDirs(listOf(desktopBindingJava))
    compileClasspath = glRendererSources.output + fontBindings
    runtimeClasspath = output + glRendererSources.runtimeClasspath + fontBindings
}
listOf(glRendererSources, lwjgl2Sources, lwjgl3Sources).forEach { sources ->
    tasks.named<JavaCompile>(sources.compileJavaTaskName) {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
        options.release.set(8)
        options.compilerArgs.add("-proc:none")
        classpath = sources.compileClasspath
    }
}
tasks.named<JavaCompile>(lwjgl3Sources.compileJavaTaskName) { dependsOn(prepareDesktopLwjgl3Binding) }

val javaToolchains = extensions.getByType<JavaToolchainService>()
val core21 = tasks.named<JavaCompile>("compileCore21Java")
val compileGlRenderer21Java by tasks.registering(JavaCompile::class) {
    dependsOn(core21)
    source(glRendererSources.java)
    classpath = files(core21.flatMap { it.destinationDirectory })
    destinationDirectory.set(layout.buildDirectory.dir("classes/java/glRenderer21"))
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    sourceCompatibility = "21"; targetCompatibility = "21"; options.release.set(21)
    options.compilerArgs.add("-proc:none")
}
val compileLwjgl3Desktop21Java by tasks.registering(JavaCompile::class) {
    dependsOn(prepareDesktopLwjgl3Binding, compileGlRenderer21Java, core21)
    source(lwjgl3Sources.java)
    classpath = files(compileGlRenderer21Java.flatMap { it.destinationDirectory }, core21.flatMap { it.destinationDirectory }) + fontBindings
    destinationDirectory.set(layout.buildDirectory.dir("classes/java/lwjgl3Desktop21"))
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    sourceCompatibility = "21"; targetCompatibility = "21"; options.release.set(21)
    options.compilerArgs.add("-proc:none")
}
val minecraftSources = sourceSets.getByName("main")
minecraftSources.java.srcDir("src/lwjgl3/java")
minecraftSources.compileClasspath += glRendererSources.output + lwjgl2Sources.output
minecraftSources.runtimeClasspath += glRendererSources.output + lwjgl2Sources.output
sourceSets.getByName("test") {
    compileClasspath += glRendererSources.output + lwjgl2Sources.output
    runtimeClasspath += glRendererSources.output + lwjgl2Sources.output
}
listOf("compileJava", "compileTestJava").forEach { name ->
    tasks.named<JavaCompile>(name) { classpath += glRendererSources.output + lwjgl2Sources.output }
}
tasks.withType<Test>().configureEach { classpath += glRendererSources.output + lwjgl2Sources.output }
listOf("jar", "shadowJar").forEach { name ->
    tasks.named<Jar>(name) { from(glRendererSources.output, lwjgl2Sources.output) }
}
