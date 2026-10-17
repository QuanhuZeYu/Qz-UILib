import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

// 核心嵌入与宿主消费者必须重写到同一私有身份；这里只维护共同内容依赖的映射。
val contentPackages = listOf("com.ibm.icu", "org.yaml.snakeyaml", "org.commonmark", "org.jsoup", "org.nibor.autolink")
val contentConsumers = setOf("coreJar", "coreLegacyJar", "shadowJar", "desktopJar", "desktopTestJar")
tasks.withType<ShadowJar>().configureEach {
    if (name in contentConsumers) contentPackages.forEach { source ->
        relocate(source, "club.heiqi.uilib.shadow.$source")
    }
}
