import org.gradle.api.tasks.SourceSetContainer
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.util.zip.ZipFile

val sourceSets = extensions.getByType<SourceSetContainer>()
val development = configurations.getByName("uilibCoreDevelopment")
val font = configurations.getByName("uilibFont8")
val gl = configurations.getByName("uilibGl8")
val lwjgl2 = configurations.getByName("uilibLwjgl2")
val lwjgl3 = configurations.getByName("uilibLwjgl3_8")
val content = configurations.getByName("coreContentRuntime")
val bindings = configurations.getByName("fontBindings")
val adapters = font + gl + lwjgl2 + lwjgl3
// RFB只提供可选编译API；独立配置避免宿主api/dev的版本选择提升此固定缓存。
val nativeInputRfbApi = configurations.create("nativeInputRfbApi") { isCanBeConsumed = false; isTransitive = false }
dependencies.add(nativeInputRfbApi.name, "com.github.GTNewHorizons:lwjgl3ify:3.0.20:forgePatches")
val main = sourceSets.getByName("main")
main.java.setSrcDirs(listOf("src/minecraft/java", "src/minecraftShared/java", "src/minecraftResource/java"))
main.resources.setSrcDirs(listOf("src/minecraft/resources"))
main.compileClasspath += development + adapters + content + bindings + nativeInputRfbApi
main.runtimeClasspath += development + adapters + content
sourceSets.getByName("test") {
    compileClasspath += development + adapters + content + bindings + configurations.getByName("uilibCoreVerification") + configurations.getByName("uilibPlatformVerification")
    runtimeClasspath += development + adapters + content + configurations.getByName("fontNativeRuntime") + configurations.getByName("uilibCoreVerification") + configurations.getByName("uilibPlatformVerification")
}
listOf("compileJava", "compileTestJava").forEach { name ->
    tasks.named<JavaCompile>(name) {
        // LWJGL3 PointerBuffer/BufferUtils 先于 MC 的 LWJGL2；旧 GL11/Display 仍走宿主转换链。
        classpath = bindings + development + adapters + content + classpath
    }
}
tasks.named<JavaCompile>("compileTestJava") {
    classpath += configurations.getByName("uilibCoreVerification") + configurations.getByName("uilibPlatformVerification")
}
tasks.withType<Test>().configureEach {
    classpath = development + adapters + content + configurations.getByName("fontNativeRuntime") + configurations.getByName("uilibCoreVerification") + configurations.getByName("uilibPlatformVerification") + classpath
}
configurations.named("runtimeClasspath") { extendsFrom(content) }
listOf("runClient", "runServer").forEach { name ->
    tasks.named<JavaExec>(name) { classpath = content + development + font + configurations.getByName("uilibMedia8") + classpath }
}

// Platform 只编译一个无 MC 标记的 binding。1.7.10 装配时仅给该 class 加官方标记，方法直接保留原字节码。
val bindingClass = "club/heiqi/uilib/host/client/gl/Lwjgl3GlApi.class"
val markedDirectory = layout.buildDirectory.dir("generated/minecraft-lwjgl3")
val prepareMinecraftLwjgl3Binding by tasks.registering {
    inputs.files(lwjgl3)
    outputs.dir(markedDirectory)
    doLast {
        ZipFile(lwjgl3.singleFile).use { jar ->
            val reader = ClassReader(jar.getInputStream(jar.getEntry(bindingClass)).use { it.readBytes() })
            val writer = ClassWriter(reader, 0)
            reader.accept(object : ClassVisitor(Opcodes.ASM9, writer) {
                override fun visitAnnotation(descriptor: String, visible: Boolean): org.objectweb.asm.AnnotationVisitor? {
                    check(descriptor != "Lme/eigenraven/lwjgl3ify/api/Lwjgl3Aware;") { "Platform binding必须保持无MC标记" }
                    return super.visitAnnotation(descriptor, visible)
                }
                override fun visitEnd() {
                    writer.visitAnnotation("Lme/eigenraven/lwjgl3ify/api/Lwjgl3Aware;", true).visitEnd()
                    super.visitEnd()
                }
            }, 0)
            val target = markedDirectory.get().file(bindingClass).asFile
            target.parentFile.mkdirs()
            target.writeBytes(writer.toByteArray())
        }
    }
}
listOf("jar", "shadowJar").forEach { name ->
    tasks.named<Jar>(name) {
        dependsOn(prepareMinecraftLwjgl3Binding)
        // Platform 各适配模块携带同一份第三方通知；合并后只保留 gl-renderer 的一份，避免重复条目。
        from(provider { zipTree(gl.singleFile) }) { exclude("META-INF/MANIFEST.MF", "LICENSE") }
        from(provider { zipTree(lwjgl2.singleFile) }) { exclude("META-INF/MANIFEST.MF", "LICENSE", "META-INF/THIRD-PARTY-NOTICES.md") }
        from(provider { zipTree(lwjgl3.singleFile) }) { exclude("META-INF/MANIFEST.MF", "LICENSE", bindingClass, "META-INF/THIRD-PARTY-NOTICES.md") }
        from(markedDirectory)
    }
}
// 资源管理器只看到外层Mod，不能依赖Core嵌套库恰巧可作为资源包。
val coreResourceArtifact = configurations.getByName("uilibCore8")
val fontResourceRoots = listOf("assets/qz_uilib/fonts/", "assets/qz_uilib/unicode/emoji17/")
tasks.named<ProcessResources>("processResources") {
    from(provider { zipTree(coreResourceArtifact.singleFile) }) {
        include(fontResourceRoots.map { it + "**" })
    }
}
val fontHostArchives = listOf("jar", "shadowJar", "reobfJar").map { tasks.named(it, AbstractArchiveTask::class.java).flatMap { task -> task.archiveFile } }
tasks.register("verifyHostFontResources") {
    group = "verification"; dependsOn("jar", "shadowJar", "reobfJar")
    inputs.files(coreResourceArtifact, fontHostArchives)
    doLast {
        ZipFile(coreResourceArtifact.singleFile).use { core ->
            val entries = core.entries().asSequence().filter { !it.isDirectory && fontResourceRoots.any { root -> it.name.startsWith(root) } }.toList()
            val required = listOf("emoji-data.txt", "emoji-sequences.txt", "emoji-zwj-sequences.txt", "emoji-variation-sequences.txt", "LICENSE.txt")
                    .map { "assets/qz_uilib/unicode/emoji17/$it" }
            required.forEach { check(core.getEntry(it) != null) { "Core缺少当代Unicode资源：$it" } }
            fontHostArchives.forEach { artifact ->
                ZipFile(artifact.get().asFile).use { host ->
                    entries.forEach { entry ->
                        check(host.getEntry(entry.name)?.size == entry.size) { "正式宿主未完整导入字体/Unicode资源：${entry.name}" }
                    }
                    logger.lifecycle("外层Mod字体、Unicode与许可证已验证: {} 条 {}", entries.size, host.name)
                }
            }
        }
    }
}
tasks.named("check") { dependsOn("verifyHostFontResources") }
