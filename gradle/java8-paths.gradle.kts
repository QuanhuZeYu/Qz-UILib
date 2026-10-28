// Java8 Windows launcher 使用相对路径绕过中文绝对 classpath 的解码问题。
// 启动前保留声明的 classpath 供依赖与隔离检查，Java 目标沿任务配置。
val windows = System.getProperty("os.name").lowercase().contains("win")
tasks.withType<JavaExec>().configureEach {
    doFirst {
        if (windows && (javaLauncher.orNull?.metadata?.languageVersion?.asInt() == 8 || (name == "lwjgl2HostScenario" && providers.gradleProperty("fontLegacyJava").isPresent))) {
            val location = workingDir.toPath()
            val relative = classpath.files.joinToString(java.io.File.pathSeparator) { artifact ->
                val absolute = artifact.toPath().toAbsolutePath()
                if (location.root == absolute.root) location.relativize(absolute).toString() else absolute.toString()
            }
            setArgs(args.map { argument ->
                val value = argument.toString()
                val file = java.io.File(value)
                if (file.isAbsolute) {
                    val path = file.toPath()
                    if (location.root == path.root) location.relativize(path).toString() else value
                } else value
            })
            classpath = objects.fileCollection()
            jvmArgs("-cp", relative)
        }
    }
}
