import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Zip

// 正式发行装配：build/release 仅含本次版本的正式资产，Sync 删除旧版本残留。
// 安装包是自包含的宿主运行集合；源包与媒体/人工附件分别独立，禁止扁平汇总跨版本同名库。
val releaseVersion = version.toString()
val releaseHost = "1.7.10"
val releaseDir = layout.buildDirectory.dir("release")
val releaseInputDir = layout.buildDirectory.dir("release-input")
val installZipName = "qz-uilib-minecraft-$releaseVersion-$releaseHost.zip"
val assetsName = "qz-uilib-minecraft-$releaseVersion-$releaseHost-assets.txt"
val assetsManifestFile = layout.buildDirectory.file("release-input/$assetsName")

fun archiveProvider(name: String) =
    tasks.named(name, AbstractArchiveTask::class.java).flatMap { it.archiveFile }

val core8 = configurations.getByName("uilibCore8")
val font8 = configurations.getByName("uilibFont8")
val media8 = configurations.getByName("uilibMedia8")

val installAssets = listOf(
    Triple<Any, String, String>(archiveProvider("reobfJar"), "qz_uilib-$releaseVersion.jar", "Forge 1.7.10 正式宿主（GL 与 binding 已合并）"),
    Triple<Any, String, String>(core8, "qz_uilib-core-java8.jar", "Java8 正式核心库"),
    Triple<Any, String, String>(font8, "qz_uilib-font-adapter-java8.jar", "Java8 正式字体适配库")
)
val manualTestAsset = Triple<Any, String, String>(archiveProvider("manualTestJar"), "qz_uilib_manualtest-$releaseVersion.jar", "人工测试附加包（可选）")
val topLevelAssets = listOf(
    Triple<Any, String, String>(archiveProvider("reobfJar"), "qz_uilib-$releaseVersion.jar", "正式 Mod"),
    Triple<Any, String, String>(archiveProvider("sourcesJar"), "qz_uilib-$releaseVersion-sources.jar", "正式源码包"),
    manualTestAsset,
    Triple<Any, String, String>(media8, "qz_uilib-media-adapter-$releaseVersion-java8.jar", "媒体适配可选包（人工测试媒体页依赖）")
)
val requirements = listOf(
    "Minecraft 1.7.10 + Forge 10.13.4.1614",
    "需要 UniMixins：宿主经 MixinTweaker 接管原版字体与客户端生命周期",
    "正式包不携带 gallery/manual 测试代码；人工验收另附人工测试附加包与媒体适配包"
)

val assetsManifest = tasks.register("releaseAssetsManifest") {
    val output = assetsManifestFile
    inputs.property("uilibVersion", releaseVersion)
    inputs.property("requirements", requirements)
    inputs.property("installAssets", installAssets.map { listOf(it.second, it.third) })
    inputs.property("topLevelAssets", topLevelAssets.map { listOf(it.second, it.third) })
    outputs.file(output)
    doLast {
        val text = StringBuilder()
        text.append("Qz-UILib Minecraft $releaseHost 正式安装包 $releaseVersion\n")
        text.append("许可：自有代码 MIT；第三方组件保留各自许可。\n\n")
        text.append("运行要求：\n")
        requirements.forEach { text.append("- ").append(it).append('\n') }
        text.append("\n安装包内容：\n")
        installAssets.forEach { text.append("- ").append(it.second).append('：').append(it.third).append('\n') }
        text.append("\n本次顶层附件：\n")
        topLevelAssets.forEach { text.append("- ").append(it.second).append('：').append(it.third).append('\n') }
        val file = output.get().asFile
        file.parentFile.mkdirs()
        file.writeText(text.toString())
    }
}

val installZip = tasks.register<Zip>("releaseInstallZip") {
    dependsOn(assetsManifest, "reobfJar")
    archiveFileName.set(installZipName)
    destinationDirectory.set(releaseInputDir)
    installAssets.forEach { asset -> from(asset.first) { rename { asset.second } } }
    from(assetsManifest) { rename { "MANIFEST.txt" } }
}

// 实例安装复用上述正式与人工附件 provider；安装集合不从 release 目录枚举。
val runtimeInstallAssets = installAssets.map { mapOf("from" to it.first, "name" to it.second) } + listOf(
    mapOf("from" to manualTestAsset.first, "name" to manualTestAsset.second),
    mapOf("from" to media8, "name" to "qz_uilib-media-adapter-java8.jar")
)
tasks.register<Sync>("installBundle") {
    group = "distribution"
    dependsOn("build", "reobfJar", "manualTestJar")
    into(layout.buildDirectory.dir("install"))
    runtimeInstallAssets.forEach { asset -> from(asset.getValue("from")) { rename { asset.getValue("name").toString() } } }
}
extra["uilibInstallHost"] = "legacy"
extra["uilibInstallAssets"] = runtimeInstallAssets
extra["uilibInstallDependencies"] = listOf("uilibCore8", "uilibFont8", "uilibMedia8", "uilibGl8",
    "uilibLwjgl2", "uilibLwjgl3_8", "uilibGallery", "uilibCoreVerification").map { configurations.getByName(it) }
apply(from = "gradle/install-manifest.gradle")

tasks.register<Sync>("releaseAssets") {
    group = "distribution"
    description = "装配 $releaseHost 的 GitHub Release 资产：安装包、正式 Mod、源包与可选附件"
    dependsOn(installZip, "reobfJar", "sourcesJar", "manualTestJar")
    into(releaseDir)
    from(installZip)
    from(assetsManifest)
    topLevelAssets.forEach { asset -> from(asset.first) { rename { asset.second } } }
}
