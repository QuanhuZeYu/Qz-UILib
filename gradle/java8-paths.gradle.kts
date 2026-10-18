// Java8 Windows launcher在中文工作区不能解析绝对classpath；生产/验证仍使用原Java目标。
// 仅在进程启动边界换为相对路径，保留任务声明的classpath用于依赖与运行隔离检查。
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
