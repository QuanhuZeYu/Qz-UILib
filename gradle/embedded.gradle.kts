// 从已解析 Maven 产物同步显式安装目录，各 loader 工程分别解析自己的 Maven 依赖。
fun Sync.artifact(configuration: String, target: String) {
    from(configurations.getByName(configuration)) { rename { target } }
}
tasks.register<Sync>("prepareEmbeddedLegacy") {
    group = "build"
    description = "装配 Maven Java8 核心与适配输入"
    into(layout.buildDirectory.dir("embedded/java8"))
    artifact("uilibCore8", "core.jar")
    artifact("uilibFont8", "font-adapter.jar")
    artifact("uilibMedia8", "media-adapter.jar")
    artifact("uilibGl8", "gl-renderer.jar")
    artifact("uilibLwjgl2", "lwjgl2.jar")
    artifact("uilibLwjgl3_8", "lwjgl3.jar")
    artifact("uilibGallery", "gallery.jar")
}
tasks.register<Sync>("prepareEmbedded") {
    group = "build"
    description = "装配 Maven Java21 核心与适配输入"
    into(layout.buildDirectory.dir("embedded/java21"))
    artifact("uilibCore21", "core.jar")
    artifact("uilibFont21", "font-adapter.jar")
    artifact("uilibMedia21", "media-adapter.jar")
    artifact("uilibGl21", "gl-renderer.jar")
    artifact("uilibLwjgl3_21", "lwjgl3.jar")
    artifact("uilibGallery", "gallery.jar")
}
// 从正式 Maven Jar 提取验证资源至本仓 build，按场景所需目录布局传入。
val core = configurations.getByName("uilibCore8")
val verification = configurations.getByName("uilibCoreVerification")
tasks.register<Sync>("extractVerificationResources") {
    into(layout.buildDirectory.dir("verification-inputs"))
    from(provider { zipTree(core.singleFile) }) { include("assets/**"); into("src/core/resources") }
    from(provider { zipTree(verification.singleFile) }) {
        exclude("**/*.class", "META-INF/**")
        into("src/test/resources")
    }
}
