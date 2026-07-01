package club.heiqi.uilib.ui.scene.layout;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * {@link LayoutAssertions} 断言库的最小冒烟测试。
 *
 * <p>对每个 helper 方法构造最小场景验证「正确情况不抛 / 错误情况抛 {@link AssertionError}」，
 * 确保断言库本身的判定逻辑正确，避免后续 L2 测试误用坏掉的 helper。</p>
 *
 * <p>装配复用 scene 测试标准模式：{@link FixedTextMeasurer}（确定性度量替身）+
 * {@link SceneLayoutEngine} + {@link Constraints}。</p>
 */
public class LayoutAssertionsTest {

    /** 确定性文本度量替身：每字符宽 8px，行高 16px。 */
    private final FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
    /** 被测布局引擎。 */
    private final SceneLayoutEngine engine = new SceneLayoutEngine(measurer);

    // ============================================================
    // assertLocalBox
    // ============================================================

    /**
     * 正确值：构造 column + 一个固定尺寸子，layout 后用 assertLocalBox 断言实际值不抛。
     */
    @Test
    public void assertLocalBox_matchesActual_passes() {
        SceneNode root = SceneNode.column();
        SceneNode child = new SceneNode().setPreferredWidth(50).setPreferredHeight(20);
        root.appendChild(child);

        engine.layout(root, new Constraints(100));

        // 子固定宽 50、固定高 20；column 首子 x=padLeft=0、y=padTop=0
        LayoutAssertions.assertLocalBox(child, 0, 0, 50, 20);
    }

    /**
     * 错误值：断言 assertLocalBox 在高度不匹配时抛 {@link AssertionError}。
     */
    @Test
    public void assertLocalBox_wrongValue_fails() {
        SceneNode root = SceneNode.column();
        SceneNode child = new SceneNode().setPreferredWidth(50).setPreferredHeight(20);
        root.appendChild(child);

        engine.layout(root, new Constraints(100));

        try {
            LayoutAssertions.assertLocalBox(child, 0, 0, 50, 999);
            Assert.fail("高度不匹配应抛 AssertionError");
        } catch (AssertionError expected) {
            // 期望路径：helper 正确检测到不匹配
        }
    }

    /**
     * cachedLayout 为 null（未 layout）时 assertLocalBox 应抛 {@link AssertionError}。
     */
    @Test
    public void assertLocalBox_nullCache_fails() {
        SceneNode node = new SceneNode();
        node.markSelfLayout(); // 清空 cachedLayout
        try {
            LayoutAssertions.assertLocalBox(node, 0, 0, 10, 10);
            Assert.fail("cachedLayout 为 null 应抛 AssertionError");
        } catch (AssertionError expected) {
            // 期望路径
        }
    }

    // ============================================================
    // assertOnlyInvalidation
    // ============================================================

    /**
     * LAYOUT 级：markSelfLayout 后断言恰好 LAYOUT 失效，不抛。
     */
    @Test
    public void assertOnlyInvalidation_layout_passes() {
        SceneNode node = new SceneNode();
        node.markSelfLayout();
        LayoutAssertions.assertOnlyInvalidation(node, LayoutAssertions.InvalidationLevel.LAYOUT);
    }

    /**
     * GEOMETRY 级：markGeometryDirty 后断言恰好 GEOMETRY 失效，不抛。
     */
    @Test
    public void assertOnlyInvalidation_geometry_passes() {
        SceneNode node = new SceneNode();
        node.markGeometryDirty();
        LayoutAssertions.assertOnlyInvalidation(node, LayoutAssertions.InvalidationLevel.GEOMETRY);
    }

    /**
     * 混级污染应被检测：markSelfLayout + markSelfPaint 后断言恰好 LAYOUT 应抛 {@link AssertionError}。
     */
    @Test
    public void assertOnlyInvalidation_multiLevel_fails() {
        SceneNode node = new SceneNode();
        node.markSelfLayout();
        node.markSelfPaint(); // 同时污染 PAINT
        try {
            LayoutAssertions.assertOnlyInvalidation(node, LayoutAssertions.InvalidationLevel.LAYOUT);
            Assert.fail("同时有 PAINT 脏时应抛 AssertionError");
        } catch (AssertionError expected) {
            // 期望路径：helper 正确检测到 PAINT 污染
        }
    }

    // ============================================================
    // assertClean
    // ============================================================

    /**
     * 初始干净节点：assertClean 对所有 dirty 位为 false 的新建节点不抛。
     *
     * <p>注：layout 遍历只清 layout 相关 dirty，paint/geometry/composite 由各自遍历清；
     * 故「完全 clean」的判定逻辑用初始干净节点验证，避免与 layout 的部分清脏语义耦合。</p>
     */
    @Test
    public void assertClean_freshNode_passes() {
        SceneNode fresh = new SceneNode();
        LayoutAssertions.assertClean(fresh);
    }

    // ============================================================
    // assertColumnHeightSum
    // ============================================================

    /**
     * shrink COLUMN 容器：两固定高子 + gap + padding，layout 后断言高度 == 求和值，不抛。
     */
    @Test
    public void assertColumnHeightSum_shrinkColumn_passes() {
        SceneNode container = SceneNode.column(5).setPadding(4);
        SceneNode a = new SceneNode().setPreferredHeight(10);
        SceneNode b = new SceneNode().setPreferredHeight(10);
        container.appendChild(a);
        container.appendChild(b);

        engine.layout(container, new Constraints(200));

        // 期望 = (10+0)+(10+0) + gap*(2-1) + padV(4+4) = 20 + 5 + 8 = 33
        LayoutAssertions.assertColumnHeightSum(container);
        // 额外精确核对一次，确保 helper 内部算式与手算一致
        Object cached = container.getCachedLayout();
        Assert.assertTrue(cached instanceof LayoutBox);
        Assert.assertEquals("COLUMN shrink 高度精确值", 33, ((LayoutBox) cached).getHeight());
    }

    // ============================================================
    // assertNoRelayout / assertRelayoutSet
    // ============================================================

    /**
     * 干净帧：同一约束连续 layout 两次，第二次应零重算，assertNoRelayout 不抛。
     */
    @Test
    public void assertNoRelayout_cleanSecondLayout_passes() {
        SceneNode root = SceneNode.column();
        root.appendChild(new SceneNode().setPreferredHeight(10));
        Constraints c = new Constraints(100);

        engine.layout(root, c);           // 首次布局
        LayoutResult second = engine.layout(root, c); // 同约束复算 → 干净帧

        LayoutAssertions.assertNoRelayout(second);
    }

    /**
     * 首次 layout 有重算：assertRelayoutSet 精确匹配被重算的节点集合。
     */
    @Test
    public void assertRelayoutSet_firstLayout_containsRootAndChild() {
        SceneNode root = SceneNode.column();
        SceneNode child = new SceneNode().setPreferredHeight(10);
        root.appendChild(child);

        LayoutResult first = engine.layout(root, new Constraints(100));

        // 首次 layout：root 与 child 均 selfLayoutDirty（appendChild 标脏），都应进重算集
        LayoutAssertions.assertRelayoutSet(first, root, child);
    }
}
