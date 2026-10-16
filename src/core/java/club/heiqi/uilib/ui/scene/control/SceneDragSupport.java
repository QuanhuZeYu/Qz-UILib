package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 拖动控件共用的纯资格/几何及有界滚动计算；不持有第二套场景几何。 */
final class SceneDragSupport {
    private SceneDragSupport() {}

    /** 作用域只观察正式拖动；normalDrop 返回清理后的业务动作，cancel 永不提交。 */
    interface Observer {
        boolean same(Observer other);
        /** 同一来源身份的当前配置刷新；same 只比较，不修改挂载或会话状态。 */
        default void configure(SceneNode node, Observer next) {}
        void attach(SceneRuntime runtime, SceneNode node, Runnable cancel);
        void detach(SceneNode node);
        void cancel(SceneNode node);
        void update(SceneNode node, SceneGeometryView geometry, boolean keyboard, LayoutBox box, int x, int y);
        Runnable normalDrop(SceneNode node, SceneGeometryView geometry, boolean keyboard, LayoutBox box, int x, int y);
        LayoutBox keyboardTarget(SceneNode node, SceneGeometryView geometry, SceneEditEvent.Command direction);
        SceneNode scrollSource(SceneNode node, SceneGeometryView geometry);
    }

    static LayoutBox visibleBox(SceneGeometryView geometry, SceneGeometryView.Entry entry) {
        if (entry == null) return null;
        LayoutBox box = entry.box.intersect(geometry.viewport());
        return entry.clip == null ? box : box.intersect(entry.clip);
    }
    static int centerX(LayoutBox box) { return integer((long) box.x + box.width / 2); }
    static int centerY(LayoutBox box) { return integer((long) box.y + box.height / 2); }
    static boolean within(SceneNode child, SceneNode parent) {
        for (SceneNode cursor = child; cursor != null; cursor = cursor.parent()) if (cursor == parent) return true;
        return false;
    }
    static boolean eligible(SceneNode node, SceneNode root) {
        if (node == null || !within(node, root)) return false;
        for (SceneNode cursor = node; cursor != null; cursor = cursor.parent()) {
            if (!cursor.visible() || !cursor.enabled() || cursor.destroyed()) return false;
            if (cursor == root) return true;
        }
        return false;
    }
    static boolean inside(SceneGeometryView.Entry entry, int x, int y) {
        return entry != null && entry.box.contains(x, y) && (entry.clip == null || entry.clip.contains(x, y));
    }
    static boolean visible(SceneGeometryView geometry, SceneNode node) {
        SceneGeometryView.Entry entry = geometry.get(node);
        if (entry == null) return false;
        LayoutBox box = entry.box.intersect(geometry.viewport());
        if (entry.clip != null) box = box.intersect(entry.clip);
        return box.width > 0 && box.height > 0;
    }
    static SceneNode handle(SceneNode root, SceneNode[] handles, SceneGeometryView geometry, int x, int y) {
        for (SceneNode handle : handles)
            if (eligible(handle, root) && inside(geometry.get(handle), x, y)) return handle;
        return null;
    }
    static boolean handleFocus(SceneNode root, SceneNode[] handles, SceneNode focused) {
        for (SceneNode handle : handles)
            if (eligible(handle, root) && within(focused, handle)) return true;
        return false;
    }
    static boolean dragCommand(SceneEditEvent event) {
        if (event.command == null || event.composing || event.compositionEvent) return false;
        switch (event.command) {
            case ACTIVATE_ONLY: case SPACE: case ESCAPE: case LEFT: case RIGHT: case UP: case DOWN: return true;
            default: return false;
        }
    }
    /** 半平面中的近邻评分；跨轴偏移参与距离，背向候选不参与。 */
    static double directionScore(LayoutBox origin, LayoutBox box, boolean horizontal, boolean positive) {
        double dx = (double) centerX(box) - centerX(origin), dy = (double) centerY(box) - centerY(origin);
        double primary = horizontal ? dx : dy, cross = horizontal ? dy : dx;
        if (positive ? primary <= 0 : primary >= 0) return Double.POSITIVE_INFINITY;
        return primary * primary + 4 * cross * cross;
    }
    static int integer(long value) { return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value)); }
    static int snap(int value, int grid) {
        return grid <= 0 ? value : integer(Math.round((double) value / grid) * (long) grid);
    }
    static int clamp(long wanted, int size, int origin, int available) {
        return integer(Math.max(origin, Math.min(Math.max((long) origin, (long) origin + available - size), wanted)));
    }
    static LayoutBox inner(SceneGeometryView.Entry entry) {
        int p = entry.node.padding();
        return new LayoutBox(integer((long) entry.box.x + p), integer((long) entry.box.y + p),
                (int) Math.max(0L, (long) entry.box.width - 2L * p),
                (int) Math.max(0L, (long) entry.box.height - 2L * p));
    }

    /** 每轴只保存时间及亚像素余量；最近视口与窗口来自当前共同几何。 */
    static final class AutoScroll {
        private long last;
        private boolean sampled;
        private final AxisScroll horizontal = new AxisScroll(SceneNode.Axis.ROW);
        private final AxisScroll vertical = new AxisScroll(SceneNode.Axis.COLUMN);
        void reset(long now) { last = now; sampled = true; horizontal.reset(); vertical.reset(); }
        static final class Shift {
            static final Shift NONE = new Shift(null, null, 0, 0);
            final SceneNode xScroller, yScroller;
            final int x, y;
            Shift(SceneNode xScroller, SceneNode yScroller, int x, int y) { this.xScroller = xScroller; this.yScroller = yScroller; this.x = x; this.y = y; }
        }
        Shift advance(SceneRuntime runtime, SceneNode source, SceneGeometryView geometry,
                int x, int y, long now, boolean allowX, boolean allowY) {
            return advance(runtime, source, geometry, x, y, now, allowX, allowY, 1.0);
        }
        Shift advance(SceneRuntime runtime, SceneNode source, SceneGeometryView geometry,
                int x, int y, long now, boolean allowX, boolean allowY, double speed) {
            long elapsed = sample(now);
            if (source == null || !runtime.interactive(source) || speed == 0) { horizontal.reset(); vertical.reset(); return Shift.NONE; }
            int dx = horizontal.advance(runtime, source, geometry, x, y, elapsed, speed, allowX);
            int dy = vertical.advance(runtime, source, geometry, x, y, elapsed, speed, allowY);
            return dx == 0 && dy == 0 ? Shift.NONE : new Shift(horizontal.previous, vertical.previous, -dx, -dy);
        }
        private long sample(long now) {
            long elapsed = sampled ? Math.max(0L, Math.min(50_000_000L, now - last)) : 0;
            sampled = true; last = now; return elapsed;
        }
        private static final class AxisScroll {
            private final SceneNode.Axis axis;
            private double remainder;
            private SceneNode previous;
            private int direction;
            AxisScroll(SceneNode.Axis axis) { this.axis = axis; }
            void reset() { remainder = 0; previous = null; direction = 0; }
            int advance(SceneRuntime runtime, SceneNode source, SceneGeometryView geometry,
                    int x, int y, long elapsed, double speed, boolean allowed) {
                SceneNode scroller = allowed ? nearest(runtime, source, geometry) : null;
                if (scroller == null) { reset(); return 0; }
                SceneScrollViewport.Snapshot view = runtime.scrollSnapshot(scroller, axis, geometry);
                if (view == null) { reset(); return 0; }
                LayoutBox window = view.box.intersect(geometry.viewport());
                if (view.clip != null) window = window.intersect(view.clip);
                double strength = strength(window, x, y);
                return integrate(runtime, scroller, strength, elapsed, speed);
            }
            private SceneNode nearest(SceneRuntime runtime, SceneNode source, SceneGeometryView geometry) {
                SceneNode boundary = geometry.layerBoundary(source);
                for (SceneNode node = source; node != null; node = node == boundary ? null : node.parent()) {
                    if (!runtime.interactive(node)) return null;
                    if (runtime.scrollSnapshot(node, axis, geometry) != null) return node;
                }
                return null;
            }
            private double strength(LayoutBox window, int x, int y) {
                if (window.width == 0 || window.height == 0) return 0;
                boolean horizontal = axis == SceneNode.Axis.ROW;
                int cross = horizontal ? y : x, start = horizontal ? window.y : window.x, size = horizontal ? window.height : window.width;
                if (cross < start || (long) cross >= (long) start + size) return 0;
                return edgeStrength(horizontal ? x : y, horizontal ? window.x : window.y, horizontal ? window.width : window.height);
            }
            private int integrate(SceneRuntime runtime, SceneNode scroller, double strength, long elapsed, double speed) {
                int sign = Double.compare(strength, 0);
                if (scroller != previous || sign != direction) remainder = 0;
                previous = scroller; direction = sign;
                if (sign == 0) return 0;
                double pixels = remainder + strength * 720.0 * speed * elapsed / 1_000_000_000.0;
                int whole = (int) pixels;
                remainder = pixels - whole;
                int actual = runtime.scrollByPixels(scroller, axis, whole);
                if (actual != whole) remainder = 0;
                return actual;
            }
        }
        private static double edgeStrength(int point, int start, int size) {
            double edge = Math.min(48.0, size / 3.0);
            if (point < start + edge) return -Math.min(1.0, (start + edge - point) / edge);
            if (point > start + size - edge) return Math.min(1.0, (point - start - size + edge) / edge);
            return 0;
        }
    }
}
