import org.gradle.api.tasks.SourceSetContainer
val sourceSets = extensions.getByType<SourceSetContainer>()
val coreSources = sourceSets.getByName("core")
// 测试页面只看无平台core；缺失宿主能力在入口显式供给，不能通过MC开发类路径补齐。
val gallerySources = sourceSets.create("gallery") {
    java.setSrcDirs(listOf("src/gallery/java"))
    resources.setSrcDirs(listOf("src/gallery/resources"))
    compileClasspath = coreSources.output
    runtimeClasspath = output + compileClasspath
}
tasks.named<JavaCompile>(gallerySources.compileJavaTaskName) {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.release.set(8)
    options.compilerArgs.add("-proc:none")
    classpath = gallerySources.compileClasspath
}
