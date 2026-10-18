// 跨仓只解析正式 Maven 产物；开发包提供原包名，发行仍使用私有重定位核心。
val uilibVersion = providers.gradleProperty("uilibVersion").getOrElse("4.11.1")
repositories.maven {
    name = "MojangICU"
    url = uri("https://libraries.minecraft.net")
    content { includeModule("com.ibm.icu", "icu4j") }
}
providers.gradleProperty("uilibRepository").orNull?.let { repository ->
    val uilibMaven = repositories.maven { name = "UILib"; url = uri(repository) }
    repositories.exclusiveContent {
        forRepositories(uilibMaven)
        filter { includeGroup("club.heiqi.uilib") }
    }
}
fun artifact(name: String, module: String, classifier: String = "") = configurations.create(name) {
    isCanBeConsumed = false
    isTransitive = false
    dependencies.add(project.dependencies.create("club.heiqi.uilib:$module:$uilibVersion" + if (classifier.isEmpty()) "" else ":$classifier"))
}
artifact("uilibCore8", "core", "java8")
artifact("uilibCore21", "core")
artifact("uilibCoreDevelopment", "core", "development-java8")
artifact("uilibFont8", "font-adapter", "java8")
artifact("uilibFont21", "font-adapter")
artifact("uilibMedia8", "media-adapter", "java8")
artifact("uilibMedia21", "media-adapter")
artifact("uilibGl8", "gl-renderer", "java8")
artifact("uilibGl21", "gl-renderer")
artifact("uilibLwjgl2", "lwjgl2-adapter", "java8")
artifact("uilibLwjgl3_8", "lwjgl3-adapter", "java8")
artifact("uilibLwjgl3_21", "lwjgl3-adapter")
artifact("uilibGallery", "gallery")
artifact("uilibCoreVerification", "core-verification")
artifact("uilibPlatformVerification", "platform-verification")
val coreContentRuntime = configurations.create("coreContentRuntime") { isCanBeConsumed = false }
val fontBindings = configurations.create("fontBindings") { isCanBeConsumed = false; isTransitive = false }
val fontNativeRuntime = configurations.create("fontNativeRuntime") { isCanBeConsumed = false; extendsFrom(fontBindings) }
val lwjgl2Bindings = configurations.create("lwjgl2Bindings") { isCanBeConsumed = false; isTransitive = false }
dependencies {
    add(coreContentRuntime.name, "com.ibm.icu:icu4j:76.1")
    add(coreContentRuntime.name, "org.yaml:snakeyaml:2.4")
    listOf("commonmark", "commonmark-ext-gfm-strikethrough", "commonmark-ext-gfm-tables").forEach { add(coreContentRuntime.name, "org.commonmark:$it:0.21.0") }
    add(coreContentRuntime.name, "org.jsoup:jsoup:1.19.1")
    add(coreContentRuntime.name, "org.nibor.autolink:autolink:0.10.0")
    add(lwjgl2Bindings.name, "org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209")
    listOf("lwjgl", "lwjgl-freetype", "lwjgl-harfbuzz", "lwjgl-opengl", "lwjgl-glfw").forEach { add(fontBindings.name, "org.lwjgl:$it:3.4.2") }
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val classifier = when {
        os.contains("win") -> if (arch == "aarch64") "natives-windows-arm64" else if (arch == "x86") "natives-windows-x86" else "natives-windows"
        os.contains("mac") -> if (arch == "aarch64") "natives-macos-arm64" else "natives-macos"
        else -> if (arch == "aarch64") "natives-linux-arm64" else "natives-linux"
    }
    listOf("lwjgl", "lwjgl-freetype", "lwjgl-opengl", "lwjgl-glfw").forEach { add(fontNativeRuntime.name, "org.lwjgl:$it:3.4.2:$classifier") }
}
