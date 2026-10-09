package club.heiqi.uilib.ui.scene.layout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.IdentityHashMap;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
/** 先测内容，再按声明分配；固定尺寸不因父空间不足被缩小。 */
public final class SceneLayoutEngine {
    private final SceneTextMeasurer text;
    private final Map<SceneNode, Map<Long, int[]>> measuredCache = new IdentityHashMap<>();
    private final Map<SceneNode, Map<Integer, TextLayout>> textCache = new IdentityHashMap<>();
    public SceneLayoutEngine(SceneTextMeasurer text) { this.text = text; }
    public SceneGeometry layout(List<SceneNode> roots, int width, int height) {
        measuredCache.clear(); textCache.clear();
        List<SceneGeometryView.Entry> entries = new ArrayList<>();
        for (SceneNode root : roots) {
            int[] desired = measure(root, width, height);
            int w = cross(root.width(), desired[0], width), h = cross(root.height(), desired[1], height);
            arrange(root, new LayoutBox(0, 0, w, h), null, entries);
        }
        return new SceneGeometry(entries, new LayoutBox(0, 0, width, height));
    }
    private int cross(Size size, int desired, int available) {
        return size.constrain(size.mode == Size.Mode.FIXED ? size.value : size.mode == Size.Mode.FILL ? Math.max(0, available) : desired);
    }
    private TextLayout textLayout(SceneNode node, int width) {
        return textCache.computeIfAbsent(node, key -> new HashMap<>()).computeIfAbsent(width, key -> text.layout(node.text(), node.style(), width, node.textPolicy()));
    }
    private int[] measure(SceneNode node, int availableWidth, int availableHeight) {
        long constraintKey = ((long) availableWidth << 32) | (availableHeight & 0xffffffffL);
        return measuredCache.computeIfAbsent(node, key -> new HashMap<>()).computeIfAbsent(constraintKey, key -> measureUncached(node, availableWidth, availableHeight));
    }
    private int[] measureUncached(SceneNode node, int availableWidth, int availableHeight) {
        if (!node.visible()) return new int[] {0, 0};
        int measuredWidth = node.width().mode == Size.Mode.FIXED ? node.width().constrain(node.width().value) : node.width().constrain(availableWidth);
        int contentWidth = Math.max(0, measuredWidth - node.padding() * 2);
        // 自然高度尚未分配，不把父空间冒充自身高度；固定/fill 则携带真实有限约束。
        int measuredHeight = node.height().mode == Size.Mode.FIXED ? node.height().constrain(node.height().value)
            : node.height().mode == Size.Mode.FILL && availableHeight != Integer.MAX_VALUE ? node.height().constrain(availableHeight) : Integer.MAX_VALUE;
        int contentHeight = measuredHeight == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, measuredHeight - node.padding() * 2);
        if (node.control() != null) {
            MeasuredSize custom = node.control().measure(node, new Constraints(contentWidth, contentHeight), text);
            return new int[] {node.width().constrain(node.width().mode == Size.Mode.FIXED ? node.width().value : custom.width + node.padding() * 2),
                node.height().constrain(node.height().mode == Size.Mode.FIXED ? node.height().value : custom.height + node.padding() * 2)};
        }
        TextLayout lines = textLayout(node, contentWidth);
        int w = lines.width, h = lines.height;
        List<SceneNode> children = new ArrayList<>();
        List<int[]> measured = new ArrayList<>();
        for (SceneNode child : node.children()) if (child.visible()) { children.add(child); measured.add(measure(child, contentWidth, node.axis() == SceneNode.Axis.ROW ? contentHeight : Integer.MAX_VALUE)); }
        int gaps = Math.max(0, children.size() - 1) * node.gap();
        boolean row = node.axis() == SceneNode.Axis.ROW;
        int[] allocated = row ? allocate(children, measured, Math.max(0, contentWidth - gaps), true)
            : contentHeight != Integer.MAX_VALUE ? allocate(children, measured, Math.max(0, contentHeight - gaps), false) : null;
        int sum = 0, maximum = 0;
        for (int i = 0; i < children.size(); i++) {
            SceneNode child = children.get(i);
            // 自然高度以最终受限/分配宽度求解，并向父自然高度传播，不能绘制后读回修补。
            int allocatedHeight = row ? contentHeight : allocated == null ? Integer.MAX_VALUE : allocated[i];
            int[] heightAware = measure(child, contentWidth, allocatedHeight);
            int cw = row ? allocated[i] : cross(child.width(), heightAware[0], contentWidth);
            int[] actual = measure(child, cw, allocatedHeight);
            int ch = !row && allocated != null ? allocated[i] : child.height().constrain(child.height().mode == Size.Mode.FIXED ? child.height().value : actual[1]);
            sum += row ? cw : ch;
            maximum = Math.max(maximum, row ? ch : cw);
        }
        if (row) { w = Math.max(w, sum + gaps); h = Math.max(h, maximum); }
        else { w = Math.max(w, maximum); h = Math.max(h, sum + gaps); }
        return new int[] {node.width().constrain(node.width().mode == Size.Mode.FIXED ? node.width().value : w + node.padding() * 2),
            node.height().constrain(node.height().mode == Size.Mode.FIXED ? node.height().value : h + node.padding() * 2)};
    }
    private void arrange(SceneNode node, LayoutBox box, LayoutBox ancestorClip, List<SceneGeometryView.Entry> entries) {
        if (!node.visible()) return;
        int innerWidth = Math.max(0, box.width - node.padding() * 2);
        int innerHeight = Math.max(0, box.height - node.padding() * 2);
        LayoutBox ownClip = node.clip() || node.scrollable() ? box : null;
        LayoutBox clip = ancestorClip == null ? ownClip : ownClip == null ? ancestorClip : ancestorClip.intersect(ownClip);
        TextLayout lines = textLayout(node, innerWidth);
        if (node.control() != null) lines = node.control().presentText(node, lines, innerWidth, innerHeight, text);
        int entryIndex = entries.size();
        entries.add(new SceneGeometryView.Entry(node, box, clip, lines));
        if (node.control() != null) {
            java.util.Set<SceneNode> placed = java.util.Collections.newSetFromMap(new IdentityHashMap<SceneNode, Boolean>());
            List<ChildPlacements.Entry> placements = node.control().arrange(node, box).entries();
            boolean row = node.axis() == SceneNode.Axis.ROW;
            long extent = 0;
            for (ChildPlacements.Entry child : placements) extent = Math.max(extent,
                row ? (long) child.relativeBox.x + child.relativeBox.width : (long) child.relativeBox.y + child.relativeBox.height);
            int maximum = node.scrollable() ? (int) Math.min(Integer.MAX_VALUE, Math.max(0L, extent - (row ? box.width : box.height))) : 0;
            int desired = node.runtime() == null ? node.scrollOffset() : node.runtime().scrollOffset(node);
            int scroll = node.scrollable() ? Math.min(desired, maximum) : 0;
            entries.set(entryIndex, new SceneGeometryView.Entry(node, box, clip, lines, scroll, maximum));
            for (ChildPlacements.Entry child : placements) {
                if (child.child.parent() != node || !placed.add(child.child)) throw new IllegalArgumentException("控件排列包含非逻辑子节点或重复节点");
                LayoutBox relative = child.relativeBox;
                arrange(child.child, new LayoutBox(box.x + relative.x - (row ? scroll : 0), box.y + relative.y - (row ? 0 : scroll), relative.width, relative.height), clip, entries);
            }
            return;
        }
        List<SceneNode> children = new ArrayList<>();
        List<int[]> measured = new ArrayList<>();
        for (SceneNode child : node.children()) if (child.visible()) { children.add(child); measured.add(measure(child, innerWidth, node.axis() == SceneNode.Axis.ROW ? innerHeight : Integer.MAX_VALUE)); }
        boolean row = node.axis() == SceneNode.Axis.ROW;
        int available = (row ? innerWidth : innerHeight) - Math.max(0, children.size() - 1) * node.gap();
        int[] allocated = allocate(children, measured, Math.max(0, available), row);
        long content = Math.max(0L, children.size() - 1L) * node.gap();
        for (int size : allocated) content += size;
        int maximum = node.scrollable() ? (int) Math.min(Integer.MAX_VALUE, Math.max(0L, content - (row ? innerWidth : innerHeight))) : 0;
        int desired = node.runtime() == null ? node.scrollOffset() : node.runtime().scrollOffset(node);
        int scroll = node.scrollable() ? Math.min(desired, maximum) : 0;
        entries.set(entryIndex, new SceneGeometryView.Entry(node, box, clip, lines, scroll, maximum));
        int cursor = -scroll;
        for (int i = 0; i < children.size(); i++) {
            SceneNode child = children.get(i);
            int allocatedHeight = row ? innerHeight : allocated[i];
            int[] heightAware = measure(child, innerWidth, allocatedHeight);
            int childWidth = row ? allocated[i] : cross(child.width(), heightAware[0], innerWidth);
            int[] actual = measure(child, childWidth, allocatedHeight);
            int childHeight = row ? cross(child.height(), actual[1], innerHeight) : allocated[i];
            int x = box.x + node.padding() + (row ? cursor : 0);
            int y = box.y + node.padding() + (row ? 0 : cursor);
            arrange(child, new LayoutBox(x, y, childWidth, childHeight), clip, entries);
            cursor += allocated[i] + node.gap();
        }
    }
    /** 有界水位分配：固定占用保留，触及边界的填充退出，余数沿子序分配。 */
    private int[] allocate(List<SceneNode> nodes, List<int[]> measured, int available, boolean row) {
        int[] result = new int[nodes.size()];
        List<Integer> active = new ArrayList<>();
        int remaining = available;
        for (int i = 0; i < nodes.size(); i++) {
            Size size = row ? nodes.get(i).width() : nodes.get(i).height();
            if (size.mode == Size.Mode.FILL) active.add(i);
            else { result[i] = size.constrain(size.mode == Size.Mode.FIXED ? size.value : measured.get(i)[row ? 0 : 1]); remaining -= result[i]; }
        }
        if (!active.isEmpty()) {
            long minimum = 0, maximum = 0;
            double upper = 0;
            for (int i : active) {
                Size size = row ? nodes.get(i).width() : nodes.get(i).height();
                minimum += size.min; maximum += size.max; upper = Math.max(upper, (double) size.max / size.value);
            }
            long target = Math.max(minimum, Math.min(maximum, Math.max(0, remaining)));
            double lower = 0;
            // 求所有上下界的共同水位；临时触及 min 的项仍可接回 max 释放的空间。
            for (int step = 0; step < 80; step++) {
                double water = (lower + upper) / 2, total = 0;
                for (int i : active) {
                    Size size = row ? nodes.get(i).width() : nodes.get(i).height();
                    total += Math.max(size.min, Math.min(size.max, water * size.value));
                }
                if (total < target) lower = water; else upper = water;
            }
            long fixed = 0, freeWeights = 0;
            List<Integer> free = new ArrayList<>();
            for (int i : active) {
                Size size = row ? nodes.get(i).width() : nodes.get(i).height();
                double share = upper * size.value;
                if (share <= size.min) { result[i] = size.min; fixed += result[i]; }
                else if (share >= size.max) { result[i] = size.max; fixed += result[i]; }
                else { free.add(i); freeWeights += size.value; }
            }
            // 水位只用于识别约束区间；自由项用整数有理分配，避免浮点整数边界错分余数。
            for (int i : free) {
                Size size = row ? nodes.get(i).width() : nodes.get(i).height();
                result[i] = size.constrain((int) ((target - fixed) * size.value / freeWeights));
            }
            long distributed = 0;
            for (int i : active) distributed += result[i];
            long rest = target - distributed;
            for (int i : active) {
                Size size = row ? nodes.get(i).width() : nodes.get(i).height();
                if (rest > 0 && result[i] < size.max) { result[i]++; rest--; }
            }
        }
        return result;
    }
}
