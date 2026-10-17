package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutPresentation;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.MotionValue;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 尺寸先参与真实分配，重排轨迹再作用于局部原点；滚动不进入重排目标。 */
final class SceneLayoutMotions implements SceneTransitions.Step {
    private static final class Scope {
        MotionSpec spec;
        List<SceneNode> order = new ArrayList<>();
        boolean reordered, resizing;
        Scope(MotionSpec spec) { this.spec = spec; }
        void order(List<SceneNode> next) {
            reordered = !order.equals(next);
            if (reordered) order = new ArrayList<>(next);
        }
    }
    private static final class Dimensions {
        final MotionValue width = new MotionValue(), height = new MotionValue();
        boolean initialized;
        void retarget(SceneNode node, LayoutBox box, MotionSpec spec, long now) {
            if (!initialized) {
                width.snap(box.width, now); height.snap(box.height, now); initialized = true;
            }
            projectBound(width, node.width(), now); projectBound(height, node.height(), now);
            width.retarget(box.width, spec, now); height.retarget(box.height, spec, now);
        }
        boolean differs(SceneNode node, LayoutBox box) {
            return current(width, node.width()) != box.width || current(height, node.height()) != box.height;
        }
        Size size(SceneNode.Axis axis, Size original) {
            return Size.fixed(current(axis == SceneNode.Axis.ROW ? width : height, original)).bounds(original.min, original.max);
        }
        boolean advance(long now) {
            long w = Math.round(width.value()), h = Math.round(height.value());
            width.advance(now); height.advance(now);
            return w != Math.round(width.value()) || h != Math.round(height.value());
        }
        boolean active() { return width.active() || height.active(); }
    }
    /** 局部基准与世界呈现只用于相应的换基，不作为新的布局权威。 */
    private static final class Position {
        final MotionValue x = new MotionValue(), y = new MotionValue();
        SceneNode parent;
        int baseX, baseY;
        LayoutBox published;
        Position(SceneNode parent, int x, int y, long now) {
            this.parent = parent; baseX = x; baseY = y;
            this.x.snap(0, now); this.y.snap(0, now);
        }
        void retarget(SceneNode parent, LayoutBox box, int nextX, int nextY, boolean rebase, MotionSpec spec, long now) {
            if (this.parent != parent && published != null) {
                x.shift(published.x - box.x - x.value()); y.shift(published.y - box.y - y.value());
                this.parent = parent;
            } else if (rebase) {
                x.shift(baseX - nextX); y.shift(baseY - nextY);
            }
            baseX = nextX; baseY = nextY;
            x.retarget(0, spec, now); y.retarget(0, spec, now);
        }
        LayoutBox place(LayoutBox box) {
            return new LayoutBox(box.x + (int) Math.round(x.value()), box.y + (int) Math.round(y.value()), box.width, box.height);
        }
        boolean displaced() { return x.value() != 0 || y.value() != 0; }
        boolean advance(long now) {
            long beforeX = Math.round(x.value()), beforeY = Math.round(y.value());
            x.advance(now); y.advance(now);
            return beforeX != Math.round(x.value()) || beforeY != Math.round(y.value());
        }
        boolean active() { return x.active() || y.active(); }
    }
    private final SceneRuntime runtime;
    private final Map<SceneNode, Scope> positions = new IdentityHashMap<>();
    private final Map<SceneNode, MotionSpec> sizes = new IdentityHashMap<>();
    private final Map<SceneNode, Dimensions> dimensions = new IdentityHashMap<>();
    private final Map<SceneNode, Position> offsets = new IdentityHashMap<>();
    SceneLayoutMotions(SceneRuntime runtime) { this.runtime = runtime; }
    void configure(SceneNode node, boolean size, MotionSpec spec) {
        if (size) sizes.put(node, spec);
        else {
            Scope scope = positions.get(node);
            if (scope == null) positions.put(node, new Scope(spec)); else scope.spec = spec;
        }
        runtime.invalidateGeometry();
    }
    void remove(SceneNode node, boolean size) {
        if (size) { sizes.remove(node); dimensions.remove(node); }
        else { positions.remove(node); offsets.entrySet().removeIf(entry -> entry.getValue().parent == node); }
        schedule();
    }
    void release(SceneNode node) { remove(node, true); remove(node, false); offsets.remove(node); }
    boolean declared() { return !positions.isEmpty() || !sizes.isEmpty(); }
    boolean prepareSizes(SceneGeometryView target) {
        long now = runtime.inputNanos(); boolean overrides = false;
        for (Map.Entry<SceneNode, MotionSpec> entry : sizes.entrySet()) {
            SceneNode node = entry.getKey(); SceneGeometryView.Entry box = target.get(node);
            if (box == null) { dimensions.remove(node); continue; }
            Dimensions state = dimensions.computeIfAbsent(node, ignored -> new Dimensions());
            state.retarget(node, box.box, entry.getValue(), now);
            overrides |= state.differs(node, box.box);
        }
        schedule(); return overrides;
    }
    private static void projectBound(MotionValue value, Size bound, long now) {
        if (value.value() < bound.min || value.value() > bound.max) value.snap(current(value, bound), now);
    }
    private boolean sizeMoves(SceneNode parent) {
        for (SceneNode child : parent.children()) {
            Dimensions state = dimensions.get(child);
            if (state != null && state.active()) return true;
        }
        return false;
    }
    boolean preparePositions(SceneGeometryView current) {
        long now = runtime.inputNanos();
        for (Map.Entry<SceneNode, Scope> entry : positions.entrySet()) {
            Scope scope = entry.getValue();
            scope.order(entry.getKey().children());
            // 一个作用域的尺寸活动只求一次，避免每个孩子重复扫描全部兄弟。
            scope.resizing = sizeMoves(entry.getKey());
        }
        Set<SceneNode> seen = Collections.newSetFromMap(new IdentityHashMap<SceneNode, Boolean>());
        for (SceneGeometryView.Entry entry : current.sourceOrdered()) preparePosition(entry, current, seen, now);
        offsets.keySet().retainAll(seen); schedule();
        for (Position state : offsets.values()) if (state.displaced()) return true;
        return false;
    }
    private void preparePosition(SceneGeometryView.Entry entry, SceneGeometryView geometry, Set<SceneNode> seen, long now) {
        SceneNode node = entry.node, parent = node.parent(); Scope scope = positions.get(parent);
        if (scope == null || geometry.layerBoundary(node) == node) return;
        SceneGeometryView.Entry parentBox = geometry.get(parent);
        if (parentBox == null) return;
        seen.add(node);
        int scroll = parentBox.scrollOffset - runtime.scrollPresentationOffset(parent);
        int x = entry.box.x - parentBox.box.x - Math.round(node.translationX());
        int y = entry.box.y - parentBox.box.y - Math.round(node.translationY());
        if (parent.scrollable()) {
            if (parent.axis() == SceneNode.Axis.ROW) x += scroll; else y += scroll;
        }
        Position state = offsets.get(node);
        if (state == null) {
            state = new Position(parent, x, y, now); offsets.put(node, state);
        }
        state.retarget(parent, entry.box, x, y, scope.reordered || !scope.resizing, scope.spec, now);
    }
    void published(SceneGeometryView geometry) {
        for (Map.Entry<SceneNode, Position> entry : offsets.entrySet()) {
            SceneGeometryView.Entry box = geometry.get(entry.getKey());
            entry.getValue().published = box == null ? null : box.box;
        }
    }
    SceneLayoutPresentation presentation(boolean place) {
        return new SceneLayoutPresentation() {
            public Size size(SceneNode node, SceneNode.Axis axis) {
                Size original = axis == SceneNode.Axis.ROW ? node.width() : node.height();
                Dimensions state = dimensions.get(node);
                return state == null ? original : state.size(axis, original);
            }
            public LayoutBox place(SceneNode node, LayoutBox box) {
                Position state = place ? offsets.get(node) : null;
                return state == null ? box : state.place(box);
            }
        };
    }
    private static int current(MotionValue value, Size size) {
        return (int) Math.max(size.min, Math.min((long) size.max, Math.round(value.value())));
    }
    public boolean advance(long now) {
        boolean changed = false;
        for (Dimensions state : dimensions.values()) changed |= state.advance(now);
        for (Position state : offsets.values()) changed |= state.advance(now);
        if (changed) runtime.invalidateGeometry();
        return active();
    }
    private boolean active() {
        for (Dimensions state : dimensions.values()) if (state.active()) return true;
        for (Position state : offsets.values()) if (state.active()) return true;
        return false;
    }
    private void schedule() { if (active()) runtime.transitions().start(this); else runtime.transitions().stop(this); }
}
