package club.heiqi.uilib.host.desktop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSchema;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.text.font.FontCandidateConfiguration;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.settings.SettingsConfiguration;

/** 单窗口桌面应用的配置归属；窗口和页面借用同一会话，资源执行仍归窗口。 */
final class DesktopApplicationConfiguration implements AutoCloseable {
    final ReactiveDomain domain = ReactiveDomain.direct();
    final Path path;
    Owner owner;
    ConfigSession session;
    ConfigKey<List<String>> fontCandidates;
    ThemeConfiguration theme;
    SettingsConfiguration settings;
    Computed<String> title;

    DesktopApplicationConfiguration(Path path) {
        this.path = Objects.requireNonNull(path, "configPath").toAbsolutePath().normalize();
    }

    /** 字体和图像服务就绪后装配；调用者先登记close，初始化失败也能释放已取得的Owner。 */
    void initialize(List<FontSource> defaults, Consumer<List<String>> validate, Runnable resourcesReloaded) {
        fontCandidates = FontCandidateConfiguration.key(defaults);
        domain.execute(() -> {
            owner = domain.owner();
            session = new ConfigSession(domain, ConfigSchema.of(
                    DesktopWindowConfiguration.PROFILE, fontCandidates, ThemeConfiguration.KEY));
            try {
                if (Files.exists(path)) session.load(path);
                else {
                    Path parent = path.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    session.save(path);
                }
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
            // load后才创建应用投影与保存基线，页面关闭不结束这些对象。
            theme = new ThemeConfiguration(domain, session, owner);
            settings = new SettingsConfiguration(domain, session, fontCandidates, null, path,
                    FontCandidateConfiguration.defaults(defaults), validate, resourcesReloaded);
            title = domain.computed(owner, () -> session.get(DesktopWindowConfiguration.PROFILE).title);
        });
    }

    void reload() throws IOException {
        domain.checkWrite();
        settings.reload();
    }

    @Override public void close() {
        domain.execute(() -> { if (owner != null) owner.close(); });
    }
}
