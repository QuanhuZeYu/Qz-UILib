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

    /** 只保存时间及亚像素余量；nearest 的窗口来自当前 clip，不把整块屏外容器当边缘。 */
    static final class AutoScroll {
        private long last;
        private boolean sampled;
        private double remainder;
        private SceneNode previous;
        private int direction;
        void reset(long now) { last = now; sampled = true; remainder = 0; previous = null; direction = 0; }
        static final class Shift {
            static final Shift NONE = new Shift(null, 0, 0);
            final SceneNode scroller;
            final int x, y;
            Shift(SceneNode scroller, int x, int y) { this.scroller = scroller; this.x = x; this.y = y; }
        }
        Shift advance(SceneRuntime runtime, SceneNode source, SceneGeometryView geometry,
                int x, int y, long now, boolean allowX, boolean allowY) {
            return advance(runtime, source, geometry, x, y, now, allowX, allowY, 1.0);
        }
        Shift advance(SceneRuntime runtime, SceneNode source, SceneGeometryView geometry,
                int x, int y, long now, boolean allowX, boolean allowY, double speed) {
            long elapsed = sampled ? Math.max(0L, Math.min(50_000_000L, now - last)) : 0;
            sampled = true; last = now;
            if (source == null || !runtime.interactive(source) || speed == 0) {
                remainder = 0; previous = null; direction = 0; return Shift.NONE;
            }
            SceneNode boundary = geometry.layerBoundary(source);
            for (SceneNode ancestor = source; ancestor != null; ancestor = ancestor == boundary ? null : ancestor.parent()) {
                if (!runtime.interactive(ancestor)) return Shift.NONE;
                if (!ancestor.scrollable()) continue;
                boolean horizontal = ancestor.axis() == SceneNode.Axis.ROW;
                if (horizontal ? !allowX : !allowY) continue;
                SceneGeometryView.Entry entry = geometry.get(ancestor);
                if (entry == null) return Shift.NONE;
                LayoutBox window = inner(entry).intersect(geometry.viewport());
                if (entry.clip != null) window = window.intersect(entry.clip);
                if (window.width == 0 || window.height == 0) return Shift.NONE;
                int point = horizontal ? x : y, start = horizontal ? window.x : window.y;
                int size = horizontal ? window.width : window.height;
                int cross = horizontal ? y : x, crossStart = horizontal ? window.y : window.x;
                int crossSize = horizontal ? window.height : window.width;
                if (cross < crossStart || (long) cross >= (long) crossStart + crossSize) return Shift.NONE;
                double edge = Math.min(48.0, size / 3.0);
                double strength = point < start + edge ? -Math.min(1.0, (start + edge - point) / edge)
                        : point > start + size - edge ? Math.min(1.0, (point - start - size + edge) / edge) : 0;
                int sign = Double.compare(strength, 0);
                if (ancestor != previous || sign != direction) remainder = 0;
                previous = ancestor; direction = sign;
                if (sign == 0) return Shift.NONE;
                double pixels = remainder + strength * 720.0 * speed * elapsed / 1_000_000_000.0;
                int whole = (int) pixels;
                remainder = pixels - whole;
                int actual = runtime.scrollByPixels(ancestor, whole);
                if (actual != whole) remainder = 0;
                return actual == 0 ? Shift.NONE : new Shift(ancestor, horizontal ? -actual : 0, horizontal ? 0 : -actual);
            }
            remainder = 0; previous = null; direction = 0;
            return Shift.NONE;
        }
    }
}
