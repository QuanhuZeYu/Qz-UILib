package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;

/** 一次轨道翻页的有限位置意图；目标位置仍由视口原所有者限位。 */
final class SceneScrollPage {
    final Signal<Boolean> active;
    private long started;
    private int from, to;
    SceneScrollPage(ReactiveDomain domain) { active = domain.signal(false); }
    void begin(SceneScrollViewport.Snapshot view, int direction, long now) {
        started = now; from = view.offset;
        to = (int) Math.max(0L, Math.min(view.maximum, (long) from + (long) direction * view.extent));
        active.set(to != from);
    }
    void advance(SceneScrollViewport viewport, SceneScrollViewport.Snapshot view, long now) {
        if (!active.get()) return;
        double progress = Math.max(0, Math.min(1, (double) (now - started) / 160_000_000L));
        int position = (int) Math.round(from + (to - (double) from) * (1 - Math.pow(1 - progress, 3)));
        viewport.moveToPixels(position);
        if (progress >= 1) stop();
    }
    void stop() { active.set(false); }
}
