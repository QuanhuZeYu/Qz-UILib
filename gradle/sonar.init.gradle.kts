import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.sonarqube.gradle.SonarExtension
import org.sonarqube.gradle.SonarQubePlugin
import org.sonarqube.gradle.SonarTask

/**
 * 可选人工分析入口，不由项目构建或CI自动应用；Sonar结果只供审阅，不作为交付指标。
 *
 * 先查看本地输入（不连接Sonar服务器）：
 *   ./gradlew -I gradle/sonar.init.gradle.kts sonarInputs --no-configuration-cache
 * 手动提交分析（Windows将./gradlew换成gradlew.bat）：
 *   ./gradlew -I gradle/sonar.init.gradle.kts sonar --no-configuration-cache
 *     -Dsonar.host.url=https://your-sonar.example -Dsonar.projectKey=your-project
 * 上述参数写在同一条命令；SonarQube Cloud另传-Dsonar.organization=你的组织。
 * 令牌通过SONAR_TOKEN环境变量注入，勿写入仓库或命令行。
 * 地址/项目也可用SONAR_HOST_URL、SONAR_PROJECT_KEY；本入口不预设服务器，不创建远程项目。
 *
 * 分析入口只依赖编译，不运行测试。扫描失败如实暴露，但普通build/check及CI不依赖此任务。
 * 默认不等待Quality Gate，不新增复杂度/覆盖率阈值；分析结果结合代码职责与真实行为判断。
 * 本入口分析根工程编译图；platforms中的独立宿主使用各自构建类路径，不能合并不同MC映射库。
 * 固定官方版本：https://github.com/SonarSource/sonar-scanner-gradle/releases/tag/7.5.0.8588
 */
initscript {
    repositories { gradlePluginPortal() }
    dependencies { classpath("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:7.5.0.8588") }
}

gradle.rootProject {
    apply<SonarQubePlugin>()
    val target = this

    // 等项目声明完成，使用真实sourceSet和JavaCompile类路径，避免复制宿主依赖表。
    gradle.projectsEvaluated {
        val sets = target.extensions.getByType<SourceSetContainer>()
        val production = listOf("core", "fontAdapter", "mediaAdapter", "glRenderer", "lwjgl2", "lwjgl3", "desktop", "main")
            .map(sets::getByName)
        val tests = listOf("gallery", "testSupport", "test", "mediaTest", "desktopTest", "manualTest", "lwjgl2Test")
            .map(sets::getByName)
        val all = production + tests
        // SDL专项是独立JavaCompile，不属于sourceSet；来源、输出和依赖沿该任务读取。
        val sdl = target.tasks.named<JavaCompile>("compileSdlTextInputScenario")
        val buildRoot = target.layout.buildDirectory.get().asFile.canonicalFile.toPath()
        val compilations = all.map { target.tasks.named<JavaCompile>(it.compileJavaTaskName).get() } + sdl.get()
        // 同一分析模块使用所包含源码的最高语法级别；部署字节码仍由各编译任务自己的target/release决定。
        val languageLevel = compilations.maxOf { JavaVersion.toVersion(it.sourceCompatibility).majorVersion.toInt() }
        val sourceLevels = compilations.map {
            it.name + ": source=" + it.sourceCompatibility + ", target=" + it.targetCompatibility +
                    ", release=" + (it.options.release.orNull?.toString() ?: "未指定")
        }

        fun sourceRoots(names: List<String>) = names.flatMap { sets.getByName(it).java.srcDirs }
            .filter { it.isDirectory && !it.canonicalFile.toPath().startsWith(buildRoot) }
            .map { it.canonicalFile }.distinct()
        fun binaries(names: List<String>) = names.flatMap { sets.getByName(it).output.classesDirs.files }
            .filter { it.isDirectory }.map { it.canonicalFile }.distinct()
        fun libraries(names: List<String>) = names.flatMap {
            target.tasks.named<JavaCompile>(sets.getByName(it).compileJavaTaskName).get().classpath.files
        }.filter { it.exists() }.map { it.canonicalFile }.distinct()

        target.extensions.configure<SonarExtension> {
            properties {
                property("sonar.sourceEncoding", "UTF-8")
                property("sonar.java.source", languageLevel.toString())
                // 沿真实源码根索引，desktop的去标记lwjgl3生成副本不重复进入分析。
                property("sonar.sources", sourceRoots(production.map { it.name }))
                property("sonar.tests", sourceRoots(tests.map { it.name }) + sdl.get().source.files)
                property("sonar.java.binaries", binaries(production.map { it.name }))
                property("sonar.java.test.binaries", (binaries(tests.map { it.name })
                    + listOf(sdl.get().destinationDirectory.get().asFile).filter { it.isDirectory }).distinct())
                property("sonar.java.libraries", libraries(production.map { it.name }))
                property("sonar.java.test.libraries", (libraries(tests.map { it.name })
                    + sdl.get().classpath.files.filter { it.exists() }).distinct())
                property("sonar.qualitygate.wait", false)
                property("sonar.working.directory", target.layout.buildDirectory.dir("sonar").get().asFile)
                target.providers.environmentVariable("SONAR_PROJECT_KEY").orNull
                    ?.takeIf { it.isNotBlank() }?.let { property("sonar.projectKey", it) }
                target.providers.environmentVariable("SONAR_HOST_URL").orNull
                    ?.takeIf { it.isNotBlank() }?.let { property("sonar.host.url", it) }
            }
        }

        val sonarTask = target.tasks.named<SonarTask>("sonar")
        // 官方还注册了独立的旧sonarqube任务，所有扫描入口共用同一边界。
        target.tasks.withType<SonarTask>().configureEach {
            // 字节码与各宿主实际编译类路径必须齐全；不连到check/build，也不执行测试应用。
            dependsOn(all.map { target.tasks.named(it.compileJavaTaskName) }, sdl)
            doFirst {
                // 插件会推导默认projectKey；目标必须来自调用方，不能把推导值当显式配置。
                val host = target.providers.systemProperty("sonar.host.url")
                    .orElse(target.providers.environmentVariable("SONAR_HOST_URL")).orNull
                val projectKey = target.providers.systemProperty("sonar.projectKey")
                    .orElse(target.providers.environmentVariable("SONAR_PROJECT_KEY")).orNull
                require(!host.isNullOrBlank()) {
                    "可选Sonar分析需显式指定服务：-Dsonar.host.url或SONAR_HOST_URL；本地查看输入请用sonarInputs。"
                }
                require(!projectKey.isNullOrBlank()) {
                    "可选Sonar分析需指定项目：-Dsonar.projectKey或SONAR_PROJECT_KEY。"
                }
            }
        }

        target.tasks.register("sonarInputs") {
            group = "help"
            description = "只查看可选Sonar的本地分析输入，不连接服务器、不读取质量门槛"
            dependsOn(all.map { target.tasks.named(it.compileJavaTaskName) }, sdl)
            val report = target.layout.buildDirectory.file("reports/sonar/inputs.txt")
            outputs.file(report)
            outputs.upToDateWhen { false }
            doLast {
                val effective = sonarTask.get().properties.get()
                // 只输出明确的非秘密输入，不序列化插件全量属性（其中可能含认证信息）。
                val names = listOf("sonar.sources", "sonar.tests", "sonar.java.binaries",
                    "sonar.java.test.binaries", "sonar.java.libraries", "sonar.java.test.libraries",
                    "sonar.java.source", "sonar.qualitygate.wait")
                val file = report.get().asFile
                file.parentFile.mkdirs()
                file.writeText(names.joinToString("\n", postfix = "\n") { it + "=" + effective[it].orEmpty() } +
                        "\n编译任务的语法与字节码级别：\n" + sourceLevels.joinToString("\n", postfix = "\n"))
                logger.lifecycle("Sonar本地输入清单：" + file.absolutePath + "；未提交远程分析，未等待Quality Gate。")
            }
        }
    }
}
