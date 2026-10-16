import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaToolchainService

val sourceSets = extensions.getByType<SourceSetContainer>()
val javaToolchains = extensions.getByType<JavaToolchainService>()
val core21 = tasks.named<JavaCompile>("compileCore21Java")
val coreSources = sourceSets.getByName("core")
// 只供媒体自己的编译和嵌套装配；不扩展MC、核心或窗口的公共类路径。
val mediaBindings by configurations.creating
val mediaNativeRuntime by configurations.creating
val isolatedMediaRuntime by configurations.creating { extendsFrom(mediaBindings, mediaNativeRuntime) }
dependencies {
    add(mediaBindings.name, "org.bytedeco:javacpp:1.5.10") { isTransitive = false }
    add(mediaBindings.name, "org.bytedeco:ffmpeg:6.1.1-1.5.10") { isTransitive = false }
    add(mediaBindings.name, "io.lindstrom:m3u8-parser:0.29") { isTransitive = false }
    listOf("lwjgl", "lwjgl-openal").forEach { module ->
        add(mediaBindings.name, "org.lwjgl:$module:3.4.2") { isTransitive = false }
        add(mediaNativeRuntime.name, "org.lwjgl:$module:3.4.2:natives-windows") { isTransitive = false }
    }
    add(mediaNativeRuntime.name, "org.bytedeco:javacpp:1.5.10:windows-x86_64") { isTransitive = false }
    add(mediaNativeRuntime.name, "org.bytedeco:ffmpeg:6.1.1-1.5.10:windows-x86_64") { isTransitive = false }
}
val mediaResources = layout.buildDirectory.dir("generated/isolated-media")
val packageMediaBindings by tasks.registering {
    val bindings = objects.fileCollection().from(isolatedMediaRuntime)
    val target = mediaResources
    inputs.files(bindings)
    outputs.dir(target)
    doLast {
        val directory = target.get().dir("assets/qz_uilib/runtime/media").asFile
        directory.deleteRecursively(); directory.mkdirs()
        val index = ArrayList<String>()
        for (artifact in bindings.files.sortedBy { it.name }) {
            val output = File(directory, artifact.name)
            if (artifact.name.startsWith("lwjgl-")) {
                // 与字体一样固定官方根JNI；不使用私有包的Java25 FFM覆盖，不改宿主配置。
                ZipFile(artifact).use { source -> ZipOutputStream(output.outputStream()).use { zip ->
                    for (entry in source.entries().asSequence()) if (!entry.name.startsWith("META-INF/versions/")) {
                        zip.putNextEntry(ZipEntry(entry.name).apply { time = 0L })
                        if (!entry.isDirectory) source.getInputStream(entry).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                } }
            } else artifact.copyTo(output, overwrite = true)
            val hash = MessageDigest.getInstance("SHA-256").digest(output.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
            index.add(artifact.name + " " + hash)
        }
        File(directory, "index.txt").writeText(index.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }
}
val mediaAdapterSources = sourceSets.create("mediaAdapter") {
    resources.srcDir(mediaResources)
    compileClasspath = files(core21.flatMap { it.destinationDirectory }) + mediaBindings
    runtimeClasspath = output + files(core21.flatMap { it.destinationDirectory })
}
tasks.named(mediaAdapterSources.processResourcesTaskName) { dependsOn(packageMediaBindings) }
tasks.named<JavaCompile>(mediaAdapterSources.compileJavaTaskName) {
    dependsOn(core21)
    classpath = mediaAdapterSources.compileClasspath
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    sourceCompatibility = "21"; targetCompatibility = "21"; options.release.set(21)
    options.compilerArgs.add("-proc:none")
}
val compileMediaAdapterLegacyJava by tasks.registering(JavaCompile::class) {
    dependsOn(tasks.named(coreSources.classesTaskName))
    source(mediaAdapterSources.java)
    classpath = coreSources.output + mediaBindings
    destinationDirectory.set(layout.buildDirectory.dir("classes/java/mediaAdapterLegacy"))
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    sourceCompatibility = "1.8"; targetCompatibility = "1.8"; options.release.set(8)
    options.compilerArgs.add("-proc:none")
}
val mediaAdapterJar by tasks.registering(Jar::class) {
    group = "build"; archiveClassifier.set("media-adapter")
    from(mediaAdapterSources.output)
}
val mediaAdapterLegacyJar by tasks.registering(Jar::class) {
    group = "build"; archiveClassifier.set("media-adapter-java8")
    dependsOn(compileMediaAdapterLegacyJava, tasks.named(mediaAdapterSources.processResourcesTaskName))
    from(compileMediaAdapterLegacyJava.flatMap { it.destinationDirectory }, mediaAdapterSources.output.resourcesDir)
}
// 本地发行组成，不向convention新增publication或更改POM。
val minecraftLegacyArtifacts = files(tasks.named<Jar>("coreLegacyJar").flatMap { it.archiveFile },
    tasks.named<Jar>("fontAdapterLegacyJar").flatMap { it.archiveFile }, mediaAdapterLegacyJar.flatMap { it.archiveFile })
extensions.extraProperties.set("minecraftLegacyArtifacts", minecraftLegacyArtifacts)
tasks.named("assemble") { dependsOn(mediaAdapterJar, mediaAdapterLegacyJar) }

// 同一测试源独立编译；生产始终从正式Jar供给，测试包没有外部runtime依赖。
val testSupport = files(provider { sourceSets.getByName("testSupport").output })
val gl21 = tasks.named<JavaCompile>("compileGlRenderer21Java")
val mediaTestSources = sourceSets.create("mediaTest") {
    compileClasspath = files(core21.flatMap { it.destinationDirectory }, gl21.flatMap { it.destinationDirectory }) + mediaAdapterSources.output + testSupport
}
tasks.named<JavaCompile>(mediaTestSources.compileJavaTaskName) {
    dependsOn(core21, gl21, tasks.named(mediaAdapterSources.classesTaskName), "testSupportClasses")
    classpath = mediaTestSources.compileClasspath
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    sourceCompatibility = "21"; targetCompatibility = "21"; options.release.set(21)
    options.compilerArgs.add("-proc:none")
}
val compileMediaTestLegacyJava by tasks.registering(JavaCompile::class) {
    dependsOn("coreClasses", "glRendererClasses", compileMediaAdapterLegacyJava, "testSupportClasses")
    source(mediaTestSources.java)
    classpath = coreSources.output + sourceSets.getByName("glRenderer").output + files(compileMediaAdapterLegacyJava.flatMap { it.destinationDirectory }) + testSupport
    destinationDirectory.set(layout.buildDirectory.dir("classes/java/mediaTestLegacy"))
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    sourceCompatibility = "1.8"; targetCompatibility = "1.8"; options.release.set(8)
    options.compilerArgs.add("-proc:none")
}
val mediaTestJar by tasks.registering(Jar::class) {
    group = "verification"; archiveClassifier.set("media-test")
    from(mediaTestSources.output, testSupport)
}
val mediaTestLegacyJar by tasks.registering(Jar::class) {
    group = "verification"; archiveClassifier.set("media-test-java8")
    dependsOn(compileMediaTestLegacyJava)
    dependsOn(tasks.named(mediaTestSources.processResourcesTaskName))
    from(compileMediaTestLegacyJava.flatMap { it.destinationDirectory }, mediaTestSources.output.resourcesDir, testSupport)
}
