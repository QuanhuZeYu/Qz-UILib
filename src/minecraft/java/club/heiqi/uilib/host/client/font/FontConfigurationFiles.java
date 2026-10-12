package club.heiqi.uilib.host.client.font;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.scene.text.font.FontCandidateConfiguration;
import club.heiqi.uilib.ui.scene.text.font.FontSource;

/** MC 的文件选择与一次迁移；类型、持久化格式和权威值均由核心承担。
 * YAML > Properties > Forge，仅选择一个来源，不合并、不改写旧文件。 */
public final class FontConfigurationFiles {
    private FontConfigurationFiles() {}

    public static Path initialize(ConfigSession session, Path legacy,
            ConfigKey<Boolean> enabled, ConfigKey<List<String>> candidates,
            List<FontSource> builtins) throws IOException {
        String name = legacy.getFileName().toString();
        int extension = name.lastIndexOf('.');
        String base = extension < 0 ? name : name.substring(0, extension);
        Path yaml = legacy.resolveSibling(base + ".yaml");
        if (Files.exists(yaml)) {
            session.load(yaml);
            return yaml;
        }
        Path properties = legacy.resolveSibling(base + ".properties");
        if (Files.exists(properties)) {
            session.loadProperties(properties);
        } else if (Files.exists(legacy)) {
            LegacyFontConfiguration.Snapshot old = LegacyFontConfiguration.read(legacy);
            List<String> migrated = FontCandidateConfiguration.migrate(old.paths, builtins);
            session.set(enabled, old.enabled);
            session.set(candidates, migrated);
        }
        Files.createDirectories(yaml.toAbsolutePath().getParent());
        session.save(yaml);
        return yaml;
    }
}
