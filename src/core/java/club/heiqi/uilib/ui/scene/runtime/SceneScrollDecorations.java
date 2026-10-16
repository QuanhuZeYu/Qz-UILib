package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneScrollArea;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.control.SceneScrollViewport;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 滚动声明自带框架装饰；只在挂载/声明通知后装配，不扫描静止页面。 */
final class SceneScrollDecorations {
    private final SceneRuntime runtime;
    private final Map<SceneNode, List<SceneNode>> defaults = new IdentityHashMap<>();
    private final Set<SceneNode> pending = new LinkedHashSet<>();
    private final List<SceneScrollViewport> explicit = new ArrayList<>();
    private final Signal<ReadableSignal<SceneTheme>> theme;
    SceneScrollDecorations(SceneRuntime runtime) {
        this.runtime = runtime; theme = runtime.domain().signal(SceneTheme::light);
    }
    void theme(ReadableSignal<SceneTheme> source) { theme.set(source); }
    void changed(SceneNode node) { pending.add(node); }
    boolean pending() { return !pending.isEmpty(); }
    List<SceneNode> nodes(SceneNode host) { return defaults.getOrDefault(host, Collections.emptyList()); }
    AutoCloseable claim(SceneScrollViewport viewport) {
        explicit.add(viewport); runtime.invalidateScrollDecorations();
        return () -> { explicit.remove(viewport); runtime.invalidateScrollDecorations(); };
    }
    boolean claimed(SceneNode host, SceneNode.Axis axis) {
        for (SceneScrollViewport viewport : explicit) if (viewport.node() == host && viewport.axis() == axis) return true;
        return false;
    }
    void settle() {
        List<SceneNode> batch = new ArrayList<>(pending); pending.clear();
        for (SceneNode host : batch) {
            if (host.runtime() != runtime) continue;
            boolean needed = host.scrollable() || host.control() != null && host.control().scrollbarAxes(host) != SceneScrollArea.Axes.NONE;
            if (needed && !defaults.containsKey(host)) install(host);
            else if (!needed) for (SceneNode bar : remove(host)) runtime.releaseScrollDecoration(bar);
        }
    }
    private void install(SceneNode host) {
        List<SceneNode> bars = Arrays.asList(
                SceneScrollbar.automatic(runtime, host, SceneNode.Axis.ROW, () -> theme.get().get()),
                SceneScrollbar.automatic(runtime, host, SceneNode.Axis.COLUMN, () -> theme.get().get()));
        defaults.put(host, bars);
        for (SceneNode bar : bars) runtime.mountScrollDecoration(host, bar);
    }
    List<SceneNode> remove(SceneNode host) {
        pending.remove(host); List<SceneNode> bars = defaults.remove(host);
        return bars == null ? Collections.emptyList() : bars;
    }
}
