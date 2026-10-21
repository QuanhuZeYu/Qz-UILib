import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Zip

// 输入仓与发布输出可独立指定；原有工作站 -PuilibRepository 调用保持可用。
val developmentRepository = uri(findProperty("uilibPublicationRepository")?.toString()
    ?: findProperty("uilibRepository")?.toString()
    ?: layout.buildDirectory.dir("maven").get().asFile.toURI().toString())

// 明确发布本仓接口编译产物；Compat通过Maven消费，不读取本仓源码或classes。
val developmentJar = tasks.register<Jar>("minecraftDevelopmentJar") {
    archiveClassifier.set("development-java8")
    from(project.extensions.getByType<SourceSetContainer>().getByName("main").output)
    from("LICENSE")
}
extensions.configure<org.gradle.api.publish.PublishingExtension> {
    publications.create<MavenPublication>("minecraftDevelopment") {
        artifactId = "minecraft-host"
        artifact(developmentJar)
    }
    repositories.maven {
        name = "CompatWorkspace"
        url = developmentRepository
    }
}

// 复用正式 publication 的 Maven 输出，仅打包本次坐标，不带入输入仓或旧版本。
tasks.register<Zip>("minecraftMavenZip") {
    group = "publishing"
    description = "生成独立 Compat 可消费的 Minecraft 开发接口 Maven ZIP"
    dependsOn("publishMinecraftDevelopmentPublicationToCompatWorkspaceRepository")
    archiveFileName.set("qz-uilib-minecraft-${project.version}-maven.zip")
    destinationDirectory.set(layout.buildDirectory.dir("release-maven"))
    val coordinatePath = "${project.group.toString().replace('.', '/')}/minecraft-host/${project.version}"
    val artifactNames = listOf("minecraft-host-${project.version}.pom",
        "minecraft-host-${project.version}-development-java8.jar")
    val publicationDirectory = provider {
        require(developmentRepository.scheme == "file") { "Maven ZIP 需要显式本地发布输出仓" }
        file(developmentRepository).resolve(coordinatePath)
    }
    from(publicationDirectory) {
        into(coordinatePath)
        artifactNames.forEach { include(it, "$it.*") }
    }
    doFirst {
        artifactNames.forEach { name ->
            check(publicationDirectory.get().resolve(name).isFile) { "正式 publication 缺少 $name" }
        }
    }
}
