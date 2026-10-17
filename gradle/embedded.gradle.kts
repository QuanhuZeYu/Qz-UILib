import org.gradle.api.tasks.SourceSetContainer

val sourceSets = extensions.getByType<SourceSetContainer>()
val renderer = sourceSets.getByName("glRenderer")
val lwjgl2 = sourceSets.getByName("lwjgl2")
val lwjgl3 = sourceSets.getByName("lwjgl3")

// 独立宿主消费根构建输出；嵌入资源组合仍属于GL适配，不进入无平台核心。
val glRendererLegacyJar by tasks.registering(Jar::class) {
    archiveClassifier.set("gl-renderer-java8")
    from(renderer.output)
}
val glRendererJar by tasks.registering(Jar::class) {
    archiveClassifier.set("gl-renderer")
    from(tasks.named<JavaCompile>("compileGlRenderer21Java").flatMap { it.destinationDirectory })
}
val lwjgl2AdapterJar by tasks.registering(Jar::class) {
    archiveClassifier.set("lwjgl2-adapter")
    from(lwjgl2.output)
}
val lwjgl3AdapterLegacyJar by tasks.registering(Jar::class) {
    archiveClassifier.set("lwjgl3-adapter-java8")
    from(lwjgl3.output)
}
val lwjgl3AdapterJar by tasks.registering(Jar::class) {
    archiveClassifier.set("lwjgl3-adapter")
    from(tasks.named<JavaCompile>("compileLwjgl3Desktop21Java").flatMap { it.destinationDirectory })
}
// 只供显式平台构建，广场继续不进入正式assemble/publish。
val embeddedGalleryJar by tasks.registering(Jar::class) {
    archiveClassifier.set("gallery-test")
    from(sourceSets.getByName("gallery").output)
}
fun Sync.artifact(taskName: String, target: String) {
    val archive = tasks.named<Jar>(taskName)
    dependsOn(archive)
    from(archive.flatMap { it.archiveFile }) { rename { target } }
}
val prepareEmbeddedLegacy by tasks.registering(Sync::class) {
    group = "build"
    description = "装配Java8嵌入宿主的独立核心与适配输入"
    into(layout.buildDirectory.dir("embedded/java8"))
    artifact("coreLegacyJar", "core.jar")
    artifact("fontAdapterLegacyJar", "font-adapter.jar")
    artifact("mediaAdapterLegacyJar", "media-adapter.jar")
    artifact("glRendererLegacyJar", "gl-renderer.jar")
    artifact("lwjgl2AdapterJar", "lwjgl2.jar")
    artifact("lwjgl3AdapterLegacyJar", "lwjgl3.jar")
    artifact("embeddedGalleryJar", "gallery.jar")
}
val prepareEmbedded by tasks.registering(Sync::class) {
    group = "build"
    description = "装配Java21嵌入宿主的独立核心与适配输入"
    into(layout.buildDirectory.dir("embedded/java21"))
    artifact("coreJar", "core.jar")
    artifact("fontAdapterJar", "font-adapter.jar")
    artifact("mediaAdapterJar", "media-adapter.jar")
    artifact("glRendererJar", "gl-renderer.jar")
    artifact("lwjgl3AdapterJar", "lwjgl3.jar")
    artifact("embeddedGalleryJar", "gallery.jar")
}
