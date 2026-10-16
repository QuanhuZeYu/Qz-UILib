package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** 只追踪挂载观察者需要的发布差异；当前位置权威始终归 SceneRuntime。 */
final class SceneScrollObservers {
    private final Map<SceneNode, Watch> watches = new IdentityHashMap<>();
    private SceneGeometryView publishedGeometry;
    private long publishedRevision;
    AutoCloseable observe(SceneNode viewport, SceneGeometryView.Entry initial, Consumer<SceneGeometryView.Entry> changed) {
        // 初值消费成功后才登记，回调失败不能留下无法释放的观察者。
        changed.accept(initial);
        Watch watch = watches.computeIfAbsent(viewport, ignored -> new Watch(initial));
        watch.listeners.add(changed);
        publishedGeometry = null;
        return () -> {
            watch.listeners.remove(changed);
            if (watch.listeners.isEmpty()) watches.remove(viewport, watch);
        };
    }
    void publish(SceneGeometryView geometry, Predicate<SceneNode> eligible, long revision) {
        // 静止帧复用发布结果；禁用、模态等交互资格通过原代际独立失效。
        if (publishedGeometry == geometry && publishedRevision == revision) return;
        publishedGeometry = geometry; publishedRevision = revision;
        for (Map.Entry<SceneNode, Watch> observed : new ArrayList<>(watches.entrySet())) {
            SceneNode viewport = observed.getKey();
            SceneGeometryView.Entry entry = viewport.scrollable() && eligible.test(viewport) ? geometry.get(viewport) : null;
            Watch watch = observed.getValue();
            if (watch.same(entry)) continue;
            watch.remember(entry);
            for (Consumer<SceneGeometryView.Entry> listener : new ArrayList<>(watch.listeners)) listener.accept(entry);
        }
    }
    void clear() { watches.clear(); publishedGeometry = null; }
    private static final class Watch {
        final List<Consumer<SceneGeometryView.Entry>> listeners = new ArrayList<>();
        private boolean present;
        private int offset, maximum, width, height;
        Watch(SceneGeometryView.Entry entry) { remember(entry); }
        boolean same(SceneGeometryView.Entry entry) {
            if (entry == null) return !present;
            return present && offset == entry.scrollOffset && maximum == entry.scrollMaximum
                    && width == entry.box.width && height == entry.box.height;
        }
        void remember(SceneGeometryView.Entry entry) {
            present = entry != null;
            offset = entry == null ? 0 : entry.scrollOffset; maximum = entry == null ? 0 : entry.scrollMaximum;
            width = entry == null ? 0 : entry.box.width; height = entry == null ? 0 : entry.box.height;
        }
    }
}
