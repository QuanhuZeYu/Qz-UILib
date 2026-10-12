package club.heiqi.uilib.ui.scene.control;

import java.util.Objects;
import club.heiqi.uilib.ui.config.ConfigCodec;
import club.heiqi.uilib.ui.config.ConfigCodecs;
import club.heiqi.uilib.ui.config.ConfigException;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;

/** 借用应用唯一配置；跨页面/状态域投影通过既有Effect递交，不轮询文件。
 * 共享配置归宿主，页面只拥有自己的订阅。持久化沿ConfigSession显式保存。 */
public final class ThemeConfiguration {
    public static final ConfigKey<ThemeMode> KEY = new ConfigKey<>("ui.theme", new ConfigCodec<ThemeMode>() {
        public ThemeMode read(Object value, String path) {
            String text = ConfigCodecs.STRING.read(value, path);
            if ("light".equals(text)) return ThemeMode.LIGHT;
            if ("dark".equals(text)) return ThemeMode.DARK;
            throw new ConfigException(path, "主题须为 light 或 dark");
        }
        public Object write(ThemeMode value) { return snapshot(value).isDark() ? "dark" : "light"; }
        public ThemeMode snapshot(ThemeMode value) { return Objects.requireNonNull(value, "theme"); }
    }, ThemeMode.LIGHT).withComment("UILib全局主题：light（浅色）或dark（深色）；应用后生效，保存后重启保留。");

    private final ReactiveDomain domain;
    private final ConfigSession session;
    private final Owner scope;
    private volatile ThemeMode published;

    public ThemeConfiguration(ReactiveDomain domain, ConfigSession session, Owner owner) {
        this.domain = Objects.requireNonNull(domain, "domain");
        domain.checkWrite();
        this.session = Objects.requireNonNull(session, "session");
        published = session.get(KEY);
        scope = Objects.requireNonNull(owner, "owner").child();
        domain.effect(scope, () -> published = session.get(KEY));
    }

    /** 返回调用页面域内可追踪的选择；异步通知关闭后无效，迟到旧批次不能覆盖新值。 */
    public ReadableSignal<ThemeMode> bind(ReactiveDomain target, Owner owner) {
        Objects.requireNonNull(target, "target").checkWrite();
        Objects.requireNonNull(owner, "owner");
        if (target == domain) return () -> session.get(KEY);
        Signal<ThemeMode> mode = target.signal(published);
        Delivery delivery = new Delivery();
        owner.own(() -> {
            delivery.closed = true;
            domain.execute(() -> { if (delivery.subscription != null) delivery.subscription.close(); });
        });
        domain.execute(() -> {
            if (delivery.closed || scope.isDisposed()) return;
            delivery.subscription = scope.child();
            domain.effect(delivery.subscription, () -> {
                ThemeMode next = session.get(KEY);
                Object token = new Object();
                delivery.latest = token;
                if (!delivery.closed) target.execute(() -> {
                    if (!delivery.closed && delivery.latest == token) mode.set(next);
                });
            });
        });
        return mode;
    }

    private static final class Delivery {
        volatile boolean closed;
        volatile Object latest;
        Owner subscription;
    }
}
