package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 只持有滑块的有限可见性，不持有滚动位置；挂载结束后释放计时状态。 */
final class SceneScrollVisibility {
    final Signal<Float> opacity;
    final Signal<Long> wake;
    private final SceneRuntime runtime;
    private final ReadableSignal<SceneScrollArea.Options> source;
    private boolean hovered, areaHovered, dragging;
    private long fadeAt;

    SceneScrollVisibility(SceneRuntime runtime, ReadableSignal<SceneScrollArea.Options> source) {
        this.runtime = runtime; this.source = source;
        opacity = runtime.domain().signal(0f); wake = runtime.domain().signal(Long.MAX_VALUE);
    }
    void activity() {
        opacity.set(1f);
        schedule();
    }
    void hover(boolean inside) {
        hovered = inside;
        if (inside) activity(); else schedule();
    }
    void areaHover(boolean inside) {
        areaHovered = inside;
        if (inside) activity(); else schedule();
    }
    void configure() { schedule(); }
    void drag(boolean active) {
        dragging = active;
        if (active) activity(); else schedule();
    }
    private void schedule() {
        SceneScrollArea.Options options = source.get();
        fadeAt = runtime.inputNanos() + options.hideDelayMillis * 1_000_000L;
        wake.set(options.autoHide && !hovered && !areaHovered && !dragging && opacity.get() > 0 ? fadeAt : Long.MAX_VALUE);
    }
    long next() { return source.get().autoHide ? wake.get() : Long.MAX_VALUE; }
    float alpha() { return source.get().autoHide ? opacity.get() : 1f; }
    void advance(long now) {
        SceneScrollArea.Options options = source.get();
        if (!options.autoHide || hovered || areaHovered || dragging) { wake.set(Long.MAX_VALUE); return; }
        long elapsed = now - fadeAt;
        if (elapsed < 0) return;
        float value = options.fadeMillis == 0 ? 0 : Math.max(0f, 1f - (float) ((double) elapsed / (options.fadeMillis * 1_000_000L)));
        opacity.set(value); wake.set(value > 0 ? 0L : Long.MAX_VALUE);
    }
    void reset() { hovered = false; areaHovered = false; dragging = false; opacity.set(0f); wake.set(Long.MAX_VALUE); }
}
