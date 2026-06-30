package club.heiqi.uilib.ui.scene.layout;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * ROW 主轴 grow 宽度分配专测（阶段 4.2 ConstraintResolver.computeRowGrowWidths 验收）。
 *
 * <p>对称于 {@link SceneLayoutEngineTest} 的 COLUMN flexGrow 权重分配系列，
 * 覆盖 ROW 主轴三类场景：</p>
 * <ul>
 *   <li>viewport(flexGrow=1) + 固定 preferredWidth column 不溢出父（滚动条溢出修复回归）；</li>
 *   <li>多 grow 子按权重分配 + 固定兄弟 + gap 精确吃满父宽；</li>
 *   <li>固定兄弟不可先验时早退回退现状，不抛异常（回退行为锚定）。</li>
 * </ul>
 *
 * <p>度量用 {@link FixedTextMeasurer}（charWidth=8, lineHeight=16），保证纯 JUnit 可断言。
 * 布局引擎 per-test 新建，避免跨用例缓存污染。</p>
 */
public class RowGrowWidthAllocationTest {

    private final FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
    private final SceneLayoutEngine engine = new SceneLayoutEngine(measurer);

    // ============================================================
    // 场景 1：viewport flexGrow=1 + 固定 preferredWidth column 不溢出父
    // ============================================================

    /**
     * ROW 容器宽=100、gap=3，viewport(scrollable, flexGrow=1, 无 preferredWidth) +
     * column(preferredWidth=8)。
     *
     * <p>fixedW=8（column priorKnownChildWidth=preferredWidth），totalGap=3，
     * freeW=100-8-3=89，viewport flexGrow=1 吃满 89。
     * 断言：viewport LayoutBox width=89；column LayoutBox x=92 width=8 right=100，
     * column 不溢出父边界（right==父宽 100）。</p>
     *
     * <p>这是 commit 4562afc7「修滚动条溢出」场景的回归专测：旧路径下固定宽 column
     * 的「依赖约束宽」子节点按裸约束宽布局而溢出父盒，computeRowGrowWidths 修复后
     * viewport 按权重分得剩余宽，column 紧贴父右边界。</p>
     */
    @Test
    public void viewportGrowWithFixedPreferredWidthColumnShouldNotOverflowParent() {
        // ROW 容器：宽 100 由约束下传，gap=3
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.ROW);
        row.setGap(3);

        // viewport：scrollable + flexGrow=1，无 preferredWidth
        SceneNode viewport = new SceneNode();
        viewport.setScrollable(true);
        viewport.setFlexGrow(1);

        // column：固定 preferredWidth=8（COLUMN 方向容器，有 preferredWidth 即可先验）
        SceneNode column = new SceneNode();
        column.setFlexDirection(FlexDirection.COLUMN);
        column.setPreferredWidth(8);

        row.appendChild(viewport);
        row.appendChild(column);

        engine.layout(row, new Constraints(100, 100));

        LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
        LayoutBox columnBox = (LayoutBox) column.getCachedLayout();
        Assert.assertNotNull("viewport 应已布局", viewportBox);
        Assert.assertNotNull("column 应已布局", columnBox);

        // viewport 按权重吃满 freeW=89
        Assert.assertEquals("viewport LayoutBox width=89 (100-8-3)",
                89, viewportBox.getWidth());
        // viewport 起始 x=0
        Assert.assertEquals("viewport x=0", 0, viewportBox.getX());

        // column 紧贴 viewport 之后 + gap
        Assert.assertEquals("column x=92 (89+3)", 92, columnBox.getX());
        Assert.assertEquals("column width=8 (preferredWidth)", 8, columnBox.getWidth());
        // column 右边界 = x + width = 100，不溢出父
        int columnRight = columnBox.getX() + columnBox.getWidth();
        Assert.assertEquals("column right=100，不溢出父边界",
                100, columnRight);
    }

    // ============================================================
    // 场景 2：多 grow 子按权重分配 + 固定兄弟 + gap 精确吃满父宽
    // ============================================================

    /**
     * ROW 容器宽=100、gap=2，A(flexGrow=2) + B(flexGrow=1) + C(preferredWidth=20)。
     *
     * <p>fixedW=20（C），totalGap=2*2=4（3 子 2 gap），freeW=100-20-4=76，Σw=3。
     * A=76*2/3=50（非末位按比例），B=末位补余=76-50=26。
     * 断言 A+B+C+gaps=50+26+20+4=100 精确吃满父宽。</p>
     */
    @Test
    public void rowMultiGrowByWeightWithFixedSiblingAndGapShouldFillParent() {
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.ROW);
        row.setGap(2);

        SceneNode a = new SceneNode();
        a.setFlexGrow(2);
        SceneNode b = new SceneNode();
        b.setFlexGrow(1);
        SceneNode c = new SceneNode();
        c.setPreferredWidth(20);

        row.appendChild(a);
        row.appendChild(b);
        row.appendChild(c);

        engine.layout(row, new Constraints(100, 100));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        LayoutBox cBox = (LayoutBox) c.getCachedLayout();
        Assert.assertNotNull("a 应已布局", aBox);
        Assert.assertNotNull("b 应已布局", bBox);
        Assert.assertNotNull("c 应已布局", cBox);

        Assert.assertEquals("A 按权重 2/3 分得 50", 50, aBox.getWidth());
        Assert.assertEquals("B 末位补余得 26", 26, bBox.getWidth());
        Assert.assertEquals("C 固定 preferredWidth=20", 20, cBox.getWidth());

        // Σalloc + 固定 + gap 精确吃满父宽 100
        int total = aBox.getWidth() + bBox.getWidth() + cBox.getWidth() + 2 * 2;
        Assert.assertEquals("A+B+C+gaps=100 精确吃满父宽",
                100, total);

        // 位置连续性：A x=0, B x=50+2=52, C x=52+26+2=80, C right=100
        Assert.assertEquals("A x=0", 0, aBox.getX());
        Assert.assertEquals("B x=52 (50+2)", 52, bBox.getX());
        Assert.assertEquals("C x=80 (52+26+2)", 80, cBox.getX());
        Assert.assertEquals("C right=100 不溢出",
                100, cBox.getX() + cBox.getWidth());
    }

    // ============================================================
    // 场景 3：固定兄弟不可先验时早退回退现状（不抛异常）
    // ============================================================

    /**
     * ROW 容器宽=100，A(flexGrow=1) + B(无 preferredWidth，有子节点容器)。
     *
     * <p>priorKnownChildWidth(B)=UNCONSTRAINED（容器先验宽不可知）→
     * computeRowGrowWidths 早退返回空 Map → buildChildConstraints 回退现状
     * （childWidth = innerWidth - marginH = 100）。A 收到 Constraints(100, ...)，
     * computeWidth 返回约束宽 100，A 拿满宽 100（回退行为）。</p>
     *
     * <p>断言：不抛异常；A LayoutBox width=100（回退现状，grow 分配放弃）。
     * B 同样收到 100 宽（会与 A 溢出父盒，但回退路径不保证不溢出，本测只锚定
     * 「不抛异常 + A 拿满宽」的回退契约）。</p>
     */
    @Test
    public void rowGrowWithUnconstrainedSiblingContainerShouldFallbackNotThrow() {
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.ROW);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);

        // B：容器节点（有子），无 preferredWidth → priorKnownChildWidth=UNCONSTRAINED
        SceneNode b = new SceneNode();
        SceneNode bChild = new SceneNode();
        bChild.setPreferredWidth(30);
        b.appendChild(bChild);

        row.appendChild(a);
        row.appendChild(b);

        // 不抛异常是首要断言（早退回退路径不应崩）
        engine.layout(row, new Constraints(100, 100));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        Assert.assertNotNull("A 应已布局（回退路径不抛异常）", aBox);
        // 回退现状：A 收到 innerWidth=100，拿满宽 100（grow 分配放弃）
        Assert.assertEquals("A 回退拿满宽 100（grow 分配早退）",
                100, aBox.getWidth());
    }
}
