package club.heiqi.uilib.ui.scene.layout;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Assume;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 布局不变量断言库 —— 把 {@code NORTH_STAR.md} 的关键不变量（I4 失效级别矩阵、
 * I7 重算收敛、I12 / §4.5 坐标语义、求和不变量）翻译成可复用的数学断言方法。
 *
 * <p>本类专供 L2 纯数学测试层使用：构造场景树 → 调用
 * {@link SceneLayoutEngine#layout} → 用本库的方法断言布局结果与失效状态。
 * 所有方法均为 {@code static}，类不可实例化。</p>
 *
 * <h3>快速选择指南（场景 → helper）</h3>
 * <pre>
 * | 你要断言什么 | 用哪个 helper |
 * |---|---|
 * | 某 setter 只触发某一级失效（I4 矩阵） | assertOnlyInvalidation(node, level) |
 * | 遍历后节点完全清脏 | assertClean(node) |
 * | 局部坐标全 4 维（x/y/w/h） | assertLocalBox(node, x, y, w, h) |
 * | 绝对坐标累加（I12/§4.5，只 x/y） | assertAbsoluteBox(node, rootX, rootY, x, y) |
 * | COLUMN shrink-to-fit 高度求和 | assertColumnHeightSum(container) |
 * | ROW SHRINK 宽度求和 | assertRowWidthSum(container, availW) |
 * | 增量失效恰好重算某集合（I7） | assertRelayoutSet(result, nodes...) |
 * | 干净帧零重算（I7） | assertNoRelayout(result) |
 * </pre>
 * <p>不在此表的几何/失效断言，优先扩充本库而非在测试里裸写 {@code assertEquals}；
 * 纯缓存计数/并行池等非几何量除外。</p>
 *
 * <h3>失效级别探针可用性结论</h3>
 * <p>{@link SceneNode} 为四类失效（layout / paint / geometry / composite）都暴露了
 * dirty 探针，故 {@link #assertOnlyInvalidation} 能对四个级别做直接断言，无需间接推断：</p>
 * <ul>
 *   <li>LAYOUT → {@link SceneNode#__isSelfLayoutDirty()}</li>
 *   <li>PAINT → {@link SceneNode#__isSelfPaintDirty()}</li>
 *   <li>GEOMETRY → {@link SceneNode#__isSelfGeometryDirty()}</li>
 *   <li>COMPOSITE → {@link SceneNode#__isCompositeDirty()}</li>
 * </ul>
 *
 * <h3>同包可见性</h3>
 * <p>本类置于 {@code club.heiqi.uilib.ui.scene.layout} 包（测试源集），既能访问
 * 包级可见的 {@link LayoutBox} / {@link Constraints} / {@link SizingCalculator}，
 * 也能访问 {@link SceneGeometry} 的公开只读几何工具。</p>
 */
public final class LayoutAssertions {

    /** 工具类不可实例化。 */
    private LayoutAssertions() {
    }

    /**
     * 失效级别枚举 —— 对齐 {@code NORTH_STAR} I4 的四级失效矩阵。
     *
     * <p>每个值映射到 {@link SceneNode} 的一个 self dirty 探针，
     * 用于 {@link #assertOnlyInvalidation} 精确判定本节点恰好处于哪一级失效。</p>
     */
    public enum InvalidationLevel {
        /** 布局失效：节点尺寸/位置或布局输入变化，需 layout 引擎重算 LayoutBox。 */
        LAYOUT,
        /** 绘制失效：节点像素输出变化（文本/颜色/背景），需 paint 引擎重建 fragment。 */
        PAINT,
        /** 几何失效：节点绝对位置/尺寸变化（layout 产出），paint 用新 offset 叠加但复用 fragment。 */
        GEOMETRY,
        /** 合成失效：opacity/transform 变化，合成层调整 group opacity / transform offset，不触碰布局与绘制。 */
        COMPOSITE
    }

    // ==================== I4 失效级别断言 ====================

    /**
     * 断言节点恰好处于指定级别的失效状态：该级别的 self dirty 位为 true，
     * 其余三个级别的 self dirty 位为 false。
     *
     * <p><b>语义边界</b>：本方法只校验 self 位，不校验 descendant 路标（路标描述的是
     * 「后代有脏，向上冒泡告知祖先」的传播信号，不属于本节点自身的失效级别）。
     * 例如 {@link SceneNode#markSelfLayout()} 会同时点亮祖先的
     * {@code descendantLayoutDirty}，但本节点自身的失效级别仍是「恰好 LAYOUT」。</p>
     *
     * <p>用于 I4 失效级别矩阵表测试：对每个 setter 验证它只触发声明级别的失效，
     * 不污染其它级别。</p>
     *
     * @param node  待断言节点（非 null）
     * @param level 期望的失效级别（非 null）
     */
    public static void assertOnlyInvalidation(SceneNode node, InvalidationLevel level) {
        if (node == null) {
            throw new IllegalArgumentException("node 不可为 null");
        }
        if (level == null) {
            throw new IllegalArgumentException("level 不可为 null");
        }
        boolean layout = node.__isSelfLayoutDirty();
        boolean paint = node.__isSelfPaintDirty();
        boolean geometry = node.__isSelfGeometryDirty();
        boolean composite = node.__isCompositeDirty();
        switch (level) {
            case LAYOUT:
                Assert.assertTrue("LAYOUT 级应 selfLayoutDirty==true", layout);
                Assert.assertFalse("LAYOUT 级不应 selfPaintDirty", paint);
                Assert.assertFalse("LAYOUT 级不应 selfGeometryDirty", geometry);
                Assert.assertFalse("LAYOUT 级不应 compositeDirty", composite);
                break;
            case PAINT:
                Assert.assertTrue("PAINT 级应 selfPaintDirty==true", paint);
                Assert.assertFalse("PAINT 级不应 selfLayoutDirty", layout);
                Assert.assertFalse("PAINT 级不应 selfGeometryDirty", geometry);
                Assert.assertFalse("PAINT 级不应 compositeDirty", composite);
                break;
            case GEOMETRY:
                Assert.assertTrue("GEOMETRY 级应 selfGeometryDirty==true", geometry);
                Assert.assertFalse("GEOMETRY 级不应 selfLayoutDirty", layout);
                Assert.assertFalse("GEOMETRY 级不应 selfPaintDirty", paint);
                Assert.assertFalse("GEOMETRY 级不应 compositeDirty", composite);
                break;
            case COMPOSITE:
                Assert.assertTrue("COMPOSITE 级应 compositeDirty==true", composite);
                Assert.assertFalse("COMPOSITE 级不应 selfLayoutDirty", layout);
                Assert.assertFalse("COMPOSITE 级不应 selfPaintDirty", paint);
                Assert.assertFalse("COMPOSITE 级不应 selfGeometryDirty", geometry);
                break;
            default:
                throw new IllegalArgumentException("未知失效级别: " + level);
        }
    }

    /**
     * 断言节点完全 clean：四个级别的 self dirty 位与 descendant 路标全部为 false。
     *
     * <p>用于 layout / paint / geometry / composite 遍历完成后的清脏验证。
     * 严格校验全部 8 个位，确保任何残留脏标记都会被捕获。</p>
     *
     * @param node 待断言节点（非 null）
     */
    public static void assertClean(SceneNode node) {
        if (node == null) {
            throw new IllegalArgumentException("node 不可为 null");
        }
        Assert.assertFalse("clean 节点 selfLayoutDirty 应 false", node.__isSelfLayoutDirty());
        Assert.assertFalse("clean 节点 descendantLayoutDirty 应 false", node.__isDescendantLayoutDirty());
        Assert.assertFalse("clean 节点 selfPaintDirty 应 false", node.__isSelfPaintDirty());
        Assert.assertFalse("clean 节点 descendantPaintDirty 应 false", node.__isDescendantPaintDirty());
        Assert.assertFalse("clean 节点 selfGeometryDirty 应 false", node.__isSelfGeometryDirty());
        Assert.assertFalse("clean 节点 descendantGeometryDirty 应 false", node.__isDescendantGeometryDirty());
        Assert.assertFalse("clean 节点 compositeDirty 应 false", node.__isCompositeDirty());
        Assert.assertFalse("clean 节点 descendantCompositeDirty 应 false", node.__isDescendantCompositeDirty());
    }

    // ==================== I12 / §4.5 坐标断言 ====================

    /**
     * 断言节点的局部 {@link LayoutBox}（相对父容器左上角的坐标）精确匹配期望值。
     *
     * <p>坐标语义见 {@link LayoutBox} 类注释：{@code x/y} 为相对父左上角的像素偏移，
     * {@code width/height} 为节点所占像素尺寸。失败信息逐字段输出，便于定位偏差。</p>
     *
     * <p><b>误用提示</b>：grow 主轴分配测试只关心主轴尺寸，交叉轴受 STRETCH/align 影响难预期，
     * 不要为凑本方法伪造交叉轴期望值；只断主轴时直接裸 {@code assertEquals}
     * （见 {@link GrowAllocationTableTest}）。</p>
     *
     * @param node   待断言节点（非 null，需已 layout）
     * @param x      期望相对父 X 坐标
     * @param y      期望相对父 Y 坐标
     * @param width  期望宽度
     * @param height 期望高度
     */
    public static void assertLocalBox(SceneNode node, int x, int y, int width, int height) {
        if (node == null) {
            throw new IllegalArgumentException("node 不可为 null");
        }
        Object cached = node.getCachedLayout();
        Assert.assertNotNull("节点 cachedLayout 为 null（未 layout 或被失效）", cached);
        Assert.assertTrue("cachedLayout 不是 LayoutBox: " + cached.getClass(),
                cached instanceof LayoutBox);
        LayoutBox box = (LayoutBox) cached;
        Assert.assertEquals("local x", x, box.getX());
        Assert.assertEquals("local y", y, box.getY());
        Assert.assertEquals("local width", width, box.getWidth());
        Assert.assertEquals("local height", height, box.getHeight());
    }

    /**
     * 断言节点局部高度（单维，用于只关心高度的测试）。
     *
     * <p>语义同 {@link #assertLocalBox} 的 height 维，但只断一维，避免为凑其它三维
     * 期望值而引入虚假断言。常用于 grow 分配 / COLUMN 求和等只关心主轴尺寸的场景。</p>
     *
     * @param node     待断言节点（非 null，需已 layout）
     * @param expected 期望高度
     */
    public static void assertHeight(SceneNode node, int expected) {
        if (node == null) {
            throw new IllegalArgumentException("node 不可为 null");
        }
        Object cached = node.getCachedLayout();
        Assert.assertNotNull("节点 cachedLayout 为 null（未 layout 或被失效）", cached);
        Assert.assertTrue("cachedLayout 不是 LayoutBox: " + cached.getClass(),
                cached instanceof LayoutBox);
        LayoutBox box = (LayoutBox) cached;
        Assert.assertEquals("local height", expected, box.getHeight());
    }

    /**
     * 断言节点局部宽度（单维，用于只关心宽度的测试）。
     *
     * <p>语义同 {@link #assertLocalBox} 的 width 维。常用于 ROW SHRINK 求和 /
     * grow 主轴分配等只关心主轴尺寸的场景。</p>
     *
     * @param node     待断言节点（非 null，需已 layout）
     * @param expected 期望宽度
     */
    public static void assertWidth(SceneNode node, int expected) {
        if (node == null) {
            throw new IllegalArgumentException("node 不可为 null");
        }
        Object cached = node.getCachedLayout();
        Assert.assertNotNull("节点 cachedLayout 为 null（未 layout 或被失效）", cached);
        Assert.assertTrue("cachedLayout 不是 LayoutBox: " + cached.getClass(),
                cached instanceof LayoutBox);
        LayoutBox box = (LayoutBox) cached;
        Assert.assertEquals("local width", expected, box.getWidth());
    }

    /**
     * 断言节点局部 x（单维，用于只关心相对父 X 偏移的测试）。
     *
     * <p>语义同 {@link #assertLocalBox} 的 x 维。常用于 justify/space-between 等只关心
     * 主轴排布位置的场景。</p>
     *
     * @param node     待断言节点（非 null，需已 layout）
     * @param expected 期望相对父 X 坐标
     */
    public static void assertX(SceneNode node, int expected) {
        if (node == null) {
            throw new IllegalArgumentException("node 不可为 null");
        }
        Object cached = node.getCachedLayout();
        Assert.assertNotNull("节点 cachedLayout 为 null（未 layout 或被失效）", cached);
        Assert.assertTrue("cachedLayout 不是 LayoutBox: " + cached.getClass(),
                cached instanceof LayoutBox);
        LayoutBox box = (LayoutBox) cached;
        Assert.assertEquals("local x", expected, box.getX());
    }

    /**
     * 断言节点局部 y（单维，用于只关心相对父 Y 偏移的测试）。
     *
     * <p>语义同 {@link #assertLocalBox} 的 y 维。常用于 align/space-around 等只关心
     * 交叉轴排布位置的场景。</p>
     *
     * @param node     待断言节点（非 null，需已 layout）
     * @param expected 期望相对父 Y 坐标
     */
    public static void assertY(SceneNode node, int expected) {
        if (node == null) {
            throw new IllegalArgumentException("node 不可为 null");
        }
        Object cached = node.getCachedLayout();
        Assert.assertNotNull("节点 cachedLayout 为 null（未 layout 或被失效）", cached);
        Assert.assertTrue("cachedLayout 不是 LayoutBox: " + cached.getClass(),
                cached instanceof LayoutBox);
        LayoutBox box = (LayoutBox) cached;
        Assert.assertEquals("local y", expected, box.getY());
    }

    /**
     * 断言节点的绝对坐标（沿 parent 链累加 LayoutBox 偏移，含 scrollable 祖先的
     * scrollOffsetY 注入）精确匹配期望值。
     *
     * <p>委托 {@link SceneGeometry#absoluteBox} 计算，{@code rootAbsX/rootAbsY} 传 0
     * 即得 host 局部坐标系下的绝对位置。仅校验 x/y（坐标累加是 §4.5 的核心不变量），
     * width/height 沿用节点自身 LayoutBox 尺寸，不做断言。</p>
     *
     * <p><b>误用提示</b>：只校验 x/y，不校验 w/h；要校验尺寸用 {@link #assertLocalBox}。</p>
     *
     * @param node      待断言节点（非 null，需已 layout）
     * @param rootAbsX  根坐标系 X 偏移（host 局部传 0）
     * @param rootAbsY  根坐标系 Y 偏移（host 局部传 0）
     * @param expectedX 期望绝对 X 坐标
     * @param expectedY 期望绝对 Y 坐标
     */
    public static void assertAbsoluteBox(SceneNode node, int rootAbsX, int rootAbsY,
                                         int expectedX, int expectedY) {
        if (node == null) {
            throw new IllegalArgumentException("node 不可为 null");
        }
        AnchorRect abs = SceneGeometry.absoluteBox(node, rootAbsX, rootAbsY);
        Assert.assertEquals("absolute x", expectedX, abs.getX());
        Assert.assertEquals("absolute y", expectedY, abs.getY());
    }

    // ==================== 求和不变量（带前置条件守卫） ====================

    /**
     * 断言 COLUMN 容器高度 == Σ(子节点高度 + 子节点 marginV) + gap*(n-1) + 上下 padding。
     *
     * <p>对应 {@code SizingCalculator.computeContentHeight} 的 COLUMN 分支 +
     * {@code computeHeight} 的非 fill/非 scrollable 出口。仅当容器高度完全由
     * 子内容聚合决定时（shrink-to-fit 语义），求和等式才严格成立。</p>
     *
     * <h3>前置条件守卫</h3>
     * <p>以下任一条件不满足时，本方法调用 {@link Assume#assumeTrue(boolean)} 让测试
     * <b>跳过</b>（而非失败）—— 因为求和等式在该场景下本就不成立，不应误判为缺陷：</p>
     * <ul>
     *   <li>容器方向为 COLUMN；</li>
     *   <li>容器非 scrollable（scrollable 走 viewportHeight，主动忽略内容高）；</li>
     *   <li>容器非 fill / 非 grow / 非 percent（否则高度取 max(内容高, 约束高)）；</li>
     *   <li>容器无 preferredHeight 覆盖（否则被外尺寸下限 max）；</li>
     *   <li>容器无 maxHeight 上限（否则被 clampHeight min 截断）；</li>
     *   <li>容器有至少一个带 cachedLayout 的子节点。</li>
     * </ul>
     *
     * <p><b>误用提示</b>：守卫不满足时 {@link Assume} 跳过而非失败，看到 skip 属正常，
     * 不要误判为断言失效。</p>
     *
     * @param container 待断言的 COLUMN 容器（非 null，需已 layout）
     */
    public static void assertColumnHeightSum(SceneNode container) {
        if (container == null) {
            throw new IllegalArgumentException("container 不可为 null");
        }
        Assume.assumeTrue("容器方向须为 COLUMN",
                container.getFlexDirection() == FlexDirection.COLUMN);
        Assume.assumeTrue("COLUMN 求和断言要求容器非 scrollable",
                !container.isScrollable());
        Assume.assumeTrue("COLUMN 求和断言要求非 fill/grow/percent",
                !container.isFillParentHeight()
                        && container.getFlexGrow() <= 0
                        && container.getPercentHeight() <= 0);
        Assume.assumeTrue("COLUMN 求和断言要求无 preferredHeight 覆盖",
                container.getPreferredHeight() <= 0);
        Assume.assumeTrue("COLUMN 求和断言要求无 maxHeight 上限",
                container.getMaxHeight() <= 0);

        List<SceneNode> children = container.__getChildren();
        int total = 0;
        int count = 0;
        for (SceneNode child : children) {
            Object childCached = child.getCachedLayout();
            if (!(childCached instanceof LayoutBox)) {
                continue;
            }
            LayoutBox childBox = (LayoutBox) childCached;
            // 主轴占用含子 marginV（CSS box model 语义），与 computeContentHeight 对齐
            total += childBox.getHeight() + child.marginV();
            count++;
        }
        Assume.assumeTrue("COLUMN 求和断言要求至少一个带 cachedLayout 的子节点", count > 0);

        int totalGap = count > 1 ? container.getGap() * (count - 1) : 0;
        int padV = container.getPaddingTop() + container.getPaddingBottom();
        int expected = total + totalGap + padV;

        Object cached = container.getCachedLayout();
        Assert.assertNotNull("容器 cachedLayout 为 null（未 layout）", cached);
        Assert.assertTrue("容器 cachedLayout 不是 LayoutBox", cached instanceof LayoutBox);
        LayoutBox box = (LayoutBox) cached;
        Assert.assertEquals("COLUMN shrink-to-fit 容器高度应 == Σ(子高+marginV)+gap*(n-1)+padV",
                expected, box.getHeight());
    }

    /**
     * 断言 ROW 容器宽度 == min(可用外宽, Σ(子节点宽度 + 子节点 marginH) + gap*(n-1) + 左右 padding)。
     *
     * <p>对应 {@code SizingCalculator.computeShrinkContainerWidth} 的 ROW 分支。
     * SHRINK 容器在内容宽不超过可用外宽时回收内容宽，被 {@code outerWidth} clamp，
     * 故求和等式带 min(outerWidth, ...)。</p>
     *
     * <h3>前置条件守卫</h3>
     * <p>以下任一条件不满足时跳过：</p>
     * <ul>
     *   <li>容器方向为 ROW；</li>
     *   <li>容器宽度策略为 {@link SceneNode.WidthSizing#SHRINK}（非 SHRINK 是 fill=可用宽）；</li>
     *   <li>容器无 maxWidth 上限（避免双重 min 截断）；</li>
     *   <li>容器有至少一个带 cachedLayout 的子节点。</li>
     * </ul>
     *
     * <p><b>误用提示</b>：守卫不满足时 {@link Assume} 跳过而非失败，看到 skip 属正常，
     * 不要误判为断言失效。</p>
     *
     * @param container      待断言的 ROW 容器（非 null，需已 layout）
     * @param availableWidth 容器收到的可用外宽（即约束下传的 availableWidth）
     */
    public static void assertRowWidthSum(SceneNode container, int availableWidth) {
        if (container == null) {
            throw new IllegalArgumentException("container 不可为 null");
        }
        Assume.assumeTrue("容器方向须为 ROW",
                container.getFlexDirection() == FlexDirection.ROW);
        Assume.assumeTrue("ROW 求和断言要求宽度策略为 SHRINK",
                container.getWidthSizing() == SceneNode.WidthSizing.SHRINK);
        Assume.assumeTrue("ROW 求和断言要求无 maxWidth 上限",
                container.getMaxWidth() <= 0);

        List<SceneNode> children = container.__getChildren();
        int contentWidth = 0;
        int count = 0;
        for (SceneNode child : children) {
            Object childCached = child.getCachedLayout();
            if (!(childCached instanceof LayoutBox)) {
                continue;
            }
            LayoutBox childBox = (LayoutBox) childCached;
            // 主轴占用含子 marginH（CSS box model 语义），与 computeShrinkContainerWidth 对齐
            contentWidth += childBox.getWidth() + child.marginH();
            count++;
        }
        Assume.assumeTrue("ROW 求和断言要求至少一个带 cachedLayout 的子节点", count > 0);

        int totalGap = count > 1 ? container.getGap() * (count - 1) : 0;
        int padH = container.getPaddingLeft() + container.getPaddingRight();
        int expected = Math.min(availableWidth, contentWidth + totalGap + padH);

        Object cached = container.getCachedLayout();
        Assert.assertNotNull("容器 cachedLayout 为 null（未 layout）", cached);
        Assert.assertTrue("容器 cachedLayout 不是 LayoutBox", cached instanceof LayoutBox);
        LayoutBox box = (LayoutBox) cached;
        Assert.assertEquals("ROW SHRINK 容器宽度应 == min(外宽, Σ(子宽+marginH)+gap*(n-1)+padH)",
                expected, box.getWidth());
    }

    // ==================== I7 重算断言 ====================

    /**
     * 断言本次 layout 的重算节点集合（{@link LayoutResult#getRelayoutedNodes()}）
     * 精确匹配期望集合（顺序无关）。
     *
     * <p>用于 I7 增量失效验证：构造失效场景后，断言恰好预期的脏节点被重算，
     * 既不多（不该脏的被牵连）也不少（该脏的没漏掉）。</p>
     *
     * @param result   layout 结果（非 null）
     * @param expected 期望被重算的节点（可为空，表示无重算）
     */
    public static void assertRelayoutSet(LayoutResult result, SceneNode... expected) {
        if (result == null) {
            throw new IllegalArgumentException("result 不可为 null");
        }
        Set<SceneNode> actual = result.getRelayoutedNodes();
        Set<SceneNode> expectedSet = new HashSet<>();
        for (SceneNode n : expected) {
            expectedSet.add(n);
        }
        Assert.assertEquals("重算节点集合应精确匹配", expectedSet, actual);
    }

    /**
     * 断言本次 layout 无任何重算（{@link LayoutResult#getRelayoutCount()}==0 且
     * 重算节点集合为空）。
     *
     * <p>这是 I7 增量失效的核心断言：干净帧（约束未变、无 setter 触发失效）下，
     * layout 引擎应整棵跳过，零重算。</p>
     *
     * @param result layout 结果（非 null）
     */
    public static void assertNoRelayout(LayoutResult result) {
        if (result == null) {
            throw new IllegalArgumentException("result 不可为 null");
        }
        Assert.assertEquals("本帧不应重算任何节点", 0, result.getRelayoutCount());
        Assert.assertTrue("重算节点集合应为空", result.getRelayoutedNodes().isEmpty());
    }
}
