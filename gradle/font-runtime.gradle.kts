import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import org.gradle.api.tasks.SourceSetContainer

val sourceSets = extensions.getByType<SourceSetContainer>()
// GPU bindings由宿主提供；CPU字体bindings以嵌套jar隔离发行，不覆盖LWJGL2/3宿主的同名类。
val fontBindings by configurations.creating
val fontNativeRuntime by configurations.creating { extendsFrom(fontBindings) }
val fontUnicodeRuntime by configurations.creating
val isolatedFontRuntime by configurations.creating
val lwjgl2Bindings by configurations.creating
dependencies {
    add(fontUnicodeRuntime.name, "com.ibm.icu:icu4j:76.1")
    add(lwjgl2Bindings.name, "org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209") { isTransitive = false }
    listOf("lwjgl", "lwjgl-freetype", "lwjgl-harfbuzz").forEach {
        add(isolatedFontRuntime.name, "org.lwjgl:$it:3.4.2") { isTransitive = false }
    }
    listOf("natives-windows", "natives-windows-x86", "natives-windows-arm64", "natives-linux", "natives-linux-arm64", "natives-macos", "natives-macos-arm64").forEach { classifier ->
        listOf("lwjgl", "lwjgl-freetype").forEach { module ->
            add(isolatedFontRuntime.name, "org.lwjgl:$module:3.4.2:$classifier") { isTransitive = false }
        }
    }
    listOf("lwjgl", "lwjgl-freetype", "lwjgl-harfbuzz", "lwjgl-opengl", "lwjgl-glfw").forEach {
        add(fontBindings.name, "org.lwjgl:$it:3.4.2") { isTransitive = false }
    }
    val platform = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val nativeClassifier = when {
        platform.contains("win") -> if (arch == "aarch64") "natives-windows-arm64" else if (arch == "x86") "natives-windows-x86" else "natives-windows"
        platform.contains("mac") -> if (arch == "aarch64") "natives-macos-arm64" else "natives-macos"
        else -> if (arch == "aarch64") "natives-linux-arm64" else "natives-linux"
    }
    listOf("lwjgl", "lwjgl-freetype", "lwjgl-opengl", "lwjgl-glfw").forEach {
        add(fontNativeRuntime.name, "org.lwjgl:$it:3.4.2:$nativeClassifier") { isTransitive = false }
    }
}
configurations.named("compileOnly") { extendsFrom(fontBindings) }
// PointerBuffer与BufferUtils须使用LWJGL3核心，不能被旧LWJGL2编译classpath遮蔽。
tasks.named<JavaCompile>("compileJava") { classpath = fontBindings + classpath }
tasks.named<JavaCompile>("compileTestJava") { classpath = fontBindings + classpath }
val isolatedFontResources = layout.buildDirectory.dir("generated/isolated-font")
val packageFontBindings by tasks.registering {
    inputs.files(isolatedFontRuntime)
    outputs.dir(isolatedFontResources)
    doLast {
        val destination = isolatedFontResources.get().dir("assets/qz_uilib/runtime/font").asFile
        destination.mkdirs()
        val artifacts = isolatedFontRuntime.files.sortedBy { it.name }
        // 私有字体固定使用完整的官方JNI实现。3.4.2的Java25多版本FFM在
        // FT_New_Memory_Face的invokePPNNPI生成时发生Operand stack underflow。
        // 仅移除随包字体绑定的版本覆盖，保留根类及全部native；不改宿主LWJGL或全局配置。
        // 升级绑定时重新评估：FFM完成同包Java8/25回归后再恢复多版本覆盖。
        artifacts.forEach { artifact ->
            ZipFile(artifact).use { source ->
                ZipOutputStream(File(destination, artifact.name).outputStream()).use { output ->
                    val entries = source.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.name.startsWith("META-INF/versions/")) {
                            val copy = ZipEntry(entry.name)
                            copy.time = 0L
                            output.putNextEntry(copy)
                            if (!entry.isDirectory) source.getInputStream(entry).use { it.copyTo(output) }
                            output.closeEntry()
                        }
                    }
                }
            }
        }
        File(destination, "index.txt").writeText(artifacts.joinToString("\n") { it.name } + "\n", Charsets.UTF_8)
    }
}
sourceSets.getByName("main").resources.srcDir(isolatedFontResources)
tasks.named("processResources") { dependsOn(packageFontBindings) }
tasks.named<Jar>("sourcesJar") {
    dependsOn(packageFontBindings)
    exclude("assets/qz_uilib/runtime/font/**")
    from("src/lwjgl2/java")
}
val lwjgl2Sources = sourceSets.create("lwjgl2") {
    java.srcDir("src/lwjgl2/java")
    compileClasspath = sourceSets.getByName("main").output + lwjgl2Bindings
    runtimeClasspath = output + compileClasspath
}
tasks.named<JavaCompile>(lwjgl2Sources.compileJavaTaskName) {
    dependsOn(tasks.named("compileJava"))
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.compilerArgs.add("-proc:none")
}
tasks.named<Jar>("jar") { from(lwjgl2Sources.output) }
tasks.named<Jar>("shadowJar") { from(lwjgl2Sources.output) }
