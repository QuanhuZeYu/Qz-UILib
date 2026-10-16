package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/** 轴端口和旧Entry适配共用同一订阅生命周期，只保存发布差异，不持有位置权威。 */
final class SceneScrollObservers {
    private final Set<Observation<?>> observers = new LinkedHashSet<>();
    private SceneGeometryView publishedGeometry;
    private long publishedRevision;
    <T> AutoCloseable observe(T initial, Function<SceneGeometryView, T> read, BiPredicate<T, T> same, Consumer<T> changed) {
        // 初值回调成功才登记，失败不会留下无法释放的订阅。
        changed.accept(initial);
        Observation<T> observation = new Observation<>(initial, read, same, changed);
        observers.add(observation); publishedGeometry = null;
        return () -> observers.remove(observation);
    }
    void publish(SceneGeometryView geometry, long revision) {
        if (publishedGeometry == geometry && publishedRevision == revision) return;
        publishedGeometry = geometry; publishedRevision = revision;
        for (Observation<?> observation : new ArrayList<>(observers))
            if (observers.contains(observation)) observation.publish(geometry);
    }
    void clear() { observers.clear(); publishedGeometry = null; }
    private static final class Observation<T> {
        private final Function<SceneGeometryView, T> read;
        private final BiPredicate<T, T> same;
        private final Consumer<T> changed;
        private T current;
        Observation(T initial, Function<SceneGeometryView, T> read, BiPredicate<T, T> same, Consumer<T> changed) {
            current = initial; this.read = read; this.same = same; this.changed = changed;
        }
        void publish(SceneGeometryView geometry) {
            T next = read.apply(geometry);
            if (current == null ? next == null : next != null && same.test(current, next)) return;
            current = next; changed.accept(next);
        }
    }
}
