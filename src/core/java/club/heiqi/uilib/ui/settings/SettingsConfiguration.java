package club.heiqi.uilib.ui.settings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;

/** 设置工作区借用正式配置；保存基线归宿主持有的对象，不随页面释放。
 * 字体来源读取由宿主复用既有解析器，配置文档与其他模块字段仍由 session 保存。 */
public final class SettingsConfiguration {
    public static final class Snapshot {
        private final List<String> candidates;
        private final boolean replace;

        public Snapshot(List<String> candidates, boolean replace) {
            this.candidates = freeze(candidates);
            this.replace = replace;
        }
        public List<String> candidates() { return candidates; }
        public boolean replace() { return replace; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Snapshot)) return false;
            Snapshot next = (Snapshot) other;
            return replace == next.replace && candidates.equals(next.candidates);
        }
        @Override public int hashCode() { return Objects.hash(candidates, replace); }
    }

    private final ReactiveDomain domain;
    private final ConfigSession session;
    private final ConfigKey<List<String>> fontKey;
    private final ConfigKey<Boolean> mcKey;
    private final Path path;
    private final List<String> builtins;
    private final Consumer<List<String>> validateCandidates;
    private final Signal<Snapshot> saved;
    private final Signal<String> resourceError;
    private final Runnable reloadResources;

    public SettingsConfiguration(ReactiveDomain domain, ConfigSession session,
            ConfigKey<List<String>> fontKey, ConfigKey<Boolean> mcKey, Path path,
            List<String> builtinCandidates, Consumer<List<String>> validateCandidates) {
        this(domain, session, fontKey, mcKey, path, builtinCandidates, validateCandidates, () -> {});
    }

    /** 显式文件加载完成后让宿主更新所属资源；同值加载仍刷新原地替换的字体文件。
     * 回调失败保留已经读取并发布的配置事实，不增加监视或恢复策略。 */
    public SettingsConfiguration(ReactiveDomain domain, ConfigSession session,
            ConfigKey<List<String>> fontKey, ConfigKey<Boolean> mcKey, Path path,
            List<String> builtinCandidates, Consumer<List<String>> validateCandidates, Runnable reloadResources) {
        this.domain = Objects.requireNonNull(domain, "domain");
        domain.checkAccess();
        this.session = Objects.requireNonNull(session, "session");
        this.fontKey = Objects.requireNonNull(fontKey, "fontKey");
        this.mcKey = mcKey;
        this.path = Objects.requireNonNull(path, "path");
        this.builtins = freeze(builtinCandidates);
        this.validateCandidates = Objects.requireNonNull(validateCandidates, "validateCandidates");
        // 宿主先完成实际 load/初始化；构造时的真实值即最近成功读取值。
        saved = domain.signal(current());
        resourceError = domain.signal("");
        this.reloadResources = Objects.requireNonNull(reloadResources, "reloadResources");
    }

    private static List<String> freeze(List<String> values) {
        List<String> copy = new ArrayList<>(Objects.requireNonNull(values, "candidates"));
        for (String value : copy) Objects.requireNonNull(value, "candidate");
        return Collections.unmodifiableList(copy);
    }

    public ReactiveDomain domain() { return domain; }
    public Path path() { return path; }
    public List<String> builtinCandidates() { return builtins; }
    public boolean hasMinecraft() { return mcKey != null; }
    public List<String> defaultCandidates() { return fontKey.defaultValue(); }
    public boolean defaultReplace() { return mcKey != null && mcKey.defaultValue(); }
    public Snapshot current() { return new Snapshot(session.get(fontKey), mcKey != null && session.get(mcKey)); }
    public ReadableSignal<Snapshot> saved() { return saved; }
    public ReadableSignal<String> resourceError() { return resourceError; }

    /** 仅为宿主字体资源更新提供已知失败边界；不捕获域、布局或绘制的一般异常。
     * 配置已经发布后资源失败仍保留当前值，错误交给共享工作区显示；无回滚或自动重试。 */
    public void updateResources(Runnable update) {
        domain.checkWrite();
        Objects.requireNonNull(update, "update");
        try {
            update.run();
            resourceError.set("");
        } catch (RuntimeException failure) {
            String reason = failure.getMessage();
            if (reason == null || reason.isEmpty()) reason = failure.getClass().getSimpleName();
            resourceError.set("字体资源更新失败：" + path + " — " + reason);
        }
    }

    void validateCandidates(List<String> candidates) {
        List<String> frozen = freeze(candidates);
        domain.readOnly(() -> { validateCandidates.accept(frozen); return null; });
    }

    public void apply(List<String> candidates, boolean replace) {
        domain.checkWrite();
        List<String> frozen = freeze(candidates);
        validateCandidates(frozen);
        Map<ConfigKey<?>, Object> updates = new LinkedHashMap<>();
        updates.put(fontKey, frozen);
        if (mcKey != null) updates.put(mcKey, replace);
        session.setAll(updates);
    }

    public void save() throws IOException {
        domain.checkWrite();
        session.save(path);
        saved.set(current());
    }

    public void reload() throws IOException {
        domain.checkWrite();
        session.load(path);
        saved.set(current());
        reloadResources.run();
    }
}
