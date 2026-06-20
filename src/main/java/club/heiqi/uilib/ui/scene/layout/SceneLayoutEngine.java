package club.heiqi.uilib.ui.scene.layout;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/**
 * 增量布局引擎 —— 实施 I7"干净子树三阶段跳过"的布局核心。
 *
 * <h3>核心思想：双标记决定跳过/下沉/重算</h3>
 * <p>每个节点持有两个布局脏标记：
 * {@code selfLayoutDirty}（自身输入变化）和 {@code descendantLayoutDirty}（后代存在脏节点）。
 * DFS 遍历时，引擎读取这两个布尔标记决定行为：</p>
 * <ul>
 *   <li><b>双 false → 整棵跳过</b>：节点自身和所有后代均干净，直接 return，
 *       复用 {@code cachedLayout}。这是 I7 的核心价值：干净子树零开销。</li>
 *   <li><b>selfLayoutDirty==true → 重算本节点</b>：自身的 text/子节点集合等输入变了，
 *       执行后序遍历：先递归子节点，再基于子节点布局结果重算本节点。</li>
 *   <li><b>selfLayoutDirty==false && descendantLayoutDirty==true → 下沉但不重算</b>：
 *       本节点自身输入未变，cachedLayout 可复用。但后代有脏节点，需递归子节点
 *       （子节点各自再过双标记判定，干净的在入口被跳过）。</li>
 * </ul>
 *
 * <h3>真实字体度量（解除偏离 1）</h3>
 * <p>叶节点宽度由 {@link SceneTextMeasurer#measureWidth} 提供 shrink-to-fit 语义，
 * 行高由 {@link SceneTextMeasurer#lineHeight} 驱动。叶节点主轴宽不再被 cross-align 改写，
 * 使 ROW+CENTER 主轴偏移恢复非 0（叶节点内在宽 &lt; 可用宽时居中可见）。</p>
 *
 * <h3>I10 接缝纯净</h3>
 * <p>引擎只认 scene 端口 {@link SceneTextMeasurer}，绝不 import 任何平台类、
 * 渲染上下文或 {@code ui.text.*} 度量实现。真实度量由装配层 adapter 委托完成（I6）。</p>
 *
 * <h3>epoch 失效链（I7 铁律：只向上冒泡）</h3>
 * <p>字体运行时 epoch 变化时，遍历上一帧测量过的文本叶节点逐个 {@code markSelfLayout()}
 * （只向上冒泡，O(文本节点数)，<b>绝不向下递归标脏</b>），随后清空集合本帧重填。</p>
 */
public class SceneLayoutEngine {

    /** 构造注入：文本度量服务（scene 核心只认窄端口，不 import ui.text.*） */
    private final SceneTextMeasurer measurer;

    /**
     * 上一次完成测量时的字体运行时 epoch。
     * <p>初值 -1：保证首帧必判定一次 epoch 失效流程（虽首帧无 measuredTextNodes，逻辑安全）。</p>
     */
    private int lastMeasureEpoch = -1;

    /**
     * 本帧测量过文本的叶节点集合（IdentityHashMap-backed，按引用相等去重）。
     * <p>epoch 变化时遍历此集合逐个向上冒泡标脏后清空。每帧测量文本叶时重填。</p>
     */
    private final Set<SceneNode> measuredTextNodes = Collections.newSetFromMap(new IdentityHashMap<>());

    // ==================== 构造器 ====================

    /**
     * 使用指定文本度量服务创建布局引擎。
     *
     * @param measurer 文本度量服务（非 null）
     */
    public SceneLayoutEngine(SceneTextMeasurer measurer) {
        if (measurer == null) {
            throw new IllegalArgumentException("SceneTextMeasurer 不可为 null");
        }
        this.measurer = measurer;
    }

    // ==================== 测试探针 ====================

    /**
     * 本次 {@link #layout} 调用中的重算次数。
     * 每次 {@code performLayout} 被调用时递增。
     * 仅供测试断言，生产代码不应依赖此字段。
     */
    private int relayoutCount = 0;

    /**
     * 本次 {@link #layout} 调用中被重算的节点集合。
     * 仅供测试断言 I7 跳过行为。
     */
    private final Set<SceneNode> relayoutedNodes = new HashSet<>();

    /**
     * 上一次 layout 调用传入的根约束。
     *
     * <p>用于检测约束变化：约束变化时驱动 root 标脏，保证约束增高/降低
     * 能被布局引擎感知。约束不变时不做任何标脏，保持 I7 双 false 跳过。</p>
     */
    private Constraints lastRootConstraints;

    /**
     * 对以 root 为根的子树执行增量布局。
     *
     * <p>调用前应确保 root 的脏标记正确反映变更（各 SceneNode.setter 已自动维护）。
     * 调用后所有被访问节点的 {@code selfLayoutDirty} 和 {@code descendantLayoutDirty}
     * 均被清除，cachedLayout 更新为最新值。</p>
     *
     * @param root            场景树根节点
     * @param rootConstraints 根节点的布局约束（如屏幕可用宽度）
     */
    public void layout(SceneNode root, Constraints rootConstraints) {
        relayoutCount = 0;
        relayoutedNodes.clear();

        // epoch 失效链：字体运行时变化时，把上一帧测量过的文本叶逐个向上冒泡标脏。
        // 严禁向下递归（I7），detached 节点冒泡到 null parent 无害。
        int epoch = measurer.epoch();
        if (epoch != lastMeasureEpoch) {
            for (SceneNode textNode : measuredTextNodes) {
                textNode.markSelfLayout();
            }
            measuredTextNodes.clear();
            lastMeasureEpoch = epoch;
        }

        // 约束变化感知：约束变化时只标 root 自己 selfLayoutDirty，
        // 绝不触碰任何后代节点（后代脏标记由各自 setter 自行维护）
        if (!Objects.equals(rootConstraints, lastRootConstraints)) {
            root.markSelfLayout();
        }
        lastRootConstraints = rootConstraints;

        layoutInternal(root, rootConstraints);
    }

    // ==================== 内部递归 ====================

    /**
     * DFS 递归布局，实施双标记判定（I7 灵魂）+ 子节点几何变化上传。
     *
     * <h3>返回值</h3>
     * <p>返回 {@code true} 表示以本节点为根的子树几何发生了变化（本节点或后代
     * 的 LayoutBox 被更新）。父节点收集所有子节点的返回值：若任一子节点返回
     * {@code true}，即使父节点自身 {@code selfLayoutDirty==false}，也需要
     * 走 {@link #performLayout} 重新定位子节点 y 坐标 + 重算自身高度。
     * 这使几何变化沿脏链按需上传（O(脏链深度)），但绝不退化为全量。</p>
     *
     * <p>后序遍历：先递归子节点（确保子节点布局已算好），再按需要重算本节点。</p>
     *
     * <p>跳过条件：双标记 false <b>且 cachedLayout 非空</b>。仅双标记 false
     * 但缓存为空（如首次 layout 从未被标脏的干净叶子），仍需进入流程确保
     * 有 LayoutBox 产出。</p>
     *
     * @param node        当前节点
     * @param constraints 父容器传给当前节点的布局约束
     * @return 本子树几何是否发生了变化
     */
    private boolean layoutInternal(SceneNode node, Constraints constraints) {
        // ==== I7 核心判定：缓存有效 + 双 false → 整棵跳过，几何未变 ====
        if (node.getCachedLayout() != null
                && !node.__isSelfLayoutDirty()
                && !node.__isDescendantLayoutDirty()) {
            // 本节点及整棵后代均干净且缓存有效，直接 return false
            return false;
        }

        // ==== 后序遍历：先递归子节点，收集几何变化信号 ====
        // 按 flexDirection + padding 扣减内容宽：COLUMN/ROW 子节点都拿父内容宽作可用宽约束。
        // ROW 不做 grow 比例分配（YAGNI）。
        int innerWidth = constraints.getAvailableWidth() - node.getPaddingLeft() - node.getPaddingRight();
        if (innerWidth < 0) {
            innerWidth = 0;
        }
        Constraints childConstraints = new Constraints(innerWidth);
        List<SceneNode> children = node.__getChildren();
        boolean anyChildGeometryChanged = false;
        for (SceneNode child : children) {
            if (layoutInternal(child, childConstraints)) {
                anyChildGeometryChanged = true;
            }
        }

        // ==== 判定是否需要重算本节点 ====
        // 需要重算条件：自身脏 / 无缓存 / 子节点几何变化导致需重新定位
        boolean selfDirty = node.__isSelfLayoutDirty() || node.getCachedLayout() == null;
        boolean needRelayout = selfDirty || anyChildGeometryChanged;

        if (needRelayout) {
            // 仅在"节点自身内容变化"时计入重算统计（I7 语义）
            // 因兄弟几何变化导致的"位置顺移"不算入重算计数
            if (selfDirty) {
                relayoutCount++;
                relayoutedNodes.add(node);
            }
            performLayout(node, constraints);
        }

        // ==== 清除本节点布局脏标记 ====
        // 使用 SceneNode.clearLayoutDirty() 只清 layout 两个标记，
        // 不误清 paint/composite 标记
        node.clearLayoutDirty();
        return needRelayout;
    }

    /**
     * 执行单节点布局计算（flex 主轴/交叉轴定位）。
     *
     * <p>按 {@code flexDirection} 划分主轴/交叉轴，应用 padding / gap / 主轴对齐 /
     * 交叉轴对齐，为子节点设置局部坐标，并计算本节点自身尺寸。</p>
     *
     * <h3>I7 铁律</h3>
     * <p>仍走 {@code newBox.equals(childBox)} 几何闸门 + {@code markGeometryDirty}：
     * 仅在 LayoutBox 值确实变化时才替换缓存并标记 geometry 脏。<b>绝不调用任何子节点的
     * {@code markSelfLayout}，绝不向下递归触碰后代。</b>padding/gap 等容器属性变化
     * 通过本节点 selfLayoutDirty 触发重定位，干净子节点的 LayoutBox 若值不变则引用复用。</p>
     *
     * @param node        要计算布局的节点
     * @param constraints 当前节点的布局约束
     */
    private void performLayout(SceneNode node, Constraints constraints) {
        // 1. 读取轴向、padding、gap 与内容宽
        int outerWidth = constraints.getAvailableWidth();
        boolean row = node.getFlexDirection() == FlexDirection.ROW;
        int padTop = node.getPaddingTop();
        int padRight = node.getPaddingRight();
        int padBottom = node.getPaddingBottom();
        int padLeft = node.getPaddingLeft();
        int gap = node.getGap();
        int innerWidth = Math.max(0, outerWidth - padLeft - padRight);

        List<SceneNode> children = node.__getChildren();

        // 2. 汇总主轴总尺寸与交叉轴最大尺寸
        int mainContentSize = 0;
        int crossMax = 0;
        int childCount = 0;
        for (SceneNode child : children) {
            LayoutBox cb = (LayoutBox) child.getCachedLayout();
            if (cb == null) {
                continue;
            }
            int childMain = row ? cb.getWidth() : cb.getHeight();
            int childCross = row ? cb.getHeight() : cb.getWidth();
            mainContentSize += childMain;
            if (childCross > crossMax) {
                crossMax = childCross;
            }
            childCount++;
        }
        int totalGap = childCount > 1 ? gap * (childCount - 1) : 0;
        int mainContentWithGap = mainContentSize + totalGap;

        // 3. 主轴可用空间与主轴起点偏移
        int mainAvail = row
                ? innerWidth
                : containerMainExtent(node, constraints, mainContentWithGap, padTop, padBottom);
        int mainStart;
        switch (node.getMainAxisAlign()) {
            case CENTER:
                mainStart = Math.max(0, (mainAvail - mainContentWithGap) / 2);
                break;
            case END:
                mainStart = Math.max(0, mainAvail - mainContentWithGap);
                break;
            case START:
            default:
                mainStart = 0;
                break;
        }

        // 4. 逐子定位（几何闸门 + markGeometryDirty，绝不向下递归标脏）
        int cursor = (row ? padLeft : padTop) + mainStart;
        for (SceneNode child : children) {
            LayoutBox cb = (LayoutBox) child.getCachedLayout();
            if (cb == null) {
                continue;
            }
            int childMain = row ? cb.getWidth() : cb.getHeight();
            int childCrossSize = row ? cb.getHeight() : cb.getWidth();
            int crossAvail = row ? crossMax : innerWidth;

            int crossPos;
            int finalCrossSize = childCrossSize;
            switch (node.getCrossAxisAlign()) {
                case START:
                    crossPos = 0;
                    break;
                case CENTER:
                    crossPos = Math.max(0, (crossAvail - childCrossSize) / 2);
                    break;
                case END:
                    crossPos = Math.max(0, crossAvail - childCrossSize);
                    break;
                case STRETCH:
                default:
                    crossPos = 0;
                    finalCrossSize = crossAvail;
                    break;
            }

            int nx;
            int ny;
            int nw;
            int nh;
            if (row) {
                nx = cursor;
                ny = padTop + crossPos;
                nw = childMain;
                nh = finalCrossSize;
            } else {
                nx = padLeft + crossPos;
                ny = cursor;
                nw = finalCrossSize;
                nh = childMain;
            }

            LayoutBox newBox = new LayoutBox(nx, ny, nw, nh);
            // 仅在位置或尺寸确实变化时才替换，保持缓存引用稳定（I7 几何闸门）
            if (!newBox.equals(cb)) {
                child.setCachedLayout(newBox);
                // 位置/尺寸变化 → geometry 级标记，让 paint 遍历感知 offset 需更新
                child.markGeometryDirty();
            }
            cursor += childMain + gap;
        }

        // 5. 本节点自身尺寸（值不变时不替换引用）
        int width = computeWidth(node, constraints);
        int height = computeHeight(node, constraints);
        LayoutBox newSelfBox = new LayoutBox(0, 0, width, height);
        LayoutBox oldSelfBox = (LayoutBox) node.getCachedLayout();
        if (!newSelfBox.equals(oldSelfBox)) {
            node.setCachedLayout(newSelfBox);
            // 自身位置/尺寸变化 → geometry 级标记
            node.markGeometryDirty();
        }
    }

    /**
     * 计算节点宽度（解除偏离 1 的核心）。
     *
     * <ul>
     *   <li>叶节点（无子节点）且有文本：shrink-to-fit，
     *       {@code min(outerWidth, measureWidth(text, fontSize) + padLeft + padRight)}，
     *       使叶节点主轴宽=内在宽（不被 cross-align STRETCH 改写为可用宽），
     *       ROW+CENTER 主轴偏移恢复非 0。</li>
     *   <li>叶节点无文本：宽=outerWidth（保留现状，preferredHeight 矩形仍铺满）。</li>
     *   <li>容器节点：宽=outerWidth（fill 语义不动，本轮不碰容器 shrink）。</li>
     * </ul>
     *
     * <p>注意：父 STRETCH（默认）在 cross 维度仍会把叶 cross 改写为 crossAvail，
     * 故默认 COLUMN+STRETCH 的 fill 宽度行为零回归（叶 cross=宽，被改写填满）；
     * ROW 下叶 main=宽=内在宽不被 cross-align 改写。</p>
     *
     * @param node        节点
     * @param constraints 当前节点的布局约束
     * @return 节点宽度（像素）
     */
    private int computeWidth(SceneNode node, Constraints constraints) {
        int outerWidth = constraints.getAvailableWidth();
        List<SceneNode> children = node.__getChildren();
        if (!children.isEmpty()) {
            // 容器节点：宽=可用宽（fill 语义）
            return outerWidth;
        }

        String text = node.getText();
        if (text == null || text.isEmpty()) {
            // 无文本叶节点：保留现状，宽=可用宽
            return outerWidth;
        }

        // 文本叶节点：shrink-to-fit。多行取各行最大测量宽。
        int padH = node.getPaddingLeft() + node.getPaddingRight();
        int intrinsicWidth = measureMaxLineWidth(text, node.getFontSize()) + padH;
        // 记录该叶为本帧测量过的文本节点，供 epoch 失效链向上冒泡使用
        measuredTextNodes.add(node);
        return Math.min(outerWidth, intrinsicWidth);
    }

    /**
     * 计算 COLUMN 容器主轴（高度）方向上的可用空间。
     *
     * <p>主轴可用空间 = 容器最终高度减上下 padding。为避免与 {@link #computeHeight}
     * 形成循环依赖，采用最简解：</p>
     * <ul>
     *   <li>shrink-to-fit（非 fill 或无高度约束）时 extent==contentExtent,
     *       mainAvail==mainContentWithGap，CENTER/END 的 offset 算出 0 → 退化为 START，
     *       零行为漂移。</li>
     *   <li>只有 fill 容器有高度盈余时 CENTER/END 才产生可见偏移。</li>
     * </ul>
     *
     * @param node               容器节点
     * @param constraints        容器布局约束
     * @param mainContentWithGap 子节点主轴总尺寸（含 gap）
     * @param padStart           主轴起点 padding（COLUMN 为上）
     * @param padEnd             主轴终点 padding（COLUMN 为下）
     * @return 主轴可用空间（像素）
     */
    private int containerMainExtent(SceneNode node, Constraints constraints,
                                    int mainContentWithGap, int padStart, int padEnd) {
        int contentExtent = mainContentWithGap + padStart + padEnd;
        int extent = contentExtent;
        if (node.isFillParentHeight() && constraints.hasHeightConstraint()) {
            extent = Math.max(contentExtent, constraints.getAvailableHeight());
        }
        return Math.max(0, extent - padStart - padEnd);
    }

    /**
     * 计算节点高度。
     *
     * <p>先按 shrink-to-fit 计算内容高度：
     * 容器节点（有子节点）= 子节点 cachedLayout 高度之和；
     * 叶节点 = 文本行数 × 行高（{@link SceneTextMeasurer#lineHeight}）；
     * 无文本叶节点 = 0。</p>
     *
     * <p>如果节点设置了 {@code fillParentHeight} 且约束有高度约束，
     * 则返回 max(内容高度, 约束高度) 实现"至少填满"语义。</p>
     *
     * @param node        节点
     * @param constraints 当前节点的布局约束
     * @return 节点高度（像素）
     */
    private int computeHeight(SceneNode node, Constraints constraints) {
        // 1. 计算内容高度（shrink-to-fit）
        int contentHeight = computeContentHeight(node);

        // 2. fill 分支：内容高度 vs 约束高度取 max
        if (node.isFillParentHeight() && constraints.hasHeightConstraint()) {
            return Math.max(contentHeight, constraints.getAvailableHeight());
        }
        return contentHeight;
    }

    /**
     * 按 shrink-to-fit 计算节点的内容高度（含上下 padding，不考虑 fill）。
     *
     * <p>按 {@code flexDirection} 区分容器主轴：</p>
     * <ul>
     *   <li>ROW 容器：高度 = 子节点最大高度（crossMax） + 上下 padding。</li>
     *   <li>COLUMN 容器：高度 = 子节点高度之和 + gap*(n-1) + 上下 padding。</li>
     *   <li>叶节点：max(文本高度, preferredHeight) + 上下 padding。</li>
     * </ul>
     *
     * @param node 节点
     * @return 内容高度（像素）
     */
    private int computeContentHeight(SceneNode node) {
        int padV = node.getPaddingTop() + node.getPaddingBottom();
        List<SceneNode> children = node.__getChildren();
        if (!children.isEmpty()) {
            boolean row = node.getFlexDirection() == FlexDirection.ROW;
            if (row) {
                // ROW 容器：高度 = 子节点最大高度 + 上下 padding
                int crossMax = 0;
                for (SceneNode child : children) {
                    LayoutBox childBox = (LayoutBox) child.getCachedLayout();
                    if (childBox != null && childBox.getHeight() > crossMax) {
                        crossMax = childBox.getHeight();
                    }
                }
                return crossMax + padV;
            }
            // COLUMN 容器：高度 = 子节点高度之和 + gap*(count-1) + 上下 padding
            int total = 0;
            int count = 0;
            for (SceneNode child : children) {
                LayoutBox childBox = (LayoutBox) child.getCachedLayout();
                if (childBox != null) {
                    total += childBox.getHeight();
                    count++;
                }
            }
            int totalGap = count > 1 ? node.getGap() * (count - 1) : 0;
            return total + totalGap + padV;
        }

        // 叶节点：文本行数 × 行高（真实度量）；无文本 → 高度为 0
        // preferredHeight 作为显式最小高度，与文本高度取 max
        String text = node.getText();
        int textHeight = 0;
        if (text != null && !text.isEmpty()) {
            int lines = countLines(text);
            textHeight = lines * measurer.lineHeight(node.getFontSize());
        }
        int preferred = node.getPreferredHeight();
        int contentLeaf = Math.max(textHeight, preferred);
        return contentLeaf + padV;
    }

    /**
     * 统计文本逻辑行数（按 {@code \n} 切分），空文本视作 1 行。
     *
     * @param text 文本内容
     * @return 行数（至少 1）
     */
    private int countLines(String text) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    /**
     * 测量多行文本中各行的最大 UI 像素宽度。
     *
     * @param text       文本内容（按 {@code \n} 切分多行）
     * @param fontSizePx 字号（UI 像素）
     * @return 各行测量宽的最大值
     */
    private int measureMaxLineWidth(String text, int fontSizePx) {
        int max = 0;
        int start = 0;
        int len = text.length();
        for (int i = 0; i <= len; i++) {
            if (i == len || text.charAt(i) == '\n') {
                String line = text.substring(start, i);
                int w = measurer.measureWidth(line, fontSizePx);
                if (w > max) {
                    max = w;
                }
                start = i + 1;
            }
        }
        return max;
    }

    // ==================== 测试探针 ====================

    /**
     * 返回最近一次 {@link #layout} 调用中的重算次数。
     * 仅供测试断言 I7 跳过行为。
     *
     * @return 重算次数
     */
    public int __getRelayoutCount() {
        return relayoutCount;
    }

    /**
     * 返回最近一次 {@link #layout} 调用中被重算的节点集合（不可变视图）。
     * 仅供测试断言 I7 跳过行为。
     *
     * @return 被重算的节点集合
     */
    public Set<SceneNode> __getRelayoutedNodes() {
        return Collections.unmodifiableSet(relayoutedNodes);
    }
}
