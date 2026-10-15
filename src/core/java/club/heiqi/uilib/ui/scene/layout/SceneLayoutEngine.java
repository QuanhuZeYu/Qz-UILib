package club.heiqi.uilib.ui.scene.layout;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 先测内容，再按声明分配；固定尺寸不因父空间不足被缩小。 */
public final class SceneLayoutEngine {
    private final SceneTextMeasurer text;
    private final java.util.function.ToLongFunction<SceneNode> nodeMeasureRevision;
    private final java.util.function.ToIntFunction<SceneNode> scrollOffsets;
    private final Map<SceneNode, Map<Long, int[]>> measuredCache = new IdentityHashMap<>();
    private final Map<SceneNode, Map<Integer, TextLayout>> textCache = new IdentityHashMap<>();
    private final Map<SceneNode, club.heiqi.uilib.ui.scene.text.TextStyle> textCacheStyles = new IdentityHashMap<>();
    private final Map<SceneNode, BatchTextMeasurer> batchText = new IdentityHashMap<>();
    private final Map<SceneNode, club.heiqi.uilib.ui.scene.text.TextStyle> presentedStyles = new IdentityHashMap<>();
    // 子测量入口仅在当批保持身份，文字缓存的代际和生命周期独立。
    private final Map<SceneNode, SceneChildMeasurer> batchChildren = new IdentityHashMap<>();
    private final Set<BatchTextMeasurer> activeText =
            Collections.newSetFromMap(new IdentityHashMap<BatchTextMeasurer, Boolean>());
    private boolean metricsKnown;
    private long metricsRevision, measureRevision;
    private int measuredWidth, measuredHeight;

    private BatchTextMeasurer textFor(SceneNode node) {
        BatchTextMeasurer batch =
                batchText.computeIfAbsent(node, key -> new BatchTextMeasurer(text));
        batch.prepareRevision(nodeMeasureRevision == null ? 0 : nodeMeasureRevision.applyAsLong(node));
        if (activeText.add(batch)) batch.beginRequests();
        return batch;
    }

    private static club.heiqi.uilib.ui.scene.control.SceneLayer.Policy layerPolicy(SceneNode node) {
        return node.control() == null ? null : node.control().layerPolicy(node);
    }
    private SceneChildMeasurer childrenFor(SceneNode parent) {
        return batchChildren.computeIfAbsent(parent, key -> new SceneChildMeasurer() {
            private final List<SceneNode> flow = flowFor(key);
            public List<SceneNode> flowChildren() { return flow; }
            public MeasuredSize measureChild(SceneNode child, Constraints constraints) {
                if (child.parent() != key) throw new IllegalArgumentException("只能测量直属子内容: parent=" + key.id() + " child=" + child.id());
                if (layerPolicy(child) != null) throw new IllegalArgumentException("普通流不能测量所属层: parent=" + key.id() + " child=" + child.id());
                int[] size = measure(child, constraints.availableWidth, constraints.availableHeight);
                return new MeasuredSize(size[0], size[1]);
            }
        });
    }
    private static List<SceneNode> flowFor(SceneNode parent) {
        List<SceneNode> flow = new ArrayList<>();
        for (SceneNode child : parent.children()) if (child.visible() && layerPolicy(child) == null) flow.add(child);
        return java.util.Collections.unmodifiableList(flow);
    }

    public SceneLayoutEngine(SceneTextMeasurer text) {
        // 保留原入口的当前滚动语义；正式管线在构造边界明确提供唯一运行时的事实。
        this(text, node -> node.runtime() == null ? node.scrollOffset() : node.runtime().scrollOffset(node));
    }

    /** 布局只读取本次滚动位置，位置权威及最终范围发布由调用方协调。 */
    public SceneLayoutEngine(SceneTextMeasurer text, java.util.function.ToIntFunction<SceneNode> scrollOffsets) {
        this(text, scrollOffsets, null);
    }

    public SceneLayoutEngine(SceneTextMeasurer text,
            java.util.function.ToIntFunction<SceneNode> scrollOffsets,
            java.util.function.ToLongFunction<SceneNode> nodeMeasureRevision) {
        this.text = text;
        this.scrollOffsets = java.util.Objects.requireNonNull(scrollOffsets, "scrollOffsets");
        this.nodeMeasureRevision = nodeMeasureRevision;
    }

    /** 帧管线结束后释放跨批文字与本批事实，不持续持有已退出页面。 */
    public void clearCaches() {
        batchText.clear();
        presentedStyles.clear();
        measuredCache.clear();
        textCache.clear();
        textCacheStyles.clear();
        batchChildren.clear();
        activeText.clear();
        metricsKnown = false;
    }

    // 跨批寿命按完整挂载树：READY图像的隐藏备用仍会在主布局后独立捕获，不能按本批测量集合修剪。
    private void retainMounted(List<SceneNode> roots) {
        Set<SceneNode> mounted = Collections.newSetFromMap(new IdentityHashMap<SceneNode, Boolean>());
        for (SceneNode root : roots) collectMounted(root, mounted);
        // 主几何省略 READY 图像的备用子树；生命周期必须按完整挂载树判定。
        batchText.keySet().retainAll(mounted);
        presentedStyles.keySet().retainAll(mounted);
        // 隐藏节点可能本批不访问；代际结束也要及时丢弃其旧内容及inline载荷。
        if (nodeMeasureRevision != null)
            for (Map.Entry<SceneNode, BatchTextMeasurer> entry : batchText.entrySet())
                entry.getValue().prepareRevision(nodeMeasureRevision.applyAsLong(entry.getKey()));
    }

    private void collectMounted(SceneNode node, Set<SceneNode> mounted) {
        if (!mounted.add(node)) return;
        for (SceneNode child : node.children()) collectMounted(child, mounted);
    }

    public SceneGeometry layout(List<SceneNode> roots, int width, int height) {
        // 无度量代际的旧入口不能假定字体资源稳定。
        batchText.clear();
        metricsKnown = false;
        return arrangeRoots(roots, width, height);
    }

    /** 字体、整体测量或视口换代结束旧请求；局部代际在访问节点时检查。 */
    public SceneGeometry layout(
            List<SceneNode> roots, int width, int height, long nextMetrics, long nextMeasure) {
        retainMounted(roots);
        if (!metricsKnown || metricsRevision != nextMetrics) batchText.clear();
        else if (measureRevision != nextMeasure
                || measuredWidth != width
                || measuredHeight != height) {
            // 先清旧请求再重新测量，不跨声明代际积累动态文字或任意宽度。
            for (BatchTextMeasurer batch : batchText.values()) batch.resetRequests();
            batchText.clear();
        }
        metricsKnown = true;
        metricsRevision = nextMetrics;
        measureRevision = nextMeasure;
        measuredWidth = width;
        measuredHeight = height;
        return arrangeRoots(roots, width, height);
    }

    private SceneGeometry arrangeRoots(List<SceneNode> roots, int width, int height) {
        activeText.clear();
        try {
            measuredCache.clear();
            textCache.clear();
            textCacheStyles.clear();
            List<SceneGeometryView.Entry> entries = new ArrayList<>();
            for (SceneNode root : roots) {
                if (layerPolicy(root) != null) throw new IllegalArgumentException("所属层不能作为无来源根: node=" + root.id());
                int[] desired = measure(root, width, height);
                int w = cross(root.width(), desired[0], width),
                        h = cross(root.height(), desired[1], height);
                arrange(root, new LayoutBox(0, 0, w, h), null, entries);
            }
            LayoutBox viewport = new LayoutBox(0, 0, width, height);
            Map<SceneNode, SceneGeometryView.Entry> index = new IdentityHashMap<>();
            for (SceneGeometryView.Entry entry : entries) index.put(entry.node, entry);
            Map<SceneNode, Boolean> sources = new IdentityHashMap<>();
            for (SceneNode root : roots) projectLayers(root, viewport, entries, index, sources);
            SceneGeometry geometry = ScenePresentationOrder.publish(roots, entries, viewport, sources);
            presentedStyles.clear();
            for (SceneGeometryView.Entry entry : geometry.ordered()) presentedStyles.put(entry.node, entry.node.style());
            return geometry;
        } finally {
            endTextRequests();
        }
    }

    /** 纯绘制投影只能读取定位阶段已消费的子度量，不得偷偷触发新测量。 */
    private SceneChildMeasurer positionedChildrenFor(SceneNode parent) {
        SceneChildMeasurer measured = childrenFor(parent);
        return new SceneChildMeasurer() {
            public List<SceneNode> flowChildren() { return measured.flowChildren(); }
            public MeasuredSize measureChild(SceneNode child, Constraints constraints) {
                if (child.parent() != parent || layerPolicy(child) != null)
                    throw new IllegalArgumentException("纯绘制只能读取直属普通流子内容度量");
                Map<Long, int[]> cached = measuredCache.get(child);
                long key = ((long) constraints.availableWidth << 32) | (constraints.availableHeight & 0xffffffffL);
                int[] size = cached == null ? null : cached.get(key);
                if (size == null) throw new IllegalStateException("纯绘制投影请求了未定位的子度量: node=" + child.id());
                return new MeasuredSize(size[0], size[1]);
            }
        };
    }

    /** 纯前景色变化沿正式内容投影刷新绘制样式，不调用控件 measure/arrange。 */
    public SceneGeometry refreshTextStyles(SceneGeometry geometry) {
        Map<SceneNode, TextLayout> updated = new IdentityHashMap<>();
        try {
            for (SceneGeometryView.Entry entry : geometry.sourceOrdered()) {
                SceneNode node = entry.node;
                club.heiqi.uilib.ui.scene.text.TextStyle previous = presentedStyles.get(node);
                if (node.style().equals(previous)) continue;
                if (previous == null || !previous.withColor(node.style().color).equals(node.style()))
                    throw new IllegalStateException("文字几何样式变化必须先重新布局: node=" + node.id());
                textCache.remove(node);
                int innerWidth = Math.max(0, entry.box.width - node.padding() * 2);
                int innerHeight = Math.max(0, entry.box.height - node.padding() * 2);
                TextLayout lines = node.control() == null
                        ? textFor(node).layout(node.text(), node.style(), innerWidth, node.textPolicy())
                        : node.control().layoutText(node, textFor(node), innerWidth, positionedChildrenFor(node));
                if (node.control() != null)
                    lines = node.control().presentText(node, lines, innerWidth, innerHeight,
                            textFor(node), positionedChildrenFor(node));
                updated.put(node, lines);
            }
            SceneGeometry refreshed = geometry.withText(updated);
            for (SceneNode node : updated.keySet()) presentedStyles.put(node, node.style());
            return refreshed;
        } finally {
            endTextRequests();
        }
    }

    /** 来源盒已含普通滚动；所属层只锚定一次，clip 从页面视口开始。 */
    private void projectLayers(SceneNode node, LayoutBox viewport, List<SceneGeometryView.Entry> entries,
            Map<SceneNode, SceneGeometryView.Entry> index, Map<SceneNode, Boolean> sources) {
        for (SceneNode child : node.children()) {
            club.heiqi.uilib.ui.scene.control.SceneLayer.Policy policy = layerPolicy(child);
            if (policy != null) {
                SceneGeometryView.Entry source = index.get(node);
                boolean available = source != null && sourceVisible(node, source, viewport);
                sources.put(child, available);
                if (!available || !child.visible()) continue;
                int[] desired = measure(child, viewport.width, viewport.height);
                int w = cross(child.width(), desired[0], viewport.width), h = cross(child.height(), desired[1], viewport.height);
                int x = anchored(source.box.x, w, viewport.x, viewport.width);
                int y = anchored((long) source.box.y + source.box.height + policy.gap, h, viewport.y, viewport.height);
                int begin = entries.size();
                arrange(child, new LayoutBox(x, y, w, h), viewport, entries);
                for (int i = begin; i < entries.size(); i++) index.put(entries.get(i).node, entries.get(i));
            }
            if (index.containsKey(child)) projectLayers(child, viewport, entries, index, sources);
        }
    }
    private static boolean sourceVisible(SceneNode node, SceneGeometryView.Entry entry, LayoutBox viewport) {
        for (SceneNode cursor = node; cursor != null; cursor = cursor.parent())
            if (!cursor.visible() || !cursor.enabled() || cursor.destroyed()) return false;
        LayoutBox visible = entry.box.intersect(viewport);
        if (entry.clip != null) visible = visible.intersect(entry.clip);
        return visible.width > 0 && visible.height > 0;
    }
    private static int anchored(long preferred, int size, int start, int available) {
        return size > available ? start : (int) Math.max(start, Math.min((long) start + available - size, preferred));
    }
    /** 仅内容投影/捕获的边界拒绝层声明，保留边界与声明位置。 */
    public static void rejectContentLayers(SceneNode boundary, SceneNode content, String kind) {
        if (layerPolicy(content) != null)
            throw new IllegalArgumentException("内容分支不支持所属层: boundary=" + boundary.id() + " kind=" + kind + " layer=" + content.id());
        for (SceneNode child : content.children()) rejectContentLayers(boundary, child, kind);
    }

    private void endTextRequests() {
        for (BatchTextMeasurer batch : activeText) batch.endRequests();
        activeText.clear();
        batchChildren.clear();
    }

    private int cross(Size size, int desired, int available) {
        return SceneFlowLayout.cross(size, desired, available);
    }

    private TextLayout textLayout(SceneNode node, int width) {
        // 备用子树不在主几何内，捕获时也必须消费当前绘制样式。
        if (!node.style().equals(textCacheStyles.get(node))) {
            textCache.remove(node);
            textCacheStyles.put(node, node.style());
        }
        return textCache
                .computeIfAbsent(node, key -> new HashMap<>())
                .computeIfAbsent(
                        width,
                        key ->
                                node.control() == null
                                        ? textFor(node)
                                                .layout(
                                                        node.text(),
                                                        node.style(),
                                                        width,
                                                        node.textPolicy())
                                        : node.control()
                                                .layoutText(
                                                        node, textFor(node), width, childrenFor(node)));
    }

    private int[] measure(SceneNode node, int availableWidth, int availableHeight) {
        long constraintKey = ((long) availableWidth << 32) | (availableHeight & 0xffffffffL);
        return measuredCache
                .computeIfAbsent(node, key -> new HashMap<>())
                .computeIfAbsent(
                        constraintKey,
                        key -> measureUncached(node, availableWidth, availableHeight));
    }

    private int[] measureUncached(SceneNode node, int availableWidth, int availableHeight) {
        if (!node.visible()) return new int[] {0, 0};
        int measuredWidth =
                node.width().mode == Size.Mode.FIXED
                        ? node.width().constrain(node.width().value)
                        : node.width().constrain(availableWidth);
        int contentWidth = Math.max(0, measuredWidth - node.padding() * 2);
        // 自然高度尚未分配，不把父空间冒充自身高度；固定/fill 则携带真实有限约束。
        int measuredHeight =
                node.height().mode == Size.Mode.FIXED
                        ? node.height().constrain(node.height().value)
                        : node.height().mode == Size.Mode.FILL
                                        && availableHeight != Integer.MAX_VALUE
                                ? node.height().constrain(availableHeight)
                                : Integer.MAX_VALUE;
        int contentHeight =
                measuredHeight == Integer.MAX_VALUE
                        ? Integer.MAX_VALUE
                        : Math.max(0, measuredHeight - node.padding() * 2);
        if (node.control() != null) {
            MeasuredSize custom = requireControlResult(node, "measure",
                    node.control().measure(node, new Constraints(contentWidth, contentHeight),
                            textFor(node), childrenFor(node)));
            return new int[] {
                node.width()
                        .constrain(
                                node.width().mode == Size.Mode.FIXED
                                        ? node.width().value
                                        : custom.width + node.padding() * 2),
                node.height()
                        .constrain(
                                node.height().mode == Size.Mode.FIXED
                                        ? node.height().value
                                        : custom.height + node.padding() * 2)
            };
        }
        TextLayout lines = textLayout(node, contentWidth);
        MeasuredSize flow = SceneFlowLayout.measure(node, new Constraints(contentWidth, contentHeight), childrenFor(node));
        int w = Math.max(lines.width, flow.width), h = Math.max(lines.height, flow.height);
        return new int[] {
            node.width()
                    .constrain(
                            node.width().mode == Size.Mode.FIXED
                                    ? node.width().value
                                    : w + node.padding() * 2),
            node.height()
                    .constrain(
                            node.height().mode == Size.Mode.FIXED
                                    ? node.height().value
                                    : h + node.padding() * 2)
        };
    }

    /** 在既定父区域捕获已挂载占位子树，沿生产排列，不发布输入几何或改live节点。 仅根的框架hidden被忽略；后代显隐/滚动/裁剪仍走同一规则。静态帧复用呈现计划。 */
    public SceneGeometry captureSubtree(
            SceneNode root, LayoutBox allocated, LayoutBox ancestorClip, LayoutBox viewport) {
        rejectContentLayers(root, root, "paint capture");
        // 晚期备用捕获仅结算实际访问的节点，不能清掉其它主树节点的测量请求。
        activeText.clear();
        try {
            List<SceneGeometryView.Entry> entries = new ArrayList<>();
            arrange(root, allocated, ancestorClip, entries, true);
            return new SceneGeometry(entries, viewport);
        } finally {
            endTextRequests();
        }
    }

    private void arrange(
            SceneNode node,
            LayoutBox box,
            LayoutBox ancestorClip,
            List<SceneGeometryView.Entry> entries) {
        arrange(node, box, ancestorClip, entries, false);
    }

    private void arrange(
            SceneNode node,
            LayoutBox box,
            LayoutBox ancestorClip,
            List<SceneGeometryView.Entry> entries,
            boolean captureRoot) {
        if (!captureRoot && !node.visible()) return;
        int innerWidth = Math.max(0, box.width - node.padding() * 2);
        int innerHeight = Math.max(0, box.height - node.padding() * 2);
        LayoutBox ownClip = node.clip() || node.scrollable() ? box : null;
        LayoutBox clip =
                ancestorClip == null
                        ? ownClip
                        : ownClip == null ? ancestorClip : ancestorClip.intersect(ownClip);
        TextLayout lines = textLayout(node, innerWidth);
        if (node.control() != null)
            lines =
                    node.control()
                            .presentText(
                                    node,
                                    lines,
                                    innerWidth,
                                    innerHeight,
                                    textFor(node),
                                    childrenFor(node));
        int entryIndex = entries.size();
        entries.add(new SceneGeometryView.Entry(node, box, clip, lines));
        java.util.Set<SceneNode> placed = java.util.Collections.newSetFromMap(new IdentityHashMap<SceneNode, Boolean>());
        boolean builtin = node.control() == null;
        boolean flow = builtin || layerPolicy(node) != null;
        ChildPlacements arrangement = builtin ? SceneFlowLayout.arrange(node, box, childrenFor(node))
                : requireControlResult(node, "arrange", node.control().arrange(node, box, childrenFor(node)));
        List<ChildPlacements.Entry> placements = arrangement.entries();
        boolean row = node.axis() == SceneNode.Axis.ROW;
        long extent = 0;
        for (ChildPlacements.Entry child : placements) {
            if (child.child.parent() != node || !placed.add(child.child))
                throw new IllegalArgumentException("控件排列包含非逻辑子节点或重复节点: parent=" + node.id() + " child=" + child.child.id());
            if (layerPolicy(child.child) != null)
                throw new IllegalArgumentException("普通排列不能包含所属层: parent=" + node.id() + " control="
                        + (node.control() == null ? "flow" : node.control().getClass().getName()) + " child=" + child.child.id());
            if (!child.contentBox) {
                validatePlacement(node, child.child, "width", child.relativeBox.width, child.child.width());
                validatePlacement(node, child.child, "height", child.relativeBox.height, child.child.height());
            }
            extent = Math.max(extent, row ? (long) child.relativeBox.x + child.relativeBox.width
                    : (long) child.relativeBox.y + child.relativeBox.height);
        }
        int maximum = node.scrollable() ? (int) Math.min(Integer.MAX_VALUE,
                Math.max(0L, extent + (flow && !placements.isEmpty() ? node.padding() : 0) - (row ? box.width : box.height))) : 0;
        int scroll = node.scrollable() ? Math.min(scrollOffsets.applyAsInt(node), maximum) : 0;
        entries.set(entryIndex, new SceneGeometryView.Entry(node, box, clip, lines, scroll, maximum));
        for (ChildPlacements.Entry child : placements) {
            LayoutBox relative = child.relativeBox;
            arrange(child.child, new LayoutBox(box.x + relative.x - (row ? scroll : 0),
                    box.y + relative.y - (row ? 0 : scroll), relative.width, relative.height), clip, entries);
        }
    }

    /** 回调返回后其栈帧已退出；在消费边界定位非法结果，主动抛出的原异常不经包装。 */
    private static <T> T requireControlResult(SceneNode node, String rule, T result) {
        if (result == null)
            throw new IllegalArgumentException("控件布局规则返回null: node=" + node.id()
                    + " control=" + node.control().getClass().getName() + " rule=" + rule);
        return result;
    }

    private static void validatePlacement(
            SceneNode parent, SceneNode child, String axis, int actual, Size declared) {
        // natural 的内容求解由控件负责，这里只守住已声明的硬边界与固定尺寸。
        if (actual < declared.min
                || actual > declared.max
                || declared.mode == Size.Mode.FIXED && actual != declared.constrain(declared.value))
            throw new IllegalArgumentException(
                    "控件排列尺寸非法: parent=" + parent.id()
                            + " control=" + (parent.control() == null ? "flow" : parent.control().getClass().getName())
                            + " child=" + child.id()
                            + " axis=" + axis + " actual=" + actual
                            + " declared=" + declared.mode + "(value=" + declared.value
                            + ", min=" + declared.min + ", max=" + declared.max + ")");
    }

}
