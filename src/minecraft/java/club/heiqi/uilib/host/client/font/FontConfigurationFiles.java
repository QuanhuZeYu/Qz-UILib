package club.heiqi.uilib.host.client.font;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import club.heiqi.uilib.ui.config.ConfigDocument;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSchema;
import club.heiqi.uilib.ui.scene.text.font.FontCandidateConfiguration;
import club.heiqi.uilib.ui.scene.text.font.FontSource;

/** MC 的文件选择与一次迁移；类型、持久化格式和权威值均由核心承担。
 * 按 YAML > Properties > Forge 选择首个现有来源，旧文件保持原样。 */
public final class FontConfigurationFiles {
    private FontConfigurationFiles() {}

    /** 从旧文件路径推导同目录的 YAML 路径。 */
    public static Path yamlPath(Path legacy) { return sibling(legacy, ".yaml"); }

    private static Path sibling(Path legacy, String suffix) {
        String name = legacy.getFileName().toString();
        int extension = name.lastIndexOf('.');
        String base = extension < 0 ? name : name.substring(0, extension);
        return legacy.resolveSibling(base + suffix);
    }

    /** 准备配置文档并完成迁移目标写入；调用方选择 I/O 线程并负责配置发布。 */
    public static ConfigDocument prepare(ConfigSchema schema, Path legacy,
            ConfigKey<Boolean> enabled, ConfigKey<List<String>> candidates,
            List<FontSource> builtins) throws IOException {
        Path yaml = yamlPath(legacy);
        if (Files.exists(yaml)) return ConfigDocument.read(schema, yaml);
        Path properties = sibling(legacy, ".properties");
        ConfigDocument document;
        if (Files.exists(properties)) {
            document = ConfigDocument.readProperties(schema, properties);
        } else if (Files.exists(legacy)) {
            LegacyFontConfiguration.Snapshot old = LegacyFontConfiguration.read(legacy);
            Map<ConfigKey<?>, Object> migrated = new LinkedHashMap<>();
            migrated.put(enabled, old.enabled);
            migrated.put(candidates, FontCandidateConfiguration.migrate(old.paths, builtins));
            document = ConfigDocument.defaults(schema).withAll(migrated);
        } else {
            document = ConfigDocument.defaults(schema);
        }
        Files.createDirectories(yaml.toAbsolutePath().getParent());
        document.write(yaml);
        return document;
    }
}
