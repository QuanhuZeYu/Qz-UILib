package club.heiqi.uilib.ui.settings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.settings.SettingsConfiguration.Snapshot;

/** 一份工作区草稿，分类导航共享同一实例；页面释放仅结束本页订阅。
 * 值语义区分草稿、live 与 saved，不把字体路径当作行身份。 */
public final class SettingsModel {
    public static final class Source {
        private final Object identity = new Object();
        private final String declaration;
        private Source(String declaration) { this.declaration = declaration; }
        public Object identity() { return identity; }
        public String declaration() { return declaration; }
    }

    /** 同文案的两次成功仍是两个操作；呈现期限不改变业务结果。 */
    static final class Feedback {
        final String text;
        final boolean success;
        Feedback(String text, boolean success) { this.text = text; this.success = success; }
    }

    private final SettingsConfiguration configuration;
    private final ReactiveDomain domain;
    private final Owner scope;
    private final Signal<List<Source>> sources;
    private final Signal<Boolean> replace;
    private final Signal<ThemeMode> theme;
    private final Signal<Boolean> externalChanged;
    private final Signal<String> fontError;
    private final Signal<String> fileError;
    private final Signal<Feedback> message;
    private final ReadableSignal<Boolean> dirty;
    private final ReadableSignal<Boolean> unsaved;
    private final ReadableSignal<String> error;
    private final ReadableSignal<String> saveDisabledReason;
    // 仅为观察 live 变化前的语义基线；权威值始终读取 configuration/session。
    private Snapshot observed;

    public SettingsModel(SettingsConfiguration configuration, Owner pageScope) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        domain = configuration.domain();
        domain.checkWrite();
        scope = Objects.requireNonNull(pageScope, "pageScope").child();
        observed = configuration.current();
        sources = domain.signal(rows(observed.candidates(), Collections.emptyList()));
        replace = domain.signal(observed.replace());
        theme = domain.signal(observed.theme());
        externalChanged = domain.signal(false);
        fontError = domain.signal("");
        fileError = domain.signal("");
        message = domain.signal(null);
        dirty = domain.computed(scope, () -> !draft().equals(configuration.current()));
        unsaved = domain.computed(scope, () -> !configuration.current().equals(configuration.saved().get()));
        error = domain.computed(scope, () -> !fontError.get().isEmpty() ? fontError.get()
                : !fileError.get().isEmpty() ? fileError.get() : configuration.resourceError().get());
        saveDisabledReason = domain.computed(scope, () -> dirty.get() ? "先应用更改" : unsaved.get() ? "" : "没有需要保存的更改");
        domain.effect(scope, this::synchronize);
    }

    public SettingsConfiguration configuration() { return configuration; }
    public ReadableSignal<List<Source>> sources() { return sources; }
    public ReadableSignal<Boolean> replace() { return replace; }
    public ReadableSignal<ThemeMode> theme() { return theme; }
    public ReadableSignal<Boolean> dirty() { return dirty; }
    public ReadableSignal<Boolean> unsaved() { return unsaved; }
    public ReadableSignal<Boolean> externalChanged() { return externalChanged; }
    public ReadableSignal<String> error() { return error; }
    public ReadableSignal<String> fontError() { return fontError; }
    public ReadableSignal<String> fileError() { return fileError; }
    public ReadableSignal<String> message() {
        return () -> { Feedback value = feedback().get(); return value == null ? "" : value.text; };
    }
    ReadableSignal<Feedback> feedback() {
        return () -> configuration.resourceError().get().isEmpty() ? message.get() : null;
    }
    public ReadableSignal<String> saveDisabledReason() { return saveDisabledReason; }

    private Snapshot draft() {
        List<String> values = new ArrayList<>();
        for (Source source : sources.get()) values.add(source.declaration());
        return new Snapshot(values, replace.get(), theme.get());
    }

    private static List<Source> rows(List<String> values, List<Source> previous) {
        List<Source> available = new ArrayList<>(previous);
        List<Source> next = new ArrayList<>();
        for (String value : values) {
            Source match = null;
            for (Source source : available) {
                if (source.declaration().equals(value)) { match = source; break; }
            }
            if (match == null) match = new Source(value);
            else available.remove(match);
            next.add(match);
        }
        return Collections.unmodifiableList(next);
    }

    private void write() {
        domain.checkWrite();
        if (scope.isDisposed()) throw new IllegalStateException("配置页面已释放");
        synchronize();
    }

    private void synchronize() {
        if (!configuration.resourceError().get().isEmpty()) message.set(null);
        Snapshot current = configuration.current();
        configuration.saved().get();
        if (!current.equals(observed)) {
            boolean pristine = draft().equals(observed);
            observed = current;
            if (pristine) restore(current);
            else externalChanged.set(!draft().equals(current));
        }
        if (draft().equals(current)) externalChanged.set(false);
    }

    private void restore(Snapshot snapshot) {
        domain.batch(() -> {
            sources.set(rows(snapshot.candidates(), sources.get()));
            replace.set(snapshot.replace());
            theme.set(snapshot.theme());
            externalChanged.set(false);
        });
    }

    private void clearFeedback() {
        fontError.set("");
        fileError.set("");
        message.set(null);
    }

    public void add(String declaration) {
        write();
        clearFeedback();
        try {
            List<String> values = new ArrayList<>(draft().candidates());
            values.add(Objects.requireNonNull(declaration, "字体来源不能为空"));
            configuration.validateCandidates(values);
            List<Source> next = new ArrayList<>(sources.get());
            next.add(new Source(declaration));
            sources.set(Collections.unmodifiableList(next));
        } catch (RuntimeException failure) { fontError.set("添加来源失败：" + reason(failure)); }
    }

    public void remove(Source source) {
        write();
        List<Source> next = new ArrayList<>(sources.get());
        if (!next.remove(source)) return;
        clearFeedback();
        sources.set(Collections.unmodifiableList(next));
    }

    public void move(Source source, int direction) {
        write();
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("移动方向须为 -1 或 1");
        List<Source> next = new ArrayList<>(sources.get());
        int index = next.indexOf(source);
        int target = index + direction;
        if (index < 0 || target < 0 || target >= next.size()) return;
        clearFeedback();
        Collections.swap(next, index, target);
        sources.set(Collections.unmodifiableList(next));
    }

    /** 只在排序正常结束时变更草稿；来源身份独立于声明值，重复字体也不会串行。 */
    public void moveBefore(Object sourceKey, Object beforeKey) {
        write();
        List<Source> current=sources.get();
        Source source=null,before=null;
        for(Source item:current){if(item.identity()==sourceKey)source=item;if(item.identity()==beforeKey)before=item;}
        if(source==null||source==before||beforeKey!=null&&before==null)return;
        List<Source> next=new ArrayList<>(current);next.remove(source);
        next.add(before==null?next.size():next.indexOf(before),source);
        if(next.equals(current))return;
        clearFeedback();sources.set(Collections.unmodifiableList(next));
    }

    public void setReplace(boolean value) {
        write();
        if (!configuration.hasMinecraft() || replace.get() == value) return;
        clearFeedback();
        replace.set(value);
    }

    /** 主题与其他字段共享草稿；预览不发布全局值，也不保存文件。 */
    public void setTheme(ThemeMode value) {
        write();
        Objects.requireNonNull(value, "theme");
        if (theme.get() == value) return;
        clearFeedback();
        theme.set(value);
    }

    public void resetFonts() {
        write();
        clearFeedback();
        sources.set(rows(configuration.defaultCandidates(), sources.get()));
    }

    public void resetMinecraft() {
        setReplace(configuration.defaultReplace());
    }

    public void discard() {
        write();
        clearFeedback();
        observed = configuration.current();
        restore(observed);
    }

    /** 独立域操作让资源 Effect 的延后异常进入同一个正式失败边界；不创建线程。
     * 校验/I/O 拒绝留在对应表单，已发布后的异常按 live 事实反馈且不回滚。 */
    public void apply() {
        write();
        domain.enqueue(() -> {
            if (scope.isDisposed()) return;
            synchronize();
            if (!dirty.get()) return;
            clearFeedback();
            Snapshot submitted = draft();
            try {
                configuration.apply(submitted.candidates(), submitted.replace(), submitted.theme());
                observed = configuration.current();
                externalChanged.set(false);
                message.set(new Feedback("已应用全部更改", true));
            } catch (RuntimeException failure) { applicationFailure(failure); }
        }, failure -> { if (!scope.isDisposed()) applicationFailure(failure); });
    }

    private void applicationFailure(Throwable failure) {
        observed = configuration.current();
        boolean published = draft().equals(observed);
        if (published) externalChanged.set(false);
        message.set(null);
        fontError.set((published ? "配置已发布，资源更新失败：" : "应用失败：") + reason(failure));
    }

    public void save() {
        write();
        domain.enqueue(() -> {
            if (scope.isDisposed()) return;
            synchronize();
            if (dirty.get()) { message.set(new Feedback("先应用更改", false)); return; }
            if (!unsaved.get()) return;
            clearFeedback();
            try {
                configuration.save();
                message.set(new Feedback("已保存到 " + configuration.path(), true));
            } catch (IOException | RuntimeException failure) { fileFailure("保存", failure); }
        }, failure -> { if (!scope.isDisposed()) fileFailure("保存后的通知", failure); });
    }

    /** 放弃未落盘变化的确认归页面模态；本方法执行已确认的实际加载。 */
    public void reload() {
        write();
        domain.enqueue(() -> {
            if (scope.isDisposed()) return;
            clearFeedback();
            try {
                configuration.reload();
                observed = configuration.current();
                restore(observed);
                message.set(new Feedback("已从 " + configuration.path() + " 重新加载", true));
            } catch (IOException | RuntimeException failure) { fileFailure("重新加载", failure); }
        }, failure -> { if (!scope.isDisposed()) fileFailure("重新加载后的资源更新", failure); });
    }

    private void fileFailure(String action, Throwable failure) {
        // load 可能已发布而同步观察者失败：更新观察基线，保留原草稿与真实保存基线。
        observed = configuration.current();
        message.set(null);
        fileError.set(action + "失败：" + configuration.path() + " — " + reason(failure));
    }

    private static String reason(Throwable failure) {
        StringBuilder result = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            String text = current.getMessage();
            if (text == null || text.isEmpty()) text = current.getClass().getSimpleName();
            if (result.length() > 0) result.append("；");
            result.append(text);
            if (current.getCause() == current) break;
        }
        return result.toString();
    }
}
