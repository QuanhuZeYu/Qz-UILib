package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutPresentation;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.MotionValue;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** 尺寸先参与真实分配，重排轨迹再作用于局部原点；滚动不进入重排目标。 */
final class SceneLayoutMotions implements SceneTransitions.Step {
    private static final class Scope {
        MotionSpec spec;
        List<SceneNode> order = new ArrayList<>();
        boolean reordered;
        Scope(MotionSpec spec) { this.spec = spec; }
    }
    private static final class Dimensions {
        final MotionValue width = new MotionValue(), height = new MotionValue();
        boolean initialized;
    }
    private static final class Position {
        final MotionValue x = new MotionValue(), y = new MotionValue();
        SceneNode parent;
        int baseX, baseY;
        LayoutBox published;
    }
    private final SceneRuntime runtime;
    private final Map<SceneNode, Scope> positions = new IdentityHashMap<>();
    private final Map<SceneNode, MotionSpec> sizes = new IdentityHashMap<>();
    private final Map<SceneNode, Dimensions> dimensions = new IdentityHashMap<>();
    private final Map<SceneNode, Position> offsets = new IdentityHashMap<>();
    SceneLayoutMotions(SceneRuntime runtime) { this.runtime = runtime; }
    void configure(SceneNode node, boolean size, MotionSpec spec) {
        if (size) sizes.put(node, spec);
        else { Scope scope = positions.get(node); if (scope == null) positions.put(node, new Scope(spec)); else scope.spec = spec; }
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
            if (!state.initialized) {
                state.width.snap(box.box.width, now); state.height.snap(box.box.height, now); state.initialized = true;
            }
            projectBound(state.width, node.width(), now); projectBound(state.height, node.height(), now);
            state.width.retarget(box.box.width, entry.getValue(), now);
            state.height.retarget(box.box.height, entry.getValue(), now);
            overrides |= current(state.width, node.width()) != box.box.width || current(state.height, node.height()) != box.box.height;
        }
        schedule(); return overrides;
    }
    private static void projectBound(MotionValue value, Size bound, long now) {
        if (value.value() < bound.min || value.value() > bound.max) value.snap(current(value, bound), now);
    }
    private boolean sizeMoves(SceneNode parent) {
        for (SceneNode child : parent.children()) {
            Dimensions state = dimensions.get(child);
            if (state != null && (state.width.active() || state.height.active())) return true;
        }
        return false;
    }
    boolean preparePositions(SceneGeometryView current) {
        long now = runtime.inputNanos();
        for (Map.Entry<SceneNode, Scope> entry : positions.entrySet()) {
            Scope scope = entry.getValue();
            scope.reordered = !scope.order.equals(entry.getKey().children());
            scope.order = new ArrayList<>(entry.getKey().children());
        }
        java.util.Set<SceneNode> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<SceneNode, Boolean>());
        for (SceneGeometryView.Entry entry : current.sourceOrdered()) {
            SceneNode node = entry.node, parent = node.parent(); Scope scope = positions.get(parent);
            if (scope == null || current.layerBoundary(node) == node) continue;
            SceneGeometryView.Entry parentBox = current.get(parent);
            if (parentBox == null) continue;
            seen.add(node);
            int x = entry.box.x - parentBox.box.x + (parent.scrollable() && parent.axis() == SceneNode.Axis.ROW ? parentBox.scrollOffset - runtime.scrollPresentationOffset(parent) : 0) - Math.round(node.translationX());
            int y = entry.box.y - parentBox.box.y + (parent.scrollable() && parent.axis() == SceneNode.Axis.COLUMN ? parentBox.scrollOffset - runtime.scrollPresentationOffset(parent) : 0) - Math.round(node.translationY());
            Position state = offsets.get(node);
            if (state == null) {
                state = new Position(); state.x.snap(0, now); state.y.snap(0, now);
                state.parent = parent; state.baseX = x; state.baseY = y; offsets.put(node, state);
            } else if (state.parent != parent && state.published != null) {
                state.x.shift(state.published.x - entry.box.x - state.x.value());
                state.y.shift(state.published.y - entry.box.y - state.y.value());
                state.parent = parent;
            } else if (scope.reordered || !sizeMoves(parent)) {
                state.x.shift(state.baseX - x); state.y.shift(state.baseY - y);
            }
            state.baseX = x; state.baseY = y;
            state.x.retarget(0, scope.spec, now); state.y.retarget(0, scope.spec, now);
        }
        offsets.keySet().retainAll(seen); schedule();
        for (Position state : offsets.values()) if (state.x.value() != 0 || state.y.value() != 0) return true;
        return false;
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
                if (state == null) return original;
                return Size.fixed(current(axis == SceneNode.Axis.ROW ? state.width : state.height, original)).bounds(original.min, original.max);
            }
            public LayoutBox place(SceneNode node, LayoutBox box) {
                Position state = place ? offsets.get(node) : null;
                if (state == null) return box;
                return new LayoutBox(box.x + (int) Math.round(state.x.value()), box.y + (int) Math.round(state.y.value()), box.width, box.height);
            }
        };
    }
    private static int current(MotionValue value, Size size) {
        return (int) Math.max(size.min, Math.min((long) size.max, Math.round(value.value())));
    }
    public boolean advance(long now) {
        boolean changed = false;
        for (Dimensions state : dimensions.values()) {
            long w = Math.round(state.width.value()), h = Math.round(state.height.value());
            state.width.advance(now); state.height.advance(now);
            changed |= w != Math.round(state.width.value()) || h != Math.round(state.height.value());
        }
        for (Position state : offsets.values()) {
            long x = Math.round(state.x.value()), y = Math.round(state.y.value());
            state.x.advance(now); state.y.advance(now);
            changed |= x != Math.round(state.x.value()) || y != Math.round(state.y.value());
        }
        if (changed) runtime.invalidateGeometry();
        return active();
    }
    private boolean active() {
        for (Dimensions state : dimensions.values()) if (state.width.active() || state.height.active()) return true;
        for (Position state : offsets.values()) if (state.x.active() || state.y.active()) return true;
        return false;
    }
    private void schedule() { if (active()) runtime.transitions().start(this); else runtime.transitions().stop(this); }
}
