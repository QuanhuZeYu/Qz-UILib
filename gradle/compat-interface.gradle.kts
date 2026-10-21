import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer

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
        url = uri(findProperty("uilibRepository")?.toString() ?: layout.buildDirectory.dir("maven").get().asFile.toURI().toString())
    }
}
