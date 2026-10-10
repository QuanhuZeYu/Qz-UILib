package club.heiqi.uilib.host.client.localization;

import java.util.function.Consumer;
import club.heiqi.uilib.ui.scene.localization.LocalizationSnapshot;
import club.heiqi.uilib.ui.scene.localization.LocalizationSource;
import club.heiqi.uilib.ui.scene.localization.MutableLocalizationSource;

/** MC语言资料owner：加载回调仅捕获，正常生命周期出口才执行消费者。
 * refreshResources会把RuntimeException认作坏资源包并清空配置，故不能在其reload调用内发布。
 * pending只合并最终资料事实；发布前消费标记，即使消费者失败也不在下一tick重放。 */
public final class MinecraftLocalizationSource implements LocalizationSource, AutoCloseable {
    private final MutableLocalizationSource source;
    private LocalizationSnapshot pending;
    private boolean closed;
    public MinecraftLocalizationSource(LocalizationSnapshot initial) { source = new MutableLocalizationSource(initial); }
    public void loaded(LanguageManagerAccess manager) {
        if (closed) return;
        pending = MinecraftLocalizationServices.capture(manager);
    }
    public void dispatchPending() {
        if (closed || pending == null) return;
        LocalizationSnapshot next = pending; pending = null; source.publish(next);
    }
    @Override public LocalizationSnapshot current() { return source.current(); }
    @Override public Subscription subscribe(Consumer<LocalizationSnapshot> listener) { return source.subscribe(listener); }
    @Override public void close() { if (closed) return; closed = true; pending = null; source.close(); }
}
